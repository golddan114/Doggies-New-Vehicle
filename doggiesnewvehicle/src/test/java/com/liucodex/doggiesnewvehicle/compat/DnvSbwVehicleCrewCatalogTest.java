package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DnvSbwVehicleCrewCatalogTest {
  @Test
  void disabledProfileIsRemovedAndCanBeExplicitlyRestoredWithoutChangingOtherProfiles() {
    var catalog = new DnvSbwVehicleCrewCatalog();
    var selected = document("a", "dragonrise_reforge:ac130", 2, 42, "seat_1");
    var candidate = document("b", "superbwarfare:ac_130h", 1, 99, "seat_1").getAsJsonObject();
    assertTrue(catalog.replace(Map.of("a", selected, "b", candidate), 0).accepted());
    var retained = catalog.find("dragonrise_reforge:ac130").orElseThrow();
    candidate.addProperty("enabled", false);
    assertTrue(catalog.replace(Map.of("a", selected, "b", candidate), 1).accepted());
    assertTrue(catalog.find("superbwarfare:ac_130h").isEmpty());
    assertEquals(retained, catalog.find("dragonrise_reforge:ac130").orElseThrow());
    candidate.addProperty("enabled", true);
    assertTrue(catalog.replace(Map.of("a", selected, "b", candidate), 2).accepted());
    assertTrue(catalog.find("superbwarfare:ac_130h").isPresent());
  }

  @Test
  void malformedEnabledValuesDisarmTheCatalogInsteadOfCoercingToBoolean() {
    for (String value : java.util.List.of("null", "1", "\"false\"", "[]", "{}")) {
      var catalog = new DnvSbwVehicleCrewCatalog();
      var profile = document("a", "dragonrise_reforge:ac130", 2, 42, "seat_1").getAsJsonObject();
      assertTrue(catalog.replace(Map.of("a", profile), 0).accepted());
      profile.add("enabled", JsonParser.parseString(value));
      var result = catalog.replace(Map.of("a", profile), 1);
      assertFalse(result.accepted(), value);
      assertTrue(result.snapshot().entries().isEmpty(), value);
      assertTrue(result.issues().stream().anyMatch(issue ->
          issue.code() == DnvSbwVehicleCrewCatalog.IssueCode.EXPECTED_BOOLEAN), value);
    }
  }

  @Test
  void disabledProfilesStillRequireValidSchemaAndGeometry() {
    var profile = document("a", "superbwarfare:ac_130h", 1, 99, "seat_1").getAsJsonObject();
    profile.addProperty("enabled", false);
    profile.addProperty("expectedLayoutFingerprint", -1);
    var result = new DnvSbwVehicleCrewCatalog().replace(Map.of("a", profile), 0);
    assertFalse(result.accepted());
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvSbwVehicleCrewCatalog.IssueCode.OUT_OF_RANGE));
  }

  @Test
  void validDocumentsReplaceTheCatalogInStableSourceOrder() {
    DnvSbwVehicleCrewCatalog catalog = new DnvSbwVehicleCrewCatalog();
    Map<String, JsonElement> documents = new LinkedHashMap<>();
    documents.put("z:last", document("z", "superbwarfare:ztz_99a", 7, 99, "seat_1"));
    documents.put("a:first", document("a", "superbwarfare:ac_130h", 3, 42, "seat_1"));

    DnvSbwVehicleCrewCatalog.ReloadResult result = catalog.replace(documents, 0L);

    assertTrue(result.accepted());
    assertEquals(1L, result.snapshot().revision());
    assertEquals(
        java.util.List.of("superbwarfare:ac_130h", "superbwarfare:ztz_99a"),
        java.util.List.copyOf(result.snapshot().entries().keySet()));
    assertTrue(catalog.find("superbwarfare:ac_130h").isPresent());
  }

  @Test
  void malformedReloadAdvancesRevisionAndDisarmsTheOldCatalog() {
    DnvSbwVehicleCrewCatalog catalog = new DnvSbwVehicleCrewCatalog();
    assertTrue(catalog.replace(
        Map.of("valid", document("a", "superbwarfare:ac_130h", 1, 1, "seat_1")),
        0L).accepted());

    DnvSbwVehicleCrewCatalog.ReloadResult rejected = catalog.replace(
        Map.of("broken", JsonParser.parseString("{\"schemaVersion\":2}")),
        1L);

    assertFalse(rejected.accepted());
    assertEquals(2L, rejected.snapshot().revision());
    assertTrue(rejected.snapshot().entries().isEmpty());
    assertTrue(rejected.issues().stream().anyMatch(issue ->
        issue.code() == DnvSbwVehicleCrewCatalog.IssueCode.UNSUPPORTED_SCHEMA));
  }

  @Test
  void duplicateVehicleOrAdapterRejectsTheWholeReload() {
    DnvSbwVehicleCrewCatalog catalog = new DnvSbwVehicleCrewCatalog();
    Map<String, JsonElement> documents = Map.of(
        "one", document("same", "superbwarfare:ac_130h", 1, 1, "seat_1"),
        "two", document("same", "superbwarfare:ac_130h", 1, 1, "seat_1"));

    DnvSbwVehicleCrewCatalog.ReloadResult result = catalog.replace(documents, 0L);

    assertFalse(result.accepted());
    assertTrue(result.snapshot().entries().isEmpty());
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvSbwVehicleCrewCatalog.IssueCode.DUPLICATE_VEHICLE_TYPE));
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvSbwVehicleCrewCatalog.IssueCode.DUPLICATE_ADAPTER_ID));
  }

  @Test
  void staleReloadCannotReplaceANewerCatalog() {
    DnvSbwVehicleCrewCatalog catalog = new DnvSbwVehicleCrewCatalog();
    catalog.replace(
        Map.of("valid", document("a", "superbwarfare:ac_130h", 1, 1, "seat_1")),
        0L);

    DnvSbwVehicleCrewCatalog.ReloadResult result = catalog.replace(Map.of(), 0L);

    assertFalse(result.accepted());
    assertEquals(1L, result.snapshot().revision());
    assertFalse(result.snapshot().entries().isEmpty());
    assertEquals(
        DnvSbwVehicleCrewCatalog.IssueCode.STALE_CATALOG_REVISION,
        result.issues().get(0).code());
  }

  @Test
  void unknownFieldsFractionalNumbersAndCrcOverflowFailClosed() {
    DnvSbwVehicleCrewCatalog catalog = new DnvSbwVehicleCrewCatalog();
    JsonElement invalid = JsonParser.parseString("""
        {
          "schemaVersion": 1,
          "adapterId": "a",
          "revision": 1.5,
          "vehicleTypeId": "superbwarfare:ac_130h",
          "expectedLayoutFingerprint": 4294967296,
          "aiStationIds": ["seat_1", "seat_1"],
          "unsafeDefault": true
        }
        """);

    DnvSbwVehicleCrewCatalog.ReloadResult result =
        catalog.replace(Map.of("invalid", invalid), 0L);

    assertFalse(result.accepted());
    assertTrue(result.snapshot().entries().isEmpty());
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvSbwVehicleCrewCatalog.IssueCode.UNKNOWN_FIELD));
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvSbwVehicleCrewCatalog.IssueCode.EXPECTED_INTEGER));
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvSbwVehicleCrewCatalog.IssueCode.OUT_OF_RANGE));
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvSbwVehicleCrewCatalog.IssueCode.DUPLICATE_STATION));
  }

  @Test
  void optionalFlightActuatorIsPreservedButNotGrantedByTheParser() {
    DnvSbwVehicleCrewCatalog catalog = new DnvSbwVehicleCrewCatalog();
    JsonElement document = JsonParser.parseString("""
        {
          "schemaVersion": 1,
          "adapterId": "doggiesnewvehicle:dragonrise_reforge_ac130_v1",
          "revision": 1,
          "vehicleTypeId": "dragonrise_reforge:ac130",
          "expectedLayoutFingerprint": 1895251193,
          "aiStationIds": ["seat_1"],
          "flightActuatorId": "doggiesnewvehicle:dragonrise_ac130_route_test_v1",
          "aimChannels": {
            "seat_1": {
              "aimChannelId": "sbw:passenger",
              "sharedWith": ["driver", "seat_1"]
            }
          }
        }
        """);

    DnvSbwVehicleCrewCatalog.ReloadResult result =
        catalog.replace(Map.of("dragonrise", document), 0L);

    assertTrue(result.accepted());
    assertEquals(
        "doggiesnewvehicle:dragonrise_ac130_route_test_v1",
        catalog.find("dragonrise_reforge:ac130")
            .orElseThrow().flightActuatorId());
    DnvSbwVehicleCrewAdapter.AimChannelBinding channel =
        catalog.find("dragonrise_reforge:ac130")
            .orElseThrow().aimChannels().get("seat_1");
    assertEquals("sbw:passenger", channel.aimChannelId());
    assertTrue(DnvSbwVehicleCrewAdapter.resolveFlightActuator(catalog.find("dragonrise_reforge:ac130").orElseThrow()).isEmpty());
    assertEquals(java.util.Set.of("driver", "seat_1"), channel.sharedWith());
  }

  private static JsonElement document(
      String adapterId,
      String vehicleTypeId,
      long revision,
      long fingerprint,
      String station) {
    return JsonParser.parseString("""
        {
          "schemaVersion": 1,
          "adapterId": "%s",
          "revision": %d,
          "vehicleTypeId": "%s",
          "expectedLayoutFingerprint": %d,
          "aiStationIds": ["%s"]
        }
        """.formatted(adapterId, revision, vehicleTypeId, fingerprint, station));
  }
}
