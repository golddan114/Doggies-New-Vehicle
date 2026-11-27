package com.liucodex.noise.client;

import com.atsuishio.superbwarfare.client.renderer.ModRenderTypes;
import com.liucodex.noise.entity.NoiseLaserBoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Instant full-length Waveforce-style beam with a Laser-Tower-like core. */
public final class NoiseLaserBoltRenderer extends EntityRenderer<NoiseLaserBoltEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(
            "superbwarfare", "textures/entity/white.png");

    public NoiseLaserBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override public boolean shouldRender(NoiseLaserBoltEntity entity,
            net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        // Vanilla uses the tiny projectile's body size, hiding this full ray after only a few blocks.
        return com.liucodex.doggiesnewvehicle.boss.BeamVisibility.nearby(new Vec3(x, y, z), entity.position(), entity.beamEnd())
                && frustum.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    public void render(
            NoiseLaserBoltEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight) {
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() <= 1.0E-8D) return;
        double horizontal = Math.hypot(velocity.x, velocity.z);
        float yaw = (float) (Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(velocity.y, horizontal) * Mth.RAD_TO_DEG);
        float length = entity.getBeamLength();
        float life = Mth.clamp(
                1.0F - (entity.tickCount + partialTick) / entity.getLifetime(),
                0.0F,
                1.0F);
        if (life <= 0.0F || length <= 0.05F) return;
        float pulse = 0.94F + 0.06F * Mth.sin((entity.tickCount + partialTick) * 2.4F);
        // Keep the very short-lived beam readable through its final frame.
        // The old linear alpha fade made most of the five-tick effect nearly
        // invisible by the time the client actually rendered it.
        float fade = Mth.clamp(life * 2.25F, 0.45F, 1.0F);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        // The entity stays at the muzzle for its short life. Centering the
        // prism halfway down +Z produces a clean instantaneous ray instead of
        // the former moving six-block light bar.
        poseStack.translate(0.0D, 0.0D, length * 0.5F);
        // Use SBW's own additive laser pass. Unlike entityTranslucent this is
        // not darkened by night lighting or Oculus shader packs, and matches
        // the Laser Tower / Waveforce Tower presentation.
        VertexConsumer laser = buffers.getBuffer(ModRenderTypes.LASER.apply(WHITE));
        drawBar(poseStack, laser,
                0.22F * pulse, length * 0.5F,
                255, 12, 2, Math.round(105.0F * fade));
        drawBar(poseStack, laser,
                0.115F * pulse, length * 0.5F,
                255, 92, 24, Math.round(210.0F * fade));
        drawBar(poseStack, laser,
                0.052F * pulse, length * 0.5F,
                255, 250, 232, Math.round(255.0F * fade));
        poseStack.popPose();
    }

    private static void drawBar(
            PoseStack poseStack,
            VertexConsumer consumer,
            float radius,
            float halfLength,
            int red,
            int green,
            int blue,
            int alpha) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        quad(consumer, matrix, normal, -radius, -radius, -halfLength,
                radius, -radius, -halfLength, radius, -radius, halfLength,
                -radius, -radius, halfLength, 0, -1, 0, red, green, blue, alpha);
        quad(consumer, matrix, normal, -radius, radius, halfLength,
                radius, radius, halfLength, radius, radius, -halfLength,
                -radius, radius, -halfLength, 0, 1, 0, red, green, blue, alpha);
        quad(consumer, matrix, normal, -radius, -radius, halfLength,
                -radius, radius, halfLength, -radius, radius, -halfLength,
                -radius, -radius, -halfLength, -1, 0, 0, red, green, blue, alpha);
        quad(consumer, matrix, normal, radius, -radius, -halfLength,
                radius, radius, -halfLength, radius, radius, halfLength,
                radius, -radius, halfLength, 1, 0, 0, red, green, blue, alpha);
    }

    private static void quad(
            VertexConsumer consumer,
            Matrix4f matrix,
            Matrix3f normal,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float nx, float ny, float nz,
            int red, int green, int blue, int alpha) {
        vertex(consumer, matrix, normal, x0, y0, z0, 0, 0, nx, ny, nz, red, green, blue, alpha);
        vertex(consumer, matrix, normal, x1, y1, z1, 1, 0, nx, ny, nz, red, green, blue, alpha);
        vertex(consumer, matrix, normal, x2, y2, z2, 1, 1, nx, ny, nz, red, green, blue, alpha);
        vertex(consumer, matrix, normal, x3, y3, z3, 0, 1, nx, ny, nz, red, green, blue, alpha);
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            Matrix3f normal,
            float x, float y, float z,
            float u, float v,
            float nx, float ny, float nz,
            int red, int green, int blue, int alpha) {
        consumer.vertex(matrix, x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(NoiseLaserBoltEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
