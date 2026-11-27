package com.liucodex.doggiesnewvehicle.ai;

import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Per-vehicle runtime owner for one installed crew core. It never scans worlds or inventories. */
public final class DnvVehicleCrewController {
  public static final int DEFAULT_AI_RESUME_DELAY_TICKS = 10;

  private final UUID vehicleId;
  private final String vehicleTypeId;
  private DnvVehicleCrewProfile.Snapshot installedCore;
  private DnvControlAuthority authority;
  private long coreRevision;

  public DnvVehicleCrewController(UUID vehicleId, String vehicleTypeId) {
    this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
    this.vehicleTypeId = requireId(vehicleTypeId, "vehicleTypeId");
  }

  public UUID vehicleId() {
    return vehicleId;
  }

  public long coreRevision() {
    return coreRevision;
  }

  public Optional<UUID> installedCoreId() {
    return installedCore == null ? Optional.empty() : Optional.of(installedCore.coreId());
  }

  public RuntimeRevision revision() {
    return new RuntimeRevision(coreRevision, authority == null ? 0L : authority.revision());
  }

  public MutationResult installCore(
      DnvVehicleCrewProfile.Snapshot snapshot, long expectedCoreRevision) {
    Objects.requireNonNull(snapshot, "snapshot");
    if (expectedCoreRevision != coreRevision) {
      return rejected(RejectReason.STALE_CORE_REVISION);
    }
    if (!vehicleTypeId.equals(snapshot.vehicleTypeId())) {
      return rejected(RejectReason.WRONG_VEHICLE_TYPE);
    }
    List<DnvControlAuthority.StationDefinition> definitions = snapshot.stations().values()
        .stream()
        .map(station -> new DnvControlAuthority.StationDefinition(
            station.id(), station.aiCapable(), station.mode()))
        .toList();
    installedCore = snapshot;
    authority = new DnvControlAuthority(definitions);
    nextCoreRevision();
    return accepted();
  }

  public MutationResult removeCore(UUID coreId, long expectedCoreRevision) {
    Objects.requireNonNull(coreId, "coreId");
    if (expectedCoreRevision != coreRevision) {
      return rejected(RejectReason.STALE_CORE_REVISION);
    }
    if (installedCore == null) {
      return rejected(RejectReason.NO_CORE);
    }
    if (!installedCore.coreId().equals(coreId)) {
      return rejected(RejectReason.WRONG_CORE);
    }
    authority.clearAllPlayerLeases();
    installedCore = null;
    authority = null;
    nextCoreRevision();
    return accepted();
  }

  /**
   * Applies a complete authoritative seat snapshot after validating every station first. Missing
   * entries mean empty seats; unknown entries reject the whole update without partial mutation.
   */
  public MutationResult synchronizePhysicalOccupants(
      Map<String, UUID> occupants,
      long serverTick,
      int aiResumeDelayTicks,
      RuntimeRevision expectedRevision) {
    Objects.requireNonNull(occupants, "occupants");
    Objects.requireNonNull(expectedRevision, "expectedRevision");
    RejectReason revisionFailure = validateRuntimeRevision(expectedRevision);
    if (revisionFailure != RejectReason.NONE) {
      return rejected(revisionFailure);
    }
    if (!authority.stationIds().containsAll(occupants.keySet())) {
      return rejected(RejectReason.UNKNOWN_STATION);
    }
    if (aiResumeDelayTicks < 0
        || aiResumeDelayTicks > DnvControlAuthority.MAX_AI_RESUME_DELAY_TICKS) {
      return rejected(RejectReason.INVALID_RESUME_DELAY);
    }
    Map<String, UUID> snapshot = new LinkedHashMap<>(occupants);
    for (String stationId : authority.stationIds()) {
      DnvControlAuthority.MutationResult result = authority.synchronizePhysicalOccupant(
          stationId,
          Optional.ofNullable(snapshot.get(stationId)),
          serverTick,
          aiResumeDelayTicks,
          authority.revision());
      if (!result.accepted()) {
        throw new IllegalStateException("validated occupant update failed: " + result.reason());
      }
    }
    return accepted();
  }

  public StationDecision stationDecision(String stationId, long serverTick) {
    if (authority == null || !authority.stationIds().contains(stationId)) {
      return new StationDecision(
          DnvControlAuthority.ControlOwner.NONE,
          Optional.empty(),
          DnvControlAuthority.WeaponMode.HOLD_FIRE,
          false,
          false,
          revision());
    }
    DnvControlAuthority.StationAuthority station = authority.weaponAuthority(stationId, serverTick);
    return new StationDecision(
        station.owner(),
        station.playerId(),
        station.mode(),
        station.mayTrack(),
        station.mayFire(),
        revision());
  }

  /** Entity unload/restart cleanup; the validated item policy may be reinstalled after reload. */
  public void clearRuntime() {
    if (authority == null && installedCore == null) {
      return;
    }
    if (authority != null) {
      authority.clearAllPlayerLeases();
    }
    installedCore = null;
    authority = null;
    nextCoreRevision();
  }

  private RejectReason validateRuntimeRevision(RuntimeRevision expected) {
    if (authority == null || installedCore == null) {
      return RejectReason.NO_CORE;
    }
    if (expected.coreRevision() != coreRevision) {
      return RejectReason.STALE_CORE_REVISION;
    }
    return expected.controlRevision() == authority.revision()
        ? RejectReason.NONE
        : RejectReason.STALE_CONTROL_REVISION;
  }

  private MutationResult accepted() {
    return new MutationResult(true, RejectReason.NONE, revision());
  }

  private MutationResult rejected(RejectReason reason) {
    return new MutationResult(false, reason, revision());
  }

  private void nextCoreRevision() {
    if (coreRevision == Long.MAX_VALUE) {
      throw new IllegalStateException("crew core revision exhausted");
    }
    coreRevision++;
  }

  private static String requireId(String value, String label) {
    String normalized = Objects.requireNonNull(value, label).trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return normalized;
  }

  public record RuntimeRevision(long coreRevision, long controlRevision) {}

  public record MutationResult(
      boolean accepted, RejectReason reason, RuntimeRevision revision) {}

  public record StationDecision(
      DnvControlAuthority.ControlOwner owner,
      Optional<UUID> playerId,
      DnvControlAuthority.WeaponMode mode,
      boolean mayTrack,
      boolean mayFire,
      RuntimeRevision revision) {}

  public enum RejectReason {
    NONE,
    NO_CORE,
    WRONG_CORE,
    WRONG_VEHICLE_TYPE,
    UNKNOWN_STATION,
    INVALID_RESUME_DELAY,
    STALE_CORE_REVISION,
    STALE_CONTROL_REVISION
  }
}
