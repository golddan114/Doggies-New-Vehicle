package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteDrafts;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteProtocol;
import com.liucodex.doggiesnewvehicle.mission.DnvAirRouteValidation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DnvTacticalRouteClientGateTest {
  private static final UUID CORE_A = id(10);
  private static final UUID CORE_B = id(11);
  private static final UUID DRAFT_A = id(12);
  private static final UUID DRAFT_B = id(13);

  @Test
  void lateAResponseCannotOverwriteTheCurrentBRequest() {
    DnvTacticalRouteClientGate gate = gate();
    var requestA = gate.begin(
        DnvTacticalRouteProtocol.Action.SNAPSHOT, Optional.empty(), 0L);
    var loadedA = response(
        requestA,
        DnvTacticalRouteProtocol.Action.SNAPSHOT,
        projection(CORE_A, DRAFT_A, 0L));
    assertEquals(
        DnvTacticalRouteClientGate.Disposition.REPLACE_LOCAL,
        gate.accept(loadedA, Optional.empty(), 0L).disposition());

    var referenceA = loadedA.projection().orElseThrow().reference();
    var requestB = gate.begin(
        DnvTacticalRouteProtocol.Action.CYCLE, Optional.of(referenceA), 0L);
    assertEquals(
        DnvTacticalRouteClientGate.Disposition.IGNORED,
        gate.accept(loadedA, Optional.of(referenceA), 0L).disposition());
    assertTrue(gate.waiting());

    var loadedB = response(
        requestB,
        DnvTacticalRouteProtocol.Action.CYCLE,
        projection(CORE_B, DRAFT_B, 0L));
    assertEquals(
        DnvTacticalRouteClientGate.Disposition.REPLACE_LOCAL,
        gate.accept(loadedB, Optional.of(referenceA), 0L).disposition());
    assertFalse(gate.waiting());
  }

  @Test
  void successfulCommitDoesNotEraseEditsMadeAfterSubmission() {
    DnvTacticalRouteClientGate gate = gate();
    var reference = new DnvTacticalRouteDrafts.DraftRef(CORE_A, DRAFT_A);
    var request = gate.begin(
        DnvTacticalRouteProtocol.Action.COMMIT, Optional.of(reference), 7L);
    var accepted = response(
        request,
        DnvTacticalRouteProtocol.Action.COMMIT,
        projection(CORE_A, DRAFT_A, 4L));

    assertEquals(
        DnvTacticalRouteClientGate.Disposition.KEEP_LOCAL_EDITS,
        gate.accept(accepted, Optional.of(reference), 8L).disposition());
  }

  @Test
  void absentPlatformProjectionClosesThePendingSnapshotWithoutInventingADraft() {
    DnvTacticalRouteClientGate gate = gate();
    var request = gate.begin(
        DnvTacticalRouteProtocol.Action.SNAPSHOT, Optional.empty(), 0L);
    var unavailable = DnvTacticalRouteProtocol.RouteResponse.snapshot(
        request, Optional.empty(), Optional.empty());

    assertEquals(DnvTacticalRouteDrafts.Issue.BINDING_REQUIRED, unavailable.issue());
    assertEquals(
        DnvTacticalRouteClientGate.Disposition.NO_PROJECTION,
        gate.accept(unavailable, Optional.empty(), 0L).disposition());
    assertFalse(gate.waiting());
  }

  @Test
  void closeAndMismatchedDraftBothDropResponsesWithoutReopeningState() {
    DnvTacticalRouteClientGate gate = gate();
    var reference = new DnvTacticalRouteDrafts.DraftRef(CORE_A, DRAFT_A);
    var request = gate.begin(
        DnvTacticalRouteProtocol.Action.COMMIT, Optional.of(reference), 1L);
    var wrongDraft = response(
        request,
        DnvTacticalRouteProtocol.Action.COMMIT,
        projection(CORE_B, DRAFT_B, 2L));
    assertEquals(
        DnvTacticalRouteClientGate.Disposition.IGNORED,
        gate.accept(wrongDraft, Optional.of(reference), 1L).disposition());

    var next = gate.begin(
        DnvTacticalRouteProtocol.Action.SNAPSHOT, Optional.empty(), 1L);
    gate.close();
    assertEquals(
        DnvTacticalRouteClientGate.Disposition.IGNORED,
        gate.accept(
            response(
                next,
                DnvTacticalRouteProtocol.Action.SNAPSHOT,
                projection(CORE_A, DRAFT_A, 2L)),
            Optional.empty(),
            1L).disposition());
  }

  private static DnvTacticalRouteClientGate gate() {
    AtomicLong next = new AtomicLong(100L);
    return new DnvTacticalRouteClientGate(() -> id(next.getAndIncrement()));
  }

  private static DnvTacticalRouteProtocol.RouteResponse response(
      DnvTacticalRouteProtocol.RequestContext context,
      DnvTacticalRouteProtocol.Action action,
      DnvTacticalRouteDrafts.DraftProjection projection) {
    return new DnvTacticalRouteProtocol.RouteResponse(
        context,
        action,
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

  private static DnvTacticalRouteDrafts.DraftProjection projection(
      UUID core, UUID draft, long revision) {
    return new DnvTacticalRouteDrafts.DraftProjection(
        new DnvTacticalRouteDrafts.DraftRef(core, draft),
        new DnvTacticalRouteDrafts.Snapshot(
            revision,
            "minecraft:overworld",
            List.of()),
        new DnvTacticalRouteDrafts.VehicleBinding(
            "minecraft:overworld", core, id(draft.getLeastSignificantBits() + 1000L)),
        1,
        2);
  }

  private static UUID id(long value) {
    return new UUID(0L, value);
  }
}
