package com.liucodex.doggiesnewvehicle.ai;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvVehicleCrewProfileTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000012");
  private static final UUID CORE = UUID.fromString("00000000-0000-0000-0000-000000000013");

  @Test
  void acceptsExactVehicleAndBuildsImmutableStationPolicies() {
    DnvVehicleCrewProfile.ValidationResult result = DnvVehicleCrewProfile.validate(
        draft(OWNER, DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED),
        template(false),
        new DnvVehicleCrewProfile.ValidationContext(OWNER, false));

    assertTrue(result.accepted());
    DnvVehicleCrewProfile.Snapshot snapshot = result.snapshot().orElseThrow();
    assertEquals(2, snapshot.stations().size());
    assertEquals(List.of("nose", "tail"), List.copyOf(snapshot.stations().keySet()));
    assertEquals(0, snapshot.stations().get("nose").seatIndex());
    org.junit.jupiter.api.Assertions.assertThrows(
        UnsupportedOperationException.class,
        () -> snapshot.stations().clear());
  }

  @Test
  void ordinaryPlayerCannotAuthorAnotherPlayersCore() {
    DnvVehicleCrewProfile.ValidationResult result = DnvVehicleCrewProfile.validate(
        draft(OTHER, DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED),
        template(false),
        new DnvVehicleCrewProfile.ValidationContext(OWNER, false));
    assertFalse(result.accepted());
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvVehicleCrewProfile.IssueCode.NOT_CALLER));
  }

  @Test
  void rejectsAiFlightForTurretOnlyThirdPartyAdapter() {
    DnvVehicleCrewProfile.ValidationResult result = DnvVehicleCrewProfile.validate(
        draft(OWNER, DnvVehicleCrewProfile.FlightMode.AI_PREFERRED),
        template(false),
        new DnvVehicleCrewProfile.ValidationContext(OWNER, false));
    assertFalse(result.accepted());
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == DnvVehicleCrewProfile.IssueCode.AI_FLIGHT_UNSUPPORTED));
  }

  @Test
  void rejectsUnknownDuplicateMissingAndDisallowedStationsTogether() {
    DnvVehicleCrewProfile.Draft invalid = new DnvVehicleCrewProfile.Draft(
        1, CORE, OWNER, "sbw_test", 7L, "superbwarfare:test_vehicle",
        DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
        List.of(
            new DnvVehicleCrewProfile.StationDraft(
                "nose", DnvControlAuthority.WeaponMode.AUTO_ENGAGE),
            new DnvVehicleCrewProfile.StationDraft(
                "nose", DnvControlAuthority.WeaponMode.MANUAL_ONLY),
            new DnvVehicleCrewProfile.StationDraft(
                "unknown", DnvControlAuthority.WeaponMode.HOLD_FIRE)));
    DnvVehicleCrewProfile.ValidationResult result = DnvVehicleCrewProfile.validate(
        invalid, template(false), new DnvVehicleCrewProfile.ValidationContext(OWNER, false));

    assertFalse(result.accepted());
    Set<DnvVehicleCrewProfile.IssueCode> codes = result.issues().stream()
        .map(DnvVehicleCrewProfile.Issue::code)
        .collect(java.util.stream.Collectors.toSet());
    assertTrue(codes.contains(DnvVehicleCrewProfile.IssueCode.DUPLICATE_STATION));
    assertTrue(codes.contains(DnvVehicleCrewProfile.IssueCode.UNKNOWN_STATION));
    assertTrue(codes.contains(DnvVehicleCrewProfile.IssueCode.MISSING_STATION));
  }

  @Test
  void adapterRejectsTwoLogicalStationsMappedToOnePhysicalSeat() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new DnvVehicleCrewProfile.AdapterTemplate(
            "bad", 1L, "superbwarfare:test_vehicle", false,
            List.of(
                new DnvVehicleCrewProfile.StationTemplate(
                    "nose", 0, true, Set.of(DnvControlAuthority.WeaponMode.AUTO_ENGAGE)),
                new DnvVehicleCrewProfile.StationTemplate(
                    "tail", 0, true, Set.of(DnvControlAuthority.WeaponMode.AUTO_ENGAGE)))));
  }

  private static DnvVehicleCrewProfile.Draft draft(
      UUID configuredBy, DnvVehicleCrewProfile.FlightMode flightMode) {
    return new DnvVehicleCrewProfile.Draft(
        1, CORE, configuredBy, "sbw_test", 7L, "superbwarfare:test_vehicle", flightMode,
        List.of(
            new DnvVehicleCrewProfile.StationDraft(
                "nose", DnvControlAuthority.WeaponMode.AUTO_ENGAGE),
            new DnvVehicleCrewProfile.StationDraft(
                "tail", DnvControlAuthority.WeaponMode.MANUAL_ONLY)));
  }

  private static DnvVehicleCrewProfile.AdapterTemplate template(boolean aiFlight) {
    return new DnvVehicleCrewProfile.AdapterTemplate(
        "sbw_test", 7L, "superbwarfare:test_vehicle", aiFlight,
        List.of(
            new DnvVehicleCrewProfile.StationTemplate(
                "nose", 0, true, Set.of(
                    DnvControlAuthority.WeaponMode.AUTO_ENGAGE,
                    DnvControlAuthority.WeaponMode.MANUAL_ONLY)),
            new DnvVehicleCrewProfile.StationTemplate(
                "tail", 1, false, Set.of(DnvControlAuthority.WeaponMode.MANUAL_ONLY))));
  }
}
