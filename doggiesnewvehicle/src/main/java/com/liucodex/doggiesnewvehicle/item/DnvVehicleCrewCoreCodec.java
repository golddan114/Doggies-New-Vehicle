package com.liucodex.doggiesnewvehicle.item;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Bounded NBT boundary for the configuration carried by a future vehicle crew core item. */
public final class DnvVehicleCrewCoreCodec {
  public static final String ROOT_KEY = "DnvVehicleCrewCore";
  private static final int MAX_ID_LENGTH = 256;
  private static final int MAX_STATION_ID_LENGTH = 128;

  private DnvVehicleCrewCoreCodec() {}

  public static CompoundTag encode(
      DnvVehicleCrewConfigSessions.CommitResult result) {
    Objects.requireNonNull(result, "result");
    return encode(result.snapshot().orElseThrow());
  }

  public static CompoundTag encode(DnvVehicleCrewProfile.Snapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    CompoundTag payload = new CompoundTag();
    payload.putInt("SchemaVersion", snapshot.schemaVersion());
    payload.putUUID("CoreId", snapshot.coreId());
    payload.putUUID("ConfiguredBy", snapshot.configuredBy());
    payload.putString("AdapterId", snapshot.adapterId());
    payload.putLong("AdapterRevision", snapshot.adapterRevision());
    payload.putString("VehicleTypeId", snapshot.vehicleTypeId());
    payload.putString("FlightMode", snapshot.flightMode().name());
    ListTag stations = new ListTag();
    snapshot.stations().values().forEach(station -> {
      CompoundTag encoded = new CompoundTag();
      encoded.putString("StationId", station.id());
      encoded.putString("Mode", station.mode().name());
      stations.add(encoded);
    });
    payload.put("Stations", stations);
    CompoundTag root = new CompoundTag();
    root.put(ROOT_KEY, payload);
    return root;
  }

  public static DecodeResult decode(CompoundTag root) {
    List<Issue> issues = new ArrayList<>();
    if (root == null || !root.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
      return DecodeResult.rejected(issue(ROOT_KEY, IssueCode.REQUIRED));
    }
    CompoundTag payload = root.getCompound(ROOT_KEY);
    Integer schema = integer(payload, "SchemaVersion", issues);
    if (schema == null || schema != DnvVehicleCrewProfile.CURRENT_SCHEMA_VERSION) {
      if (schema != null) {
        issues.add(issue("SchemaVersion", IssueCode.UNSUPPORTED_SCHEMA));
      }
      return new DecodeResult(Optional.empty(), List.copyOf(issues));
    }

    UUID coreId = uuid(payload, "CoreId", issues);
    UUID configuredBy = uuid(payload, "ConfiguredBy", issues);
    String adapterId = text(payload, "AdapterId", MAX_ID_LENGTH, issues);
    Long adapterRevision = longValue(payload, "AdapterRevision", issues);
    String vehicleTypeId = text(payload, "VehicleTypeId", MAX_ID_LENGTH, issues);
    DnvVehicleCrewProfile.FlightMode flightMode = enumValue(
        payload,
        "FlightMode",
        DnvVehicleCrewProfile.FlightMode.class,
        issues);
    List<DnvVehicleCrewProfile.StationDraft> stations = stations(payload, issues);
    if (!issues.isEmpty()) {
      return new DecodeResult(Optional.empty(), List.copyOf(issues));
    }
    return new DecodeResult(
        Optional.of(new DnvVehicleCrewProfile.Draft(
            schema,
            coreId,
            configuredBy,
            adapterId,
            adapterRevision,
            vehicleTypeId,
            flightMode,
            stations)),
        List.of());
  }

  private static List<DnvVehicleCrewProfile.StationDraft> stations(
      CompoundTag payload, List<Issue> issues) {
    Tag raw = payload.get("Stations");
    if (!(raw instanceof ListTag list)
        || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
      issues.add(issue("Stations", IssueCode.WRONG_TYPE));
      return List.of();
    }
    if (list.size() > DnvVehicleCrewProfile.MAX_STATIONS) {
      issues.add(issue("Stations", IssueCode.TOO_MANY_STATIONS));
      return List.of();
    }
    List<DnvVehicleCrewProfile.StationDraft> result = new ArrayList<>();
    for (int index = 0; index < list.size(); index++) {
      CompoundTag station = list.getCompound(index);
      String field = "Stations[" + index + "]";
      String id = text(station, "StationId", MAX_STATION_ID_LENGTH, issues, field);
      DnvControlAuthority.WeaponMode mode = enumValue(
          station, "Mode", DnvControlAuthority.WeaponMode.class, issues, field);
      if (id != null && mode != null) {
        result.add(new DnvVehicleCrewProfile.StationDraft(id, mode));
      }
    }
    return List.copyOf(result);
  }

