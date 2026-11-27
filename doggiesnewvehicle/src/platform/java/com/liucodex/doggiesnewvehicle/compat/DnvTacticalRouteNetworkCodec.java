package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.route.DnvRouteWaypoint;
import com.liucodex.doggiesnewvehicle.mission.DnvAirRouteValidation;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteDrafts;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteProtocol;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;

/** Exact bounded wire codec for tactical-route protocol 3. */
final class DnvTacticalRouteNetworkCodec {
  private DnvTacticalRouteNetworkCodec() {}

  static void encodeSnapshotRequest(
      DnvTacticalRouteNetwork.SnapshotRequest message,
      FriendlyByteBuf buffer) {
    writeContext(buffer, message.context());
    buffer.writeBoolean(message.reference().isPresent());
    message.reference().ifPresent(reference -> writeReference(buffer, reference));
    buffer.writeVarLong(message.expectedRevision());
  }

  static DnvTacticalRouteNetwork.SnapshotRequest decodeSnapshotRequest(
      FriendlyByteBuf buffer) {
    DnvTacticalRouteProtocol.RequestContext context = readContext(buffer);
    Optional<DnvTacticalRouteDrafts.DraftRef> reference = buffer.readBoolean()
        ? Optional.of(readReference(buffer))
        : Optional.empty();
    return new DnvTacticalRouteNetwork.SnapshotRequest(
        context, reference, buffer.readVarLong());
  }

  static void encodeActionRequest(
      DnvTacticalRouteNetwork.ActionRequest message,
      FriendlyByteBuf buffer) {
    writeContext(buffer, message.context());
    buffer.writeEnum(message.action());
    writeReference(buffer, message.reference());
    buffer.writeVarLong(message.expectedRevision());
  }

  static DnvTacticalRouteNetwork.ActionRequest decodeActionRequest(
      FriendlyByteBuf buffer) {
    return new DnvTacticalRouteNetwork.ActionRequest(
        readContext(buffer),
        buffer.readEnum(DnvTacticalRouteProtocol.Action.class),
        readReference(buffer),
        buffer.readVarLong());
  }

  static void encodeCommitRequest(
      DnvTacticalRouteNetwork.CommitRequest message,
      FriendlyByteBuf buffer) {
    writeContext(buffer, message.context());
    writeReference(buffer, message.reference());
    buffer.writeVarLong(message.expectedRevision());
    writeWaypoints(buffer, message.waypoints());
  }

  static DnvTacticalRouteNetwork.CommitRequest decodeCommitRequest(
      FriendlyByteBuf buffer) {
    return new DnvTacticalRouteNetwork.CommitRequest(
        readContext(buffer),
        readReference(buffer),
        buffer.readVarLong(),
        readWaypoints(buffer));
  }

  static void encodeResponse(
      DnvTacticalRouteProtocol.RouteResponse message,
      FriendlyByteBuf buffer) {
    writeContext(buffer, message.context());
    buffer.writeEnum(message.action());
    buffer.writeBoolean(message.accepted());
    buffer.writeBoolean(message.replayed());
    buffer.writeEnum(message.issue());
    buffer.writeBoolean(message.projection().isPresent());
    message.projection().ifPresent(projection -> writeProjection(buffer, projection));
    buffer.writeBoolean(message.validation().isPresent());
    message.validation().ifPresent(validation -> writeValidation(buffer, validation));
  }

  static DnvTacticalRouteProtocol.RouteResponse decodeResponse(
      FriendlyByteBuf buffer) {
    DnvTacticalRouteProtocol.RequestContext context = readContext(buffer);
    DnvTacticalRouteProtocol.Action action =
        buffer.readEnum(DnvTacticalRouteProtocol.Action.class);
    boolean accepted = buffer.readBoolean();
    boolean replayed = buffer.readBoolean();
    DnvTacticalRouteDrafts.Issue issue =
        buffer.readEnum(DnvTacticalRouteDrafts.Issue.class);
    Optional<DnvTacticalRouteDrafts.DraftProjection> projection = buffer.readBoolean()
        ? Optional.of(readProjection(buffer))
        : Optional.empty();
    Optional<DnvAirRouteValidation.Result> validation = buffer.readBoolean()
        ? Optional.of(readValidation(buffer))
        : Optional.empty();
    return new DnvTacticalRouteProtocol.RouteResponse(
        context, action, accepted, replayed, projection, validation, issue);
  }

  private static void writeValidation(
      FriendlyByteBuf buffer, DnvAirRouteValidation.Result validation) {
    buffer.writeEnum(validation.status());
    buffer.writeEnum(validation.reason());
    buffer.writeVarInt(validation.sampleCount());
    buffer.writeVarInt(validation.sampleBudget());
    buffer.writeVarInt(validation.segments().size());
    for (DnvAirRouteValidation.SegmentResult segment : validation.segments()) {
      buffer.writeVarInt(segment.segmentIndex());
      buffer.writeEnum(segment.status());
      buffer.writeEnum(segment.reason());
      buffer.writeDouble(segment.sampleRange().startFraction());
      buffer.writeDouble(segment.sampleRange().endFraction());
      buffer.writeVarInt(segment.sampleRange().sampleCount());
    }
  }

