package com.liucodex.doggiesnewvehicle.entity;

import net.minecraft.core.Rotations;
import net.minecraft.util.Mth;

/** Client interpolation fed by one atomic server-owned yaw/pitch metadata value. */
public final class DnvTurretPoseInterpolation {
  private Rotations previous = new Rotations(0.0F, 0.0F, 0.0F);
  private Rotations current = previous;
  private boolean initialized;

  public Motion advance(Rotations next) {
    if (next == null) throw new IllegalArgumentException("turret pose is required");
    if (!initialized) {
      previous = next;
      current = next;
      initialized = true;
      return Motion.NONE;
    }
    previous = current;
    current = next;
    return new Motion(
        Mth.wrapDegrees(current.getY() - previous.getY()),
        Mth.wrapDegrees(current.getX() - previous.getX()));
  }

  public float yaw(float partialTicks, Rotations fallback) {
    return initialized ? Mth.rotLerp(partialTicks, previous.getX(), current.getX()) : fallback.getX();
  }

  public float pitch(float partialTicks, Rotations fallback) {
    return initialized ? Mth.rotLerp(partialTicks, previous.getY(), current.getY()) : fallback.getY();
  }

  public float currentYaw(Rotations fallback) { return initialized ? current.getX() : fallback.getX(); }
  public float currentPitch(Rotations fallback) { return initialized ? current.getY() : fallback.getY(); }

  public record Motion(float pitchDelta, float yawDelta) {
    private static final Motion NONE = new Motion(0.0F, 0.0F);
  }
}
