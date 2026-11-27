package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.TurretWreckEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.api.combat.DnvWreckLifecycle;
import net.minecraft.world.entity.Entity;

/** Loaded server ticks, independent of hull HP and live-vehicle burning/repair settings. */
public final class DnvWreckLifetime {
    public static final int LOADED_TICKS = 20 * 60;
    private static final String AGE = "DnvWreckLoadedTicks", DONE = "DnvWreckSettled";
    private DnvWreckLifetime() { }

    /** Returns true only when this tick must stop because the expired wreck was removed. */
    public static boolean tick(Entity entity) {
        if (entity.level().isClientSide || entity.isRemoved()) return false;
        boolean wreck = entity instanceof VehicleEntity vehicle && vehicle.isWreck()
                || entity instanceof TurretWreckEntity;
        if (!wreck) {
            entity.getPersistentData().remove(AGE);
            return false;
        }
        if (entity instanceof DnvWreckLifecycle lifecycle && !lifecycle.dnvWreckReadyForCleanup()) return false;
        int limit = entity instanceof DnvWreckLifecycle lifecycle
                ? Math.max(1, Math.min(LOADED_TICKS, lifecycle.dnvWreckLifetimeTicks())) : LOADED_TICKS;
        var data = entity.getPersistentData();
        int age = Math.max(0, Math.min(LOADED_TICKS, data.getInt(AGE))) + 1;
        data.putInt(AGE, age);
        if (age < limit) return false;
        finish(entity);
        return true;
    }

    public static void finish(Entity entity) {
        if (entity.level().isClientSide || entity.isRemoved()) return;
        var data = entity.getPersistentData();
        if (data.getBoolean(DONE)) { entity.discard(); return; }
        data.putBoolean(DONE, true); // Reserve settlement before loot; reload/re-entry cannot duplicate it.
        entity.ejectPassengers();
        entity.discard();
        if (entity instanceof VehicleEntity vehicle) vehicle.generateWreckageLoot();
        else if (entity instanceof TurretWreckEntity turret) turret.generateWreckageLoot();
        // Expiry adds no second explosion, avoiding the old upstream keepBlock implementation as well.
    }
}
