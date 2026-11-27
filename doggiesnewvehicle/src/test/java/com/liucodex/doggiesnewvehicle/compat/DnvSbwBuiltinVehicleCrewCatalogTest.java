package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewRuntime;
import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewCoreCodec;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvSbwBuiltinVehicleCrewCatalogTest {
  private static final String RESOURCE_ROOT =
      "data/doggiesnewvehicle/dnv/vehicle_crew_adapters/sbw_0_8_9_1/";

  @Test
  void defaultCatalogShipsNoRetiredOrUnselectedCrewAdapter() {
    var catalog = new DnvSbwVehicleCrewCatalog();
    var documents = documents();
    assertEquals(10, documents.size());
    assertTrue(documents.values().stream().allMatch(json -> json.getAsJsonObject().has("enabled")));
    var loaded = catalog.replace(documents, 0);
    assertTrue(loaded.accepted(), () -> loaded.issues().toString());
    assertTrue(loaded.snapshot().entries().isEmpty());
  }

  @Test
  void narrowingTheCatalogDisarmsAnExistingCoreAndPreservesItsPortableConfiguration() {
    var catalog = new DnvSbwVehicleCrewCatalog();
    var former = documents();
    former.get(RESOURCE_ROOT + "mi_28.json").getAsJsonObject().addProperty("enabled", true);
    assertTrue(catalog.replace(former, 0).accepted());
    var layout = contracts().stream().filter(value -> value.fileName().equals("mi_28"))
        .findFirst().orElseThrow().layout();
    var template = DnvSbwVehicleCrewAdapter.inspectLayout(layout,
        catalog.find(layout.vehicleTypeId()).orElseThrow()).template().orElseThrow();
    UUID owner = UUID.randomUUID();
    UUID core = UUID.randomUUID();
    var context = new DnvVehicleCrewProfile.ValidationContext(owner, false);
    var draft = new DnvVehicleCrewProfile.Draft(DnvVehicleCrewProfile.CURRENT_SCHEMA_VERSION,
        core, owner, template.id(), template.revision(), layout.vehicleTypeId(),
        DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
        template.stations().stream().map(station -> new DnvVehicleCrewProfile.StationDraft(station.id(),
            station.aiCapable() ? DnvControlAuthority.WeaponMode.AUTO_ENGAGE
                : DnvControlAuthority.WeaponMode.MANUAL_ONLY)).toList());
    var tag = DnvVehicleCrewCoreCodec.encode(
        DnvVehicleCrewProfile.validate(draft, template, context).snapshot().orElseThrow());
    var before = tag.copy();
    var runtime = new DnvVehicleCrewRuntime(UUID.randomUUID(), layout.vehicleTypeId());
    assertTrue(runtime.install(tag, template, context, 1, 0).accepted());
    assertTrue(runtime.stationDecision("seat_1", 1).mayFire());

    assertTrue(catalog.replace(documents(), 1).accepted());
    var changed = catalog.find(layout.vehicleTypeId())
        .flatMap(entry -> DnvSbwVehicleCrewAdapter.inspectLayout(layout, entry).template());
    assertEquals(DnvVehicleCrewRuntime.Action.DISARMED,
        runtime.revalidate(changed, catalog.snapshot().revision()).action());
    assertEquals(core, runtime.configuredCoreId().orElseThrow());
    assertFalse(runtime.stationDecision("seat_1", 2).mayFire());
    assertEquals(before, tag);
    assertTrue(DnvVehicleCrewCoreCodec.decode(tag).accepted());

    assertTrue(catalog.replace(former, 2).accepted());
    var restored = catalog.find(layout.vehicleTypeId())
        .flatMap(entry -> DnvSbwVehicleCrewAdapter.inspectLayout(layout, entry).template());
    assertEquals(DnvVehicleCrewRuntime.Action.REARMED,
        runtime.revalidate(restored, catalog.snapshot().revision()).action());
    assertTrue(runtime.stationDecision("seat_1", 3).mayFire());
  }

  @Test
  void retainedReferenceLayoutsStillMatchWhenExplicitlyEnabledForInspection() throws Exception {
    List<LayoutContract> contracts = contracts();
    Map<String, JsonElement> documents = documents();
    documents.values().forEach(json -> json.getAsJsonObject().addProperty("enabled", true));

    DnvSbwVehicleCrewCatalog catalog = new DnvSbwVehicleCrewCatalog();
    DnvSbwVehicleCrewCatalog.ReloadResult loaded = catalog.replace(documents, 0L);

    assertTrue(loaded.accepted(), () -> loaded.issues().toString());
    assertEquals(contracts.size(), loaded.snapshot().entries().size());
    for (LayoutContract contract : contracts) {
      DnvSbwVehicleCrewAdapter.AllowlistEntry entry = catalog
          .find(contract.layout().vehicleTypeId())
          .orElseThrow();
      assertEquals(contract.aiStations(), entry.aiStationIds());
      assertEquals(contract.flightActuatorId(), entry.flightActuatorId());
      assertEquals(
          entry.expectedLayoutFingerprint(),
          DnvSbwVehicleCrewAdapter.fingerprint(contract.layout()),
          contract.layout().vehicleTypeId());
      assertTrue(
          DnvSbwVehicleCrewAdapter.inspectLayout(contract.layout(), entry).accepted(),
          contract.layout().vehicleTypeId());
      assertEquals(
          !contract.flightActuatorId().isEmpty(),
          DnvSbwVehicleCrewAdapter.inspectLayout(contract.layout(), entry)
              .template().orElseThrow().aiFlightCapable());
    }
  }

  private Map<String, JsonElement> documents() {
    Map<String, JsonElement> documents = new LinkedHashMap<>();
    for (LayoutContract contract : contracts()) {
      String path = RESOURCE_ROOT + contract.fileName() + ".json";
      InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
      assertNotNull(stream, path);
      try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        documents.put(path, JsonParser.parseReader(reader));
      } catch (java.io.IOException exception) {
        throw new AssertionError("failed to close " + path, exception);
      }
    }

    return documents;
  }

  private static List<LayoutContract> contracts() {
    return List.of(
        contract("bmp_2", 0, 1, Set.of("seat_0", "seat_1"), List.of(
            weapons("Cannon", "MachineGun", "Missile"),
            weapons("MG1"), weapons("MG2"), weapons("MG3"),
            weapons("MG4"), weapons("MG5"), weapons("MG6"))),
        contract("bradley", 0, 1, Set.of("seat_0", "seat_1"), List.of(
            weapons("Cannon", "MachineGun", "Missile"),
            weapons("MG1"), weapons("MG2"), none(), none(), none(), none())),
        contract("fh_77bw", 1, 1, Set.of("seat_1"), List.of(
            none(), weapons("Main"), weapons("MachineGun"), none(), none())),
        contract("j_16", 0, 1, Set.of("seat_0", "seat_1"), List.of(
            weapons("Cannon"), weapons("Missile"))),
        contract("ju_87", 1, 1, Set.of("seat_1"), List.of(
            weapons("MachineGun", "Bomb", "BombSmall"), weapons("PassengerGun"))),
        contract("m_1a_2", 0, 1, Set.of("seat_0", "seat_1"), List.of(
            weapons("Cannon", "MachineGun"),
            weapons("PassengerMachineGun"), none(), none(), none())),
        contract("mi_28", 1, 1, Set.of("seat_1"), List.of(
            weapons("Rocket", "DriverMissile", "DriverAAMissile"),
            weapons("Cannon", "PassengerMissile", "SeekMissile"))),
        contract("t_90a", 0, 1, Set.of("seat_0", "seat_1"), List.of(
            weapons("Cannon", "MachineGun"), weapons("PassengerMachineGun"), none())),
        contract("yx_100", 0, 1, Set.of("seat_0", "seat_1"), List.of(
            weapons("Cannon", "MachineGun"), weapons("PassengerCannon"),
            weapons("SwarmDrone"), none(), none())),
        contract("ztz_99a", 0, 1, Set.of("seat_0", "seat_1"), List.of(
            weapons("Cannon", "MachineGun"),
            weapons("PassengerMachineGun"), none(), none(), none())));
  }

  private static LayoutContract contract(
      String fileName,
      int turretController,
      int passengerController,
      Set<String> aiStations,
      List<List<String>> weaponSeats) {
    return contract(
        fileName,
        "superbwarfare:" + fileName,
        turretController,
        passengerController,
        aiStations,
        "",
        weaponSeats);
  }

  private static LayoutContract contract(
      String fileName,
      String vehicleTypeId,
      int turretController,
      int passengerController,
      Set<String> aiStations,
      String flightActuatorId,
      List<List<String>> weaponSeats) {
    List<DnvSbwVehicleCrewAdapter.SeatLayout> seats = new java.util.ArrayList<>();
    for (int index = 0; index < weaponSeats.size(); index++) {
      seats.add(new DnvSbwVehicleCrewAdapter.SeatLayout(index, weaponSeats.get(index)));
    }
    return new LayoutContract(
        fileName,
        aiStations,
        flightActuatorId,
        new DnvSbwVehicleCrewAdapter.VehicleLayout(
            vehicleTypeId,
            turretController,
            passengerController,
            seats));
  }

  private static List<String> weapons(String... names) {
    return List.of(names);
  }

  private static List<String> none() {
    return List.of();
  }

  private record LayoutContract(
      String fileName,
      Set<String> aiStations,
      String flightActuatorId,
      DnvSbwVehicleCrewAdapter.VehicleLayout layout) {}
}
