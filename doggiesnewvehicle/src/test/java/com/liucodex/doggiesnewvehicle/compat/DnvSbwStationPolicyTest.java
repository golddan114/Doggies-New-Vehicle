package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore;
import com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvSbwStationPolicyTest {
  private static final String TYPE = "superbwarfare:test_station_policy";

  @Test
  void exactGenericSeatCarriesPolicyAndSharedWeaponOrderIntoTheDecision() throws Exception {
    var catalog = new DnvSbwVehicleCrewCatalog();
    assertTrue(catalog.replace(Map.of("generic", definition()), 0).accepted());
    var entry = catalog.find(TYPE).orElseThrow();
    var inspection = DnvSbwVehicleCrewAdapter.inspectLayout(exactLayout(), entry);
    assertTrue(inspection.accepted(), () -> inspection.issues().toString());
    var actuator = inspection.actuators().get(0);
    assertEquals("seat_1", actuator.stationId());
    assertEquals(List.of("side_gun"), actuator.weapons());
    assertEquals("sbw:turret", actuator.aimChannelId());
    assertEquals(Set.of("seat_1"), actuator.sharedWith());
    assertEquals(entry.stationPolicies().get("seat_1"), actuator.policy());
    assertEquals(-90, actuator.geometry().baseYaw());
    assertTrue(actuator.geometry().contains(14, -30));
    assertFalse(actuator.geometry().contains(61, 0));
    UUID target = UUID.randomUUID();
    var observed = new DnvStationEngagementCore.TargetSnapshot(target,
        DnvStationEngagementCore.TargetKind.LIVING, 20, -8, 8.4, 10, true, true, false, true);
    var core = new DnvStationEngagementCore();
    var readiness = DnvSbwStationRuntime.Readiness.aggregate(List.of(
        new DnvSbwStationRuntime.Readiness.Sample(true, 1, true, false),
        new DnvSbwStationRuntime.Readiness.Sample(true, 0, false, true)));
    var station = new DnvStationEngagementCore.StationSnapshot(DnvAutonomyCore.DomainOwner.AI,
        true, actuator.geometry(), actuator.policy(), readiness, Map.of(target, true));
    var decision = core.decide(new DnvStationEngagementCore.Request(100, 1, 0, 0, 0, 0,
        Optional.of(target), List.of(observed), Map.of(actuator.stationId(), station)));
    assertEquals(DnvStationEngagementCore.Reason.COOLDOWN,
        decision.stationIntents().get("seat_1").reason());
  }

  @Test
  void policiesCannotAuthorizeOtherStationsOrAnotherVehicleType() throws Exception {
    var document = definition();
    var policies = document.getAsJsonObject("stationPolicies");
    policies.add("seat_4", policies.remove("seat_1"));
    var catalog = new DnvSbwVehicleCrewCatalog();
    var result = catalog.replace(Map.of("wrong", document), 0);
    assertFalse(result.accepted());
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvSbwVehicleCrewCatalog.IssueCode.UNKNOWN_POLICY_STATION));
    assertTrue(catalog.replace(Map.of("valid", definition()), 1).accepted());
    var entry = catalog.find(TYPE).orElseThrow();
    var nativeLayout = new DnvSbwVehicleCrewAdapter.VehicleLayout(
        "dragonrise_reforge:ac130", 0, 1, exactLayout().seats());
    assertFalse(DnvSbwVehicleCrewAdapter.inspectLayout(nativeLayout, entry).accepted());
  }

  @Test
  void invalidPolicyReloadClearsCatalogAndDoesNotRetainArmedConfiguration() throws Exception {
    for (String mutation : List.of("range", "negative", "fraction-string", "infinite", "kind", "mode", "unknown")) {
      var document = definition();
      var policy = document.getAsJsonObject("stationPolicies").getAsJsonObject("seat_1");
      switch (mutation) {
        case "range" -> policy.addProperty("maximumRange", 129);
        case "negative" -> policy.addProperty("minimumRange", -1);
        case "fraction-string" -> policy.addProperty("minimumRange", "0.1");
        case "infinite" -> policy.add("maximumRange", JsonParser.parseString("1e999"));
        case "kind" -> policy.add("allowedTargetKinds", JsonParser.parseString("[\"PLAYER\"]"));
        case "mode" -> policy.addProperty("targetingMode", "SELF_DEFENSE");
        default -> policy.addProperty("maxRangeTypo", 10);
      }
      var catalog = new DnvSbwVehicleCrewCatalog();
      assertTrue(catalog.replace(Map.of("valid", definition()), 0).accepted());
      var bad = catalog.replace(Map.of("bad", document), 1);
      assertFalse(bad.accepted(), mutation);
      assertEquals(2, bad.snapshot().revision());
      assertTrue(bad.snapshot().entries().isEmpty(), mutation);
    }
  }

  @Test
  void oldDefinitionsKeepTheirExistingDefaultPolicy() throws Exception {
    var document = definition();
    document.remove("stationPolicies");
    var catalog = new DnvSbwVehicleCrewCatalog();
    assertTrue(catalog.replace(Map.of("legacy", document), 0).accepted());
    var entry = catalog.find(TYPE).orElseThrow();
    assertTrue(entry.stationPolicies().isEmpty());
    var actuator = DnvSbwVehicleCrewAdapter.inspectLayout(exactLayout(), entry).actuators().get(0);
    assertEquals(DnvSbwVehicleCrewAdapter.defaultStationPolicy(), actuator.policy());
  }

  private static JsonObject definition() {
    JsonObject document = JsonParser.parseString("""
        {"schemaVersion":1,"enabled":true,"adapterId":"dnv:test_station_policy","revision":1,
         "vehicleTypeId":"superbwarfare:test_station_policy","expectedLayoutFingerprint":0,
         "aiStationIds":["seat_1"],
         "stationPolicies":{"seat_1":{"targetingMode":"SHARED_PREFERRED",
            "allowedTargetKinds":["LIVING","VEHICLE"],"minimumRange":0.1,"maximumRange":128.0}},
         "aimChannels":{"seat_1":{"aimChannelId":"sbw:turret","sharedWith":["seat_1"]}}}
        """).getAsJsonObject();
    document.addProperty("expectedLayoutFingerprint", DnvSbwVehicleCrewAdapter.fingerprint(exactLayout()));
    return document;
  }

  private static DnvSbwVehicleCrewAdapter.VehicleLayout exactLayout() {
    return new DnvSbwVehicleCrewAdapter.VehicleLayout(TYPE, 1, 1,
        List.of(new DnvSbwVehicleCrewAdapter.SeatLayout(1, List.of("side_gun"),
            2.0, 2.4, 8.4, -90, -60, 60, -40, -15)));
  }
}
