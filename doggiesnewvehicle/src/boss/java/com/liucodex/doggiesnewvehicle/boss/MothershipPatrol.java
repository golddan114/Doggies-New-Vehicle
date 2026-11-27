package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;

/** Stateless idle-leg selection; the server entity owns the destination, pauses and cancellation. */
public final class MothershipPatrol {
    public static final double HOME_RADIUS = 30;
    public static final int LEG_TIMEOUT = 1200;
    public record Leg(V destination, double clearance) { }
    private MothershipPatrol() { }

    public static Leg choose(V current, V home, double clearance, double angle, double distance, double height) {
        double length = 10 + 5 * unit(distance);
        double direction = unit(angle) * Math.PI * 2;
        double x = current.x() + Math.cos(direction) * length, z = current.z() + Math.sin(direction) * length;
        if (Math.hypot(x - home.x(), z - home.z()) > HOME_RADIUS) {
            // Walk back after a pursuit; never snap the ship or its next leg to the encounter origin.
            direction = Math.atan2(home.z() - current.z(), home.x() - current.x());
            x = current.x() + Math.cos(direction) * length; z = current.z() + Math.sin(direction) * length;
        }
        double low = MothershipTactics.MIN_IDLE_CLEARANCE, high = MothershipTactics.MAX_IDLE_CLEARANCE;
        double start = Double.isFinite(clearance) ? Math.max(low, Math.min(high, clearance)) : MothershipTactics.GROUND_CLEARANCE;
        boolean up = start <= (low + high) / 2;
        double room = up ? high - start : start - low;
        double change = 5 + unit(height) * (Math.min(10, room) - 5);
        return new Leg(new V(x, current.y(), z), start + (up ? change : -change));
    }

    private static double unit(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : .5; }
}
