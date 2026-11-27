package com.liucodex.doggiesnewvehicle.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side, per-vehicle control owner. Flight and weapon leases are deliberately independent so
 * a player pilot never disables unoccupied automatic turrets.
 */
public final class DnvControlAuthority {
  public static final int MAX_LEASE_TICKS = 1_200;
  public static final int MAX_AI_RESUME_DELAY_TICKS = 200;

  private final Map<String, WeaponStation> stations = new LinkedHashMap<>();
  private PlayerLease flightLease;
  private long revision;

  public DnvControlAuthority(Collection<StationDefinition> definitions) {
    Objects.requireNonNull(definitions, "definitions");
    for (StationDefinition definition : definitions) {
      Objects.requireNonNull(definition, "station definition");
      String id = requireStationId(definition.id());
      if (stations.putIfAbsent(id, new WeaponStation(definition)) != null) {
        throw new IllegalArgumentException("duplicate weapon station: " + id);
      }
    }
  }

  public long revision() {
    return revision;
  }

  public Set<String> stationIds() {
    return Collections.unmodifiableSet(new LinkedHashSet<>(stations.keySet()));
  }

  public ControlOwner flightOwner(long serverTick) {
    return isActive(flightLease, serverTick) ? ControlOwner.PLAYER : ControlOwner.AI;
  }

  public Optional<UUID> flightPlayer(long serverTick) {
    return isActive(flightLease, serverTick)
        ? Optional.of(flightLease.playerId())
        : Optional.empty();
  }

  public MutationResult claimFlight(
      UUID playerId, long serverTick, int leaseTicks, long expectedRevision) {
    RejectReason invalid = validateMutation(playerId, leaseTicks, expectedRevision);
    if (invalid != RejectReason.NONE) {
      return rejected(invalid);
    }
    if (isActive(flightLease, serverTick) && !flightLease.playerId().equals(playerId)) {
      return rejected(RejectReason.OCCUPIED);
    }
    flightLease = new PlayerLease(playerId, expiresAt(serverTick, leaseTicks));
    return accepted();
  }

  public MutationResult releaseFlight(UUID playerId, long expectedRevision) {
    RejectReason invalid = validateRevisionAndPlayer(playerId, expectedRevision);
    if (invalid != RejectReason.NONE) {
      return rejected(invalid);
    }
    if (flightLease == null || !flightLease.playerId().equals(playerId)) {
      return rejected(RejectReason.NOT_OWNER);
    }
    flightLease = null;
    return accepted();
  }

  public MutationResult claimWeapon(
      String stationId,
      UUID playerId,
      long serverTick,
      int leaseTicks,
      long expectedRevision) {
    RejectReason invalid = validateMutation(playerId, leaseTicks, expectedRevision);
    if (invalid != RejectReason.NONE) {
      return rejected(invalid);
    }
    WeaponStation station = stations.get(stationId);
    if (station == null) {
      return rejected(RejectReason.UNKNOWN_STATION);
    }
    if (station.mode == WeaponMode.HOLD_FIRE) {
      return rejected(RejectReason.HOLD_FIRE);
    }
    if (station.physicalOccupant != null
        && !station.physicalOccupant.equals(playerId)) {
      return rejected(RejectReason.OCCUPIED);
    }
    if (isActive(station.lease, serverTick) && !station.lease.playerId().equals(playerId)) {
      return rejected(RejectReason.OCCUPIED);
    }
    station.lease = new PlayerLease(playerId, expiresAt(serverTick, leaseTicks));
    return accepted();
  }

  public MutationResult releaseWeapon(
      String stationId, UUID playerId, long expectedRevision) {
    RejectReason invalid = validateRevisionAndPlayer(playerId, expectedRevision);
    if (invalid != RejectReason.NONE) {
      return rejected(invalid);
    }
    WeaponStation station = stations.get(stationId);
    if (station == null) {
      return rejected(RejectReason.UNKNOWN_STATION);
    }
    if (station.lease == null || !station.lease.playerId().equals(playerId)) {
      return rejected(RejectReason.NOT_OWNER);
    }
    station.lease = null;
    return accepted();
  }

