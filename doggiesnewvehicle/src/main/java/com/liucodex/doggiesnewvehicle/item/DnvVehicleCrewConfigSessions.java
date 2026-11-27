package com.liucodex.doggiesnewvehicle.item;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.compat.DnvSbwVehicleCrewAdapter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Per-server, expiring authority for editing one held portable crew core. */
public final class DnvVehicleCrewConfigSessions {
  public static final int MAX_SESSION_AGE_TICKS = 1_200;
  private static final List<DnvVehicleCrewProfile.FlightMode> SBW_FLIGHT_MODES = List.of(
      DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
      DnvVehicleCrewProfile.FlightMode.MANUAL_ONLY);
  private static final List<DnvVehicleCrewProfile.FlightMode> AI_FLIGHT_MODES = List.of(
      DnvVehicleCrewProfile.FlightMode.PLAYER_PREFERRED,
      DnvVehicleCrewProfile.FlightMode.AI_PREFERRED,
      DnvVehicleCrewProfile.FlightMode.MANUAL_ONLY);
  private static final List<DnvControlAuthority.WeaponMode> AI_WEAPON_MODES = List.of(
      DnvControlAuthority.WeaponMode.AUTO_ENGAGE,
      DnvControlAuthority.WeaponMode.TRACK_ONLY,
      DnvControlAuthority.WeaponMode.MANUAL_PREFERRED,
      DnvControlAuthority.WeaponMode.MANUAL_ONLY,
      DnvControlAuthority.WeaponMode.HOLD_FIRE);
  private static final List<DnvControlAuthority.WeaponMode> MANUAL_WEAPON_MODES = List.of(
      DnvControlAuthority.WeaponMode.MANUAL_ONLY,
      DnvControlAuthority.WeaponMode.HOLD_FIRE);

  private final Map<UUID, Session> sessions = new LinkedHashMap<>();
  private final Map<UUID, UUID> playerSessions = new LinkedHashMap<>();

  public OpenResult open(
      DnvVehicleCrewProfile.Draft draft,
      DnvSbwVehicleCrewAdapter.AllowlistEntry allowlist,
      UUID playerId,
      boolean admin,
      long catalogRevision,
      long nowTick,
      HandSlot hand) {
    Objects.requireNonNull(draft, "draft");
    Objects.requireNonNull(allowlist, "allowlist");
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(hand, "hand");
    if (catalogRevision < 0L || nowTick < 0L) {
      return OpenResult.rejected(Issue.INVALID_TIME_OR_REVISION);
    }
    if (!admin && !playerId.equals(draft.configuredBy())) {
      return OpenResult.rejected(Issue.NOT_OWNER);
    }
    Optional<DnvVehicleCrewProfile.AdapterTemplate> template = template(draft, allowlist);
    if (template.isEmpty()) {
      return OpenResult.rejected(Issue.CATALOG_MISMATCH);
    }
    DnvVehicleCrewProfile.ValidationResult validation = DnvVehicleCrewProfile.validate(
        draft,
        template.orElseThrow(),
        new DnvVehicleCrewProfile.ValidationContext(playerId, admin));
    if (!validation.accepted()) {
      return OpenResult.rejected(Issue.PROFILE_REJECTED);
    }
    DnvVehicleCrewProfile.Snapshot snapshot = validation.snapshot().orElseThrow();
    UUID previous = playerSessions.remove(playerId);
    if (previous != null) {
      sessions.remove(previous);
    }
    UUID token = UUID.randomUUID();
    Session session = new Session(
        token,
        playerId,
        catalogRevision,
        nowTick,
        hand,
        draft(snapshot),
        template.orElseThrow());
    sessions.put(token, session);
    playerSessions.put(playerId, token);
    return new OpenResult(Optional.of(view(session, snapshot)), Issue.NONE);
  }