  private static UUID uuid(CompoundTag tag, String key, List<Issue> issues) {
    if (!tag.hasUUID(key)) {
      issues.add(issue(key, IssueCode.WRONG_TYPE));
      return null;
    }
    try {
      return tag.getUUID(key);
    } catch (IllegalArgumentException exception) {
      issues.add(issue(key, IssueCode.INVALID_UUID));
      return null;
    }
  }

  private static Integer integer(CompoundTag tag, String key, List<Issue> issues) {
    if (!tag.contains(key, Tag.TAG_INT)) {
      issues.add(issue(key, IssueCode.WRONG_TYPE));
      return null;
    }
    return tag.getInt(key);
  }

  private static Long longValue(CompoundTag tag, String key, List<Issue> issues) {
    if (!tag.contains(key, Tag.TAG_LONG)) {
      issues.add(issue(key, IssueCode.WRONG_TYPE));
      return null;
    }
    long value = tag.getLong(key);
    if (value < 0L) {
      issues.add(issue(key, IssueCode.NEGATIVE_REVISION));
      return null;
    }
    return value;
  }

  private static String text(
      CompoundTag tag, String key, int maxLength, List<Issue> issues) {
    return text(tag, key, maxLength, issues, "");
  }

  private static String text(
      CompoundTag tag,
      String key,
      int maxLength,
      List<Issue> issues,
      String prefix) {
    String field = prefix.isEmpty() ? key : prefix + "." + key;
    if (!tag.contains(key, Tag.TAG_STRING)) {
      issues.add(issue(field, IssueCode.WRONG_TYPE));
      return null;
    }
    String value = tag.getString(key);
    if (value.isBlank()) {
      issues.add(issue(field, IssueCode.BLANK_TEXT));
      return null;
    }
    if (value.length() > maxLength) {
      issues.add(issue(field, IssueCode.TEXT_TOO_LONG));
      return null;
    }
    return value;
  }

  private static <E extends Enum<E>> E enumValue(
      CompoundTag tag,
      String key,
      Class<E> type,
      List<Issue> issues) {
    return enumValue(tag, key, type, issues, "");
  }

  private static <E extends Enum<E>> E enumValue(
      CompoundTag tag,
      String key,
      Class<E> type,
      List<Issue> issues,
      String prefix) {
    String raw = text(tag, key, MAX_STATION_ID_LENGTH, issues, prefix);
    if (raw == null) {
      return null;
    }
    try {
      return Enum.valueOf(type, raw);
    } catch (IllegalArgumentException exception) {
      String field = prefix.isEmpty() ? key : prefix + "." + key;
      issues.add(issue(field, IssueCode.UNKNOWN_ENUM));
      return null;
    }
  }

  private static Issue issue(String field, IssueCode code) {
    return new Issue(field, code);
  }

  public record DecodeResult(Optional<DnvVehicleCrewProfile.Draft> draft, List<Issue> issues) {
    public DecodeResult {
      draft = Objects.requireNonNull(draft, "draft");
      issues = List.copyOf(issues);
    }

    public boolean accepted() {
      return draft.isPresent() && issues.isEmpty();
    }

    private static DecodeResult rejected(Issue issue) {
      return new DecodeResult(Optional.empty(), List.of(issue));
    }
  }

  public record Issue(String field, IssueCode code) {}

  public enum IssueCode {
    REQUIRED,
    WRONG_TYPE,
    UNSUPPORTED_SCHEMA,
    INVALID_UUID,
    NEGATIVE_REVISION,
    BLANK_TEXT,
    TEXT_TOO_LONG,
    UNKNOWN_ENUM,
    TOO_MANY_STATIONS
  }
}
