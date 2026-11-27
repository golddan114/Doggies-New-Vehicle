package com.liucodex.doggiesnewvehicle.client;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * DNV-owned renderer shell for its authored turret meshes.
 *
 * <p>Superb Warfare 0.8.9.1 moved {@code AutoAimableEntity} off GeckoLib's vehicle base, and
 * 0.8.10 removes the old GeckoLib vehicle renderer entirely. Keeping the small vehicle-axis and
 * culling contract here lets DNV render its own meshes without pretending its entities are Gecko
 * animatables.
 */
abstract class DnvTurretRenderer<T extends VehicleEntity> extends EntityRenderer<T> {
  private final ResourceLocation texture;
  private final MeshRenderer<T> meshRenderer;

  protected DnvTurretRenderer(
      EntityRendererProvider.Context context,
      ResourceLocation texture,
      MeshRenderer<T> meshRenderer) {
    super(context);
    this.texture = texture;
    this.meshRenderer = meshRenderer;
    shadowRadius = 1.0F;
  }

  @Override
  public final void render(
      T entity,
      float entityYaw,
      float partialTick,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      int packedLight) {
    poseStack.pushPose();
    applyVehicleAxis(entity, entityYaw, partialTick, poseStack);
    meshRenderer.render(entity, partialTick, poseStack, bufferSource, packedLight);
    poseStack.popPose();
    super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
  }

  private static void applyVehicleAxis(
      VehicleEntity entity, float entityYaw, float partialTick, PoseStack poseStack) {
    float pivotY = (float) entity.getRotateOffsetHeight();
    poseStack.rotateAround(Axis.YP.rotationDegrees(-entityYaw), 0.0F, pivotY, 0.0F);
    poseStack.rotateAround(
        Axis.XP.rotationDegrees(
            Mth.lerp(
                partialTick,
                entity.xRotO + entity.getFakePitchO(),
                entity.getXRot() + entity.getFakePitch())),
        0.0F,
        pivotY,
        0.0F);
    poseStack.rotateAround(
        Axis.ZP.rotationDegrees(
            Mth.lerp(
                partialTick,
                entity.getPrevRoll() + entity.getFakeRollO(),
                entity.getRoll() + entity.getFakeRoll())),
        0.0F,
        pivotY,
        0.0F);
  }

  @Override
  public final boolean shouldRender(
      T entity, Frustum camera, double cameraX, double cameraY, double cameraZ) {
    if (!entity.shouldRender(cameraX, cameraY, cameraZ)) {
      return false;
    }
    if (entity.noCulling) {
      return true;
    }
    AABB bounds = entity.getBoundingBoxForCulling().inflate(5.0D);
    if (bounds.hasNaN() || bounds.getSize() == 0.0D) {
      bounds = new AABB(
          entity.getX() - 8.0D,
          entity.getY() - 6.0D,
          entity.getZ() - 8.0D,
          entity.getX() + 8.0D,
          entity.getY() + 6.0D,
          entity.getZ() + 8.0D);
    }
    return camera.isVisible(bounds);
  }

  @Override
  public final ResourceLocation getTextureLocation(T entity) {
    return texture;
  }

  @FunctionalInterface
  protected interface MeshRenderer<T> {
    void render(
        T entity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight);
  }
}
