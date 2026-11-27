package com.liucodex.doggiesnewvehicle.boss;

import net.minecraft.world.phys.Vec3;

/** Final physical barrel check; a target solution cannot turn a projectile behind the visible gun. */
public final class DnvBossMainAim {
    private DnvBossMainAim() { }

    public static boolean aligned(float yaw, float pitch, Vec3 direction, double distance) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || direction == null
                || !Double.isFinite(distance) || distance <= 0 || !Double.isFinite(direction.lengthSqr())
                || direction.lengthSqr() < 1E-8) return false;
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        Vec3 forward = new Vec3(-Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p));
        // Authored twin muzzles converge from either side of the core; allowance shrinks with range.
        double allowance = Math.min(Math.toRadians(25), Math.atan2(4, distance) + Math.toRadians(2));
        return forward.dot(direction.normalize()) >= Math.cos(allowance);
    }
}
