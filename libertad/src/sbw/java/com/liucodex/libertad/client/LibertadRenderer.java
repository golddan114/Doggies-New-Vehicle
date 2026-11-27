package com.liucodex.libertad.client;

import com.liucodex.libertad.Libertad;
import com.liucodex.libertad.ship.LibertadEntity;
import com.liucodex.libertad.ship.ShipModel;
import com.liucodex.libertad.naval.MountGeometry.Angles;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4d;

@Mod.EventBusSubscriber(modid = Libertad.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LibertadRenderer extends EntityRenderer<LibertadEntity> {
    private static final ResourceLocation BASE = new ResourceLocation(Libertad.ID, "textures/entity/baked.png");
    static final ResourceLocation SEARCHLIGHT_FACE = new ResourceLocation(Libertad.ID, "textures/entity/searchlight-face.png");
    private static final ResourceLocation SEARCHLIGHT_EMISSION = new ResourceLocation(Libertad.ID, "textures/entity/searchlight-emission.png");
    private final Map<ResourceLocation, RenderType> layers = new HashMap<>();
    private final RenderType litSearchlight = RenderType.eyes(SEARCHLIGHT_EMISSION);

    public LibertadRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void render(LibertadEntity ship, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        ShipModel model = ShipModel.get();
        var position = ship.getPosition(partial);
        Matrix4d body = new Matrix4d().translation(-position.x, -position.y, -position.z).mul(ship.getVehicleTransform(partial));
        var pose = new ShipRenderPose();
        var base = stack.last();
        double spin = ship.propellerAngle(partial);
        int lastPart = -1;
        int shade = ship.isWreck() ? 100 : 255;
        boolean lightsOn = ship.searchlightsOn();
        ShipModel.Batch glowingLamps = null;
        for (ShipModel.Batch batch : model.batches) {
            int part = batch.part();
            if (part != lastPart) {
                var angles = part > 0 && part < ShipModel.FIRST_PROPELLER ? ship.angles((part - 1) / 2, partial) : null;
                if (part >= ShipModel.PARTS) {
                    var mount = model.aaMounts.get((part - ShipModel.PARTS) / 2);
                    angles = new Angles(ship.aaHeading(mount.zone(), partial), ship.aaPitch(mount.zone(), partial));
                }
                pose.update(model, body, base.pose(), base.normal(), part, angles, spin);
                lastPart = part;
            }
            var consumer = buffers.getBuffer(layers.computeIfAbsent(batch.texture(), TriangleLayer::of));
            emitBatch(batch.vertices(), pose, consumer, light, shade);
            if (lightsOn && batch.texture().equals(SEARCHLIGHT_FACE)) glowingLamps = batch;
        }
        if (glowingLamps != null) {
            pose.update(model, body, base.pose(), base.normal(), glowingLamps.part(), null, spin);
            emitSearchlightGlow(glowingLamps.vertices(), pose, buffers.getBuffer(litSearchlight));
        }
        super.render(ship, yaw, partial, stack, buffers, light);
    }

    private static void emitBatch(float[] vertices, ShipRenderPose pose, VertexConsumer consumer, int light, int shade) {
        for (int i = 0; i < vertices.length; i += 8) {
            emitVertex(vertices, i, pose, consumer, light, shade);
        }
    }

    static void emitSearchlightGlow(float[] vertices, ShipRenderPose pose, VertexConsumer consumer) {
        // The native eyes layer consumes quads. Repeating C preserves each source triangle A-B-C.
        for (int i = 0; i < vertices.length; i += 24) {
            emitVertex(vertices, i, pose, consumer, LightTexture.FULL_BRIGHT, 255);
            emitVertex(vertices, i + 8, pose, consumer, LightTexture.FULL_BRIGHT, 255);
            emitVertex(vertices, i + 16, pose, consumer, LightTexture.FULL_BRIGHT, 255);
            emitVertex(vertices, i + 16, pose, consumer, LightTexture.FULL_BRIGHT, 255);
        }
    }

    private static void emitVertex(float[] vertices, int i, ShipRenderPose pose, VertexConsumer consumer, int light, int shade) {
        consumer.vertex(pose.position, vertices[i], vertices[i + 1], vertices[i + 2]).color(shade, shade, shade, 255)
                .uv(vertices[i + 6], vertices[i + 7]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.normal, vertices[i + 3], vertices[i + 4], vertices[i + 5]).endVertex();
    }

    @Override public boolean shouldRender(LibertadEntity ship, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(ship.getBoundingBoxForCulling());
    }

    @Override public ResourceLocation getTextureLocation(LibertadEntity ship) { return BASE; }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Libertad.SHIP.get(), LibertadRenderer::new);
    }

    private abstract static class TriangleLayer extends RenderType {
        private TriangleLayer() { super("libertad", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, false, false, () -> {}, () -> {}); }

        static RenderType of(ResourceLocation texture) {
            return create("libertad_triangles", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 65536, false, false,
                    CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false)).setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(true));
        }

    }
}
