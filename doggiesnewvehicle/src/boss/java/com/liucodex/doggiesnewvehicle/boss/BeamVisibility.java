package com.liucodex.doggiesnewvehicle.boss;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared boss light-ray culling; cosmetic only, never a weapon range or damage query. */
public final class BeamVisibility {
    public static final double VIEW_DISTANCE = 256;
    private BeamVisibility() { }

    public static double distanceSquared(Vec3 camera, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double lengthSquared = delta.lengthSqr();
        if (!Double.isFinite(lengthSquared)) return Double.POSITIVE_INFINITY;
        double t = lengthSquared < 1.0E-12 ? 0
                : Math.max(0, Math.min(1, camera.subtract(start).dot(delta) / lengthSquared));
        return camera.distanceToSqr(start.add(delta.scale(t)));
    }

    public static boolean nearby(Vec3 camera, Vec3 start, Vec3 end) {
        return distanceSquared(camera, start, end) < VIEW_DISTANCE * VIEW_DISTANCE;
    }

    public static AABB bounds(Vec3 start, Vec3 end) {
        return new AABB(start, end).inflate(1);
    }
}
