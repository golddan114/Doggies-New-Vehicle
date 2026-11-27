package com.liucodex.doggiesnewvehicle.armored;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Server flight limits, applied even after manual TV exit. */
public final class BlizzardMissileFlight {
    public static final int LIFE = 20 * 20, RELOAD = 20 * 20;
    public static final double SPEED = 12.0 / 20, DISTANCE = SPEED * LIFE;
    public static final int LIQUID_RADIUS = 10;
    public static final int SCENT_RADIUS = LIQUID_RADIUS - 4;
    private BlizzardMissileFlight() { }
    public static boolean validAngles(float yaw, float pitch) { return Float.isFinite(yaw) && Float.isFinite(pitch); }
    public static float turn(float current, float requested) {
        return Mth.wrapDegrees(current + Mth.clamp(Mth.wrapDegrees(requested - current), -6, 6));
    }
    public static Vec3 velocity(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)).scale(SPEED);
    }
    public static boolean expired(int age, double travelled) { return age >= LIFE || travelled >= DISTANCE - 1e-9; }
}
