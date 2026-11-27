package com.liucodex.doggiesnewvehicle.core;

import java.util.Objects;
import java.util.function.Predicate;

/** Bounded, deterministic hover motion. World reads remain with the server entity. */
public final class DnvHoverFlightPolicy {
    private static final double ORBIT_RADIUS = 10.0D;
    private static final double ORBIT_RADIANS_PER_TICK = 0.018D;
    private static final double HOVER_HEIGHT = 8.0D;
    private static final double BOB_HEIGHT = 0.35D;
    private static final double BOB_RADIANS_PER_TICK = 0.055D;

    private DnvHoverFlightPolicy() {
    }

    public record Point(double x, double y, double z) {
    }

    public static Point orbitGoal(
            double anchorX,
            double anchorZ,
            int tick,
            int terrainY,
            int minBuildY,
            int maxBuildY) {
        double angle = tick * ORBIT_RADIANS_PER_TICK;
        double x = anchorX + ORBIT_RADIUS * Math.cos(angle);
        double z = anchorZ + ORBIT_RADIUS * Math.sin(angle);
        double y = clamp(
                terrainY + HOVER_HEIGHT + BOB_HEIGHT * Math.sin(tick * BOB_RADIANS_PER_TICK),
                minBuildY + 2.0D,
                maxBuildY - 4.0D);
        return new Point(x, y, z);
    }

    public static Point nextMotion(Point position, Point velocity, Point goal) {
        return new Point(
                axis(velocity.x, goal.x - position.x, 0.16D),
                axis(velocity.y, goal.y - position.y, 0.12D),
                axis(velocity.z, goal.z - position.z, 0.16D));
    }

    /** One bounded local slide around an obstruction; the caller owns world safety checks. */
    public static Point chooseUnobstructed(
            Point planned,
            Predicate<Point> traversable) {
        Objects.requireNonNull(planned, "planned");
        Objects.requireNonNull(traversable, "traversable");
        if (traversable.test(planned)) {
            return planned;
        }
        Point xOnly = new Point(planned.x, planned.y, 0.0D);
        Point zOnly = new Point(0.0D, planned.y, planned.z);
        Point first = Math.abs(planned.x) >= Math.abs(planned.z) ? xOnly : zOnly;
        Point second = first == xOnly ? zOnly : xOnly;
        if (traversable.test(first)) {
            return first;
        }
        return traversable.test(second) ? second : new Point(0.0D, 0.0D, 0.0D);
    }

    private static double axis(double velocity, double error, double limit) {
        return clamp(velocity * 0.80D + clamp(error * 0.025D, -0.045D, 0.045D),
                -limit, limit);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
