package com.liucodex.doggiesnewvehicle.api.combat;

/** Optional content owns its destruction sequence; the common SBW adapter owns subsequent expiry. */
public interface DnvWreckLifecycle {
    boolean dnvWreckReadyForCleanup();
    default int dnvWreckLifetimeTicks() { return 1200; }
    /** A scripted destruction sequence may keep its combat bar empty instead of displaying salvage durability. */
    default boolean dnvShowWreckHealth() { return true; }
    /** Scripted entry can suppress the native name/health/distance marker before combat begins. */
    default boolean dnvShowVehicleMarker() { return true; }
}
