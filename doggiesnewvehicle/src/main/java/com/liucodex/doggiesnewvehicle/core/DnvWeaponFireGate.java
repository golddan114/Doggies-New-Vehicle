package com.liucodex.doggiesnewvehicle.core;

import java.util.Objects;

/** Pure final-fire checklist shared by weapon adapters; it never performs the fire side effect. */
public final class DnvWeaponFireGate {
  private DnvWeaponFireGate() {}

  public static Decision evaluate(Request request) {
    Objects.requireNonNull(request, "request");
    if (!request.coreAllows()) {
      return denied(Reason.CORE_HOLD, DnvAutonomyCore.ExecutionOutcome.UNAVAILABLE);
    }
    if (!request.stationAllows()) {
      return denied(Reason.STATION_HOLD, DnvAutonomyCore.ExecutionOutcome.UNAVAILABLE);
    }
    if (!request.policyAllows()) {
      return denied(Reason.POLICY_HOLD, DnvAutonomyCore.ExecutionOutcome.UNAVAILABLE);
    }
    if (!request.authorityAllows()) {
      return denied(Reason.AUTHORITY_DENIED,
          DnvAutonomyCore.ExecutionOutcome.AUTHORITY_DENIED);
    }
    if (!request.externalAllows()) {
      return denied(Reason.EXTERNAL_AUTHORIZATION_DENIED,
          DnvAutonomyCore.ExecutionOutcome.AUTHORITY_DENIED);
    }
    if (!request.corridorClear()) {
      return denied(Reason.FIRING_CORRIDOR_BLOCKED,
          DnvAutonomyCore.ExecutionOutcome.BLOCKED);
    }
    if (!request.aligned()) {
      return denied(Reason.NOT_ALIGNED, DnvAutonomyCore.ExecutionOutcome.BLOCKED);
    }
    if (!request.cooldownReady()) {
      return denied(Reason.COOLDOWN, DnvAutonomyCore.ExecutionOutcome.COOLDOWN);
    }
    if (!request.ammoAvailable()) {
      return denied(Reason.OUT_OF_AMMO, DnvAutonomyCore.ExecutionOutcome.OUT_OF_AMMO);
    }
    return new Decision(true, Reason.READY, DnvAutonomyCore.ExecutionOutcome.SUCCEEDED);
  }

  private static Decision denied(
      Reason reason, DnvAutonomyCore.ExecutionOutcome outcome) {
    return new Decision(false, reason, outcome);
  }

  /** Additional observations can only restrict an otherwise allowed attempt. */
  public static Decision restrict(Decision previous, Reason rejection) {
    Objects.requireNonNull(previous, "previous");
    if (rejection == Reason.READY) throw new IllegalArgumentException("a restriction cannot authorize fire");
    return !previous.allowed() || rejection == null ? previous
        : denied(rejection, DnvAutonomyCore.ExecutionOutcome.BLOCKED);
  }

  public record Request(
      boolean coreAllows,
      boolean stationAllows,
      boolean policyAllows,
      boolean authorityAllows,
      boolean externalAllows,
      boolean corridorClear,
      boolean aligned,
      boolean cooldownReady,
      boolean ammoAvailable) {
    public Request withAuthority(boolean value) {
      return new Request(
          coreAllows, stationAllows, policyAllows, value, externalAllows,
          corridorClear, aligned, cooldownReady, ammoAvailable);
    }

    public Request withCorridor(boolean value) {
      return new Request(
          coreAllows, stationAllows, policyAllows, authorityAllows, externalAllows,
          value, aligned, cooldownReady, ammoAvailable);
    }

    public Request withAmmo(boolean value) {
      return new Request(
          coreAllows, stationAllows, policyAllows, authorityAllows, externalAllows,
          corridorClear, aligned, cooldownReady, value);
    }
  }

  public record Decision(
      boolean allowed, Reason reason, DnvAutonomyCore.ExecutionOutcome outcome) {
    public Decision {
      Objects.requireNonNull(reason, "reason");
      Objects.requireNonNull(outcome, "outcome");
      if (allowed != (reason == Reason.READY)) {
        throw new IllegalArgumentException("only READY can authorize a fire attempt");
      }
    }
  }

  public enum Reason {
    READY,
    CORE_HOLD,
    STATION_HOLD,
    POLICY_HOLD,
    AUTHORITY_DENIED,
    EXTERNAL_AUTHORIZATION_DENIED,
    FIRING_CORRIDOR_BLOCKED,
    NOT_ALIGNED,
    COOLDOWN,
    OUT_OF_AMMO,
    WEAPON_UNAVAILABLE,
    TARGET_INVALID,
    BALLISTIC_AMMO_UNSUPPORTED,
    BALLISTIC_PROFILE_UNVERIFIED,
    BALLISTIC_MEDIUM_UNSUPPORTED,
    BALLISTIC_PATH_UNLOADED,
    BALLISTIC_PATH_BLOCKED,
    BALLISTIC_FRIENDLY_CORRIDOR,
    BALLISTIC_FRIENDLY_DANGER_AREA,
    BALLISTIC_SCAN_LIMIT,
    BALLISTIC_MUZZLE_STALE
  }
}
