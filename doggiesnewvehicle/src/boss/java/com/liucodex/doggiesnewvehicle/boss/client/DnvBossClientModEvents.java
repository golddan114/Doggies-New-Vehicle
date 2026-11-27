package com.liucodex.doggiesnewvehicle.boss.client;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.boss.DnvBossContent;
import com.liucodex.doggiesnewvehicle.client.DnvHoverCubeBossRenderer;
import com.liucodex.doggiesnewvehicle.client.DnvInvisibleEntityRenderer;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvVehicleMesh;
import com.liucodex.noise.client.NoiseLaserBoltRenderer;
import com.liucodex.noise.client.NoiseVehicleRenderer;
import com.liucodex.noise.client.mesh.NoiseDropRigMeshReloadListener;
import com.liucodex.silentheaven.client.SilentHeavenVehicleRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DnvBossClientModEvents {
    private DnvBossClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DnvBossContent.HOVER_CUBE_BOSS.get(),
                DnvHoverCubeBossRenderer::new);
        event.registerEntityRenderer(DnvBossContent.MOTHERSHIP.get(),DnvMothershipRenderer::new);
        event.registerEntityRenderer(com.liucodex.noise.init.ModEntities.NOISE.get(),
                NoiseVehicleRenderer::new);
        event.registerEntityRenderer(com.liucodex.noise.init.ModEntities.AUTOCANNON_ROUND.get(),
                DnvInvisibleEntityRenderer::new);
        event.registerEntityRenderer(com.liucodex.noise.init.ModEntities.LASER_BOLT.get(),
                NoiseLaserBoltRenderer::new);
        event.registerEntityRenderer(com.liucodex.silentheaven.init.ModEntities.SILENT_HEAVEN.get(),
                SilentHeavenVehicleRenderer::new);
        event.registerEntityRenderer(com.liucodex.silentheaven.init.ModEntities.AUTOCANNON_ROUND.get(),
                DnvInvisibleEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(DnvVehicleMesh.NOISE.reloadListener());
        event.registerReloadListener(new NoiseDropRigMeshReloadListener());
        event.registerReloadListener(DnvVehicleMesh.SILENT_HEAVEN.reloadListener());
    }
}
