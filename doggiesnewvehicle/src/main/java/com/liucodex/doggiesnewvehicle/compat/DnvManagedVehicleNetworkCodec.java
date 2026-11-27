package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;

/** Bounded wire codec for the loaded vehicle directory. */
final class DnvManagedVehicleNetworkCodec {
  private DnvManagedVehicleNetworkCodec() {}

  static void encode(
      DnvManagedVehicleDirectory.View view, FriendlyByteBuf buffer) {
    buffer.writeUUID(view.requestId());
    buffer.writeBoolean(view.administrator());
    buffer.writeUtf(view.issue(), 96);
    buffer.writeVarInt(view.vehicles().size());
    for (DnvManagedVehicleDirectory.Vehicle vehicle : view.vehicles()) {
      buffer.writeUUID(vehicle.vehicleId());
      buffer.writeUtf(vehicle.vehicleTypeId(), 256);
      buffer.writeUtf(vehicle.dimension(), 256);
      buffer.writeInt(vehicle.x());
      buffer.writeInt(vehicle.y());
      buffer.writeInt(vehicle.z());
      buffer.writeUtf(vehicle.flightMode(), 64);
      buffer.writeUtf(vehicle.runtimeState(), 64);
      buffer.writeBoolean(vehicle.teleportAllowed());
      buffer.writeUUID(vehicle.coreId());
      buffer.writeVarLong(vehicle.coreRevision());
    }
    buffer.writeBoolean(view.detail().isPresent());
    view.detail().ifPresent(detail -> writeDetail(detail, buffer));
  }

  static DnvManagedVehicleDirectory.View decode(FriendlyByteBuf buffer) {
    java.util.UUID requestId = buffer.readUUID();
    boolean administrator = buffer.readBoolean();
    String issue = buffer.readUtf(96);
    int count = buffer.readVarInt();
    if (count < 0 || count > DnvManagedVehicleDirectory.MAX_VEHICLES) {
      throw new IllegalArgumentException("managed vehicle count is out of bounds");
    }
    List<DnvManagedVehicleDirectory.Vehicle> vehicles = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      vehicles.add(new DnvManagedVehicleDirectory.Vehicle(
          buffer.readUUID(), buffer.readUtf(256), buffer.readUtf(256),
          buffer.readInt(), buffer.readInt(), buffer.readInt(),
          buffer.readUtf(64), buffer.readUtf(64),
          buffer.readBoolean(), buffer.readUUID(), buffer.readVarLong()));
    }
    Optional<DnvManagedVehicleDirectory.Detail> detail = buffer.readBoolean()
        ? Optional.of(readDetail(buffer)) : Optional.empty();
    return new DnvManagedVehicleDirectory.View(requestId, administrator, issue, vehicles, detail);
  }

  static void encodeQuery(DnvManagedVehicleDirectory.Query query, FriendlyByteBuf buffer) {
    buffer.writeUUID(query.requestId());
    buffer.writeBoolean(query.selected().isPresent());
    query.selected().ifPresent(selection -> writeSelection(selection, buffer));
  }

  static DnvManagedVehicleDirectory.Query decodeQuery(FriendlyByteBuf buffer) {
    java.util.UUID requestId = buffer.readUUID();
    return new DnvManagedVehicleDirectory.Query(requestId,
        buffer.readBoolean() ? Optional.of(readSelection(buffer)) : Optional.empty());
  }

  private static void writeSelection(
      DnvManagedVehicleDirectory.Selection selection, FriendlyByteBuf buffer) {
    buffer.writeUUID(selection.vehicleId());
    buffer.writeUtf(selection.dimension(), 256);
    buffer.writeUUID(selection.coreId());
    buffer.writeVarLong(selection.coreRevision());
  }

  private static DnvManagedVehicleDirectory.Selection readSelection(FriendlyByteBuf buffer) {
    return new DnvManagedVehicleDirectory.Selection(
        buffer.readUUID(), buffer.readUtf(256), buffer.readUUID(), buffer.readVarLong());
  }

  private static void writeDetail(
      DnvManagedVehicleDirectory.Detail detail, FriendlyByteBuf buffer) {
    writeSelection(detail.identity(), buffer);
    buffer.writeEnum(detail.status());
    buffer.writeBoolean(detail.routeSupported());
    buffer.writeVarLong(detail.sampleAgeTicks());
    buffer.writeVarInt(detail.weapons().size());
    for (DnvManagedVehicleDirectory.Weapon weapon : detail.weapons()) {
      buffer.writeUtf(weapon.stationId(), 160);
      buffer.writeUtf(weapon.weaponName(), 160);
      buffer.writeUtf(weapon.controlOwner(), 64);
      buffer.writeUtf(weapon.gateReason(), 64);
      buffer.writeVarInt(weapon.ammoCount());
      buffer.writeBoolean(weapon.canShoot());
      buffer.writeVarInt(weapon.cooldownTicks());
    }
  }

  private static DnvManagedVehicleDirectory.Detail readDetail(FriendlyByteBuf buffer) {
    DnvManagedVehicleDirectory.Selection identity = readSelection(buffer);
    DnvManagedVehicleDirectory.DetailStatus status =
        buffer.readEnum(DnvManagedVehicleDirectory.DetailStatus.class);
    boolean routeSupported = buffer.readBoolean();
    long age = buffer.readVarLong();
    int count = buffer.readVarInt();
    if (count < 0 || count > DnvManagedVehicleDirectory.MAX_WEAPONS) {
      throw new IllegalArgumentException("vehicle weapon count is out of bounds");
    }
    List<DnvManagedVehicleDirectory.Weapon> weapons = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      weapons.add(new DnvManagedVehicleDirectory.Weapon(
          buffer.readUtf(160), buffer.readUtf(160), buffer.readUtf(64), buffer.readUtf(64),
          buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt()));
    }
    return new DnvManagedVehicleDirectory.Detail(identity, status, routeSupported, age, weapons);
  }
}
