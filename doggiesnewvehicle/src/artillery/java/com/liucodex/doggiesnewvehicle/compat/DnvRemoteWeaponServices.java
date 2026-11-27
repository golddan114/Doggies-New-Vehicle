package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvRemoteManualTarget;

import com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.*;
import com.liucodex.doggiesnewvehicle.core.DnvTurretDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Loaded-world command adapter. Drafts are ephemeral; only LinkStore owns connections. */
public final class DnvRemoteWeaponServices {
  static final String STATION_TAG = "dnv_rws_station";
  static final int DRAFT_TICKS = 200;
  static final int MAX_DRAFTS = 128;
  static final double STATION_DISTANCE_SQUARED = 64;
  static final double LINK_DISTANCE_SQUARED = 128 * 128;
  private static final Map<MinecraftServer, LinkedHashMap<UUID, Draft>> DRAFTS = new WeakHashMap<>();

  private DnvRemoteWeaponServices() {}

  public static Reason create(ServerPlayer player, BlockPos pos) {
    Reason access = access(player, player.serverLevel(), pos);
    if (access != Reason.NONE) return access;
    BlockEntity anchor = anchor(player.serverLevel(), pos);
    if (anchor == null) return Reason.STATION_MISSING;
    DnvRemoteWeaponLinkStore store = DnvRemoteWeaponLinkStore.get(player.server);
    if (!store.writable()) return Reason.STORAGE_INVALID;
    if (anchor.getPersistentData().contains(STATION_TAG)) {
      if (!anchor.getPersistentData().hasUUID(STATION_TAG)
          || store.station(anchor.getPersistentData().getUUID(STATION_TAG)).isPresent()) return Reason.STATION_EXISTS;
      // A crash may save the marker before SavedData. Explicit create can replace an orphan marker,
      // but can never recover a link or steal the identity of a station elsewhere.
    }
    UUID id = UUID.randomUUID();
    Reason created = store.create(id, player.getUUID(), dimension(player.serverLevel()), pos.asLong());
    if (created == Reason.NONE) {
      anchor.getPersistentData().putUUID(STATION_TAG, id);
      anchor.setChanged();
    }
    return created;
  }

  public static Optional<Station> station(ServerPlayer player, BlockPos pos) {
    if (access(player, player.serverLevel(), pos) != Reason.NONE) return Optional.empty();
    return DnvRemoteWeaponLinkStore.get(player.server).at(dimension(player.serverLevel()), pos.asLong())
        .filter(station -> station.owner().equals(player.getUUID()));
  }

  public static Reason status(ServerLevel level, Station station) {
    Reason anchor = stationObservation(level, station);
    if (anchor != Reason.NONE) return anchor;
    if (station.target().isEmpty()) return Reason.STATION_UNLINKED;
    Target identity = station.target().orElseThrow();
    Entity target = level.getEntity(identity.id());
    return targetObservation(level, BlockPos.of(station.position()), station.owner(), identity, target);
  }

  public static Preparation prepare(ServerPlayer player, BlockPos pos, Action action, UUID targetId) {
    ServerLevel level = player.serverLevel();
    Reason access = access(player, level, pos);
    if (access != Reason.NONE) return new Preparation(access, Optional.empty());
    DnvRemoteWeaponLinkStore store = DnvRemoteWeaponLinkStore.get(player.server);
    if (!store.writable()) return new Preparation(Reason.STORAGE_INVALID, Optional.empty());
    Station station = store.at(dimension(level), pos.asLong()).orElse(null);
    if (station == null) return new Preparation(Reason.STATION_MISSING, Optional.empty());
    if (!station.owner().equals(player.getUUID())) return new Preparation(Reason.NOT_OWNER, Optional.empty());
    Reason observation = stationObservation(level, station);
    if (action != Action.RETIRE && observation != Reason.NONE) return new Preparation(observation, Optional.empty());
    Entity target = targetId == null ? null : level.getEntity(targetId);
    Optional<Target> identity = Optional.empty();
    if (action == Action.LINK || action == Action.REPLACE) {
      if (target == null) return new Preparation(Reason.TARGET_UNLOADED, Optional.empty());
      identity = Optional.of(identity(target));
      observation = targetObservation(level, pos, player.getUUID(), identity.orElseThrow(), target);
      if (observation != Reason.NONE) return new Preparation(observation, Optional.empty());
    }
    // Recheck after opt-in callbacks: no stale station/actor may enter the confirmation draft.
    access = access(player, level, pos);
    if (access != Reason.NONE) return new Preparation(access, Optional.empty());
    if (action != Action.RETIRE && stationObservation(level, station) != Reason.NONE) {
      return new Preparation(Reason.STATION_CHANGED, Optional.empty());
    }
    LinkedHashMap<UUID, Draft> drafts = drafts(player.server);
    drafts.entrySet().removeIf(entry -> entry.getValue().player().getUUID().equals(player.getUUID()));
    if (drafts.size() >= MAX_DRAFTS) return new Preparation(Reason.CAPACITY, Optional.empty());
    UUID operation = UUID.randomUUID();
    Request request = new Request(player.getUUID(), operation, station.id(), station.revision(), action, identity);
    Draft draft = new Draft(request, station, player, level, anchor(level, pos), target, player.server.getTickCount());
    drafts.put(operation, draft);
    return new Preparation(Reason.NONE, Optional.of(draft));
  }

