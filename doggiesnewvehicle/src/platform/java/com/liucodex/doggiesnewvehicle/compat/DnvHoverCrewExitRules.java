package com.liucodex.doggiesnewvehicle.compat;

/** World-independent height gate used before any hover-platform dismount. */
final class DnvHoverCrewExitRules {
    static final double MAX_GROUND_OFFSET = 1.6D;

    private DnvHoverCrewExitRules() {
    }

    static boolean groundClose(double referenceY, double surfaceY, double maxOffset) {
        return Double.isFinite(referenceY) && Double.isFinite(surfaceY)
                && maxOffset >= 0.0D
                && Math.abs(surfaceY - referenceY) <= maxOffset;
    }
}
