package com.liucodex.doggiesnewvehicle.core;

/** Pure firing-position geometry; callers retain target, terrain and movement authority. */
public final class DnvFiringRange {
    private DnvFiringRange() { }

    public static double minimum(double rise, double speed, double gravity,
            double lowerDegrees, double upperDegrees, double baseRange, double maximumRange) {
        if (!Double.isFinite(rise) || !Double.isFinite(speed) || speed <= 0
                || !Double.isFinite(gravity) || gravity < 0) return baseRange;
        double angle = Math.toRadians(rise >= 0 ? upperDegrees : lowerDegrees);
        double tangent = Math.tan(angle);
        double curvature = gravity / (2 * speed * speed * Math.pow(Math.cos(angle), 2));
        double distance;
        if (curvature < 1E-12) distance = rise / tangent;
        else {
            double discriminant = tangent * tangent - 4 * curvature * rise;
            if (discriminant < 0) return maximumRange;
            double root = Math.sqrt(discriminant);
            distance = rise >= 0 ? 2 * rise / (tangent + root) : (tangent + root) / (2 * curvature);
        }
        return Double.isFinite(distance) ? Math.max(baseRange, Math.min(maximumRange, distance)) : maximumRange;
    }
}
