package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvTurretTargetMemoryTest {
  private static final UUID AUTONOMOUS = UUID.randomUUID();
  private static final UUID COMMANDED = UUID.randomUUID();
  private static final DnvTurretTargetMemory.Position AUTO_POSITION =
      new DnvTurretTargetMemory.Position(4.0D, 5.0D, 6.0D);
  private static final DnvTurretTargetMemory.Position COMMAND_POSITION =
      new DnvTurretTargetMemory.Position(14.0D, 15.0D, 16.0D);

  @Test
  void explicitCommandWinsAndRetainsItsIdentityTtlPositionAndReason() {
    var memory = new DnvTurretTargetMemory();
    var first = memory.decide(input(100, 3, true, DnvTurretTargetMemory.CommandMode.ACTIVE,
        Optional.of(command(100, 160)), Optional.of(autonomous(100, 120))));
    var target = first.target().orElseThrow();
    assertEquals(COMMANDED, target.targetId());
    assertEquals(DnvTurretTargetMemory.Source.EXPLICIT_COMMAND, target.source());
    assertEquals(DnvTurretTargetMemory.COMMAND_PRIORITY, target.priority());
    assertEquals(7, target.revision());
    assertEquals(160, target.expiresGameTime());
    assertEquals(COMMAND_POSITION, target.lastVerifiedPosition());
    assertEquals(DnvTurretTargetMemory.DecisionReason.COMMAND_SELECTED, first.reason());

    var retained = memory.decide(input(101, 3, true,
        DnvTurretTargetMemory.CommandMode.ACTIVE,
        Optional.of(command(101, 160)), Optional.of(autonomous(101, 121))));
    assertEquals(DnvTurretTargetMemory.DecisionReason.COMMAND_RETAINED, retained.reason());
  }

  @Test
  void cancellationImmediatelyFallsBackToARevalidatedAutonomousCandidate() {
    var memory = new DnvTurretTargetMemory();
    memory.decide(input(100, 3, true, DnvTurretTargetMemory.CommandMode.ACTIVE,
        Optional.of(command(100, 160)), Optional.empty()));
    var fallback = memory.decide(input(101, 3, true,
        DnvTurretTargetMemory.CommandMode.NONE,
        Optional.empty(), Optional.of(autonomous(101, 121))));
    assertEquals(AUTONOMOUS, fallback.target().orElseThrow().targetId());
    assertEquals(DnvTurretTargetMemory.Source.AUTONOMOUS,
        fallback.target().orElseThrow().source());
    assertEquals(DnvTurretTargetMemory.DecisionReason.AUTONOMOUS_SELECTED,
        fallback.reason());
    assertEquals(DnvTurretTargetMemory.InvalidationReason.COMMAND_ENDED,
        fallback.invalidatedBy().orElseThrow());
  }

  @Test
  void invalidActiveCommandAndDisableOrHoldNeverLeakIntoAutonomousFire() {
    for (var mode : new DnvTurretTargetMemory.CommandMode[] {
        DnvTurretTargetMemory.CommandMode.ACTIVE,
        DnvTurretTargetMemory.CommandMode.DISABLE,
        DnvTurretTargetMemory.CommandMode.HOLD}) {
      var memory = new DnvTurretTargetMemory();
      var decision = memory.decide(input(100, 3, true, mode,
          Optional.empty(), Optional.of(autonomous(100, 120))));
      assertTrue(decision.target().isEmpty(), mode.name());
    }
  }

  @Test
  void ttlTargetLossAndClockRollbackClearRatherThanResurrectMemory() {
    var memory = new DnvTurretTargetMemory();
    memory.decide(input(100, 3, true, DnvTurretTargetMemory.CommandMode.ACTIVE,
        Optional.of(command(100, 100)), Optional.empty()));
    var expired = memory.decide(input(101, 3, true,
        DnvTurretTargetMemory.CommandMode.NONE, Optional.empty(), Optional.empty()));
    assertTrue(expired.target().isEmpty());
    assertEquals(DnvTurretTargetMemory.InvalidationReason.TTL_EXPIRED,
        expired.invalidatedBy().orElseThrow());

    memory.decide(input(110, 3, true, DnvTurretTargetMemory.CommandMode.NONE,
        Optional.empty(), Optional.of(autonomous(110, 130))));
    var rollback = memory.decide(input(109, 3, true,
        DnvTurretTargetMemory.CommandMode.NONE,
        Optional.empty(), Optional.of(autonomous(109, 129))));
    assertTrue(rollback.target().isEmpty());
    assertEquals(DnvTurretTargetMemory.DecisionReason.CLOCK_ROLLBACK, rollback.reason());
  }

  @Test
  void configurationAndCoreChangesInvalidateOldFactsBeforeFreshSelection() {
    var memory = new DnvTurretTargetMemory();
    memory.decide(input(100, 3, true, DnvTurretTargetMemory.CommandMode.NONE,
        Optional.empty(), Optional.of(autonomous(100, 120))));
    var reconfigured = memory.decide(input(101, 4, true,
        DnvTurretTargetMemory.CommandMode.NONE,
        Optional.empty(), Optional.of(autonomous(101, 121))));
    assertEquals(AUTONOMOUS, reconfigured.target().orElseThrow().targetId());
    assertEquals(DnvTurretTargetMemory.InvalidationReason.CONFIGURATION_CHANGED,
        reconfigured.invalidatedBy().orElseThrow());

    var disabled = memory.decide(input(102, 4, false,
        DnvTurretTargetMemory.CommandMode.NONE,
        Optional.empty(), Optional.of(autonomous(102, 122))));
    assertTrue(disabled.target().isEmpty());
    assertEquals(DnvTurretTargetMemory.DecisionReason.CORE_INACTIVE, disabled.reason());
  }

  @Test
  void unloadDeathAndRestartHaveNoPersistentCommandMemory() {
    var memory = new DnvTurretTargetMemory();
    memory.decide(input(100, 3, true, DnvTurretTargetMemory.CommandMode.ACTIVE,
        Optional.of(command(100, 160)), Optional.empty()));
    var invalidated = memory.invalidate(
        101, DnvTurretTargetMemory.InvalidationReason.ENTITY_INVALIDATED);
    assertTrue(invalidated.target().isEmpty());
    assertTrue(new DnvTurretTargetMemory().current().target().isEmpty());
  }

  private static DnvTurretTargetMemory.Input input(
      long now,
      long configurationRevision,
      boolean coreActive,
      DnvTurretTargetMemory.CommandMode commandMode,
      Optional<DnvTurretTargetMemory.Candidate> command,
      Optional<DnvTurretTargetMemory.Candidate> autonomous) {
    return new DnvTurretTargetMemory.Input(now, configurationRevision, coreActive,
        commandMode, command, autonomous);
  }

  private static DnvTurretTargetMemory.Candidate command(long observed, long expires) {
    return DnvTurretTargetMemory.Candidate.command(
        COMMANDED, true, 7, observed, expires, COMMAND_POSITION);
  }

  private static DnvTurretTargetMemory.Candidate autonomous(long observed, long expires) {
    return DnvTurretTargetMemory.Candidate.autonomous(
        AUTONOMOUS, 2, observed, expires, AUTO_POSITION);
  }
}
