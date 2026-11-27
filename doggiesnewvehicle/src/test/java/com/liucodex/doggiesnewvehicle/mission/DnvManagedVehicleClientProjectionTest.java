package com.liucodex.doggiesnewvehicle.mission;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DnvManagedVehicleClientProjectionTest {
  @AfterEach
  void clearCallbacks() {
    DnvManagedVehicleClientProjection.install(() -> {}, view -> {});
  }

  @Test
  void openAndDirectoryDeliveryUseTheCurrentCallbackSet() {
    var old = new ArrayList<DnvManagedVehicleDirectory.View>();
    var current = new ArrayList<DnvManagedVehicleDirectory.View>();
    var openings = new ArrayList<String>();
    var view = new DnvManagedVehicleDirectory.View(
        UUID.randomUUID(), false, "NONE", List.of(), Optional.empty());
    DnvManagedVehicleClientProjection.install(() -> openings.add("open"), old::add);
    DnvManagedVehicleClientProjection.open();
    DnvManagedVehicleClientProjection.directory(view);
    DnvManagedVehicleClientProjection.install(() -> openings.add("new"), current::add);
    assertTrue(current.isEmpty());
    DnvManagedVehicleClientProjection.directory(view);
    assertEquals(List.of("open"), openings);
    assertEquals(List.of(view), old);
    assertEquals(List.of(view), current);
  }

  @Test
  void invalidInstallKeepsBothExistingCallbacks() {
    var openings = new ArrayList<String>();
    var views = new ArrayList<DnvManagedVehicleDirectory.View>();
    DnvManagedVehicleClientProjection.install(() -> openings.add("open"), views::add);
    assertThrows(NullPointerException.class,
        () -> DnvManagedVehicleClientProjection.install(null, view -> fail()));
    assertThrows(NullPointerException.class,
        () -> DnvManagedVehicleClientProjection.install(() -> fail(), null));
    var view = new DnvManagedVehicleDirectory.View(
        UUID.randomUUID(), false, "NONE", List.of(), Optional.empty());
    DnvManagedVehicleClientProjection.open();
    DnvManagedVehicleClientProjection.directory(view);
    assertEquals(List.of("open"), openings);
    assertEquals(List.of(view), views);
  }
}
