package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.*;
import com.liucodex.doggiesnewvehicle.entity.DnvM1YunlongEntity;
import com.liucodex.doggiesnewvehicle.init.DnvM1Content;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Production BlockItem, block callbacks, BE, chat links, M1 and SavedData in actual ServerLevel. */
@GameTestHolder("dnvrwsgametest")
@PrefixGameTestTemplate(false)
public final class DnvRemoteWeaponStationGameTests {
  @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
  public static void placedStationNativeEntryAndDiagnosticTransactions(GameTestHelper helper) {
    try (Fixture f = new Fixture(helper)) {
      check(BuiltInRegistries.BLOCK.getKey(f.level.getBlockState(f.pos).getBlock()).equals(DnvRemoteWeaponStationContent.ID), "registered block");
      check(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(f.anchor().getType()).equals(DnvRemoteWeaponStationContent.ID), "registered BE");
      check(f.station().owner().equals(f.player.getUUID()), "placement creates ownership");
      f.click();
      String link = f.prepare("link ");
      check(f.command(link + f.target.getUUID()) == 1, "owner uses diagnostic prepare command");
      String confirm = f.suggestion("/dnv rws confirm ");
      check(f.command(confirm) == 1 && f.command(confirm) == 1, "confirm and idempotent replay from actual feedback");
      check(f.station().revision() == 1 && f.station().target().orElseThrow().id().equals(f.target.getUUID()), "single committed link");
      var next = spawn(f.level, f.pos.offset(3, 0, 0), f.player.getUUID());
      try {
        check(f.command(f.prepare("replace ") + next.getUUID()) == 1, "owner diagnostic replace");
        confirm = f.suggestion("/dnv rws confirm ");
        CompoundTag revoked = next.saveWithoutId(new CompoundTag());
        revoked.getCompound("M1Ownership").putUUID("Owner", UUID.randomUUID()); next.load(revoked);
        check(f.command(confirm) == 0, "revoked target rejected on final confirm");
        check(f.station().target().orElseThrow().id().equals(f.target.getUUID()), "failed replace keeps old link");
        revoked.getCompound("M1Ownership").putUUID("Owner", f.player.getUUID()); next.load(revoked);
        f.command(f.prepare("replace ") + next.getUUID());
        check(f.command(f.suggestion("/dnv rws confirm ")) == 1, "atomic replace confirmed");
        check(f.station().revision() == 2 && f.station().target().orElseThrow().id().equals(next.getUUID()), "new link only after success");
        check(f.command(f.prepare("unlink")) == 1, "owner diagnostic unlink prepare");
        check(f.command(f.suggestion("/dnv rws confirm ")) == 1 && f.station().target().isEmpty(), "unlink confirmed");
        check(f.messages.stream().anyMatch(message -> message.getString().contains("回执重放")), "player sees replay receipt");
      } finally { next.discard(); }
      helper.succeed();
    }
  }

  @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
  public static void physicalRemovalReleasesTargetAndInvalidatesOnlyMatchingRuntime(GameTestHelper helper) {
    try (Fixture f = new Fixture(helper)) {
      f.link(); var station = f.station();
      var draft = DnvRemoteWeaponServices.prepare(f.player, f.pos, Action.UNLINK, null).draft().orElseThrow();
      var drops = Block.getDrops(f.level.getBlockState(f.pos), f.level, f.pos, f.anchor());
      check(drops.size() == 1 && drops.get(0).is(DnvRemoteWeaponStationContent.ITEM)
          && drops.get(0).getTag() == null, "loot drops plain content, no connection identity: " + drops
          + " table=" + f.level.getBlockState(f.pos).getBlock().getLootTable()
          + " item=" + BuiltInRegistries.ITEM.getKey(DnvRemoteWeaponStationContent.ITEM));
      // Vanilla world replacement invokes the same onRemove used by break and explosion.
      f.level.setBlockAndUpdate(f.pos, Blocks.AIR.defaultBlockState());
      check(f.store().station(station.id()).isEmpty(), "removal retires actual station immediately");
      check(DnvRemoteWeaponServices.confirm(f.player, draft.request().operation()).reason() == Reason.DRAFT_MISSING, "removed BE draft cleared");
      f.place(new ItemStack(DnvRemoteWeaponStationContent.ITEM));
      check(!f.station().id().equals(station.id()), "replacement creates fresh identity");
      f.link(); check(f.station().target().orElseThrow().id().equals(f.target.getUUID()), "reverse occupancy released for new station");
      helper.succeed();
    }
  }