  private static DnvAirRouteValidation.Result readValidation(
      FriendlyByteBuf buffer) {
    DnvAirRouteValidation.Status status =
        buffer.readEnum(DnvAirRouteValidation.Status.class);
    DnvAirRouteValidation.Reason reason =
        buffer.readEnum(DnvAirRouteValidation.Reason.class);
    int sampleCount = buffer.readVarInt();
    int sampleBudget = buffer.readVarInt();
    int segmentCount = buffer.readVarInt();
    if (segmentCount < 0
        || segmentCount > DnvRouteWaypoint.MAX_WAYPOINTS - 1) {
      throw new IllegalArgumentException(
          "AIR route validation segment count is outside protocol bounds");
    }
    List<DnvAirRouteValidation.SegmentResult> segments = new ArrayList<>();
    for (int index = 0; index < segmentCount; index++) {
      segments.add(new DnvAirRouteValidation.SegmentResult(
          buffer.readVarInt(),
          buffer.readEnum(DnvAirRouteValidation.Status.class),
          buffer.readEnum(DnvAirRouteValidation.Reason.class),
          new DnvAirRouteValidation.SampleRange(
              buffer.readDouble(), buffer.readDouble(), buffer.readVarInt())));
    }
    return new DnvAirRouteValidation.Result(
        status, reason, segments, sampleCount, sampleBudget);
  }

  private static void writeContext(
      FriendlyByteBuf buffer, DnvTacticalRouteProtocol.RequestContext context) {
    buffer.writeUUID(context.requestId());
    buffer.writeVarLong(context.sequence());
  }

  private static DnvTacticalRouteProtocol.RequestContext readContext(
      FriendlyByteBuf buffer) {
    return new DnvTacticalRouteProtocol.RequestContext(
        buffer.readUUID(), buffer.readVarLong());
  }

  private static void writeReference(
      FriendlyByteBuf buffer, DnvTacticalRouteDrafts.DraftRef reference) {
    buffer.writeUUID(reference.coreId());
    buffer.writeUUID(reference.draftId());
  }

  private static DnvTacticalRouteDrafts.DraftRef readReference(
      FriendlyByteBuf buffer) {
    return new DnvTacticalRouteDrafts.DraftRef(
        buffer.readUUID(), buffer.readUUID());
  }

  private static void writeProjection(
      FriendlyByteBuf buffer,
      DnvTacticalRouteDrafts.DraftProjection projection) {
    writeReference(buffer, projection.reference());
    writeSnapshot(buffer, projection.snapshot());
    buffer.writeUtf(projection.binding().dimension(), 256);
    buffer.writeUUID(projection.binding().coreId());
    buffer.writeUUID(projection.binding().vehicleId());
    buffer.writeVarInt(projection.ordinal());
    buffer.writeVarInt(projection.totalDrafts());
  }

  private static DnvTacticalRouteDrafts.DraftProjection readProjection(
      FriendlyByteBuf buffer) {
    DnvTacticalRouteDrafts.DraftRef reference = readReference(buffer);
    DnvTacticalRouteDrafts.Snapshot snapshot = readSnapshot(buffer);
    DnvTacticalRouteDrafts.VehicleBinding binding =
        new DnvTacticalRouteDrafts.VehicleBinding(
            buffer.readUtf(256), buffer.readUUID(), buffer.readUUID());
    return new DnvTacticalRouteDrafts.DraftProjection(
        reference,
        snapshot,
        binding,
        buffer.readVarInt(),
        buffer.readVarInt());
  }

  private static void writeSnapshot(
      FriendlyByteBuf buffer, DnvTacticalRouteDrafts.Snapshot snapshot) {
    buffer.writeVarLong(snapshot.revision());
    buffer.writeUtf(snapshot.dimension(), 256);
    writeWaypoints(buffer, snapshot.waypoints());
  }

  private static DnvTacticalRouteDrafts.Snapshot readSnapshot(
      FriendlyByteBuf buffer) {
    return new DnvTacticalRouteDrafts.Snapshot(
        buffer.readVarLong(), buffer.readUtf(256), readWaypoints(buffer));
  }

  private static void writeWaypoints(
      FriendlyByteBuf buffer, List<DnvRouteWaypoint> waypoints) {
    buffer.writeVarInt(waypoints.size());
    for (DnvRouteWaypoint waypoint : waypoints) {
      buffer.writeDouble(waypoint.x());
      buffer.writeDouble(waypoint.y());
      buffer.writeDouble(waypoint.z());
    }
  }

  private static List<DnvRouteWaypoint> readWaypoints(
      FriendlyByteBuf buffer) {
    int count = buffer.readVarInt();
    if (count < 0 || count > DnvRouteWaypoint.MAX_WAYPOINTS) {
      throw new IllegalArgumentException("route waypoint count is outside protocol bounds");
    }
    List<DnvRouteWaypoint> waypoints = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      waypoints.add(new DnvRouteWaypoint(
          buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }
    return List.copyOf(waypoints);
  }
}
