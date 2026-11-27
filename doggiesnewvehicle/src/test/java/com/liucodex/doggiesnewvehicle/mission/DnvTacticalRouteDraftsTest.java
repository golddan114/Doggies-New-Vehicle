package com.liucodex.doggiesnewvehicle.mission;

import com.liucodex.doggiesnewvehicle.route.DnvRouteWaypoint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DnvTacticalRouteDraftsTest {
  private static final UUID PLAYER = id(301);
  private static final UUID OTHER_PLAYER = id(302);
  private static final UUID CORE_A = id(303);
  private static final UUID CORE_B = id(304);
  private static final UUID VEHICLE_A = id(305);
  private static final UUID VEHICLE_B = id(306);
  private static final UUID VEHICLE_C = id(307);
  private static final String DIMENSION = "minecraft:overworld";
  private static final DnvTacticalRouteDrafts.Bounds BOUNDS =
      new DnvTacticalRouteDrafts.Bounds(
          -50_000, 50_000, -64, 319, -50_000, 50_000);

  @Test
  void unboundRouteCannotBeCreatedOrCommitted() {
    DnvTacticalRouteDrafts drafts = drafts();
    var missing = new DnvTacticalRouteDrafts.DraftRef(CORE_A, id(400));

    assertTrue(drafts.active(PLAYER, DIMENSION, 1L).isEmpty());
    var created = drafts.create(PLAYER, missing, id(401), 0L, DIMENSION, 2L);
    var committed = drafts.commit(
        PLAYER, missing, id(402), 0L, DIMENSION,
        List.of(point(0, 100, 0)), BOUNDS, 3L);

    assertFalse(created.accepted());
    assertEquals(DnvTacticalRouteDrafts.Issue.DRAFT_NOT_FOUND, created.issue());
    assertFalse(committed.accepted());
    assertEquals(DnvTacticalRouteDrafts.Issue.DRAFT_NOT_FOUND, committed.issue());
    assertTrue(drafts.active(PLAYER, DIMENSION, 4L).isEmpty());
  }

  @Test
  void ownedPlatformUsesTheStablePlatformIdForTheExistingDraftProtocol() {
    DnvTacticalRouteDrafts drafts = drafts();
    UUID platformId = id(490);
    var bound = drafts.bindOwnedPlatform(PLAYER, DIMENSION, platformId, 10L);

    assertTrue(bound.accepted());
    var projection = bound.projection().orElseThrow();
    assertEquals(platformId, projection.reference().coreId());
    assertEquals(platformId, projection.binding().vehicleId());
    assertTrue(drafts.active(OTHER_PLAYER, DIMENSION, 11L).isEmpty());
    assertEquals(1, drafts.invalidateCore(PLAYER, platformId));
    assertTrue(drafts.active(PLAYER, DIMENSION, 12L).isEmpty());
  }

  @Test
  void twoCoresKeepIndependentRoutesWhenThePlayerCyclesBack() {
    DnvTacticalRouteDrafts drafts = drafts();
    var draftA = bind(drafts, PLAYER, CORE_A, VEHICLE_A, 10L);
    var routeA = List.of(point(0, 100, 0), point(100, 120, 100));
    assertTrue(commit(drafts, PLAYER, draftA, id(401), routeA, 11L).accepted());

    var draftB = bind(drafts, PLAYER, CORE_B, VEHICLE_B, 12L);
    var routeB = List.of(point(500, 140, 500), point(700, 150, 500));
    assertTrue(commit(drafts, PLAYER, draftB, id(402), routeB, 13L).accepted());
    var selectedA = drafts.cycle(
        PLAYER,
        draftB.reference(),
        id(403),
        1L,
        DIMENSION,
        14L);

    assertTrue(selectedA.accepted());
    assertEquals(draftA.reference(), selectedA.projection().orElseThrow().reference());
    assertEquals(routeA, selectedA.projection().orElseThrow().snapshot().waypoints());
    assertEquals(2, selectedA.projection().orElseThrow().totalDrafts());
  }

  @Test
  void sameCoreCanOwnTwoExplicitDraftsWithoutCopyingWaypoints() {
    DnvTacticalRouteDrafts drafts = drafts();
    var first = bind(drafts, PLAYER, CORE_A, VEHICLE_A, 20L);
    var firstRoute = List.of(point(0, 100, 0), point(64, 110, 64));
    var savedFirst = commit(
        drafts, PLAYER, first, id(410), firstRoute, 21L)
        .projection().orElseThrow();

    var created = drafts.create(
        PLAYER,
        savedFirst.reference(),
        id(411),
        savedFirst.snapshot().revision(),
        DIMENSION,
        22L);

    assertTrue(created.accepted());
    var second = created.projection().orElseThrow();
    assertNotEquals(first.reference().draftId(), second.reference().draftId());
    assertEquals(CORE_A, second.reference().coreId());
    assertTrue(second.snapshot().waypoints().isEmpty());
    assertEquals(VEHICLE_A, second.binding().vehicleId());
    var returned = drafts.cycle(
        PLAYER, second.reference(), id(412), 0L, DIMENSION, 23L);
    assertEquals(firstRoute, returned.projection().orElseThrow().snapshot().waypoints());
  }

  @Test
  void playersUsingTheSameCoreIdentityNeverShareDraftsOrReceipts() {
    DnvTacticalRouteDrafts drafts = drafts();
    var first = bind(drafts, PLAYER, CORE_A, VEHICLE_A, 30L);
    var other = bind(drafts, OTHER_PLAYER, CORE_A, VEHICLE_A, 31L);
    assertTrue(commit(
        drafts,
        PLAYER,
        first,
        id(420),
        List.of(point(0, 100, 0)),
        32L).accepted());

    assertTrue(drafts.active(OTHER_PLAYER, DIMENSION, 33L)
        .orElseThrow().snapshot().waypoints().isEmpty());
    var forged = drafts.commit(
        OTHER_PLAYER,
        first.reference(),
        id(421),
        1L,
        DIMENSION,
        List.of(point(50, 100, 0)),
        BOUNDS,
        34L);
    assertEquals(DnvTacticalRouteDrafts.Issue.DRAFT_NOT_FOUND, forged.issue());
    assertEquals(other.reference(), drafts.active(
        OTHER_PLAYER, DIMENSION, 35L).orElseThrow().reference());
  }

  @Test
  void duplicateCommitReplaysOnceAndSameOperationWithNewPayloadIsRejected() {
    DnvTacticalRouteDrafts drafts = drafts();
    var draft = bind(drafts, PLAYER, CORE_A, VEHICLE_A, 40L);
    UUID operation = id(430);
    var route = List.of(point(0, 100, 0), point(80, 100, 0));
    var first = commit(drafts, PLAYER, draft, operation, route, 41L);
    var replay = drafts.commit(
        PLAYER,
        draft.reference(),
        operation,
        0L,
        DIMENSION,
        route,
        BOUNDS,
        42L);
    var mismatch = drafts.commit(
        PLAYER,
        draft.reference(),
        operation,
        0L,
        DIMENSION,
        List.of(point(200, 100, 0)),
        BOUNDS,
        43L);

    assertTrue(first.accepted());
    assertTrue(replay.accepted());
    assertTrue(replay.replayed());
    assertEquals(1L, replay.projection().orElseThrow().snapshot().revision());
    assertFalse(mismatch.accepted());
    assertEquals(DnvTacticalRouteDrafts.Issue.OPERATION_MISMATCH, mismatch.issue());
    assertEquals(1L, drafts.active(
        PLAYER, DIMENSION, 44L).orElseThrow().snapshot().revision());
  }

  @Test
  void staleRevisionAndGeometryFailuresReturnOnlyThatDraftSnapshot() {
    DnvTacticalRouteDrafts drafts = drafts();
    var draft = bind(drafts, PLAYER, CORE_A, VEHICLE_A, 50L);
    var saved = commit(
        drafts,
        PLAYER,
        draft,
        id(440),
        List.of(point(0, 100, 0)),
        51L);
    var stale = drafts.commit(
        PLAYER,
        draft.reference(),
        id(441),
        0L,
        DIMENSION,
        List.of(point(100, 100, 0)),
        BOUNDS,
        52L);
    var nonFinite = drafts.commit(
        PLAYER,
        draft.reference(),
        id(442),
        saved.projection().orElseThrow().snapshot().revision(),
        DIMENSION,
        List.of(point(Double.NaN, 100, 0)),
        BOUNDS,
        53L);

    assertEquals(DnvTacticalRouteDrafts.Issue.STALE_REVISION, stale.issue());
    assertEquals(point(0, 100, 0), stale.projection()
        .orElseThrow().snapshot().waypoints().get(0));
    assertEquals(DnvTacticalRouteDrafts.Issue.NON_FINITE_WAYPOINT, nonFinite.issue());
  }

  @Test
  void bindingANewVehicleCreatesAnEmptyDraftInsteadOfMovingTheOldRoute() {
    DnvTacticalRouteDrafts drafts = drafts();
    var original = bind(drafts, PLAYER, CORE_A, VEHICLE_A, 60L);
    var route = List.of(point(0, 120, 0), point(100, 130, 0));
    commit(drafts, PLAYER, original, id(450), route, 61L);

    var replacement = bind(drafts, PLAYER, CORE_A, VEHICLE_C, 62L);

    assertTrue(replacement.snapshot().waypoints().isEmpty());
    assertEquals(VEHICLE_C, replacement.binding().vehicleId());
    var returned = drafts.cycle(
        PLAYER, replacement.reference(), id(451), 0L, DIMENSION, 63L)
        .projection().orElseThrow();
    assertEquals(VEHICLE_A, returned.binding().vehicleId());
    assertEquals(route, returned.snapshot().waypoints());
  }

  @Test
  void logoutCoreInvalidationExpiryAndClockRegressionRejectOldIdentities() {
    DnvTacticalRouteDrafts drafts = drafts();
    var coreA = bind(drafts, PLAYER, CORE_A, VEHICLE_A, 70L);
    UUID committedOperation = id(459);
    List<DnvRouteWaypoint> committedRoute =
        List.of(point(0, 120, 0), point(100, 130, 0));
    assertTrue(commit(
        drafts, PLAYER, coreA, committedOperation, committedRoute, 70L).accepted());
    bind(drafts, PLAYER, CORE_B, VEHICLE_B, 71L);
    assertEquals(1, drafts.invalidateCore(PLAYER, CORE_A));
    assertEquals(DnvTacticalRouteDrafts.Issue.DRAFT_NOT_FOUND, drafts.commit(
        PLAYER,
        coreA.reference(),
        committedOperation,
        0L,
        DIMENSION,
        committedRoute,
        BOUNDS,
        72L).issue());

    assertEquals(1, drafts.invalidate(PLAYER));
    assertTrue(drafts.active(PLAYER, DIMENSION, 73L).isEmpty());
    var reconnected = bind(drafts, PLAYER, CORE_A, VEHICLE_A, 74L);
    drafts.expire(74L + DnvTacticalRouteDrafts.DRAFT_LIFETIME_TICKS + 1L);
    assertTrue(drafts.active(PLAYER, DIMENSION, 20_000L).isEmpty());

    bind(drafts, PLAYER, CORE_A, VEHICLE_A, 30_000L);
    drafts.expire(29_999L);
    assertEquals(0, drafts.routeCount());
    assertNotEquals(coreA.reference(), reconnected.reference());
  }

  @Test
  void perCoreAndPerPlayerCapacityRejectWithoutEvictingValidDrafts() {
    DnvTacticalRouteDrafts drafts = drafts();
    var current = bind(drafts, PLAYER, CORE_A, VEHICLE_A, 100L);
    for (int index = 1; index < DnvTacticalRouteDrafts.MAX_DRAFTS_PER_CORE; index++) {
      current = drafts.create(
          PLAYER,
          current.reference(),
          id(500 + index),
          current.snapshot().revision(),
          DIMENSION,
          100L + index).projection().orElseThrow();
    }
    var coreLimit = drafts.create(
        PLAYER,
        current.reference(),
        id(510),
        current.snapshot().revision(),
        DIMENSION,
        110L);
    assertEquals(DnvTacticalRouteDrafts.Issue.CORE_CAPACITY_REACHED, coreLimit.issue());

    for (int index = DnvTacticalRouteDrafts.MAX_DRAFTS_PER_CORE;
        index < DnvTacticalRouteDrafts.MAX_DRAFTS_PER_PLAYER;
        index++) {
      assertTrue(drafts.bindVehicle(
          PLAYER,
          DIMENSION,
          id(600 + index),
          id(700 + index),
          120L + index).accepted());
    }
    var playerLimit = drafts.bindVehicle(
        PLAYER, DIMENSION, id(800), id(801), 140L);
    assertEquals(
        DnvTacticalRouteDrafts.Issue.PLAYER_CAPACITY_REACHED,
        playerLimit.issue());
    assertEquals(DnvTacticalRouteDrafts.MAX_DRAFTS_PER_PLAYER, drafts.routeCount());
  }

  @Test
  void routeGeometryKeepsIndependentFiniteSpacingSegmentAndTotalBudgets() {
    assertEquals(
        DnvTacticalRouteDrafts.Issue.WAYPOINTS_TOO_CLOSE,
        DnvTacticalRouteDrafts.validate(
            List.of(point(0, 100, 0), point(1, 100, 1)), BOUNDS));
    assertEquals(
        DnvTacticalRouteDrafts.Issue.SEGMENT_TOO_LONG,
        DnvTacticalRouteDrafts.validate(
            List.of(point(0, 100, 0), point(9_000, 100, 0)), BOUNDS));
    assertEquals(
        DnvTacticalRouteDrafts.Issue.OUTSIDE_WORLD_BOUNDS,
        DnvTacticalRouteDrafts.validate(List.of(point(0, 500, 0)), BOUNDS));
    assertEquals(
        DnvTacticalRouteDrafts.Issue.ROUTE_TOO_LONG,
        DnvTacticalRouteDrafts.validate(
            List.of(
                point(0, 100, 0),
                point(7_000, 100, 0),
                point(14_000, 100, 0),
                point(21_000, 100, 0),
                point(28_000, 100, 0),
                point(35_000, 100, 0)),
            BOUNDS));
  }

  private static DnvTacticalRouteDrafts drafts() {
    AtomicLong next = new AtomicLong(1_000L);
    return new DnvTacticalRouteDrafts(() -> id(next.getAndIncrement()));
  }

  private static DnvTacticalRouteDrafts.DraftProjection bind(
      DnvTacticalRouteDrafts drafts,
      UUID player,
      UUID core,
      UUID vehicle,
      long gameTime) {
    return drafts.bindVehicle(player, DIMENSION, core, vehicle, gameTime)
        .projection().orElseThrow();
  }

  private static DnvTacticalRouteDrafts.OperationResult commit(
      DnvTacticalRouteDrafts drafts,
      UUID player,
      DnvTacticalRouteDrafts.DraftProjection draft,
      UUID operation,
      List<DnvRouteWaypoint> route,
      long gameTime) {
    return drafts.commit(
        player,
        draft.reference(),
        operation,
        draft.snapshot().revision(),
        DIMENSION,
        route,
        BOUNDS,
        gameTime);
  }

  private static DnvRouteWaypoint point(
      double x, double y, double z) {
    return new DnvRouteWaypoint(x, y, z);
  }

  private static UUID id(long value) {
    return new UUID(0L, value);
  }
}
