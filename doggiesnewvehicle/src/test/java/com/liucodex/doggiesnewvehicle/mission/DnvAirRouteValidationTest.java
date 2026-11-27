package com.liucodex.doggiesnewvehicle.mission;

import com.liucodex.doggiesnewvehicle.route.DnvRouteWaypoint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DnvAirRouteValidationTest {
  private static final DnvTacticalRouteDrafts.Bounds BOUNDS =
      new DnvTacticalRouteDrafts.Bounds(-40_000, 40_000, -64, 320, -40_000, 40_000);
  private static final DnvAirRouteValidation.FlightEnvelope ENVELOPE =
      new DnvAirRouteValidation.FlightEnvelope(64.0D, 120.0D, 256.0D, 40.0D, 2.0D);

  @Test
  void geometryReportsStableZeroLengthLongBoundsAndFiniteReasons() {
    assertReason(
        List.of(point(0, 100, 0), point(0, 100, 0)),
        DnvAirRouteValidation.Reason.ZERO_LENGTH_SEGMENT);
    assertReason(
        List.of(point(0, 100, 0), point(9_000, 100, 0)),
        DnvAirRouteValidation.Reason.SEGMENT_TOO_LONG);
    assertReason(
        List.of(point(0, 500, 0), point(32, 100, 0)),
        DnvAirRouteValidation.Reason.OUTSIDE_WORLD_BOUNDS);
    assertReason(
        List.of(point(0, 100, 0), point(Double.NaN, 100, 0)),
        DnvAirRouteValidation.Reason.NON_FINITE_WAYPOINT);
  }

  @Test
  void loadedCorridorDistinguishesExecutableObstacleAndConfigurationChange() {
    List<DnvRouteWaypoint> route =
        List.of(point(0, 100, 0), point(64, 100, 0));
    AtomicReference<Double> surface = new AtomicReference<>(70.0D);

    DnvAirRouteValidation.Result executable = DnvAirRouteValidation.validate(
        route, BOUNDS, ENVELOPE,
        (x, z) -> DnvAirRouteValidation.TerrainSample.loaded(surface.get()));
    surface.set(230.0D);
    DnvAirRouteValidation.Result changedTerrain = DnvAirRouteValidation.validate(
        route, BOUNDS, ENVELOPE,
        (x, z) -> DnvAirRouteValidation.TerrainSample.loaded(surface.get()));
    DnvAirRouteValidation.Result changedConfiguration = DnvAirRouteValidation.validate(
        route,
        BOUNDS,
        new DnvAirRouteValidation.FlightEnvelope(
            64.0D, 120.0D, 120.0D, 40.0D, 2.0D),
        (x, z) -> DnvAirRouteValidation.TerrainSample.loaded(90.0D));

    assertEquals(DnvAirRouteValidation.Status.EXECUTABLE, executable.status());
    assertEquals(DnvAirRouteValidation.Status.BLOCKED, changedTerrain.status());
    assertEquals(
        DnvAirRouteValidation.Reason.TERRAIN_CLEARANCE_EXCEEDS_ENVELOPE,
        changedTerrain.reason());
    assertEquals(DnvAirRouteValidation.Status.BLOCKED, changedConfiguration.status());
  }

  @Test
  void unloadedTerrainAndMissingConfigurationStayUnverified() {
    List<DnvRouteWaypoint> route =
        List.of(point(0, 100, 0), point(64, 100, 0));
    DnvAirRouteValidation.Result unloaded = DnvAirRouteValidation.validate(
        route, BOUNDS, ENVELOPE,
        (x, z) -> DnvAirRouteValidation.TerrainSample.unloaded());
    DnvAirRouteValidation.Result missing =
        DnvAirRouteValidation.configurationUnavailable(route, BOUNDS);

    assertEquals(DnvAirRouteValidation.Status.UNVERIFIED, unloaded.status());
    assertEquals(DnvAirRouteValidation.Reason.CHUNK_UNLOADED, unloaded.reason());
    assertEquals(DnvAirRouteValidation.Status.UNVERIFIED, missing.status());
    assertEquals(
        DnvAirRouteValidation.Reason.CONFIGURATION_UNAVAILABLE, missing.reason());
  }

  @Test
  void samplingBudgetIsHardBoundAndLeavesRemainingSegmentsUnverified() {
    List<DnvRouteWaypoint> route = new ArrayList<>();
    for (int index = 0; index <= 4; index++) {
      route.add(point(index * 8_192.0D, 120, 0));
    }
    AtomicInteger calls = new AtomicInteger();

    DnvAirRouteValidation.Result result = DnvAirRouteValidation.validate(
        route,
        BOUNDS,
        ENVELOPE,
        (x, z) -> {
          calls.incrementAndGet();
          return DnvAirRouteValidation.TerrainSample.loaded(60.0D);
        });

    assertEquals(DnvAirRouteValidation.MAX_TERRAIN_SAMPLES, calls.get());
    assertEquals(DnvAirRouteValidation.MAX_TERRAIN_SAMPLES, result.sampleCount());
    assertEquals(DnvAirRouteValidation.Status.UNVERIFIED, result.status());
    assertTrue(result.segments().stream().anyMatch(segment ->
        segment.reason() == DnvAirRouteValidation.Reason.SAMPLE_BUDGET_EXCEEDED));
  }

  private static void assertReason(
      List<DnvRouteWaypoint> route,
      DnvAirRouteValidation.Reason expected) {
    DnvAirRouteValidation.Result result = DnvAirRouteValidation.validate(
        route, BOUNDS, ENVELOPE,
        (x, z) -> DnvAirRouteValidation.TerrainSample.loaded(64.0D));
    assertEquals(DnvAirRouteValidation.Status.BLOCKED, result.status());
    assertEquals(expected, result.reason());
  }

  private static DnvRouteWaypoint point(
      double x, double y, double z) {
    return new DnvRouteWaypoint(x, y, z);
  }
}
