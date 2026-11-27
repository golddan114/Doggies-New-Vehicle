package com.liucodex.doggiesnewvehicle.compat;

import net.minecraft.world.phys.Vec3;

/** Bounded direct interception. Turn radius shrinks with distance instead of circling a nearby target. */
public final class DnvMissileIntercept {
    public static final double MAX_SPEED = 4;
    private DnvMissileIntercept() { }

    public record Steering(Vec3 direction, float turnDegrees, double speed) { }

    public static Steering plan(Vec3 position, Vec3 velocity, Vec3 look, Vec3 target, Vec3 targetVelocity, int turningTicks) {
        Vec3 movement = finite(targetVelocity) ? targetVelocity : Vec3.ZERO;
        // Guidance runs after movement; the next native sweep sees one target update later,
        // regardless of entity tick order. Feed forward that update exactly once below.
        Vec3 relative = target.subtract(position);
        double distance = relative.length();
        double time = interceptTime(relative, movement, MAX_SPEED);
        Vec3 direction = relative.add(movement.scale(time)).normalize();
        // Feed forward target motion during terminal closure. Distance-only braking otherwise
        // settles behind a moving target at a constant gap instead of reaching its collision box.
        Vec3 terminal = movement.add(relative.scale(.65));
        if (distance < 12) direction = terminal.normalize();
        if (direction.lengthSqr() < 1E-10) direction = look;
        float turn = Math.min(24, 1.5F + Math.max(1, turningTicks) * .75F);
        double angle = Math.acos(Math.max(-1, Math.min(1, look.normalize().dot(direction))));
        double speed = Math.min(MAX_SPEED, Math.max(.25, velocity.length()) + .25);
        // Native swept collision still produces every impact; guidance never applies remote damage.
        if (distance < 12) speed = Math.min(speed, Math.max(.08, terminal.length()));
        if (angle > Math.toRadians(10)) speed = Math.min(speed, Math.max(.08, distance * Math.toRadians(turn) * .6 + movement.length()));
        return new Steering(direction, turn, speed);
    }

    static double interceptTime(Vec3 relative, Vec3 movement, double speed) {
        double a = movement.lengthSqr() - speed * speed;
        double b = 2 * relative.dot(movement), c = relative.lengthSqr();
        double time = Math.sqrt(c) / speed;
        double discriminant = b * b - 4 * a * c;
        if (Math.abs(a) < 1E-8) {
            if (b < -1E-8) time = -c / b;
        } else if (discriminant >= 0) {
            double root = Math.sqrt(discriminant);
            double first = (-b - root) / (2 * a), second = (-b + root) / (2 * a);
            if (first > 0 && second > 0) time = Math.min(first, second);
            else if (first > 0) time = first;
            else if (second > 0) time = second;
        }
        return Math.max(0, Math.min(40, time));
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