  public CommitResult commit(
      UUID token,
      UUID playerId,
      boolean admin,
      long catalogRevision,
      long nowTick,
      HandSlot hand,
      DnvVehicleCrewProfile.Draft currentDraft,
      DnvVehicleCrewProfile.FlightMode flightMode,
      List<DnvVehicleCrewProfile.StationDraft> stations) {
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(hand, "hand");
    Objects.requireNonNull(currentDraft, "currentDraft");
    Objects.requireNonNull(stations, "stations");
    Session session = sessions.get(token);
    if (session == null) {
      return CommitResult.rejected(Issue.STALE_SESSION);
    }
    if (!playerId.equals(session.playerId()) || hand != session.hand()) {
      return CommitResult.rejected(Issue.SESSION_OWNER_MISMATCH);
    }
    if (expired(session, nowTick)) {
      close(token);
      return CommitResult.rejected(Issue.SESSION_EXPIRED);
    }
    if (catalogRevision != session.catalogRevision()) {
      close(token);
      return CommitResult.rejected(Issue.CATALOG_CHANGED);
    }
    if (!session.originalDraft().equals(currentDraft)) {
      close(token);
      return CommitResult.rejected(Issue.CORE_CHANGED);
    }
    if (flightMode == null || !session.template().allowedFlightModes().contains(flightMode)) {
      return CommitResult.rejected(Issue.FLIGHT_MODE_NOT_ALLOWED);
    }
    List<DnvVehicleCrewProfile.StationDraft> ordered = canonicalStations(
        session.template(), stations);
    if (ordered.isEmpty() && !session.template().stations().isEmpty()) {
      return CommitResult.rejected(Issue.STATIONS_CHANGED);
    }
    DnvVehicleCrewProfile.Draft updated = new DnvVehicleCrewProfile.Draft(
        currentDraft.schemaVersion(),
        currentDraft.coreId(),
        currentDraft.configuredBy(),
        currentDraft.adapterId(),
        currentDraft.adapterRevision(),
        currentDraft.vehicleTypeId(),
        flightMode,
        ordered);
    DnvVehicleCrewProfile.ValidationResult validation = DnvVehicleCrewProfile.validate(
        updated,
        session.template(),
        new DnvVehicleCrewProfile.ValidationContext(playerId, admin));
    if (!validation.accepted()) {
      return CommitResult.rejected(Issue.PROFILE_REJECTED);
    }
    close(token);
    return new CommitResult(validation.snapshot(), Issue.NONE);
  }

  public CommitResult commitWireValues(
      UUID token,
      UUID playerId,
      boolean admin,
      long catalogRevision,
      long nowTick,
      HandSlot hand,
      DnvVehicleCrewProfile.Draft currentDraft,
      String flightModeName,
      List<String> stationIds,
      List<String> stationModeNames) {
    Objects.requireNonNull(flightModeName, "flightModeName");
    Objects.requireNonNull(stationIds, "stationIds");
    Objects.requireNonNull(stationModeNames, "stationModeNames");
    if (stationIds.size() != stationModeNames.size()
        || stationIds.size() > DnvVehicleCrewProfile.MAX_STATIONS) {
      return CommitResult.rejected(Issue.STATIONS_CHANGED);
    }
    try {
      DnvVehicleCrewProfile.FlightMode flightMode =
          DnvVehicleCrewProfile.FlightMode.valueOf(flightModeName);
      List<DnvVehicleCrewProfile.StationDraft> stations = new ArrayList<>();
      for (int index = 0; index < stationIds.size(); index++) {
        stations.add(new DnvVehicleCrewProfile.StationDraft(
            stationIds.get(index),
            DnvControlAuthority.WeaponMode.valueOf(stationModeNames.get(index))));
      }
      return commit(
          token, playerId, admin, catalogRevision, nowTick, hand,
          currentDraft, flightMode, stations);
    } catch (IllegalArgumentException exception) {
      return CommitResult.rejected(Issue.PROFILE_REJECTED);
    }
  }

  public void closePlayer(UUID playerId) {
    UUID token = playerSessions.remove(Objects.requireNonNull(playerId, "playerId"));
    if (token != null) {
      sessions.remove(token);
    }
  }

  public boolean close(UUID token) {
    Session removed = sessions.remove(Objects.requireNonNull(token, "token"));
    if (removed == null) {
      return false;
    }
    playerSessions.remove(removed.playerId(), token);
    return true;
  }

  public boolean close(UUID token, UUID playerId) {
    Session session = sessions.get(Objects.requireNonNull(token, "token"));
    if (session == null
        || !session.playerId().equals(Objects.requireNonNull(playerId, "playerId"))) {
      return false;
    }
    return close(token);
  }

  public int expire(long nowTick) {
    List<UUID> expired = sessions.values().stream()
        .filter(session -> expired(session, nowTick))
        .map(Session::token)
        .toList();
    expired.forEach(this::close);
    return expired.size();
  }

  public void clear() {
    sessions.clear();
    playerSessions.clear();
  }

  int size() {
    return sessions.size();
  }

