package com.liucodex.noise.entity;

/** Pure timing and motion curve for Noise's vertical drop-rig entrance. */
public final class NoiseDropTimeline {
    public static final int SLAM_END_TICK = 48;
    public static final int BRAKE_END_TICK = 55;
    public static final int RELEASE_END_TICK = 66;
    public static final int LIFT_END_TICK = 78;
    public static final int TOTAL_TICKS = 140;
    public static final int ACTIVATION_TICK = TOTAL_TICKS - 1;

    private static final double RIG_START_OFFSET = 52.277664D;
    private static final double RIG_SLAM_OFFSET = 9.777664D;
    private static final double RIG_GROUND_OFFSET = 8.277664D;
    private static final double RIG_LIFT_OFFSET = 12.277664D;
    private static final double RIG_END_OFFSET = 70.277664D;

    private static final double VEHICLE_START_OFFSET = 48.0D;
    private static final double VEHICLE_SLAM_OFFSET = 5.5D;
    private static final double VEHICLE_CARRIED_OFFSET = 4.0D;
    // Verified maximum Y of the five SHM carrier parts, plus clearance below the ship.
    public static final double RIG_TOP = 3.454667D;
    public static final double RIG_HALF_WIDTH = 4.712681D;
    public static final double RIG_HALF_LENGTH = 6.082219D;
    private static final double CARRIER_SEPARATION = RIG_START_OFFSET - VEHICLE_START_OFFSET;

    public static float mothershipStartOffset(double bottomAboveGround) {
        double start = bottomAboveGround - CARRIER_SEPARATION - RIG_TOP - 0.75D;
        return !Double.isFinite(start) || start < VEHICLE_SLAM_OFFSET ? -1F : (float)Math.min(48, start);
    }

    public static float sanitizeStartOffset(float value) {
        return Float.isFinite(value) && value >= VEHICLE_SLAM_OFFSET && value <= 48 ? value : -1F;
    }

    public static double vehicleVerticalOffset(double elapsed, float start) {
        if (sanitizeStartOffset(start) < 0) return vehicleVerticalOffset(elapsed);
        return elapsed <= SLAM_END_TICK
                ? lerp(easeOutQuint(progress(elapsed, 0, SLAM_END_TICK)), start, VEHICLE_SLAM_OFFSET)
                : vehicleVerticalOffset(elapsed);
    }

    public static double rigVerticalOffset(double elapsed, float start) {
        if (sanitizeStartOffset(start) < 0) return rigVerticalOffset(elapsed);
        if (elapsed <= BRAKE_END_TICK) return vehicleVerticalOffset(elapsed, start) + CARRIER_SEPARATION;
        if (elapsed <= RELEASE_END_TICK) return RIG_GROUND_OFFSET;
        double end = start + CARRIER_SEPARATION;
        double lift = Math.min(RIG_LIFT_OFFSET, end);
        return elapsed <= LIFT_END_TICK
                ? lerp(easeOutCubic(progress(elapsed, RELEASE_END_TICK, LIFT_END_TICK)), RIG_GROUND_OFFSET, lift)
                : lerp(easeInCubic(progress(elapsed, LIFT_END_TICK, ACTIVATION_TICK)), lift, end);
    }

    private NoiseDropTimeline() {
    }

    public static boolean isRigVisible(double elapsedTicks) {
        return elapsedTicks >= 0 && elapsedTicks < ACTIVATION_TICK;
    }

    public static boolean shouldActivate(int elapsedTicks) {
        return elapsedTicks == ACTIVATION_TICK;
    }

    public static double renderElapsedTicks(int remainingTicks, float partialTick) {
        double elapsed = TOTAL_TICKS - remainingTicks + clamp(partialTick);
        return Math.max(0.0D, Math.min(ACTIVATION_TICK, elapsed));
    }

