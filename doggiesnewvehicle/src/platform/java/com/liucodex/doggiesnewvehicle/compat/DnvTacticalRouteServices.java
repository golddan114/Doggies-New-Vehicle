package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.mission.DnvAirRouteValidation;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteDrafts;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteProtocol;
import com.liucodex.doggiesnewvehicle.platform.DnvRouteDraftServices;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread coordinator for route packets; platform owns the draft state. */
public final class DnvTacticalRouteServices {
  @FunctionalInterface
  public interface PlatformValidation {
    DnvAirRouteValidation.Result validate(
        ServerPlayer player, DnvTacticalRouteDrafts.DraftProjection projection);
  }

  private static final PlatformValidation NO_PLATFORM = (player, projection) ->
      DnvAirRouteValidation.configurationUnavailable(
          projection.snapshot().waypoints(),
          DnvRouteDraftServices.bounds(player.serverLevel()));
  private static volatile PlatformValidation platformValidation = NO_PLATFORM;

  private DnvTacticalRouteServices() {}

  public static synchronized void installPlatformValidation(PlatformValidation validation) {
    if (platformValidation != NO_PLATFORM) {
      throw new IllegalStateException("platform route validation already installed");
    }
    platformValidation = Objects.requireNonNull(validation, "validation");
  }

  static void request(
      ServerPlayer player, DnvTacticalRouteNetwork.SnapshotRequest request) {
    DnvTacticalRouteDrafts routes =
        DnvRouteDraftServices.routes(player.server);
    String dimension = DnvRouteDraftServices.dimension(player);
    long gameTime = player.server.getTickCount();
    if (request.reference().isPresent()) {
      DnvTacticalRouteDrafts.OperationResult result = routes.select(
          player.getUUID(),
          request.reference().orElseThrow(),
          request.context().requestId(),
          request.expectedRevision(),
          dimension,
          gameTime);
      DnvTacticalRouteNetwork.respond(player, response(
          player,
          request.context(),
          DnvTacticalRouteProtocol.Action.SNAPSHOT,
          result));
      return;
    }
    Optional<DnvTacticalRouteDrafts.DraftProjection> projection =
        routes.active(player.getUUID(), dimension, gameTime);
    DnvTacticalRouteNetwork.respond(
        player,
        DnvTacticalRouteProtocol.RouteResponse.snapshot(
            request.context(),
            projection,
            validation(player, projection)));
  }

  static void changeDraft(
      ServerPlayer player, DnvTacticalRouteNetwork.ActionRequest request) {
    DnvTacticalRouteDrafts routes =
        DnvRouteDraftServices.routes(player.server);
    long gameTime = player.server.getTickCount();
    String dimension = DnvRouteDraftServices.dimension(player);
    DnvTacticalRouteDrafts.OperationResult result = switch (request.action()) {
      case SELECT -> routes.select(
          player.getUUID(), request.reference(), request.context().requestId(),
          request.expectedRevision(), dimension, gameTime);
      case CREATE -> routes.create(
          player.getUUID(), request.reference(), request.context().requestId(),
          request.expectedRevision(), dimension, gameTime);
      case CYCLE -> routes.cycle(
          player.getUUID(), request.reference(), request.context().requestId(),
          request.expectedRevision(), dimension, gameTime);
      case SNAPSHOT, COMMIT -> throw new IllegalArgumentException(
          "invalid tactical route action");
    };
    DnvTacticalRouteNetwork.respond(
        player, response(player, request.context(), request.action(), result));
  }

  static void commit(
      ServerPlayer player, DnvTacticalRouteNetwork.CommitRequest request) {
    DnvTacticalRouteDrafts.OperationResult result =
        DnvRouteDraftServices.routes(player.server).commit(
            player.getUUID(),
            request.reference(),
            request.context().requestId(),
            request.expectedRevision(),
            DnvRouteDraftServices.dimension(player),
            request.waypoints(),
            DnvRouteDraftServices.bounds(player.serverLevel()),
            player.server.getTickCount());
    DnvTacticalRouteNetwork.respond(
        player, response(
            player,
            request.context(),
            DnvTacticalRouteProtocol.Action.COMMIT,
            result));
  }

  private static DnvTacticalRouteProtocol.RouteResponse response(
      ServerPlayer player,
      DnvTacticalRouteProtocol.RequestContext context,
      DnvTacticalRouteProtocol.Action action,
      DnvTacticalRouteDrafts.OperationResult result) {
    return DnvTacticalRouteProtocol.RouteResponse.from(
        context, action, result, validation(player, result.projection()));
  }

  private static Optional<DnvAirRouteValidation.Result> validation(
      ServerPlayer player,
      Optional<DnvTacticalRouteDrafts.DraftProjection> projection) {
    return projection.map(current -> platformValidation.validate(player, current));
  }
}
