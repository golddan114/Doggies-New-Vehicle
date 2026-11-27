package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.mission.DnvCombatUi;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;

/** Strict bounded codec for the separate combat UI channel. */
final class DnvCombatUiCodec {
  private DnvCombatUiCodec() {}

  static void encodeRequest(DnvCombatUi.Request request, FriendlyByteBuf buffer) {
    buffer.writeUUID(request.requestId());
    buffer.writeVarLong(request.sequence());
    writeSelection(request.selection(), buffer);
    buffer.writeVarLong(request.expectedOrderRevision());
    buffer.writeUUID(request.operationId());
    buffer.writeEnum(request.action());
    writeOptionalTarget(request.target(), buffer);
  }

  static DnvCombatUi.Request decodeRequest(FriendlyByteBuf buffer) {
    DnvCombatUi.Request request = new DnvCombatUi.Request(
        buffer.readUUID(), buffer.readVarLong(), readSelection(buffer), buffer.readVarLong(),
        buffer.readUUID(), buffer.readEnum(DnvCombatUi.Action.class), readOptionalTarget(buffer));
    if (buffer.isReadable()) throw new IllegalArgumentException("unexpected combat request payload");
    return request;
  }

  static void encodeView(DnvCombatUi.View view, FriendlyByteBuf buffer) {
    buffer.writeUUID(view.requestId());
    buffer.writeVarLong(view.sequence());
    writeSelection(view.selection(), buffer);
    buffer.writeUtf(view.result(), 96);
    buffer.writeVarLong(view.orderRevision());
    buffer.writeVarLong(view.serverGameTime());
    buffer.writeVarInt(view.capabilities().size());
    for (DnvCombatUi.Capability capability : view.capabilities()) {
      buffer.writeBoolean(capability.entityDesignation());
      buffer.writeBoolean(capability.ready());
      buffer.writeUtf(capability.issue(), 96);
    }
    buffer.writeVarInt(view.targets().size());
    for (DnvCombatUi.Target target : view.targets()) {
      writeTarget(target.identity(), buffer);
      buffer.writeUtf(target.label(), DnvCombatUi.MAX_TEXT);
      buffer.writeBoolean(target.eligible());
      buffer.writeUtf(target.issue(), 96);
    }
    buffer.writeBoolean(view.order().isPresent());
    view.order().ifPresent(order -> writeOrder(order, buffer));
  }

  static DnvCombatUi.View decodeView(FriendlyByteBuf buffer) {
    java.util.UUID requestId = buffer.readUUID();
    long sequence = buffer.readVarLong();
    DnvManagedVehicleDirectory.Selection selection = readSelection(buffer);
    String result = buffer.readUtf(96);
    long revision = buffer.readVarLong();
    long gameTime = buffer.readVarLong();
    int capabilityCount = boundedCount(buffer.readVarInt(), DnvCombatUi.MAX_CAPABILITIES, "capability");
    List<DnvCombatUi.Capability> capabilities = new ArrayList<>();
    for (int index = 0; index < capabilityCount; index++) {
      capabilities.add(new DnvCombatUi.Capability(
          buffer.readBoolean(), buffer.readBoolean(),
          buffer.readUtf(96)));
    }
    int targetCount = boundedCount(buffer.readVarInt(), DnvCombatUi.MAX_TARGETS, "target");
    List<DnvCombatUi.Target> targets = new ArrayList<>();
    for (int index = 0; index < targetCount; index++) {
      targets.add(new DnvCombatUi.Target(
          readTarget(buffer), buffer.readUtf(DnvCombatUi.MAX_TEXT), buffer.readBoolean(),
          buffer.readUtf(96)));
    }
    Optional<DnvCombatUi.Order> order = buffer.readBoolean()
        ? Optional.of(readOrder(buffer)) : Optional.empty();
    return new DnvCombatUi.View(requestId, sequence, selection, result, revision, gameTime,
        capabilities, targets, order);
  }

  private static void writeOrder(DnvCombatUi.Order order, FriendlyByteBuf buffer) {
    buffer.writeUUID(order.orderId());
    buffer.writeVarLong(order.revision());
    writeOptionalTarget(order.target(), buffer);
    buffer.writeBoolean(order.cancellable());
    buffer.writeEnum(order.phase());
    buffer.writeUtf(order.reason(), 96);
    buffer.writeVarLong(order.expiresGameTime());
  }

  private static DnvCombatUi.Order readOrder(FriendlyByteBuf buffer) {
    return new DnvCombatUi.Order(
        buffer.readUUID(), buffer.readVarLong(), readOptionalTarget(buffer),
        buffer.readBoolean(), buffer.readEnum(DnvCombatUi.Phase.class),
        buffer.readUtf(96), buffer.readVarLong());
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

  private static void writeOptionalTarget(
      Optional<DnvCombatUi.TargetIdentity> target, FriendlyByteBuf buffer) {
    buffer.writeBoolean(target.isPresent());
    target.ifPresent(value -> writeTarget(value, buffer));
  }

  private static Optional<DnvCombatUi.TargetIdentity> readOptionalTarget(FriendlyByteBuf buffer) {
    return buffer.readBoolean() ? Optional.of(readTarget(buffer)) : Optional.empty();
  }

  private static void writeTarget(DnvCombatUi.TargetIdentity target, FriendlyByteBuf buffer) {
    buffer.writeUtf(target.dimension(), 256);
    buffer.writeUUID(target.entityId());
  }

  private static DnvCombatUi.TargetIdentity readTarget(FriendlyByteBuf buffer) {
    return new DnvCombatUi.TargetIdentity(buffer.readUtf(256), buffer.readUUID());
  }

  private static int boundedCount(int value, int maximum, String label) {
    if (value < 0 || value > maximum) {
      throw new IllegalArgumentException("combat " + label + " count is out of bounds");
    }
    return value;
  }
}
