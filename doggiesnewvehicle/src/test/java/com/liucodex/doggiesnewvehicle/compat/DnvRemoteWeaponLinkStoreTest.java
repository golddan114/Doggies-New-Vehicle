package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.*;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class DnvRemoteWeaponLinkStoreTest {
  @Test void receiptWindowEvictionAfterSaveRejectsOldOperationWithoutChangingLink() {
    var store = new DnvRemoteWeaponLinkStore(); UUID station = UUID.randomUUID(), actor = UUID.randomUUID();
    store.create(station, actor, "minecraft:overworld", 10);
    var original = new Request(actor, UUID.randomUUID(), station, 0, Action.LINK,
        Optional.of(new Target(UUID.randomUUID(), "test:manual_target", "minecraft:overworld")));
    assertTrue(store.apply(original, Reason.NONE).accepted());
    var unchanged = store.station(station).orElseThrow();
    for (int i=0;i<com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.MAX_RECEIPTS;i++) {
      assertEquals(Reason.STALE_REVISION,store.apply(new Request(actor,UUID.randomUUID(),station,0,Action.UNLINK,Optional.empty()),Reason.NONE).reason());
    }
    assertFalse(store.snapshot().receipts().stream().anyMatch(receipt -> receipt.request().equals(original)));
    var restored = DnvRemoteWeaponLinkStore.load(store.save(new CompoundTag()));
    var rejected = restored.apply(original,Reason.NONE);
    assertFalse(rejected.accepted()); assertFalse(rejected.replayed()); assertEquals(Reason.STALE_REVISION,rejected.reason());
    assertEquals(unchanged,restored.station(station).orElseThrow());
    restored = DnvRemoteWeaponLinkStore.load(restored.save(new CompoundTag()));
    var replay = restored.apply(original,Reason.NONE);
    assertTrue(replay.replayed()); assertFalse(replay.accepted()); assertEquals(Reason.STALE_REVISION,replay.reason());
    assertEquals(unchanged,restored.station(station).orElseThrow());
  }
  @Test void nbtRoundTripRetainsOwnerTargetRevisionAndReceipt() {
    var store = new DnvRemoteWeaponLinkStore();
    UUID station = UUID.randomUUID(), actor = UUID.randomUUID();
    store.create(station, actor, "minecraft:overworld", 10);
    var request = new Request(actor, UUID.randomUUID(), station, 0, Action.LINK,
        Optional.of(new Target(UUID.randomUUID(), "test:manual_target", "minecraft:overworld")));
    assertTrue(store.apply(request, Reason.NONE).accepted());
    var loaded = DnvRemoteWeaponLinkStore.load(store.save(new CompoundTag()));
    assertTrue(loaded.writable());
    assertEquals(store.snapshot(), loaded.snapshot());
    assertTrue(loaded.apply(request, Reason.NONE).replayed());
  }

  @Test void futureAndMalformedSchemasPreserveOriginalDataAndRejectWrites() {
    CompoundTag tag = new CompoundTag();
    tag.putInt("Schema", 2); tag.putString("Future", "retain-me");
    assertPreserved(tag);
    tag.putInt("Schema", 1);
    assertPreserved(tag);
    tag.put("Stations", new ListTag()); tag.put("Receipts", new ListTag());
    ListTag invalid = new ListTag(); invalid.add(new CompoundTag()); tag.put("Stations", invalid);
    assertPreserved(tag);
  }

  @Test void commandTreeRetainsConfirmedLinkRecoveryWithoutRetiredControlLease() {
    var dispatcher = new CommandDispatcher<CommandSourceStack>();
    DnvRemoteWeaponCommands.register(dispatcher);
    var rws = dispatcher.getRoot().getChild("dnv").getChild("rws");
    assertNotNull(rws.getChild("confirm").getChild("operation").getCommand());
    var pos = rws.getChild("position");
    assertNotNull(pos.getChild("create").getCommand());
    assertNotNull(pos.getChild("status").getCommand());
    assertNotNull(pos.getChild("prepare").getChild("replace").getChild("target").getCommand());
    assertNotNull(pos.getChild("prepare").getChild("unlink").getCommand());
    assertNotNull(pos.getChild("prepare").getChild("retire").getCommand());
    assertNull(pos.getChild("fire")); assertNull(pos.getChild("claim"));
    for (String action : java.util.List.of("heartbeat", "release")) {
      assertNull(rws.getChild(action));
    }
  }

  private static void assertPreserved(CompoundTag tag) {
    var store = DnvRemoteWeaponLinkStore.load(tag);
    assertFalse(store.writable());
    assertEquals(tag, store.save(new CompoundTag()));
    assertEquals(Reason.STORAGE_INVALID, store.create(UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld", 0));
    assertFalse(store.isDirty());
  }
}
