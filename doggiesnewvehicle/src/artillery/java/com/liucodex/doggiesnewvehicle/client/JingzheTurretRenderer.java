package com.liucodex.doggiesnewvehicle.client;

import static com.liucodex.doggiesnewvehicle.client.DnvTurretMeshRenderSupport.*;

import com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh;
import com.liucodex.doggiesnewvehicle.entity.JingzheTurretEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class JingzheTurretRenderer extends DnvTurretRenderer<JingzheTurretEntity> {
    private static final ResourceLocation MAIN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "doggiesnewvehicle", "textures/entity/jingzhe_turret.png");
    private static final ResourceLocation LIGHTS_ON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "doggiesnewvehicle", "textures/entity/jingzhe_turret_lights_on.png");
    private static final ResourceLocation LIGHTS_OFF_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "doggiesnewvehicle", "textures/entity/jingzhe_turret_lights_off.png");
    private static final ResourceLocation LIGHTS_DESTROYED_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "doggiesnewvehicle", "textures/entity/jingzhe_turret_lights_destroyed.png");
    private static final RenderType MAIN_RENDER_TYPE = RenderType.entityCutout(MAIN_TEXTURE);
    private static final RenderType DARK_LAMP_RENDER_TYPE = RenderType.entityCutout(LIGHTS_OFF_TEXTURE);
    private static final RenderType ACTIVE_GLOW_RENDER_TYPE = RenderType.eyes(LIGHTS_ON_TEXTURE);
    private static final RenderType DESTROYED_GLOW_RENDER_TYPE = RenderType.eyes(LIGHTS_DESTROYED_TEXTURE);

    private static final float KEY_DROP = 0.341166F;
    private static final float SCROLL_OUTWARD = 1.256428F;
    private static final float SCROLL_UPWARD = 1.256428F;
    // The Blender lamp prisms sit almost flush with the scroll shell. A tiny
    // horizontal expansion prevents the shell from winning the depth test on
    // the two exposed lamp faces without changing the authored silhouette.
    private static final float SCROLL_LAMP_SURFACE_SCALE = 1.02F;
    private static final float ORBIT_CENTER_Y = 2.639705F;
    private static final float[] SCROLL_PIVOT_X = {
        -0.044486F, 1.197801F, 0.784767F, -0.712788F, -1.225294F
    };
    private static final float[] SCROLL_PIVOT_Z = {
        1.273896F, 0.435964F, -1.004457F, -1.056752F, 0.351347F
    };

    public JingzheTurretRenderer(EntityRendererProvider.Context context) {
        super(context, MAIN_TEXTURE, JingzheTurretRenderer::renderTurret);
    }

    private static void renderTurret(
            JingzheTurretEntity entity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        DnvTurretMesh.MeshData mesh = DnvTurretMesh.JINGZHE.get(DnvRenderDetail.distanceSquared(entity));
        boolean destroyed = entity.getHealth() <= 0.0F;
        boolean powered = entity.hasOperationalPower() && !destroyed;
        int bodyColor = destroyed ? 92 : 255;
        float fixedLampBrightness = destroyed
                ? destroyedBlink(entity, partialTicks)
                : powered ? 1.0F : 0.0F;
        float coreRatio = powered ? entity.getCoreEnergyRatio() : 0.0F;
        float outward = entity.getScrollOutwardProgress(partialTicks);
        float upward = entity.getScrollUpwardProgress(partialTicks);

        renderPass(entity, partialTicks, poseStack, mesh,
                bufferSource.getBuffer(MAIN_RENDER_TYPE), packedLight, MeshPass.MAIN,
                bodyColor, fixedLampBrightness, coreRatio, outward, upward, destroyed, powered);
        renderPass(entity, partialTicks, poseStack, mesh,
                bufferSource.getBuffer(DARK_LAMP_RENDER_TYPE), packedLight, MeshPass.LAMP_BASE,
                bodyColor, fixedLampBrightness, coreRatio, outward, upward, destroyed, powered);
        if (destroyed || powered) {
            renderPass(entity, partialTicks, poseStack, mesh,
                    bufferSource.getBuffer(destroyed
                            ? DESTROYED_GLOW_RENDER_TYPE
                            : ACTIVE_GLOW_RENDER_TYPE),
                    LightTexture.FULL_BRIGHT, MeshPass.LAMP_GLOW,
                    bodyColor, fixedLampBrightness, coreRatio, outward, upward, destroyed, powered);
        }
    }

    private static void renderPass(
            JingzheTurretEntity entity,
            float partialTicks,
            PoseStack poseStack,
            DnvTurretMesh.MeshData mesh,
            VertexConsumer consumer,
            int packedLight,
            MeshPass pass,
            int bodyColor,
            float fixedLampBrightness,
            float coreRatio,
            float outward,
            float upward,
            boolean destroyed,
            boolean powered) {
        if (pass == MeshPass.MAIN) {
            renderStream(mesh.part("base_static").main(), poseStack, consumer, packedLight, bodyColor);
        } else {
            for (int lamp = 0; lamp < 8; lamp++) {
                renderLamp(mesh.part("panel_light_" + lamp), poseStack, consumer,
                        packedLight, fixedLampBrightness, pass);
            }

            poseStack.pushPose();
            poseStack.translate(0.0F, -KEY_DROP * entity.getKeyDropProgress(partialTicks), 0.0F);
            renderLamp(mesh.part("key_light"), poseStack, consumer,
                    packedLight, fixedLampBrightness, pass);
            poseStack.popPose();
        }

        if (pass == MeshPass.MAIN) {
            renderStream(mesh.part("core").main(), poseStack, consumer, packedLight, bodyColor);
        } else {
            for (int segment = 0; segment < 4; segment++) {
                float brightness = destroyed
                        ? destroyedBlink(entity, partialTicks)
                        : segmentBrightness(coreRatio, segment);
                renderLamp(mesh.part("core_light_" + segment), poseStack, consumer,
                        packedLight, brightness, pass);
            }
            renderLamp(mesh.part("core_light_4"), poseStack, consumer,
                    packedLight, fixedLampBrightness, pass);
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, ORBIT_CENTER_Y, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getVisualOrbitAngle(partialTicks)));
        poseStack.translate(0.0F, -ORBIT_CENTER_Y, 0.0F);
        for (int module = 0; module < JingzheTurretEntity.MODULE_COUNT; module++) {
            poseStack.pushPose();
            float baseRadius = Mth.sqrt(
                    SCROLL_PIVOT_X[module] * SCROLL_PIVOT_X[module]
                            + SCROLL_PIVOT_Z[module] * SCROLL_PIVOT_Z[module]);
            float radialX = baseRadius <= 1.0e-6F ? 0.0F : SCROLL_PIVOT_X[module] / baseRadius;
            float radialZ = baseRadius <= 1.0e-6F ? 0.0F : SCROLL_PIVOT_Z[module] / baseRadius;
            poseStack.translate(
                    radialX * SCROLL_OUTWARD * outward,
                    SCROLL_UPWARD * upward,
                    radialZ * SCROLL_OUTWARD * outward);
            if (pass == MeshPass.MAIN) {
                renderStream(mesh.part("scroll_" + module).main(),
                        poseStack, consumer, packedLight, bodyColor);
            } else {
                float moduleRatio = powered && entity.isCombatDeployed()
                        ? entity.getModuleChargeRatio(module, partialTicks)
                        : 0.0F;
                for (int segment = 0; segment < 4; segment++) {
                    float brightness = destroyed
                            ? destroyedBlink(entity, partialTicks)
                            : segmentBrightness(moduleRatio, segment);
                    renderScrollLamp(
                            module,
                            mesh.part("scroll_" + module + "_light_" + segment),
                            poseStack,
                            consumer,
                            packedLight,
                            brightness,
                            pass);
                }
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void renderScrollLamp(
            int module,
            DnvTurretMesh.MeshPart part,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            float brightness,
            MeshPass pass) {
        poseStack.pushPose();
        poseStack.translate(SCROLL_PIVOT_X[module], 0.0F, SCROLL_PIVOT_Z[module]);
        poseStack.scale(SCROLL_LAMP_SURFACE_SCALE, 1.0F, SCROLL_LAMP_SURFACE_SCALE);
        poseStack.translate(-SCROLL_PIVOT_X[module], 0.0F, -SCROLL_PIVOT_Z[module]);
        renderLamp(part, poseStack, consumer, packedLight, brightness, pass);
        poseStack.popPose();
    }

    private static float segmentBrightness(float ratio, int bottomToTopIndex) {
        return Mth.clamp(ratio * 4.0F - bottomToTopIndex, 0.0F, 1.0F);
    }

    private static float destroyedBlink(JingzheTurretEntity entity, float partialTicks) {
        double phase = (entity.tickCount + partialTicks) * Math.PI * 2.0D / 24.0D;
        return 0.30F + 0.70F * (float) ((Math.sin(phase) + 1.0D) * 0.5D);
    }

    private static void renderLamp(
            DnvTurretMesh.MeshPart part,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            float brightness,
            MeshPass pass) {
        if (pass == MeshPass.LAMP_BASE) {
            renderStream(part.emissive(), poseStack, consumer, packedLight, 255);
            return;
        }
        if (pass == MeshPass.LAMP_GLOW) {
            int glowColor = Mth.clamp(Math.round(brightness * 255.0F), 0, 255);
            if (glowColor > 0) {
                renderStream(part.emissive(), poseStack, consumer, packedLight, glowColor);
            }
        }
    }

}
