package com.liucodex.doggiesnewvehicle.boss.client;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.noise.client.NoiseSoundManager;
import com.liucodex.silentheaven.client.SilentHeavenSoundManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DnvBossClientGameEvents {
    private DnvBossClientGameEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            NoiseSoundManager.tick();
            SilentHeavenSoundManager.tick();
            MothershipSoundManager.tick();
        }
    }
}
