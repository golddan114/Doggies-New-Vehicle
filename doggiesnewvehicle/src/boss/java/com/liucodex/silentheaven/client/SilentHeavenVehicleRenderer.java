package com.liucodex.silentheaven.client;

import com.liucodex.silentheaven.entity.SilentHeavenVehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Renders the autonomous Superb Warfare vehicle with the production SHM. */
public final class SilentHeavenVehicleRenderer
        extends EntityRenderer<SilentHeavenVehicleEntity> {
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("silent_heaven", "textures/entity/silent_heaven_body.png");

    public SilentHeavenVehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 3.5F;
    }

    @Override
    public void render(
            SilentHeavenVehicleEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        SilentHeavenMeshLayer.renderVehicle(
                poseStack, bufferSource, packedLight, entity, partialTick);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SilentHeavenVehicleEntity entity) {
        return FALLBACK_TEXTURE;
    }
}
