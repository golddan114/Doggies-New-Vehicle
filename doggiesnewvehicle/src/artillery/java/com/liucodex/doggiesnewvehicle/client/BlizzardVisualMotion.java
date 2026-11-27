package com.liucodex.doggiesnewvehicle.client;

/** Small client-only suspension motion; never changes collision, seats or server aim. */
public final class BlizzardVisualMotion {
    private BlizzardVisualMotion() { }
    public static double bob(double tick, double speed, boolean powered) {
        if (!powered || !Double.isFinite(tick) || !Double.isFinite(speed)) return 0;
        return .03 * Math.sin(tick * Math.PI / 60) / (1 + Math.max(0, speed) * 5);
    }
    public static double thrust(double speed, double vertical, boolean lift) {
        if (!Double.isFinite(speed) || !Double.isFinite(vertical)) return 0;
        return Math.min(1, (lift ? .38 + Math.max(0, vertical) * 2 : 0) + Math.abs(speed) * .8);
    }
}
