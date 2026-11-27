package com.liucodex.doggiesnewvehicle.entity;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Compatibility facade for the retained M1 identity, backed by the new author's anchors. */
final class DnvM1Pose {
  static final Vec3 PITCH = DnvDragonTurretProfile.M1.pitch;
  static final Vec3 MUZZLE = PITCH.add(DnvDragonTurretProfile.M1.projectiles.get(0));
  static final Vec3 CAMERA = DnvDragonTurretProfile.M1.ads;
  private DnvM1Pose() {}
  static Vec3 pitched(Vec3 point,float yaw,float elevation) { return DnvDragonTurretProfile.M1.posed(point,yaw,elevation); }
  static Vec3 direction(float yaw,float elevation) {
    return new Vec3(0,0,1).xRot(elevation*Mth.DEG_TO_RAD).yRot(-yaw*Mth.DEG_TO_RAD).normalize();
  }
  static List<AABB> deploymentClearance(float yaw) { return DnvDragonTurretProfile.M1.clearance(yaw); }
}
