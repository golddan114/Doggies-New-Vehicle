package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.entity.StormBulletEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** Invisible rounds; the turret renderer owns the muzzle flash. */
public final class StormBulletRenderer extends EntityRenderer<StormBulletEntity> {
    public StormBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(StormBulletEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public void render(StormBulletEntity entity, float yaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // Emit no geometry, tracer or nameplate, even for direct render calls.
    }

    @Override
    public ResourceLocation getTextureLocation(StormBulletEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
