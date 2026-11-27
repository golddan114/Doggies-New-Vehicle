package com.liucodex.doggiesnewvehicle.ai;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Validates the portable configuration stored by a future DNV vehicle crew core item. */
public final class DnvVehicleCrewProfile {
  public static final int CURRENT_SCHEMA_VERSION = 1;
  public static final int MAX_STATIONS = 32;

  private DnvVehicleCrewProfile() {}

  /** Default bind policy: player retains flight, AI-capable empty stations auto-engage. */
  public static Draft createDefaultDraft(
      UUID coreId, UUID configuredBy, AdapterTemplate template) {
    Objects.requireNonNull(template, "template");
    List<StationDraft> stations = template.stations().stream()
        .map(station -> new StationDraft(
            station.id(),
            station.aiCapable()
                ? DnvControlAuthority.WeaponMode.AUTO_ENGAGE
                : DnvControlAuthority.WeaponMode.MANUAL_ONLY))
        .toList();
    return new Draft(
        CURRENT_SCHEMA_VERSION,
        Objects.requireNonNull(coreId, "coreId"),
        Objects.requireNonNull(configuredBy, "configuredBy"),
        template.id(),
        template.revision(),
        template.vehicleTypeId(),
        FlightMode.PLAYER_PREFERRED,
        stations);
  }

  public static ValidationResult validate(
      Draft draft, AdapterTemplate template, ValidationContext context) {
    Objects.requireNonNull(draft, "draft");
    Objects.requireNonNull(template, "template");
    Objects.requireNonNull(context, "context");
    List<Issue> issues = new ArrayList<>();

    if (draft.schemaVersion() != CURRENT_SCHEMA_VERSION) {
      issues.add(issue("schemaVersion", IssueCode.UNSUPPORTED_SCHEMA));
    }
    if (draft.coreId() == null) {
      issues.add(issue("coreId", IssueCode.REQUIRED));
    }
    if (draft.configuredBy() == null) {
      issues.add(issue("configuredBy", IssueCode.REQUIRED));
    } else if (!context.admin() && !draft.configuredBy().equals(context.callerId())) {
      issues.add(issue("configuredBy", IssueCode.NOT_CALLER));
    }
    if (!template.id().equals(normalize(draft.adapterId()))) {
      issues.add(issue("adapterId", IssueCode.UNKNOWN_ADAPTER));
    }
    if (draft.adapterRevision() != template.revision()) {
      issues.add(issue("adapterRevision", IssueCode.STALE_ADAPTER));
    }
    if (!template.vehicleTypeId().equals(normalize(draft.vehicleTypeId()))) {
      issues.add(issue("vehicleTypeId", IssueCode.WRONG_VEHICLE_TYPE));
    }
    FlightMode flightMode = draft.flightMode();
    if (flightMode == null) {
      issues.add(issue("flightMode", IssueCode.REQUIRED));
    } else if (!template.allowedFlightModes().contains(flightMode)) {
      issues.add(issue(
          "flightMode",
          flightMode.aiControlled() && !template.aiFlightCapable()
              ? IssueCode.AI_FLIGHT_UNSUPPORTED
              : IssueCode.FLIGHT_MODE_NOT_ALLOWED));
    }

    List<StationDraft> requested = draft.stations() == null ? List.of() : draft.stations();
    Map<String, StationTemplate> templateStations = template.stationsById();
    if (requested.size() > MAX_STATIONS) {
      issues.add(issue("stations", IssueCode.TOO_MANY_STATIONS));
    }
    Map<String, StationPolicy> policies = new LinkedHashMap<>();
    for (int index = 0; index < requested.size(); index++) {
      StationDraft station = requested.get(index);
      String field = "stations[" + index + "]";
      if (station == null) {
        issues.add(issue(field, IssueCode.REQUIRED));
        continue;
      }
      String stationId = normalize(station.stationId());
      StationTemplate allowed = templateStations.get(stationId);
      if (allowed == null) {
        issues.add(issue(field + ".stationId", IssueCode.UNKNOWN_STATION));
        continue;
      }
      if (policies.containsKey(stationId)) {
        issues.add(issue(field + ".stationId", IssueCode.DUPLICATE_STATION));
        continue;
      }
      DnvControlAuthority.WeaponMode mode = station.mode();
      if (mode == null) {
        issues.add(issue(field + ".mode", IssueCode.REQUIRED));
        continue;
      }
      if (!allowed.allowedModes().contains(mode)
          || (!allowed.aiCapable() && isAiMode(mode))) {
        issues.add(issue(field + ".mode", IssueCode.MODE_NOT_ALLOWED));
        continue;
      }
      policies.put(stationId, new StationPolicy(
          stationId, allowed.seatIndex(), allowed.aiCapable(), mode));
    }
    for (String required : templateStations.keySet()) {
      if (!policies.containsKey(required)) {
        issues.add(issue("stations." + required, IssueCode.MISSING_STATION));
      }
    }
    if (!issues.isEmpty()) {
      return new ValidationResult(Optional.empty(), List.copyOf(issues));
    }
    Snapshot snapshot = new Snapshot(
        CURRENT_SCHEMA_VERSION,
        draft.coreId(),
        draft.configuredBy(),
        template.id(),
        template.revision(),
        template.vehicleTypeId(),
        flightMode,
        policies);
    return new ValidationResult(Optional.of(snapshot), List.of());
  }

