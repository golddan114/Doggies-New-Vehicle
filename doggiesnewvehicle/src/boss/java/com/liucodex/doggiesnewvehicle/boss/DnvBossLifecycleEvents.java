package com.liucodex.doggiesnewvehicle.boss;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Persistent occupancy remains boss-owned; the base lifecycle hook owns no boss state. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle")
public final class DnvBossLifecycleEvents {
    private DnvBossLifecycleEvents() { }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level && level.getGameTime() % 20 == 0)
            MothershipPresence.get(level).reconcile(level);
    }
}
