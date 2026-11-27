package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DnvSbwCrewAutonomyTest {
  @Test
  void alignmentRejectsZeroAndOppositeVectors() {
    assertTrue(DnvSbwCrewAutonomy.isAligned(
        new Vec3(0.0D, 0.0D, 2.0D),
        new Vec3(0.0D, 0.0D, 10.0D)));
    assertFalse(DnvSbwCrewAutonomy.isAligned(
        new Vec3(0.0D, 0.0D, -1.0D),
        new Vec3(0.0D, 0.0D, 1.0D)));
    assertFalse(DnvSbwCrewAutonomy.isAligned(Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D)));
  }

  @Test
  void rpmCadenceHasOneTickFloorAndSafeInvalidFallback() {
    assertEquals(20, DnvSbwCrewAutonomy.fireIntervalTicks(60));
    assertEquals(2, DnvSbwCrewAutonomy.fireIntervalTicks(600));
    assertEquals(1, DnvSbwCrewAutonomy.fireIntervalTicks(1_200));
    assertEquals(20, DnvSbwCrewAutonomy.fireIntervalTicks(0));
  }

  @Test
  void exactWeaponFireOperationIsDeterministicBoundedAndSingleUse() {
    UUID core = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    UUID target = UUID.fromString("b2000000-0000-0000-0000-000000000002");
    UUID operation = DnvSbwCrewAutonomy.fireOperationId(
        core, 7L, "seat_1", "Cannon40", target, 20_000L, 0);
    assertEquals(operation, DnvSbwCrewAutonomy.fireOperationId(
        core, 7L, "seat_1", "Cannon40", target, 20_000L, 0));
    assertFalse(operation.equals(DnvSbwCrewAutonomy.fireOperationId(
        core, 7L, "seat_1", "Cannon105", target, 20_000L, 0)));
    assertFalse(operation.equals(DnvSbwCrewAutonomy.fireOperationId(
        core, 8L, "seat_1", "Cannon40", target, 20_000L, 0)));
    Map<UUID, DnvSbwCrewAutonomy.FireAudit> audits = new LinkedHashMap<>();
    DnvSbwCrewAutonomy.FireAudit audit = new DnvSbwCrewAutonomy.FireAudit(
        operation, core, 7L, "seat_1", "Cannon40", target, 20_000L, 0);
    assertTrue(DnvSbwCrewAutonomy.rememberFireAttempt(audits, audit));
    assertFalse(DnvSbwCrewAutonomy.rememberFireAttempt(audits, audit));
    assertEquals(1, audits.size());
  }

  @Test
  void stationSchedulerRoundRobinsIndependentReadyWeapons() {
    assertEquals(0, DnvSbwCrewAutonomy.selectReadyWeapon(
        java.util.List.of(true, true, true), 0));
    assertEquals(1, DnvSbwCrewAutonomy.selectReadyWeapon(
        java.util.List.of(true, true, true), 1));
    assertEquals(2, DnvSbwCrewAutonomy.selectReadyWeapon(
        java.util.List.of(false, false, true), 0));
    assertEquals(-1, DnvSbwCrewAutonomy.selectReadyWeapon(
        java.util.List.of(false, false, false), 2));
  }

  @Test
  void blockLineCannotHideABlockedProtectedEntityCorridor() {
    assertTrue(DnvSbwCrewAutonomy.targetLineUsable(true, true));
    assertFalse(DnvSbwCrewAutonomy.targetLineUsable(true, false));
    assertFalse(DnvSbwCrewAutonomy.targetLineUsable(false, true));
  }

  @Test
  void exactManualMouseInputIsDetectedWithoutTakingFlightAuthority() {
    assertTrue(DnvSbwAimChannelRuntime.manualAimActive(
        0.01F, 0.0F));
    assertTrue(DnvSbwAimChannelRuntime.manualAimActive(
        0.0F, -0.01F));
    assertFalse(DnvSbwAimChannelRuntime.manualAimActive(
        0.0F, 0.0F));
  }
}
