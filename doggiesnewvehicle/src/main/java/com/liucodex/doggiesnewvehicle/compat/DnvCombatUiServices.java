package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.core.DnvEntityDesignationSession;
import com.liucodex.doggiesnewvehicle.mission.DnvCombatUi;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Server adapter for the F1 page. It projects and submits intent but never authorizes fire. */
final class DnvCombatUiServices {
  private DnvCombatUiServices() {}

  static void handle(ServerPlayer player, DnvCombatUi.Request request) {
    DnvVehicleCrewServices.synchronizeTracked(player.server);
    ResolutionOutcome outcome = resolve(player, request.selection());
    if (outcome.resolution() == null) {
      sendUnavailable(player, request, outcome.reason());
      return;
    }
    Resolution resolution = outcome.resolution();
    VehicleEntity vehicle = resolution.vehicle();
    DnvVehicleCrewAttachment attachment = resolution.attachment();
    DnvEntityDesignationSession.View observed = DnvEntityDesignationServices.observe(vehicle, attachment);
    String result = "NONE";
    if (request.action() != DnvCombatUi.Action.REFRESH) {
      DnvEntityDesignationSession.Result submitted = submit(player, vehicle, attachment, request);
      observed = submitted.view();
      result = submitted.accepted() ? "NONE" : submitted.reason();
    }
    DnvCombatUiNetwork.send(player, project(request, resolution, observed, result));
  }

  private static DnvEntityDesignationSession.Result submit(
      ServerPlayer player, VehicleEntity vehicle, DnvVehicleCrewAttachment attachment,
      DnvCombatUi.Request request) {
    return switch (request.action()) {
      case DESIGNATE_ENTITY -> {
        DnvCombatUi.TargetIdentity target = request.target().orElseThrow();
        if (!target.dimension().equals(request.selection().dimension())) {
          yield rejected(vehicle, attachment, request.operationId(), "DIMENSION_MISMATCH");
        }
        yield DnvEntityDesignationServices.submitEntity(
            player, vehicle, attachment, target.entityId(), request.operationId(),
            request.expectedOrderRevision());
      }
      case CANCEL -> DnvEntityDesignationServices.observe(vehicle, attachment)
          .orders().activeOrder().isEmpty()
              ? rejected(vehicle, attachment, request.operationId(), "NO_ACTIVE_INTENT")
              : DnvEntityDesignationServices.cancel(
                  player, vehicle, attachment, request.operationId(), request.expectedOrderRevision());
      case REFRESH -> throw new IllegalStateException("refresh has no mutation");
    };
  }

  private static DnvEntityDesignationSession.Result rejected(
      VehicleEntity vehicle, DnvVehicleCrewAttachment attachment, UUID operationId, String reason) {
    return new DnvEntityDesignationSession.Result(
        false, reason, operationId, DnvEntityDesignationServices.observe(vehicle, attachment));
  }

  static ResolutionOutcome resolve(
      ServerPlayer player, DnvManagedVehicleDirectory.Selection selection) {
    VehicleEntity vehicle = DnvVehicleCrewServices
        .trackedVehicle(player.server, selection.vehicleId()).orElse(null);
    if (vehicle == null || vehicle.isRemoved() || vehicle.isWreck()) {
      return ResolutionOutcome.rejected("PLATFORM_UNAVAILABLE");
    }
    if (!(vehicle.level() instanceof ServerLevel level)
        || level != player.serverLevel()
        || !level.dimension().location().toString().equals(selection.dimension())) {
      return ResolutionOutcome.rejected("DIMENSION_MISMATCH");
    }
    if (!level.hasChunkAt(vehicle.blockPosition())) {
      return ResolutionOutcome.rejected("PLATFORM_UNAVAILABLE");
    }
    DnvVehicleCrewAttachment attachment = vehicle
        .getCapability(DnvVehicleCrewCapability.CAPABILITY).resolve().orElse(null);
    if (attachment == null) return ResolutionOutcome.rejected("PLATFORM_UNAVAILABLE");
    attachment.synchronize(DnvVehicleCrewServices.sbwCatalog());
    DnvVehicleCrewAttachment.ManagedState state = attachment.managedState().orElse(null);
    if (state == null) return ResolutionOutcome.rejected("PLATFORM_UNAVAILABLE");
    if (!state.coreId().equals(selection.coreId())
        || state.coreRevision() != selection.coreRevision()) {
      return ResolutionOutcome.rejected("CORE_CHANGED");
    }
    if (!DnvEntityDesignationServices.mayOperate(player, vehicle, state.ownerId())) {
      return ResolutionOutcome.rejected("PERMISSION_DENIED");
    }
    return new ResolutionOutcome(new Resolution(vehicle, attachment, state), "NONE");
  }