  private static boolean isAiMode(DnvControlAuthority.WeaponMode mode) {
    return mode == DnvControlAuthority.WeaponMode.AUTO_ENGAGE
        || mode == DnvControlAuthority.WeaponMode.TRACK_ONLY
        || mode == DnvControlAuthority.WeaponMode.MANUAL_PREFERRED;
  }

  private static Issue issue(String field, IssueCode code) {
    return new Issue(field, code);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  public record Draft(
      int schemaVersion,
      UUID coreId,
      UUID configuredBy,
      String adapterId,
      long adapterRevision,
      String vehicleTypeId,
      FlightMode flightMode,
      List<StationDraft> stations) {}

  public record StationDraft(String stationId, DnvControlAuthority.WeaponMode mode) {
    public static StationDraft fromModeName(String stationId, String mode) {
      return new StationDraft(stationId, DnvControlAuthority.WeaponMode.valueOf(mode));
    }
    public String modeName() {
      return mode.name();
    }
  }

  public record AdapterTemplate(
      String id,
      long revision,
      String vehicleTypeId,
      boolean aiFlightCapable,
      Collection<FlightMode> allowedFlightModes,
      Collection<StationTemplate> stations) {
    public AdapterTemplate {
      id = requireId(id, "adapter id");
      vehicleTypeId = requireId(vehicleTypeId, "vehicle type id");
      if (revision < 0) {
        throw new IllegalArgumentException("adapter revision must not be negative");
      }
      Objects.requireNonNull(allowedFlightModes, "allowedFlightModes");
      if (allowedFlightModes.isEmpty()
          || allowedFlightModes.stream().anyMatch(Objects::isNull)
          || (!aiFlightCapable
              && allowedFlightModes.stream().anyMatch(FlightMode::aiControlled))) {
        throw new IllegalArgumentException("invalid allowed flight modes");
      }
      allowedFlightModes = List.copyOf(new LinkedHashSet<>(allowedFlightModes));
      Objects.requireNonNull(stations, "stations");
      if (stations.size() > MAX_STATIONS) {
        throw new IllegalArgumentException("too many station templates");
      }
      LinkedHashSet<String> ids = new LinkedHashSet<>();
      LinkedHashSet<Integer> seatIndexes = new LinkedHashSet<>();
      for (StationTemplate station : stations) {
        Objects.requireNonNull(station, "station template");
        if (!ids.add(station.id())) {
          throw new IllegalArgumentException("duplicate station template: " + station.id());
        }
        if (!seatIndexes.add(station.seatIndex())) {
          throw new IllegalArgumentException(
              "duplicate seat index: " + station.seatIndex());
        }
      }
      stations = List.copyOf(stations);
    }

    public AdapterTemplate(
        String id,
        long revision,
        String vehicleTypeId,
        boolean aiFlightCapable,
        Collection<StationTemplate> stations) {
      this(
          id,
          revision,
          vehicleTypeId,
          aiFlightCapable,
          aiFlightCapable
              ? List.of(FlightMode.PLAYER_PREFERRED, FlightMode.AI_PREFERRED,
                  FlightMode.AI_ONLY, FlightMode.MANUAL_ONLY)
              : List.of(FlightMode.PLAYER_PREFERRED, FlightMode.MANUAL_ONLY),
          stations);
    }

    private Map<String, StationTemplate> stationsById() {
      Map<String, StationTemplate> result = new LinkedHashMap<>();
      for (StationTemplate station : stations) {
        result.put(station.id(), station);
      }
      return result;
    }
  }

  public record StationTemplate(
      String id,
      int seatIndex,
      boolean aiCapable,
      Set<DnvControlAuthority.WeaponMode> allowedModes) {
    public StationTemplate {
      id = requireId(id, "station id");
      if (seatIndex < 0) {
        throw new IllegalArgumentException("seat index must not be negative");
      }
      Objects.requireNonNull(allowedModes, "allowedModes");
      if (allowedModes.isEmpty()) {
        throw new IllegalArgumentException("allowedModes must not be empty");
      }
      allowedModes = Set.copyOf(allowedModes);
    }
  }

  public record ValidationContext(UUID callerId, boolean admin) {
    public ValidationContext {
      Objects.requireNonNull(callerId, "callerId");
    }
  }

  public record Snapshot(
      int schemaVersion,
      UUID coreId,
      UUID configuredBy,
      String adapterId,
      long adapterRevision,
      String vehicleTypeId,
      FlightMode flightMode,
      Map<String, StationPolicy> stations) {
    public Snapshot {
      Objects.requireNonNull(coreId, "coreId");
      Objects.requireNonNull(configuredBy, "configuredBy");
      adapterId = requireId(adapterId, "adapter id");
      vehicleTypeId = requireId(vehicleTypeId, "vehicle type id");
      Objects.requireNonNull(flightMode, "flightMode");
      Objects.requireNonNull(stations, "stations");
      stations = Collections.unmodifiableMap(new LinkedHashMap<>(stations));
    }
  }

  public record StationPolicy(
      String id,
      int seatIndex,
      boolean aiCapable,
      DnvControlAuthority.WeaponMode mode) {}

  public record ValidationResult(Optional<Snapshot> snapshot, List<Issue> issues) {
    public ValidationResult {
      snapshot = Objects.requireNonNull(snapshot, "snapshot");
      issues = List.copyOf(issues);
    }

    public boolean accepted() {
      return snapshot.isPresent() && issues.isEmpty();
    }
  }

  public record Issue(String field, IssueCode code) {}

  public enum FlightMode {
    PLAYER_PREFERRED(false),
    AI_PREFERRED(true),
    AI_ONLY(true),
    MANUAL_ONLY(false);

    private final boolean aiControlled;

    FlightMode(boolean aiControlled) {
      this.aiControlled = aiControlled;
    }

    public boolean aiControlled() {
      return aiControlled;
    }
  }

  public enum IssueCode {
    REQUIRED,
    UNSUPPORTED_SCHEMA,
    NOT_CALLER,
    UNKNOWN_ADAPTER,
    STALE_ADAPTER,
    WRONG_VEHICLE_TYPE,
    AI_FLIGHT_UNSUPPORTED,
    FLIGHT_MODE_NOT_ALLOWED,
    TOO_MANY_STATIONS,
    UNKNOWN_STATION,
    DUPLICATE_STATION,
    MISSING_STATION,
    MODE_NOT_ALLOWED
  }

  private static String requireId(String value, String label) {
    String normalized = normalize(value);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return normalized;
  }
}
