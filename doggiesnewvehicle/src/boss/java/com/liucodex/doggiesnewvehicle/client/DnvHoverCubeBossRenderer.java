package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.entity.DnvHoverCubeBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;

/** Temporary visual only; a future model may replace this renderer without changing the entity. */
public final class DnvHoverCubeBossRenderer extends EntityRenderer<DnvHoverCubeBossEntity> {
    private static final float SIZE = 3.0F;
    private final BlockRenderDispatcher blocks;

    public DnvHoverCubeBossRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
        shadowRadius = 0.0F;
    }

    @Override
    public void render(
            DnvHoverCubeBossEntity entity,
            float yaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(-SIZE / 2.0F, 0.0D, -SIZE / 2.0F);
        poseStack.scale(SIZE, SIZE, SIZE);
        blocks.renderSingleBlock(
                Blocks.IRON_BLOCK.defaultBlockState(),
                poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DnvHoverCubeBossEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
