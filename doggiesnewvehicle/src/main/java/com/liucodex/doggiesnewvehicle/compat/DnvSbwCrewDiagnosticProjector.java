package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore;
import com.liucodex.doggiesnewvehicle.core.DnvSbwWeaponDiagnostics;
import com.liucodex.doggiesnewvehicle.core.DnvWeaponFireGate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Read-only adapter from live exact-SBW state to the bounded diagnostic projection. */
final class DnvSbwCrewDiagnosticProjector {
  private final VehicleEntity vehicle;
  private final DnvSbwAimChannelRuntime aimChannels;
  private final DnvSbwWeaponDiagnostics diagnostics;

  DnvSbwCrewDiagnosticProjector(
      VehicleEntity vehicle,
      DnvSbwAimChannelRuntime aimChannels,
      DnvSbwWeaponDiagnostics diagnostics) {
    this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
    this.aimChannels = Objects.requireNonNull(aimChannels, "aimChannels");
    this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
  }

  void recordStation(
      String stationId,
      List<String> weapons,
      String aimChannelId,
      DnvSbwStationRuntime state,
      DnvStationEngagementCore.StationIntent stationIntent,
      Entity target,
      List<DnvSbwStationRuntime.WeaponFrame> frames,
      List<DnvWeaponFireGate.Decision> gates,
      int tick) {
    DnvSbwAimChannelRuntime.ChannelSnapshot channel =
        aimChannels.snapshot(stationId, aimChannelId);
    for (int index = 0; index < weapons.size(); index++) {
      String weaponName = weapons.get(index);
      DnvSbwStationRuntime.WeaponFrame frame =
          index < frames.size() ? frames.get(index) : null;
      DnvSbwStationRuntime.WeaponState weaponState = state.weapon(weaponName);
      DnvWeaponFireGate.Decision gate = index < gates.size() ? gates.get(index) : null;
      DiagnosticGun gun = diagnosticGun(
          weaponName, frame == null ? null : frame.gunData());
      float[] desiredAngles = frame == null
          ? new float[] {Float.NaN, Float.NaN}
          : desiredAngles(frame.desired());
      diagnostics.record(draft(
          stationId,
          stationIntent,
          channel,
          Optional.of(target.getUUID()),
          weaponName,
          weaponState,
          gun,
          desiredAngles,
          tick,
          gate == null ? DnvWeaponFireGate.Reason.WEAPON_UNAVAILABLE.name()
              : gate.reason().name()));
    }
  }

  void recordInactive(
      String stationId,
      List<String> weapons,
      DnvSbwStationRuntime state,
      DnvStationEngagementCore.StationIntent stationIntent,
      DnvSbwAimChannelRuntime.ChannelSnapshot channel,
      Entity target,
      int tick,
      DnvWeaponFireGate.Reason gateReason) {
    for (String weaponName : weapons) {
      DnvSbwStationRuntime.WeaponState weaponState =
          state == null ? null : state.weapon(weaponName);
      diagnostics.record(draft(
          stationId,
          stationIntent,
          channel,
          Optional.ofNullable(target).map(Entity::getUUID),
          weaponName,
          weaponState,
          diagnosticGun(weaponName, null),
          new float[] {Float.NaN, Float.NaN},
          tick,
          gateReason.name()));
    }
  }

  private DnvSbwWeaponDiagnostics.WeaponDraft draft(
      String stationId,
      DnvStationEngagementCore.StationIntent stationIntent,
      DnvSbwAimChannelRuntime.ChannelSnapshot channel,
      Optional<java.util.UUID> targetId,
      String weaponName,
      DnvSbwStationRuntime.WeaponState weaponState,
      DiagnosticGun gun,
      float[] desiredAngles,
      int tick,
      String gateReason) {
    return new DnvSbwWeaponDiagnostics.WeaponDraft(
        stationId,
        weaponName,
        targetId,
        stationIntent == null ? "STATION_UNAVAILABLE" : stationIntent.reason().name(),
        stationIntent == null ? 0.0F : stationIntent.localYaw(),
        stationIntent == null ? 0.0F : stationIntent.localPitch(),
        channel.channelId(),
        channel.owner(),
        channel.reason(),
        channel.actualYaw(),
        channel.actualPitch(),
        desiredAngles[0],
        desiredAngles[1],
        weaponState == null ? 0 : weaponState.alignedTicks(),
        weaponState != null && weaponState.blockLineClear(),
        weaponState != null && weaponState.fireCorridorClear(),
        gun.ammoCount(),
        gun.canShoot(),
        weaponState == null ? 0 : weaponState.cooldownRemaining(tick),
        gateReason);
  }

  private DiagnosticGun diagnosticGun(String weaponName, GunData knownGunData) {
    try {
      GunData gunData = knownGunData == null ? vehicle.getGunData(weaponName) : knownGunData;
      if (gunData == null) {
        return new DiagnosticGun(0, false);
      }
      return new DiagnosticGun(
          Math.max(0, vehicle.getAmmo(gunData)),
          gunData.canShoot(vehicle.getAmmoSupplier()));
    } catch (RuntimeException | LinkageError exception) {
      return new DiagnosticGun(0, false);
    }
  }

  private static float[] desiredAngles(Vec3 desired) {
    double horizontal = Math.sqrt(desired.x * desired.x + desired.z * desired.z);
    return new float[] {
        (float) Math.toDegrees(Math.atan2(desired.z, desired.x)) - 90.0F,
        (float) -Math.toDegrees(Math.atan2(desired.y, Math.max(1.0E-9D, horizontal)))
    };
  }

  private record DiagnosticGun(int ammoCount, boolean canShoot) {}
}
