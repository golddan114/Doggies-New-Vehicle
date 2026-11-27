package com.liucodex.doggiesnewvehicle.api;

import net.minecraft.world.entity.player.Player;

/** Optional seated-driver mode capability. The vehicle owns readiness, cooldown and synchronized state. */
public interface DnvVehicleModeSwitch {
    boolean requestMode(Player actor);
}
