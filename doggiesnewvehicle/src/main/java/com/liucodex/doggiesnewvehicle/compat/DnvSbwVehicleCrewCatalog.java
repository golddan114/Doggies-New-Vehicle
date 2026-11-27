package com.liucodex.doggiesnewvehicle.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Per-server authority for exact SBW vehicle layouts allowed to consume a DNV crew core.
 * Invalid reloads activate an empty catalog so stale layouts cannot remain armed.
 */
public final class DnvSbwVehicleCrewCatalog {
  public static final int CURRENT_SCHEMA_VERSION = 1;
  public static final int MAX_ENTRIES = 256;
  public static final int MAX_ID_LENGTH = 160;
  public static final long MAX_LAYOUT_FINGERPRINT = 0xFFFF_FFFFL;

  private static final Set<String> ALLOWED_FIELDS = Set.of(
      "schemaVersion",
      "enabled",
      "adapterId",
      "revision",
      "vehicleTypeId",
      "expectedLayoutFingerprint",
      "aiStationIds",
      "flightActuatorId",
      "aimChannels",
      "stationPolicies");

  private static final Set<String> AIM_CHANNEL_FIELDS = Set.of(
      "aimChannelId", "sharedWith");

  private Snapshot snapshot = new Snapshot(0L, Map.of());

  public Snapshot snapshot() {
    return snapshot;
  }

