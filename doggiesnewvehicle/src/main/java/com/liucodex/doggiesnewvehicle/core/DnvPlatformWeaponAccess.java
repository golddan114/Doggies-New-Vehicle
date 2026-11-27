package com.liucodex.doggiesnewvehicle.core;

import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
import com.liucodex.doggiesnewvehicle.core.DnvWeaponFireGate.Reason;

/** Runtime-only platform port. Exact resources and mutable vehicle state stay in the adapter. */
public interface DnvPlatformWeaponAccess {
  boolean applies(String weapon);
  boolean aim(String weapon, Point desired);
  Point angles(int seat);
  boolean contains(String weapon, Point desired);
  Reason inspectProfile(Profile profile);

  DnvPlatformWeaponAccess UNAVAILABLE = new DnvPlatformWeaponAccess() {
    public boolean applies(String weapon) { return false; }
    public boolean aim(String weapon, Point desired) { return false; }
    public Point angles(int seat) { throw new IllegalStateException("platform pose unavailable"); }
    public boolean contains(String weapon, Point desired) { return false; }
    public Reason inspectProfile(Profile profile) { return Reason.BALLISTIC_PROFILE_UNVERIFIED; }
  };

  record Profile(String weapon, String ammo, String projectile, String shellType,
      double velocity, double gravity, double spread, double explosionRadius,
      int amount, int delay, boolean customModifiers) {
    public DnvProjectilePreflight.DryAirProfile dryAir(double maxRange, int maxTicks) {
      return new DnvProjectilePreflight.DryAirProfile((float) velocity, (float) gravity,
          (float) spread, explosionRadius, maxRange, maxTicks);
    }
  }
}
