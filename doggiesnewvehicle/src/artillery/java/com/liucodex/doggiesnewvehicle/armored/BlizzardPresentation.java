package com.liucodex.doggiesnewvehicle.armored;

import java.util.ArrayDeque;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;

/** One bounded playback clock for the hull, turret, exhaust and accepted cold shots. No gameplay writes. */
public final class BlizzardPresentation {
    public record Pose(long tick, Vec3 position, float yaw, float pitch, float roll,
            float turretYaw, float turretPitch, Vec3 velocity, boolean motionPowered) {
        public Pose(long tick,Vec3 position,float yaw,float pitch,float roll,float turretYaw,float turretPitch,Vec3 velocity){
            this(tick,position,yaw,pitch,roll,turretYaw,turretPitch,velocity,false);
        }
        public Matrix4d transform() {
            return new Matrix4d().translation(position.x, position.y, position.z)
                    .rotateY(Math.toRadians(-yaw)).rotateX(Math.toRadians(pitch)).rotateZ(Math.toRadians(roll));
        }
    }
    public record Pulse(long tick, Vec3 origin, Vec3 velocity, double distance) {
        public Vec3 point(double time) {
            double age = time - tick;
            return age < 0 || age * velocity.length() >= distance ? null : origin.add(velocity.scale(age));
        }
    }
    public record Laser(long tick, Vec3 origin, Vec3 end) {
        public float alpha(double at) {
            double age = at - tick;
            return age < 0 || age >= BlizzardRules.TRAIL_TICKS ? 0 : (float)(1 - age / BlizzardRules.TRAIL_TICKS);
        }
    }
    private final ArrayDeque<Pose> poses = new ArrayDeque<>();
    private final ArrayDeque<Pulse> pulses = new ArrayDeque<>();
    private final ArrayDeque<Laser> lasers = new ArrayDeque<>();
    private double previousTime, time;
    private long lastSoundTick = Long.MIN_VALUE;
    private long lastLaserSoundTick = Long.MIN_VALUE;
    private long laserTrackingStart = Long.MIN_VALUE;
    // One cue per accepted shot; duplicate snapshots never add voices.
    private static final int LASER_SOUND_TICKS = 6;

