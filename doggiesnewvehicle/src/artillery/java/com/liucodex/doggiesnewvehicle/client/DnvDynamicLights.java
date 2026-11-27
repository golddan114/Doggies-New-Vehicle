package com.liucodex.doggiesnewvehicle.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded client block-light sources; native propagation handles terrain, occlusion and removal. */
@Mod.EventBusSubscriber(modid="doggiesnewvehicle",value=Dist.CLIENT)
public final class DnvDynamicLights {
    private static final DnvDynamicLightField FIELD=new DnvDynamicLightField();
    private static int clock;
    private DnvDynamicLights(){ }
    public static int emission(Object engine,BlockPos position){
        return FIELD.emission(engine,position.getX(),position.getY(),position.getZ());
    }
    public static void reload(){
        var mc=Minecraft.getInstance();clock=0;
        if(mc.level==null){FIELD.clear(null);return;}
        var engine=mc.level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        refresh(mc,engine,FIELD.clear(engine));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        // A missing player can retain the same client world: withdraw through its live block engine.
        if(mc.level==null||mc.player==null){reload();return;}
        if(mc.isPaused()||clock++%5!=0)return;
        var sources=new ArrayList<DnvDynamicLightField.Source>();
        BlizzardClientEffects.appendLights(sources,mc.level);
        NuclearClientEffects.appendLights(sources,mc.level);
        var engine=mc.level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        refresh(mc,engine,FIELD.update(engine,sources,mc.player.getX(),mc.player.getY(),mc.player.getZ()));
    }
    private static void refresh(Minecraft mc,LayerLightEventListener engine,List<DnvDynamicLightField.Block> blocks){
        for(var block:blocks){
            var position=new BlockPos(block.x(),block.y(),block.z());
            if(mc.level.hasChunkAt(position))engine.checkBlock(position);
        }
    }
}
