package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvSbwWeaponDiagnostics;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory.DetailStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reduces an existing diagnostic sample to a bounded, read-only wire projection. */
final class DnvManagedVehicleDetailProjector {
  private DnvManagedVehicleDetailProjector() {}

  static boolean visibleTo(UUID requester, boolean administrator, UUID owner) {
    return administrator || owner.equals(requester);
  }

  static DnvManagedVehicleDirectory.Detail project(
      DnvManagedVehicleDirectory.Selection requested,
      DnvManagedVehicleDirectory.Selection current,
      boolean routeSupported,
      Optional<DnvSbwWeaponDiagnostics.Snapshot> sample,
      long gameTime) {
    if (!requested.equals(current)) {
      return unavailable(requested, DetailStatus.CORE_CHANGED);
    }
    if (sample.isEmpty() || sample.orElseThrow().weapons().isEmpty()) {
      return new DnvManagedVehicleDirectory.Detail(
          requested, DetailStatus.DIAGNOSTICS_PENDING, routeSupported, 0L, List.of());
    }
    DnvSbwWeaponDiagnostics.Snapshot snapshot = sample.orElseThrow();
    long age = gameTime - snapshot.capturedGameTime();
    if (age < 0L || age > DnvSbwWeaponDiagnostics.SAMPLE_INTERVAL_TICKS * 2L) {
      return new DnvManagedVehicleDirectory.Detail(
          requested, DetailStatus.STALE, routeSupported, Math.max(0L, age), List.of());
    }
    return new DnvManagedVehicleDirectory.Detail(
        requested, DetailStatus.SAMPLED, routeSupported, age,
        snapshot.weapons().stream().map(weapon -> new DnvManagedVehicleDirectory.Weapon(
            weapon.stationId(), weapon.weaponName(), weapon.physicalOwner().name(),
            displayReason(weapon), weapon.ammoCount(), weapon.canShoot(),
            weapon.cooldownRemainingTicks())).toList());
  }

  private static String displayReason(DnvSbwWeaponDiagnostics.WeaponSnapshot weapon) {
    if ("STATION_HOLD".equals(weapon.gateReason())) {
      return switch (weapon.arcReason()) {
        case "OUT_OF_AMMO", "COOLDOWN", "WEAPON_NOT_READY" -> !weapon.canShoot()
            ? (weapon.ammoCount() > 0 ? "WEAPON_NOT_READY" : "OUT_OF_AMMO")
            : (weapon.cooldownRemainingTicks() > 0 ? "COOLDOWN" : weapon.arcReason());
        case "WEAPON_UNAVAILABLE", "FIRING_LINE_BLOCKED" -> weapon.arcReason();
        default -> weapon.gateReason();
      };
    }
    // SBW canShoot also rejects overheating, reload/charge and bolt state. Its false result
    // cannot prove ammunition exhaustion when the observed vehicle still has ammunition.
    return "OUT_OF_AMMO".equals(weapon.gateReason()) && weapon.ammoCount() > 0
        ? "WEAPON_UNAVAILABLE" : weapon.gateReason();
  }

  static DnvManagedVehicleDirectory.Detail unavailable(
      DnvManagedVehicleDirectory.Selection requested, DetailStatus status) {
    return new DnvManagedVehicleDirectory.Detail(requested, status, false, 0L, List.of());
  }
}
