package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvCombatOrderServiceTest {
  private static final UUID ISSUER = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID PLATFORM = UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final String DIMENSION = "minecraft:overworld";

  @Test
  void acceptedDesignationIsVersionedIdempotentAndDoesNotFire() {
    DnvCombatOrderService service = new DnvCombatOrderService(PLATFORM);
    UUID operation = UUID.fromString("00000000-0000-0000-0000-000000000111");
    DnvCombatOrderService.Command command = designate(operation, 0L, TARGET);
    DnvCombatOrderService.ValidationContext context = context(100L, ISSUER, DIMENSION, true, 20.0D,
        DnvCombatOrderService.TargetKind.LIVING);

    DnvCombatOrderService.Receipt first = service.submit(command, context);
    DnvCombatOrderService.Receipt replay = service.submit(command, context);

    assertEquals(DnvCombatOrderService.Status.ACCEPTED, first.status());
    assertEquals(first, replay);
    assertEquals(Optional.of(command), service.commandForOperation(operation));
    assertEquals(1L, service.snapshot(100L).revision());
    assertEquals(DnvCombatOrderService.OrderType.DESIGNATE_ENTITY,
        service.snapshot(100L).activeOrder().orElseThrow().type());
  }

  @Test
  void rejectsOutOfOrderFutureAndMutatedReplay() {
    DnvCombatOrderService service = new DnvCombatOrderService(PLATFORM);
    UUID operation = UUID.fromString("00000000-0000-0000-0000-000000000112");
    DnvCombatOrderService.ValidationContext context = context(100L, ISSUER, DIMENSION, true, 20.0D,
        DnvCombatOrderService.TargetKind.LIVING);
    assertEquals(DnvCombatOrderService.Status.ACCEPTED,
        service.submit(designate(operation, 0L, TARGET), context).status());
    assertEquals(DnvCombatOrderService.Reason.REVISION_MISMATCH,
        service.submit(designate(UUID.randomUUID(), 0L, TARGET), context).reason());
    assertEquals(DnvCombatOrderService.Reason.REVISION_MISMATCH,
        service.submit(designate(UUID.randomUUID(), 7L, TARGET), context).reason());
    assertEquals(DnvCombatOrderService.Reason.OPERATION_COLLISION,
        service.submit(designate(operation, 1L, UUID.randomUUID()), context).reason());
  }

  @Test
  void rejectsUnauthorizedCrossDimensionOverRangeAndPvpTargets() {
    DnvCombatOrderService service = new DnvCombatOrderService(PLATFORM);
    assertEquals(DnvCombatOrderService.Reason.PERMISSION_DENIED,
        service.submit(designate(UUID.randomUUID(), 0L, TARGET),
            context(100L, ISSUER, DIMENSION, false, 20.0D,
            DnvCombatOrderService.TargetKind.LIVING)).reason());
    assertEquals(DnvCombatOrderService.Reason.DIMENSION_MISMATCH,
        service.submit(designate(UUID.randomUUID(), 0L, TARGET),
            context(100L, ISSUER, "minecraft:the_nether", true, 20.0D,
            DnvCombatOrderService.TargetKind.LIVING)).reason());
    assertEquals(DnvCombatOrderService.Reason.OUT_OF_RANGE,
        service.submit(designate(UUID.randomUUID(), 0L, TARGET),
            context(100L, ISSUER, DIMENSION, true, 257.0D,
            DnvCombatOrderService.TargetKind.LIVING)).reason());
    assertEquals(DnvCombatOrderService.Reason.PVP_FORBIDDEN,
        service.submit(designate(UUID.randomUUID(), 0L, TARGET),
            context(100L, ISSUER, DIMENSION, true, 20.0D,
            DnvCombatOrderService.TargetKind.PLAYER_OCCUPIED_VEHICLE)).reason());
    assertEquals(DnvCombatOrderService.Reason.TARGET_INVALID,
        service.submit(designate(UUID.randomUUID(), 0L, TARGET),
            new DnvCombatOrderService.ValidationContext(
                100L, ISSUER, DIMENSION, true, 20.0D, true, false, DnvCombatOrderService.TargetKind.LIVING)).reason());
  }

  @Test
  void expiredOrderAndCancellationFailClosed() {
    DnvCombatOrderService service = new DnvCombatOrderService(PLATFORM);
    DnvCombatOrderService.ValidationContext at100 = context(
        100L, ISSUER, DIMENSION, true, 20.0D, DnvCombatOrderService.TargetKind.LIVING);
    assertEquals(DnvCombatOrderService.Status.ACCEPTED,
        service.submit(designate(UUID.randomUUID(), 0L, TARGET), at100).status());
    assertTrue(service.snapshot(301L).activeOrder().isEmpty());
    assertEquals(2L, service.snapshot(301L).revision());

    DnvCombatOrderService.Command cancel = new DnvCombatOrderService.Command(
        UUID.randomUUID(), UUID.randomUUID(), ISSUER, PLATFORM, 2L, 302L, 320L, DIMENSION,
        DnvCombatOrderService.OrderType.CANCEL_ORDER, Optional.empty());
    assertEquals(DnvCombatOrderService.Status.ACCEPTED,
        service.submit(cancel, context(302L, ISSUER, DIMENSION, true, 20.0D,
            DnvCombatOrderService.TargetKind.NONE)).status());
    assertTrue(service.snapshot(302L).activeOrder().isEmpty());
  }

  @Test
  void replayCannotDiscloseAcceptanceAfterPermissionIdentityDimensionOrTimeLoss() {
    DnvCombatOrderService service = new DnvCombatOrderService(PLATFORM);
    var command = designate(UUID.randomUUID(), 0L, TARGET);
    assertEquals(DnvCombatOrderService.Status.ACCEPTED, service.submit(command,
        context(100, ISSUER, DIMENSION, true, 20, DnvCombatOrderService.TargetKind.LIVING)).status());
    assertEquals(DnvCombatOrderService.Reason.PERMISSION_DENIED, service.submit(command,
        context(100, ISSUER, DIMENSION, false, 20, DnvCombatOrderService.TargetKind.LIVING)).reason());
    assertEquals(DnvCombatOrderService.Reason.ISSUER_MISMATCH, service.submit(command,
        context(100, UUID.randomUUID(), DIMENSION, true, 20, DnvCombatOrderService.TargetKind.LIVING)).reason());
    assertEquals(DnvCombatOrderService.Reason.DIMENSION_MISMATCH, service.submit(command,
        context(100, ISSUER, "minecraft:the_nether", true, 20, DnvCombatOrderService.TargetKind.LIVING)).reason());
    assertEquals(DnvCombatOrderService.Reason.OUT_OF_RANGE, service.submit(command,
        context(100, ISSUER, DIMENSION, true, 300, DnvCombatOrderService.TargetKind.LIVING)).reason());
    assertEquals(DnvCombatOrderService.Reason.TIME_INVALID, service.submit(command,
        context(301, ISSUER, DIMENSION, true, 20, DnvCombatOrderService.TargetKind.LIVING)).reason());
    assertTrue(service.snapshot(301).activeOrder().isEmpty());
    assertEquals(2, service.snapshot(301).revision());
  }

  @Test
  void timeRollbackInvalidatesOnceAndCancelledDesignationCannotBeReplayedIntoActivity() {
    DnvCombatOrderService service = new DnvCombatOrderService(PLATFORM);
    var command = designate(UUID.randomUUID(), 0, TARGET);
    var context = context(100, ISSUER, DIMENSION, true, 20, DnvCombatOrderService.TargetKind.LIVING);
    service.submit(command, context);
    assertTrue(service.snapshot(99).activeOrder().isEmpty());
    assertEquals(2, service.snapshot(99).revision());
    assertEquals(2, service.snapshot(98).revision());
    // A replay acknowledges an old operation but cannot execute it a second time.
    assertEquals(DnvCombatOrderService.Status.ACCEPTED, service.submit(command, context).status());
    assertFalse(service.snapshot(100).activeOrder().isPresent());
  }

  private static DnvCombatOrderService.Command designate(
      UUID operationId, long revision, UUID target) {
    return new DnvCombatOrderService.Command(
        operationId, UUID.fromString("00000000-0000-0000-0000-000000000121"), ISSUER, PLATFORM, revision, 100L,
        300L, DIMENSION, DnvCombatOrderService.OrderType.DESIGNATE_ENTITY, Optional.of(target));
  }

  private static DnvCombatOrderService.ValidationContext context(
      long gameTime,
      UUID authenticatedIssuer,
      String dimension,
      boolean permission,
      double distance,
      DnvCombatOrderService.TargetKind targetKind) {
    return new DnvCombatOrderService.ValidationContext(
        gameTime, authenticatedIssuer, dimension, permission, distance, true, true, targetKind);
  }
}
