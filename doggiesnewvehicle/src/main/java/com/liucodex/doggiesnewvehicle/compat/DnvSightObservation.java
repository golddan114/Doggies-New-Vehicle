package com.liucodex.doggiesnewvehicle.compat;

import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Short-lived sight result for one weapon origin, target body and weapon pose. */
public record DnvSightObservation(
    UUID targetId,
    Vec3 origin,
    AABB targetBounds,
    double eyeY,
    long poseKey,
    int checkedTick,
    int nextCheckTick,
    boolean clear) {
  private static final double MAX_MOVEMENT_SQR = 0.25D * 0.25D;

  public boolean reusable(UUID currentId, Vec3 currentOrigin, AABB currentBounds,
      double currentEyeY, long currentPoseKey, int tick) {
    return targetId.equals(currentId)
        && tick >= checkedTick && tick < nextCheckTick
        && origin.distanceToSqr(currentOrigin) <= MAX_MOVEMENT_SQR
        && endpointMovedSqr(targetBounds.minX, targetBounds.minY, targetBounds.minZ,
            currentBounds.minX, currentBounds.minY, currentBounds.minZ) <= MAX_MOVEMENT_SQR
        && endpointMovedSqr(targetBounds.maxX, targetBounds.maxY, targetBounds.maxZ,
            currentBounds.maxX, currentBounds.maxY, currentBounds.maxZ) <= MAX_MOVEMENT_SQR
        && Math.abs(currentEyeY - eyeY) <= 0.25D
        && poseKey == currentPoseKey;
  }

  private static double endpointMovedSqr(double ax, double ay, double az,
      double bx, double by, double bz) {
    double x = ax - bx;
    double y = ay - by;
    double z = az - bz;
    return x * x + y * y + z * z;
  }
}