  public MutationResult setWeaponMode(
      String stationId, WeaponMode mode, long expectedRevision) {
    if (expectedRevision != revision) {
      return rejected(RejectReason.STALE_REVISION);
    }
    WeaponStation station = stations.get(stationId);
    if (station == null) {
      return rejected(RejectReason.UNKNOWN_STATION);
    }
    WeaponMode nextMode = Objects.requireNonNull(mode, "mode");
    if (station.mode == nextMode) {
      return new MutationResult(true, RejectReason.NONE, revision);
    }
    station.mode = nextMode;
    if (nextMode == WeaponMode.HOLD_FIRE) {
      station.lease = null;
    }
    return accepted();
  }

  /**
   * Synchronizes the authoritative physical seat occupant. A real passenger always preempts a
   * remote lease for the same station; after vacating, AI waits for the configured safety delay.
   */
  public MutationResult synchronizePhysicalOccupant(
      String stationId,
      Optional<UUID> occupant,
      long serverTick,
      int aiResumeDelayTicks,
      long expectedRevision) {
    Objects.requireNonNull(occupant, "occupant");
    if (expectedRevision != revision) {
      return rejected(RejectReason.STALE_REVISION);
    }
    if (aiResumeDelayTicks < 0 || aiResumeDelayTicks > MAX_AI_RESUME_DELAY_TICKS) {
      return rejected(RejectReason.INVALID_RESUME_DELAY);
    }
    WeaponStation station = stations.get(stationId);
    if (station == null) {
      return rejected(RejectReason.UNKNOWN_STATION);
    }
    UUID next = occupant.orElse(null);
    if (Objects.equals(station.physicalOccupant, next)) {
      return new MutationResult(true, RejectReason.NONE, revision);
    }
    station.physicalOccupant = next;
    station.lease = null;
    station.aiResumeAtTick = next == null
        ? expiresAt(serverTick, aiResumeDelayTicks)
        : Long.MAX_VALUE;
    return accepted();
  }

  public StationAuthority weaponAuthority(String stationId, long serverTick) {
    WeaponStation station = stations.get(stationId);
    if (station == null) {
      throw new IllegalArgumentException("unknown weapon station: " + stationId);
    }
    if (station.physicalOccupant != null) {
      boolean mayTrack = station.mode != WeaponMode.HOLD_FIRE;
      boolean mayFire = mayTrack && station.mode != WeaponMode.TRACK_ONLY;
      return new StationAuthority(
          ControlOwner.PLAYER,
          Optional.of(station.physicalOccupant),
          station.mode,
          mayTrack,
          mayFire,
          revision);
    }
    if (isActive(station.lease, serverTick)) {
      boolean mayFire = station.mode != WeaponMode.TRACK_ONLY
          && station.mode != WeaponMode.HOLD_FIRE;
      return new StationAuthority(
          ControlOwner.REMOTE_MANUAL_FIRE,
          Optional.of(station.lease.playerId()),
          station.mode,
          true,
          mayFire,
          revision);
    }
    if (serverTick < station.aiResumeAtTick) {
      return new StationAuthority(
          ControlOwner.NONE,
          Optional.empty(),
          station.mode,
          false,
          false,
          revision);
    }
    boolean aiOwns =
        station.aiCapable
            && (station.mode == WeaponMode.AUTO_ENGAGE
                || station.mode == WeaponMode.MANUAL_PREFERRED
                || station.mode == WeaponMode.TRACK_ONLY);
    return new StationAuthority(
        aiOwns ? ControlOwner.AI : ControlOwner.NONE,
        Optional.empty(),
        station.mode,
        aiOwns,
        aiOwns && station.mode != WeaponMode.TRACK_ONLY,
        revision);
  }

  /** Clears expired leases once per server lifecycle pass and bumps revision at most once. */
  public boolean expireLeases(long serverTick) {
    boolean changed = false;
    if (flightLease != null && !isActive(flightLease, serverTick)) {
      flightLease = null;
      changed = true;
    }
    for (WeaponStation station : stations.values()) {
      if (station.lease != null && !isActive(station.lease, serverTick)) {
        station.lease = null;
        changed = true;
      }
    }
    if (changed) {
      nextRevision();
    }
    return changed;
  }

  /** Disconnect/death/dimension cleanup for one player; unrelated occupants stay intact. */
  public boolean clearPlayer(UUID playerId) {
    return clearPlayer(playerId, 0L, 0);
  }