  @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
  public static void copiedBlockEntityAndItemCannotStealOrRetireOriginal(GameTestHelper helper) {
    try (Fixture f = new Fixture(helper)) {
      f.link(); var station = f.station();
      CompoundTag identity = f.anchor().saveWithFullMetadata();
      BlockPos copy = f.pos.offset(3, 0, 2);
      f.level.setBlockAndUpdate(copy, DnvRemoteWeaponStationContent.BLOCK.defaultBlockState());
      var copied = f.level.getBlockEntity(copy); copied.load(identity);
      check(DnvRemoteWeaponServices.create(f.player, copy) == Reason.STATION_EXISTS, "cloned marker cannot acquire station identity");
      check(DnvRemoteWeaponServices.prepare(f.player, copy, Action.UNLINK, null).reason() == Reason.STATION_MISSING, "copied location is not original");
      f.level.setBlockAndUpdate(copy, Blocks.AIR.defaultBlockState());
      check(f.store().station(station.id()).orElseThrow().equals(station), "removal of clone does not retire original");
      // Actual creative-copy item placement ignores a forged BlockEntityTag, even for an operator.
      f.level.setBlockAndUpdate(f.pos, Blocks.AIR.defaultBlockState());
      ItemStack forged = new ItemStack(DnvRemoteWeaponStationContent.ITEM);
      forged.getOrCreateTag().put("BlockEntityTag", identity);
      f.place(forged);
      check(!f.station().id().equals(station.id()) && f.station().target().isEmpty(), "item creates new unlinked instance");
      ServerPlayer other = DnvRemoteWeaponGameTests.login(f.level, f.pos);
      try {
        check(DnvRemoteWeaponServices.prepare(other, f.pos, Action.RETIRE, null).reason() == Reason.NOT_OWNER, "nonowner cannot retire");
        check(DnvRemoteWeaponServices.create(other, f.pos) == Reason.STATION_EXISTS, "nonowner cannot reinitialize");
      } finally { DnvRemoteWeaponGameTests.logout(other); }
      helper.succeed();
    }
  }

  @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
  public static void unloadAndMalformedIdentityNeverRestoreRuntimeOrEraseStoredLink(GameTestHelper helper) {
    try (Fixture f = new Fixture(helper)) {
      f.link(); var station = f.station();
      var draft = DnvRemoteWeaponServices.prepare(f.player, f.pos, Action.UNLINK, null).draft().orElseThrow();
      CompoundTag saved = f.anchor().saveWithFullMetadata();
      f.anchor().onChunkUnloaded();
      check(f.store().station(station.id()).orElseThrow().equals(station), "chunk unload preserves link");
      check(DnvRemoteWeaponServices.confirm(f.player, draft.request().operation()).reason() == Reason.DRAFT_MISSING, "unload clears draft");
      f.anchor().load(saved);
      CompoundTag malformed = saved.copy();
      malformed.getCompound("ForgeData").putString(DnvRemoteWeaponServices.STATION_TAG, "not-a-uuid");
      f.anchor().load(malformed);
      check(DnvRemoteWeaponServices.create(f.player, f.pos) == Reason.STATION_EXISTS, "malformed identity not overwritten");
      check(DnvRemoteWeaponServices.status(f.level, station) == Reason.STATION_CHANGED, "bad identity unavailable");
      check(f.store().station(station.id()).orElseThrow().equals(station), "bad identity does not erase connection");
      check(f.anchor().saveWithFullMetadata().getCompound("ForgeData").getString(DnvRemoteWeaponServices.STATION_TAG).equals("not-a-uuid"),
          "malformed marker retained exactly");
      var retirement = DnvRemoteWeaponServices.prepare(f.player, f.pos, Action.RETIRE, null).draft().orElseThrow();
      check(DnvRemoteWeaponServices.confirm(f.player, retirement.request().operation()).accepted(), "owner explicitly retires broken station");
      f.anchor().load(saved); // Orphan UUID is replaced only by explicit create.
      check(DnvRemoteWeaponServices.create(f.player, f.pos) == Reason.NONE, "explicit orphan recovery creates unlinked instance");
      helper.succeed();
    }
  }

