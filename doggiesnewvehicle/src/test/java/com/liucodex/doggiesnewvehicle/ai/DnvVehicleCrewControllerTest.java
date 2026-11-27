package com.liucodex.doggiesnewvehicle.ai;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvVehicleCrewControllerTest {
  private static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-000000000021");
  private static final UUID CORE = UUID.fromString("00000000-0000-0000-0000-000000000022");
  private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000023");
  private static final String VEHICLE_TYPE = "superbwarfare:test_vehicle";

  @Test
  void noCoreFailsSafeAndWrongVehicleCannotInstall() {
    DnvVehicleCrewController controller = new DnvVehicleCrewController(VEHICLE, VEHICLE_TYPE);
    assertFalse(controller.stationDecision("nose", 0L).mayFire());

    DnvVehicleCrewProfile.Snapshot wrong = snapshot("superbwarfare:other_vehicle");
    DnvVehicleCrewController.MutationResult result = controller.installCore(wrong, 0L);
    assertFalse(result.accepted());
    assertEquals(DnvVehicleCrewController.RejectReason.WRONG_VEHICLE_TYPE, result.reason());
  }

  @Test
  void unknownStationDecisionFailsClosedInsteadOfThrowing() {
    DnvVehicleCrewController controller = installed();
    DnvVehicleCrewController.StationDecision decision =
        controller.stationDecision("unknown", 0L);
    assertEquals(DnvControlAuthority.ControlOwner.NONE, decision.owner());
    assertFalse(decision.mayTrack());
    assertFalse(decision.mayFire());
  }

  @Test
  void playerOccupiesOnlyOneStationAndAiResumesAfterDelay() {
    DnvVehicleCrewController controller = installed();
    DnvVehicleCrewController.RuntimeRevision before = controller.revision();
    assertTrue(controller.synchronizePhysicalOccupants(
        Map.of("nose", PLAYER), 100L, 10, before).accepted());

    assertEquals(
        DnvControlAuthority.ControlOwner.PLAYER,
        controller.stationDecision("nose", 100L).owner());
    assertEquals(
        DnvControlAuthority.ControlOwner.AI,
        controller.stationDecision("tail", 100L).owner());

    assertTrue(controller.synchronizePhysicalOccupants(
        Map.of(), 110L, 10, controller.revision()).accepted());
    assertEquals(
        DnvControlAuthority.ControlOwner.NONE,
        controller.stationDecision("nose", 119L).owner());
    assertEquals(
        DnvControlAuthority.ControlOwner.AI,
        controller.stationDecision("nose", 120L).owner());
  }

  @Test
  void unknownStationRejectsAtomicallyAndCoreSwapInvalidatesOldRevision() {
    DnvVehicleCrewController controller = installed();
    DnvVehicleCrewController.RuntimeRevision before = controller.revision();
    DnvVehicleCrewController.MutationResult unknown = controller.synchronizePhysicalOccupants(
        Map.of("unknown", PLAYER), 0L, 10, before);
    assertFalse(unknown.accepted());
    assertEquals(DnvVehicleCrewController.RejectReason.UNKNOWN_STATION, unknown.reason());
    assertEquals(DnvControlAuthority.ControlOwner.AI,
        controller.stationDecision("nose", 0L).owner());

    assertTrue(controller.installCore(snapshot(VEHICLE_TYPE), controller.coreRevision()).accepted());
    DnvVehicleCrewController.MutationResult stale = controller.synchronizePhysicalOccupants(
        Map.of(), 0L, 10, before);
    assertFalse(stale.accepted());
    assertEquals(DnvVehicleCrewController.RejectReason.STALE_CORE_REVISION, stale.reason());
  }

  @Test
  void removingCoreStopsAiImmediatelyAndRequiresExactCoreId() {
    DnvVehicleCrewController controller = installed();
    assertTrue(controller.stationDecision("nose", 0L).mayFire());
    DnvVehicleCrewController.MutationResult wrong = controller.removeCore(
        UUID.randomUUID(), controller.coreRevision());
    assertFalse(wrong.accepted());

    assertTrue(controller.removeCore(CORE, controller.coreRevision()).accepted());
    assertFalse(controller.stationDecision("nose", 0L).mayFire());
  }

  private static DnvVehicleCrewController installed() {
    DnvVehicleCrewController controller = new DnvVehicleCrewController(VEHICLE, VEHICLE_TYPE);
    assertTrue(controller.installCore(snapshot(VEHICLE_TYPE), 0L).accepted());
    return controller;
  }

  private static DnvVehicleCrewProfile.Snapshot snapshot(String vehicleType) {
    return new DnvVehicleCrewProfile.Snapshot(
        1,
        CORE,
        PLAYER,
        "sbw_test",
        7L,
        vehicleType,
        DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
        Map.of(
            "nose", new DnvVehicleCrewProfile.StationPolicy(
                "nose", 0, true, DnvControlAuthority.WeaponMode.AUTO_ENGAGE),
            "tail", new DnvVehicleCrewProfile.StationPolicy(
                "tail", 1, true, DnvControlAuthority.WeaponMode.AUTO_ENGAGE)));
  }
}