  /** Disconnect/death/dimension cleanup with a bounded AI hand-back delay. */
  public boolean clearPlayer(UUID playerId, long serverTick, int aiResumeDelayTicks) {
    Objects.requireNonNull(playerId, "playerId");
    if (aiResumeDelayTicks < 0 || aiResumeDelayTicks > MAX_AI_RESUME_DELAY_TICKS) {
      throw new IllegalArgumentException("invalid AI resume delay: " + aiResumeDelayTicks);
    }
    boolean changed = false;
    if (flightLease != null && flightLease.playerId().equals(playerId)) {
      flightLease = null;
      changed = true;
    }
    for (WeaponStation station : stations.values()) {
      if (playerId.equals(station.physicalOccupant)) {
        station.physicalOccupant = null;
        station.aiResumeAtTick = expiresAt(serverTick, aiResumeDelayTicks);
        changed = true;
      }
      if (station.lease != null && station.lease.playerId().equals(playerId)) {
        station.lease = null;
        station.aiResumeAtTick = expiresAt(serverTick, aiResumeDelayTicks);
        changed = true;
      }
    }
    if (changed) {
      nextRevision();
    }
    return changed;
  }

  /** Entity unload and server restart never restore stale player leases. */
  public boolean clearAllPlayerLeases() {
    boolean changed = flightLease != null;
    flightLease = null;
    for (WeaponStation station : stations.values()) {
      changed |= station.lease != null || station.physicalOccupant != null;
      station.lease = null;
      station.physicalOccupant = null;
      station.aiResumeAtTick = 0L;
    }
    if (changed) {
      nextRevision();
    }
    return changed;
  }

  private RejectReason validateMutation(
      UUID playerId, int leaseTicks, long expectedRevision) {
    RejectReason common = validateRevisionAndPlayer(playerId, expectedRevision);
    if (common != RejectReason.NONE) {
      return common;
    }
    return leaseTicks > 0 && leaseTicks <= MAX_LEASE_TICKS
        ? RejectReason.NONE
        : RejectReason.INVALID_DURATION;
  }

  private RejectReason validateRevisionAndPlayer(UUID playerId, long expectedRevision) {
    Objects.requireNonNull(playerId, "playerId");
    return expectedRevision == revision ? RejectReason.NONE : RejectReason.STALE_REVISION;
  }

  private MutationResult accepted() {
    nextRevision();
    return new MutationResult(true, RejectReason.NONE, revision);
  }

  private MutationResult rejected(RejectReason reason) {
    return new MutationResult(false, reason, revision);
  }

  private void nextRevision() {
    if (revision == Long.MAX_VALUE) {
      throw new IllegalStateException("control revision exhausted");
    }
    revision++;
  }

  private static boolean isActive(PlayerLease lease, long serverTick) {
    return lease != null && serverTick < lease.expiresAtTick();
  }

  private static long expiresAt(long serverTick, int leaseTicks) {
    return serverTick > Long.MAX_VALUE - leaseTicks
        ? Long.MAX_VALUE
        : serverTick + leaseTicks;
  }

  private static String requireStationId(String id) {
    String normalized = Objects.requireNonNull(id, "station id").trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("weapon station id must not be blank");
    }
    return normalized;
  }

  public record StationDefinition(String id, boolean aiCapable, WeaponMode initialMode) {
    public StationDefinition {
      requireStationId(id);
      Objects.requireNonNull(initialMode, "initialMode");
    }
  }

  public record StationAuthority(
      ControlOwner owner,
      Optional<UUID> playerId,
      WeaponMode mode,
      boolean mayTrack,
      boolean mayFire,
      long revision) {}

  public record MutationResult(boolean accepted, RejectReason reason, long revision) {}

  public enum ControlOwner {
    AI,
    PLAYER,
    REMOTE_MANUAL_FIRE,
    NONE
  }

  public enum WeaponMode {
    AUTO_ENGAGE,
    TRACK_ONLY,
    MANUAL_PREFERRED,
    MANUAL_ONLY,
    HOLD_FIRE
  }

  public enum RejectReason {
    NONE,
    STALE_REVISION,
    INVALID_DURATION,
    UNKNOWN_STATION,
    OCCUPIED,
    NOT_OWNER,
    HOLD_FIRE,
    INVALID_RESUME_DELAY
  }

  private record PlayerLease(UUID playerId, long expiresAtTick) {}

  private static final class WeaponStation {
    private final boolean aiCapable;
    private WeaponMode mode;
    private PlayerLease lease;
    private UUID physicalOccupant;
    private long aiResumeAtTick;

    private WeaponStation(StationDefinition definition) {
      aiCapable = definition.aiCapable();
      mode = definition.initialMode();
    }
  }
}
