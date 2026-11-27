package com.liucodex.doggiesnewvehicle.mission;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewClientProjection;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewClientProjection.CommitResponse;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewConfigSessions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DnvClientProjectionDispatchTest {
  @BeforeEach
  @AfterEach
  void clearCallbacks() {
    DnvVehicleCrewClientProjection.install(value -> {}, value -> {});
    DnvTacticalRouteClientProjection.install(value -> {});
  }

  @Test
  void installingAClientDoesNotReplayOldMessagesOrRetainThePreviousSink() {
    var old = new ArrayList<DnvTacticalRouteProtocol.RouteResponse>();
    var current = new ArrayList<DnvTacticalRouteProtocol.RouteResponse>();
    var response = routeResponse();
    DnvTacticalRouteClientProjection.response(response);
    DnvTacticalRouteClientProjection.install(old::add);
    assertTrue(old.isEmpty());
    DnvTacticalRouteClientProjection.response(response);
    DnvTacticalRouteClientProjection.install(current::add);
    assertTrue(current.isEmpty());
    DnvTacticalRouteClientProjection.response(response);
    assertEquals(List.of(response), old);
    assertEquals(List.of(response), current);
  }

  @Test
  void invalidInstallCannotPartiallyReplaceAnyCallbackSet() {
    var crew = new ArrayList<CommitResponse>();
    var routes = new ArrayList<DnvTacticalRouteProtocol.RouteResponse>();
    DnvVehicleCrewClientProjection.install(value -> {}, crew::add);
    DnvTacticalRouteClientProjection.install(routes::add);

    assertThrows(NullPointerException.class, () ->
        DnvVehicleCrewClientProjection.install(null, value -> fail()));
    assertThrows(NullPointerException.class, () ->
        DnvTacticalRouteClientProjection.install(null));

    DnvVehicleCrewClientProjection.respond(rejectedCrewResponse());
    DnvTacticalRouteClientProjection.response(routeResponse());
    assertEquals(1, crew.size());
    assertEquals(1, routes.size());
  }

  @Test
  void crewOpeningAndRejectedReceiptPreserveIdentityAndSessionCorrelation() {
    var token = UUID.randomUUID();
    var mode = DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED;
    var view = new DnvVehicleCrewConfigSessions.View(token,
        DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND, UUID.randomUUID(),
        "superbwarfare:bradley", mode, List.of(mode), List.of());
    var response = new CommitResponse(token, false,
        DnvVehicleCrewConfigSessions.Issue.SESSION_EXPIRED,
        Optional.empty(), List.of(), List.of());
    var views = new ArrayList<DnvVehicleCrewConfigSessions.View>();
    var responses = new ArrayList<CommitResponse>();
    DnvVehicleCrewClientProjection.install(views::add, responses::add);

    DnvVehicleCrewClientProjection.open(view);
    DnvVehicleCrewClientProjection.respond(response);

    assertSame(view, views.get(0));
    assertSame(response, responses.get(0));
    assertEquals(token, responses.get(0).token());
    assertFalse(responses.get(0).accepted());
  }

  @Test
  void crewResponseStillRejectsContradictionsAndCopiesSavedStationLists() {
    var token = UUID.randomUUID();
    var ids = new ArrayList<>(List.of("left"));
    var modes = new ArrayList<>(List.of("AI_ONLY"));
    var response = new CommitResponse(token, true,
        DnvVehicleCrewConfigSessions.Issue.NONE, Optional.of("AI_ONLY"), ids, modes);
    ids.clear();
    modes.clear();
    assertEquals(List.of("left"), response.savedStationIds());
    assertEquals(List.of("AI_ONLY"), response.savedStationModeNames());
    assertThrows(UnsupportedOperationException.class, () -> response.savedStationIds().clear());
    assertThrows(IllegalArgumentException.class, () -> new CommitResponse(token, true,
        DnvVehicleCrewConfigSessions.Issue.SESSION_EXPIRED, Optional.of("AI_ONLY"), List.of(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new CommitResponse(token, true,
        DnvVehicleCrewConfigSessions.Issue.NONE, Optional.empty(), List.of(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new CommitResponse(token, true,
        DnvVehicleCrewConfigSessions.Issue.NONE, Optional.of("AI_ONLY"), List.of("left"), List.of()));
  }

  @Test
  void routeResponseKeepsDraftOperationRevisionAndIssueTogether() {
    var response = routeResponse();
    var results = new ArrayList<DnvTacticalRouteProtocol.RouteResponse>();
    DnvTacticalRouteClientProjection.install(results::add);

    DnvTacticalRouteClientProjection.response(response);

    assertSame(response, results.get(0));
    assertEquals(7L, results.get(0).projection()
        .orElseThrow().snapshot().revision());
    assertEquals(DnvTacticalRouteDrafts.Issue.NONE, results.get(0).issue());
    assertEquals(DnvTacticalRouteProtocol.Action.SNAPSHOT, results.get(0).action());
  }

  private static DnvTacticalRouteDrafts.Snapshot route() {
    return new DnvTacticalRouteDrafts.Snapshot(7L, "minecraft:overworld", List.of());
  }

  private static DnvTacticalRouteProtocol.RouteResponse routeResponse() {
    UUID core = UUID.fromString("56000000-0000-0000-0000-000000000006");
    UUID draft = UUID.fromString("57000000-0000-0000-0000-000000000007");
    var reference = new DnvTacticalRouteDrafts.DraftRef(core, draft);
    var projection = new DnvTacticalRouteDrafts.DraftProjection(
        reference,
        route(),
        new DnvTacticalRouteDrafts.VehicleBinding(
            "minecraft:overworld",
            core,
            UUID.fromString("58000000-0000-0000-0000-000000000008")),
        1,
        1);
    return new DnvTacticalRouteProtocol.RouteResponse(
        new DnvTacticalRouteProtocol.RequestContext(
            UUID.fromString("59000000-0000-0000-0000-000000000009"), 1L),
        DnvTacticalRouteProtocol.Action.SNAPSHOT,
        true,
        false,
        Optional.of(projection),
        Optional.of(new DnvAirRouteValidation.Result(
            DnvAirRouteValidation.Status.BLOCKED,
            DnvAirRouteValidation.Reason.ROUTE_INCOMPLETE,
            List.of(),
            0,
            DnvAirRouteValidation.MAX_TERRAIN_SAMPLES)),
        DnvTacticalRouteDrafts.Issue.NONE);
  }

  private static CommitResponse rejectedCrewResponse() {
    return new CommitResponse(UUID.randomUUID(), false,
        DnvVehicleCrewConfigSessions.Issue.SESSION_EXPIRED, Optional.empty(), List.of(), List.of());
  }

}
