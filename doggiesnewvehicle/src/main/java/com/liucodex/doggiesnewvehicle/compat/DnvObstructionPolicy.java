package com.liucodex.doggiesnewvehicle.compat;

/** Shared staged recovery thresholds for obstructed autonomous bosses. */
public final class DnvObstructionPolicy {
    public static final int REPOSITION_AFTER_TICKS = 10;
    public static final int RETARGET_AFTER_TICKS = 60;
    public static final int TELEPORT_AFTER_TICKS = 200;
    public static final int RETARGET_INTERVAL_TICKS = 20;
    public static final float MANEUVER_YAW_DEGREES = 38.0F;

    private DnvObstructionPolicy() {
    }

    public static Stage stage(int obstructedTicks) {
        int ticks = Math.max(0, obstructedTicks);
        if (ticks >= TELEPORT_AFTER_TICKS) {
            return Stage.TELEPORT;
        }
        if (ticks >= RETARGET_AFTER_TICKS) {
            return Stage.RETARGET;
        }
        if (ticks >= REPOSITION_AFTER_TICKS) {
            return Stage.REPOSITION;
        }
        return ticks == 0 ? Stage.CLEAR : Stage.AIM_ADJUST;
    }

    public static boolean shouldReposition(int obstructedTicks) {
        return stage(obstructedTicks).ordinal() >= Stage.REPOSITION.ordinal();
    }

    public static boolean shouldRetarget(int obstructedTicks) {
        Stage stage = stage(obstructedTicks);
        return stage == Stage.RETARGET || stage == Stage.TELEPORT;
    }

    public static boolean shouldTeleport(int obstructedTicks) {
        return stage(obstructedTicks) == Stage.TELEPORT;
    }

    /** Alternates the circling direction slowly so a blocked hull can unstick. */
    public static float maneuverYawOffset(int entityId, int obstructedTicks) {
        int phase = Math.floorDiv(Math.max(0, obstructedTicks), 40);
        return Math.floorMod(entityId + phase, 2) == 0
                ? MANEUVER_YAW_DEGREES
                : -MANEUVER_YAW_DEGREES;
    }

    public enum Stage {
        CLEAR,
        AIM_ADJUST,
        REPOSITION,
        RETARGET,
        TELEPORT
    }
}
