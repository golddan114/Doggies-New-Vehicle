package com.liucodex.doggiesnewvehicle.client;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.init.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = DoggiesNewVehicle.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void constructClient(net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent event) {
        com.liucodex.doggiesnewvehicle.weapons.Dsx1Item.installRenderer(
                com.liucodex.doggiesnewvehicle.client.weapons.Dsx1Renderer::new);
    }

    @SubscribeEvent
    public static void setupClient(FMLClientSetupEvent event) {
        event.enqueueWork(DnvClientNetworkBindings::install);
        event.enqueueWork(com.liucodex.doggiesnewvehicle.client.weapons.Dsx1TrailRenderer::install);
        event.enqueueWork(() -> net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                com.liucodex.doggiesnewvehicle.world.DnvControlledFire.FIRE.get(), net.minecraft.client.renderer.RenderType.cutout()));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntities.COMBAT_PROXY.get(),
                DnvInvisibleEntityRenderer::new);
    }
}
