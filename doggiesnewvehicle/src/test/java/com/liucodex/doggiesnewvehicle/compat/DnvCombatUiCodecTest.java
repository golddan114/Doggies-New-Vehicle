package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.mission.DnvCombatUi;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class DnvCombatUiCodecTest {
  @Test
  void designationCancellationAndRefreshRoundTripWithStableIdentity() {
    var target = new DnvCombatUi.TargetIdentity("minecraft:overworld", UUID.randomUUID());
    var requests = List.of(
        new DnvCombatUi.Request(
            UUID.randomUUID(), 1, selection(), 5, UUID.randomUUID(), DnvCombatUi.Action.DESIGNATE_ENTITY,
            Optional.of(target)),
        new DnvCombatUi.Request(
            UUID.randomUUID(), 2, selection(), 6, UUID.randomUUID(), DnvCombatUi.Action.CANCEL,
            Optional.empty()),
        new DnvCombatUi.Request(
            UUID.randomUUID(), 3, selection(), 6, UUID.randomUUID(), DnvCombatUi.Action.REFRESH,
            Optional.empty()));
    for (var request : requests) {
      var buffer = new FriendlyByteBuf(Unpooled.buffer());
      try {
        DnvCombatUiCodec.encodeRequest(request, buffer);
        assertEquals(request, DnvCombatUiCodec.decodeRequest(buffer));
        assertFalse(buffer.isReadable());
      } finally {
        buffer.release();
      }
    }
  }

  @Test
  void designationCapabilitiesTargetsAndCancellationRoundTrip() {
    var selection = selection();
    var identity = new DnvCombatUi.TargetIdentity(selection.dimension(), UUID.randomUUID());
    var order = new DnvCombatUi.Order(
        UUID.randomUUID(), 9, Optional.of(identity), false, DnvCombatUi.Phase.CANCELLED, "CANCELLED", 800);
    var expected = new DnvCombatUi.View(
        UUID.randomUUID(), 4, selection, "NONE", 9, 201,
        List.of(new DnvCombatUi.Capability(false, true, "NONE")),
        List.of(new DnvCombatUi.Target(identity, "example", true, "NONE")), Optional.of(order));
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      DnvCombatUiCodec.encodeView(expected, buffer);
      assertEquals(expected, DnvCombatUiCodec.decodeView(buffer));
      assertFalse(buffer.isReadable());
    } finally {
      buffer.release();
    }
  }

  @Test
  void oldHandshakeAndOldPayloadLayoutAreRejected() {
    assertTrue(DnvCombatUiNetwork.acceptsProtocol("2"));
    for (String version : List.of("1", "ABSENT", "ACCEPTVANILLA")) {
      assertFalse(DnvCombatUiNetwork.acceptsProtocol(version));
    }
    var request = new DnvCombatUi.Request(UUID.randomUUID(), 1, selection(), 0,
        UUID.randomUUID(), DnvCombatUi.Action.CANCEL, Optional.empty());
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      DnvCombatUiCodec.encodeRequest(request, buffer);
      // Old layout also carried point, station and weapon payloads.
      buffer.writeBoolean(false);
      buffer.writeUtf("");
      buffer.writeUtf("");
      assertThrows(IllegalArgumentException.class, () -> DnvCombatUiCodec.decodeRequest(buffer));
    } finally {
      buffer.release();
    }
  }

  @Test
  void malformedListCountsAreRejectedBeforeAllocation() {
    for (int count : new int[] {-1, DnvCombatUi.MAX_CAPABILITIES + 1, Integer.MAX_VALUE}) {
      var buffer = viewPrefix();
      try {
        buffer.writeVarInt(count);
        assertThrows(IllegalArgumentException.class, () -> DnvCombatUiCodec.decodeView(buffer));
      } finally {
        buffer.release();
      }
    }
    for (int count : new int[] {-1, DnvCombatUi.MAX_TARGETS + 1, Integer.MAX_VALUE}) {
      var buffer = viewPrefix();
      try {
        buffer.writeVarInt(0);
        buffer.writeVarInt(count);
        assertThrows(IllegalArgumentException.class, () -> DnvCombatUiCodec.decodeView(buffer));
      } finally {
        buffer.release();
      }
    }
  }

  private static FriendlyByteBuf viewPrefix() {
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    var selection = selection();
    buffer.writeUUID(UUID.randomUUID());
    buffer.writeVarLong(1);
    buffer.writeUUID(selection.vehicleId());
    buffer.writeUtf(selection.dimension(), 256);
    buffer.writeUUID(selection.coreId());
    buffer.writeVarLong(selection.coreRevision());
    buffer.writeUtf("NONE", 96);
    buffer.writeVarLong(0);
    buffer.writeVarLong(0);
    return buffer;
  }

  private static DnvManagedVehicleDirectory.Selection selection() {
    return new DnvManagedVehicleDirectory.Selection(
        UUID.randomUUID(), "minecraft:overworld", UUID.randomUUID(), 7);
  }
}
