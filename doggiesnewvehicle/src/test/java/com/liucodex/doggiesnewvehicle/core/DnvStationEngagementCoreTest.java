package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvStationEngagementCoreTest {
  private static final UUID EAST = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID WEST = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID SOUTH = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @Test
  void readinessChangesInvalidateFireWithinTheDecisionIntervalAndRecover() {
    var core = new DnvStationEngagementCore();
    var targets = List.of(target(EAST, 10, 0, 0, 20));
    var base = station(-90, DnvStationEngagementCore.TargetingMode.SHARED_PREFERRED);
    assertEquals(DnvAutonomyCore.DomainCommand.FIRE,
        core.decide(request(20, Optional.empty(), targets, Map.of("left", base)))
            .stationIntents().get("left").command());
    var states = List.of(
        new DnvStationEngagementCore.WeaponReadiness(false, true, true),
        new DnvStationEngagementCore.WeaponReadiness(true, false, true),
        new DnvStationEngagementCore.WeaponReadiness(true, true, true, true, false),
        new DnvStationEngagementCore.WeaponReadiness(false, false, true, false, false),
        new DnvStationEngagementCore.WeaponReadiness(true, true, false),
        DnvStationEngagementCore.WeaponReadiness.ready());
    var reasons = List.of(DnvStationEngagementCore.Reason.OUT_OF_AMMO,
        DnvStationEngagementCore.Reason.COOLDOWN, DnvStationEngagementCore.Reason.WEAPON_NOT_READY,
        DnvStationEngagementCore.Reason.WEAPON_UNAVAILABLE, DnvStationEngagementCore.Reason.AUTHORITY_DENIED,
        DnvStationEngagementCore.Reason.TARGET_AUTHORIZED);
    for (int i = 0; i < states.size(); i++) {
      var state = new DnvStationEngagementCore.StationSnapshot(base.owner(), true,
          base.geometry(), base.policy(), states.get(i));
      var result = core.decide(request(21 + i, Optional.empty(), targets, Map.of("left", state)));
      assertFalse(result.cached());
      assertEquals(reasons.get(i), result.stationIntents().get("left").reason());
    }
  }

  @Test
  void eachStationRequiresItsOwnCurrentMuzzleObservation() {
    var core = new DnvStationEngagementCore();
    var base = station(-90, DnvStationEngagementCore.TargetingMode.SHARED_PREFERRED);
    var unknown = new DnvStationEngagementCore.TargetSnapshot(EAST,
        DnvStationEngagementCore.TargetKind.LIVING, 10, 0, 0, 20, true, true, false, true);
    var observed = new DnvStationEngagementCore.StationSnapshot(base.owner(), true,
        base.geometry(), base.policy(), base.readiness(), Map.of(EAST, true));
    var decision = core.decide(request(20, Optional.empty(), List.of(unknown),
        Map.of("left", observed, "other", base)));
    assertEquals(DnvAutonomyCore.DomainCommand.FIRE, decision.stationIntents().get("left").command());
    assertEquals(DnvStationEngagementCore.Reason.FIRING_LINE_BLOCKED,
        decision.stationIntents().get("other").reason());
    var expired = core.decide(request(21, Optional.empty(), List.of(unknown), Map.of("left", base)));
    assertFalse(expired.cached());
    assertEquals(DnvAutonomyCore.DomainCommand.TRACK, expired.stationIntents().get("left").command());
  }

  @Test
  void narrowerPolicyWithSameModeInvalidatesCachedTargetImmediately() {
    var core = new DnvStationEngagementCore();
    var base = station(-90, DnvStationEngagementCore.TargetingMode.SHARED_PREFERRED);
    var targets = List.of(target(EAST, 10, 0, 0, 20));
    core.decide(request(20, Optional.of(EAST), targets, Map.of("left", base)));
    var narrower = new DnvStationEngagementCore.StationEngagementPolicy(
        base.policy().targetingMode(), base.policy().allowedTargetKinds(), 0.1, 5,
        0, 0, 0);
    var changed = new DnvStationEngagementCore.StationSnapshot(base.owner(), true,
        base.geometry(), narrower, base.readiness());
    var result = core.decide(request(21, Optional.of(EAST), targets, Map.of("left", changed)));
    assertFalse(result.cached());
    assertFalse(result.stationIntents().get("left").command() == DnvAutonomyCore.DomainCommand.FIRE);
  }

  @Test
  void cachedFireStillNeedsTheCurrentStationEnvelopeAtExecution() {
    var core = new DnvStationEngagementCore();
    var left = station(-90.0F, DnvStationEngagementCore.TargetingMode.SHARED_PREFERRED);
    var stations = Map.of("left", left);
    assertEquals(DnvAutonomyCore.DomainCommand.FIRE,
        core.decide(request(20, Optional.empty(), List.of(target(EAST, 10, 0, 0, 20)),
            stations)).stationIntents().get("left").command());
    var moved = core.decide(request(21, Optional.empty(),
        List.of(target(EAST, 0, 0, 10, 20)), stations));
    assertTrue(moved.cached(), "position alone does not invalidate the scheduled allocation");
    assertEquals(DnvAutonomyCore.DomainCommand.FIRE,
        moved.stationIntents().get("left").command());

    assertTrue(DnvStationEngagementCore.withinExecutionEnvelope(
        0, 0, 0, 0, left.geometry(), left.policy(), 10, 0, 0));
    assertFalse(DnvStationEngagementCore.withinExecutionEnvelope(
        0, 0, 0, 0, left.geometry(), left.policy(), 0, 0, 10));
    assertFalse(DnvStationEngagementCore.withinExecutionEnvelope(
        0, 0, 0, 90, left.geometry(), left.policy(), 10, 0, 0));
    assertFalse(DnvStationEngagementCore.withinExecutionEnvelope(
        0, 0, 0, 0, left.geometry(), left.policy(), 10, 20, 0));
    assertTrue(DnvStationEngagementCore.withinExecutionEnvelope(
        0, 0, 0, 0, left.geometry(), left.policy(), 128, 0, 0));
    assertFalse(DnvStationEngagementCore.withinExecutionEnvelope(
        0, 0, 0, 0, left.geometry(), left.policy(), 128.01, 0, 0));
    var minimumFive = new DnvStationEngagementCore.StationEngagementPolicy(
        left.policy().targetingMode(), left.policy().allowedTargetKinds(), 5, 128, 25, 20, 40);
    assertFalse(DnvStationEngagementCore.withinExecutionEnvelope(
        0, 0, 0, 0, left.geometry(), minimumFive, 4.99, 0, 0));
    assertTrue(DnvStationEngagementCore.withinExecutionEnvelope(
        0, 0, 0, 0, left.geometry(), minimumFive, 5, 0, 0));
    assertFalse(DnvStationEngagementCore.withinExecutionEnvelope(
        0, 0, 0, 0, left.geometry(), left.policy(), Double.NaN, 0, 0));
  }

  @Test
  void oneSharedCandidateFrameProducesIndependentStationTargets() {
    DnvStationEngagementCore core = new DnvStationEngagementCore();
    DnvStationEngagementCore.Decision decision = core.decide(request(
        20L,
        Optional.empty(),
        List.of(target(EAST, 10.0D, 0.0D, 0.0D, 30.0D),
            target(WEST, -10.0D, 0.0D, 0.0D, 40.0D)),
        Map.of(
            "left", station(-90.0F, DnvStationEngagementCore.TargetingMode.INDEPENDENT),
            "right", station(90.0F, DnvStationEngagementCore.TargetingMode.INDEPENDENT))));

    assertEquals(EAST, decision.stationIntents().get("left").targetId().orElseThrow());
    assertEquals(WEST, decision.stationIntents().get("right").targetId().orElseThrow());
    assertEquals(DnvAutonomyCore.DomainCommand.FIRE,
        decision.stationIntents().get("left").command());
    assertEquals(2, decision.candidateCount());
  }

  @Test
  void sixStationsDistributeTwoTargetsButCanConcentrateOnOneOrADesignation() {
    DnvStationEngagementCore core = new DnvStationEngagementCore();
    Map<String, DnvStationEngagementCore.StationSnapshot> stations = new java.util.LinkedHashMap<>();
    for (int index = 0; index < 6; index++) stations.put("station" + index, wideStation());
    var east = target(EAST, 10, 0, 0, 30);
    var west = target(WEST, -10, 0, 0, 29);
    var first = core.decide(request(20, Optional.empty(), List.of(east, west), stations));
    assertEquals(3, first.stationIntents().values().stream()
        .filter(intent -> intent.targetId().equals(Optional.of(EAST))).count());
    assertEquals(3, first.stationIntents().values().stream()
        .filter(intent -> intent.targetId().equals(Optional.of(WEST))).count());

    var retained = core.decide(request(35, Optional.empty(), List.of(west, east), stations));
    assertEquals(first.stationIntents(), retained.stationIntents(),
        "a new candidate order must not reshuffle valid station assignments");

    var designated = core.decide(request(36, Optional.of(WEST), List.of(east, west), stations));
    assertTrue(designated.stationIntents().values().stream()
        .allMatch(intent -> intent.targetId().equals(Optional.of(WEST))));

    core.clear(37);
    var oneTarget = core.decide(request(37, Optional.empty(), List.of(east), stations));
    assertTrue(oneTarget.stationIntents().values().stream()
        .allMatch(intent -> intent.targetId().equals(Optional.of(EAST))));
  }

  @Test
  void unloadedTargetImmediatelyLosesItsReservationAndNeverGetsAFireIntent() {
    DnvStationEngagementCore core = new DnvStationEngagementCore();
    Map<String, DnvStationEngagementCore.StationSnapshot> stations = Map.of(
        "left", wideStation(), "right", wideStation());
    var east = target(EAST, 10, 0, 0, 30);
    var west = target(WEST, -10, 0, 0, 29);
    core.decide(request(20, Optional.empty(), List.of(east, west), stations));
    var unloaded = new DnvStationEngagementCore.TargetSnapshot(EAST,
        DnvStationEngagementCore.TargetKind.LIVING,
        10, 0, 0, 30, false, true, true, true);
    var changed = core.decide(request(21, Optional.empty(), List.of(unloaded, west), stations));
    assertFalse(changed.cached());
    assertTrue(changed.stationIntents().values().stream()
        .allMatch(intent -> intent.targetId().equals(Optional.of(WEST))));
  }

  @Test
  void designatedTargetCannotBypassAStationEnvelope() {
    DnvStationEngagementCore core = new DnvStationEngagementCore();
    DnvStationEngagementCore.Decision decision = core.decide(request(
        20L,
        Optional.of(SOUTH),
        List.of(target(EAST, 10.0D, 0.0D, 0.0D, 10.0D),
            target(SOUTH, 0.0D, 0.0D, 10.0D, 100.0D)),
        Map.of("left", station(
            -90.0F, DnvStationEngagementCore.TargetingMode.DESIGNATED_ONLY))));

    DnvStationEngagementCore.StationIntent intent = decision.stationIntents().get("left");
    assertEquals(Optional.empty(), intent.targetId());
    assertEquals(DnvAutonomyCore.DomainCommand.HOLD, intent.command());
    assertEquals(DnvStationEngagementCore.Reason.DESIGNATED_TARGET_OUTSIDE_ENVELOPE,
        intent.reason());
  }

  @Test
  void playerOnlyOwnsTheOccupiedStationDomain() {
    DnvStationEngagementCore.StationSnapshot player = new DnvStationEngagementCore.StationSnapshot(
        DnvAutonomyCore.DomainOwner.PLAYER,
        true,
        geometry(-90.0F),
        policy(DnvStationEngagementCore.TargetingMode.INDEPENDENT),
        DnvStationEngagementCore.WeaponReadiness.ready());
    DnvStationEngagementCore core = new DnvStationEngagementCore();
    DnvStationEngagementCore.Decision decision = core.decide(request(
        20L,
        Optional.empty(),
        List.of(target(EAST, 10.0D, 0.0D, 0.0D, 20.0D),
            target(WEST, -10.0D, 0.0D, 0.0D, 20.0D)),
        Map.of("left", player,
            "right", station(90.0F, DnvStationEngagementCore.TargetingMode.INDEPENDENT))));

    assertEquals(DnvStationEngagementCore.Reason.PLAYER_DOMAIN_OVERRIDE,
        decision.stationIntents().get("left").reason());
    assertEquals(DnvAutonomyCore.DomainCommand.FIRE,
        decision.stationIntents().get("right").command());
  }

  @Test
  void remoteLeaseStopsOnlyItsStationAiDomain() {
    DnvStationEngagementCore.StationSnapshot remote = new DnvStationEngagementCore.StationSnapshot(
        DnvAutonomyCore.DomainOwner.REMOTE_MANUAL_FIRE,
        true,
        geometry(-90.0F),
        policy(DnvStationEngagementCore.TargetingMode.INDEPENDENT),
        DnvStationEngagementCore.WeaponReadiness.ready());
    DnvStationEngagementCore.Decision decision = new DnvStationEngagementCore().decide(request(
        20L,
        Optional.empty(),
        List.of(target(EAST, 10.0D, 0.0D, 0.0D, 20.0D),
            target(WEST, -10.0D, 0.0D, 0.0D, 20.0D)),
        Map.of("left", remote,
            "right", station(90.0F, DnvStationEngagementCore.TargetingMode.INDEPENDENT))));

    assertEquals(DnvStationEngagementCore.Reason.DOMAIN_NOT_AUTHORIZED,
        decision.stationIntents().get("left").reason());
    assertEquals(DnvAutonomyCore.DomainCommand.FIRE,
        decision.stationIntents().get("right").command());
  }

  @Test
  void readinessAndLineOfSightProduceStableHoldReasons() {
    DnvStationEngagementCore.StationSnapshot noAmmo = new DnvStationEngagementCore.StationSnapshot(
        DnvAutonomyCore.DomainOwner.AI,
        true,
        geometry(-90.0F),
        policy(DnvStationEngagementCore.TargetingMode.INDEPENDENT),
        new DnvStationEngagementCore.WeaponReadiness(false, true, true));
    DnvStationEngagementCore core = new DnvStationEngagementCore();
    DnvStationEngagementCore.Decision decision = core.decide(request(
        20L, Optional.empty(),
        List.of(target(EAST, 10.0D, 0.0D, 0.0D, 20.0D)),
        Map.of("left", noAmmo)));

    assertEquals(DnvAutonomyCore.DomainCommand.TRACK,
        decision.stationIntents().get("left").command());
    assertEquals(DnvStationEngagementCore.Reason.OUT_OF_AMMO,
        decision.stationIntents().get("left").reason());
  }

  @Test
  void noPvpCategoriesStayDeniedEvenWhenExplicitlyDesignated() {
    DnvStationEngagementCore.TargetSnapshot player = new DnvStationEngagementCore.TargetSnapshot(
        EAST,
        DnvStationEngagementCore.TargetKind.PLAYER,
        10.0D, 0.0D, 0.0D,
        100.0D,
        true, true, true, false);
    DnvStationEngagementCore core = new DnvStationEngagementCore();
    DnvStationEngagementCore.Decision decision = core.decide(request(
        20L, Optional.of(EAST), List.of(player),
        Map.of("left", station(-90.0F,
            DnvStationEngagementCore.TargetingMode.DESIGNATED_ONLY))));

    assertEquals(DnvStationEngagementCore.Reason.PVP_TARGET_DENIED,
        decision.stationIntents().get("left").reason());
    assertFalse(decision.stationIntents().get("left").targetId().isPresent());
  }

  @Test
  void wrappedYawArcsAndForbiddenSectorsUseNormalizedLocalAngles() {
    DnvStationEngagementCore.YawArc wrapped = new DnvStationEngagementCore.YawArc(170.0F, -170.0F);
    assertTrue(wrapped.contains(179.0F));
    assertTrue(wrapped.contains(-179.0F));
    assertFalse(wrapped.contains(0.0F));

    DnvStationEngagementCore.StationGeometryProfile geometry =
        new DnvStationEngagementCore.StationGeometryProfile(
            "dnv:test", 1L, 0.0D, 0.0D, 0.0D, -90.0F,
            List.of(new DnvStationEngagementCore.YawArc(-30.0F, 30.0F)),
            new DnvStationEngagementCore.PitchArc(-45.0F, 45.0F),
            List.of(new DnvStationEngagementCore.YawArc(-2.0F, 2.0F)), false);
    assertFalse(geometry.contains(0.0F, 0.0F));
    assertTrue(geometry.contains(10.0F, 0.0F));
  }

  @Test
  void decisionBudgetIsDeterministicAndSafetyChangesCanInvalidateIt() {
    DnvStationEngagementCore core = new DnvStationEngagementCore();
    DnvStationEngagementCore.Request first = request(
        20L, Optional.empty(), List.of(target(EAST, 10.0D, 0.0D, 0.0D, 20.0D)),
        Map.of("left", station(-90.0F, DnvStationEngagementCore.TargetingMode.INDEPENDENT)));
    DnvStationEngagementCore.Decision accepted = core.decide(first);
    DnvStationEngagementCore.Decision cached = core.decide(request(
        21L, Optional.empty(), List.of(target(EAST, 11.0D, 0.0D, 0.0D, 999.0D)),
        first.stations()));
    assertEquals(accepted.stationIntents(), cached.stationIntents());
    assertTrue(cached.cached());

    Map<String, DnvStationEngagementCore.StationSnapshot> playerTookOver = Map.of(
        "left", new DnvStationEngagementCore.StationSnapshot(
            DnvAutonomyCore.DomainOwner.PLAYER, true, geometry(-90.0F),
            policy(DnvStationEngagementCore.TargetingMode.INDEPENDENT),
            DnvStationEngagementCore.WeaponReadiness.ready()));
    DnvStationEngagementCore.Decision invalidated = core.decide(request(
        21L, Optional.empty(), first.candidates(), playerTookOver));
    assertFalse(invalidated.cached());
    assertEquals(DnvStationEngagementCore.Reason.PLAYER_DOMAIN_OVERRIDE,
        invalidated.stationIntents().get("left").reason());
  }

  @Test
  void lostEligibilityAndUnloadedTargetsCannotReuseCachedFire() {
    for (boolean loaded : List.of(true, false)) {
      var core = new DnvStationEngagementCore();
      var stations = Map.of("left", station(-90, DnvStationEngagementCore.TargetingMode.SHARED_PREFERRED));
      core.decide(request(20, Optional.empty(), List.of(target(EAST, 10, 0, 0, 20)), stations));
      var revoked = new DnvStationEngagementCore.TargetSnapshot(EAST,
          DnvStationEngagementCore.TargetKind.LIVING, 10, 0, 0, 20, loaded, true, true, !loaded);
      var result = core.decide(request(21, Optional.empty(), List.of(revoked), stations));
      assertFalse(result.cached());
      assertFalse(result.stationIntents().get("left").command() == DnvAutonomyCore.DomainCommand.FIRE);
    }
  }

  @Test
  void hardBudgetsRejectOversizedFrames() {
    assertThrows(IllegalArgumentException.class, () -> new DnvStationEngagementCore.Request(
        0L, 1, 0.0D, 0.0D, 0.0D, 0.0F, Optional.empty(),
        java.util.stream.IntStream.rangeClosed(0, DnvStationEngagementCore.MAX_CANDIDATES)
            .mapToObj(index -> target(new UUID(0L, index + 10L), 10.0D, 0.0D, 0.0D, 1.0D))
            .toList(),
        Map.of()));
  }

  @Test
  void acceptedOrderOverridesCachedTargetButNotManualOwnershipAndCancelReleasesIt() {
    UUID owner = UUID.randomUUID();
    UUID platform = UUID.randomUUID();
    var binding = new DnvEntityDesignationSession.Binding(
        platform, UUID.randomUUID(), 1, owner, "minecraft:overworld");
    var orders = new DnvEntityDesignationSession(platform);
    orders.synchronize(Optional.of(binding));
    var validation = new DnvCombatOrderService.ValidationContext(
        100, owner, binding.dimension(), true, 10, true, true, DnvCombatOrderService.TargetKind.LIVING);
    UUID operation = UUID.randomUUID();
    var command = new DnvCombatOrderService.Command(
        operation, operation, owner, platform, orders.view(100).orders().revision(), 100, 300,
        binding.dimension(), DnvCombatOrderService.OrderType.DESIGNATE_ENTITY, Optional.of(SOUTH));
    var core = new DnvStationEngagementCore();
    var candidates = List.of(target(EAST, 10, 0, 0, 100), target(SOUTH, 20, 0, 0, 1));
    var ai = station(-90, DnvStationEngagementCore.TargetingMode.SHARED_PREFERRED);
    assertEquals(Optional.of(EAST), core.decide(request(100, Optional.empty(), candidates,
        Map.of("left", ai))).stationIntents().get("left").targetId());
    var accepted = orders.submit(binding, command, validation);
    assertTrue(accepted.accepted());
    var designated = core.decide(request(101, accepted.view().targetId(), candidates, Map.of("left", ai)));
    assertFalse(designated.cached());
    assertEquals(Optional.of(SOUTH), designated.stationIntents().get("left").targetId());
    var player = new DnvStationEngagementCore.StationSnapshot(
        DnvAutonomyCore.DomainOwner.PLAYER, true, ai.geometry(), ai.policy(), ai.readiness());
    assertEquals(DnvStationEngagementCore.Reason.PLAYER_DOMAIN_OVERRIDE,
        core.decide(request(102, accepted.view().targetId(), candidates, Map.of("left", player)))
            .stationIntents().get("left").reason());
    operation = UUID.randomUUID();
    var cancel = new DnvCombatOrderService.Command(
        operation, operation, owner, platform, accepted.view().orders().revision(), 100, 300,
        binding.dimension(), DnvCombatOrderService.OrderType.CANCEL_ORDER, Optional.empty());
    var cancelled = orders.submit(binding, cancel, validation);
    assertTrue(cancelled.accepted());
    assertTrue(cancelled.view().targetId().isEmpty());
    core.clear(103); // The actuator owner releases target memory when the order revision changes.
    assertEquals(Optional.of(EAST), core.decide(request(103, cancelled.view().targetId(), candidates,
        Map.of("left", ai))).stationIntents().get("left").targetId());
  }

  private static DnvStationEngagementCore.Request request(
      long gameTime,
      Optional<UUID> designated,
      List<DnvStationEngagementCore.TargetSnapshot> targets,
      Map<String, DnvStationEngagementCore.StationSnapshot> stations) {
    return new DnvStationEngagementCore.Request(
        gameTime, 7, 0.0D, 0.0D, 0.0D, 0.0F, designated, targets, stations);
  }

  private static DnvStationEngagementCore.TargetSnapshot target(
      UUID id, double x, double y, double z, double threat) {
    return new DnvStationEngagementCore.TargetSnapshot(
        id, DnvStationEngagementCore.TargetKind.LIVING,
        x, y, z, threat, true, true, true, true);
  }

  private static DnvStationEngagementCore.StationSnapshot station(
      float baseYaw, DnvStationEngagementCore.TargetingMode mode) {
    return new DnvStationEngagementCore.StationSnapshot(
        DnvAutonomyCore.DomainOwner.AI,
        true,
        geometry(baseYaw),
        policy(mode),
        DnvStationEngagementCore.WeaponReadiness.ready());
  }

  private static DnvStationEngagementCore.StationSnapshot wideStation() {
    return new DnvStationEngagementCore.StationSnapshot(
        DnvAutonomyCore.DomainOwner.AI, true,
        new DnvStationEngagementCore.StationGeometryProfile(
            "dnv:wide", 1L, 0, 0, 0, 0,
            List.of(new DnvStationEngagementCore.YawArc(-180, 180)),
            new DnvStationEngagementCore.PitchArc(-45, 45), List.of(), false),
        policy(DnvStationEngagementCore.TargetingMode.SHARED_PREFERRED),
        DnvStationEngagementCore.WeaponReadiness.ready());
  }

  private static DnvStationEngagementCore.StationGeometryProfile geometry(float baseYaw) {
    return new DnvStationEngagementCore.StationGeometryProfile(
        "dnv:test", 1L, 0.0D, 0.0D, 0.0D, baseYaw,
        List.of(new DnvStationEngagementCore.YawArc(-20.0F, 20.0F)),
        new DnvStationEngagementCore.PitchArc(-45.0F, 45.0F), List.of(), false);
  }

  private static DnvStationEngagementCore.StationEngagementPolicy policy(
      DnvStationEngagementCore.TargetingMode mode) {
    return new DnvStationEngagementCore.StationEngagementPolicy(
        mode,
        Set.of(DnvStationEngagementCore.TargetKind.LIVING,
            DnvStationEngagementCore.TargetKind.VEHICLE,
            DnvStationEngagementCore.TargetKind.PROJECTILE),
        0.0D, 128.0D, 25.0D, 20, 40);
  }
}
