package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.core.DnvPhysicalAimChannelAuthority;
import com.liucodex.doggiesnewvehicle.core.DnvSbwWeaponDiagnostics;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory.DetailStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvManagedVehicleDetailProjectorTest {
  @Test
  void ballisticRefusalsReachExistingDetailsAndHaveBothLanguageLabels() throws Exception {
    var chinese = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
        "src/main/resources/assets/doggiesnewvehicle/lang/zh_cn.json"))).getAsJsonObject();
    var english = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
        "src/main/resources/assets/doggiesnewvehicle/lang/en_us.json"))).getAsJsonObject();
    for (var reason : com.liucodex.doggiesnewvehicle.core.DnvWeaponFireGate.Reason.values()) {
      if (!reason.name().startsWith("BALLISTIC_")) continue;
      var diagnostics = new DnvSbwWeaponDiagnostics();
      diagnostics.beginFrame(100, 1, 4, 8);
      diagnostics.record(draft("Cannon105", 1, 0, true,
          DnvPhysicalAimChannelAuthority.Owner.AI, reason.name()));
      diagnostics.completeFrame();
      var id = identity();
      var detail = DnvManagedVehicleDetailProjector.project(id, id, true,
          Optional.of(diagnostics.snapshot()), 100);
      assertEquals(reason.name(), detail.weapons().get(0).gateReason());
      String key = "screen.doggiesnewvehicle.vehicle_operations.vehicle.detail.gate."
          + reason.name().toLowerCase(java.util.Locale.ROOT);
      assertTrue(chinese.has(key), key);
      assertTrue(english.has(key), key);
    }
  }

  @Test
  void stationReadinessRejectionReachesDetailsButNeverOverridesManualDenial() {
    for (String arc : List.of("OUT_OF_AMMO", "COOLDOWN", "WEAPON_NOT_READY", "WEAPON_UNAVAILABLE",
        "FIRING_LINE_BLOCKED")) {
      for (String gate : List.of("STATION_HOLD", "AUTHORITY_DENIED")) {
        var diagnostics = new DnvSbwWeaponDiagnostics();
        diagnostics.beginFrame(100, 1, 4, 8);
        diagnostics.record(new DnvSbwWeaponDiagnostics.WeaponDraft("seat_1", "Cannon105",
            Optional.empty(), arc, 0, 0, "sbw:passenger", DnvPhysicalAimChannelAuthority.Owner.AI,
            DnvPhysicalAimChannelAuthority.Reason.AI_AUTHORIZED,
            0, 0, 0, 0, 0, true, true,
            "WEAPON_NOT_READY".equals(arc) ? 8 : 0,
            "COOLDOWN".equals(arc), "COOLDOWN".equals(arc) ? 5 : 0, gate));
        diagnostics.completeFrame();
        var id = identity();
        var detail = DnvManagedVehicleDetailProjector.project(
            id, id, true, Optional.of(diagnostics.snapshot()), 100);
        assertEquals("STATION_HOLD".equals(gate) ? arc : gate, detail.weapons().get(0).gateReason());
      }
    }
  }

  @Test
  void stationCooldownDoesNotHideAnIndividualEmptyOrReloadingGun() {
    var diagnostics = new DnvSbwWeaponDiagnostics();
    diagnostics.beginFrame(100, 1, 4, 8);
    for (int i = 0; i < 3; i++) {
      diagnostics.record(new DnvSbwWeaponDiagnostics.WeaponDraft("seat_1", "gun_" + i,
          Optional.empty(), "COOLDOWN", 0, 0, "sbw:passenger", DnvPhysicalAimChannelAuthority.Owner.AI,
          DnvPhysicalAimChannelAuthority.Reason.AI_AUTHORIZED,
          0, 0, 0, 0, 0, true, true, i, i == 2, i == 2 ? 3 : 0, "STATION_HOLD"));
    }
    diagnostics.completeFrame();
    var id = identity();
    var detail = DnvManagedVehicleDetailProjector.project(
        id, id, true, Optional.of(diagnostics.snapshot()), 100);
    assertEquals(List.of("OUT_OF_AMMO", "WEAPON_NOT_READY", "COOLDOWN"),
        detail.weapons().stream().map(DnvManagedVehicleDirectory.Weapon::gateReason).toList());
  }
  @Test
  void failedNativeBaseConditionsDoNotClaimEmptyAmmoWhenRoundsRemain() {
    var diagnostics = new DnvSbwWeaponDiagnostics();
    diagnostics.beginFrame(100L, 1, 4L, 8L);
    diagnostics.record(draft("Cannon25", 20, 0, false,
        DnvPhysicalAimChannelAuthority.Owner.AI, "OUT_OF_AMMO"));
    diagnostics.completeFrame();
    var identity = identity();
    var projected = DnvManagedVehicleDetailProjector.project(
        identity, identity, false, Optional.of(diagnostics.snapshot()), 100L);
    assertEquals("WEAPON_UNAVAILABLE", projected.weapons().get(0).gateReason());
    assertEquals(20, projected.weapons().get(0).ammoCount());
    assertFalse(projected.weapons().get(0).canShoot());
    assertEquals("OUT_OF_AMMO", diagnostics.snapshot().weapons().get(0).gateReason());
  }

  @Test
  void ownerAndAdministratorCanInspectButAnUnrelatedPlayerCannot() {
    UUID owner = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    assertTrue(DnvManagedVehicleDetailProjector.visibleTo(owner, false, owner));
    assertTrue(DnvManagedVehicleDetailProjector.visibleTo(other, true, owner));
    assertFalse(DnvManagedVehicleDetailProjector.visibleTo(other, false, owner));
  }

  @Test
  void threeWeaponsKeepIndependentAmmoCooldownAndManualReasons() {
    var identity = identity();
    var sample = sample();
    var detail = DnvManagedVehicleDetailProjector.project(identity, identity, true, sample, 110L);
    assertEquals(DetailStatus.SAMPLED, detail.status());
    assertEquals(List.of("Cannon105", "Cannon40", "Cannon25"),
        detail.weapons().stream().map(DnvManagedVehicleDirectory.Weapon::weaponName).toList());
    assertEquals("OUT_OF_AMMO", detail.weapons().get(0).gateReason());
    assertEquals(0, detail.weapons().get(0).ammoCount());
    assertFalse(detail.weapons().get(0).canShoot());
    assertEquals(7, detail.weapons().get(1).cooldownTicks());
    assertEquals("MANUAL", detail.weapons().get(2).controlOwner());
    assertEquals("AUTHORITY_DENIED", detail.weapons().get(2).gateReason());
    assertEquals(10L, detail.sampleAgeTicks());
    assertTrue(detail.routeSupported());
  }

  @Test
  void staleRegressedEmptyAndChangedCoreSnapshotsNeverExposeOldWeapons() {
    var identity = identity();
    for (long now : List.of(99L, 141L)) {
      var detail = DnvManagedVehicleDetailProjector.project(identity, identity, false, sample(), now);
      assertEquals(DetailStatus.STALE, detail.status());
      assertTrue(detail.weapons().isEmpty());
    }
    var pending = DnvManagedVehicleDetailProjector.project(identity, identity, false, Optional.empty(), 100L);
    assertEquals(DetailStatus.DIAGNOSTICS_PENDING, pending.status());
    assertFalse(pending.routeSupported());
    var changed = DnvManagedVehicleDetailProjector.project(identity, identity(), true, sample(), 100L);
    assertEquals(DetailStatus.CORE_CHANGED, changed.status());
    assertTrue(changed.weapons().isEmpty());
  }

  @Test
  void diagnosticSamplesAreInvalidatedByCoreAndHumanControlRevisions() {
    var snapshot = sample().orElseThrow();
    assertTrue(snapshot.matchesRevision(4L, 8L));
    assertFalse(snapshot.matchesRevision(5L, 8L));
    assertFalse(snapshot.matchesRevision(4L, 9L));
  }

  private static Optional<DnvSbwWeaponDiagnostics.Snapshot> sample() {
    var diagnostics = new DnvSbwWeaponDiagnostics();
    diagnostics.beginFrame(100L, 3, 4L, 8L);
    diagnostics.record(draft("Cannon105", 0, 0, false, DnvPhysicalAimChannelAuthority.Owner.AI, "OUT_OF_AMMO"));
    diagnostics.record(draft("Cannon40", 8, 7, true, DnvPhysicalAimChannelAuthority.Owner.AI, "COOLDOWN"));
    diagnostics.record(draft("Cannon25", 20, 0, true, DnvPhysicalAimChannelAuthority.Owner.MANUAL, "AUTHORITY_DENIED"));
    diagnostics.completeFrame();
    return Optional.of(diagnostics.snapshot());
  }

  private static DnvSbwWeaponDiagnostics.WeaponDraft draft(
      String name, int ammo, int cooldown, boolean canShoot,
      DnvPhysicalAimChannelAuthority.Owner owner, String reason) {
    return new DnvSbwWeaponDiagnostics.WeaponDraft(
        "seat_1", name, Optional.empty(), "TARGET_AUTHORIZED", 0F, 0F,
        "sbw:passenger", owner, DnvPhysicalAimChannelAuthority.Reason.AI_AUTHORIZED,
        0F, 0F, 0F, 0F, 0, true, true, ammo, canShoot, cooldown, reason);
  }

  private static DnvManagedVehicleDirectory.Selection identity() {
    return new DnvManagedVehicleDirectory.Selection(
        UUID.randomUUID(), "minecraft:overworld", UUID.randomUUID(), 4L);
  }
}
