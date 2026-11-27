package com.liucodex.noise.client;

import com.liucodex.noise.entity.NoiseVehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Renders the autonomous Superb Warfare vehicle with the production SHM. */
public final class NoiseVehicleRenderer
        extends EntityRenderer<NoiseVehicleEntity> {
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("noise", "textures/entity/noise_body.png");

    public NoiseVehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 3.5F;
    }

    @Override
    public void render(
            NoiseVehicleEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        NoiseDropRigLayer.render(
                poseStack, bufferSource, packedLight, entity, partialTick);
        NoiseMeshLayer.renderVehicle(
                poseStack, bufferSource, packedLight, entity, partialTick);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(NoiseVehicleEntity entity) {
        return FALLBACK_TEXTURE;
    }
}

