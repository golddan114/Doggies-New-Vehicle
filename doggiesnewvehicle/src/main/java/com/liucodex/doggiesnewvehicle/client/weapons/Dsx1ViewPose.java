package com.liucodex.doggiesnewvehicle.client.weapons;

/** One display weight shared by depth compression and optical alignment; never writes native zoom. */
public final class Dsx1ViewPose {
    private Dsx1ViewPose() { }
    public static float depthScale(double ads) { return (float)(1 - .985 * Math.min(1, unit(ads) * 2)); }
    public static double ads(double zoom, boolean action, double progress) {
        // GunData owns the action and SBW owns zoom. A bolt/reload never releases ADS.
        return unit(zoom);
    }
    public static float actionRotation(double zoom) { return (float)(1 - .92 * unit(zoom)); }
    public static float actionTranslation(double zoom) { return (float)(1 - .82 * unit(zoom)); }
    private static double unit(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
}
