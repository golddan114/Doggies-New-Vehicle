package com.liucodex.doggiesnewvehicle.core;

/** Damage arithmetic for one exact SBW Annihilator pulse tick. The entity owns the running budget. */
public final class MothershipAnnihilatorDefense {
    public static final float LASER_MULTIPLIER = 0.02F;
    public static final float MAX_TICK_DAMAGE = 80.0F;

    private MothershipAnnihilatorDefense() { }

    public static float laser(float raw) {
        return Float.isFinite(raw) && raw > 0 ? raw * LASER_MULTIPLIER : 0;
    }

    public static float withinTick(float alreadyApplied, float requested) {
        if (!Float.isFinite(requested) || requested <= 0) return 0;
        float remaining = Math.max(0, MAX_TICK_DAMAGE - Math.max(0, alreadyApplied));
        return Math.min(requested, remaining);
    }
}
