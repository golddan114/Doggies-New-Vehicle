package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.entity.DnvM1YunlongEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Single client boundary over the M1 platform entity. Render and viewport code consume immutable
 * projections instead of each adding another client-to-platform dependency.
 */
final class DnvM1ClientBridge {
  private DnvM1ClientBridge() {}
  static float modelScale() { return DnvM1YunlongEntity.MODEL_SCALE; }
  static com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile profile(Entity entity) {
    return ((DnvM1YunlongEntity)entity).profile();
  }
  static float recoil(Entity entity,int barrel,float partialTick){return ((DnvM1YunlongEntity)entity).barrelRecoil(barrel,partialTick);}

  static boolean supports(Entity entity) {
    return entity instanceof DnvM1YunlongEntity;
  }

  static boolean isRemoteCrew(Entity player) {
    return player != null && player.getVehicle() instanceof DnvM1YunlongEntity m1 && m1.isRemoteOperator(player);
  }

  static VisualState visual(Entity entity, float partialTick) {
    if (!(entity instanceof DnvM1YunlongEntity m1)) throw new IllegalArgumentException("Not an M1 entity");
    return new VisualState(
        m1.getVisualBaseYaw(partialTick),
        m1.getVisualAimYaw(partialTick),
        m1.getVisualAimElevation(partialTick),
        m1.getRecoilProgress(partialTick),
        m1.hasOperationalPower(),
        m1.isDamaged(),
        m1.isDestroyed());
  }

  static CameraPose cameraPose(Entity entity, float partialTick) {
    if (!(entity instanceof DnvM1YunlongEntity m1)) throw new IllegalArgumentException("Not an M1 entity");
    return new CameraPose(
        m1.getAdsCameraPosition(partialTick),
        m1.getViewYRot(partialTick),
        m1.getViewXRot(partialTick));
  }

  record VisualState(
      float baseYaw,
      float yaw,
      float elevation,
      float recoil,
      boolean powered,
      boolean damaged,
      boolean destroyed) {}

  record CameraPose(Vec3 position, float yaw, float pitch) {}
  static ThirdPerson thirdPerson(Entity entity, float partialTick) {
    if (!(entity instanceof DnvM1YunlongEntity m1)) throw new IllegalArgumentException("Not an M1 entity");
    var offset = m1.getThirdPersonCameraPosition();
    return new ThirdPerson(m1.getPosition(partialTick).add(0, m1.profile().pitch.y + offset.y, 0), Math.max(4, offset.x));
  }
  record ThirdPerson(Vec3 position, double distance) {}
}