    public static double rigVerticalOffset(double elapsedTicks) {
        if (elapsedTicks <= SLAM_END_TICK) {
            double progress = progress(elapsedTicks, 0, SLAM_END_TICK);
            return lerp(easeOutQuint(progress), RIG_START_OFFSET, RIG_SLAM_OFFSET);
        }
        if (elapsedTicks <= BRAKE_END_TICK) {
            double progress = progress(elapsedTicks, SLAM_END_TICK, BRAKE_END_TICK);
            return lerp(easeOutCubic(progress), RIG_SLAM_OFFSET, RIG_GROUND_OFFSET);
        }
        if (elapsedTicks <= RELEASE_END_TICK) {
            return RIG_GROUND_OFFSET;
        }
        if (elapsedTicks <= LIFT_END_TICK) {
            double progress = progress(elapsedTicks, RELEASE_END_TICK, LIFT_END_TICK);
            return lerp(easeOutCubic(progress), RIG_GROUND_OFFSET, RIG_LIFT_OFFSET);
        }
        double progress = progress(elapsedTicks, LIFT_END_TICK, ACTIVATION_TICK);
        return lerp(easeInCubic(progress), RIG_LIFT_OFFSET, RIG_END_OFFSET);
    }

    public static double vehicleVerticalOffset(double elapsedTicks) {
        if (elapsedTicks <= SLAM_END_TICK) {
            double progress = progress(elapsedTicks, 0, SLAM_END_TICK);
            return lerp(easeOutQuint(progress), VEHICLE_START_OFFSET, VEHICLE_SLAM_OFFSET);
        }
        if (elapsedTicks <= BRAKE_END_TICK) {
            double progress = progress(elapsedTicks, SLAM_END_TICK, BRAKE_END_TICK);
            return lerp(easeOutCubic(progress), VEHICLE_SLAM_OFFSET, VEHICLE_CARRIED_OFFSET);
        }
        if (elapsedTicks <= RELEASE_END_TICK) {
            double progress = progress(elapsedTicks, BRAKE_END_TICK, RELEASE_END_TICK);
            return lerp(easeInCubic(progress), VEHICLE_CARRIED_OFFSET, 0.0D);
        }
        return 0.0D;
    }

    /** Normalized engine output used to scale the four downward exhaust plumes. */
    public static double exhaustStrength(int elapsedTicks) {
        if (elapsedTicks <= SLAM_END_TICK) {
            double progress = progress(elapsedTicks, 0, SLAM_END_TICK);
            return lerp(easeOutQuint(progress), 1.0D, 0.28D);
        }
        if (elapsedTicks <= BRAKE_END_TICK) {
            double progress = progress(elapsedTicks, SLAM_END_TICK, BRAKE_END_TICK);
            return lerp(easeOutCubic(progress), 0.28D, 0.16D);
        }
        if (elapsedTicks <= RELEASE_END_TICK) {
            double progress = progress(elapsedTicks, BRAKE_END_TICK, RELEASE_END_TICK);
            return lerp(progress, 0.16D, 0.12D);
        }
        if (elapsedTicks <= LIFT_END_TICK) {
            double progress = progress(elapsedTicks, RELEASE_END_TICK, LIFT_END_TICK);
            return lerp(easeOutCubic(progress), 0.40D, 0.68D);
        }
        double progress = progress(elapsedTicks, LIFT_END_TICK, ACTIVATION_TICK);
        return lerp(easeInCubic(progress), 0.68D, 1.0D);
    }

    private static double progress(double value, int start, int end) {
        if (end <= start) {
            return value >= end ? 1.0D : 0.0D;
        }
        return clamp((value - start) / (double) (end - start));
    }

    private static double easeOutQuint(double value) {
        return 1.0D - Math.pow(1.0D - value, 5.0D);
    }

    private static double easeOutCubic(double value) {
        return 1.0D - Math.pow(1.0D - value, 3.0D);
    }

    private static double easeInCubic(double value) {
        return value * value * value;
    }

    private static double lerp(double progress, double start, double end) {
        return start + (end - start) * progress;
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
