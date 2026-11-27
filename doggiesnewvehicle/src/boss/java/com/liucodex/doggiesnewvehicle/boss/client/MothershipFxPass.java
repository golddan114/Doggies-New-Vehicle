package com.liucodex.doggiesnewvehicle.boss.client;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvMothershipEntity;
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

/** Only entities admitted by the real renderer enter this frame-local pass; no world scan or combat writes. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MothershipFxPass {
    private static final Set<DnvMothershipEntity> pending = new LinkedHashSet<>();
    private static MultiBufferSource.BufferSource buffers;

    private MothershipFxPass() { }

    public static void enqueue(DnvMothershipEntity ship) { pending.add(ship); }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) { if (event.getLevel().isClientSide()) pending.clear(); }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            pending.clear();
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || pending.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.level == null) return;
            if (buffers == null) buffers = MultiBufferSource.immediate(new BufferBuilder(128 * 1024));
            for (DnvMothershipEntity ship : pending) {
                if (!ship.isRemoved() && ship.level() == mc.level) {
                    DnvMothershipRenderer.renderFx(ship, event.getPartialTick(), event.getPoseStack(),
                            buffers, event.getCamera().getPosition());
                }
            }
            buffers.endBatch();
        } finally {
            pending.clear();
        }
    }
}
