package com.liucodex.silentheaven.entity;

/** Pure health boundary for the two Silent Heaven lives. */
public final class SilentHeavenPhaseHealth {
    public static final float FIRST_CAPACITY = 4_000.0F;
    public static final float SECOND_CAPACITY = 3_000.0F;
    public static final float TRANSITION_SURVIVAL = 1.0F;

    private SilentHeavenPhaseHealth() { }

    public static float capacity(int phase) {
        return phase == 0 ? FIRST_CAPACITY : SECOND_CAPACITY;
    }

    public static float beforeTransitionDamage(float health) {
        if (!Float.isFinite(health) || health <= 0) return 0;
        // Keep the native vehicle alive while probing whether this hit actually changes health.
        return health > TRANSITION_SURVIVAL ? health - TRANSITION_SURVIVAL : health * 0.5F;
    }

    public static float barProgress(int phase, int transitionTick, int healStartTick, float health) {
        if (phase == 1 && transitionTick < healStartTick) return 0;
        if (!Float.isFinite(health)) return 0;
        return Math.max(0, Math.min(1, health / capacity(phase)));
    }
}