  @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 150)
  public static void formalStationAndProductionM1SurviveSeparateProcessWithoutRuntime(GameTestHelper helper) throws Exception {
    ServerLevel level = helper.getLevel(); BlockPos pos = new BlockPos(128, 90, 64);
    level.getChunkAt(pos); // Explicit isolated fixture chunk; production never obtains tickets.
    File expectedFile = new File("rws-formal-station-restart-expected.nbt");
    UUID owner = UUID.fromString("07b6e96c-0e3b-4c70-9165-cd882f605841");
    ServerPlayer player = DnvRemoteWeaponGameTests.login(level, pos, owner);
    try {
      if (!expectedFile.isFile()) {
        level.setBlockAndUpdate(pos, DnvRemoteWeaponStationContent.BLOCK.defaultBlockState());
        check(DnvRemoteWeaponServices.create(player, pos) == Reason.NONE, "formal restart create");
        var target = spawn(level, pos.offset(2, 0, 0), owner);
        var link = DnvRemoteWeaponServices.prepare(player, pos, Action.LINK, target.getUUID()).draft().orElseThrow();
        check(DnvRemoteWeaponServices.confirm(player, link.request().operation()).accepted(), "formal restart link");
        var pending = DnvRemoteWeaponServices.prepare(player, pos, Action.UNLINK, null).draft().orElseThrow();
        CompoundTag expected = new CompoundTag();
        expected.putUUID("Station", link.station().id()); expected.putUUID("Target", target.getUUID());
        expected.putUUID("Pending", pending.request().operation()); expected.putLong("WriterPid", ProcessHandle.current().pid());
        level.getServer().saveAllChunks(false, true, true); NbtIo.writeCompressed(expected, expectedFile);
        System.out.println("RWS_FORMAL_RESTART_WRITE pid=" + ProcessHandle.current().pid());
        DnvRemoteWeaponGameTests.logout(player); helper.succeed();
      } else {
        CompoundTag expected = NbtIo.readCompressed(expectedFile);
        check(expected.getLong("WriterPid") != ProcessHandle.current().pid(), "distinct process");
        helper.runAfterDelay(10, () -> {
          try {
            var station = DnvRemoteWeaponLinkStore.get(player.server).station(expected.getUUID("Station")).orElseThrow();
            check(level.getBlockEntity(pos) instanceof DnvRemoteWeaponStationContent.StationBlockEntity, "real registered BE restored from chunk");
            check(station.owner().equals(owner) && station.revision() == 1 && station.target().orElseThrow().id().equals(expected.getUUID("Target")),
                "persisted exact owner and link");
            check(level.getEntity(expected.getUUID("Target")) instanceof DnvM1YunlongEntity restored
                && !restored.isVehicle(), "production M1 restored without transient remote passengers");
            check(DnvRemoteWeaponServices.status(level, station) == Reason.NONE, "restored BE identity and target valid");
            check(DnvRemoteWeaponServices.confirm(player, expected.getUUID("Pending")).reason() == Reason.DRAFT_MISSING, "pending draft never restored");
            System.out.println("RWS_FORMAL_RESTART_READ writer=" + expected.getLong("WriterPid") + " reader=" + ProcessHandle.current().pid());
            helper.succeed();
          } finally { DnvRemoteWeaponGameTests.logout(player); }
        });
      }
    } catch (Exception failure) { DnvRemoteWeaponGameTests.logout(player); throw failure; }
  }

  private static final class Fixture implements AutoCloseable {
    final ServerLevel level;
    final BlockPos pos;
    final ServerPlayer player;
    final List<Component> messages = new ArrayList<>();
    final DnvM1YunlongEntity target;
    Fixture(GameTestHelper helper) {
      level = helper.getLevel(); pos = helper.absolutePos(new BlockPos(1, 2, 1));
      player = DnvRemoteWeaponGameTests.login(level, pos, UUID.randomUUID(), packet -> {
        if (packet instanceof ClientboundSystemChatPacket chat) messages.add(chat.content());
      });
      // Recover only this exact isolated test position after a failed run, through the existing owner.
      store().at(level.dimension().location().toString(), pos.asLong()).ifPresent(old ->
          check(store().apply(new Request(old.owner(), UUID.randomUUID(), old.id(), old.revision(), Action.RETIRE, Optional.empty()),
              Reason.NONE).accepted(), "retire failed fixture"));
      level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
      for (Direction direction : Direction.Plane.HORIZONTAL)
        level.setBlockAndUpdate(pos.relative(direction).below(), Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
      place(new ItemStack(DnvRemoteWeaponStationContent.ITEM));
      target = spawn(level, pos.offset(2, 0, 0), player.getUUID());
      DnvRemoteWeaponCommands.register(player.server.getCommands().getDispatcher());
    }
    void place(ItemStack stack) {
      player.setItemInHand(InteractionHand.MAIN_HAND, stack);
      var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
      check(DnvRemoteWeaponStationContent.ITEM.place(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)))
          .consumesAction(), "actual BlockItem placement");
    }
    void click() {
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      check(player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit).consumesAction(), "real right-click entry");
      check(player.containerMenu == player.inventoryMenu, "unbound station never opens a retired management menu");
      check(!player.isPassenger(), "unbound station never mounts an actor");
    }
    String suggestion(String contains) {
      for (int i = messages.size() - 1; i >= 0; i--) {
        var click = messages.get(i).getStyle().getClickEvent();
        if (click != null && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND && click.getValue().contains(contains)) return click.getValue();
      }
      throw new IllegalStateException("missing chat command: " + contains);
    }
    int command(String text) {
      try { return player.server.getCommands().getDispatcher().execute(text.startsWith("/") ? text.substring(1) : text, player.createCommandSourceStack()); }
      catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    void link() {
      var draft = DnvRemoteWeaponServices.prepare(player, pos, Action.LINK, target.getUUID()).draft().orElseThrow();
      check(DnvRemoteWeaponServices.confirm(player, draft.request().operation()).accepted(), "link production target");
    }
    String prepare(String action) {
      return "dnv rws " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " prepare " + action;
    }
    Station station() { return DnvRemoteWeaponServices.station(player, pos).orElseThrow(); }
    DnvRemoteWeaponLinkStore store() { return DnvRemoteWeaponLinkStore.get(player.server); }
    DnvRemoteWeaponStationContent.StationBlockEntity anchor() {
      return (DnvRemoteWeaponStationContent.StationBlockEntity) level.getBlockEntity(pos);
    }
    @Override public void close() {
      level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); target.discard(); DnvRemoteWeaponGameTests.logout(player);
    }
  }
  private static DnvM1YunlongEntity spawn(ServerLevel level, BlockPos pos, UUID owner) {
    var target = DnvM1Content.M1.get().create(level); check(target != null && target.initializeOwner(owner), "M1 ownership");
    target.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5); check(level.addFreshEntity(target), "production M1 added"); return target;
  }
  private static void check(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
}
