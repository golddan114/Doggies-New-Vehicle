package com.liucodex.doggiesnewvehicle.api.combat;

/** Optional content controls its own camera feedback without exposing its implementation to base. */
public interface DnvCameraShakeSource {
    boolean dnvCameraShakeAllowed();
}
