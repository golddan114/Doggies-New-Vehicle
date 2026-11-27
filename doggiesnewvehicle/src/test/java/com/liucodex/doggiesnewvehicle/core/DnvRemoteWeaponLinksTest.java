package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DnvRemoteWeaponLinksTest {
  private final DnvRemoteWeaponLinks state = new DnvRemoteWeaponLinks();
  private final UUID actor = UUID.randomUUID();
  private final UUID station = UUID.randomUUID();
  private final Target first = target("minecraft:overworld");
  private final Target second = target("minecraft:overworld");

  DnvRemoteWeaponLinksTest() { assertEquals(Reason.NONE, state.create(station, actor, "minecraft:overworld", 10)); }

  @Test void connectReplaceUnlinkAndReverseReservationAreOneTransaction() {
    assertTrue(apply(Action.LINK, 0, first).accepted());
    assertTrue(apply(Action.REPLACE, 1, second).accepted());
    UUID another = UUID.randomUUID();
    assertEquals(Reason.NONE, state.create(another, actor, "minecraft:overworld", 20));
    assertTrue(state.apply(request(another, Action.LINK, 0, first), Reason.NONE).accepted());
    assertEquals(second, state.station(station).orElseThrow().target().orElseThrow());
    assertTrue(apply(Action.UNLINK, 2, null).accepted());
    assertTrue(state.apply(request(another, Action.REPLACE, 1, second), Reason.NONE).accepted());
  }

  @ParameterizedTest @EnumSource(value = Reason.class, names = {"NONE"}, mode = EnumSource.Mode.EXCLUDE)
  void failedReplacementKeepsOldTargetAndRevision(Reason reason) {
    assertTrue(apply(Action.LINK, 0, first).accepted());
    Request replace = request(station, Action.REPLACE, 1, second);
    assertEquals(reason, state.apply(replace, reason).reason());
    assertEquals(first, state.station(station).orElseThrow().target().orElseThrow());
    assertEquals(1, state.station(station).orElseThrow().revision());
    assertTrue(state.apply(replace, Reason.NONE).replayed());
    assertEquals(reason, state.apply(replace, Reason.NONE).reason());
  }

  @Test void twoStationsCannotClaimTheSameEntityEvenAcrossDimensions() {
    assertTrue(apply(Action.LINK, 0, first).accepted());
    UUID another = UUID.randomUUID();
    state.create(another, actor, "minecraft:the_nether", 10);
    Target moved = new Target(first.id(), first.type(), "minecraft:the_nether");
    assertEquals(Reason.TARGET_ALREADY_LINKED, state.apply(request(another, Action.LINK, 0, moved), Reason.NONE).reason());
  }

  @Test void staleConcurrentRevisionAndOperationConflictCannotOverwrite() {
    Request initial = request(station, Action.LINK, 0, first);
    assertTrue(state.apply(initial, Reason.NONE).accepted());
    assertTrue(state.apply(initial, Reason.NONE).replayed());
    Request altered = new Request(actor, initial.operation(), station, 1, Action.REPLACE, Optional.of(second));
    assertEquals(Reason.OPERATION_CONFLICT, state.apply(altered, Reason.NONE).reason());
    assertEquals(Reason.STALE_REVISION, apply(Action.REPLACE, 0, second).reason());
    assertEquals(first, state.station(station).orElseThrow().target().orElseThrow());
  }

  @Test void ownershipPrecedesReplayAndAnOperatorCannotRetireAnotherStation() {
    Request initial = request(station, Action.LINK, 0, first);
    state.apply(initial, Reason.NONE);
    Request other = new Request(UUID.randomUUID(), initial.operation(), station, 1, Action.RETIRE, Optional.empty());
    assertEquals(Reason.NOT_OWNER, state.apply(other, Reason.NONE).reason());
    assertEquals(1, state.snapshot().receipts().size());
  }

  @Test void retireReleasesTargetAndReplaysAfterRecoveryWithoutResurrectingStation() {
    state.apply(request(station, Action.LINK, 0, first), Reason.NONE);
    Request retire = request(station, Action.RETIRE, 1, null);
    assertTrue(state.apply(retire, Reason.NONE).accepted());
    var restored = DnvRemoteWeaponLinks.restore(state.snapshot());
    assertTrue(restored.station(station).isEmpty());
    assertTrue(restored.apply(retire, Reason.NONE).replayed());
    UUID next = UUID.randomUUID();
    restored.create(next, actor, "minecraft:overworld", 10);
    assertTrue(restored.apply(request(next, Action.LINK, 0, first), Reason.NONE).accepted());
  }

  @Test void validRecoveryRebuildsReverseIndexAndOriginalReceipts() {
    Request initial = request(station, Action.LINK, 0, first);
    state.apply(initial, Reason.NONE);
    var restored = DnvRemoteWeaponLinks.restore(state.snapshot());
    assertTrue(restored.apply(initial, Reason.TARGET_UNLOADED).replayed());
    UUID another = UUID.randomUUID();
    restored.create(another, actor, "minecraft:overworld", 20);
    assertEquals(Reason.TARGET_ALREADY_LINKED, restored.apply(request(another, Action.LINK, 0, first), Reason.NONE).reason());
  }

  @Test void duplicatePositionsTargetsAndOperationsFailClosedOnRecovery() {
    Station linked = new Station(station, actor, "minecraft:overworld", 10, 1, Optional.of(first));
    Station duplicateTarget = new Station(UUID.randomUUID(), actor, "minecraft:overworld", 20, 1, Optional.of(first));
    assertThrows(IllegalArgumentException.class, () -> DnvRemoteWeaponLinks.restore(new Snapshot(List.of(linked, duplicateTarget), List.of())));
    Station duplicatePos = new Station(UUID.randomUUID(), actor, "minecraft:overworld", 10, 1, Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> DnvRemoteWeaponLinks.restore(new Snapshot(List.of(linked, duplicatePos), List.of())));
    Receipt receipt = new Receipt(request(station, Action.LINK, 0, first), Reason.NONE, 1);
    assertThrows(IllegalArgumentException.class, () -> DnvRemoteWeaponLinks.restore(new Snapshot(List.of(linked), List.of(receipt, receipt))));
  }

  @Test void revisionExhaustionCannotPartiallyReleaseOldTarget() {
    var restored = DnvRemoteWeaponLinks.restore(new Snapshot(List.of(
        new Station(station, actor, "minecraft:overworld", 10, Long.MAX_VALUE, Optional.of(first))), List.of()));
    assertEquals(Reason.REVISION_EXHAUSTED, restored.apply(request(station, Action.REPLACE, Long.MAX_VALUE, second), Reason.NONE).reason());
    assertEquals(first, restored.station(station).orElseThrow().target().orElseThrow());
  }

  @Test void stateAndReceiptCapacityAreBounded() {
    for (int i = 1; i < DnvRemoteWeaponLinks.MAX_STATIONS; i++) state.create(UUID.randomUUID(), actor, "minecraft:overworld", 10 + i);
    assertEquals(Reason.CAPACITY, state.create(UUID.randomUUID(), actor, "minecraft:overworld", 9999));
    for (int i = 0; i < DnvRemoteWeaponLinks.MAX_RECEIPTS + 20; i++) apply(Action.UNLINK, 0, null);
    assertEquals(DnvRemoteWeaponLinks.MAX_RECEIPTS, state.snapshot().receipts().size());
  }

  @Test void terminalRetirementAtMaximumRevisionReleasesTargetWithoutOverflowAndStillReplays() {
    var restored = DnvRemoteWeaponLinks.restore(new Snapshot(List.of(
        new Station(station, actor, "minecraft:overworld", 10, Long.MAX_VALUE, Optional.of(first))), List.of()));
    Request retire = request(station, Action.RETIRE, Long.MAX_VALUE, null);
    assertTrue(restored.apply(retire, Reason.NONE).accepted());
    assertTrue(restored.station(station).isEmpty());
    var recovered = DnvRemoteWeaponLinks.restore(restored.snapshot());
    assertTrue(recovered.apply(retire, Reason.NONE).replayed());
    assertEquals(Long.MAX_VALUE, recovered.apply(retire, Reason.NONE).revision());
    UUID another = UUID.randomUUID();
    recovered.create(another, actor, "minecraft:overworld", 10);
    assertTrue(recovered.apply(request(another, Action.LINK, 0, first), Reason.NONE).accepted());
  }

  @Test void actionAndDimensionConstraintsAreExplicit() {
    assertEquals(Reason.STATION_UNLINKED, apply(Action.REPLACE, 0, first).reason());
    assertEquals(Reason.WRONG_DIMENSION, apply(Action.LINK, 0, target("minecraft:the_nether")).reason());
    assertTrue(apply(Action.LINK, 0, first).accepted());
    assertEquals(Reason.STATION_ALREADY_LINKED, apply(Action.LINK, 1, second).reason());
    assertEquals(Reason.SAME_TARGET, apply(Action.REPLACE, 1, first).reason());
    assertThrows(IllegalArgumentException.class, () -> request(station, Action.UNLINK, 1, first));
  }

  private Result apply(Action action, long revision, Target target) { return state.apply(request(station, action, revision, target), Reason.NONE); }
  private Request request(UUID stationId, Action action, long revision, Target target) {
    return new Request(actor, UUID.randomUUID(), stationId, revision, action, Optional.ofNullable(target));
  }
  private static Target target(String dimension) { return new Target(UUID.randomUUID(), "test:manual_target", dimension); }
}
