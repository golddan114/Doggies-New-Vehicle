package com.liucodex.doggiesnewvehicle.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.core.DnvPlatformCapabilities;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class DnvRouteFollowerTest {
  @Test
  void turretRejectsEveryMovementPlan() {
    DnvRouteFollower follower = new DnvRouteFollower();
    DnvRouteFollower.RouteUpdate update = follower.apply(
        plan(DnvRoutePlan.MovementMedium.GROUND),
        routeCapability(DnvPlatformCapabilities.defaults(
            DnvPlatformCapabilities.PlatformFamily.TURRET)));
    assertFalse(update.accepted());
    assertEquals(DnvRouteFollower.Reason.MOVEMENT_UNSUPPORTED, update.reason());
  }

  @Test
  void groundConsumesOnlyServerLocalTraversabilityAndNeverClientHeight() {
    DnvRouteFollower follower = configured(DnvPlatformCapabilities.PlatformFamily.GROUND);
    DnvRouteFollower.Intent intent = follower.tick(sensor(
        100L, 0.0D, 240.0D, 0.0D, true, true, false, true,
        OptionalDouble.empty(), false));
    assertEquals(DnvRouteFollower.State.APPROACH, intent.state());
    assertEquals(0.0D, intent.climbCommand());
    assertTrue(intent.throttle() > 0.0D);
  }

  @Test
  void everyMediumHoldsAtAnUnloadedSegmentWithoutForcingIt() {
    for (DnvPlatformCapabilities.PlatformFamily family : List.of(
        DnvPlatformCapabilities.PlatformFamily.GROUND,
        DnvPlatformCapabilities.PlatformFamily.SURFACE,
        DnvPlatformCapabilities.PlatformFamily.AIR)) {
      DnvRouteFollower follower = configured(family);
      DnvRouteFollower.Intent intent = follower.tick(sensor(
          100L, 0.0D, 80.0D, 0.0D, false, true, true, true,
          OptionalDouble.of(60.0D), false));
      assertTrue(intent.hardHold());
      assertEquals(DnvRouteFollower.Reason.ROUTE_SEGMENT_UNLOADED, intent.reason());
    }
  }

  @Test
  void groundBlockageEscalatesWithABoundedReplanCooldown() {
    DnvRouteFollower follower = configured(DnvPlatformCapabilities.PlatformFamily.GROUND);
    DnvRouteFollower.Intent latest = null;
    for (int tick = 100; tick < 110; tick++) {
      latest = follower.tick(sensor(
          tick, 0.0D, 70.0D, 0.0D, true, false, false, true,
          OptionalDouble.empty(), false));
    }
    assertEquals(DnvRouteFollower.State.LOCAL_REPLAN, latest.state());
    assertTrue(latest.replanRequested());
    DnvRouteFollower.Intent next = follower.tick(sensor(
        110L, 0.0D, 70.0D, 0.0D, true, false, false, true,
        OptionalDouble.empty(), false));
    assertFalse(next.replanRequested());
  }

  @Test
  void surfaceAndAirFailClosedOnTheirOwnMissingSafetyFacts() {
    DnvRouteFollower surface = configured(DnvPlatformCapabilities.PlatformFamily.SURFACE);
    DnvRouteFollower.Intent dry = surface.tick(sensor(
        100L, 0.0D, 63.0D, 0.0D, true, true, false, false,
        OptionalDouble.empty(), false));
    assertEquals(DnvRouteFollower.Reason.NOT_ON_NAVIGABLE_WATER, dry.reason());

    DnvRouteFollower air = configured(DnvPlatformCapabilities.PlatformFamily.AIR);
    DnvRouteFollower.Intent unknownTerrain = air.tick(sensor(
        100L, 0.0D, 80.0D, 0.0D, true, true, true, true,
        OptionalDouble.empty(), false));
    assertEquals(DnvRouteFollower.Reason.TERRAIN_SAMPLE_UNAVAILABLE, unknownTerrain.reason());
    assertTrue(unknownTerrain.hardHold());
  }

  @Test
  void plansAreBoundedVersionedAndRejectMediumMismatches() {
    assertThrows(IllegalArgumentException.class, () -> new DnvRoutePlan(
        DnvRoutePlan.CURRENT_SCHEMA,
        "minecraft:overworld",
        1L,
        DnvRoutePlan.MovementMedium.GROUND,
        java.util.Collections.nCopies(33,
            new DnvRoutePlan.RoutePoint(0.0D, 0.0D, OptionalDouble.empty(),
                DnvRoutePlan.WaypointAction.PASS_THROUGH)),
        DnvRoutePlan.CompletionPolicy.HOLD,
        DnvRoutePlan.BlockedPolicy.HOLD,
        DnvRoutePlan.AltitudePolicy.none()));

    DnvRouteFollower follower = new DnvRouteFollower();
    DnvRouteFollower.RouteUpdate mismatch = follower.apply(
        plan(DnvRoutePlan.MovementMedium.AIR),
        routeCapability(DnvPlatformCapabilities.defaults(
            DnvPlatformCapabilities.PlatformFamily.GROUND)));
    assertEquals(DnvRouteFollower.Reason.MEDIUM_UNSUPPORTED, mismatch.reason());
  }

  @Test
  void airRouteWaitsForResolvedHeightBeforeCompletingHorizontalWaypoint() {
    DnvRouteFollower follower = new DnvRouteFollower();
    DnvRoutePlan plan = new DnvRoutePlan(
        DnvRoutePlan.CURRENT_SCHEMA,
        "minecraft:overworld",
        1L,
        DnvRoutePlan.MovementMedium.AIR,
        List.of(new DnvRoutePlan.RoutePoint(
            0.0D, 0.0D, OptionalDouble.of(96.0D),
            DnvRoutePlan.WaypointAction.HOLD)),
        DnvRoutePlan.CompletionPolicy.HOLD,
        DnvRoutePlan.BlockedPolicy.RECOVERY_REQUIRED,
        new DnvRoutePlan.AltitudePolicy(
            DnvRoutePlan.AltitudeReference.ABSOLUTE,
            96.0D, 8.0D, 128.0D));
    assertTrue(follower.apply(plan, new DnvRouteFollower.RouteCapability(
        true, java.util.Set.of(DnvRoutePlan.MovementMedium.AIR))).accepted());
    DnvRouteFollower.Intent climbing = follower.tick(sensor(
        100L, 0.0D, 70.0D, 0.0D, true, true, false, false,
        OptionalDouble.of(60.0D), false));
    assertEquals(DnvRouteFollower.State.APPROACH, climbing.state());
    assertTrue(climbing.climbCommand() > 0.0D);
    DnvRouteFollower.Intent arrived = follower.tick(sensor(
        101L, 0.0D, 95.0D, 0.0D, true, true, false, false,
        OptionalDouble.of(60.0D), false));
    assertEquals(DnvRouteFollower.State.COMPLETE, arrived.state());
  }

  private static DnvRouteFollower configured(DnvPlatformCapabilities.PlatformFamily family) {
    DnvRouteFollower follower = new DnvRouteFollower();
    DnvPlatformCapabilities capabilities = DnvPlatformCapabilities.defaults(family);
    DnvRoutePlan.MovementMedium medium = DnvRoutePlan.MovementMedium.valueOf(
        capabilities.movementMedia().iterator().next().name());
    assertTrue(follower.apply(plan(medium), routeCapability(capabilities)).accepted());
    return follower;
  }

  private static DnvRoutePlan plan(DnvRoutePlan.MovementMedium medium) {
    DnvRoutePlan.AltitudePolicy altitude = medium == DnvRoutePlan.MovementMedium.AIR
        ? new DnvRoutePlan.AltitudePolicy(
            DnvRoutePlan.AltitudeReference.ABOVE_TERRAIN, 50.0D, 20.0D, 120.0D)
        : DnvRoutePlan.AltitudePolicy.none();
    return new DnvRoutePlan(
        DnvRoutePlan.CURRENT_SCHEMA,
        "minecraft:overworld",
        1L,
        medium,
        List.of(new DnvRoutePlan.RoutePoint(
            100.0D, 0.0D, OptionalDouble.empty(), DnvRoutePlan.WaypointAction.HOLD)),
        DnvRoutePlan.CompletionPolicy.HOLD,
        DnvRoutePlan.BlockedPolicy.RECOVERY_REQUIRED,
        altitude);
  }

  private static DnvRouteFollower.RouteCapability routeCapability(
      DnvPlatformCapabilities capabilities) {
    return new DnvRouteFollower.RouteCapability(
        capabilities.movementActuator(),
        capabilities.movementMedia().stream()
            .map(value -> DnvRoutePlan.MovementMedium.valueOf(value.name()))
            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
  }

  private static DnvRouteFollower.SensorSnapshot sensor(
      long gameTime,
      double x,
      double y,
      double z,
      boolean routeSegmentLoaded,
      boolean localTraversable,
      boolean navigableWater,
      boolean afloat,
      OptionalDouble terrainHeight,
      boolean playerOverride) {
    return new DnvRouteFollower.SensorSnapshot(
        gameTime, x, y, z, -90.0D, 0.0D, routeSegmentLoaded, localTraversable,
        navigableWater, afloat, terrainHeight, 12.0D, playerOverride);
  }
}
