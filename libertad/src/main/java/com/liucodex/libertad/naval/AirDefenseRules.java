package com.liucodex.libertad.naval;

/** Fixed firing cadence and the four authored deck sectors. */
public final class AirDefenseRules {
    public static final int ZONES = 4, PULSE_TICKS = 8, RANGE = 150;
    public static final float PULSE_DAMAGE = 45;
    public static final float FIRE_VOLUME = .75F;
    public static final int FIRE_SOUND_RANGE = 200;

    private AirDefenseRules() {}

    public static boolean enabled(boolean multicrew, boolean secondaryActive, boolean powered) {
        return powered && (multicrew || secondaryActive);
    }

    public static boolean nearSuperstructure(double x, double y, double z) {
        // Close defense around the central upper works, including a margin over the roofs.
        return Math.abs(x) <= 6 && Math.abs(z) <= 30 && y >= 3 && y <= 18;
    }

    public static boolean covers(int zone, double localX, double relativeX, double relativeY, double relativeZ,
                                 boolean closeDefense) {
        if (relativeX * relativeX + relativeY * relativeY + relativeZ * relativeZ > RANGE * RANGE) return false;
        double pitch = Math.toDegrees(Math.atan2(relativeY, Math.hypot(relativeX, relativeZ)));
        if (pitch < -22) return false;
        if (closeDefense) return true;
        double side = (zone & 1) == 0 ? 1 : -1;
        if (localX * side < 0 || (localX == 0 && side < 0)) return false;
        if (pitch > 80) return true;
        double yaw = Math.toDegrees(Math.atan2(relativeX * side, relativeZ));
        return zone < 2 ? yaw >= -18 && yaw <= 146 : yaw >= 38 || yaw <= -160;
    }

}