  public static Result confirm(ServerPlayer player, UUID operation) {
    LinkedHashMap<UUID, Draft> drafts = DRAFTS.get(player.server);
    Draft draft = drafts == null ? null : drafts.get(operation);
    if (draft == null || !draft.request().actor().equals(player.getUUID())) return rejected(operation, Reason.DRAFT_MISSING);
    long elapsed = (long) player.server.getTickCount() - draft.createdTick();
    if (elapsed < 0 || elapsed >= DRAFT_TICKS) {
      drafts.remove(operation);
      return rejected(operation, Reason.DRAFT_EXPIRED);
    }
    BlockPos pos = BlockPos.of(draft.station().position());
    Reason access = access(player, draft.level(), pos);
    if (player != draft.player()) access = Reason.ACTOR_UNAVAILABLE;
    if (access != Reason.NONE) return rejected(operation, access);
    DnvRemoteWeaponLinkStore store = DnvRemoteWeaponLinkStore.get(player.server);
    Reason observation = Reason.NONE;
    if (draft.request().action() != Action.RETIRE) {
      observation = stationObservation(draft.level(), draft.station());
      if (observation == Reason.NONE && anchor(draft.level(), pos) != draft.anchor()) observation = Reason.STATION_CHANGED;
      if (observation != Reason.NONE) return rejected(operation, observation);
    }
    if (draft.request().target().isPresent()) {
      observation = targetObservation(draft.level(), pos, player.getUUID(), draft.request().target().orElseThrow(), draft.target());
      // Opt-in/ownership callbacks cannot invalidate the source anchor or player and still commit.
      access = access(player, draft.level(), pos);
      if (access != Reason.NONE) return rejected(operation, access);
      if (stationObservation(draft.level(), draft.station()) != Reason.NONE || anchor(draft.level(), pos) != draft.anchor()) {
        return rejected(operation, Reason.STATION_CHANGED);
      }
    }
    if (DRAFTS.get(player.server) != drafts || drafts.get(operation) != draft) {
      return rejected(operation, Reason.DRAFT_MISSING);
    }
    Result result = store.apply(draft.request(), observation);
    if (result.accepted() && draft.request().action() == Action.RETIRE) {
      BlockEntity current = anchor(draft.level(), pos);
      if (current != null && hasIdentity(current, draft.station().id())) {
        current.getPersistentData().remove(STATION_TAG);
        current.setChanged();
      }
    }
    return result;
  }

  static Reason targetObservation(ServerLevel level, BlockPos pos, UUID actor, Target expected, Entity target) {
    if (!expected.dimension().equals(dimension(level))) return Reason.WRONG_DIMENSION;
    if (target == null || !level.hasChunkAt(target.blockPosition())) return Reason.TARGET_UNLOADED;
    if (target.level() != level || target.isRemoved() || !target.isAlive()
        || level.getEntity(expected.id()) != target || !identity(target).equals(expected)) return Reason.TARGET_CHANGED;
    if (!(target instanceof DnvRemoteManualTarget capable)) return Reason.TARGET_NOT_REMOTE_MANUAL_CAPABLE;
    DnvTurretDefinition definition = capable.remoteManualDefinition();
    UUID owner = capable.remoteManualOwner();
    if (definition == null || !definition.remoteManualOptIn() || !definition.entityType().equals(expected.type())) {
      return Reason.TARGET_NOT_REMOTE_MANUAL_CAPABLE;
    }
    if (!actor.equals(owner)) return Reason.TARGET_NOT_OWNED;
    if (target.level() != level || target.isRemoved() || !target.isAlive()
        || level.getEntity(expected.id()) != target || !identity(target).equals(expected)) return Reason.TARGET_CHANGED;
    if (!level.hasChunkAt(target.blockPosition())) return Reason.TARGET_UNLOADED;
    return target.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= LINK_DISTANCE_SQUARED
        ? Reason.NONE : Reason.TOO_FAR;
  }

