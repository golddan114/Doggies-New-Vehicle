package com.liucodex.carriercube;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class CubeRecovery {
    private static final int CURVE_STEPS = 16;
    private static final int TURN_TICKS = 48;
    private static final int HOVER_RAMP_TICKS = 12;

    record Pose(Vec3 position, float yaw, float pitch, float roll) {}

    private static final double FINAL_DISTANCE=96, FINAL_HEIGHT=12, TURN_RADIUS=38, PATTERN_SPEED=1.24;
    private static final int ENTRY_TICKS=40;
    private final int routeVersion;
    private float entryPower=.83F;
    private final int patternTicks, glideTicks;
    private final double finalLength;
    private final Pose start;
    private final Vec3 bow;
    private final Vec3 parked;
    private final float shipYaw;
    private final List<Vec3> route;
    private final double[] distances;
    private final List<AABB> corridor;
    private final AABB clearance;
    private final Vec3 initialVelocity;
    private final double entrySpeed;
    private final double touchdownSpeed;
    private final double approachLength;
    private final int rampTicks;
    private final int approachTicks;
    private final int rolloutTicks;

    CubeRecovery(Pose start, Vec3 hull, float shipYaw, Vec3 velocity) {
        this(start,hull,shipYaw,velocity,4);
    }

    private CubeRecovery(Pose start,Vec3 hull,float shipYaw,Vec3 velocity,int routeVersion) {
        this.routeVersion=routeVersion;
        this.start = start;
        this.initialVelocity = velocity;
        this.shipYaw = shipYaw;
        Vec3 forward = CubeGeometry.forward(shipYaw);
        bow = CubeGeometry.rail(hull, forward, CubeGeometry.LAUNCH_TICKS);
        parked = CubeGeometry.rail(hull, forward, 0);
        double currentSpeed = velocity.length();
        rampTicks = currentSpeed < CubeGeometry.MIN_JET_CAPTURE_SPEED ? HOVER_RAMP_TICKS : 0;
        entrySpeed = routeVersion>=3 ? Math.min(currentSpeed,CubeGeometry.FLIGHT_SPEED) : rampTicks == 0 ? Math.min(currentSpeed, CubeGeometry.FLIGHT_SPEED) : .32;
        touchdownSpeed = routeVersion>=3 ? .34 : Math.min(entrySpeed, .34);
        route = routeVersion>=3 ? pattern(forward) : planApproach(forward);
        distances = new double[route.size()];
        var segments = new ArrayList<AABB>();
        AABB bounds = flightSweep(bow, parked);
        segments.add(bounds);
        for (int i = 1; i < route.size(); i++) {
            Vec3 from = route.get(i - 1);
            Vec3 to = route.get(i);
            distances[i] = distances[i - 1] + from.distanceTo(to);
            AABB segment = flightSweep(from, to);
            segments.add(segment);
            bounds = bounds.minmax(segment);
        }
        corridor = List.copyOf(segments);
        clearance = bounds;
        approachLength = distances[distances.length - 1];
        finalLength=routeVersion>=3 ? approachLength-distances[route.size()-CURVE_STEPS-1] : 0;
        double entryLength=ENTRY_TICKS*(entrySpeed+PATTERN_SPEED)/2;
        patternTicks=(int)Math.ceil(Math.max(0,approachLength-finalLength-entryLength)/PATTERN_SPEED);
        double finalSpeed = routeVersion >= 4 ? PATTERN_SPEED : (PATTERN_SPEED + touchdownSpeed) / 2;
        glideTicks=(int)Math.ceil(finalLength / finalSpeed);
        approachTicks=routeVersion>=3 ? ENTRY_TICKS+patternTicks+glideTicks :
                (int)Math.ceil(2*approachLength/(entrySpeed+touchdownSpeed))+rampTicks/2;
        rolloutTicks = (int) Math.ceil(2 * bow.distanceTo(parked) / touchdownSpeed);
    }

    void enterAtPower(float power) { entryPower=Mth.clamp(power,.25F,1F); }

    net.minecraft.nbt.CompoundTag save() {
        var state = new net.minecraft.nbt.CompoundTag();
        state.putInt("routeVersion",routeVersion);
        state.putFloat("entryPower",entryPower);
        state.put("start", CubeVehicle.vectorTag(start.position()));
        state.put("speed", CubeVehicle.vectorTag(initialVelocity));
        state.putFloat("yaw", start.yaw());
        state.putFloat("pitch", start.pitch());
        state.putFloat("roll", start.roll());
        return state;
    }

    static CubeRecovery load(net.minecraft.nbt.CompoundTag tag, Vec3 anchor, float yaw) {
        var start = new Pose(CubeVehicle.vector(tag.getCompound("start")), tag.getFloat("yaw"),
                tag.getFloat("pitch"), tag.getFloat("roll"));
        var plan=new CubeRecovery(start,anchor,yaw,CubeVehicle.vector(tag.getCompound("speed")),Math.max(1,tag.getInt("routeVersion")));
        if (tag.contains("entryPower")) plan.enterAtPower(tag.getFloat("entryPower"));
        return plan;
    }

    AABB clearance() {
        return clearance;
    }

    boolean intersects(AABB obstacle) {
        for (AABB segment : corridor) {
            if (segment.intersects(obstacle)) {
                return true;
            }
        }
        return false;
    }

    int approachTicks() {
        return approachTicks;
    }

    int rolloutEnd() {
        return approachTicks + rolloutTicks;
    }

    int duration() {
        return rolloutEnd() + TURN_TICKS;
    }

    Pose pose(int tick) {
        float inboundYaw = Mth.wrapDegrees(shipYaw + 180);
        if (tick < approachTicks) {
            // The slow entry spends the first ramp ticks accelerating before following the regular profile.
            double rampOffset = rampTicks * .5;
            double elapsed = tick < rampTicks ? (double) tick * tick / (2 * rampTicks) : tick - rampOffset;
            double t = elapsed / (approachTicks - rampOffset);
            double progress = t * (2 * entrySpeed + (touchdownSpeed - entrySpeed) * t)
                    / (entrySpeed + touchdownSpeed);
            double distance = routeVersion>=3 ? patternDistance(tick) : approachLength * progress;
            Vec3 point = routePoint(distance);
            double sample=routeVersion>=3 ? 4 : .5;
            Vec3 before = point.subtract(routePoint(distance - sample));
            Vec3 after = routePoint(distance + sample).subtract(point);
            Vec3 tangent = before.add(after);
            float yaw = heading(tangent);
            float pitch = (float) -Math.toDegrees(Math.atan2(tangent.y, tangent.horizontalDistance()));
            float bank = before.lengthSqr() > 1E-8 && after.lengthSqr() > 1E-8
                    ? Mth.clamp(3 * Mth.wrapDegrees(heading(after) - heading(before)), -25, 25) : 0;
            float blend = Math.min(1, tick / 10F);
            return new Pose(point, Mth.rotLerp(blend, start.yaw(), yaw),
                    Mth.lerp(blend, start.pitch(), pitch), Mth.lerp(blend, start.roll(), bank));
        }
        if (tick <= rolloutEnd()) {
            double t = (double) (tick - approachTicks) / rolloutTicks;
            return new Pose(bow.lerp(parked, t * (2 - t)), inboundYaw, 0, 0);
        }
        float t = (float) (tick - rolloutEnd()) / TURN_TICKS;
        return new Pose(parked, tick >= duration() ? shipYaw : Mth.rotLerp((float) CubeDeckStow.ease(t), inboundYaw, shipYaw), 0, 0);
    }

    float power(int tick) {
        if (routeVersion>=3 && tick<ENTRY_TICKS) return Mth.lerp((float)CubeDeckStow.ease(tick/(double)ENTRY_TICKS),entryPower,.83F);
        if (tick>=rolloutEnd()) return 0;
        if (routeVersion >= 4) {
            if (tick < approachTicks) return .83F;
            int groundTicks = tick - approachTicks;
            double arrest = CubeDeckStow.ease(Math.min(1, groundTicks / 8.0));
            double rollout = CubeDeckStow.ease(groundTicks / (double)rolloutTicks);
            return (float)((.83 - .55 * arrest) * (1 - rollout));
        }
        if (tick>=approachTicks) return (float)(.28*(1-CubeDeckStow.ease((tick-approachTicks)/(double)rolloutTicks)));
        double glide=routeVersion>=3 ? (tick-ENTRY_TICKS-patternTicks)/(double)glideTicks : tick/(double)approachTicks;
        return (float)(.83-.55*CubeDeckStow.ease(Mth.clamp(glide,0,1)));
    }

    private double patternDistance(int tick) {
        double entryLength=ENTRY_TICKS*(entrySpeed+PATTERN_SPEED)/2;
        if (tick<=ENTRY_TICKS) return tick*(entrySpeed+(PATTERN_SPEED-entrySpeed)*tick/(2*ENTRY_TICKS));
        int cruise=tick-ENTRY_TICKS;
        if (cruise<=patternTicks) return Mth.lerp(cruise/(double)patternTicks,entryLength,approachLength-finalLength);
        double t=(cruise-patternTicks)/(double)glideTicks;
        if (routeVersion >= 4) return Mth.lerp(t, approachLength-finalLength, approachLength);
        return approachLength-finalLength+finalLength*t*(2*PATTERN_SPEED+(touchdownSpeed-PATTERN_SPEED)*t)/(PATTERN_SPEED+touchdownSpeed);
    }

    // Join an outbound tangent, then fly a wide half-circle onto the final approach.
    // Both tangent circles use the same turn direction, so their straight join has continuous heading.
    private List<Vec3> pattern(Vec3 forward) {
        Vec3 origin=start.position(), across=new Vec3(forward.z,0,-forward.x);
        Vec3 gate=bow.add(forward.scale(FINAL_DISTANCE)).add(0,FINAL_HEIGHT,0);
        List<Vec3> best=null;
        double bestCost=Double.POSITIVE_INFINITY;
        double a=Math.toRadians(start.yaw()), b=Math.toRadians(shipYaw);
        for (int side:new int[]{-1,1}) {
            Vec3 outbound=gate.add(across.scale(side*2*TURN_RADIUS));
            for (int turn:new int[]{-1,1}) {
                Vec3 launchCenter=origin.add(normal(a).scale(turn*TURN_RADIUS));
                Vec3 joinCenter=outbound.add(normal(b).scale(turn*TURN_RADIUS));
                Vec3 line=joinCenter.subtract(launchCenter);
                double middle=line.horizontalDistance()<.001 ? a : Math.atan2(-line.x,line.z);
                var points=new ArrayList<Vec3>();
                points.add(origin);
                appendArc(points,launchCenter,a,middle,turn);
                Vec3 straightEnd=joinCenter.subtract(normal(middle).scale(turn*TURN_RADIUS));
                Vec3 from=points.get(points.size()-1);
                int steps=Math.max(1,(int)Math.ceil(from.subtract(straightEnd).horizontalDistance()/3));
                for(int i=1;i<=steps;i++) points.add(from.lerp(straightEnd,i/(double)steps));
                appendArc(points,joinCenter,middle,b,turn);
                double length=0;
                for(int i=1;i<points.size();i++) length+=points.get(i).subtract(points.get(i-1)).horizontalDistance();
                double cost=length, travelled=0;
                for(int i=1;i<points.size();i++) {
                    Vec3 next=points.get(i);
                    travelled+=next.subtract(points.get(i-1)).horizontalDistance();
                    double u=length<.001 ? 1 : travelled/length;
                    double y=Mth.lerp(CubeDeckStow.ease(u),origin.y,gate.y+6);
                    next=new Vec3(next.x,y,next.z);
                    points.set(i,next);
                    Vec3 rel=next.subtract(bow);
                    double along=rel.dot(forward);
                    if (along<0 && along>-2*CubeGeometry.HALF_LENGTH && Math.abs(rel.dot(across))<CubeGeometry.HALF_WIDTH+8 && y<bow.y+12) cost+=1000;
                }
                if (cost>=bestCost) continue;
                bestCost=cost;
                for(int i=1;i<=64;i++) {
                    double u=i/64.0, angle=Math.PI*u;
                    points.add(gate.add(forward.scale(TURN_RADIUS*Math.sin(angle)))
                            .add(across.scale(side*TURN_RADIUS*(1+Math.cos(angle))))
                            .add(0,6*(1-CubeDeckStow.ease(u)),0));
                }
                appendCurve(points,gate.subtract(forward.scale(FINAL_DISTANCE/3)),
                        bow.add(forward.scale(FINAL_DISTANCE/3)),bow);
                best=points;
            }
        }
        return List.copyOf(best);
    }

    private static Vec3 normal(double yaw) {return new Vec3(-Math.cos(yaw),0,-Math.sin(yaw));}

    private static void appendArc(List<Vec3> points,Vec3 center,double from,double to,int sign) {
        double sweep=sign*(to-from)%(2*Math.PI);
        if (sweep<0) sweep+=2*Math.PI;
        int steps=(int)Math.ceil(sweep*TURN_RADIUS/2);
        for(int i=1;i<=steps;i++) points.add(center.subtract(normal(from+sign*sweep*i/steps).scale(sign*TURN_RADIUS)));
    }

    private List<Vec3> planApproach(Vec3 forward) {
        boolean legacyRoute=routeVersion<2;
        Vec3 origin = start.position();
        Vec3 offset = origin.subtract(bow);
        double ahead = offset.dot(forward);
        Vec3 across = new Vec3(forward.z, 0, -forward.x);
        Vec3 heading = Vec3.directionFromRotation(0, start.yaw());
        double entryDistance = legacyRoute ? Mth.clamp(ahead*.55,4,20) : 32;
        double entryHeight = legacyRoute ? Math.min(Mth.clamp(offset.y*.3,1.5,4.5),entryDistance*.45) : 8;
        Vec3 entry = bow.add(forward.scale(entryDistance)).add(0, entryHeight, 0);
        double heightChange = Math.abs(offset.y - entryHeight);
        var points = new ArrayList<Vec3>();
        points.add(origin);
        if (!legacyRoute && ahead < 8) {
            // Go outside the ship's beam before crossing the bow plane, including arrivals from astern.
            double lateral = offset.dot(across);
            double side = Math.copySign(1, Math.abs(lateral) < 1 ? heading.dot(across) : lateral);
            double lane = Math.max(CubeGeometry.HALF_WIDTH + 16, Math.abs(lateral));
            double transitY = Math.max(origin.y, bow.y + 16);
            Vec3 outside = origin.add(across.scale(side * lane - lateral));
            // Stay at entry altitude until the aircraft is clear of the ship's side.
            Vec3 join = bow.add(forward.scale(54)).add(across.scale(side * lane)).add(0, transitY - bow.y, 0);
            appendCurve(points, origin.lerp(outside, .35), outside.subtract(forward.scale(5)), outside);
            appendCurve(points, outside.add(forward.scale(10)), join.subtract(forward.scale(12)), join);
            appendCurve(points, join.add(forward.scale(12)), entry.add(forward.scale(14)), entry);
        } else if (heading.dot(forward) > -.7 || heightChange > (ahead - entryDistance) * .55) {
            double side = heading.dot(across);
            double lateral = offset.dot(across);
            double turnSide = Math.copySign(1, Math.abs(side) > .1 ? side : lateral);
            // Keep altitude in the outside turn and leave room to climb or descend on the inbound leg.
            double turnDistance = legacyRoute ? Math.max(Math.max(24,ahead+12),entryDistance+3*heightChange)
                    : Math.max(Math.min(160,Math.max(24,ahead+12)),entryDistance+Math.min(100,2*heightChange));
            Vec3 turn = bow.add(forward.scale(turnDistance))
                    .add(across.scale(turnSide * Math.max(12, Math.abs(lateral) + 7)))
                    .add(0, offset.y, 0);
            double lead = Math.min(10, ahead * .6);
            appendCurve(points, origin.add(heading.scale(lead)), turn.add(forward.scale(8)), turn);
            double join = Math.min(7, (turn.subtract(entry).dot(forward)) * .32);
            appendCurve(points, turn.subtract(forward.scale(join)), entry.add(forward.scale(join)), entry);
        } else {
            double join = Math.min(8, (ahead - entryDistance) * .40);
            appendCurve(points, origin.add(heading.scale(join)), entry.add(forward.scale(join)), entry);
        }
        double glide = entryDistance * .33;
        appendCurve(points, entry.subtract(forward.scale(glide)), bow.add(forward.scale(glide)), bow);
        return List.copyOf(points);
    }

    private static void appendCurve(List<Vec3> points, Vec3 control1, Vec3 control2, Vec3 end) {
        Vec3 start = points.get(points.size() - 1);
        for (int i = 1; i <= CURVE_STEPS; i++) {
            double t = (double) i / CURVE_STEPS;
            Vec3 middle = control1.lerp(control2, t);
            Vec3 a = start.lerp(control1, t).lerp(middle, t);
            Vec3 b = middle.lerp(control2.lerp(end, t), t);
            points.add(i == CURVE_STEPS ? end : a.lerp(b, t));
        }
    }

    private Vec3 routePoint(double distance) {
        double bounded = Mth.clamp(distance, 0, approachLength);
        int index = Arrays.binarySearch(distances, bounded);
        if (index >= 0) {
            return route.get(index);
        }
        int end = -index - 1;
        int start = end - 1;
        double progress = (bounded - distances[start]) / (distances[end] - distances[start]);
        return route.get(start).lerp(route.get(end), progress);
    }

    private static float heading(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    private static AABB flightSweep(Vec3 from, Vec3 to) {
        return CubeGeometry.aircraftSweep(from, to).inflate(.6, .4, .6);
    }
}
