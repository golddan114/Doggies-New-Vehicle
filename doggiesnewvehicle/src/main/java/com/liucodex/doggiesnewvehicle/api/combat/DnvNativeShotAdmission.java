package com.liucodex.doggiesnewvehicle.api.combat;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.data.gun.GunData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Optional player-vehicle boundary for one authorized native GunData shot and its actual world result. */
public interface DnvNativeShotAdmission {
    /** Only opt-in hosts move native local shot audio from prediction to accepted feedback. */
    default boolean deferNativeClientSound() { return false; }
    /** Client feedback preflight only; the existing server/world admission remains authoritative. */
    default boolean permitsNativeClientFire(Entity operator, Entity lockedTarget, boolean locked, GunData gun) {
        return true;
    }
    /** Forward the existing native release intent; continuous-input hosts own their stop state. */
    default void releaseNativeFire(Entity operator) { }
    boolean admitNativeProjectile(ServerLevel level, Entity projectile, ShootParameters parameters);
    boolean nativeProjectileAccepted(ShootParameters parameters);
}
