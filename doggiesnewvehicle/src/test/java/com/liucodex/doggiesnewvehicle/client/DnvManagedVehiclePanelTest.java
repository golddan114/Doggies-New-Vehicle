package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvManagedVehiclePanelTest {
  @Test
  void switchingVehicleClearsOldDetailAndItsLateReplyCannotRestoreIt() {
    var panel = new DnvManagedVehiclePanel();
    var first = vehicle(UUID.randomUUID(), 1L);
    var second = vehicle(UUID.randomUUID(), 1L);
    var initial = panel.requestStarted();
    assertTrue(panel.accept(view(initial, List.of(first, second)), Optional.of(first.vehicleId())).isPresent());
    var oldQuery = panel.requestStarted();
    panel.selectVehicle(second.vehicleId());
    assertTrue(panel.selectedDetail().isEmpty());
    assertTrue(panel.accept(view(oldQuery, List.of(first, second)), Optional.empty()).isEmpty());
    var next = panel.requestStarted();
    assertEquals(second.selection(), next.selected().orElseThrow());
    panel.accept(view(next, List.of(first, second)), Optional.empty());
    assertEquals(second.selection(), panel.selectedDetail().orElseThrow().identity());
  }

  @Test
  void replacedCoreAndMissingVehicleRemovePreviouslyVisibleWeaponDetails() {
    var panel = new DnvManagedVehiclePanel();
    var vehicle = vehicle(UUID.randomUUID(), 1L);
    panel.accept(view(panel.requestStarted(), List.of(vehicle)), Optional.empty());
    panel.accept(view(panel.requestStarted(), List.of(vehicle)), Optional.empty());
    assertFalse(panel.selectedDetail().orElseThrow().weapons().isEmpty());
    var replaced = new DnvManagedVehicleDirectory.Vehicle(
        vehicle.vehicleId(), vehicle.vehicleTypeId(), vehicle.dimension(), 0, 90, 0,
        "PLAYER_PREFERRED", "ACTIVE", false, vehicle.coreId(), 2L);
    panel.accept(view(panel.requestStarted(), List.of(replaced)), Optional.empty());
    assertTrue(panel.selectedDetail().isEmpty());
    var query = panel.requestStarted();
    assertEquals(2L, query.selected().orElseThrow().coreRevision());
    var missing = new DnvManagedVehicleDirectory.View(query.requestId(), false, "NONE", List.of(),
        Optional.of(new DnvManagedVehicleDirectory.Detail(query.selected().orElseThrow(),
            DnvManagedVehicleDirectory.DetailStatus.NOT_AVAILABLE, false, 0L, List.of())));
    panel.accept(missing, Optional.empty());
    assertTrue(panel.selected().isEmpty());
    assertTrue(panel.selectedDetail().isEmpty());
  }

  @Test
  void expiredAndHiddenPanelsCannotExposeOldDetailsOrAdministratorActions() {
    var panel = new DnvManagedVehiclePanel();
    var vehicle = vehicle(UUID.randomUUID(), 1L);
    panel.accept(view(panel.requestStarted(), List.of(vehicle)), Optional.empty());
    panel.accept(view(panel.requestStarted(), List.of(vehicle)), Optional.empty());
    assertTrue(panel.administrator());
    panel.refreshWidgets(true);
    panel.refreshWidgets(false);
    assertTrue(panel.selectedDetail().isEmpty());
    assertFalse(panel.administrator());
    panel.accept(view(panel.requestStarted(), List.of(vehicle)), Optional.empty());
    for (int tick = 0; tick < DnvManagedVehicleRequestGate.EXPIRY_TICKS; tick++) {
      panel.tick();
    }
    assertTrue(panel.selectedDetail().isEmpty());
    assertFalse(panel.administrator());
  }

  private static DnvManagedVehicleDirectory.View view(
      DnvManagedVehicleDirectory.Query query, List<DnvManagedVehicleDirectory.Vehicle> vehicles) {
    return new DnvManagedVehicleDirectory.View(query.requestId(), true, "NONE", vehicles,
        query.selected().map(selection -> new DnvManagedVehicleDirectory.Detail(
            selection, DnvManagedVehicleDirectory.DetailStatus.SAMPLED, false, 1L,
            List.of(new DnvManagedVehicleDirectory.Weapon("seat_1", "Cannon105", "AI", "OUT_OF_AMMO", 0, false, 0)))));
  }

  private static DnvManagedVehicleDirectory.Vehicle vehicle(UUID id, long revision) {
    return new DnvManagedVehicleDirectory.Vehicle(id, "superbwarfare:bradley", "minecraft:overworld",
        0, 90, 0, "PLAYER_PREFERRED", "ACTIVE", true, UUID.randomUUID(), revision);
  }
}
