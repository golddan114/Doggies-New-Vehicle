package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DnvWeaponFireGateTest {
  @Test
  void everyServerGateMustPassBeforeARealFireAttempt() {
    DnvWeaponFireGate.Decision allowed = DnvWeaponFireGate.evaluate(request());
    assertTrue(allowed.allowed());
    assertEquals(DnvWeaponFireGate.Reason.READY, allowed.reason());
  }

  @Test
  void authorityAndNoAmmoHaveStableFeedbackOutcomes() {
    DnvWeaponFireGate.Request ready = request();
    DnvWeaponFireGate.Decision authority = DnvWeaponFireGate.evaluate(
        ready.withAuthority(false));
    assertFalse(authority.allowed());
    assertEquals(DnvWeaponFireGate.Reason.AUTHORITY_DENIED, authority.reason());
    assertEquals(DnvAutonomyCore.ExecutionOutcome.AUTHORITY_DENIED, authority.outcome());

    DnvWeaponFireGate.Decision ammo = DnvWeaponFireGate.evaluate(ready.withAmmo(false));
    assertEquals(DnvWeaponFireGate.Reason.OUT_OF_AMMO, ammo.reason());
    assertEquals(DnvAutonomyCore.ExecutionOutcome.OUT_OF_AMMO, ammo.outcome());

    DnvWeaponFireGate.Decision policy = DnvWeaponFireGate.evaluate(
        new DnvWeaponFireGate.Request(
            true, true, false, true, true, true, true, true, true));
    assertFalse(policy.allowed());
    assertEquals(DnvWeaponFireGate.Reason.POLICY_HOLD, policy.reason());
  }

  @Test
  void safetyPriorityIsDeterministicWhenSeveralGatesFail() {
    DnvWeaponFireGate.Request ready = request();
    DnvWeaponFireGate.Decision denied = DnvWeaponFireGate.evaluate(
        new DnvWeaponFireGate.Request(
            false, false, false, false, false, false, false, false, false));
    assertEquals(DnvWeaponFireGate.Reason.CORE_HOLD, denied.reason());

    DnvWeaponFireGate.Decision corridor = DnvWeaponFireGate.evaluate(
        ready.withCorridor(false).withAmmo(false));
    assertEquals(DnvWeaponFireGate.Reason.FIRING_CORRIDOR_BLOCKED, corridor.reason());
  }

  @Test
  void preflightRestrictionCannotUpgradeAnExistingAuthorityRejection() {
    var denied = new DnvWeaponFireGate.Decision(false, DnvWeaponFireGate.Reason.AUTHORITY_DENIED,
        DnvAutonomyCore.ExecutionOutcome.AUTHORITY_DENIED);
    assertSame(denied, DnvWeaponFireGate.restrict(denied, DnvWeaponFireGate.Reason.BALLISTIC_PATH_BLOCKED));
    var ready = DnvWeaponFireGate.evaluate(request());
    assertTrue(DnvWeaponFireGate.restrict(ready, null).allowed());
    assertThrows(IllegalArgumentException.class,
        () -> DnvWeaponFireGate.restrict(ready, DnvWeaponFireGate.Reason.READY));
    assertEquals(DnvWeaponFireGate.Reason.BALLISTIC_FRIENDLY_DANGER_AREA,
        DnvWeaponFireGate.restrict(ready, DnvWeaponFireGate.Reason.BALLISTIC_FRIENDLY_DANGER_AREA).reason());
  }

  private static DnvWeaponFireGate.Request request() {
    return new DnvWeaponFireGate.Request(
        true, true, true, true, true, true, true, true, true);
  }
}
