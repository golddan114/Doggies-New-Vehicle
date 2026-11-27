package com.liucodex.doggiesnewvehicle.boss;

/** Native BOSS motion integration; shared local driving decisions live in base. */
public final class DnvBossGroundDrive {
    private DnvBossGroundDrive() { }

    public static float powerStep(float current, float requested) {
        boolean braking = Math.signum(current) != Math.signum(requested)
                || Math.abs(requested) < Math.abs(current);
        // Reach neutral before accelerating in the opposite direction.
        float target = current * requested < 0 ? 0 : requested;
        float next = approach(current, target, braking ? 0.065F : 0.022F);
        return Math.abs(next) < 0.002F ? 0 : next;
    }

    public static float steeringStep(float current, float requested) {
        float next = approach(current, requested, 0.12F);
        return Math.abs(next) < 0.01F && requested == 0 ? 0 : next;
    }

    public static float yawStep(float steering, float power, float turnSpeed) {
        return steering * turnSpeed * (1.0F - 0.15F * Math.abs(power));
    }

    public record Velocity(double x, double z) {}

    public static Velocity velocity(double x, double z, float yaw, float power, double maxSpeed) {
        double limit = maxSpeed * 1.5D;
        double magnitude = Math.hypot(x, z);
        if (magnitude > limit) { x *= limit / magnitude; z *= limit / magnitude; }
        double angle = Math.toRadians(yaw);
        double fx = -Math.sin(angle), fz = Math.cos(angle);
        double forward = x * fx + z * fz;
        double target = power * maxSpeed;
        double rate = Math.abs(target) < Math.abs(forward) || target * forward < 0 ? 0.016D : 0.008D;
        double speed = forward + Math.max(-rate, Math.min(rate, target - forward));
        if (power == 0 && Math.abs(speed) < 0.0005D) speed = 0;
        // Strong lateral damping avoids skating sideways during a pivot.
        return new Velocity(fx * speed + (x - fx * forward) * 0.4D,
                fz * speed + (z - fz * forward) * 0.4D);
    }

    public static double forwardTravel(double dx, double dz, float meanYaw) {
        double angle = Math.toRadians(meanYaw);
        return dx * -Math.sin(angle) + dz * Math.cos(angle);
    }

    private static float approach(float value, float target, float step) {
        return value + clamp(target - value, -step, step);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

}