  public Optional<DnvSbwVehicleCrewAdapter.AllowlistEntry> find(String vehicleTypeId) {
    if (vehicleTypeId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(snapshot.entries().get(vehicleTypeId.trim()));
  }

  /**
   * Atomically replaces the active catalog. A structurally invalid reload advances the revision
   * and installs an empty snapshot, forcing already-installed cores to revalidate as hold-fire.
   */
  public ReloadResult replace(
      Map<String, JsonElement> documents, long expectedCatalogRevision) {
    Objects.requireNonNull(documents, "documents");
    if (expectedCatalogRevision != snapshot.revision()) {
      return ReloadResult.rejected(
          snapshot,
          List.of(new Issue("catalog", "revision", IssueCode.STALE_CATALOG_REVISION)));
    }
    long nextRevision = nextRevision();
    ParseResult parsed = parse(documents);
    snapshot = parsed.issues().isEmpty()
        ? new Snapshot(nextRevision, parsed.entries())
        : new Snapshot(nextRevision, Map.of());
    return parsed.issues().isEmpty()
        ? ReloadResult.accepted(snapshot)
        : ReloadResult.rejected(snapshot, parsed.issues());
  }

  /** Clears all server-owned compatibility state on stop without retaining world references. */
  public Snapshot clear(long expectedCatalogRevision) {
    if (expectedCatalogRevision != snapshot.revision()) {
      return snapshot;
    }
    snapshot = new Snapshot(nextRevision(), Map.of());
    return snapshot;
  }

  private long nextRevision() {
    if (snapshot.revision() == Long.MAX_VALUE) {
      throw new IllegalStateException("vehicle crew catalog revision exhausted");
    }
    return snapshot.revision() + 1L;
  }

  private static ParseResult parse(Map<String, JsonElement> documents) {
    List<Issue> issues = new ArrayList<>();
    if (documents.size() > MAX_ENTRIES) {
      issues.add(new Issue("catalog", "documents", IssueCode.TOO_MANY_ENTRIES));
      return new ParseResult(Map.of(), issues);
    }

    Map<String, DnvSbwVehicleCrewAdapter.AllowlistEntry> entries = new LinkedHashMap<>();
    Set<String> adapterIds = new LinkedHashSet<>();
    documents.entrySet().stream()
        .sorted(Comparator.comparing(Map.Entry::getKey))
        .forEach(document -> parseDocument(document.getKey(), document.getValue(), issues)
            .ifPresent(entry -> {
              if (entries.putIfAbsent(entry.vehicleTypeId(), entry) != null) {
                issues.add(new Issue(
                    document.getKey(), "vehicleTypeId", IssueCode.DUPLICATE_VEHICLE_TYPE));
              }
              if (!adapterIds.add(entry.adapterId())) {
                issues.add(new Issue(
                    document.getKey(), "adapterId", IssueCode.DUPLICATE_ADAPTER_ID));
              }
            }));
    return issues.isEmpty()
        ? new ParseResult(entries, List.of())
        : new ParseResult(Map.of(), issues);
  }

  private static Optional<DnvSbwVehicleCrewAdapter.AllowlistEntry> parseDocument(
      String source, JsonElement element, List<Issue> issues) {
    int issueStart = issues.size();
    String safeSource = source == null || source.isBlank() ? "<unknown>" : source;
    if (element == null || !element.isJsonObject()) {
      issues.add(new Issue(safeSource, "$", IssueCode.EXPECTED_OBJECT));
      return Optional.empty();
    }
    JsonObject object = element.getAsJsonObject();
    for (String field : object.keySet()) {
      if (!ALLOWED_FIELDS.contains(field)) {
        issues.add(new Issue(safeSource, field, IssueCode.UNKNOWN_FIELD));
      }
    }

    Long schemaVersion = integer(object, safeSource, "schemaVersion", issues);
    boolean enabled = enabled(object, safeSource, issues);
    if (schemaVersion != null && schemaVersion != CURRENT_SCHEMA_VERSION) {
      issues.add(new Issue(safeSource, "schemaVersion", IssueCode.UNSUPPORTED_SCHEMA));
    }
    String adapterId = string(object, safeSource, "adapterId", issues);
    Long revision = integer(object, safeSource, "revision", issues);
    String vehicleTypeId = string(object, safeSource, "vehicleTypeId", issues);
    Long fingerprint = integer(
        object, safeSource, "expectedLayoutFingerprint", issues);
    Set<String> aiStations = stringSet(
        object, safeSource, "aiStationIds", issues);
    String flightActuatorId = optionalString(
        object, safeSource, "flightActuatorId", issues);
    Map<String, DnvSbwVehicleCrewAdapter.AimChannelBinding> aimChannels =
        aimChannels(object, safeSource, issues);
    Map<String, DnvStationEngagementCore.StationEngagementPolicy> policies =
        stationPolicies(object, safeSource, issues);
    if (!aiStations.containsAll(policies.keySet())) {
      issues.add(new Issue(safeSource, "stationPolicies", IssueCode.UNKNOWN_POLICY_STATION));
    }

    if (revision != null && revision < 0L) {
      issues.add(new Issue(safeSource, "revision", IssueCode.OUT_OF_RANGE));
    }
    if (fingerprint != null
        && (fingerprint < 0L || fingerprint > MAX_LAYOUT_FINGERPRINT)) {
      issues.add(new Issue(
          safeSource, "expectedLayoutFingerprint", IssueCode.OUT_OF_RANGE));
    }
    if (issues.size() != issueStart || !enabled) {
      return Optional.empty();
    }
    return Optional.of(new DnvSbwVehicleCrewAdapter.AllowlistEntry(
        adapterId,
        revision,
        vehicleTypeId,
        fingerprint,
        aiStations,
        flightActuatorId,
        aimChannels,
        policies));
  }

  /** Explicit profiles remain backwards compatible; disabled references never grant control. */
  private static boolean enabled(JsonObject object, String source, List<Issue> issues) {
    if (!object.has("enabled")) return true;
    JsonElement value = object.get("enabled");
    if (!(value instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
      issues.add(new Issue(source, "enabled", IssueCode.EXPECTED_BOOLEAN));
      return false;
    }
    return primitive.getAsBoolean();
  }

  private static Map<String, DnvStationEngagementCore.StationEngagementPolicy> stationPolicies(
      JsonObject object, String source, List<Issue> issues) {
    if (!object.has("stationPolicies")) return Map.of();
    JsonElement element = object.get("stationPolicies");
    if (!element.isJsonObject()) {
      issues.add(new Issue(source, "stationPolicies", IssueCode.EXPECTED_OBJECT));
      return Map.of();
    }
    JsonObject policies = element.getAsJsonObject();
    if (policies.size() > DnvVehicleCrewProfile.MAX_STATIONS) {
      issues.add(new Issue(source, "stationPolicies", IssueCode.TOO_MANY_STATIONS));
      return Map.of();
    }
    Map<String, DnvStationEngagementCore.StationEngagementPolicy> parsed = new LinkedHashMap<>();
    var defaults = DnvSbwVehicleCrewAdapter.defaultStationPolicy();
    for (var entry : policies.entrySet()) {
      String field = "stationPolicies." + entry.getKey();
      if (entry.getKey().isBlank() || entry.getKey().length() > MAX_ID_LENGTH
          || !entry.getValue().isJsonObject()) {
        issues.add(new Issue(source, field, IssueCode.INVALID_STATION_POLICY));
        continue;
      }
      JsonObject policy = entry.getValue().getAsJsonObject();
      int start = issues.size();
      for (String key : policy.keySet()) {
        if (!Set.of("targetingMode", "allowedTargetKinds", "minimumRange", "maximumRange").contains(key)) {
          issues.add(new Issue(source, field + "." + key, IssueCode.UNKNOWN_FIELD));
        }
      }
      String mode = string(policy, source, field + ".targetingMode", issues);
      Set<String> kinds = boundedStringSet(policy, source, field + ".allowedTargetKinds", 2, issues);
      Double minimum = finiteNumber(policy, source, field + ".minimumRange", issues);
      Double maximum = finiteNumber(policy, source, field + ".maximumRange", issues);
      if (issues.size() != start) continue;
      try {
        var targeting = DnvStationEngagementCore.TargetingMode.valueOf(mode);
        if (!Set.of(DnvStationEngagementCore.TargetingMode.SHARED_PREFERRED,
            DnvStationEngagementCore.TargetingMode.INDEPENDENT,
            DnvStationEngagementCore.TargetingMode.DESIGNATED_ONLY).contains(targeting)
            || maximum > DnvCombatPolicyRuntime.CREW_TARGET_RANGE
            || !Set.of("LIVING", "VEHICLE").containsAll(kinds)) {
          throw new IllegalArgumentException("unsupported policy");
        }
        Set<DnvStationEngagementCore.TargetKind> targetKinds = kinds.stream()
            .map(DnvStationEngagementCore.TargetKind::valueOf)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        parsed.put(entry.getKey(), new DnvStationEngagementCore.StationEngagementPolicy(
            targeting, targetKinds, minimum, maximum,
            defaults.switchAdvantage(), defaults.minimumHoldTicks(), defaults.lossGraceTicks()));
      } catch (IllegalArgumentException exception) {
        issues.add(new Issue(source, field, IssueCode.INVALID_STATION_POLICY));
      }
    }
    return Map.copyOf(parsed);
  }

  private static Double finiteNumber(
      JsonObject object, String source, String field, List<Issue> issues) {
    JsonElement element = object.get(simpleField(field));
    if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
      try {
        double value = primitive.getAsDouble();
        if (Double.isFinite(value)) return value;
      } catch (NumberFormatException exception) {
        // Report the field through the catalog's existing fail-closed reload receipt.
      }
    }
    issues.add(new Issue(source, field, IssueCode.EXPECTED_FINITE_NUMBER));
    return null;
  }

  private static Map<String, DnvSbwVehicleCrewAdapter.AimChannelBinding> aimChannels(
      JsonObject object, String source, List<Issue> issues) {
    if (!object.has("aimChannels")) {
      return Map.of();
    }
    JsonElement element = object.get("aimChannels");
    if (element == null || !element.isJsonObject()) {
      issues.add(new Issue(source, "aimChannels", IssueCode.EXPECTED_OBJECT));
      return Map.of();
    }
    JsonObject channels = element.getAsJsonObject();
    if (channels.size() > DnvVehicleCrewProfile.MAX_STATIONS) {
      issues.add(new Issue(source, "aimChannels", IssueCode.TOO_MANY_STATIONS));
      return Map.of();
    }
    Map<String, DnvSbwVehicleCrewAdapter.AimChannelBinding> parsed =
        new LinkedHashMap<>();
    for (String stationId : channels.keySet()) {
      String stationField = "aimChannels." + stationId;
      if (stationId.isBlank() || stationId.length() > MAX_ID_LENGTH) {
        issues.add(new Issue(source, stationField, IssueCode.INVALID_ID));
        continue;
      }
      JsonElement bindingElement = channels.get(stationId);
      if (bindingElement == null || !bindingElement.isJsonObject()) {
        issues.add(new Issue(source, stationField, IssueCode.EXPECTED_OBJECT));
        continue;
      }
      JsonObject binding = bindingElement.getAsJsonObject();
      for (String field : binding.keySet()) {
        if (!AIM_CHANNEL_FIELDS.contains(field)) {
          issues.add(new Issue(source, stationField + "." + field, IssueCode.UNKNOWN_FIELD));
        }
      }
      int issueStart = issues.size();
      String channelId = string(binding, source, stationField + ".aimChannelId", issues);
      Set<String> sharedWith = boundedStringSet(
          binding,
          source,
          stationField + ".sharedWith",
          DnvVehicleCrewProfile.MAX_STATIONS + 1,
          issues);
      if (issues.size() == issueStart) {
        try {
          parsed.put(
              stationId,
              new DnvSbwVehicleCrewAdapter.AimChannelBinding(channelId, sharedWith));
        } catch (IllegalArgumentException exception) {
          issues.add(new Issue(source, stationField, IssueCode.INVALID_ID));
        }
      }
    }
    return Collections.unmodifiableMap(parsed);
  }

  private static String optionalString(
      JsonObject object, String source, String field, List<Issue> issues) {
    return object.has(field) ? string(object, source, field, issues) : "";
  }

  private static String string(
      JsonObject object, String source, String field, List<Issue> issues) {
    JsonElement element = object.get(simpleField(field));
    if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
      issues.add(new Issue(source, field, IssueCode.EXPECTED_STRING));
      return null;
    }
    String value = primitive.getAsString().trim();
    if (value.isEmpty() || value.length() > MAX_ID_LENGTH) {
      issues.add(new Issue(source, field, IssueCode.INVALID_ID));
      return null;
    }
    return value;
  }