  static Reason stationObservation(ServerLevel level, Station station) {
    if (!dimension(level).equals(station.dimension())) return Reason.WRONG_DIMENSION;
    BlockPos pos = BlockPos.of(station.position());
    if (!level.hasChunkAt(pos)) return Reason.STATION_UNLOADED;
    BlockEntity anchor = anchor(level, pos);
    return anchor == null ? Reason.STATION_MISSING : hasIdentity(anchor, station.id()) ? Reason.NONE : Reason.STATION_CHANGED;
  }

  private static boolean hasIdentity(BlockEntity anchor, UUID id) {
    return anchor.getPersistentData().hasUUID(STATION_TAG) && anchor.getPersistentData().getUUID(STATION_TAG).equals(id);
  }

  static Reason access(ServerPlayer player, ServerLevel level, BlockPos pos) {
    if (!level.getServer().isSameThread()) throw new IllegalStateException("RWS requires the server thread");
    if (player.serverLevel() != level) return Reason.WRONG_DIMENSION;
    if (!player.isAlive() || player.isRemoved() || player.isSpectator()
        || player.server.getPlayerList().getPlayer(player.getUUID()) != player) return Reason.ACTOR_UNAVAILABLE;
    if (!level.hasChunkAt(pos)) return Reason.STATION_UNLOADED;
    if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > STATION_DISTANCE_SQUARED) return Reason.TOO_FAR;
    return level.mayInteract(player, pos) ? Reason.NONE : Reason.NOT_OWNER;
  }

  static BlockEntity anchor(ServerLevel level, BlockPos pos) {
    if (!level.hasChunkAt(pos)) return null;
    BlockEntity entity = level.getBlockEntity(pos);
    if (entity == null || entity.isRemoved()) return null;
    if (entity instanceof DnvRemoteWeaponStationContent.StationBlockEntity
        && level.getBlockState(pos).is(DnvRemoteWeaponStationContent.BLOCK)) return entity;
    return null;
  }

  /** Actual world removal, not an unload. A copied marker elsewhere must never retire the original. */
  static void stationRemoved(ServerLevel level, BlockEntity anchor) {
    anchorUnavailable(level, anchor);
    DnvRemoteWeaponLinkStore store = DnvRemoteWeaponLinkStore.get(level.getServer());
    Station station = store.at(dimension(level), anchor.getBlockPos().asLong()).orElse(null);
    if (station == null || !hasIdentity(anchor, station.id())) return;
    store.apply(new Request(station.owner(), UUID.randomUUID(), station.id(), station.revision(),
        Action.RETIRE, Optional.empty()), Reason.NONE);
  }

  /** Invalidate only operations bound to this BE object; persistent connection identity remains untouched. */
  static void anchorUnavailable(ServerLevel level, BlockEntity anchor) {
    if (!level.getServer().isSameThread()) throw new IllegalStateException("RWS requires the server thread");
    var drafts = DRAFTS.get(level.getServer());
    if (drafts != null) drafts.entrySet().removeIf(entry -> entry.getValue().anchor() == anchor);
  }

  private static Target identity(Entity target) {
    return new Target(target.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString(), dimension((ServerLevel) target.level()));
  }

  private static String dimension(ServerLevel level) { return level.dimension().location().toString(); }
  private static Result rejected(UUID operation, Reason reason) { return new Result(reason, -1, operation, false); }

  private static LinkedHashMap<UUID, Draft> drafts(MinecraftServer server) {
    LinkedHashMap<UUID, Draft> result = DRAFTS.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
    result.entrySet().removeIf(entry -> {
      long elapsed = (long) server.getTickCount() - entry.getValue().createdTick();
      return elapsed < 0 || elapsed >= DRAFT_TICKS;
    });
    return result;
  }

  public static void clear(MinecraftServer server) { DRAFTS.remove(server); }
  public static void expire(MinecraftServer server) {
    LinkedHashMap<UUID, Draft> drafts = DRAFTS.get(server);
    if (drafts == null) return;
    drafts.entrySet().removeIf(entry -> {
      Draft draft = entry.getValue();
      long elapsed = (long) server.getTickCount() - draft.createdTick();
      return elapsed < 0 || elapsed >= DRAFT_TICKS || !draft.player().isAlive()
          || draft.player().isRemoved() || draft.player().serverLevel() != draft.level();
    });
    if (drafts.isEmpty()) DRAFTS.remove(server);
  }
  public static void clearPlayer(MinecraftServer server, UUID player) {
    LinkedHashMap<UUID, Draft> drafts = DRAFTS.get(server);
    if (drafts != null) drafts.entrySet().removeIf(entry -> entry.getValue().player().getUUID().equals(player));
  }

  public record Preparation(Reason reason, Optional<Draft> draft) {}
  public record Draft(Request request, Station station, ServerPlayer player, ServerLevel level,
      BlockEntity anchor, Entity target, long createdTick) {}
}
