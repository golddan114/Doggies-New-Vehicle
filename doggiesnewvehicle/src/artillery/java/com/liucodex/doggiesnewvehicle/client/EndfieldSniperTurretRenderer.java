package com.liucodex.doggiesnewvehicle.client;

import static com.liucodex.doggiesnewvehicle.client.DnvTurretMeshRenderSupport.*;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh;
import com.liucodex.doggiesnewvehicle.entity.ChangfengCannonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class EndfieldSniperTurretRenderer extends DnvTurretRenderer<ChangfengCannonEntity> {
    private static final ResourceLocation MAIN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "doggiesnewvehicle", "textures/entity/endfield_sniper_turret.png");
    private static final ResourceLocation LIGHTS_ON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "doggiesnewvehicle", "textures/entity/endfield_sniper_turret_lights_on.png");
    private static final ResourceLocation LIGHTS_OFF_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "doggiesnewvehicle", "textures/entity/endfield_sniper_turret_lights_off.png");
    private static final RenderType MAIN_RENDER_TYPE = RenderType.entityCutout(MAIN_TEXTURE);
    private static final RenderType LIGHTS_ON_BASE_RENDER_TYPE = RenderType.entityCutout(LIGHTS_ON_TEXTURE);
    private static final RenderType LIGHTS_OFF_BASE_RENDER_TYPE = RenderType.entityCutout(LIGHTS_OFF_TEXTURE);
    private static final RenderType LIGHTS_GLOW_RENDER_TYPE = RenderType.eyes(LIGHTS_ON_TEXTURE);

    private static final float PITCH_PIVOT_X = 0.0F;
    private static final float PITCH_PIVOT_Y = 1.886380F;
    private static final float PITCH_PIVOT_Z = -0.069457F;
    private static final float RECOIL_DISTANCE = 0.14F;
    private static final float RECOIL_TICKS = 7.0F;

    public EndfieldSniperTurretRenderer(EntityRendererProvider.Context context) {
        super(context, MAIN_TEXTURE, EndfieldSniperTurretRenderer::renderTurret);
    }

    private static void renderTurret(
            ChangfengCannonEntity entity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        DnvTurretMesh.MeshData mesh = DnvTurretMesh.ENDFIELD.get(DnvRenderDetail.distanceSquared(entity));
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
    }

    /**
     * Renders one complete material pass before requesting the next buffer.
     * Reusing a consumer after switching render types crashes newer SBW, while
     * switching for every individual part destroys batching and frame rate.
     */
    private static void renderPass(
            ChangfengCannonEntity entity,
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

        int recoilTime = entity.getEntityData().get(VehicleEntity.CANNON_RECOIL_TIME);
        float recoilPhase = Math.min(1.0F, Math.max(0.0F, (recoilTime - partialTicks) / RECOIL_TICKS));
        float recoilProgress = recoilPhase * recoilPhase * (3.0F - 2.0F * recoilPhase);
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, -RECOIL_DISTANCE * recoilProgress);
        renderPart(mesh.part("barrel_recoil"), poseStack, consumer, packedLight, wrecked, pass);
        poseStack.popPose();
        poseStack.popPose();
    }

}
