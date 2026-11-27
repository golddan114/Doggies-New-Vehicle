package com.liucodex.doggiesnewvehicle.api.combat;

/** Read-only direct-hit bonus, sampled once when SBW admits the native projectile. */
public interface DnvNativeProjectilePercent {
    float directPercent(String weapon);
}
