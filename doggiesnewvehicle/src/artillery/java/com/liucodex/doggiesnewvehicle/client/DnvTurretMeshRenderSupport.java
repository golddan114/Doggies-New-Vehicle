package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvQuadStream;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Shared ETM triangle emission and render-pass policy for DNV turrets. */
final class DnvTurretMeshRenderSupport {
    private static final int WRECK_COLOR = 72;

    private DnvTurretMeshRenderSupport() {
    }

    static void renderPart(
            DnvTurretMesh.MeshPart part,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            boolean wrecked,
            MeshPass pass
    ) {
        float[] stream = pass == MeshPass.MAIN
                ? part.main()
                : part.emissive();
        int color = wrecked && pass != MeshPass.LAMP_GLOW
                ? WRECK_COLOR
                : 255;
        renderStream(stream, poseStack, consumer, packedLight, color);
    }

    static void renderStream(
            float[] stream,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int color
    ) {
        renderQuads(DnvQuadStream.quads(stream), poseStack, consumer, packedLight, color);
    }

    static void renderQuads(float[] quads, PoseStack poseStack, VertexConsumer consumer, int packedLight, int color) {
        renderTintedQuads(quads,poseStack,consumer,packedLight,color<<16|color<<8|color);
    }

    static void renderTintedQuads(float[] quads, PoseStack poseStack, VertexConsumer consumer, int packedLight, int rgb) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f positionMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        int vertexStride = DnvTurretMesh.FLOATS_PER_VERTEX;
        for (int quad = 0; quad < quads.length; quad += vertexStride * 4) {
            emitVertex(quads, quad, positionMatrix, normalMatrix,
                    consumer, packedLight, rgb);
            emitVertex(quads, quad + vertexStride,
                    positionMatrix, normalMatrix,
                    consumer, packedLight, rgb);
            emitVertex(quads, quad + vertexStride * 2,
                    positionMatrix, normalMatrix,
                    consumer, packedLight, rgb);
            emitVertex(quads, quad + vertexStride * 3,
                    positionMatrix, normalMatrix,
                    consumer, packedLight, rgb);
        }
    }

    private static void emitVertex(
            float[] stream,
            int index,
            Matrix4f positionMatrix,
            Matrix3f normalMatrix,
            VertexConsumer consumer,
            int packedLight,
            int rgb
    ) {
        consumer.vertex(
                        positionMatrix,
                        stream[index],
                        stream[index + 1],
                        stream[index + 2])
                .color(rgb>>16&255, rgb>>8&255, rgb&255, 255)
                .uv(stream[index + 6], stream[index + 7])
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(
                        normalMatrix,
                        stream[index + 3],
                        stream[index + 4],
                        stream[index + 5])
                .endVertex();
    }

    enum MeshPass {
        MAIN,
        LAMP_BASE,
        LAMP_GLOW
    }
}
