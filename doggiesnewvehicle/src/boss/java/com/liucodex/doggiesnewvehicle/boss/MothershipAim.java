package com.liucodex.doggiesnewvehicle.boss;

/** Hull-local aim: yaw zero is the bow, positive elevation points upward. All values are degrees. */
public final class MothershipAim {
    public static final double HULL_TURN_PER_TICK = .15;
    public record Aim(double yaw, double elevation, boolean mayFire) { }
    private MothershipAim() { }

    public static boolean doomsdayArc(double relativeYaw) {
        return Double.isFinite(relativeYaw) && Math.abs(wrap(relativeYaw)) <= 90;
    }

    /** Turn the hull to the target before beginning lock, then allow tracking only in the forward hemisphere. */
    public static boolean mayBeginDoomsdayLock(double relativeYaw) {
        return Double.isFinite(relativeYaw) && Math.abs(wrap(relativeYaw)) <= 5;
    }

    public static double turnHull(double current, double desired) {
        if (!Double.isFinite(current) || !Double.isFinite(desired)) throw new IllegalArgumentException("Invalid yaw");
        return wrap(current + Math.max(-HULL_TURN_PER_TICK, Math.min(HULL_TURN_PER_TICK, wrap(desired - current))));
    }

    /** Conservative belly-mount limits; blocked by the hull still requires a physical muzzle clearance test. */
    public static Aim turret(MothershipArmament.Kind kind, double yaw, double elevation) {
        if (kind == null || !Double.isFinite(yaw) || !Double.isFinite(elevation)) return new Aim(0, 0, false);
        double yawLimit = kind == MothershipArmament.Kind.TWIN_LASER ? 150 : 100;
        double minimum = kind == MothershipArmament.Kind.HE_ROCKET ? -70 : -80;
        double maximum = kind == MothershipArmament.Kind.HE_ROCKET ? -5 : 0;
        double relative = wrap(yaw);
        boolean valid = Math.abs(relative) <= yawLimit && elevation >= minimum && elevation <= maximum;
        return new Aim(Math.max(-yawLimit, Math.min(yawLimit, relative)), Math.max(minimum, Math.min(maximum, elevation)), valid);
    }

    private static double wrap(double degrees) { return (degrees % 360 + 540) % 360 - 180; }
}
