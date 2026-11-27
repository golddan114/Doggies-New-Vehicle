package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import com.atsuishio.superbwarfare.tools.ParticleTool;
import com.liucodex.doggiesnewvehicle.api.combat.DnvColdArmor;
import com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotAdmission;
import com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor;
import com.liucodex.doggiesnewvehicle.core.DnvNativeRemoteShotGuard;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Quaterniond;

/** Fixed-casemate player tank. Native SBW owns chassis, crew, engine audio, inventory and GunData. */
public final class DnvProject612Entity extends VehicleEntity implements DnvColdArmor, DnvPhysicalArmor,
        DnvHullTarget, DnvNativeShotAdmission, DnvNativeRemoteShotGuard {
    public static final String TYPE_ID = "doggievehicle:project612";
    private static final EntityDataAccessor<Float> TRACK_L = scalar(), TRACK_R = scalar(), WHEEL_L = scalar(), WHEEL_R = scalar();
    private final Project612Armament armament = new Project612Armament(this);
    private float oldTrackL, oldTrackR, oldWheelL, oldWheelR;
    private List<OBB> nativeBoxes, damageBoxes;

    public DnvProject612Entity(EntityType<? extends DnvProject612Entity> type, Level level) { super(type, level); }
    @Override public Tier coldArmorTier() { return Tier.HEAVY; }
    @Override public float ordinaryArmorReduction() { return com.liucodex.doggiesnewvehicle.core.DnvArmorRules.Profile.HEAVY.reduction(); }
    private static EntityDataAccessor<Float> scalar() {
        return SynchedEntityData.defineId(DnvProject612Entity.class, EntityDataSerializers.FLOAT);
    }
    @Override protected void defineSynchedData() {
        super.m_8097_();
        entityData.define(TRACK_L, 0F); entityData.define(TRACK_R, 0F);
        entityData.define(WHEEL_L, 0F); entityData.define(WHEEL_R, 0F);
    }
    @Override public boolean canShoot(LivingEntity actor) {
        return actor != null && actor == getFirstPassenger() && actor.isAlive() && !actor.isSpectator() && !isWreck()
                && (level().isClientSide ? super.canShoot(actor) : armament.ready(actor));
    }
    @Override public void vehicleShoot(LivingEntity actor, UUID target, Vec3 point) { armament.fire(actor, null); }
    @Override public boolean permitsNativeClientFire(Entity actor, Entity lockedTarget, boolean locked, GunData gun) {
        return actor instanceof Player && actor == getFirstPassenger() && actor.isAlive() && !actor.isSpectator()
                && !isRemoved() && !isWreck() && getWeaponIndex(0) == 0
                && gun != null && gun == getGunData("Main") && armament.clear();
    }
    @Override public void vehicleShoot(LivingEntity actor, String weapon, Vec3 point) { armament.fire(actor, weapon); }
    @Override public void vehicleShoot(LivingEntity actor, String weapon, UUID target, Vec3 point) { armament.fire(actor, weapon); }
    void submitNativeShot(LivingEntity actor) { super.vehicleShoot(actor, "Main", null, null); }
    @Override public Runnable guardNativeShot(Runnable work) { return armament.guard(work); }
    @Override public boolean deferNativeShotEffects() { return true; }
    @Override public boolean deferNativeClientSound() { return true; }
    @Override public void onNativeShotQueueResult(boolean accepted) { armament.queueResult(accepted); }
    @Override public boolean admitNativeProjectile(ServerLevel level, Entity projectile, ShootParameters p) { return armament.admit(level, projectile, p); }
    @Override public boolean nativeProjectileAccepted(ShootParameters p) { return armament.accepted(p); }
    @Override public void afterShoot(GunData gun, Vec3 direction) {
        super.afterShoot(gun, direction);
        if (level() instanceof ServerLevel server && gun == getGunData("Main"))
            ParticleTool.spawnBigCannonMuzzleParticles(direction, getShootPos("Main", 1), server, this);
    }
    @Override public float getEngineSoundVolume() { return engineRunning() ? Math.max(.16F, super.getEngineSoundVolume()) : 0; }

    @Override public void travel() {
        float before = getYRot();
        boolean left = leftInputDown(), right = rightInputDown();
        float aim = hullAimError();
        boolean following = Float.isFinite(aim);
        if (following) {
            if (Math.abs(aim) <= .05F) { setDeltaRot(0); setHoldTick(0); }
            else {
                // The native track engine reverses its steering branch while S is held.
                boolean turnRight = aim > 0;
                if (backInputDown()) turnRight = !turnRight;
                setLeftInputDown(!turnRight); setRightInputDown(turnRight);
            }
        }
        setSprintInputDown(false);
        try { super.travel(); }
        finally { setLeftInputDown(left); setRightInputDown(right); }
        float turn = Mth.clamp(ApocalypseMotion.degrees(getYRot() - before), -1F, 1F);
        if (following && Math.signum(turn) == Math.signum(aim) && Math.abs(turn) > Math.abs(aim)) {
            turn = aim; setDeltaRot(0); setHoldTick(0);
        }
        setYRot(before + turn);
        Vec3 velocity = getDeltaMovement();
        double cap = velocity.dot(getViewVector(1)) < 0 ? .05 : .15;
        double speed = velocity.horizontalDistance();
        if (onGround() && speed > cap) setDeltaMovement(velocity.x * cap / speed, velocity.y, velocity.z * cap / speed);
    }
    private float hullAimError() {
        Entity driver = getNthEntity(0);
        if (!(driver instanceof Player) || !driver.isAlive() || driver.isSpectator() || isRemoved() || isWreck()
                // Native engineRunning() means nonzero throttle, not readiness; a stationary track can pivot.
                || (level().isClientSide && !isControlledByLocalInstance()) || !onGround() || getEnergy() <= 0
                || leftInputDown() || rightInputDown()) return Float.NaN;
        Vec3 view = driver.getViewVector(1);
        if (!Double.isFinite(view.x + view.y + view.z) || view.horizontalDistanceSqr() < 1e-8) return Float.NaN;
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-view.x, view.z)) - getYRot());
    }
    @Override public float getTurretTurnYSpeed() { return 0; }
    @Override public float getTurretTurnXSpeed() { return .9F; }
    @Override public float getTurretMinPitch() { return -3; }
    @Override public float getTurretMaxPitch() { return 25; }
    @Override public void adjustTurretAngle() {
        Entity driver = getNthEntity(0);
        if (driver == null || isWreck()) return;
        Vec3 aim = getBoundingBox().getCenter().add(driver.getViewVector(1).scale(512));
        turretAutoAimFromVector(aim.subtract(firingPivot()));
    }
    @Override public void turretAutoAimFromVector(Vec3 direction) {
        super.turretAutoAimFromVector(direction);
        setTurretYRot(0);
        setTurretXRot(Mth.clamp(getTurretXRot(), -25, 3));
    }
    public Matrix4d groupTransform(String group, float partial) {
        Matrix4d body = getVehicleTransform(partial);
        return group.equals("Main") ? body.mul(Project612Rig.INSTANCE.local(getTurretPitch(partial))) : body;
    }
    @Override public Matrix4d getTransformFromString(String name, float partial) {
        return name != null && name.startsWith("Project612") ? groupTransform(name.substring(10), partial) : super.getTransformFromString(name, partial);
    }
    @Override public Quaterniond getRotationFromString(String name, float partial) {
        return name != null && name.startsWith("Project612") ? groupTransform(name.substring(10), partial).getNormalizedRotation(new Quaterniond())
                : super.getRotationFromString(name, partial);
    }
    @Override public Quaterniond getRotationFromString(String name) {
        return name != null && name.startsWith("Project612") ? getRotationFromString(name, 1) : super.getRotationFromString(name);
    }
    Vec3 firingPivot() { return Project612Rig.point(getVehicleTransform(1), Project612Rig.INSTANCE.pivot); }
    @Override public Vec3 getShootPos(String weapon, float partial) {
        return Project612Rig.point(groupTransform("Main", partial), Project612Rig.INSTANCE.muzzle);
    }
    @Override public Vec3 getShootVec(String weapon, float partial) {
        return Project612Rig.direction(groupTransform("Main", partial), Project612Rig.INSTANCE.direction);
    }
    @Override public Vec3 getShootPos(Entity actor, float partial) { return getShootPos("Main", partial); }
    @Override public Vec3 getShootVec(Entity actor, float partial) { return getShootVec("Main", partial); }
    @Override public Vec3 getShootPosForHud(Entity actor, float partial) { return getShootPos("Main", partial); }
    @Override public Vec3 getShootDirectionForHud(Entity actor, float partial) { return getShootVec("Main", partial); }
    @Override public List<OBB> getOBBs() {
        List<OBB> source = super.getOBBs();
        if (source != nativeBoxes) { nativeBoxes = source; damageBoxes = source.stream().filter(b -> b.part != OBB.Part.COLLISION).toList(); }
        return damageBoxes;
    }
    @Override public OBB getCollisionOBB() { var info = getCollisionOBBInfo(); return info == null ? null : info.getOBB(); }

    @Override public void tick() {
        oldTrackL = entityData.get(TRACK_L); oldTrackR = entityData.get(TRACK_R);
        oldWheelL = entityData.get(WHEEL_L); oldWheelR = entityData.get(WHEEL_R);
        Vec3 previous = position();
        float priorYaw = getYRot();
        super.tick();
        if (isRemoved()) return;
        if (!level().isClientSide) {
            double turn = Math.toRadians(ApocalypseMotion.degrees(getYRot() - priorYaw));
            double heading = Math.toRadians(priorYaw) + turn * .5;
            Vec3 delta = position().subtract(previous);
            double travel = -Math.sin(heading) * delta.x + Math.cos(heading) * delta.z;
            if (delta.lengthSqr() < 16 && Math.abs(turn) < .5) {
                double halfTrack = Math.abs(Project612Rig.INSTANCE.tracks.get(0).x());
                gear(TRACK_L, WHEEL_L, travel - halfTrack * turn);
                gear(TRACK_R, WHEEL_R, travel + halfTrack * turn);
            }
        }
        updateOBB();
        refreshBoundingBoxFromOBBs();
    }
    private void gear(EntityDataAccessor<Float> track, EntityDataAccessor<Float> wheel, double distance) {
        var rig = Project612Rig.INSTANCE;
        entityData.set(track, (float) ApocalypseMotion.wrap(entityData.get(track) + distance / rig.tracks.get(0).length(), 1));
        entityData.set(wheel, (float) ApocalypseMotion.wrap(entityData.get(wheel) + Math.toDegrees(distance / rig.wheels.get(0).radius()), 360));
    }
    public float trackPhase(boolean left, float partial) {
        return ApocalypseMotion.interpolateCycle(left ? oldTrackL : oldTrackR, entityData.get(left ? TRACK_L : TRACK_R), partial, 1);
    }
    public float wheelAngle(boolean left, float partial) {
        return ApocalypseMotion.interpolateCycle(left ? oldWheelL : oldWheelR, entityData.get(left ? WHEEL_L : WHEEL_R), partial, 360);
    }
    @Override protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty() && super.m_7310_(passenger);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.m_7378_(tag);
        armament.invalidate();
    }
}