    public void accept(Pose pose, Pulse pulse) {
        if (!poses.isEmpty() && pose.tick <= poses.getLast().tick) return;
        if (poses.isEmpty() || pose.tick - poses.getLast().tick > 20
                || pose.position.distanceToSqr(poses.getLast().position) > 64 * 64) {
            poses.clear(); pulses.clear(); lasers.clear(); previousTime = time = pose.tick;
            // Joining, teleporting or resuming after a long gap must not replay old shots.
            lastSoundTick = pose.tick;
            laserTrackingStart = pose.tick;
            lastLaserSoundTick = Long.MIN_VALUE;
        }
        poses.addLast(pose);
        while (poses.size() > 16) poses.removeFirst();
        acceptPulse(pulse,pose.tick);
        while (!pulses.isEmpty() && poses.getLast().tick - pulses.getFirst().tick > BlizzardRules.BOLT_LIFE + 4) pulses.removeFirst();
        while (!lasers.isEmpty() && pose.tick - lasers.getFirst().tick > BlizzardRules.TRAIL_TICKS) lasers.removeFirst();
    }
    private void acceptPulse(Pulse pulse,long at){
        if(pulse==null||at<pulse.tick||at-pulse.tick>BlizzardRules.BOLT_LIFE+4
            ||!Double.isFinite(pulse.distance)||pulse.distance<0||pulse.distance>BlizzardRules.RANGE
            ||!Double.isFinite(pulse.origin.lengthSqr())||!Double.isFinite(pulse.velocity.lengthSqr()))return;
        var sorted=new java.util.ArrayList<Pulse>(pulses);
        for(int i=0;i<sorted.size();i++)if(sorted.get(i).tick==pulse.tick){
            var old=sorted.get(i);
            if(!old.origin.equals(pulse.origin)||!old.velocity.equals(pulse.velocity))return;
            sorted.set(i,new Pulse(old.tick,old.origin,old.velocity,Math.min(old.distance,pulse.distance)));
            pulses.clear();pulses.addAll(sorted);return;
        }
        sorted.add(pulse);sorted.sort(java.util.Comparator.comparingLong(Pulse::tick));
        while(sorted.size()>8)sorted.remove(0);
        pulses.clear();pulses.addAll(sorted);
    }
    public Pose advance() {
        if (poses.isEmpty()) return null;
        previousTime = time;
        // Retain two ticks for normal jitter; bounded catch-up never extrapolates an unauthorised hull pose.
        double desired = poses.getLast().tick - 2;
        time = Math.max(time, Math.min(poses.getLast().tick, time + Mth.clamp(desired - time, 0, 1.25)));
        return sample(time);
    }
    public double time(float partial) { return Mth.lerp(partial, previousTime, time); }
    public Pose sample(float partial) { return sample(time(partial)); }
    public Pose sample(double at) {
        if (poses.isEmpty()) return null;
        Pose before = poses.getFirst();
        for (Pose after : poses) {
            if (after.tick >= at) {
                float f = after.tick == before.tick ? 0 : (float) Mth.clamp((at - before.tick) / (after.tick - before.tick), 0, 1);
                return new Pose((long) at, before.position.lerp(after.position, f),
                        Mth.rotLerp(f, before.yaw, after.yaw), Mth.rotLerp(f, before.pitch, after.pitch),
                        Mth.rotLerp(f, before.roll, after.roll), Mth.rotLerp(f, before.turretYaw, after.turretYaw),
                        Mth.lerp(f, before.turretPitch, after.turretPitch), before.velocity.lerp(after.velocity, f),
                        f<1?before.motionPowered:after.motionPowered);
            }
            before = after;
        }
        return before;
    }
    public Iterable<Pulse> pulses() { return pulses; }
    public void acceptLaser(CompoundTag tag) {
        // Releasing fire stops damage, but an accepted shot fades at its original world-space ray.
        if (!tag.contains("Tick") || poses.isEmpty()) return;
        long tick = tag.getLong("Tick");
        long now = poses.getLast().tick;
        if (tick <= laserTrackingStart || tick > now || now - tick >= BlizzardRules.TRAIL_TICKS) return;
        if (!lasers.isEmpty() && tick <= lasers.getLast().tick) return;
        Vec3 origin = new Vec3(tag.getDouble("Ox"), tag.getDouble("Oy"), tag.getDouble("Oz"));
        Vec3 end = new Vec3(tag.getDouble("Ex"), tag.getDouble("Ey"), tag.getDouble("Ez"));
        if (!Double.isFinite(origin.lengthSqr()) || !Double.isFinite(end.lengthSqr())
                || origin.distanceToSqr(end) > BlizzardRules.MODE2_RANGE * BlizzardRules.MODE2_RANGE + .001) return;
        lasers.addLast(new Laser(tick, origin, end));
        while (lasers.size() > 8) lasers.removeFirst();
    }
    public Iterable<Laser> lasers() { return lasers; }
    /** Called once per client tick, including off-screen vehicles. Never leads the rendered clock. */
    public Pulse pollSound() {
        for (Pulse pulse : pulses) {
            if (pulse.tick <= lastSoundTick || pulse.tick > previousTime) continue;
            lastSoundTick = pulse.tick;
            // A stalled client can skip a shot, but must not burst-play an obsolete backlog.
            if (previousTime - pulse.tick <= 2) {
                if (pulse.velocity.lengthSqr() == 0) {
                    if (lastLaserSoundTick != Long.MIN_VALUE && pulse.tick - lastLaserSoundTick < LASER_SOUND_TICKS) continue;
                    lastLaserSoundTick = pulse.tick;
                }
                return pulse;
            }
        }
        return null;
    }
    public static CompoundTag encode(Pose pose, Pulse pulse) {
        var tag = new CompoundTag();
        tag.putLong("Tick", pose.tick); put(tag, "P", pose.position); put(tag, "V", pose.velocity);
        tag.putFloat("Y", pose.yaw); tag.putFloat("X", pose.pitch); tag.putFloat("R", pose.roll);
        tag.putFloat("TY", pose.turretYaw); tag.putFloat("TX", pose.turretPitch);
        tag.putBoolean("MotionPowered",pose.motionPowered);
        if (pulse != null && pose.tick - pulse.tick <= BlizzardRules.BOLT_LIFE + 4) {
            tag.putLong("Shot", pulse.tick); put(tag, "O", pulse.origin); put(tag, "D", pulse.velocity);
            tag.putDouble("Range", pulse.distance);
        }
        return tag;
    }
    /** Extend the existing hull snapshot, without another packet or gameplay owner. */
    public static void appendPulses(CompoundTag tag,Iterable<Pulse> flights){
        var list=new net.minecraft.nbt.ListTag();int count=0;
        for(var pulse:flights){
            if(++count>8)break;
            var item=new CompoundTag();item.putLong("Shot",pulse.tick);put(item,"O",pulse.origin);put(item,"D",pulse.velocity);
            item.putDouble("Range",pulse.distance);list.add(item);
        }
        tag.put("Pulses",list);
    }
    /** Same snapshot channel and bounded lifetime as the pulse history; no new fire/control owner. */
    public static void appendLasers(CompoundTag tag, Iterable<Laser> shots) {
        var list = new net.minecraft.nbt.ListTag();
        for (var shot : shots) {
            if (list.size() >= 8) break;
            var item = new CompoundTag();
            item.putLong("Tick", shot.tick); put(item, "O", shot.origin); put(item, "E", shot.end);
            list.add(item);
        }
        tag.put("Beams", list);
    }
    public void accept(CompoundTag tag) {
        if (!tag.contains("Tick")||!poses.isEmpty()&&tag.getLong("Tick")<=poses.getLast().tick) return;
        accept(new Pose(tag.getLong("Tick"), get(tag, "P"), tag.getFloat("Y"), tag.getFloat("X"), tag.getFloat("R"),
                tag.getFloat("TY"), tag.getFloat("TX"), get(tag, "V"),tag.getBoolean("MotionPowered")),
                tag.contains("Shot") ? new Pulse(tag.getLong("Shot"), get(tag, "O"), get(tag, "D"), tag.getDouble("Range")) : null);
        var flights=tag.getList("Pulses",10);
        for(int i=0;i<Math.min(8,flights.size());i++){
            var item=flights.getCompound(i);
            acceptPulse(new Pulse(item.getLong("Shot"),get(item,"O"),get(item,"D"),item.getDouble("Range")),tag.getLong("Tick"));
        }
        var beams = tag.getList("Beams", 10);
        for (int i = 0; i < Math.min(8, beams.size()); i++) acceptLaser(beams.getCompound(i));
    }
    private static void put(CompoundTag tag, String key, Vec3 v) {
        tag.putDouble(key + "x", v.x); tag.putDouble(key + "y", v.y); tag.putDouble(key + "z", v.z);
    }
    private static Vec3 get(CompoundTag tag, String key) {
        return new Vec3(tag.getDouble(key + "x"), tag.getDouble(key + "y"), tag.getDouble(key + "z"));
    }
}
