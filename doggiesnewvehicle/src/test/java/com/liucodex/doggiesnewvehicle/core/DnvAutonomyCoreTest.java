package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvAutonomyCoreTest {
  private static final float EPSILON = 0.0001F;
  private static final UUID TARGET_A =
      UUID.fromString("a1000000-0000-0000-0000-000000000001");
  private static final UUID TARGET_B =
      UUID.fromString("b2000000-0000-0000-0000-000000000002");

  private final DnvAutonomyCore core = new DnvAutonomyCore(30.0D, 40.0D);

  @Test
  void preservesGroundBossPressureBandAndPatrolThrottle() {
    assertIntent(snapshot(true, true, false, 41.0D, 0.0F, 0.0F),
        DnvAutonomyCore.TacticalState.APPROACHING,
        DnvAutonomyCore.DecisionReason.TARGET_TOO_FAR,
        1.0F,
        0.0F);
    assertIntent(snapshot(true, true, false, 29.0D, 0.0F, 0.0F),
        DnvAutonomyCore.TacticalState.RETREATING,
        DnvAutonomyCore.DecisionReason.TARGET_TOO_CLOSE,
        -0.65F,
        0.0F);
    assertIntent(snapshot(true, true, false, 35.0D, 0.0F, 0.0F),
        DnvAutonomyCore.TacticalState.FIRE_HOLD,
        DnvAutonomyCore.DecisionReason.TARGET_IN_PRESSURE_BAND,
        0.0F,
        0.0F);
    assertIntent(snapshot(false, true, false, 0.0D, 0.0F, 0.0F),
        DnvAutonomyCore.TacticalState.PATROLLING,
        DnvAutonomyCore.DecisionReason.PATROL_DESTINATION,
        0.58F,
        0.0F);
  }

  @Test
  void obstructionRepositionAndTurnInPlaceRemainDeterministic() {
    DnvAutonomyCore.ActionIntent reposition =
        core.decideMovement(snapshot(true, true, true, 35.0D, 10.0F, 28.0F));
    assertEquals(DnvAutonomyCore.TacticalState.REPOSITIONING, reposition.state());
    assertEquals(DnvAutonomyCore.DecisionReason.FIRING_LINE_BLOCKED, reposition.reason());
    assertEquals(0.52F, reposition.throttle(), EPSILON);
    assertEquals(1.0F, reposition.steering(), EPSILON);

    DnvAutonomyCore.ActionIntent largeTurn =
        core.decideMovement(snapshot(true, true, true, 35.0D, 10.0F, 43.0F));
    assertEquals(0.0F, largeTurn.throttle(), EPSILON);
    assertEquals(1.0F, largeTurn.steering(), EPSILON);
    assertEquals(largeTurn, core.decideMovement(snapshot(true, true, true, 35.0D, 10.0F, 43.0F)));
    assertEquals(largeTurn, core.lastMovementIntent());
  }

  @Test
  void holdOrderOwnsHardStopWhileTransitionOnlySuppressesNewInput() {
    DnvAutonomyCore.ActionIntent hold =
        core.decideMovement(new DnvAutonomyCore.SensorSnapshot(
            true, false, true, true, false, 50.0D, 0.0F, 0.0F));
    assertEquals(DnvAutonomyCore.DecisionReason.HOLD_ORDER, hold.reason());
    assertTrue(hold.clearRoute());
    assertTrue(hold.hardStop());

    DnvAutonomyCore.ActionIntent transition =
        core.decideMovement(new DnvAutonomyCore.SensorSnapshot(
            false, true, true, true, false, 50.0D, 0.0F, 0.0F));
    assertEquals(DnvAutonomyCore.DecisionReason.PLATFORM_TRANSITION, transition.reason());
    assertFalse(transition.clearRoute());
    assertFalse(transition.hardStop());
  }

  @Test
  void invalidGeometryFailsClosedAndYawUsesShortestTurn() {
    DnvAutonomyCore.ActionIntent invalid =
        core.decideMovement(snapshot(true, true, false, Double.NaN, 0.0F, 0.0F));
    assertEquals(DnvAutonomyCore.DecisionReason.INVALID_SENSOR_GEOMETRY, invalid.reason());
    assertEquals(0.0F, invalid.throttle(), EPSILON);
    assertEquals(0.0F, invalid.steering(), EPSILON);

    DnvAutonomyCore.ActionIntent wrapped =
        core.decideMovement(snapshot(false, true, false, 0.0D, 179.0F, -179.0F));
    assertEquals(2.0F / 18.0F, wrapped.steering(), EPSILON);
  }

  @Test
  void rejectsInvalidPressureEnvelope() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> new DnvAutonomyCore(40.0D, 30.0D));
  }

  @Test
  void deterministicCycleRetainsTargetUntilThreatSwitchThreshold() {
    DnvAutonomyCore.DecisionCycle acquired = core.decide(perception(
        100L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(target(TARGET_A, 35.0D, 0.0F, 60.0D, true)),
        aiStations()));
    assertEquals(Optional.of(TARGET_A), acquired.targetId());
    assertTrue(acquired.targetVisible());

    DnvAutonomyCore.DecisionCycle retained = core.decide(perception(
        101L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(
            target(TARGET_A, 35.0D, 0.0F, 60.0D, true),
            target(TARGET_B, 34.0D, 4.0F, 74.0D, true)),
        aiStations()));
    assertEquals(Optional.of(TARGET_A), retained.targetId());

    DnvAutonomyCore.DecisionCycle switched = core.decide(perception(
        125L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(
            target(TARGET_A, 35.0D, 0.0F, 60.0D, true),
            target(TARGET_B, 34.0D, 4.0F, 90.0D, true)),
        aiStations()));
    assertEquals(Optional.of(TARGET_B), switched.targetId());

    DnvAutonomyCore replay = new DnvAutonomyCore(30.0D, 40.0D);
    replay.decide(perception(
        100L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(target(TARGET_A, 35.0D, 0.0F, 60.0D, true)),
        aiStations()));
    replay.decide(perception(
        101L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(
            target(TARGET_A, 35.0D, 0.0F, 60.0D, true),
            target(TARGET_B, 34.0D, 4.0F, 74.0D, true)),
        aiStations()));
    DnvAutonomyCore.DecisionCycle replayed = replay.decide(perception(
        125L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(
            target(TARGET_A, 35.0D, 0.0F, 60.0D, true),
            target(TARGET_B, 34.0D, 4.0F, 90.0D, true)),
        aiStations()));
    assertEquals(switched.targetId(), replayed.targetId());
    assertEquals(switched.movement(), replayed.movement());
    assertEquals(switched.stationIntents(), replayed.stationIntents());
  }

  @Test
  void shortTargetLossUsesMemoryWithoutBlindFireThenExpires() {
    core.decide(perception(
        200L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(target(TARGET_A, 35.0D, 0.0F, 50.0D, true)),
        aiStations()));

    DnvAutonomyCore.DecisionCycle searching = core.decide(perception(
        220L, DnvAutonomyCore.DomainOwner.AI, List.of(), aiStations()));
    assertEquals(Optional.of(TARGET_A), searching.targetId());
    assertFalse(searching.targetVisible());
    assertEquals(DnvAutonomyCore.TacticalState.SEARCHING, searching.movement().state());
    assertTrue(searching.stationIntents().values().stream()
        .noneMatch(intent -> intent.command() == DnvAutonomyCore.DomainCommand.FIRE));

    DnvAutonomyCore.DecisionCycle expired = core.decide(perception(
        241L, DnvAutonomyCore.DomainOwner.AI, List.of(), aiStations()));
    assertEquals(Optional.empty(), expired.targetId());
    assertEquals(DnvAutonomyCore.TacticalState.IDLE, expired.movement().state());
  }

  @Test
  void playerOwnsOnlyOccupiedDomainWhileOtherStationKeepsFiring() {
    Map<String, DnvAutonomyCore.DomainStatus> stations = Map.of(
        "seat_0", domain(DnvAutonomyCore.DomainOwner.PLAYER, true, true),
        "seat_1", domain(DnvAutonomyCore.DomainOwner.AI, true, true));
    DnvAutonomyCore.DecisionCycle decision = core.decide(perception(
        300L,
        DnvAutonomyCore.DomainOwner.PLAYER,
        List.of(target(TARGET_A, 35.0D, 0.0F, 80.0D, true)),
        stations));

    assertEquals(
        DnvAutonomyCore.DecisionReason.PLAYER_DOMAIN_OVERRIDE,
        decision.movement().reason());
    assertEquals(
        DnvAutonomyCore.DomainCommand.HOLD,
        decision.stationIntents().get("seat_0").command());
    assertEquals(
        DnvAutonomyCore.DomainCommand.FIRE,
        decision.stationIntents().get("seat_1").command());
  }

  @Test
  void requestedShotFeedbackDoesNotClaimSuccessOrClearTargetMemory() {
    core.decide(perception(400L, DnvAutonomyCore.DomainOwner.AI,
        List.of(target(TARGET_A, 35.0D, 0.0F, 70.0D, true)), aiStations()));
    assertTrue(core.acceptFeedback(new DnvAutonomyCore.ExecutionFeedback(401L, "seat_1",
        Optional.of(TARGET_A), DnvAutonomyCore.ExecutionOutcome.REQUESTED, "weapon_fire_requested")));
    var restored = core.memorySnapshot();
    assertEquals(Optional.of(TARGET_A), restored.targetId());
    assertEquals(DnvAutonomyCore.ExecutionOutcome.REQUESTED, restored.lastFeedback().orElseThrow().outcome());
    assertEquals(0, restored.blockedUntilGameTime());
  }

  @Test
  void executionFeedbackChangesNextDecisionAndInvalidTargetClearsMemory() {
    core.decide(perception(
        400L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(target(TARGET_A, 35.0D, 0.0F, 70.0D, true)),
        aiStations()));
    core.acceptFeedback(new DnvAutonomyCore.ExecutionFeedback(
        401L,
        "movement",
        Optional.of(TARGET_A),
        DnvAutonomyCore.ExecutionOutcome.BLOCKED,
        "obstacle_ahead"));

    DnvAutonomyCore.DecisionCycle reposition = core.decide(perception(
        402L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(target(TARGET_A, 35.0D, 0.0F, 70.0D, true)),
        aiStations()));
    assertEquals(DnvAutonomyCore.TacticalState.REPOSITIONING, reposition.movement().state());
    assertEquals(
        DnvAutonomyCore.DecisionReason.EXECUTION_BLOCKED,
        reposition.movement().reason());

    core.acceptFeedback(new DnvAutonomyCore.ExecutionFeedback(
        403L,
        "station:seat_1:Cannon40",
        Optional.of(TARGET_A),
        DnvAutonomyCore.ExecutionOutcome.TARGET_INVALID,
        "target_removed"));
    DnvAutonomyCore.DecisionCycle cleared = core.decide(perception(
        404L, DnvAutonomyCore.DomainOwner.AI, List.of(), aiStations()));
    assertEquals(Optional.empty(), cleared.targetId());
    assertTrue(cleared.stationIntents().values().stream()
        .noneMatch(intent -> intent.command() == DnvAutonomyCore.DomainCommand.FIRE));
  }

  @Test
  void candidateBudgetAndTimeRegressionFailClosed() {
    List<DnvAutonomyCore.TargetObservation> tooMany = new ArrayList<>();
    for (int index = 0; index <= DnvAutonomyCore.MAX_TARGET_CANDIDATES; index++) {
      tooMany.add(target(new UUID(0L, index + 1L), 35.0D, 0.0F, index, true));
    }
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> perception(500L, DnvAutonomyCore.DomainOwner.AI, tooMany, aiStations()));

    core.decide(perception(
        500L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(target(TARGET_A, 35.0D, 0.0F, 70.0D, true)),
        aiStations()));
    DnvAutonomyCore.DecisionCycle regressed = core.decide(perception(
        499L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(target(TARGET_A, 35.0D, 0.0F, 70.0D, true)),
        aiStations()));
    assertEquals(Optional.empty(), regressed.targetId());
    assertEquals(DnvAutonomyCore.DecisionReason.TIME_REGRESSION, regressed.movement().reason());
  }

  @Test
  void restartRestoreKeepsOnlyLastKnownIdentityUntilLivePerceptionRevalidates() {
    DnvAutonomyCore.DecisionCycle acquired = core.decide(perception(
        600L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(target(TARGET_A, 35.0D, 0.0F, 70.0D, true)),
        aiStations()));
    DnvAutonomyCore restored = new DnvAutonomyCore(30.0D, 40.0D);
    assertTrue(restored.restore(acquired.memory(), 610L).accepted());

    DnvAutonomyCore.DecisionCycle waiting = restored.decide(perception(
        611L, DnvAutonomyCore.DomainOwner.AI, List.of(), aiStations()));
    assertEquals(Optional.of(TARGET_A), waiting.targetId());
    assertFalse(waiting.targetVisible());
    assertTrue(waiting.stationIntents().values().stream()
        .noneMatch(intent -> intent.command() == DnvAutonomyCore.DomainCommand.FIRE));

    DnvAutonomyCore future = new DnvAutonomyCore(30.0D, 40.0D);
    assertFalse(future.restore(acquired.memory(), 599L).accepted());
    assertEquals(Optional.empty(), future.memorySnapshot().targetId());
  }

  @Test
  void unloadOrCoreRemovalClearsTargetAndAuthorityMemory() {
    core.decide(perception(
        700L,
        DnvAutonomyCore.DomainOwner.AI,
        List.of(target(TARGET_A, 35.0D, 0.0F, 70.0D, true)),
        aiStations()));

    core.clear(710L);

    assertEquals(Optional.empty(), core.memorySnapshot().targetId());
    assertTrue(core.lastDecision().stationIntents().isEmpty());
    assertEquals(710L, core.memorySnapshot().lastGameTime());
  }

  private static DnvAutonomyCore.SensorSnapshot snapshot(
      boolean targetPresent,
      boolean destinationPresent,
      boolean reposition,
      double distance,
      float currentYaw,
      float destinationYaw) {
    return new DnvAutonomyCore.SensorSnapshot(
        false,
        false,
        targetPresent,
        destinationPresent,
        reposition,
        distance,
        currentYaw,
        destinationYaw);
  }

  private void assertIntent(
      DnvAutonomyCore.SensorSnapshot snapshot,
      DnvAutonomyCore.TacticalState state,
      DnvAutonomyCore.DecisionReason reason,
      float throttle,
      float steering) {
    DnvAutonomyCore.ActionIntent intent = core.decideMovement(snapshot);
    assertEquals(state, intent.state());
    assertEquals(reason, intent.reason());
    assertEquals(throttle, intent.throttle(), EPSILON);
    assertEquals(steering, intent.steering(), EPSILON);
  }

  private static DnvAutonomyCore.PerceptionSnapshot perception(
      long gameTime,
      DnvAutonomyCore.DomainOwner movementOwner,
      List<DnvAutonomyCore.TargetObservation> candidates,
      Map<String, DnvAutonomyCore.DomainStatus> stations) {
    return new DnvAutonomyCore.PerceptionSnapshot(
        gameTime,
        false,
        false,
        Optional.empty(),
        candidates,
        Optional.empty(),
        0.0F,
        movementOwner,
        stations);
  }

  private static DnvAutonomyCore.TargetObservation target(
      UUID id,
      double distance,
      float bearing,
      double threat,
      boolean lineOfSight) {
    return new DnvAutonomyCore.TargetObservation(
        id, 10.0D, 64.0D, 10.0D, distance, bearing, threat, true, true, lineOfSight);
  }

  private static Map<String, DnvAutonomyCore.DomainStatus> aiStations() {
    return Map.of(
        "seat_0", domain(DnvAutonomyCore.DomainOwner.AI, true, true),
        "seat_1", domain(DnvAutonomyCore.DomainOwner.AI, true, true));
  }

  private static DnvAutonomyCore.DomainStatus domain(
      DnvAutonomyCore.DomainOwner owner, boolean available, boolean lineOfSight) {
    return new DnvAutonomyCore.DomainStatus(owner, available, lineOfSight);
  }
}
