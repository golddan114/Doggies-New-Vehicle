package com.liucodex.doggiesnewvehicle.client;

import com.atsuishio.superbwarfare.client.renderer.projectile.BasicProjectileRenderer;
import com.liucodex.doggiesnewvehicle.armored.ApocalypseContent;
import com.liucodex.doggiesnewvehicle.armored.ApocalypseRig;
import com.liucodex.doggiesnewvehicle.armored.DnvApocalypseEntity;
import com.liucodex.doggiesnewvehicle.armored.DnvApocalypseMissile;
import com.liucodex.doggiesnewvehicle.armored.DnvAlliedApocalypseEntity;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh;
import com.liucodex.doggiesnewvehicle.client.mesh.ApocalypseRunningGear;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvQuadStream;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;

/** Uses the exact server muzzle/collision matrices and the original instanced running gear. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ApocalypseRenderer extends EntityRenderer<DnvApocalypseEntity> {
    private static final int LIVE_COLOR = 176;
    private static final ResourceLocation TEXTURE = texture("apocalypse");
    private static final ResourceLocation ALLIED_TEXTURE = texture("apocalypse_allied");
    private static final RenderType ARMOR = RenderType.entityCutout(TEXTURE);
    private static final RenderType ALLIED_ARMOR = RenderType.entityCutout(ALLIED_TEXTURE);
    private static final RenderType GEAR = RenderType.entityCutout(
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "textures/bedrock/vehicle/t_90a.png"));
    private static final RenderType WINDOWS = RenderType.eyes(texture("apocalypse_emission"));
    private final ApocalypseRunningGear runningGear = new ApocalypseRunningGear();

    public ApocalypseRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 3.4F;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "textures/entity/" + name + ".png");
    }

    @Override public void render(DnvApocalypseEntity tank, float yaw, float partial, PoseStack pose,
            MultiBufferSource buffers, int light) {
        var rig = ApocalypseRig.INSTANCE;
        var mesh = DnvTurretMesh.APOCALYPSE.get(DnvRenderDetail.distanceSquared(tank));
        int armorColor = LIVE_COLOR;
        RenderType armor = tank instanceof DnvAlliedApocalypseEntity ? ALLIED_ARMOR : ARMOR;
        for (var entry : rig.parts.entrySet()) {
            pose.pushPose();
            apply(tank, tank.groupTransform(entry.getValue(), partial), partial, pose);
            var part = mesh.part(entry.getKey());
            draw(part, pose, buffers, armor, light, tank.isWreck(), armorColor);
            pose.popPose();
        }
        pose.pushPose();
        apply(tank, tank.groupTransform("Body", partial), partial, pose);
        // One armor batch, one track batch, then one emissive batch. Never reuse a consumer across a type switch.
        if (stationary(tank)) {
            var gear = runningGear.sample(tank, mesh, tank.wheelAngle(true, partial), tank.wheelAngle(false, partial),
                    tank.trackPhase(true, partial), tank.trackPhase(false, partial));
            DnvTurretMeshRenderSupport.renderQuads(gear.wheels(), pose, buffers.getBuffer(armor), light, tank.isWreck() ? 72 : armorColor);
            DnvTurretMeshRenderSupport.renderQuads(gear.tracks(), pose, buffers.getBuffer(GEAR), light, tank.isWreck() ? 72 : 220);
        } else {
            movingGear(tank, mesh, partial, pose, buffers, armor, light);
        }
        pose.popPose();
        if (!tank.isWreck() && tank.getEnergy() > 0) {
            var windows = buffers.getBuffer(WINDOWS);
            for (var entry : rig.parts.entrySet()) {
                var part = mesh.part(entry.getKey());
                if (part.emissive().length == 0) continue;
                pose.pushPose();
                apply(tank, tank.groupTransform(entry.getValue(), partial), partial, pose);
                DnvTurretMeshRenderSupport.renderStream(part.emissive(), pose, windows, LightTexture.FULL_BRIGHT, 255);
                pose.popPose();
            }
        }
        super.render(tank, yaw, partial, pose, buffers, light);
    }

    private static boolean stationary(DnvApocalypseEntity tank) {
        for (boolean left : new boolean[] {true, false}) {
            if (tank.wheelAngle(left, 0) != tank.wheelAngle(left, 1)
                    || tank.trackPhase(left, 0) != tank.trackPhase(left, 1)) return false;
        }
        return true;
    }

    private static void movingGear(DnvApocalypseEntity tank, DnvTurretMesh.MeshData mesh, float partial,
            PoseStack pose, MultiBufferSource buffers, RenderType armor, int light) {
        var rig = ApocalypseRig.INSTANCE;
        var wheels = buffers.getBuffer(armor);
        for (var wheel : rig.wheels) {
            pose.pushPose();
            Vec3 p = wheel.position();
            pose.translate(p.x, p.y, p.z);
            pose.mulPose(Axis.XP.rotationDegrees(tank.wheelAngle(p.x < 0, partial)));
            DnvTurretMeshRenderSupport.renderStream(mesh.part(wheel.part()).main(), pose, wheels, light, tank.isWreck() ? 72 : LIVE_COLOR);
            pose.popPose();
        }
        var tracks = buffers.getBuffer(GEAR);
        float[] links = DnvQuadStream.quads(mesh.part("track_link").main());
        for (var track : rig.tracks) {
            double distance = tank.trackPhase(track.x() < 0, partial) * track.length();
            for (int i = 0; i < track.count(); i++) {
                var link = ApocalypseRig.link(track, distance + track.length() * i / track.count());
                pose.pushPose();
                pose.translate(link.position().x, link.position().y, link.position().z);
                pose.mulPose(Axis.XP.rotation((float) link.angle()));
                DnvTurretMeshRenderSupport.renderQuads(links, pose, tracks, light, tank.isWreck() ? 72 : 220);
                pose.popPose();
            }
        }
    }

    private static void apply(DnvApocalypseEntity tank, Matrix4d world, float partial, PoseStack pose) {
        Vec3 position = tank.getPosition(partial);
        Matrix4f local = new Matrix4f(new Matrix4d().translation(-position.x, -position.y, -position.z).mul(world));
        pose.last().pose().mul(local);
        pose.last().normal().mul(new Matrix3f(local).invert().transpose());
    }

    private static void draw(DnvTurretMesh.MeshPart part, PoseStack pose, MultiBufferSource buffers,
            RenderType type, int light, boolean wrecked, int color) {
        DnvTurretMeshRenderSupport.renderStream(part.main(), pose, buffers.getBuffer(type), light,
                wrecked ? Math.min(72, color) : color);
    }

    @Override public boolean shouldRender(DnvApocalypseEntity tank, Frustum camera, double x, double y, double z) {
        return tank.shouldRender(x, y, z) && camera.isVisible(tank.getBoundingBoxForCulling().inflate(5, 2, 5));
    }
    @Override public ResourceLocation getTextureLocation(DnvApocalypseEntity tank) {
        return tank instanceof DnvAlliedApocalypseEntity ? ALLIED_TEXTURE : TEXTURE;
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ApocalypseContent.TANK.get(), ApocalypseRenderer::new);
        event.registerEntityRenderer(ApocalypseContent.ALLIED_TANK.get(), ApocalypseRenderer::new);
        event.registerEntityRenderer(ApocalypseContent.MISSILE.get(), context ->
                new BasicProjectileRenderer<DnvApocalypseMissile>(context) {
                    @Override
                    public ResourceLocation getTextureLocation(DnvApocalypseMissile missile) {
                        return ResourceLocation.fromNamespaceAndPath("superbwarfare", "textures/bedrock/projectile/javelin_missile.png");
                    }
                });
    }
}
