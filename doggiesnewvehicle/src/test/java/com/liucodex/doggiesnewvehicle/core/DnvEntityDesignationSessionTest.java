package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvEntityDesignationSessionTest {
  private static final UUID OWNER = UUID.randomUUID();
  private static final UUID VEHICLE = UUID.randomUUID();
  private static final UUID TARGET = UUID.randomUUID();
  private static final String DIMENSION = "minecraft:overworld";
  private static final DnvEntityDesignationSession.Binding CORE =
      new DnvEntityDesignationSession.Binding(VEHICLE, UUID.randomUUID(), 4, OWNER, DIMENSION);

  @Test
  void operatorChecksDoNotLetAdministratorsBypassWorldOrRangeBoundaries() {
    UUID other = UUID.randomUUID();
    assertTrue(DnvEntityDesignationSession.allowsOperator(OWNER, OWNER, false, true, 256 * 256));
    assertTrue(DnvEntityDesignationSession.allowsOperator(other, OWNER, true, true, 1));
    assertFalse(DnvEntityDesignationSession.allowsOperator(other, OWNER, false, true, 1));
    assertFalse(DnvEntityDesignationSession.allowsOperator(other, OWNER, true, false, 1));
    for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, 256 * 256 + 1}) {
      assertFalse(DnvEntityDesignationSession.allowsOperator(OWNER, OWNER, true, true, invalid));
    }
  }

  @Test
  void commandAndCancellationReturnCorrelatedCurrentSnapshotsWithoutResurrectingOldIntents() {
    var session = session();
    var designate = command(session, DnvCombatOrderService.OrderType.DESIGNATE_ENTITY, 100);
    var first = session.submit(CORE, designate, context(100));
    assertTrue(first.accepted());
    assertEquals(designate.operationId(), first.operationId());
    assertEquals(Optional.of(TARGET), first.view().targetId());
    assertEquals(first, session.submit(CORE, designate, context(100)));
    var cancel = command(session, DnvCombatOrderService.OrderType.CANCEL_ORDER, 101);
    var cancelled = session.submit(CORE, cancel, context(101));
    assertTrue(cancelled.accepted());
    assertEquals(DnvEntityDesignationSession.State.CANCELLED, cancelled.view().state());
    assertTrue(cancelled.view().targetId().isEmpty());
    assertEquals(designate.orderId(), cancelled.view().displayedOrder().orElseThrow().orderId());
    var replay = session.submit(CORE, designate, context(102));
    assertTrue(replay.accepted());
    assertTrue(replay.view().targetId().isEmpty());
    assertEquals(cancelled.view().orders().revision(), replay.view().orders().revision());
  }

  @Test
  void replacingCoreOwnerDimensionRevisionAndUnloadingCannotCarryOldCommands() {
    for (var changed : List.of(
        new DnvEntityDesignationSession.Binding(UUID.randomUUID(), CORE.coreId(), 4, OWNER, DIMENSION),
        new DnvEntityDesignationSession.Binding(VEHICLE, UUID.randomUUID(), 4, OWNER, DIMENSION),
        new DnvEntityDesignationSession.Binding(VEHICLE, CORE.coreId(), 5, OWNER, DIMENSION),
        new DnvEntityDesignationSession.Binding(VEHICLE, CORE.coreId(), 4, UUID.randomUUID(), DIMENSION),
        new DnvEntityDesignationSession.Binding(
            VEHICLE, CORE.coreId(), 4, OWNER, "minecraft:the_nether"))) {
      var session = session();
      var command = command(session, DnvCombatOrderService.OrderType.DESIGNATE_ENTITY, 100);
      session.submit(CORE, command, context(100));
      session.synchronize(Optional.of(changed));
      assertTrue(session.view(100).targetId().isEmpty());
      assertEquals("CORE_CHANGED", session.submit(CORE, command, context(100)).reason());
      session.synchronize(Optional.empty());
      session.synchronize(Optional.of(CORE));
      assertFalse(session.submit(CORE, command, context(100)).accepted());
    }
  }

  @Test
  void targetLossDisconnectAndExternalLeaseEndAnOrderOnlyOnce() {
    for (var reason : List.of(DnvEntityDesignationSession.State.TARGET_INVALID,
        DnvEntityDesignationSession.State.ISSUER_UNAVAILABLE,
        DnvEntityDesignationSession.State.EXTERNAL_CONTROL)) {
      var session = session();
      var command = command(session, DnvCombatOrderService.OrderType.DESIGNATE_ENTITY, 100);
      session.submit(CORE, command, context(100));
      long activeRevision = session.view(100).orders().revision();
      session.invalidate(reason, 101);
      assertEquals(reason, session.view(101).state());
      assertEquals(command.orderId(), session.view(101).displayedOrder().orElseThrow().orderId());
      assertEquals(activeRevision + 1, session.view(101).orders().revision());
      session.invalidate(reason, 102);
      assertEquals(activeRevision + 1, session.view(102).orders().revision());
      assertFalse(session.submit(CORE, command, context(102)).accepted());
      assertTrue(session.view(102).targetId().isEmpty());
    }
  }

  @Test
  void expiryAndClockRollbackExposeFreshEmptyStateAndDoNotWaitForAnotherCommand() {
    var session = session();
    session.submit(CORE, command(session, DnvCombatOrderService.OrderType.DESIGNATE_ENTITY, 100), context(100));
    long revision = session.view(100).orders().revision();
    assertTrue(session.view(700).targetId().isPresent());
    assertEquals(DnvEntityDesignationSession.State.EXPIRED, session.view(701).state());
    assertEquals(revision + 1, session.view(701).orders().revision());
    assertTrue(session.view(701).targetId().isEmpty());
    assertTrue(session.view(701).displayedOrder().isPresent());
    session.submit(CORE, command(session, DnvCombatOrderService.OrderType.DESIGNATE_ENTITY, 800), context(800));
    assertEquals(DnvEntityDesignationSession.State.EXPIRED, session.view(799).state());
  }

  private static DnvEntityDesignationSession session() {
    var session = new DnvEntityDesignationSession(VEHICLE);
    session.synchronize(Optional.of(CORE));
    return session;
  }

  private static DnvCombatOrderService.Command command(
      DnvEntityDesignationSession session, DnvCombatOrderService.OrderType type, long now) {
    UUID operation = UUID.randomUUID();
    return new DnvCombatOrderService.Command(operation, operation, OWNER, VEHICLE, session.view(now).orders().revision(), now, now + 600, DIMENSION, type, type == DnvCombatOrderService.OrderType.DESIGNATE_ENTITY
            ? Optional.of(TARGET) : Optional.empty());
  }

  private static DnvCombatOrderService.ValidationContext context(long now) {
    return new DnvCombatOrderService.ValidationContext(
        now, OWNER, DIMENSION, true, 20, true, true, DnvCombatOrderService.TargetKind.LIVING);
  }
}
