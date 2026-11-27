package com.liucodex.doggiesnewvehicle.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;

/** Renderer for server-authoritative collision entities with no visual body. */
public final class DnvInvisibleEntityRenderer<T extends Entity>
        extends EntityRenderer<T> {
    public DnvInvisibleEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(
            T entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        // Deliberately empty. The firing vehicle owns the muzzle flash.
    }

    @Override
    public boolean shouldRender(
            T entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
