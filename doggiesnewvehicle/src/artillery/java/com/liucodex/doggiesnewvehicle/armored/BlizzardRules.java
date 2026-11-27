package com.liucodex.doggiesnewvehicle.armored;

/** Deterministic hover and pulse accounting, independent of client frame rate. */
public final class BlizzardRules {
    public static final int DAMAGE_INTERVAL = 10;
    public static final int MODE2_CHARGE_TICKS = 60;
    public static final int MODE2_COOLDOWN_TICKS = 40;
    public static final int MODE2_DECAY_TICKS = 120;
    public static final int TRAIL_TICKS = 24;
    public static final float MODE2_DAMAGE_SCALE = 21F;
    public static final int PULSE_FE = 3_000;
    private static final int MODE2_PULSE_FE = 75_000;
    public static final int HOVER_FE = 16;
    public static final int DRIVE_FE = 128;
    public static final int ACCELERATION_TICKS = 40;
    public static final double BOLT_SPEED = 8;
    public static final double RANGE = 256;
    public static final int BOLT_LIFE = (int)Math.ceil(RANGE / BOLT_SPEED);
    public static final double MODE2_RANGE = 80;
    public static final int JUMP_FE = 100_000;
    public static final int JUMP_COOLDOWN = 60 * 20;
    public static final int JUMP_HOLD_TICKS = 5 * 20;
    // Pinned SBW T90A, level ground, no sprint: drag .79 and thrust .15 * rate.
    public static final double FORWARD_SPEED = .15 * .78 / (1 - .79) - 1.0 / 20;
    public static final double REVERSE_SPEED = .15 * .50 / (1 - .79) + 1.0 / 20;
    private BlizzardRules() { }

    public static int pulseCost(boolean mode2) {
        return mode2 ? MODE2_PULSE_FE : PULSE_FE;
    }
    public static int motionCost(int driveTicks, boolean driving) {
        return HOVER_FE + (driving ? (int) Math.ceil(DRIVE_FE * throttle(driveTicks)) : 0);
    }

    /** Ground is optional: an unloaded edge/void cannot create a support surface. */
    public static double vertical(double velocity, double hullY, double groundY, boolean powered) {
        return vertical(velocity, hullY, groundY, powered, .5);
    }
    public static double vertical(double velocity, double hullY, double groundY, boolean powered, double height) {
        if (!powered || !Double.isFinite(groundY)) return Math.max(-.8, velocity - .04);
        double error = groundY + height - hullY;
        double result = velocity * .55 + Math.max(-.12, Math.min(.12, error * .14));
        return Math.max(-.3, Math.min(.3, result));
    }

    /** Same expm1 curve as Executor; shorter ramp for a player-operated tank. */
    public static double throttle(int ticks) {
        return Math.expm1(3 * Math.max(0, Math.min(ACCELERATION_TICKS, ticks)) / (double) ACCELERATION_TICKS) / Math.expm1(3);
    }

    public static double horizontal(double current, int input, boolean powered, int ticks, boolean supported) {
        if (!powered || input == 0 || current * input < -.002) {
            double next = current * (supported ? .55 : .88);
            return Math.abs(next) < .002 ? 0 : next;
        }
        double desired = (input > 0 ? FORWARD_SPEED : -REVERSE_SPEED) * throttle(ticks);
        // Throttle requests thrust; collision/side slip cannot be replaced by an arbitrary target velocity.
        return current + Math.max(-.025, Math.min(.025, desired - current));
    }

    public static double lateralRetention(boolean supported, boolean powered) {
        return supported ? powered ? .45 : .25 : .82;
    }

    /** Responsive pivoting with a lower turn rate at speed and a short release ramp. */
    public static float steering(float rate, int input, double speed, boolean powered) {
        double fraction = Math.min(1, Math.abs(speed) / FORWARD_SPEED);
        float target = powered ? input * (float) (3.2 - 1.2 * fraction) : 0;
        float delta = Math.max(-.45F, Math.min(.45F, target - rate));
        return rate + delta;
    }

    public static float attitudeRate(float angle, float rate, double target) {
        return (float) Math.max(-1.2, Math.min(1.2, rate * .62 + (target - angle) * .12));
    }

    public static double terrainAngle(double a, double b, double span) {
        return Double.isFinite(a) && Double.isFinite(b) ? Math.toDegrees(Math.atan2(a - b, span)) : 0;
    }
}
