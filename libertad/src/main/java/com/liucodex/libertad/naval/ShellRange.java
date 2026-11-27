package com.liucodex.libertad.naval;

import com.liucodex.libertad.naval.MountGeometry.Point;
import java.util.Objects;

/** Clip the line consumed by native collision and movement before either side effect runs. */
public record ShellRange(Point origin, double radius, long expiresAt) {
    public static final int MAIN = 720;
    public static final int SECONDARY = 600;
    public static final int LIFETIME = 200;

    public ShellRange {
        Objects.requireNonNull(origin);
        if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("Invalid shot radius");
    }

    public Step next(Point start, Point displacement, long gameTime) {
        if (gameTime >= expiresAt) return new Step(start, false, true);
        Point offset = start.minus(origin);
        double c = offset.lengthSquared() - radius * radius;
        if (c >= 0) return new Step(start, false, true);
        Point end = start.add(displacement);
        if (end.minus(origin).lengthSquared() < radius * radius) return new Step(end, true, false);
        double a = displacement.lengthSquared();
        double b = offset.dot(displacement);
        double root = Math.sqrt(b * b - a * c);
        // Stable form when near the edge and moving outward.
        double fraction = b >= 0 ? -c / (root + b) : (root - b) / a;
        return new Step(start.add(displacement.times(Math.min(fraction, 1))), true, true);
    }

    public record Step(Point endpoint, boolean trace, boolean discardIfMiss) {}
}
