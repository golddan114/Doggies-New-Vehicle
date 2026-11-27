package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class DnvManagedVehicleNetworkCodecTest {
  @Test
  void selectedIdentityAndWeaponCountersRoundTripWithoutTruncation() {
    var identity = identity();
    var query = new DnvManagedVehicleDirectory.Query(UUID.randomUUID(), Optional.of(identity));
    var vehicle = new DnvManagedVehicleDirectory.Vehicle(
        identity.vehicleId(), "dragonrise_reforge:ac130", identity.dimension(),
        4, 90, -8, "AI_ONLY", "ACTIVE", false, identity.coreId(), identity.coreRevision());
    var weapon = new DnvManagedVehicleDirectory.Weapon("侧炮位", "Cannon105", "AI", "COOLDOWN", 8, true, 12);
    var detail = new DnvManagedVehicleDirectory.Detail(
        identity, DnvManagedVehicleDirectory.DetailStatus.SAMPLED, true, 19L, List.of(weapon));
    var view = new DnvManagedVehicleDirectory.View(
        query.requestId(), false, "NONE", List.of(vehicle), Optional.of(detail));
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      DnvManagedVehicleNetworkCodec.encodeQuery(query, buffer);
      assertEquals(query, DnvManagedVehicleNetworkCodec.decodeQuery(buffer));
      DnvManagedVehicleNetworkCodec.encode(view, buffer);
      assertEquals(view, DnvManagedVehicleNetworkCodec.decode(buffer));
      assertFalse(buffer.isReadable());
    } finally {
      buffer.release();
    }
  }

  @Test
  void unloadedVehicleReplyContainsNoWeaponDataAndRoundTrips() {
    var query = new DnvManagedVehicleDirectory.Query(UUID.randomUUID(), Optional.empty());
    var view = new DnvManagedVehicleDirectory.View(query.requestId(), false, "VEHICLE_NOT_LOADED",
        List.of(), Optional.of(DnvManagedVehicleDetailProjector.unavailable(
            identity(), DnvManagedVehicleDirectory.DetailStatus.NOT_AVAILABLE)));
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      DnvManagedVehicleNetworkCodec.encodeQuery(query, buffer);
      assertEquals(query, DnvManagedVehicleNetworkCodec.decodeQuery(buffer));
      DnvManagedVehicleNetworkCodec.encode(view, buffer);
      assertEquals(view, DnvManagedVehicleNetworkCodec.decode(buffer));
    } finally {
      buffer.release();
    }
  }

  @Test
  void malformedCountsAreRejectedBeforeAllocatingTheirLists() {
    for (int count : new int[] {-1, 65, Integer.MAX_VALUE}) {
      var buffer = new FriendlyByteBuf(Unpooled.buffer());
      try {
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeBoolean(false);
        buffer.writeUtf("NONE");
        buffer.writeVarInt(count);
        assertThrows(IllegalArgumentException.class, () -> DnvManagedVehicleNetworkCodec.decode(buffer));
      } finally {
        buffer.release();
      }
    }
    for (int count : new int[] {-1, 257, Integer.MAX_VALUE}) {
      var buffer = new FriendlyByteBuf(Unpooled.buffer());
      var identity = identity();
      try {
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeBoolean(false);
        buffer.writeUtf("NONE");
        buffer.writeVarInt(0);
        buffer.writeBoolean(true);
        buffer.writeUUID(identity.vehicleId());
        buffer.writeUtf(identity.dimension());
        buffer.writeUUID(identity.coreId());
        buffer.writeVarLong(identity.coreRevision());
        buffer.writeEnum(DnvManagedVehicleDirectory.DetailStatus.SAMPLED);
        buffer.writeBoolean(false);
        buffer.writeVarLong(0L);
        buffer.writeVarInt(count);
        assertThrows(IllegalArgumentException.class, () -> DnvManagedVehicleNetworkCodec.decode(buffer));
      } finally {
        buffer.release();
      }
    }
  }

  @Test
  void invalidCountersAndUnavailableWeaponPayloadAreRejected() {
    var weapon = new DnvManagedVehicleDirectory.Weapon("seat_1", "Cannon40", "AI", "READY", 1, true, 0);
    assertThrows(IllegalArgumentException.class, () -> new DnvManagedVehicleDirectory.Detail(
        identity(), DnvManagedVehicleDirectory.DetailStatus.STALE, false, 41L, List.of(weapon)));
    assertThrows(IllegalArgumentException.class, () -> new DnvManagedVehicleDirectory.Detail(
        identity(), DnvManagedVehicleDirectory.DetailStatus.SAMPLED, false, 0L,
        Collections.nCopies(257, weapon)));
    assertThrows(IllegalArgumentException.class, () -> new DnvManagedVehicleDirectory.Weapon(
        "seat_1", "Cannon40", "AI", "READY", -1, true, 0));
    assertThrows(IllegalArgumentException.class, () -> new DnvManagedVehicleDirectory.Weapon(
        "seat_1", "Cannon40", "AI", "READY", 1, true, -1));
  }

  private static DnvManagedVehicleDirectory.Selection identity() {
    return new DnvManagedVehicleDirectory.Selection(
        UUID.randomUUID(), "minecraft:overworld", UUID.randomUUID(), 3L);
  }
}
