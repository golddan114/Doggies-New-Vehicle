package com.liucodex.noise.client;

import com.liucodex.noise.client.mesh.NoiseDropRigMesh;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvQuadStream;
import com.liucodex.noise.entity.NoiseDropTimeline;
import com.liucodex.noise.entity.NoiseVehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Client-only visual carrier; the server keeps Noise at its final ground point. */
public final class NoiseDropRigLayer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "noise", "textures/entity/noise_drop_rig.png");
    private static final String[] GROUPS = {
        "drop_rig_core", "drop_rig_thruster_fl", "drop_rig_thruster_fr",
        "drop_rig_thruster_rl", "drop_rig_thruster_rr"
    };

    private NoiseDropRigLayer() {
    }

    public static void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            NoiseVehicleEntity entity,
            float partialTick) {
        if (!entity.isEntering()) return;
        double elapsed = NoiseDropTimeline.renderElapsedTicks(
                entity.getEntranceTicks(), partialTick);
        if (!NoiseDropTimeline.isRigVisible(elapsed)) return;
        NoiseDropRigMesh mesh = NoiseDropRigMesh.get();
        if (mesh.isEmpty()) return;

        poseStack.pushPose();
        float bodyYaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.translate(0.0D, entity.presentedRigOffset(elapsed), 0.0D);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (String group : GROUPS) {
            renderStream(mesh.part(group + "/body").stream(), poseStack, consumer, packedLight);
        }
        poseStack.popPose();
    }

    private static void renderStream(
            float[] stream,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f position = pose.pose();
        Matrix3f normal = pose.normal();
        float[] quads = DnvQuadStream.quads(stream);
        int stride = NoiseDropRigMesh.FLOATS_PER_VERTEX;
        for (int quad = 0; quad < quads.length; quad += stride * 4) {
            emit(quads, quad, position, normal, consumer, packedLight);
            emit(quads, quad + stride, position, normal, consumer, packedLight);
            emit(quads, quad + stride * 2, position, normal, consumer, packedLight);
            emit(quads, quad + stride * 3, position, normal, consumer, packedLight);
        }
    }

    private static void emit(
            float[] stream,
            int index,
            Matrix4f position,
            Matrix3f normal,
            VertexConsumer consumer,
            int packedLight) {
        consumer.vertex(position, stream[index], stream[index + 1], stream[index + 2])
                .color(255, 255, 255, 255)
                .uv(stream[index + 6], stream[index + 7])
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normal, stream[index + 3], stream[index + 4], stream[index + 5])
                .endVertex();
    }
}
