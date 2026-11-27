package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.core.DnvCombatOrderService;
import com.liucodex.doggiesnewvehicle.core.DnvEntityDesignationSession;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Live server adaptation for the core-bound intent owner; never fires or spends ammunition. */
final class DnvEntityDesignationServices {
  private static final int DESIGNATION_TTL = 600;

  private DnvEntityDesignationServices() {}

  private static boolean externalControl(VehicleEntity vehicle) {
    DnvCombatCoordinator.assignedTarget(vehicle);
    return DnvCombatCoordinator.hasLiveLease(vehicle);
  }

  static boolean mayOperate(ServerPlayer player, VehicleEntity vehicle, UUID owner) {
    return DnvEntityDesignationSession.allowsOperator(
        player.getUUID(), owner, player.hasPermissions(2),
        !player.isSpectator() && player.isAlive() && !vehicle.isRemoved() && !vehicle.isWreck()
            && vehicle.level() == player.level(), player.distanceToSqr(vehicle));
  }

  static DnvEntityDesignationSession.View observe(
      VehicleEntity vehicle, DnvVehicleCrewAttachment attachment) {
    DnvEntityDesignationSession session = attachment.combatOrders();
    long now = vehicle.level().getGameTime();
    session.synchronize(attachment.hasWeaponActuator() && !vehicle.isRemoved() && !vehicle.isWreck()
        ? attachment.managedState().map(state -> new DnvEntityDesignationSession.Binding(
            vehicle.getUUID(), state.coreId(), state.coreRevision(), state.ownerId(),
            vehicle.level().dimension().location().toString()))
        : Optional.empty());
    DnvEntityDesignationSession.View view = session.view(now);
    if (view.orders().activeOrder().isEmpty() || !(vehicle.level() instanceof ServerLevel level)) {
      return view;
    }
    DnvCombatOrderService.OrderSnapshot order = view.orders().activeOrder().orElseThrow();
    UUID owner = view.binding().orElseThrow().ownerId();
    ServerPlayer issuer = level.getServer().getPlayerList().getPlayer(order.issuerId());
    Entity target = order.targetId().map(level::getEntity).orElse(null);
    if (issuer == null || !mayOperate(issuer, vehicle, owner)) {
      session.invalidate(DnvEntityDesignationSession.State.ISSUER_UNAVAILABLE, now);
    } else if (externalControl(vehicle)) {
      session.invalidate(DnvEntityDesignationSession.State.EXTERNAL_CONTROL, now);
    } else if (target == null || !level.hasChunkAt(target.blockPosition())
        || !DnvCombatPolicyRuntime.crewDesignationAllowed(vehicle, target, owner)) {
      session.invalidate(DnvEntityDesignationSession.State.TARGET_INVALID, now);
    }
    return session.view(now);
  }

  static DnvEntityDesignationSession.Result submit(
      ServerPlayer player, VehicleEntity vehicle, DnvVehicleCrewAttachment attachment,
      Optional<UUID> targetId) {
    DnvEntityDesignationSession.View view = observe(vehicle, attachment);
    return submit(player, vehicle, attachment, targetId, UUID.randomUUID(),
        view.orders().revision());
  }

  static DnvEntityDesignationSession.Result submitEntity(
      ServerPlayer player, VehicleEntity vehicle, DnvVehicleCrewAttachment attachment,
      UUID targetId, UUID operationId, long expectedRevision) {
    return submit(player, vehicle, attachment, Optional.of(targetId),
        operationId, expectedRevision);
  }

  static DnvEntityDesignationSession.Result cancel(
      ServerPlayer player, VehicleEntity vehicle, DnvVehicleCrewAttachment attachment,
      UUID operationId, long expectedRevision) {
    return submit(player, vehicle, attachment, Optional.empty(),
        operationId, expectedRevision);
  }

  private static DnvEntityDesignationSession.Result submit(
      ServerPlayer player, VehicleEntity vehicle, DnvVehicleCrewAttachment attachment,
      Optional<UUID> targetId, UUID operation, long expectedRevision) {
    attachment.synchronize(DnvVehicleCrewServices.sbwCatalog());
    DnvEntityDesignationSession.View view = observe(vehicle, attachment);
    if (view.binding().isEmpty()) {
      return new DnvEntityDesignationSession.Result(false, "UNAVAILABLE", operation, view);
    }
    DnvEntityDesignationSession.Binding binding = view.binding().orElseThrow();
    if (!mayOperate(player, vehicle, binding.ownerId())) {
      return new DnvEntityDesignationSession.Result(false, "PERMISSION_DENIED", operation, view);
    }
    if (targetId.isPresent() && externalControl(vehicle)) {
      return new DnvEntityDesignationSession.Result(false, "EXTERNAL_CONTROL", operation, view);
    }
    ServerLevel level = player.serverLevel();
    long now = level.getGameTime();
    Entity target = targetId.map(level::getEntity).orElse(null);
    DnvCombatOrderService.TargetKind kind = target instanceof Player
        ? DnvCombatOrderService.TargetKind.PLAYER
        : DnvCombatPolicyRuntime.isPvpProtected(target)
            ? DnvCombatOrderService.TargetKind.PLAYER_OCCUPIED_VEHICLE
            : target instanceof LivingEntity ? DnvCombatOrderService.TargetKind.LIVING
                : target == null ? DnvCombatOrderService.TargetKind.NONE : DnvCombatOrderService.TargetKind.UNKNOWN;
    DnvCombatOrderService.Command canonical = attachment.combatOrders()
        .commandForOperation(operation).orElse(null);
    long issued = canonical == null ? now : canonical.issuedGameTime();
    long expires = canonical == null ? Math.addExact(now, DESIGNATION_TTL) : canonical.expiresGameTime();
    DnvCombatOrderService.Command command = new DnvCombatOrderService.Command(
        operation, operation, player.getUUID(), vehicle.getUUID(), expectedRevision,
        issued, expires, binding.dimension(),
        targetId.isPresent() ? DnvCombatOrderService.OrderType.DESIGNATE_ENTITY
            : DnvCombatOrderService.OrderType.CANCEL_ORDER, targetId);
    return attachment.combatOrders().submit(binding, command,
        new DnvCombatOrderService.ValidationContext(
            now, player.getUUID(), level.dimension().location().toString(), true,
            Math.sqrt(player.distanceToSqr(vehicle)), target != null,
            target != null && level.hasChunkAt(target.blockPosition())
                && (DnvCombatPolicyRuntime.isPvpProtected(target)
                    || DnvCombatPolicyRuntime.crewDesignationAllowed(vehicle, target, binding.ownerId())), kind));
  }
}