  private static DnvCombatUi.View project(
      DnvCombatUi.Request request, Resolution resolution,
      DnvEntityDesignationSession.View view, String result) {
    VehicleEntity vehicle = resolution.vehicle();
    ServerLevel level = (ServerLevel) vehicle.level();
    return new DnvCombatUi.View(
        request.requestId(), request.sequence(), request.selection(), result,
        view.orders().revision(), level.getGameTime(),
        List.of(new DnvCombatUi.Capability(
            true, true, "NONE")),
        targets(vehicle, resolution.state().ownerId()), order(view));
  }

  private static List<DnvCombatUi.Target> targets(VehicleEntity vehicle, UUID owner) {
    ServerLevel level = (ServerLevel) vehicle.level();
    List<Entity> nearby = level.getEntities(
        vehicle, vehicle.getBoundingBox().inflate(DnvCombatPolicyRuntime.CREW_TARGET_RANGE),
        candidate -> candidate != vehicle && !candidate.isRemoved() && candidate.isAlive());
    return DnvTargetCandidateCache.boundedTopK(
        nearby, ignored -> true, vehicle::distanceToSqr, Entity::getUUID, DnvCombatUi.MAX_TARGETS)
        .stream().map(target -> {
          boolean pvp = DnvCombatPolicyRuntime.isPvpProtected(target);
          boolean eligible = !pvp
              && DnvCombatPolicyRuntime.crewDesignationAllowed(vehicle, target, owner);
          String issue = pvp ? "PVP_FORBIDDEN" : eligible ? "NONE" : "TARGET_INVALID";
          String label = target.getDisplayName().getString();
          if (label.isBlank()) label = target.getUUID().toString();
          if (label.length() > DnvCombatUi.MAX_TEXT) {
            label = label.substring(0, DnvCombatUi.MAX_TEXT);
          }
          return new DnvCombatUi.Target(
              new DnvCombatUi.TargetIdentity(level.dimension().location().toString(), target.getUUID()),
              label, eligible, issue);
        }).toList();
  }

  private static Optional<DnvCombatUi.Order> order(DnvEntityDesignationSession.View view) {
    return view.displayedOrder().map(order -> {
      DnvCombatUi.Phase phase = phase(view);
      String reason = reason(view, phase);
      return new DnvCombatUi.Order(
          order.orderId(), order.revision(),
          order.targetId().map(id -> new DnvCombatUi.TargetIdentity(order.dimension(), id)),
          view.orders().activeOrder().isPresent(), phase, reason,
          order.expiresGameTime());
    });
  }

  private static DnvCombatUi.Phase phase(DnvEntityDesignationSession.View view) {
    if (view.orders().activeOrder().isPresent()) {
      return DnvCombatUi.Phase.INTENT_ACCEPTED;
    }
    return switch (view.state()) {
      case CANCELLED -> DnvCombatUi.Phase.CANCELLED;
      case EXPIRED -> DnvCombatUi.Phase.EXPIRED;
      default -> DnvCombatUi.Phase.REJECTED;
    };
  }

  private static String reason(DnvEntityDesignationSession.View view, DnvCombatUi.Phase phase) {
    if (!"NONE".equals(view.executionReason())) return view.executionReason();
    return phase.name();
  }

  private static void sendUnavailable(
      ServerPlayer player, DnvCombatUi.Request request, String reason) {
    DnvCombatUiNetwork.send(player, new DnvCombatUi.View(
        request.requestId(), request.sequence(), request.selection(), reason,
        request.expectedOrderRevision(), Math.max(0L, player.level().getGameTime()),
        List.of(), List.of(), Optional.empty()));
  }

  record Resolution(
      VehicleEntity vehicle, DnvVehicleCrewAttachment attachment,
      DnvVehicleCrewAttachment.ManagedState state) {}

  record ResolutionOutcome(Resolution resolution, String reason) {
    private static ResolutionOutcome rejected(String reason) {
      return new ResolutionOutcome(null, reason);
    }
  }
}
