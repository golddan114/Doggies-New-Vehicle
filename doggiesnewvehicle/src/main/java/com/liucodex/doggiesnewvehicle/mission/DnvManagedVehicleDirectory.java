package com.liucodex.doggiesnewvehicle.mission;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded server projection of loaded vehicles and their current crew state. */
public final class DnvManagedVehicleDirectory {
  public static final int MAX_VEHICLES = 64;
  public static final int MAX_WEAPONS = 256;

  private DnvManagedVehicleDirectory() {}

  public record Query(UUID requestId, Optional<Selection> selected) {
    public Query {
      Objects.requireNonNull(requestId, "requestId");
      selected = Objects.requireNonNull(selected, "selected");
    }
  }

  public record Selection(UUID vehicleId, String dimension, UUID coreId, long coreRevision) {
    public Selection {
      Objects.requireNonNull(vehicleId, "vehicleId");
      Objects.requireNonNull(coreId, "coreId");
      dimension = text(dimension, 256);
      if (coreRevision < 0L) {
        throw new IllegalArgumentException("negative core revision");
      }
    }
  }

  public record View(
      UUID requestId, boolean administrator, String issue,
      List<Vehicle> vehicles, Optional<Detail> detail) {
    public View {
      Objects.requireNonNull(requestId, "requestId");
      issue = Objects.requireNonNull(issue, "issue");
      vehicles = List.copyOf(vehicles);
      detail = Objects.requireNonNull(detail, "detail");
      if (issue.length() > 96 || vehicles.size() > MAX_VEHICLES) {
        throw new IllegalArgumentException("managed vehicle directory is out of bounds");
      }
    }
  }

  public record Vehicle(
      UUID vehicleId,
      String vehicleTypeId,
      String dimension,
      int x,
      int y,
      int z,
      String flightMode,
      String runtimeState,
      boolean teleportAllowed,
      UUID coreId,
      long coreRevision) {
    public Vehicle {
      Objects.requireNonNull(vehicleId, "vehicleId");
      Objects.requireNonNull(coreId, "coreId");
      if (coreRevision < 0L) {
        throw new IllegalArgumentException("negative core revision");
      }
      vehicleTypeId = Objects.requireNonNull(vehicleTypeId, "vehicleTypeId");
      dimension = Objects.requireNonNull(dimension, "dimension");
      flightMode = Objects.requireNonNull(flightMode, "flightMode");
      runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
      if (vehicleTypeId.length() > 256 || dimension.length() > 256
          || flightMode.length() > 64 || runtimeState.length() > 64) {
        throw new IllegalArgumentException("managed vehicle summary is out of bounds");
      }
    }

    public Selection selection() {
      return new Selection(vehicleId, dimension, coreId, coreRevision);
    }
  }

  public record Detail(
      Selection identity, DetailStatus status, boolean routeSupported,
      long sampleAgeTicks, List<Weapon> weapons) {
    public Detail {
      Objects.requireNonNull(identity, "identity");
      Objects.requireNonNull(status, "status");
      weapons = List.copyOf(weapons);
      if (sampleAgeTicks < 0L || weapons.size() > MAX_WEAPONS
          || (status != DetailStatus.SAMPLED && !weapons.isEmpty())) {
        throw new IllegalArgumentException("invalid vehicle detail");
      }
    }
  }

  /** Snapshot values describe a sample, never an authorization to fire. */
  public record Weapon(
      String stationId, String weaponName, String controlOwner, String gateReason,
      int ammoCount, boolean canShoot, int cooldownTicks) {
    public Weapon {
      stationId = text(stationId, 160);
      weaponName = text(weaponName, 160);
      controlOwner = text(controlOwner, 64);
      gateReason = text(gateReason, 64);
      if (ammoCount < 0 || cooldownTicks < 0) {
        throw new IllegalArgumentException("negative weapon counters");
      }
    }
  }

  public enum DetailStatus {
    SAMPLED, DIAGNOSTICS_PENDING, STALE, CORE_CHANGED, NOT_AVAILABLE
  }

  private static String text(String value, int maximum) {
    Objects.requireNonNull(value, "text");
    if (value.isBlank() || value.length() > maximum) {
      throw new IllegalArgumentException("text is out of bounds");
    }
    return value;
  }
}
