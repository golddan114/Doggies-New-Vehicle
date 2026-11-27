package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvRemoteManualTarget;

import com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.*;
import com.liucodex.doggiesnewvehicle.core.DnvTurretDefinition;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real command/ServerLevel/SavedData tests; opt-in armor stands and transport are test fixtures only. */
@GameTestHolder("dnvrwsgametest")
@PrefixGameTestTemplate(false)
public final class DnvRemoteWeaponGameTests {
  static {
    String report = System.getProperty("dnv.rwsGameTest.report");
    if (report != null) {
      try { GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(new File(report))); }
      catch (javax.xml.parsers.ParserConfigurationException exception) { throw new ExceptionInInitializerError(exception); }
    }
  }

  @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
  public static void realCommandLinkReplaceUnlinkAndDiskReload(GameTestHelper helper) throws Exception {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
    ServerPlayer player = login(level, pos);
    ManualFixture first = target(level, pos.offset(2, 0, 0), player.getUUID());
    ManualFixture second = target(level, pos.offset(3, 0, 0), player.getUUID());
    try {
      level.setBlockAndUpdate(pos, DnvRemoteWeaponStationContent.BLOCK.defaultBlockState());
      DnvRemoteWeaponCommands.register(level.getServer().getCommands().getDispatcher());
      check(command(player, pos, "create") == 1, "real create command");
      Station station = DnvRemoteWeaponServices.station(player, pos).orElseThrow();
      check(command(player, pos, "prepare link " + first.getUUID()) == 1, "real prepare command");
      // A fresh preparation supplies the operation for a real confirm command; drafts replace, never authorize directly.
      var draft = prepare(player, pos, Action.LINK, first.getUUID());
      check(confirmCommand(player, draft.request().operation()) == 1, "real confirm command");
      var store = DnvRemoteWeaponLinkStore.get(level.getServer());
      check(store == DnvRemoteWeaponLinkStore.get(level.getServer()), "single DataStorage owner");
      check(store.station(station.id()).orElseThrow().target().orElseThrow().id().equals(first.getUUID()), "first target committed");
      check(DnvRemoteWeaponServices.confirm(player, draft.request().operation()).replayed(), "same operation replays");
      var replacement = prepare(player, pos, Action.REPLACE, second.getUUID());
      second.owner = UUID.randomUUID();
      check(DnvRemoteWeaponServices.confirm(player, replacement.request().operation()).reason() == Reason.TARGET_NOT_OWNED,
          "ownership revoked between prepare and confirm");
      check(store.station(station.id()).orElseThrow().target().orElseThrow().id().equals(first.getUUID()), "failed replace retains first");
      second.owner = player.getUUID();
      replacement = prepare(player, pos, Action.REPLACE, second.getUUID());
      check(confirmCommand(player, replacement.request().operation()) == 1, "atomic replacement command");
      long revision = store.station(station.id()).orElseThrow().revision();
      check(revision == 2, "only two successful mutations");

      // Force actual SavedData save, then reload through a new vanilla DimensionDataStorage from that same disk file.
      level.getServer().overworld().getDataStorage().save();
      File folder = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").toFile();
      File data = new File(folder, DnvRemoteWeaponLinkStore.DATA_NAME + ".dat");
      check(data.isFile(), "SavedData file exists on disk");
      CompoundTag disk = NbtIo.readCompressed(data);
      check(disk.getCompound("data").getInt("Schema") == 1, "actual SavedData envelope");
      var storage = new net.minecraft.world.level.storage.DimensionDataStorage(folder, level.getServer().getFixerUpper());
      var reloaded = storage.computeIfAbsent(DnvRemoteWeaponLinkStore::load, DnvRemoteWeaponLinkStore::new,
          DnvRemoteWeaponLinkStore.DATA_NAME);
      check(reloaded != store && reloaded.snapshot().equals(store.snapshot()), "fresh disk cache restores exact connections and receipts");
      check(reloaded.apply(replacement.request(), Reason.NONE).replayed(), "disk receipt replay");

      var unlink = prepare(player, pos, Action.UNLINK, null);
      check(confirmCommand(player, unlink.request().operation()) == 1, "unlink command");
      check(store.station(station.id()).orElseThrow().target().isEmpty(), "unlink visible in owner");
      var retire = prepare(player, pos, Action.RETIRE, null);
      check(confirmCommand(player, retire.request().operation()) == 1, "retire command");
      check(DnvRemoteWeaponServices.confirm(player, retire.request().operation()).replayed(), "retirement receipt");
      check(store.station(station.id()).isEmpty(), "retirement removes station");
      helper.succeed();
    } finally { first.discard(); second.discard(); logout(player); }
  }

  @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 100)
  public static void realInstancePermissionAndCapabilityGuards(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
    ServerPlayer player = login(level, pos);
    ManualFixture target = target(level, pos.offset(2, 0, 0), player.getUUID());
    try {
      level.setBlockAndUpdate(pos, DnvRemoteWeaponStationContent.BLOCK.defaultBlockState());
      level.getBlockEntity(pos).getPersistentData().putUUID(DnvRemoteWeaponServices.STATION_TAG, UUID.randomUUID());
      check(DnvRemoteWeaponServices.create(player, pos) == Reason.NONE, "anchor created");
      Station station = DnvRemoteWeaponServices.station(player, pos).orElseThrow();
      var draft = prepare(player, pos, Action.LINK, target.getUUID());
      ServerPlayer intruder = login(level, pos);
      try {
        check(DnvRemoteWeaponServices.prepare(intruder, pos, Action.UNLINK, null).reason() == Reason.NOT_OWNER, "other player cannot edit station");
        check(DnvRemoteWeaponServices.confirm(intruder, draft.request().operation()).reason() == Reason.DRAFT_MISSING, "nonce belongs to actor");
      } finally { logout(intruder); }
      BlockPos unloaded = new BlockPos(1_000_000, 90, 1_000_000);
      check(!level.hasChunkAt(unloaded), "unloaded test precondition");
      check(DnvRemoteWeaponServices.create(player, unloaded) == Reason.STATION_UNLOADED, "unloaded anchor rejected");
      check(!level.hasChunkAt(unloaded), "command never loads absent chunk");
      BlockPos cloned = pos.offset(2, 0, 2);
      level.setBlockAndUpdate(cloned, DnvRemoteWeaponStationContent.BLOCK.defaultBlockState());
      level.getBlockEntity(cloned).getPersistentData().putUUID(DnvRemoteWeaponServices.STATION_TAG, station.id());
      check(DnvRemoteWeaponServices.create(player, cloned) == Reason.STATION_EXISTS, "copied marker cannot steal station identity");
      level.setBlockAndUpdate(cloned, Blocks.AIR.defaultBlockState());
      target.optIn = false;
      check(DnvRemoteWeaponServices.confirm(player, draft.request().operation()).reason() == Reason.TARGET_NOT_REMOTE_MANUAL_CAPABLE,
          "explicit opt-in withdrawn");
      target.optIn = true;
      target.declaredType = "test:wrong_type";
      check(DnvRemoteWeaponServices.prepare(player, pos, Action.LINK, target.getUUID()).reason() == Reason.TARGET_NOT_REMOTE_MANUAL_CAPABLE,
          "exact entity type required");
      target.declaredType = "minecraft:armor_stand";
      var ordinary = EntityType.ARMOR_STAND.create(level);
      ordinary.setPos(pos.getX() + 3, pos.getY(), pos.getZ());
      level.addFreshEntity(ordinary);
      check(DnvRemoteWeaponServices.prepare(player, pos, Action.LINK, ordinary.getUUID()).reason() == Reason.TARGET_NOT_REMOTE_MANUAL_CAPABLE,
          "same vanilla entity type without explicit interface is rejected");
      ordinary.discard();
      draft = prepare(player, pos, Action.LINK, target.getUUID());
      CompoundTag anchorNbt = level.getBlockEntity(pos).saveWithFullMetadata();
      level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
      level.setBlockAndUpdate(pos, DnvRemoteWeaponStationContent.BLOCK.defaultBlockState());
      check(DnvRemoteWeaponServices.confirm(player, draft.request().operation()).reason() == Reason.STATION_CHANGED,
          "replacement at same coordinates cannot consume old draft");
      // Even a restored identity may not resurrect a pre-unload draft bound to the old object.
      level.getBlockEntity(pos).load(anchorNbt);
      check(DnvRemoteWeaponServices.confirm(player, draft.request().operation()).reason() == Reason.STATION_CHANGED,
          "old instance rejected after NBT reload");
      draft = prepare(player, pos, Action.LINK, target.getUUID());
      player.setPos(pos.getX() + 50, pos.getY(), pos.getZ());
      check(DnvRemoteWeaponServices.confirm(player, draft.request().operation()).reason() == Reason.TOO_FAR, "operator moved away");
      player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
      target.onInspect = () -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
      check(DnvRemoteWeaponServices.confirm(player, draft.request().operation()).reason() == Reason.STATION_CHANGED,
          "capability callback cannot invalidate anchor then commit");
      check(DnvRemoteWeaponLinkStore.get(level.getServer()).station(station.id()).orElseThrow().target().isEmpty(), "failed guards remain unlinked");
      var retire = prepare(player, pos, Action.RETIRE, null);
      check(DnvRemoteWeaponServices.confirm(player, retire.request().operation()).accepted(), "owner can retire broken station nearby");
      helper.succeed();
    } finally { target.discard(); logout(player); }
  }

  @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 300)
  public static void expiredAndReplacedDraftsNeverApply(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
    ServerPlayer player = login(level, pos);
    ManualFixture target = target(level, pos.offset(2, 0, 0), player.getUUID());
    // A previously failed isolated run may have saved this exact arena fixture before its cleanup.
    // Retire only that position through the existing transaction owner; never clear the store/world.
    var store = DnvRemoteWeaponLinkStore.get(level.getServer());
    store.at(level.dimension().location().toString(), pos.asLong()).ifPresent(previous -> {
      check(store.apply(new Request(previous.owner(), UUID.randomUUID(), previous.id(), previous.revision(),
          Action.RETIRE, Optional.empty()), Reason.NONE).accepted(), "retire previous failed arena fixture");
    });
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(pos, DnvRemoteWeaponStationContent.BLOCK.defaultBlockState());
    check(DnvRemoteWeaponServices.create(player, pos) == Reason.NONE, "expiry anchor");
    var old = prepare(player, pos, Action.LINK, target.getUUID());
    var current = prepare(player, pos, Action.LINK, target.getUUID());
    check(DnvRemoteWeaponServices.confirm(player, old.request().operation()).reason() == Reason.DRAFT_MISSING, "new draft replaces old");
    ServerLevel other = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
    player.setServerLevel(other);
    check(DnvRemoteWeaponServices.confirm(player, current.request().operation()).reason() == Reason.WRONG_DIMENSION, "dimension switch");
    player.setServerLevel(level);
    helper.runAfterDelay(DnvRemoteWeaponServices.DRAFT_TICKS + 1, () -> {
      try {
        check(DnvRemoteWeaponServices.confirm(player, current.request().operation()).reason() == Reason.DRAFT_MISSING,
            "actual server lifecycle removes expired draft");
        var again = prepare(player, pos, Action.LINK, target.getUUID());
        DnvRemoteWeaponServices.clearPlayer(player.server, player.getUUID());
        check(DnvRemoteWeaponServices.confirm(player, again.request().operation()).reason() == Reason.DRAFT_MISSING, "logout invalidates draft");
        check(DnvRemoteWeaponServices.station(player, pos).orElseThrow().target().isEmpty(), "no expired request applied");
        var retire = prepare(player, pos, Action.RETIRE, null);
        check(DnvRemoteWeaponServices.confirm(player, retire.request().operation()).accepted(), "expiry cleanup");
        helper.succeed();
      } finally { target.discard(); logout(player); }
    });
  }

  @GameTest(template = "empty", templateNamespace = "minecraft", timeoutTicks = 200)
  public static void separateProcessRestoresLinksButNeverDrafts(GameTestHelper helper) throws Exception {
    ServerLevel level = helper.getLevel();
    BlockPos pos = new BlockPos(64, 90, 64);
    // Only test setup loads this explicit isolated arena chunk; production access never does so.
    level.getChunkAt(pos);
    File expectedFile = new File("rws-restart-expected.nbt");
    UUID owner = UUID.fromString("1475a023-3859-4015-a59a-4a16e14b38e1");
    ServerPlayer player = login(level, pos, owner);
    try {
      var store = DnvRemoteWeaponLinkStore.get(level.getServer());
      if (!expectedFile.isFile()) {
        level.setBlockAndUpdate(pos, DnvRemoteWeaponStationContent.BLOCK.defaultBlockState());
        check(DnvRemoteWeaponServices.create(player, pos) == Reason.NONE, "restart station created");
        ManualFixture target = target(level, pos.offset(2, 0, 0), owner);
        var link = prepare(player, pos, Action.LINK, target.getUUID());
        check(DnvRemoteWeaponServices.confirm(player, link.request().operation()).accepted(), "restart link committed");
        var pending = prepare(player, pos, Action.UNLINK, null);
        CompoundTag expected = new CompoundTag();
        expected.putUUID("Station", link.request().station()); expected.putUUID("Target", target.getUUID());
        expected.putUUID("Committed", link.request().operation()); expected.putUUID("Pending", pending.request().operation());
        expected.putLong("WriterPid", ProcessHandle.current().pid());
        level.getServer().saveAllChunks(false, true, true);
        NbtIo.writeCompressed(expected, expectedFile);
        System.out.println("RWS_RESTART_WRITE pid=" + ProcessHandle.current().pid());
      } else {
        CompoundTag expected = NbtIo.readCompressed(expectedFile);
        check(expected.getLong("WriterPid") != ProcessHandle.current().pid(), "restart must be another process");
        Station station = store.station(expected.getUUID("Station")).orElseThrow();
        check(station.owner().equals(owner) && station.revision() == 1
            && station.target().orElseThrow().id().equals(expected.getUUID("Target")), "persisted exact binding");
        check(DnvRemoteWeaponServices.stationObservation(level, station) == Reason.NONE, "actual chunk NBT retains station identity");
        Request original = new Request(owner, expected.getUUID("Committed"), station.id(), 0, Action.LINK, station.target());
        var retained = store.snapshot().receipts().stream().filter(receipt -> receipt.request().equals(original)).findFirst();
        var replay = store.apply(original, Reason.NONE);
        if (retained.isPresent() && retained.orElseThrow().reason() == Reason.NONE) {
          check(replay.accepted() && replay.replayed(), "retained successful receipt survives process restart");
        } else {
          // The long-lived isolated world eventually exceeds the production 512-receipt window.
          // Once evicted, the stale operation is refused (and that refusal itself may be replayed).
          check(store.snapshot().receipts().size() == com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.MAX_RECEIPTS,
              "historic receipt only ages out of a full bounded ledger");
          check(!replay.accepted() && replay.reason() == Reason.STALE_REVISION, "evicted old request cannot mutate restored binding");
          System.out.println("RWS_RECEIPT_WINDOW_REJECT retained=" + retained.map(Receipt::reason) + " reason=" + replay.reason());
        }
        check(store.station(station.id()).orElseThrow().equals(station), "replay or expired receipt preserves exact saved link");
        check(DnvRemoteWeaponServices.confirm(player, expected.getUUID("Pending")).reason() == Reason.DRAFT_MISSING,
            "old runtime confirmation cannot survive process restart");
        check(DnvRemoteWeaponServices.status(level, station) != Reason.NONE,
            "test-only opt-in was not registered for restored vanilla entity; link cannot imply control readiness");
        System.out.println("RWS_RESTART_READ writer=" + expected.getLong("WriterPid") + " reader=" + ProcessHandle.current().pid());
      }
      helper.succeed();
    } finally { logout(player); }
  }

  private static DnvRemoteWeaponServices.Draft prepare(ServerPlayer player, BlockPos pos, Action action, UUID target) {
    var result = DnvRemoteWeaponServices.prepare(player, pos, action, target);
    check(result.reason() == Reason.NONE, "prepare " + action + ": " + result.reason());
    return result.draft().orElseThrow();
  }

  private static int command(ServerPlayer player, BlockPos pos, String suffix) {
    return player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),
        "dnv rws " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + suffix);
  }

  private static int confirmCommand(ServerPlayer player, UUID operation) {
    return player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "dnv rws confirm " + operation);
  }

  static ServerPlayer login(ServerLevel level, BlockPos pos) {
    return login(level, pos, UUID.randomUUID());
  }

  static ServerPlayer login(ServerLevel level, BlockPos pos, UUID owner) {
    return login(level, pos, owner, packet -> {});
  }

  static ServerPlayer login(ServerLevel level, BlockPos pos, UUID owner, java.util.function.Consumer<Packet<?>> packets) {
    ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(owner, "RwsFixture"));
    // Actual PlayerList login and command source, with no external socket/client. Transport alone is replaced.
    Connection transport = new Connection(PacketFlow.SERVERBOUND) {
      @Override public void send(Packet<?> packet) { packets.accept(packet); }
      @Override public void send(Packet<?> packet, PacketSendListener listener) { packets.accept(packet); }
    };
    new io.netty.channel.embedded.EmbeddedChannel(transport);
    level.getServer().getPlayerList().placeNewPlayer(transport, player);
    player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
    return player;
  }

  static void logout(ServerPlayer player) {
    DnvRemoteWeaponServices.clearPlayer(player.server, player.getUUID());
    player.server.getPlayerList().remove(player);
  }

  private static ManualFixture target(ServerLevel level, BlockPos pos, UUID owner) {
    ManualFixture target = new ManualFixture(level, owner);
    target.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    check(level.addFreshEntity(target), "fixture entity inserted into real level");
    return target;
  }

  private static void check(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }

  private static final class ManualFixture extends ArmorStand implements DnvRemoteManualTarget {
    private UUID owner;
    private boolean optIn = true;
    private String declaredType = "minecraft:armor_stand";
    private Runnable onInspect = () -> {};
    ManualFixture(ServerLevel level, UUID owner) { super(EntityType.ARMOR_STAND, level); this.owner = owner; }
    @Override public UUID remoteManualOwner() { return owner; }
    @Override public DnvTurretDefinition remoteManualDefinition() {
      onInspect.run();
      return new DnvTurretDefinition(declaredType, "test:manual_fixture", Map.of("main", Set.of("unconfigured")), optIn, Optional.empty());
    }
  }
}
