package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DnvCombatPolicyRuntimeTest {
  @Test
  void liveFriendshipAndLostHostilityOverrideCachedOrAssignedFireIntent() {
    assertTrue(DnvCombatPolicyRuntime.executionRelationsAllowed(false, true, false));
    assertTrue(DnvCombatPolicyRuntime.executionRelationsAllowed(false, false, true));
    assertFalse(DnvCombatPolicyRuntime.executionRelationsAllowed(false, false, false));
    assertFalse(DnvCombatPolicyRuntime.executionRelationsAllowed(true, true, false));
    assertFalse(DnvCombatPolicyRuntime.executionRelationsAllowed(true, false, true));
    assertFalse(DnvCombatPolicyRuntime.executionRelationsAllowed(true, true, true));
  }
  @Test
  void explicitDesignationDoesNotOverrideFriendshipOrAnAbsentOwner() {
    assertTrue(DnvCombatPolicyRuntime.designationRelationsAllowed(true, true, false));
    assertFalse(DnvCombatPolicyRuntime.designationRelationsAllowed(true, true, true));
    assertFalse(DnvCombatPolicyRuntime.designationRelationsAllowed(true, false, false));
    assertFalse(DnvCombatPolicyRuntime.designationRelationsAllowed(false, true, false));
  }

  @Test
  void noPvpHardBoundaryWinsOverEveryNativeHostilitySignal() {
    assertFalse(DnvCombatPolicyRuntime.nativeHostilityAllowed(true, true, true));
  }

  @Test
  void unloadedNonPlayerTargetsMayUseSbwOrIntrinsicHostility() {
    assertTrue(DnvCombatPolicyRuntime.nativeHostilityAllowed(false, true, false));
    assertTrue(DnvCombatPolicyRuntime.nativeHostilityAllowed(false, false, true));
    assertFalse(DnvCombatPolicyRuntime.nativeHostilityAllowed(false, false, false));
  }
}
