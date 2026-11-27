package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DnvSbwVehicleCrewAdapterTest {
  private static final String TYPE = "superbwarfare:test_vehicle";

  @Test
  void exactAllowlistBuildsOrderedManualAndAiStationTemplates() {
    DnvSbwVehicleCrewAdapter.VehicleLayout layout = layout();
    DnvSbwVehicleCrewAdapter.Inspection inspection =
        DnvSbwVehicleCrewAdapter.inspectLayout(layout, allowlist(layout, Set.of("seat_1")));

    assertTrue(inspection.accepted());
    DnvVehicleCrewProfile.AdapterTemplate template = inspection.template().orElseThrow();
    List<DnvVehicleCrewProfile.StationTemplate> stations = List.copyOf(template.stations());
    assertEquals(List.of("seat_1", "seat_2"), stations.stream()
        .map(DnvVehicleCrewProfile.StationTemplate::id)
        .toList());
    assertTrue(stations.get(0).aiCapable());
    assertFalse(stations.get(1).aiCapable());
    assertEquals(1, inspection.actuators().size());
    DnvSbwVehicleCrewAdapter.StationActuator actuator = inspection.actuators().get(0);
    assertEquals("seat_1", actuator.stationId());
    assertEquals(DnvSbwVehicleCrewAdapter.AimChannel.TURRET, actuator.aimChannel());
    assertEquals(List.of("main_gun", "coax"), actuator.weapons());
    assertEquals(
        Set.of(DnvControlAuthority.WeaponMode.MANUAL_ONLY,
            DnvControlAuthority.WeaponMode.HOLD_FIRE),
        stations.get(1).allowedModes());
  }

  @Test
  void exactSeatGeometryBecomesTheStationLocalEnvelope() {
    DnvSbwVehicleCrewAdapter.VehicleLayout layout = new DnvSbwVehicleCrewAdapter.VehicleLayout(
        TYPE, 1, -1,
        List.of(new DnvSbwVehicleCrewAdapter.SeatLayout(
            1, List.of("side_gun"),
            2.0D, 2.4D, 8.4D,
            -90.0F, -15.0F, 15.0F, -40.0F, -15.0F)));
    DnvSbwVehicleCrewAdapter.Inspection inspection =
        DnvSbwVehicleCrewAdapter.inspectLayout(
            layout, allowlist(layout, Set.of("seat_1")));

    assertTrue(inspection.accepted());
    DnvSbwVehicleCrewAdapter.StationActuator actuator = inspection.actuators().get(0);
    assertEquals(2.0D, actuator.geometry().mountX());
    assertEquals(2.4D, actuator.geometry().mountY());
    assertEquals(8.4D, actuator.geometry().mountZ());
    assertEquals(-90.0F, actuator.geometry().baseYaw());
    assertTrue(actuator.geometry().contains(14.9F, -20.0F));
    assertFalse(actuator.geometry().contains(16.0F, -20.0F));
    assertFalse(actuator.geometry().contains(0.0F, -10.0F));
  }

  @Test
  void layoutChangeFailsClosedAndReturnsObservedFingerprintForDiagnosis() {
    DnvSbwVehicleCrewAdapter.VehicleLayout layout = layout();
    DnvSbwVehicleCrewAdapter.AllowlistEntry stale = new DnvSbwVehicleCrewAdapter.AllowlistEntry(
        "dnv:test", 2L, TYPE, DnvSbwVehicleCrewAdapter.fingerprint(layout) + 1L,
        Set.of("seat_1"));
    DnvSbwVehicleCrewAdapter.Inspection inspection =
        DnvSbwVehicleCrewAdapter.inspectLayout(layout, stale);

    assertFalse(inspection.accepted());
    assertTrue(inspection.issues().contains(
        DnvSbwVehicleCrewAdapter.IssueCode.LAYOUT_CHANGED));
    assertEquals(DnvSbwVehicleCrewAdapter.fingerprint(layout),
        inspection.actualLayoutFingerprint());
  }

  @Test
  void aiStationMustExistAndUseAnUpstreamAimControllerSeat() {
    DnvSbwVehicleCrewAdapter.VehicleLayout layout = layout();
    DnvSbwVehicleCrewAdapter.Inspection unknown = DnvSbwVehicleCrewAdapter.inspectLayout(
        layout, allowlist(layout, Set.of("seat_99")));
    assertFalse(unknown.accepted());
    assertTrue(unknown.issues().contains(
        DnvSbwVehicleCrewAdapter.IssueCode.UNKNOWN_AI_STATION));

    DnvSbwVehicleCrewAdapter.Inspection manual = DnvSbwVehicleCrewAdapter.inspectLayout(
        layout, allowlist(layout, Set.of("seat_2")));
    assertFalse(manual.accepted());
    assertTrue(manual.issues().contains(
        DnvSbwVehicleCrewAdapter.IssueCode.AI_STATION_NOT_UPSTREAM_CONTROLLER));
  }

  @Test
  void duplicateSeatOrWrongVehicleTypeRejectsTheWholeProfile() {
    DnvSbwVehicleCrewAdapter.VehicleLayout duplicate = new DnvSbwVehicleCrewAdapter.VehicleLayout(
        TYPE, 1, -1,
        List.of(
            new DnvSbwVehicleCrewAdapter.SeatLayout(1, List.of("gun_a")),
            new DnvSbwVehicleCrewAdapter.SeatLayout(1, List.of("gun_b"))));
    DnvSbwVehicleCrewAdapter.Inspection invalid = DnvSbwVehicleCrewAdapter.inspectLayout(
        duplicate, allowlist(duplicate, Set.of("seat_1")));
    assertFalse(invalid.accepted());
    assertTrue(invalid.issues().contains(DnvSbwVehicleCrewAdapter.IssueCode.DUPLICATE_SEAT));

    DnvSbwVehicleCrewAdapter.AllowlistEntry wrong = new DnvSbwVehicleCrewAdapter.AllowlistEntry(
        "dnv:test", 2L, "superbwarfare:other", DnvSbwVehicleCrewAdapter.fingerprint(layout()),
        Set.of("seat_1"));
    assertTrue(DnvSbwVehicleCrewAdapter.inspectLayout(layout(), wrong).issues().contains(
        DnvSbwVehicleCrewAdapter.IssueCode.WRONG_VEHICLE_TYPE));
  }

  @Test
  void weaponStationCountHasAHardLimit() {
    List<DnvSbwVehicleCrewAdapter.SeatLayout> seats = new ArrayList<>();
    for (int index = 0; index <= DnvVehicleCrewProfile.MAX_STATIONS; index++) {
      seats.add(new DnvSbwVehicleCrewAdapter.SeatLayout(index, List.of("gun_" + index)));
    }
    DnvSbwVehicleCrewAdapter.VehicleLayout layout =
        new DnvSbwVehicleCrewAdapter.VehicleLayout(TYPE, 0, 1, seats);
    DnvSbwVehicleCrewAdapter.Inspection inspection = DnvSbwVehicleCrewAdapter.inspectLayout(
        layout, allowlist(layout, Set.of()));
    assertFalse(inspection.accepted());
    assertTrue(inspection.issues().contains(
        DnvSbwVehicleCrewAdapter.IssueCode.TOO_MANY_WEAPON_STATIONS));
  }

  @Test
  void weaponsWithinOneStationHaveAHardLimit() {
    List<String> weapons = new ArrayList<>();
    for (int index = 0; index <= DnvSbwVehicleCrewAdapter.MAX_WEAPONS_PER_STATION; index++) {
      weapons.add("gun_" + index);
    }
    DnvSbwVehicleCrewAdapter.VehicleLayout layout =
        new DnvSbwVehicleCrewAdapter.VehicleLayout(
            TYPE, 1, -1,
            List.of(new DnvSbwVehicleCrewAdapter.SeatLayout(1, weapons)));
    DnvSbwVehicleCrewAdapter.Inspection inspection = DnvSbwVehicleCrewAdapter.inspectLayout(
        layout, allowlist(layout, Set.of("seat_1")));

    assertFalse(inspection.accepted());
    assertTrue(inspection.issues().contains(
        DnvSbwVehicleCrewAdapter.IssueCode.TOO_MANY_WEAPONS_IN_STATION));
  }

  @Test
  void sharedUpstreamControllerIndexUsesOneDeterministicTurretChannel() {
    DnvSbwVehicleCrewAdapter.VehicleLayout layout =
        new DnvSbwVehicleCrewAdapter.VehicleLayout(
            TYPE,
            1,
            1,
            List.of(
                new DnvSbwVehicleCrewAdapter.SeatLayout(0, List.of()),
                new DnvSbwVehicleCrewAdapter.SeatLayout(1, List.of("main"))));
    DnvSbwVehicleCrewAdapter.Inspection inspection =
        DnvSbwVehicleCrewAdapter.inspectLayout(
            layout, allowlist(layout, Set.of("seat_1")));

    assertTrue(inspection.accepted());
    assertEquals(1, inspection.actuators().size());
    assertEquals(
        DnvSbwVehicleCrewAdapter.AimChannel.TURRET,
        inspection.actuators().get(0).aimChannel());
  }

  @Test
  void retiredCarriersCannotPassPureLayoutInspectionOrShipAnAdapter() {
    var loader = getClass().getClassLoader();
    String root = "data/doggiesnewvehicle/dnv/vehicle_crew_adapters/sbw_0_8_9_1/";
    assertTrue(loader.getResource(root + "ac_130h.json") == null);
    assertTrue(loader.getResource(root + "dragonrise_reforge_ac130.json") == null);
    for (String type : List.of("superbwarfare:ac_130h", "dragonrise_reforge:ac130")) {
      var layout = new DnvSbwVehicleCrewAdapter.VehicleLayout(type, 1, -1,
          List.of(new DnvSbwVehicleCrewAdapter.SeatLayout(1, List.of("main_gun"))));
      var allowlist = new DnvSbwVehicleCrewAdapter.AllowlistEntry(
          "dnv:forged", 1L, type, DnvSbwVehicleCrewAdapter.fingerprint(layout), Set.of("seat_1"));
      var inspection = DnvSbwVehicleCrewAdapter.inspectLayout(layout, allowlist);
      assertFalse(inspection.accepted());
      assertEquals(List.of(DnvSbwVehicleCrewAdapter.IssueCode.RETIRED_VEHICLE_TYPE), inspection.issues());
    }
  }

  @Test
  void flightActuatorCannotBeGrantedToAnotherAircraftByDataPack() {
    DnvSbwVehicleCrewAdapter.VehicleLayout layout = layout();
    DnvSbwVehicleCrewAdapter.AllowlistEntry forged =
        new DnvSbwVehicleCrewAdapter.AllowlistEntry(
            "dnv:forged",
            1L,
            layout.vehicleTypeId(),
            DnvSbwVehicleCrewAdapter.fingerprint(layout),
            Set.of("seat_1"),
            "doggiesnewvehicle:sbw_ac130h_route_v1");

    DnvSbwVehicleCrewAdapter.Inspection inspection =
        DnvSbwVehicleCrewAdapter.inspectLayout(layout, forged);

    assertFalse(inspection.accepted());
    assertTrue(inspection.issues().contains(
        DnvSbwVehicleCrewAdapter.IssueCode.UNKNOWN_OR_MISMATCHED_FLIGHT_ACTUATOR));
  }

  private static DnvSbwVehicleCrewAdapter.VehicleLayout layout() {
    return new DnvSbwVehicleCrewAdapter.VehicleLayout(
        TYPE, 1, 3,
        List.of(
            new DnvSbwVehicleCrewAdapter.SeatLayout(0, List.of()),
            new DnvSbwVehicleCrewAdapter.SeatLayout(1, List.of("main_gun", "coax")),
            new DnvSbwVehicleCrewAdapter.SeatLayout(2, List.of("roof_gun"))));
  }

  private static DnvSbwVehicleCrewAdapter.AllowlistEntry allowlist(
      DnvSbwVehicleCrewAdapter.VehicleLayout layout, Set<String> aiStations) {
    return new DnvSbwVehicleCrewAdapter.AllowlistEntry(
        "dnv:test", 2L, TYPE, DnvSbwVehicleCrewAdapter.fingerprint(layout),
        aiStations);
  }
}
