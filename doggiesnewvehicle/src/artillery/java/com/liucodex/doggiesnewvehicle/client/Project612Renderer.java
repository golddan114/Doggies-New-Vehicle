package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.armored.DnvProject612Entity;
import com.liucodex.doggiesnewvehicle.armored.Project612Content;
import com.liucodex.doggiesnewvehicle.armored.Project612Rig;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvQuadStream;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;

/** Accepted 4,188-triangle geometry, with native chassis/main-gun matrices and instanced wheels/shoes. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class Project612Renderer extends EntityRenderer<DnvProject612Entity> {
    private static final ResourceLocation TEXTURE = texture("project612");
    private static final RenderType ARMOR = RenderType.entityCutout(TEXTURE);
    private static final RenderType TRACKS = RenderType.entityCutout(
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "textures/bedrock/vehicle/t_90a.png"));

    public Project612Renderer(EntityRendererProvider.Context context) { super(context); shadowRadius = (float) (3.6 * Project612Rig.INSTANCE.scale); }
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "textures/entity/" + name + ".png");
    }
    @Override public void render(DnvProject612Entity tank, float yaw, float partial, PoseStack pose,
            MultiBufferSource buffers, int light) {
        var rig = Project612Rig.INSTANCE;
        var mesh = DnvTurretMesh.PROJECT612.get(DnvRenderDetail.distanceSquared(tank));
        int color = tank.isWreck() ? 72 : 255;
        for (String part : new String[] {"Body", "Main"}) {
            pose.pushPose();
            apply(tank, tank.groupTransform(part, partial), partial, pose);
            DnvTurretMeshRenderSupport.renderStream(mesh.part(part).main(), pose, buffers.getBuffer(ARMOR), light, color);
            pose.popPose();
        }
        pose.pushPose();
        apply(tank, tank.groupTransform("Body", partial), partial, pose);
        var armor = buffers.getBuffer(ARMOR);
        for (var wheel : rig.wheels) {
            pose.pushPose();
            Vec3 p = wheel.position();
            pose.translate(p.x, p.y, p.z);
            pose.mulPose(Axis.XP.rotationDegrees(tank.wheelAngle(p.x < 0, partial)));
            DnvTurretMeshRenderSupport.renderStream(mesh.part(wheel.part()).main(), pose, armor, light, color);
            pose.popPose();
        }
        var tracks = buffers.getBuffer(TRACKS);
        float[] shoe = DnvQuadStream.quads(mesh.part("Track").main());
        for (var track : rig.tracks) {
            double distance = tank.trackPhase(track.x() < 0, partial) * track.length();
            for (int i = 0; i < track.count(); i++) {
                var link = Project612Rig.link(track, distance + track.length() * i / track.count());
                pose.pushPose();
                Vec3 p = link.position();
                pose.translate(p.x, p.y, p.z);
                pose.mulPose(Axis.XP.rotation((float) link.angle()));
                DnvTurretMeshRenderSupport.renderQuads(shoe, pose, tracks, light, color);
                pose.popPose();
            }
        }
        pose.popPose();
        super.render(tank, yaw, partial, pose, buffers, light);
    }
    private static void apply(DnvProject612Entity tank, Matrix4d world, float partial, PoseStack pose) {
        Vec3 p = tank.getPosition(partial);
        Matrix4f matrix = new Matrix4f(new Matrix4d().translation(-p.x, -p.y, -p.z).mul(world));
        pose.last().pose().mul(matrix);
        pose.last().normal().mul(new Matrix3f(matrix).invert().transpose());
    }
    @Override public boolean shouldRender(DnvProject612Entity tank, Frustum camera, double x, double y, double z) {
        return tank.shouldRender(x, y, z) && camera.isVisible(tank.getBoundingBoxForCulling().inflate(3));
    }
    @Override public ResourceLocation getTextureLocation(DnvProject612Entity tank) { return TEXTURE; }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Project612Content.TANK.get(), Project612Renderer::new);
    }
}
