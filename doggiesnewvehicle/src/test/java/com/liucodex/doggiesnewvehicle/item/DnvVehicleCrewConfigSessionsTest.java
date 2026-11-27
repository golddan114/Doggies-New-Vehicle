package com.liucodex.doggiesnewvehicle.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.compat.DnvSbwVehicleCrewAdapter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvVehicleCrewConfigSessionsTest {
  private static final UUID OWNER =
      UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID OTHER =
      UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID CORE =
      UUID.fromString("00000000-0000-0000-0000-000000000203");
  private static final long CATALOG_REVISION = 9L;

  @Test
  void opensOwnedCoreWithStableListAndHardwareBoundChoices() {
    DnvVehicleCrewConfigSessions sessions = new DnvVehicleCrewConfigSessions();
    DnvVehicleCrewConfigSessions.OpenResult result = sessions.open(
        draft(OWNER),
        allowlist(),
        OWNER,
        false,
        CATALOG_REVISION,
        100L,
        DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND);

    assertTrue(result.accepted());
    DnvVehicleCrewConfigSessions.View view = result.view().orElseThrow();
    assertEquals(List.of("seat_0", "seat_1"), view.stations().stream()
        .map(DnvVehicleCrewConfigSessions.StationView::stationId)
        .toList());
    assertTrue(view.stations().get(0).allowedModes().contains(
        DnvControlAuthority.WeaponMode.AUTO_ENGAGE));
    assertEquals(
        List.of(
            DnvControlAuthority.WeaponMode.MANUAL_ONLY,
            DnvControlAuthority.WeaponMode.HOLD_FIRE),
        view.stations().get(1).allowedModes());
    assertEquals(1, sessions.size());
  }

  @Test
  void ordinaryPlayerCannotOpenAnotherPlayersCore() {
    DnvVehicleCrewConfigSessions.OpenResult result =
        new DnvVehicleCrewConfigSessions().open(
            draft(OWNER),
            allowlist(),
            OTHER,
            false,
            CATALOG_REVISION,
            100L,
            DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND);

    assertFalse(result.accepted());
    assertEquals(DnvVehicleCrewConfigSessions.Issue.NOT_OWNER, result.issue());
  }

  @Test
  void commitCanonicalizesStationOrderAndPreservesCoreIdentity() {
    DnvVehicleCrewConfigSessions sessions = new DnvVehicleCrewConfigSessions();
    DnvVehicleCrewConfigSessions.View view = open(sessions, 100L);
    List<DnvVehicleCrewProfile.StationDraft> reversed = List.of(
        new DnvVehicleCrewProfile.StationDraft(
            "seat_1", DnvControlAuthority.WeaponMode.HOLD_FIRE),
        new DnvVehicleCrewProfile.StationDraft(
            "seat_0", DnvControlAuthority.WeaponMode.TRACK_ONLY));

    DnvVehicleCrewConfigSessions.CommitResult result = sessions.commit(
        view.token(),
        OWNER,
        false,
        CATALOG_REVISION,
        101L,
        DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND,
        draft(OWNER),
        DnvVehicleCrewProfile.FlightMode.MANUAL_ONLY,
        reversed);

    assertTrue(result.accepted());
    DnvVehicleCrewProfile.Snapshot snapshot = result.snapshot().orElseThrow();
    assertEquals(CORE, snapshot.coreId());
    assertEquals(OWNER, snapshot.configuredBy());
    assertEquals(List.of("seat_0", "seat_1"), List.copyOf(snapshot.stations().keySet()));
    assertEquals(
        DnvControlAuthority.WeaponMode.TRACK_ONLY,
        snapshot.stations().get("seat_0").mode());
    assertEquals(0, sessions.size());
  }

  @Test
  void wireValueCommitPreservesValidationAndRejectsMalformedEnums() {
    DnvVehicleCrewConfigSessions sessions = new DnvVehicleCrewConfigSessions();
    DnvVehicleCrewConfigSessions.View view = open(sessions, 100L);
    DnvVehicleCrewConfigSessions.CommitResult accepted = sessions.commitWireValues(
        view.token(), OWNER, false, CATALOG_REVISION, 101L,
        DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND, draft(OWNER),
        "MANUAL_ONLY", List.of("seat_1", "seat_0"),
        List.of("HOLD_FIRE", "TRACK_ONLY"));

    assertTrue(accepted.accepted());
    assertEquals(List.of("seat_0", "seat_1"),
        List.copyOf(accepted.snapshot().orElseThrow().stations().keySet()));

    DnvVehicleCrewConfigSessions.View malformed = open(sessions, 200L);
    assertEquals(
        DnvVehicleCrewConfigSessions.Issue.PROFILE_REJECTED,
        sessions.commitWireValues(
            malformed.token(), OWNER, false, CATALOG_REVISION, 201L,
            DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND, draft(OWNER),
            "NOT_A_MODE", List.of("seat_0", "seat_1"),
            List.of("TRACK_ONLY", "HOLD_FIRE")).issue());
  }

  @Test
  void staleCatalogChangedCoreAndExpiredSessionFailClosed() {
    DnvVehicleCrewConfigSessions sessions = new DnvVehicleCrewConfigSessions();
    DnvVehicleCrewConfigSessions.View staleCatalog = open(sessions, 100L);
    assertEquals(
        DnvVehicleCrewConfigSessions.Issue.CATALOG_CHANGED,
        commit(sessions, staleCatalog, draft(OWNER), CATALOG_REVISION + 1L, 101L).issue());

    DnvVehicleCrewConfigSessions.View changedCore = open(sessions, 200L);
    assertEquals(
        DnvVehicleCrewConfigSessions.Issue.CORE_CHANGED,
        commit(sessions, changedCore, draft(OTHER), CATALOG_REVISION, 201L).issue());

    DnvVehicleCrewConfigSessions.View expired = open(sessions, 300L);
    assertEquals(
        DnvVehicleCrewConfigSessions.Issue.SESSION_EXPIRED,
        commit(
            sessions,
            expired,
            draft(OWNER),
            CATALOG_REVISION,
            300L + DnvVehicleCrewConfigSessions.MAX_SESSION_AGE_TICKS + 1L).issue());
    assertEquals(0, sessions.size());
  }

  @Test
  void manualHardwareCannotBePromotedAndPlayerCleanupIsIsolated() {
    DnvVehicleCrewConfigSessions sessions = new DnvVehicleCrewConfigSessions();
    DnvVehicleCrewConfigSessions.View view = open(sessions, 100L);
    List<DnvVehicleCrewProfile.StationDraft> invalid = List.of(
        new DnvVehicleCrewProfile.StationDraft(
            "seat_0", DnvControlAuthority.WeaponMode.AUTO_ENGAGE),
        new DnvVehicleCrewProfile.StationDraft(
            "seat_1", DnvControlAuthority.WeaponMode.AUTO_ENGAGE));

    DnvVehicleCrewConfigSessions.CommitResult result = sessions.commit(
        view.token(),
        OWNER,
        false,
        CATALOG_REVISION,
        101L,
        DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND,
        draft(OWNER),
        DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
        invalid);

    assertFalse(result.accepted());
    assertEquals(DnvVehicleCrewConfigSessions.Issue.STATIONS_CHANGED, result.issue());
    sessions.closePlayer(OTHER);
    assertEquals(1, sessions.size());
    sessions.closePlayer(OWNER);
    assertEquals(0, sessions.size());
  }

  @Test
  void retiredCoreRemainsEditableWithoutGrantingOldAiFlight() {
    DnvVehicleCrewProfile.Draft draft = new DnvVehicleCrewProfile.Draft(
        DnvVehicleCrewProfile.CURRENT_SCHEMA_VERSION,
        CORE,
        OWNER,
        "doggiesnewvehicle:dragonrise_reforge_ac130_v1",
        1L,
        "superbwarfare:ac_130h",
        DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
        List.of(
            new DnvVehicleCrewProfile.StationDraft(
                "seat_0", DnvControlAuthority.WeaponMode.MANUAL_ONLY),
            new DnvVehicleCrewProfile.StationDraft(
                "seat_1", DnvControlAuthority.WeaponMode.AUTO_ENGAGE)));
    DnvSbwVehicleCrewAdapter.AllowlistEntry allowlist =
        new DnvSbwVehicleCrewAdapter.AllowlistEntry(
            draft.adapterId(),
            draft.adapterRevision(),
            draft.vehicleTypeId(),
            1895251193L,
            Set.of("seat_1"),
            "doggiesnewvehicle:sbw_ac130h_route_v1");
    DnvVehicleCrewConfigSessions sessions = new DnvVehicleCrewConfigSessions();

    DnvVehicleCrewConfigSessions.OpenResult opened = sessions.open(
        draft,
        allowlist,
        OWNER,
        false,
        CATALOG_REVISION,
        100L,
        DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND);

    assertTrue(opened.accepted());
    assertEquals(List.of(
        DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
        DnvVehicleCrewProfile.FlightMode.MANUAL_ONLY),
        opened.view().orElseThrow().allowedFlightModes());
    assertEquals(
        DnvVehicleCrewConfigSessions.Issue.FLIGHT_MODE_NOT_ALLOWED,
        sessions.commit(
            opened.view().orElseThrow().token(),
            OWNER,
            false,
            CATALOG_REVISION,
            101L,
            DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND,
            draft,
            DnvVehicleCrewProfile.FlightMode.AI_ONLY,
            draft.stations()).issue());
    assertEquals(DnvVehicleCrewConfigSessions.Issue.FLIGHT_MODE_NOT_ALLOWED, sessions.commit(
        opened.view().orElseThrow().token(),
        OWNER,
        false,
        CATALOG_REVISION,
        102L,
        DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND,
        draft,
        DnvVehicleCrewProfile.FlightMode.AI_PREFERRED,
        draft.stations()).issue());
  }

  private static DnvVehicleCrewConfigSessions.View open(
      DnvVehicleCrewConfigSessions sessions, long tick) {
    return sessions.open(
        draft(OWNER),
        allowlist(),
        OWNER,
        false,
        CATALOG_REVISION,
        tick,
        DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND)
        .view()
        .orElseThrow();
  }

  private static DnvVehicleCrewConfigSessions.CommitResult commit(
      DnvVehicleCrewConfigSessions sessions,
      DnvVehicleCrewConfigSessions.View view,
      DnvVehicleCrewProfile.Draft current,
      long catalogRevision,
      long tick) {
    return sessions.commit(
        view.token(),
        OWNER,
        false,
        catalogRevision,
        tick,
        DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND,
        current,
        DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
        draft(OWNER).stations());
  }

  private static DnvVehicleCrewProfile.Draft draft(UUID configuredBy) {
    return new DnvVehicleCrewProfile.Draft(
        DnvVehicleCrewProfile.CURRENT_SCHEMA_VERSION,
        CORE,
        configuredBy,
        "sbw_exact",
        7L,
        "superbwarfare:test_vehicle",
        DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
        List.of(
            new DnvVehicleCrewProfile.StationDraft(
                "seat_0", DnvControlAuthority.WeaponMode.AUTO_ENGAGE),
            new DnvVehicleCrewProfile.StationDraft(
                "seat_1", DnvControlAuthority.WeaponMode.MANUAL_ONLY)));
  }

  private static DnvSbwVehicleCrewAdapter.AllowlistEntry allowlist() {
    return new DnvSbwVehicleCrewAdapter.AllowlistEntry(
        "sbw_exact",
        7L,
        "superbwarfare:test_vehicle",
        42L,
        Set.of("seat_0"));
  }
}
