package com.liucodex.doggiesnewvehicle.boss;

/** Pure boss transition geometry shared by collision projection and client presentation. */
public final class DnvVehicleTransitionTimeline {
    private static final float HATCH_OPEN_START = 20.0F;
    private static final float HATCH_OPEN_END = 60.0F;
    private static final float HATCH_CLOSE_START = 250.0F;
    private static final float HATCH_CLOSE_END = 290.0F;
    private static final float NEEDLE_FIRST_START_TICK = 70.0F;
    private static final float NEEDLE_PAIR_INTERVAL_TICKS = 30.0F;
    private static final float NEEDLE_EJECT_TICKS = 30.0F;
    private static final float NEEDLE_SETTLE_TICKS = 90.0F;
    private static final float NEEDLE_UPRIGHT_DELAY_TICKS = 66.0F;
    private static final float NEEDLE_UPRIGHT_TICKS = 54.0F;
    private static final int HALO_RING_VISIBLE_TICK = 330;
    private static final float HALO_RING_FADE_TICKS = 30.0F;

    private DnvVehicleTransitionTimeline() {
    }

    public static boolean haloNeedleVisible(
            boolean phaseTwo,
            boolean transitioning,
            int transitionTick,
            int index) {
        return phaseTwo || transitioning && transitionTick >= needleStartTick(index);
    }

    public static boolean haloRingVisible(
            boolean phaseTwo,
            boolean transitioning,
            int transitionTick) {
        return phaseTwo || transitioning && transitionTick >= HALO_RING_VISIBLE_TICK;
    }

    public static boolean haloRotationActive(
            boolean phaseTwo,
            boolean transitioning,
            float transitionTick) {
        return phaseTwo || transitioning && transitionTick >= HALO_RING_VISIBLE_TICK;
    }

    public static float hatchOpenProgress(
            boolean phaseTwo,
            boolean transitioning,
            float transitionTick) {
        if (phaseTwo || !transitioning || transitionTick < HATCH_OPEN_START) {
            return 0.0F;
        }
        if (transitionTick < HATCH_OPEN_END) {
            return smoothProgress(
                    (transitionTick - HATCH_OPEN_START) / (HATCH_OPEN_END - HATCH_OPEN_START));
        }
        if (transitionTick < HATCH_CLOSE_START) {
            return 1.0F;
        }
        return 1.0F - smoothProgress(
                (transitionTick - HATCH_CLOSE_START) / (HATCH_CLOSE_END - HATCH_CLOSE_START));
    }

    public static float haloRingOpacity(
            boolean phaseTwo,
            boolean transitioning,
            float transitionTick) {
        if (phaseTwo) {
            return 1.0F;
        }
        if (!transitioning) {
            return 0.0F;
        }
        return smoothProgress(
                (transitionTick - HALO_RING_VISIBLE_TICK) / HALO_RING_FADE_TICKS);
    }

    public static float needleStartTick(int index) {
        if (index < 0 || index >= 6) {
            throw new IllegalArgumentException("Needle index out of range: " + index);
        }
        return NEEDLE_FIRST_START_TICK + (index % 3) * NEEDLE_PAIR_INTERVAL_TICKS;
    }

    public static float needleEjectProgress(float transitionTick, int index) {
        return smoothProgress((transitionTick - needleStartTick(index)) / NEEDLE_EJECT_TICKS);
    }

    public static float needleSettleProgress(float transitionTick, int index) {
        return smoothProgress((transitionTick - needleStartTick(index) - NEEDLE_EJECT_TICKS)
                / NEEDLE_SETTLE_TICKS);
    }

    public static float needleUprightProgress(float transitionTick, int index) {
        return smoothProgress((transitionTick - needleStartTick(index)
                - NEEDLE_UPRIGHT_DELAY_TICKS) / NEEDLE_UPRIGHT_TICKS);
    }

    public static float needleDockTick(int index) {
        return needleStartTick(index) + NEEDLE_EJECT_TICKS + NEEDLE_SETTLE_TICKS;
    }

    public static float smoothProgress(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
