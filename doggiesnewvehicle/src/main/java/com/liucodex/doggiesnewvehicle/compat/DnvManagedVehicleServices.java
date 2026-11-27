package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

/** Server-authoritative loaded-vehicle directory and administrator teleport actions. */
final class DnvManagedVehicleServices {
  private DnvManagedVehicleServices() {}

  static void sendDirectory(
      ServerPlayer player, DnvManagedVehicleDirectory.Query query, String issue) {
    DnvVehicleCrewServices.synchronizeTracked(player.server);
    boolean administrator = player.hasPermissions(2);
    List<DnvManagedVehicleDirectory.Vehicle> vehicles =
        DnvVehicleCrewServices.trackedVehicles(player.server).stream()
            .filter(vehicle -> !vehicle.isRemoved())
            .map(vehicle -> vehicle.getCapability(DnvVehicleCrewCapability.CAPABILITY)
                .map(DnvVehicleCrewAttachment::managedState)
                .orElse(Optional.empty())
                .map(state -> summary(
                    player, administrator, vehicle, state))
                .orElse(null))
            .filter(Objects::nonNull)
            .sorted(Comparator
                .comparing(DnvManagedVehicleDirectory.Vehicle::dimension)
                .thenComparing(DnvManagedVehicleDirectory.Vehicle::vehicleTypeId)
                .thenComparing(result -> result.vehicleId().toString()))
            .limit(DnvManagedVehicleDirectory.MAX_VEHICLES)
            .toList();
    DnvManagedVehicleNetwork.sendDirectory(
        player, new DnvManagedVehicleDirectory.View(
            query.requestId(), administrator, issue, vehicles,
            query.selected().map(selection -> detail(player, selection))));
  }

  static void teleport(ServerPlayer player, DnvManagedVehicleDirectory.Query query) {
    if (!player.hasPermissions(2)) {
      sendDirectory(player, query, "TELEPORT_PERMISSION_DENIED");
      return;
    }
    if (query.selected().isEmpty()) {
      sendDirectory(player, query, "VEHICLE_NOT_LOADED");
      return;
    }
    UUID vehicleId = query.selected().orElseThrow().vehicleId();
    DnvVehicleCrewServices.synchronizeTracked(player.server);
    VehicleEntity vehicle =
        DnvVehicleCrewServices.trackedVehicle(player.server, vehicleId).orElse(null);
    if (vehicle == null
        || !(vehicle.level() instanceof ServerLevel level)
        || !level.hasChunkAt(vehicle.blockPosition())
        || vehicle.getCapability(DnvVehicleCrewCapability.CAPABILITY)
            .map(DnvVehicleCrewAttachment::managedState)
            .orElse(Optional.empty())
            .isEmpty()) {
      sendDirectory(player, query, "VEHICLE_NOT_LOADED");
      return;
    }
    BlockPos target = safeTeleportTarget(level, vehicle.blockPosition());
    if (target == null || !level.getWorldBorder().isWithinBounds(target)) {
      sendDirectory(player, query, "NO_SAFE_TELEPORT_POSITION");
      return;
    }
    player.stopRiding();
    player.teleportTo(
        level,
        target.getX() + 0.5D,
        target.getY(),
        target.getZ() + 0.5D,
        player.getYRot(),
        player.getXRot());
    sendDirectory(player, query, "TELEPORTED");
  }

  private static DnvManagedVehicleDirectory.Vehicle summary(
      ServerPlayer player,
      boolean administrator,
      VehicleEntity vehicle,
      DnvVehicleCrewAttachment.ManagedState state) {
    UUID ownerId = state.ownerId();
    UUID coreId = state.coreId();
    if (!DnvManagedVehicleDetailProjector.visibleTo(player.getUUID(), administrator, ownerId)) {
      return null;
    }
    String dimension = vehicle.level().dimension().location().toString();
    BlockPos position = vehicle.blockPosition();
    return new DnvManagedVehicleDirectory.Vehicle(
        vehicle.getUUID(),
        Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType()), ""),
        dimension,
        position.getX(), position.getY(), position.getZ(),
        state.flightMode(), state.runtimeState(),
        administrator && vehicle.level() instanceof ServerLevel level
            && level.hasChunkAt(position), coreId, state.coreRevision());
  }

  private static DnvManagedVehicleDirectory.Detail detail(
      ServerPlayer player, DnvManagedVehicleDirectory.Selection requested) {
    VehicleEntity vehicle = DnvVehicleCrewServices
        .trackedVehicle(player.server, requested.vehicleId()).orElse(null);
    if (vehicle == null || vehicle.isRemoved()
        || !(vehicle.level() instanceof ServerLevel level)
        || !level.hasChunkAt(vehicle.blockPosition())
        || !level.dimension().location().toString().equals(requested.dimension())) {
      return DnvManagedVehicleDetailProjector.unavailable(
          requested, DnvManagedVehicleDirectory.DetailStatus.NOT_AVAILABLE);
    }
    DnvVehicleCrewAttachment attachment = vehicle
        .getCapability(DnvVehicleCrewCapability.CAPABILITY).resolve().orElse(null);
    DnvVehicleCrewAttachment.ManagedState state = attachment == null
        ? null : attachment.managedState().orElse(null);
    if (state == null || !DnvManagedVehicleDetailProjector.visibleTo(
        player.getUUID(), player.hasPermissions(2), state.ownerId())) {
      return DnvManagedVehicleDetailProjector.unavailable(
          requested, DnvManagedVehicleDirectory.DetailStatus.NOT_AVAILABLE);
    }
    DnvManagedVehicleDirectory.Selection current = new DnvManagedVehicleDirectory.Selection(
        vehicle.getUUID(), requested.dimension(), state.coreId(), state.coreRevision());
    return DnvManagedVehicleDetailProjector.project(
        requested, current, attachment.hasRouteActuator(),
        attachment.currentWeaponDiagnostics(), level.getGameTime());
  }

  private static BlockPos safeTeleportTarget(
      ServerLevel level, BlockPos vehiclePosition) {
    BlockPos start = vehiclePosition.offset(2, 1, 2);
    for (int offset = 0; offset <= 5; offset++) {
      BlockPos feet = start.above(offset);
      if (!level.hasChunkAt(feet)) {
        return null;
      }
      BlockPos head = feet.above();
      if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
          && level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
        return feet;
      }
    }
    return null;
  }
}
