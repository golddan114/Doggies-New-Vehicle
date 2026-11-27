package com.liucodex.doggiesnewvehicle.armored;

/** Pure bounded controls; the native SBW chassis remains the movement/collision executor. */
public final class ApocalypseMotion {
    public static final double FORWARD_SPEED = .30; // 6 blocks/s: 2 slower than allied AI, before terrain/part damage.
    public static final double REVERSE_SPEED = .15; // 3 blocks/s: 1 slower than allied AI.
    public static final float HULL_TURN = .65F; // 13 degrees/s, a 30% increase.
    public static final float TURRET_TURN = .9F;
    public static final float ELEVATION_SPEED = .9F;
    private ApocalypseMotion() { }

    public static double wrap(double value, double period) { return (value % period + period) % period; }
    public static float degrees(float value) { return (float) wrap(value + 180, 360) - 180; }
    public static float floor(float relativeYaw, boolean rocket) {
        float transition = Math.max(0, Math.min(1, (Math.abs(degrees(relativeYaw)) - 120) / 20));
        return (rocket ? -3 : -5) + 5 * transition;
    }
    public static float elevation(float requested, float relativeYaw, boolean rocket) {
        if (!Float.isFinite(requested) || !Float.isFinite(relativeYaw)) return rocket ? 2 : 0;
        return Math.max(floor(relativeYaw, rocket), Math.min(rocket ? 10 : 25, requested));
    }
    public static float approach(float current, float requested, float step) {
        return current + Math.max(-step, Math.min(step, requested - current));
    }
    public static boolean cleared(float yaw, float main, float rocket) {
        return Float.isFinite(yaw) && main >= floor(yaw, false) - 1.0E-4F && main <= 25
                && rocket >= floor(yaw, true) - 1.0E-4F && rocket <= 10;
    }
    public static float interpolateCycle(float previous, float current, float partial, float period) {
        double delta = wrap(current - previous + period / 2, period) - period / 2;
        return (float) (previous + delta * partial);
    }
    public static int fireInterval(int rpm) { return Math.max(1, (int) Math.ceil(1200.0 / Math.max(1, rpm))); }
}
