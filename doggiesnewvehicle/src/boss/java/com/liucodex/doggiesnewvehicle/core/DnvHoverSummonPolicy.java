package com.liucodex.doggiesnewvehicle.core;

/** Finite per-boss Noise deployment budget and lock timing. */
public final class DnvHoverSummonPolicy {
    public static final int WARNING_TICKS = 60;
    public static final int COOLDOWN_TICKS = 600;
    public static final int FAILED_SITE_RETRY_TICKS = 100;
    public static final int MAX_LIFETIME_SUMMONS = 2;
    public static final double MAX_TARGET_SITE_DISTANCE = 24.0D;

    private DnvHoverSummonPolicy() {
    }

    public static boolean mayLock(int spawnedCount, long nextAllowedTime, long gameTime) {
        return spawnedCount >= 0
                && spawnedCount < MAX_LIFETIME_SUMMONS
                && gameTime >= nextAllowedTime;
    }

    public static int advanceWarning(int currentTicks, boolean targetStillLegal) {
        if (!targetStillLegal) {
            return 0;
        }
        return Math.min(WARNING_TICKS, Math.max(0, currentTicks) + 1);
    }

    public static long nextAllowedTime(long gameTime, boolean spawned) {
        int delay = spawned ? COOLDOWN_TICKS : FAILED_SITE_RETRY_TICKS;
        return gameTime > Long.MAX_VALUE - delay ? Long.MAX_VALUE : gameTime + delay;
    }

    public static boolean targetNearMarkedSite(
            double targetX, double targetZ, double siteX, double siteZ) {
        if (!Double.isFinite(targetX) || !Double.isFinite(targetZ)
                || !Double.isFinite(siteX) || !Double.isFinite(siteZ)) {
            return false;
        }
        double dx = targetX - siteX;
        double dz = targetZ - siteZ;
        return dx * dx + dz * dz
                <= MAX_TARGET_SITE_DISTANCE * MAX_TARGET_SITE_DISTANCE;
    }
}
