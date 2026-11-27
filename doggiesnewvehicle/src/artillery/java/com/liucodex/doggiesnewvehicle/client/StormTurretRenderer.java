package com.liucodex.doggiesnewvehicle.client;

import static com.liucodex.doggiesnewvehicle.client.DnvTurretMeshRenderSupport.*;

import com.atsuishio.superbwarfare.client.renderer.ModRenderTypes;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh;
import com.liucodex.doggiesnewvehicle.entity.StormTurretEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class StormTurretRenderer extends DnvTurretRenderer<StormTurretEntity> {
    private static final ResourceLocation MAIN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "doggiesnewvehicle", "textures/entity/storm_turret.png");
    private static final ResourceLocation LIGHTS_ON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "doggiesnewvehicle", "textures/entity/storm_turret_lights_on.png");
    private static final ResourceLocation LIGHTS_OFF_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "doggiesnewvehicle", "textures/entity/storm_turret_lights_off.png");
    private static final ResourceLocation MUZZLE_FLASH_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "superbwarfare", "textures/particle/flare.png");
    private static final RenderType MAIN_RENDER_TYPE = RenderType.entityCutout(MAIN_TEXTURE);
    private static final RenderType LIGHTS_ON_BASE_RENDER_TYPE = RenderType.entityCutout(LIGHTS_ON_TEXTURE);
    private static final RenderType LIGHTS_OFF_BASE_RENDER_TYPE = RenderType.entityCutout(LIGHTS_OFF_TEXTURE);
    private static final RenderType LIGHTS_GLOW_RENDER_TYPE = RenderType.eyes(LIGHTS_ON_TEXTURE);
    private static final float HPJ_FLASH_LENGTH = 61.44F / 16.0F;
    private static final float HPJ_FLASH_WIDTH = 12.96F / 16.0F;
    private static final float HPJ_STAR_SIZE = 12.96F / 16.0F;
    private static final float[] HPJ_STREAK_ANGLES = {-60.0F, -121.0F, 179.0F};
    private static final float HPJ_EFFECT_SCALE = 0.22F;
    private static final float MUZZLE_X = 0.306176F;
    private static final float MUZZLE_Y = 2.082112F;
    private static final float MUZZLE_Z = 1.209418F;
    private static final float PITCH_PIVOT_X = 0.0F;
    private static final float PITCH_PIVOT_Y = 2.102680F;
    private static final float PITCH_PIVOT_Z = 0.0F;

    public StormTurretRenderer(EntityRendererProvider.Context context) {
        super(context, MAIN_TEXTURE, StormTurretRenderer::renderTurret);
    }

    private static void renderTurret(
            StormTurretEntity entity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        DnvTurretMesh.MeshData mesh = DnvTurretMesh.STORM.get(DnvRenderDetail.distanceSquared(entity));
        boolean wrecked = entity.getHealth() <= 0.0F;
        boolean lampsOn = !wrecked && entity.isTurretActivated() && entity.hasOperationalPower();

        renderPass(entity, partialTicks, poseStack, mesh,
                bufferSource.getBuffer(MAIN_RENDER_TYPE), packedLight, wrecked, MeshPass.MAIN);
        renderPass(entity, partialTicks, poseStack, mesh,
                bufferSource.getBuffer(lampsOn ? LIGHTS_ON_BASE_RENDER_TYPE : LIGHTS_OFF_BASE_RENDER_TYPE),
                packedLight, wrecked, MeshPass.LAMP_BASE);
        if (lampsOn) {
            renderPass(entity, partialTicks, poseStack, mesh,
                    bufferSource.getBuffer(LIGHTS_GLOW_RENDER_TYPE),
                    LightTexture.FULL_BRIGHT, false, MeshPass.LAMP_GLOW);
        }

        if (!wrecked && entity.hasClientMuzzleFlash()) {
            renderMuzzleFlash(entity, partialTicks, poseStack, bufferSource);
        }
    }

    private static void renderPass(
            StormTurretEntity entity,
            float partialTicks,
            PoseStack poseStack,
            DnvTurretMesh.MeshData mesh,
            VertexConsumer consumer,
            int packedLight,
            boolean wrecked,
            MeshPass pass) {
        renderPart(mesh.part("base_static"), poseStack, consumer, packedLight, wrecked, pass);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getVisualTurretYaw(partialTicks)));
        renderPart(mesh.part("yaw_assembly"), poseStack, consumer, packedLight, wrecked, pass);

        poseStack.translate(PITCH_PIVOT_X, PITCH_PIVOT_Y, PITCH_PIVOT_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getVisualTurretPitch(partialTicks)));
        poseStack.translate(-PITCH_PIVOT_X, -PITCH_PIVOT_Y, -PITCH_PIVOT_Z);
        renderPart(mesh.part("pitch_assembly"), poseStack, consumer, packedLight, wrecked, pass);
        poseStack.popPose();
    }

    private static void renderMuzzleFlash(
            StormTurretEntity entity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getVisualTurretYaw(partialTicks)));
        poseStack.translate(PITCH_PIVOT_X, PITCH_PIVOT_Y, PITCH_PIVOT_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getVisualTurretPitch(partialTicks)));
        poseStack.translate(-PITCH_PIVOT_X, -PITCH_PIVOT_Y, -PITCH_PIVOT_Z);
        float muzzleX = (entity.getShotSequence() & 1) == 1 ? MUZZLE_X : -MUZZLE_X;
        renderHpj11MuzzleFlash(poseStack, bufferSource, muzzleX);
        poseStack.popPose();
    }

    /** Three forward petals plus a full-texture flare from an asset present in both SBW profiles. */
    private static void renderHpj11MuzzleFlash(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            float muzzleX) {
        float scaleX = HPJ_EFFECT_SCALE * (float) (2.0D + 0.8D * (Math.random() - 0.5D));
        float scaleY = HPJ_EFFECT_SCALE * (float) (2.0D + 0.8D * (Math.random() - 0.5D));
        float rotation = (float) (0.5D * (Math.random() - 0.5D));

        poseStack.pushPose();
        poseStack.translate(muzzleX, MUZZLE_Y, MUZZLE_Z);
        poseStack.mulPose(Axis.ZP.rotation(rotation));
        poseStack.scale(scaleX, scaleY, (scaleX + scaleY) * 0.5F);
        VertexConsumer consumer = bufferSource.getBuffer(
                ModRenderTypes.MUZZLE_FLASH_TYPE.apply(MUZZLE_FLASH_TEXTURE));

        for (float angle : HPJ_STREAK_ANGLES) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
            PoseStack.Pose streakPose = poseStack.last();
            float halfWidth = HPJ_FLASH_WIDTH * 0.5F;
            emitFlareVertex(consumer, streakPose, -halfWidth, 0.0F, 0.0F,
                    0.0F, 1.0F, 0.0F, -1.0F, 0.0F);
            emitFlareVertex(consumer, streakPose, halfWidth, 0.0F, 0.0F,
                    1.0F, 1.0F, 0.0F, -1.0F, 0.0F);
            emitFlareVertex(consumer, streakPose, halfWidth, 0.0F, HPJ_FLASH_LENGTH,
                    1.0F, 0.0F, 0.0F, -1.0F, 0.0F);
            emitFlareVertex(consumer, streakPose, -halfWidth, 0.0F, HPJ_FLASH_LENGTH,
                    0.0F, 0.0F, 0.0F, -1.0F, 0.0F);
            poseStack.popPose();
        }

        PoseStack.Pose starPose = poseStack.last();
        float halfStar = HPJ_STAR_SIZE * 0.5F;
        emitFlareVertex(consumer, starPose, -halfStar, -halfStar, 0.01F,
                0.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        emitFlareVertex(consumer, starPose, halfStar, -halfStar, 0.01F,
                1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        emitFlareVertex(consumer, starPose, halfStar, halfStar, 0.01F,
                1.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        emitFlareVertex(consumer, starPose, -halfStar, halfStar, 0.01F,
                0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        poseStack.popPose();
    }

    private static void emitFlareVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            float normalX,
            float normalY,
            float normalZ) {
        consumer.vertex(pose.pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
    }

}
