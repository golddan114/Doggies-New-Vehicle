package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.data.gun.GunProp;
import net.minecraft.nbt.CompoundTag;

/** AI logistics only. SBW owns energy storage, loaded rounds, reloading and persistence. */
final class ApocalypseAutonomousSupply {
    private static final String[] GUNS = {"MainL", "MainR", "RocketL", "RocketR", "Missiles"};
    private final DnvAlliedApocalypseEntity tank;
    private boolean primed;
    private long lastTick = Long.MIN_VALUE;

    ApocalypseAutonomousSupply(DnvAlliedApocalypseEntity tank) { this.tank = tank; }

    void tick() {
        if (!tank.autonomousReady() || !tank.isInitialized()) return;
        long now = tank.level().getGameTime();
        if (now == lastTick) return;
        for (String name : GUNS) if (tank.getGunData(name) == null) return;
        lastTick = now;
        int maximum = tank.getMaxEnergy();
        if (maximum > 0 && tank.getEnergy() <= maximum / 2) tank.setEnergy(maximum);
        for (String name : GUNS) {
            var gun = tank.getGunData(name);
            int reserve = (int) Math.max(8, Math.min(512, (long) gun.get(GunProp.MAGAZINE) * 4));
            boolean refill = gun.virtualAmmo.get() <= reserve / 2;
            boolean prime = !primed && gun.ammo.get() == 0 && !gun.reloading();
            if (refill || prime) tank.modifyGunData(name, data -> {
                if (refill) data.virtualAmmo.set(reserve);
                if (prime) data.reloadAmmo(tank, false);
            });
        }
        primed = true;
    }

    void save(CompoundTag tag) { tag.putBoolean("SupplyPrimed", primed); }
    void load(CompoundTag tag) { primed = tag.getBoolean("SupplyPrimed"); lastTick = Long.MIN_VALUE; }
}
