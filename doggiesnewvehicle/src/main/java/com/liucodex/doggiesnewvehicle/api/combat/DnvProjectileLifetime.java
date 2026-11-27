package com.liucodex.doggiesnewvehicle.api.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** Assigns a persisted deadline to a DNV shot before world admission. */
public final class DnvProjectileLifetime {
    public static final String EXPIRY = "DnvTurretShellExpiry";

    public static final String WATER_SENSITIVE = "DnvWaterSensitive";

    public static void trackKinetic(Entity projectile, long remaining) {
        track(projectile, remaining);
        projectile.getPersistentData().putBoolean(WATER_SENSITIVE, true);
    }

    private DnvProjectileLifetime() {}

    public static void track(Entity projectile, long remaining) {
        track(projectile.getPersistentData(), projectile.level().getGameTime(), remaining);
    }

    public static void track(CompoundTag data, long now, long remaining) {
        data.putLong(EXPIRY, now + Math.max(1, remaining) + 1L);
    }
}
