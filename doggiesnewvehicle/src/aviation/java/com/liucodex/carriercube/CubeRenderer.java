package com.liucodex.carriercube;

import com.atsuishio.superbwarfare.entity.projectile.Agm65Entity;
import com.atsuishio.superbwarfare.entity.projectile.BasicGeoProjectileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.atsuishio.superbwarfare.init.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CubePrototype.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CubeRenderer extends EntityRenderer<CubeVehicle> {
    private static final String[] PARTS = {"carrier","ship_lights","lift","radar","airframe","gear_nose","gear_left","gear_right","nozzle","nozzle_core","gun_cradle","gun"};
    private static final java.util.Map<String,ResourceLocation> MODELS = new java.util.HashMap<>();
    static {
        for (String key : PARTS) MODELS.put(key,new ResourceLocation(CubePrototype.ID,"shanyue/shanyue_"+key));
    }
    private final RandomSource modelRandom = RandomSource.create();
    private Agm65Entity displayMissile;

    private CubeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CubePrototype.CARRIER.get(), CubeRenderer::new);
        event.registerEntityRenderer(CubePrototype.AIRCRAFT.get(), CubeRenderer::new);
        event.registerEntityRenderer(CubePrototype.SPECIAL_MISSILE.get(), context ->
                new com.atsuishio.superbwarfare.client.renderer.projectile.BasicProjectileRenderer<FeiyingMissile>(context) {
                    @Override public ResourceLocation getTextureLocation(FeiyingMissile missile) {
                        return ResourceLocation.fromNamespaceAndPath("superbwarfare", "textures/bedrock/projectile/agm_65.png");
                    }
                });
    }

    @SubscribeEvent
    public static void registerMeshLoader(ModelEvent.RegisterGeometryLoaders event) {
        IGeometryLoader<CubeMesh> loader = (json, context) -> CubeMesh.load(json);
        event.register("mesh", loader);
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        MODELS.values().forEach(event::register);
    }

    @Override
    public void render(CubeVehicle cube, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        pose.pushPose();
        applyBodyRotation(cube, partialTick, pose);
        if (cube.carrier()) {
            pose.scale(CubeGeometry.SHIP_SCALE, CubeGeometry.SHIP_SCALE, CubeGeometry.SHIP_SCALE);
            int white=CubeWarpHull.whiteness(cube,partialTick);
            carrierParts(cube, partialTick, pose, buffers, light, 0);
            if (white > 0) carrierParts(cube, partialTick, pose, buffers, light, white);
        } else {
            var mc = Minecraft.getInstance();
            boolean gunnerView = cube.hoverMode() && mc.player != null && cube.getFirstPassenger() == mc.player
                    && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == mc.player;
            if (!gunnerView) part("airframe",pose,buffers,light);
            float fold = cube.gearFold(partialTick);
            if (fold < 1) {
                gear("gear_nose", 0, 1.0613, 3.5024, 1, 0, 108 * fold, pose, buffers, light);
                gear("gear_left", -1.5082, 1.7444, -1.9536, -.8660254F, .5F, 98 * fold, pose, buffers, light);
                gear("gear_right", 1.5082, 1.7444, -1.9536, -.8660254F, -.5F, 98 * fold, pose, buffers, light);
            }
            pose.pushPose();
            var pivot=CubeGunRig.PIVOT;
            var axle=CubeGunRig.TRAVERSE_AXIS;
            pose.translate(axle.x,axle.y,axle.z);
            pose.mulPose(Axis.YP.rotationDegrees(-cube.chinYaw(partialTick)));
            pose.translate(-axle.x,-axle.y,-axle.z);
            part("gun_cradle",pose,buffers,light);
            pose.translate(pivot.x,pivot.y,pivot.z);
            pose.mulPose(Axis.XP.rotationDegrees(cube.chinPitch(partialTick)));
            pose.translate(-pivot.x,-pivot.y,-pivot.z);
            part("gun",pose,buffers,light);
            pose.popPose();
            pose.pushPose();
            pose.translate(.103,1.933,-4.04);
            pose.mulPose(Axis.XP.rotationDegrees(-cube.nozzleAngle(partialTick)));
            pose.translate(-.103,-1.933,4.04);
            part("nozzle",pose,buffers,light);
            part("nozzle_core",pose,buffers,light);
            pose.popPose();
            displayMissile = renderStores(cube, "Missile", ModEntities.AGM_65.get(), displayMissile,
                    partialTick, pose, buffers, light);
        }
        pose.popPose();
        CubePropulsion.enqueue(cube);
        super.render(cube, yaw, partialTick, pose, buffers, light);
    }

    private <T extends MissileProjectile & BasicGeoProjectileEntity> T renderStores(CubeVehicle cube, String weapon,
            net.minecraft.world.entity.EntityType<T> type, T display, float partial, PoseStack pose,
            MultiBufferSource buffers, int light) {
        var gun = cube.getGunData(weapon);
        if (gun == null || gun.ammo.get() <= 0) return display;
        if (display == null || display.level() != cube.level()) {
            display = type.create(cube.level());
            if (display == null) return null;
            display.tickCount = display.getHiddenTicks() + 1;
            display.setSyncedTick(display.tickCount);
            display.setDeltaMovement(0,0,1);
        }
        var mounts = gun.compute().getShootPos().getPositions();
        for (int i=0; i<Math.min(mounts.size(),gun.ammo.get()); i++) {
            var mount = mounts.get(i);
            pose.pushPose();
            pose.translate(mount.x,mount.y,mount.z);
            pose.scale(.66F,.66F,.66F);
            entityRenderDispatcher.getRenderer(display).render(display,0,partial,pose,buffers,light);
            pose.popPose();
        }
        return display;
    }

    // Match SBW's vehicle transform used by seats, collision parts, optics and weapon sockets.
    static void applyBodyRotation(CubeVehicle cube, float partial, PoseStack pose) {
        double pivot = cube.getRotateOffsetHeight();
        pose.translate(0, pivot, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-cube.getYaw(partial)));
        pose.mulPose(Axis.XP.rotationDegrees(cube.getPitch(partial)));
        pose.mulPose(Axis.ZP.rotationDegrees(cube.getRoll(partial)));
        pose.translate(0, -pivot, 0);
    }

    private void carrierParts(CubeVehicle cube, float partialTick, PoseStack pose,
                              MultiBufferSource buffers, int light, int white) {
        part("carrier",pose,buffers,light,white);
        part("ship_lights", pose, buffers, cube.isWreck() ? light : LightTexture.FULL_BRIGHT,white);
        pose.pushPose();
        pose.translate(0,cube.liftHeight(partialTick) / CubeGeometry.SHIP_SCALE,0);
        part("lift",pose,buffers,light,white);
        pose.popPose();
        pose.pushPose();
        pose.translate(15.19,0,-44.15);
        pose.mulPose(Axis.YP.rotationDegrees((cube.tickCount+partialTick)*1.2F));
        pose.translate(-15.19,0,44.15);
        part("radar",pose,buffers,light,white);
        pose.popPose();
    }

    private void gear(String name, double x, double y, double z, float axisX, float axisZ, float degrees,
                      PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(new org.joml.Quaternionf().rotationAxis(degrees * net.minecraft.util.Mth.DEG_TO_RAD, axisX, 0, axisZ));
        pose.translate(-x, -y, -z);
        part(name, pose, buffers, light);
        pose.popPose();
    }

    private void part(String name, PoseStack pose,MultiBufferSource buffers,int light) {
        part(name,pose,buffers,light,0);
    }

    private void part(String name, PoseStack pose,MultiBufferSource buffers,int light,int white) {
        var model=Minecraft.getInstance().getModelManager().getModel(MODELS.get(name));
        boolean base = white == 0;
        var buffer = buffers.getBuffer(base ? RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS) : CubeWarpHull.GLOW);
        float alpha = base ? 1F : white / 32767F;
        // The baked quads carry the model's diffuse tint in each vertex.
        // The vanilla block-model convenience path drops that tint, exposing the white base sprite.
        modelRandom.setSeed(42L);
        for (var quad : model.getQuads(null, null, modelRandom, ModelData.EMPTY, RenderType.solid())) {
            buffer.putBulkData(pose.last(), quad, 1, 1, 1, alpha, base ? light : LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, base);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CubeVehicle cube) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