  private static boolean expired(Session session, long nowTick) {
    return nowTick < session.openedAtTick()
        || nowTick - session.openedAtTick() > MAX_SESSION_AGE_TICKS;
  }

  private static Optional<DnvVehicleCrewProfile.AdapterTemplate> template(
      DnvVehicleCrewProfile.Draft draft,
      DnvSbwVehicleCrewAdapter.AllowlistEntry allowlist) {
    if (draft.schemaVersion() != DnvVehicleCrewProfile.CURRENT_SCHEMA_VERSION
        || draft.coreId() == null
        || draft.configuredBy() == null
        || !allowlist.adapterId().equals(normalize(draft.adapterId()))
        || allowlist.revision() != draft.adapterRevision()
        || !allowlist.vehicleTypeId().equals(normalize(draft.vehicleTypeId()))
        || draft.stations() == null
        || draft.stations().size() > DnvVehicleCrewProfile.MAX_STATIONS) {
      return Optional.empty();
    }
    List<DnvVehicleCrewProfile.StationTemplate> stations = new ArrayList<>();
    Set<String> ids = new LinkedHashSet<>();
    Set<Integer> seats = new LinkedHashSet<>();
    for (DnvVehicleCrewProfile.StationDraft station : draft.stations()) {
      if (station == null) {
        return Optional.empty();
      }
      String id = normalize(station.stationId());
      int seat = seatIndex(id);
      if (seat < 0 || !ids.add(id) || !seats.add(seat)) {
        return Optional.empty();
      }
      boolean aiCapable = allowlist.aiStationIds().contains(id);
      List<DnvControlAuthority.WeaponMode> modes = aiCapable
          ? AI_WEAPON_MODES
          : MANUAL_WEAPON_MODES;
      stations.add(new DnvVehicleCrewProfile.StationTemplate(
          id, seat, aiCapable, EnumSet.copyOf(modes)));
    }
    if (!ids.containsAll(allowlist.aiStationIds())) {
      return Optional.empty();
    }
    return Optional.of(new DnvVehicleCrewProfile.AdapterTemplate(
        allowlist.adapterId(),
        allowlist.revision(),
        allowlist.vehicleTypeId(),
        DnvSbwVehicleCrewAdapter.resolveFlightActuator(allowlist).isPresent(),
        DnvSbwVehicleCrewAdapter.resolveFlightActuator(allowlist).isPresent()
            ? AI_FLIGHT_MODES
            : SBW_FLIGHT_MODES,
        stations));
  }

  private static List<DnvVehicleCrewProfile.StationDraft> canonicalStations(
      DnvVehicleCrewProfile.AdapterTemplate template,
      List<DnvVehicleCrewProfile.StationDraft> requested) {
    if (requested.size() != template.stations().size()) {
      return List.of();
    }
    Map<String, DnvControlAuthority.WeaponMode> modes = new LinkedHashMap<>();
    for (DnvVehicleCrewProfile.StationDraft station : requested) {
      if (station == null
          || modes.putIfAbsent(normalize(station.stationId()), station.mode()) != null) {
        return List.of();
      }
    }
    List<DnvVehicleCrewProfile.StationDraft> result = new ArrayList<>();
    for (DnvVehicleCrewProfile.StationTemplate station : template.stations()) {
      DnvControlAuthority.WeaponMode mode = modes.get(station.id());
      if (mode == null || !station.allowedModes().contains(mode)) {
        return List.of();
      }
      result.add(new DnvVehicleCrewProfile.StationDraft(station.id(), mode));
    }
    return List.copyOf(result);
  }

  private static View view(Session session, DnvVehicleCrewProfile.Snapshot snapshot) {
    List<StationView> stations = snapshot.stations().values().stream()
        .map(station -> new StationView(
            station.id(),
            station.seatIndex(),
            station.aiCapable(),
            station.mode(),
            station.aiCapable() ? AI_WEAPON_MODES : MANUAL_WEAPON_MODES))
        .toList();
    return new View(
        session.token(),
        session.hand(),
        snapshot.coreId(),
        snapshot.vehicleTypeId(),
        snapshot.flightMode(),
        allowedFlightModes(session),
        stations);
  }

  private static List<DnvVehicleCrewProfile.FlightMode> allowedFlightModes(Session session) {
    return List.copyOf(session.template().allowedFlightModes());
  }

