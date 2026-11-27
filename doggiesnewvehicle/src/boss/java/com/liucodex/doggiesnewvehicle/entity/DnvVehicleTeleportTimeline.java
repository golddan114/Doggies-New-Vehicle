package com.liucodex.doggiesnewvehicle.entity;

/** Shared pure eight-second teleport timeline for server logic and vehicle renderers. */
public final class DnvVehicleTeleportTimeline {
    public static final int CHARGE_TICKS = 60;
    public static final int PRE_TELEPORT_HIDDEN_TICKS = 40;
    public static final int RECOVERY_TICKS = 60;
    public static final int ARRIVAL_HIDDEN_TICKS = 5;
    public static final int TOTAL_TICKS =
            CHARGE_TICKS + PRE_TELEPORT_HIDDEN_TICKS + RECOVERY_TICKS;
    public static final int DISAPPEAR_REMAINING_TICKS =
            PRE_TELEPORT_HIDDEN_TICKS + RECOVERY_TICKS;
    public static final int TELEPORT_REMAINING_TICKS = RECOVERY_TICKS;
    public static final int ARRIVAL_VISIBLE_REMAINING_TICKS =
            RECOVERY_TICKS - ARRIVAL_HIDDEN_TICKS;

    private DnvVehicleTeleportTimeline() {
    }

    public static boolean isActive(int remainingTicks) {
        return remainingTicks > 0;
    }

    public static boolean isHidden(int remainingTicks) {
        return remainingTicks > ARRIVAL_VISIBLE_REMAINING_TICKS
                && remainingTicks <= DISAPPEAR_REMAINING_TICKS;
    }

    public static float whiteOverlayProgress(int remainingTicks) {
        if (remainingTicks > DISAPPEAR_REMAINING_TICKS) {
            float elapsed = TOTAL_TICKS - remainingTicks;
            return clamp(elapsed / CHARGE_TICKS);
        }
        if (remainingTicks > 0
                && remainingTicks <= ARRIVAL_VISIBLE_REMAINING_TICKS) {
            return clamp(remainingTicks / (float) ARRIVAL_VISIBLE_REMAINING_TICKS);
        }
        return 0.0F;
    }

    public static int clampRemaining(int remainingTicks) {
        return Math.max(0, Math.min(TOTAL_TICKS, remainingTicks));
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
