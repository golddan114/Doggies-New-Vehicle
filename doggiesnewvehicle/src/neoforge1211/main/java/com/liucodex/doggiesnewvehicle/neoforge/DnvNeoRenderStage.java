package com.liucodex.doggiesnewvehicle.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** NeoForge exposes the world camera matrix separately from the optional local stack. */
public final class DnvNeoRenderStage {
    private DnvNeoRenderStage() {}

    public static PoseStack stack(RenderLevelStageEvent event) {
        var stack = new PoseStack();
        stack.mulPose(event.getModelViewMatrix());
        return stack;
    }
}
