package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.compat.DnvSbwStationRuntime.Readiness.Sample;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DnvSbwStationReadinessTest {
  @Test
  void doesNotCombineAmmoAndCooldownFromDifferentWeapons() {
    var readiness = DnvSbwStationRuntime.Readiness.aggregate(List.of(
        new Sample(true, 1, true, false), new Sample(true, 0, false, true)));
    assertTrue(readiness.ammoAvailable());
    assertTrue(readiness.baseReady());
    assertFalse(readiness.cooldownReady());
  }

  @Test
  void missingGunAndBaseCheckFailureDoNotPretendAmmoExhaustion() {
    var missing = DnvSbwStationRuntime.Readiness.aggregate(List.of(Sample.unavailable()));
    assertFalse(missing.weaponAvailable());
    var blocked = DnvSbwStationRuntime.Readiness.aggregate(List.of(new Sample(true, 8, false, true)));
    assertTrue(blocked.weaponAvailable());
    assertTrue(blocked.ammoAvailable());
    assertFalse(blocked.baseReady());
    var empty = DnvSbwStationRuntime.Readiness.aggregate(List.of(new Sample(true, 0, false, true)));
    assertFalse(empty.ammoAvailable());
  }

  @Test
  void oneReadyWeaponRestoresStationAndNativeAmmoSupplierIsRespected() {
    var ready = DnvSbwStationRuntime.Readiness.aggregate(List.of(
        Sample.unavailable(), new Sample(true, 8, false, true), new Sample(true, 0, true, true)));
    assertTrue(ready.ammoAvailable());
    assertTrue(ready.baseReady());
    assertTrue(ready.cooldownReady());
    assertFalse(DnvSbwStationRuntime.Readiness.aggregate(List.of()).weaponAvailable());
  }

  @Test
  void actualWeaponMemoryRejectsUnknownOtherExpiredAndRegressedLines() {
    var state = new DnvSbwStationRuntime(1, List.of("Cannon105", "Cannon40", "Gatling25"));
    var target = UUID.randomUUID();
    var gun = state.weapon("Cannon105");
    assertTrue(state.observedLines(100).isEmpty());
    gun.recordLineOfSight(target, Vec3.ZERO, true, true, 100);
    assertTrue(gun.observedLineClear(target, 104));
    assertFalse(gun.observedLineClear(UUID.randomUUID(), 104));
    assertFalse(gun.observedLineClear(target, 99));
    assertFalse(gun.observedLineClear(target, 105));
    assertEquals(java.util.Map.of(target, true), state.observedLines(104));
    assertTrue(state.observedLines(105).isEmpty());
    state.resetTracking();
    assertTrue(state.observedLines(104).isEmpty());
  }

  @Test
  void newBlockOrFriendlyObstructionRevokesThePreviousClearLine() {
    var state = new DnvSbwStationRuntime(1, List.of("Cannon105"));
    var gun = state.weapon("Cannon105");
    var target = UUID.randomUUID();
    gun.recordLineOfSight(target, Vec3.ZERO, true, true, 100);
    gun.recordLineOfSight(target, Vec3.ZERO, true, false, 101);
    assertTrue(gun.blockLineClear());
    assertFalse(gun.fireCorridorClear());
    assertTrue(state.observedLines(101).isEmpty());
    gun.recordLineOfSight(target, Vec3.ZERO, false, true, 102);
    assertFalse(gun.blockLineClear());
    assertFalse(gun.fireCorridorClear());
  }

  @Test
  void deferredGeometryCannotReuseAClearLineAndRetriesNextTick() {
    var state = new DnvSbwStationRuntime(1, List.of("Cannon105"));
    var gun = state.weapon("Cannon105");
    var target = UUID.randomUUID();
    gun.recordLineOfSight(target, Vec3.ZERO, true, true, 100);
    assertTrue(gun.observedLineClear(target, 101));
    gun.deferLineOfSight(target);
    assertTrue(gun.geometryDeferred());
    assertTrue(gun.geometryPendingWithoutBlockedObservation(target));
    assertFalse(gun.observedLineClear(target, 101));
    assertTrue(gun.needsLineOfSightRefresh(target, 101));
    assertTrue(state.observedLines(101).isEmpty());
    gun.recordLineOfSight(target, Vec3.ZERO, true, true, 102);
    assertFalse(gun.geometryDeferred());
    assertTrue(gun.observedLineClear(target, 103));

    gun.recordLineOfSight(target, Vec3.ZERO, false, false, 104);
    gun.deferLineOfSight(target);
    assertFalse(gun.geometryPendingWithoutBlockedObservation(target));
    assertTrue(gun.geometryPendingWithoutBlockedObservation(UUID.randomUUID()));
  }

  @Test
  void clearingTrackingDoesNotEraseAWeaponCooldownOrAdvanceSequence() {
    var state = new DnvSbwStationRuntime(1, List.of("Cannon105", "Cannon40", "Gatling25"));
    state.weapon("Cannon40").onFired(100, 120);
    state.advanceAfter(1);
    state.resetTracking();
    assertEquals(2, state.nextWeaponIndex());
    assertFalse(state.weapon("Cannon40").cooldownReady(109));
    assertTrue(state.weapon("Cannon40").cooldownReady(110));
    assertTrue(state.weapon("Cannon105").cooldownReady(100));
  }

  @Test
  void successfulFinalLineRecheckKeepsNextTickTrackingAndOneTickWeaponCadence() {
    var state = new DnvSbwStationRuntime(1, List.of("Gatling25"));
    var gun = state.weapon("Gatling25");
    var target = UUID.randomUUID();
    for (int tick = 100; tick < 110; tick++) {
      gun.recordLineOfSight(target, Vec3.ZERO, true, true, tick);
      gun.onFired(tick, 2000);
      assertTrue(gun.observedLineClear(target, tick + 1));
      assertTrue(gun.cooldownReady(tick + 1));
    }
  }
}
