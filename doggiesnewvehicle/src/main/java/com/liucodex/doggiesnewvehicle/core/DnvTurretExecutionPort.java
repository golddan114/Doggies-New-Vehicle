package com.liucodex.doggiesnewvehicle.core;

import java.util.Optional;
import java.util.UUID;

/** Internal server port; a frame is an observation, never a transferable permission or saved lease. */
public interface DnvTurretExecutionPort {
  Frame inspect(UUID targetId);
  Result applyAim(Frame frame, DnvProjectilePreflight.Point direction);
  Result tryFire(Frame frame);
  void invalidate();
  boolean pending();

  /** Platform callbacks; generic values keep the core independent of Minecraft and SBW. */
  interface Hooks<T, V> {
    boolean active();
    boolean settled();
    boolean targetAllowed(T target);
    void submitNative(T owner, UUID targetId, V targetPos);
    void accepted(V direction);
  }

  /** Read-only module facts; the entity retains charge, cadence, persistence and effect ownership. */
  interface EffectHooks<T> {
    boolean active();
    EffectState state(int module);
    boolean targetAllowed(T target);
    boolean targetStillValid(T target);
    boolean pathClear(T target);
    boolean release(int module, T target);
  }

  record EffectState(String targetId, int state, int timer, int charge, boolean energyReserved,
      long lastReleaseTick, DnvStationEngagementCore.WeaponReadiness readiness) {}

  record Frame(UUID platformId, String dimension, String profileId, long profileRevision,
      long controlRevision, long tick, String station, String weapon, UUID targetId,
      DnvStationEngagementCore.WeaponReadiness readiness, String reason,
      Optional<DnvDeferredShot.Receipt> lastReceipt) {}

  record Result(boolean accepted, String reason) {
    public static Result rejected(String reason) { return new Result(false, reason); }
  }
}
