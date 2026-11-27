package com.liucodex.doggiesnewvehicle.platform.client;

import com.liucodex.doggiesnewvehicle.entity.DnvHoverRoutePlatformEntity;
import com.liucodex.doggiesnewvehicle.platform.DnvHoverPlatformStations;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;

/** Renderer-only placeholder, distinct from the boss cube. */
public final class DnvHoverRoutePlatformRenderer
        extends EntityRenderer<DnvHoverRoutePlatformEntity> {
    private final BlockRenderDispatcher blocks;

    public DnvHoverRoutePlatformRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
        shadowRadius = 0.0F;
    }

    @Override
    public void render(DnvHoverRoutePlatformEntity entity, float yaw,
            float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(-2.0D, 0.0D, -2.0D);
        poseStack.scale(4.0F, 1.5F, 4.0F);
        blocks.renderSingleBlock(Blocks.COPPER_BLOCK.defaultBlockState(),
                poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        renderGun(entity, DnvHoverPlatformStations.AI_STATION, -1.2D,
                poseStack, buffers, packedLight);
        renderGun(entity, DnvHoverPlatformStations.PLAYER_STATION, 1.2D,
                poseStack, buffers, packedLight);
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private void renderGun(DnvHoverRoutePlatformEntity entity, String station,
            double offsetX, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(offsetX, entity.getBbHeight(), 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.gunYaw(station)));
        poseStack.translate(-0.35D, 0.0D, -0.35D);
        poseStack.scale(0.7F, 0.45F, 0.7F);
        blocks.renderSingleBlock((DnvHoverPlatformStations.AI_STATION.equals(station)
                        ? Blocks.GOLD_BLOCK : Blocks.IRON_BLOCK).defaultBlockState(),
                poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(offsetX, entity.getBbHeight() + 0.3D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.gunYaw(station)));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.gunPitch(station)));
        poseStack.translate(-0.12D, -0.12D, 0.25D);
        poseStack.scale(0.24F, 0.24F, 1.1F);
        blocks.renderSingleBlock(Blocks.IRON_BLOCK.defaultBlockState(),
                poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(DnvHoverRoutePlatformEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
