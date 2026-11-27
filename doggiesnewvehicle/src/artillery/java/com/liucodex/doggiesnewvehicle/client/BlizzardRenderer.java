package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.armored.BlizzardContent;
import com.liucodex.doggiesnewvehicle.armored.DnvBlizzardEntity;
import com.liucodex.doggiesnewvehicle.armored.DnvBlizzardMissile;
import com.liucodex.doggiesnewvehicle.armored.DnvBlizzardColdBolt;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
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

/** World-lit split-normal armor, cosmetic suspension and shared jet presentation. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BlizzardRenderer extends EntityRenderer<DnvBlizzardEntity> {
    static final ResourceLocation BASE = texture("blizzard_base"), EMISSION = texture("blizzard_emission");
    static final RenderType ARMOR = RenderType.entityCutoutNoCull(BASE),
            GLOW = com.liucodex.doggiesnewvehicle.client.fx.DnvJetRenderType.textured(EMISSION);
    private static final String[] GROUPS = {"Body", "Turret", "Barrel", "HatchLeft", "HatchRight"};
    public BlizzardRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 2.7F; }
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "textures/entity/" + name + ".png"); }
    @Override public void render(DnvBlizzardEntity tank, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        BlizzardClientEffects.track(tank);
        double bob = 0; // Suspension is part of the authoritative hull pose, including every muzzle.
        var mesh = DnvTurretMesh.BLIZZARD.get(DnvRenderDetail.distanceSquared(tank));
        Vec3 position = tank.getPosition(partial);
        Matrix4f[] transforms = new Matrix4f[GROUPS.length];
        Matrix3f[] normals = new Matrix3f[GROUPS.length];
        for (int i = 0; i < GROUPS.length; i++) {
            transforms[i] = new Matrix4f(new Matrix4d().translation(-position.x, -position.y, -position.z).mul(tank.groupTransform(GROUPS[i], partial)));
            normals[i] = new Matrix3f(transforms[i]).invert().transpose();
        }
        // Complete the armor batch before requesting glow. Consumers never cross a type switch.
        for (int pass = 0; pass < (tank.powered() ? 2 : 1); pass++) {
            // Use the same unlit additive material as the jets, including the rear energy compartment.
            var consumer = buffers.getBuffer(pass == 0 ? ARMOR : GLOW);
            for (int i = 0; i < GROUPS.length; i++) {
                var part = mesh.part(GROUPS[i]);
                float[] stream = pass == 0 ? part.main() : part.emissive();
                if (stream.length == 0) continue;
                pose.pushPose(); pose.last().pose().mul(transforms[i]); pose.last().normal().mul(normals[i]);
                DnvTurretMeshRenderSupport.renderStream(stream, pose, consumer,
                        pass == 0 ? light : LightTexture.FULL_BRIGHT, pass == 0 && tank.isWreck() ? 72 : 255);
                pose.popPose();
            }
        }
        BlizzardFxRenderer.render(tank, partial, pose, buffers, entityRenderDispatcher.distanceToSqr(tank), bob);
        BlizzardFxRenderer.pulses(tank, partial, pose, buffers);
        BlizzardBeamPass.enqueue(tank);
        super.render(tank, yaw, partial, pose, buffers, light);
    }
    public static void renderBeam(DnvBlizzardEntity tank, float partial, PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
        if (!tank.isAlive() || tank.isWreck()) return;
        Vec3 offset = tank.getPosition(partial).subtract(camera);
        pose.pushPose();
        try {
            pose.translate(offset.x, offset.y, offset.z);
            BlizzardFxRenderer.continuous(tank, partial, pose, buffers);
        } finally { pose.popPose(); }
    }
    @Override public boolean shouldRender(DnvBlizzardEntity tank, Frustum frustum, double x, double y, double z) {
        if (frustum.isVisible(tank.getBoundingBox().inflate(1))) return true;
        for (var laser : tank.presentation().lasers())
            if (laser.alpha(tank.presentation().time(1)) > 0
                    && frustum.isVisible(new net.minecraft.world.phys.AABB(laser.origin(), laser.end()).inflate(.4))) return true;
        for (var pulse : tank.presentation().pulses()) {
            Vec3 point = pulse.point(tank.presentation().time(1));
            if (point != null && frustum.isVisible(new net.minecraft.world.phys.AABB(point, point).inflate(2.5))) return true;
        }
        return false;
    }
    @Override public ResourceLocation getTextureLocation(DnvBlizzardEntity entity) { return BASE; }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BlizzardContent.TANK.get(), BlizzardRenderer::new);
        event.registerEntityRenderer(BlizzardContent.MISSILE.get(), MissileRenderer::new);
        event.registerEntityRenderer(BlizzardContent.BOLT.get(), ColdBoltRenderer::new);
    }
    private static final class ColdBoltRenderer extends EntityRenderer<DnvBlizzardColdBolt> {
        ColdBoltRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public void render(DnvBlizzardColdBolt bolt, float yaw, float partial, PoseStack pose,
                MultiBufferSource buffers, int light) {
            // Gameplay projectile arrival/removal has a different network phase. The accepted shot
            // is rendered on the hull snapshot clock, from its immutable world-space launch ray.
        }
        @Override public ResourceLocation getTextureLocation(DnvBlizzardColdBolt entity) { return BASE; }
    }
    private static final class MissileRenderer extends EntityRenderer<DnvBlizzardMissile> {
        private static final ResourceLocation SKIN = texture("blizzard_missile");
        private static final RenderType MATERIAL = RenderType.entityCutoutNoCull(SKIN);
        MissileRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public void render(DnvBlizzardMissile missile, float yaw, float partial,
                PoseStack pose, MultiBufferSource buffers, int light) {
            if (net.minecraft.client.Minecraft.getInstance().getCameraEntity() == missile) return;
            pose.pushPose();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                    -net.minecraft.util.Mth.rotLerp(partial, missile.yRotO, missile.getYRot())));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(net.minecraft.util.Mth.lerp(partial, missile.xRotO, missile.getXRot())));
            var part = DnvTurretMesh.BLIZZARD_MISSILE.get().part("Missile");
            DnvTurretMeshRenderSupport.renderStream(part.main(), pose, buffers.getBuffer(MATERIAL), light, 255);
            DnvTurretMeshRenderSupport.renderStream(part.emissive(), pose, buffers.getBuffer(RenderType.eyes(SKIN)), LightTexture.FULL_BRIGHT, 255);
            pose.popPose();
            BlizzardFxRenderer.coldBolt(pose, buffers, missile.getDeltaMovement(), .6);
            super.render(missile, yaw, partial, pose, buffers, light);
        }
        @Override public ResourceLocation getTextureLocation(DnvBlizzardMissile entity) { return SKIN; }
    }
    @SubscribeEvent public static void itemColor(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> 0xFFB0DEF0, com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryItems.CRYO_MISSILE.get());
    }
}
