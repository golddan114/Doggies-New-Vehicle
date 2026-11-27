package com.liucodex.doggiesnewvehicle.boss;

/** Geometry and distance units shared by the two existing tracked BOSS vehicles. */
public final class DnvBossRunningGear {
    public static final int SHOE_COUNT = 58;
    // Preserve the stored phase cycle; one phase unit now spans two smaller shoes.
    public static final float PHASE_PERIOD = 29.0F;
    public static final double HALF_WIDTH = 1.7925D;
    public static final double WHEEL_Y = 0.719814D;
    public static final double FRONT_Z = 2.033866D;
    public static final double REAR_Z = -2.33907D;
    public static final double WHEEL_RADIUS = 0.641715D;
    public static final double PATH_RADIUS = 0.675D;
    public static final double STRAIGHT = FRONT_Z - REAR_Z;
    public static final double PERIMETER = 2.0D * STRAIGHT + 2.0D * Math.PI * PATH_RADIUS;
    public static final double PHASE_DISTANCE = PERIMETER / PHASE_PERIOD;
    public static final float WHEEL_DEGREES_PER_PHASE =
            (float) Math.toDegrees(PHASE_DISTANCE / WHEEL_RADIUS);
    public static final double SHOE_WIDTH = 0.68D;
    public static final double SHOE_THICKNESS = 0.04D;
    public static final double SHOE_LENGTH = PERIMETER / SHOE_COUNT * 0.96D;

    private DnvBossRunningGear() {}

    public static Pose shoePose(int index, double phase) {
        return atDistance(index * PERIMETER / SHOE_COUNT + phase * PHASE_DISTANCE);
    }

    /** Bottom runs toward -Z; positive X rotation follows the end-wheel arc. */
    public static Pose atDistance(double distance) {
        double s = distance - Math.floor(distance / PERIMETER) * PERIMETER;
        if (s < STRAIGHT) {
            return new Pose(WHEEL_Y - PATH_RADIUS, FRONT_Z - s, 0.0D);
        }
        s -= STRAIGHT;
        if (s < Math.PI * PATH_RADIUS) {
            double angle = s / PATH_RADIUS;
            return new Pose(WHEEL_Y - PATH_RADIUS * Math.cos(angle),
                    REAR_Z - PATH_RADIUS * Math.sin(angle), angle);
        }
        s -= Math.PI * PATH_RADIUS;
        if (s < STRAIGHT) {
            return new Pose(WHEEL_Y + PATH_RADIUS, REAR_Z + s, Math.PI);
        }
        s -= STRAIGHT;
        double angle = s / PATH_RADIUS;
        return new Pose(WHEEL_Y + PATH_RADIUS * Math.cos(angle),
                FRONT_Z + PATH_RADIUS * Math.sin(angle), Math.PI + angle);
    }

    public record Pose(double y, double z, double pitchRadians) {}
}
