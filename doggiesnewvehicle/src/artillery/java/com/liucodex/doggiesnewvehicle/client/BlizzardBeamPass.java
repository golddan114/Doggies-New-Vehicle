package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.armored.DnvBlizzardEntity;
import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Renderer-admitted beams only; flush after scene depth and into the particles target on Fabulous. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", value = Dist.CLIENT)
public final class BlizzardBeamPass {
    private static final Set<DnvBlizzardEntity> pending = new LinkedHashSet<>();
    private static MultiBufferSource.BufferSource buffers;
    private BlizzardBeamPass() { }

    public static void enqueue(DnvBlizzardEntity tank) { pending.add(tank); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel().isClientSide()) pending.clear(); }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) { pending.clear(); return; }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || pending.isEmpty()) return;
        var mc = Minecraft.getInstance();
        try {
            if (mc.level == null) return;
            if (buffers == null) buffers = MultiBufferSource.immediate(new BufferBuilder(128 * 1024));
            for (var tank : pending) {
                if (!tank.isRemoved() && tank.level() == mc.level)
                    BlizzardRenderer.renderBeam(tank, event.getPartialTick(), event.getPoseStack(), buffers, event.getCamera().getPosition());
            }
            buffers.endBatch();
        } finally { pending.clear(); }
    }
}