  private static DnvVehicleCrewProfile.Draft draft(
      DnvVehicleCrewProfile.Snapshot snapshot) {
    return new DnvVehicleCrewProfile.Draft(
        snapshot.schemaVersion(),
        snapshot.coreId(),
        snapshot.configuredBy(),
        snapshot.adapterId(),
        snapshot.adapterRevision(),
        snapshot.vehicleTypeId(),
        snapshot.flightMode(),
        snapshot.stations().values().stream()
            .map(station -> new DnvVehicleCrewProfile.StationDraft(
                station.id(), station.mode()))
            .toList());
  }

  private static int seatIndex(String stationId) {
    if (!stationId.startsWith("seat_") || stationId.length() <= 5) {
      return -1;
    }
    try {
      int value = Integer.parseInt(stationId.substring(5));
      return value < 0 ? -1 : value;
    } catch (NumberFormatException exception) {
      return -1;
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private record Session(
      UUID token,
      UUID playerId,
      long catalogRevision,
      long openedAtTick,
      HandSlot hand,
      DnvVehicleCrewProfile.Draft originalDraft,
      DnvVehicleCrewProfile.AdapterTemplate template) {}

  public record View(
      UUID token,
      HandSlot hand,
      UUID coreId,
      String vehicleTypeId,
      DnvVehicleCrewProfile.FlightMode flightMode,
      List<DnvVehicleCrewProfile.FlightMode> allowedFlightModes,
      List<StationView> stations) {
    public View {
      Objects.requireNonNull(token, "token");
      Objects.requireNonNull(hand, "hand");
      Objects.requireNonNull(coreId, "coreId");
      vehicleTypeId = Objects.requireNonNull(vehicleTypeId, "vehicleTypeId");
      Objects.requireNonNull(flightMode, "flightMode");
      allowedFlightModes = List.copyOf(allowedFlightModes);
      stations = List.copyOf(stations);
    }
  }

  public record StationView(
      String stationId,
      int seatIndex,
      boolean aiCapable,
      DnvControlAuthority.WeaponMode mode,
      List<DnvControlAuthority.WeaponMode> allowedModes) {
    public StationView {
      stationId = Objects.requireNonNull(stationId, "stationId");
      Objects.requireNonNull(mode, "mode");
      allowedModes = List.copyOf(allowedModes);
    }
    public String modeName() { return mode.name(); }
    public List<String> allowedModeNames() { return allowedModes.stream().map(Enum::name).toList(); }
  }

  public record OpenResult(Optional<View> view, Issue issue) {
    public OpenResult {
      view = Objects.requireNonNull(view, "view");
      Objects.requireNonNull(issue, "issue");
      if (view.isPresent() != (issue == Issue.NONE)) {
        throw new IllegalArgumentException("inconsistent open result");
      }
    }

    public boolean accepted() {
      return view.isPresent();
    }

    private static OpenResult rejected(Issue issue) {
      return new OpenResult(Optional.empty(), issue);
    }
  }

  public record CommitResult(
      Optional<DnvVehicleCrewProfile.Snapshot> snapshot, Issue issue) {
    public CommitResult {
      snapshot = Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(issue, "issue");
      if (snapshot.isPresent() != (issue == Issue.NONE)) {
        throw new IllegalArgumentException("inconsistent commit result");
      }
    }

    public boolean accepted() {
      return snapshot.isPresent();
    }

    public Optional<String> flightModeName() {
      return snapshot.map(value -> value.flightMode().name());
    }

    public List<String> stationIds() {
      return snapshot.map(value -> value.stations().values().stream()
          .map(DnvVehicleCrewProfile.StationPolicy::id)
          .toList()).orElse(List.of());
    }

    public List<String> stationModeNames() {
      return snapshot.map(value -> value.stations().values().stream()
          .map(station -> station.mode().name())
          .toList()).orElse(List.of());
    }

    private static CommitResult rejected(Issue issue) {
      return new CommitResult(Optional.empty(), issue);
    }
  }

  public enum HandSlot {
    MAIN_HAND,
    OFF_HAND
  }

  public enum Issue {
    NONE,
    INVALID_TIME_OR_REVISION,
    NOT_OWNER,
    CATALOG_MISMATCH,
    PROFILE_REJECTED,
    STALE_SESSION,
    SESSION_OWNER_MISMATCH,
    SESSION_EXPIRED,
    CATALOG_CHANGED,
    CORE_CHANGED,
    FLIGHT_MODE_NOT_ALLOWED,
    STATIONS_CHANGED
  }
}
