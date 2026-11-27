package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.route.DnvRouteWaypoint;
import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteClientProjection;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteDrafts;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteProtocol;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-owned route draft protocol used by the exact SBW tactical-map extension. */
public final class DnvTacticalRouteNetwork {
  private static final String PROTOCOL = "3";
  private static final SimpleChannel CHANNEL =
      net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
          ResourceLocation.fromNamespaceAndPath(
              DoggiesNewVehicle.MOD_ID, "tactical_route"),
          () -> PROTOCOL,
          PROTOCOL::equals,
          PROTOCOL::equals);
  private static boolean initialized;

  private DnvTacticalRouteNetwork() {}

  public static synchronized void initialize() {
    if (initialized) {
      return;
    }
    CHANNEL.registerMessage(
        0,
        SnapshotRequest.class,
        DnvTacticalRouteNetworkCodec::encodeSnapshotRequest,
        DnvTacticalRouteNetworkCodec::decodeSnapshotRequest,
        DnvTacticalRouteNetwork::handleSnapshotRequest,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        1,
        ActionRequest.class,
        DnvTacticalRouteNetworkCodec::encodeActionRequest,
        DnvTacticalRouteNetworkCodec::decodeActionRequest,
        DnvTacticalRouteNetwork::handleActionRequest,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        2,
        CommitRequest.class,
        DnvTacticalRouteNetworkCodec::encodeCommitRequest,
        DnvTacticalRouteNetworkCodec::decodeCommitRequest,
        DnvTacticalRouteNetwork::handleCommitRequest,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        3,
        DnvTacticalRouteProtocol.RouteResponse.class,
        DnvTacticalRouteNetworkCodec::encodeResponse,
        DnvTacticalRouteNetworkCodec::decodeResponse,
        DnvTacticalRouteNetwork::handleResponse,
        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    initialized = true;
  }

  @OnlyIn(Dist.CLIENT)
  public static void requestSnapshot(
      DnvTacticalRouteProtocol.RequestContext context,
      Optional<DnvTacticalRouteDrafts.DraftRef> reference,
      long expectedRevision) {
    CHANNEL.sendToServer(new SnapshotRequest(context, reference, expectedRevision));
  }

  @OnlyIn(Dist.CLIENT)
  public static void action(
      DnvTacticalRouteProtocol.RequestContext context,
      DnvTacticalRouteProtocol.Action action,
      DnvTacticalRouteDrafts.DraftRef reference,
      long expectedRevision) {
    CHANNEL.sendToServer(new ActionRequest(
        context, action, reference, expectedRevision));
  }

  @OnlyIn(Dist.CLIENT)
  public static void commit(
      DnvTacticalRouteProtocol.RequestContext context,
      DnvTacticalRouteDrafts.DraftRef reference,
      long expectedRevision,
      List<DnvRouteWaypoint> waypoints) {
    CHANNEL.sendToServer(new CommitRequest(
        context, reference, expectedRevision, waypoints));
  }

  public static void respond(
      ServerPlayer player, DnvTacticalRouteProtocol.RouteResponse response) {
    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), response);
  }

  private static void handleSnapshotRequest(
      SnapshotRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    ServerPlayer player = context.getSender();
    if (player != null) {
      context.enqueueWork(() ->
          DnvTacticalRouteServices.request(player, message));
    }
    context.setPacketHandled(true);
  }

  private static void handleActionRequest(
      ActionRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    ServerPlayer player = context.getSender();
    if (player != null) {
      context.enqueueWork(() ->
          DnvTacticalRouteServices.changeDraft(player, message));
    }
    context.setPacketHandled(true);
  }

  private static void handleCommitRequest(
      CommitRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    ServerPlayer player = context.getSender();
    if (player != null) {
      context.enqueueWork(() ->
          DnvTacticalRouteServices.commit(player, message));
    }
    context.setPacketHandled(true);
  }

  private static void handleResponse(
      DnvTacticalRouteProtocol.RouteResponse message,
      Supplier<NetworkEvent.Context> contextSupplier) {
    NetworkEvent.Context context = contextSupplier.get();
    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
        Dist.CLIENT,
        () -> () -> DnvTacticalRouteClientProjection.response(message)));
    context.setPacketHandled(true);
  }

  public record SnapshotRequest(
      DnvTacticalRouteProtocol.RequestContext context,
      Optional<DnvTacticalRouteDrafts.DraftRef> reference,
      long expectedRevision) {
    public SnapshotRequest {
      Objects.requireNonNull(context, "context");
      reference = Objects.requireNonNull(reference, "reference");
      if (expectedRevision < 0L || reference.isEmpty() && expectedRevision != 0L) {
        throw new IllegalArgumentException("invalid tactical route snapshot request");
      }
    }
  }

  public record ActionRequest(
      DnvTacticalRouteProtocol.RequestContext context,
      DnvTacticalRouteProtocol.Action action,
      DnvTacticalRouteDrafts.DraftRef reference,
      long expectedRevision) {
    public ActionRequest {
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(action, "action");
      Objects.requireNonNull(reference, "reference");
      if (expectedRevision < 0L
          || action == DnvTacticalRouteProtocol.Action.SNAPSHOT
          || action == DnvTacticalRouteProtocol.Action.COMMIT) {
        throw new IllegalArgumentException("invalid tactical route action request");
      }
    }
  }

  public record CommitRequest(
      DnvTacticalRouteProtocol.RequestContext context,
      DnvTacticalRouteDrafts.DraftRef reference,
      long expectedRevision,
      List<DnvRouteWaypoint> waypoints) {
    public CommitRequest {
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(reference, "reference");
      waypoints = List.copyOf(waypoints);
      if (expectedRevision < 0L
          || waypoints.size() > DnvRouteWaypoint.MAX_WAYPOINTS) {
        throw new IllegalArgumentException("invalid tactical route commit request");
      }
    }
  }

}
