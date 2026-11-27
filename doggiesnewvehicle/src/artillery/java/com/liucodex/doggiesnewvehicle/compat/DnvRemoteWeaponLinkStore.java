package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks;
import com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Single Overworld SavedData owner. Invalid/future data is retained verbatim and never made writable. */
public final class DnvRemoteWeaponLinkStore extends SavedData {
  public static final String DATA_NAME = "doggiesnewvehicle_remote_links_v1";
  private DnvRemoteWeaponLinks state = new DnvRemoteWeaponLinks();
  private CompoundTag preservedInvalid;

  public static DnvRemoteWeaponLinkStore get(MinecraftServer server) {
    if (!server.isSameThread()) throw new IllegalStateException("RWS requires the server thread");
    return server.overworld().getDataStorage().computeIfAbsent(
        DnvRemoteWeaponLinkStore::load, DnvRemoteWeaponLinkStore::new, DATA_NAME);
  }

  public boolean writable() { return preservedInvalid == null; }
  public Optional<Station> station(UUID id) { return state.station(id); }
  public Optional<Station> at(String dimension, long position) { return state.at(dimension, position); }
  public Snapshot snapshot() { return state.snapshot(); }

  public Reason create(UUID id, UUID owner, String dimension, long position) {
    if (!writable()) return Reason.STORAGE_INVALID;
    Reason result = state.create(id, owner, dimension, position);
    if (result == Reason.NONE) setDirty();
    return result;
  }

  public Result apply(Request request, Reason observation) {
    if (!writable()) return new Result(Reason.STORAGE_INVALID, -1, request.operation(), false);
    Result result = state.apply(request, observation);
    if (!result.replayed() && result.reason() != Reason.NOT_OWNER && result.reason() != Reason.STATION_MISSING) setDirty();
    return result;
  }

  @Override
  public CompoundTag save(CompoundTag tag) {
    if (!writable()) return preservedInvalid.copy();
    tag.putInt("Schema", 1);
    ListTag stations = new ListTag();
    for (Station station : state.snapshot().stations()) {
      CompoundTag value = new CompoundTag();
      value.putUUID("Id", station.id()); value.putUUID("Owner", station.owner());
      value.putString("Dimension", station.dimension()); value.putLong("Position", station.position());
      value.putLong("Revision", station.revision());
      station.target().ifPresent(target -> value.put("Target", writeTarget(target)));
      stations.add(value);
    }
    tag.put("Stations", stations);
    ListTag receipts = new ListTag();
    for (Receipt receipt : state.snapshot().receipts()) {
      CompoundTag value = new CompoundTag();
      Request request = receipt.request();
      value.putUUID("Actor", request.actor()); value.putUUID("Operation", request.operation());
      value.putUUID("Station", request.station()); value.putLong("Expected", request.expectedRevision());
      value.putString("Action", request.action().name());
      request.target().ifPresent(target -> value.put("Target", writeTarget(target)));
      value.putString("Reason", receipt.reason().name()); value.putLong("Revision", receipt.revision());
      receipts.add(value);
    }
    tag.put("Receipts", receipts);
    return tag;
  }

  public static DnvRemoteWeaponLinkStore load(CompoundTag tag) {
    DnvRemoteWeaponLinkStore store = new DnvRemoteWeaponLinkStore();
    try {
      require(tag, "Schema", Tag.TAG_INT);
      if (tag.getInt("Schema") != 1) throw new IllegalArgumentException("unsupported RWS schema");
      List<Station> stations = new ArrayList<>();
      for (Tag element : list(tag, "Stations", DnvRemoteWeaponLinks.MAX_STATIONS)) {
        CompoundTag value = (CompoundTag) element;
        require(value, "Dimension", Tag.TAG_STRING); require(value, "Position", Tag.TAG_LONG);
        require(value, "Revision", Tag.TAG_LONG);
        stations.add(new Station(uuid(value, "Id"), uuid(value, "Owner"), value.getString("Dimension"),
            value.getLong("Position"), value.getLong("Revision"), readTarget(value)));
      }
      List<Receipt> receipts = new ArrayList<>();
      for (Tag element : list(tag, "Receipts", DnvRemoteWeaponLinks.MAX_RECEIPTS)) {
        CompoundTag value = (CompoundTag) element;
        require(value, "Expected", Tag.TAG_LONG); require(value, "Revision", Tag.TAG_LONG);
        require(value, "Action", Tag.TAG_STRING); require(value, "Reason", Tag.TAG_STRING);
        Request request = new Request(uuid(value, "Actor"), uuid(value, "Operation"), uuid(value, "Station"),
            value.getLong("Expected"), Action.valueOf(value.getString("Action")), readTarget(value));
        receipts.add(new Receipt(request, Reason.valueOf(value.getString("Reason")), value.getLong("Revision")));
      }
      store.state = DnvRemoteWeaponLinks.restore(new Snapshot(stations, receipts));
    } catch (IllegalArgumentException exception) {
      store.preservedInvalid = tag.copy();
    }
    return store;
  }

  private static ListTag list(CompoundTag tag, String key, int maximum) {
    require(tag, key, Tag.TAG_LIST);
    ListTag list = (ListTag) tag.get(key);
    if (list.size() > maximum || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
      throw new IllegalArgumentException("invalid RWS list");
    }
    return list;
  }

  private static CompoundTag writeTarget(Target target) {
    CompoundTag tag = new CompoundTag();
    tag.putUUID("Id", target.id()); tag.putString("Type", target.type()); tag.putString("Dimension", target.dimension());
    return tag;
  }

  private static Optional<Target> readTarget(CompoundTag parent) {
    if (!parent.contains("Target")) return Optional.empty();
    require(parent, "Target", Tag.TAG_COMPOUND);
    CompoundTag tag = parent.getCompound("Target");
    require(tag, "Type", Tag.TAG_STRING); require(tag, "Dimension", Tag.TAG_STRING);
    return Optional.of(new Target(uuid(tag, "Id"), tag.getString("Type"), tag.getString("Dimension")));
  }

  private static UUID uuid(CompoundTag tag, String key) {
    if (!tag.hasUUID(key)) throw new IllegalArgumentException("invalid UUID");
    return tag.getUUID(key);
  }

  private static void require(CompoundTag tag, String key, int type) {
    if (!tag.contains(key, type)) throw new IllegalArgumentException("missing/invalid " + key);
  }
}