  private static Long integer(
      JsonObject object, String source, String field, List<Issue> issues) {
    JsonElement element = object.get(field);
    if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
      issues.add(new Issue(source, field, IssueCode.EXPECTED_INTEGER));
      return null;
    }
    try {
      BigDecimal decimal = primitive.getAsBigDecimal().stripTrailingZeros();
      if (decimal.scale() > 0) {
        throw new ArithmeticException("fractional");
      }
      return decimal.longValueExact();
    } catch (ArithmeticException | NumberFormatException exception) {
      issues.add(new Issue(source, field, IssueCode.EXPECTED_INTEGER));
      return null;
    }
  }

  private static Set<String> stringSet(
      JsonObject object, String source, String field, List<Issue> issues) {
    return boundedStringSet(
        object, source, field, DnvVehicleCrewProfile.MAX_STATIONS, issues);
  }

  private static Set<String> boundedStringSet(
      JsonObject object,
      String source,
      String field,
      int maximumSize,
      List<Issue> issues) {
    JsonElement element = object.get(simpleField(field));
    if (element == null || !element.isJsonArray()) {
      issues.add(new Issue(source, field, IssueCode.EXPECTED_ARRAY));
      return Set.of();
    }
    JsonArray array = element.getAsJsonArray();
    if (array.size() > maximumSize) {
      issues.add(new Issue(source, field, IssueCode.TOO_MANY_STATIONS));
      return Set.of();
    }
    Set<String> values = new LinkedHashSet<>();
    for (int index = 0; index < array.size(); index++) {
      JsonElement member = array.get(index);
      String memberField = field + "[" + index + "]";
      if (!(member instanceof JsonPrimitive primitive) || !primitive.isString()) {
        issues.add(new Issue(source, memberField, IssueCode.EXPECTED_STRING));
        continue;
      }
      String value = primitive.getAsString().trim();
      if (value.isEmpty() || value.length() > MAX_ID_LENGTH) {
        issues.add(new Issue(source, memberField, IssueCode.INVALID_ID));
      } else if (!values.add(value)) {
        issues.add(new Issue(source, memberField, IssueCode.DUPLICATE_STATION));
      }
    }
    return values;
  }

  private static String simpleField(String field) {
    int separator = field.lastIndexOf('.');
    return separator < 0 ? field : field.substring(separator + 1);
  }

  public record Snapshot(
      long revision,
      Map<String, DnvSbwVehicleCrewAdapter.AllowlistEntry> entries) {
    public Snapshot {
      if (revision < 0L) {
        throw new IllegalArgumentException("catalog revision must not be negative");
      }
      entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }
  }

  public record ReloadResult(boolean accepted, Snapshot snapshot, List<Issue> issues) {
    public ReloadResult {
      Objects.requireNonNull(snapshot, "snapshot");
      issues = List.copyOf(issues);
      if (accepted == !issues.isEmpty()) {
        throw new IllegalArgumentException("inconsistent catalog reload result");
      }
    }

    private static ReloadResult accepted(Snapshot snapshot) {
      return new ReloadResult(true, snapshot, List.of());
    }

    private static ReloadResult rejected(Snapshot snapshot, List<Issue> issues) {
      return new ReloadResult(false, snapshot, issues);
    }
  }

  private record ParseResult(
      Map<String, DnvSbwVehicleCrewAdapter.AllowlistEntry> entries,
      List<Issue> issues) {
    private ParseResult {
      entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
      issues = List.copyOf(issues);
    }
  }

  public record Issue(String source, String field, IssueCode code) {
    public Issue {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(code, "code");
    }
  }

  public enum IssueCode {
    UNKNOWN_POLICY_STATION,
    INVALID_STATION_POLICY,
    EXPECTED_FINITE_NUMBER,
    STALE_CATALOG_REVISION,
    TOO_MANY_ENTRIES,
    EXPECTED_OBJECT,
    UNKNOWN_FIELD,
    EXPECTED_STRING,
    EXPECTED_INTEGER,
    EXPECTED_BOOLEAN,
    EXPECTED_ARRAY,
    INVALID_ID,
    UNSUPPORTED_SCHEMA,
    OUT_OF_RANGE,
    TOO_MANY_STATIONS,
    DUPLICATE_STATION,
    DUPLICATE_VEHICLE_TYPE,
    DUPLICATE_ADAPTER_ID
  }
}
