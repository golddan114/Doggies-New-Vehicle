package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.route.DnvRouteWaypoint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.liucodex.doggiesnewvehicle.mission.DnvAirRouteValidation;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteClientProjection;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteDrafts;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteProtocol;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DnvTacticalRouteNetworkCodecTest {
  private static final UUID CORE = id(1);
  private static final UUID DRAFT = id(2);
  private static final UUID VEHICLE = id(3);
  private static final UUID REQUEST = id(4);
  private static final UUID PLAYER = id(5);

  @AfterEach
  void resetProjectionSink() {
    DnvTacticalRouteClientProjection.install(ignored -> {});
  }

  @Test
  void serverOwnedReceiptRoundTripsThroughWireAndCurrentClientLayer() {
    var drafts = new DnvTacticalRouteDrafts();
    var projection = drafts.bindVehicle(
        PLAYER, "minecraft:overworld", CORE, VEHICLE, 40L)
        .projection().orElseThrow();
    var gate = new DnvTacticalRouteClientGate(() -> REQUEST);
    var context = gate.begin(
        DnvTacticalRouteProtocol.Action.COMMIT,
        Optional.of(projection.reference()),
        0L);
    var route = List.of(
        new DnvRouteWaypoint(1.0D, 90.0D, 2.0D),
        new DnvRouteWaypoint(65.0D, 120.0D, 66.0D));
    var result = drafts.commit(
        PLAYER,
        projection.reference(),
        context.requestId(),
        projection.snapshot().revision(),
        "minecraft:overworld",
        route,
        new DnvTacticalRouteDrafts.Bounds(-1000, 1000, -64, 320, -1000, 1000),
        41L);
    var response = DnvTacticalRouteProtocol.RouteResponse.from(
        context,
        DnvTacticalRouteProtocol.Action.COMMIT,
        result,
        Optional.of(executableValidation()));
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      DnvTacticalRouteNetworkCodec.encodeResponse(response, buffer);
      var decoded = DnvTacticalRouteNetworkCodec.decodeResponse(buffer);
      var observed = new AtomicReference<DnvTacticalRouteClientGate.Decision>();
      DnvTacticalRouteClientProjection.install(message -> observed.set(gate.accept(
          message, Optional.of(projection.reference()), 0L)));
      DnvTacticalRouteClientProjection.response(decoded);

      assertEquals(
          DnvTacticalRouteClientGate.Disposition.REPLACE_LOCAL,
          observed.get().disposition());
      assertEquals(projection.reference(),
          observed.get().response().projection().orElseThrow().reference());
      assertEquals(1L,
          observed.get().response().projection().orElseThrow().snapshot().revision());
      assertEquals(route,
          observed.get().response().projection().orElseThrow().snapshot().waypoints());
    } finally {
      buffer.release();
    }
  }

  @Test
  void protocolThreeRoundTripsIdentityOperationRevisionProjectionAndSegments() {
    var context = new DnvTacticalRouteProtocol.RequestContext(REQUEST, 9L);
    var reference = new DnvTacticalRouteDrafts.DraftRef(CORE, DRAFT);
    var waypoints = List.of(
        new DnvRouteWaypoint(1.0D, 90.0D, 2.0D),
        new DnvRouteWaypoint(65.0D, 120.0D, 66.0D));
    var commit = new DnvTacticalRouteNetwork.CommitRequest(
        context, reference, 7L, waypoints);
    FriendlyByteBuf commitBuffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      DnvTacticalRouteNetworkCodec.encodeCommitRequest(commit, commitBuffer);
      assertEquals(
          commit,
          DnvTacticalRouteNetworkCodec.decodeCommitRequest(commitBuffer));
    } finally {
      commitBuffer.release();
    }

    var projection = new DnvTacticalRouteDrafts.DraftProjection(
        reference,
        new DnvTacticalRouteDrafts.Snapshot(8L, "minecraft:overworld", waypoints),
        new DnvTacticalRouteDrafts.VehicleBinding(
            "minecraft:overworld", CORE, VEHICLE),
        2,
        3);
    var response = new DnvTacticalRouteProtocol.RouteResponse(
        context,
        DnvTacticalRouteProtocol.Action.COMMIT,
        true,
        true,
        Optional.of(projection),
        Optional.of(executableValidation()),
        DnvTacticalRouteDrafts.Issue.NONE);
    FriendlyByteBuf responseBuffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      DnvTacticalRouteNetworkCodec.encodeResponse(response, responseBuffer);
      assertEquals(
          response,
          DnvTacticalRouteNetworkCodec.decodeResponse(responseBuffer));
    } finally {
      responseBuffer.release();
    }
  }

  @Test
  void snapshotAndDraftActionsRoundTripWithoutClientPlayerIdentity() {
    var context = new DnvTacticalRouteProtocol.RequestContext(REQUEST, 10L);
    var reference = new DnvTacticalRouteDrafts.DraftRef(CORE, DRAFT);
    var snapshot = new DnvTacticalRouteNetwork.SnapshotRequest(
        context, Optional.of(reference), 4L);
    FriendlyByteBuf snapshotBuffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      DnvTacticalRouteNetworkCodec.encodeSnapshotRequest(snapshot, snapshotBuffer);
      assertEquals(
          snapshot,
          DnvTacticalRouteNetworkCodec.decodeSnapshotRequest(snapshotBuffer));
    } finally {
      snapshotBuffer.release();
    }

    var action = new DnvTacticalRouteNetwork.ActionRequest(
        context, DnvTacticalRouteProtocol.Action.CREATE, reference, 4L);
    FriendlyByteBuf actionBuffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      DnvTacticalRouteNetworkCodec.encodeActionRequest(action, actionBuffer);
      assertEquals(
          action,
          DnvTacticalRouteNetworkCodec.decodeActionRequest(actionBuffer));
    } finally {
      actionBuffer.release();
    }
  }

  @Test
  void constructorsRejectUnboundedOrContradictoryWireValues() {
    var context = new DnvTacticalRouteProtocol.RequestContext(REQUEST, 1L);
    var reference = new DnvTacticalRouteDrafts.DraftRef(CORE, DRAFT);
    assertThrows(IllegalArgumentException.class, () ->
        new DnvTacticalRouteNetwork.ActionRequest(
            context, DnvTacticalRouteProtocol.Action.COMMIT, reference, 0L));
    assertThrows(IllegalArgumentException.class, () ->
        new DnvTacticalRouteProtocol.RouteResponse(
            context,
            DnvTacticalRouteProtocol.Action.SNAPSHOT,
            true,
            false,
            Optional.empty(),
            Optional.empty(),
            DnvTacticalRouteDrafts.Issue.NONE));
    assertThrows(IllegalArgumentException.class, () ->
        new DnvTacticalRouteNetwork.SnapshotRequest(
            context, Optional.empty(), 1L));
  }

  private static UUID id(long value) {
    return new UUID(0L, value);
  }

  private static DnvAirRouteValidation.Result executableValidation() {
    return new DnvAirRouteValidation.Result(
        DnvAirRouteValidation.Status.EXECUTABLE,
        DnvAirRouteValidation.Reason.NONE,
        List.of(new DnvAirRouteValidation.SegmentResult(
            0,
            DnvAirRouteValidation.Status.EXECUTABLE,
            DnvAirRouteValidation.Reason.NONE,
            new DnvAirRouteValidation.SampleRange(0.0D, 1.0D, 15))),
        15,
        DnvAirRouteValidation.MAX_TERRAIN_SAMPLES);
  }
}
