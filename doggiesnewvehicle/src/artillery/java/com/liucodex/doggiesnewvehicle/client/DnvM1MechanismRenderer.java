package com.liucodex.doggiesnewvehicle.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The existing M1 registration uses the new author at native game scale. */
@Mod.EventBusSubscriber(modid="doggiesnewvehicle",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class DnvM1MechanismRenderer extends DnvDragonTurretRenderer {
    public DnvM1MechanismRenderer(EntityRendererProvider.Context context){super(context);}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(com.liucodex.doggiesnewvehicle.init.DnvM1Content.M1.get(),DnvM1MechanismRenderer::new);
    }
}
