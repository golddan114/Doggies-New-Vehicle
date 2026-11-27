package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewController;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewRuntime;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore;
import com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

/** Exact-SBW value adapter for one shared candidate frame and independent station decisions. */
final class DnvSbwStationEngagementAdapter {
  private final VehicleEntity vehicle;
  private final DnvStationEngagementCore core = new DnvStationEngagementCore();

  DnvSbwStationEngagementAdapter(VehicleEntity vehicle) {
    this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
  }

  StationInput input(
      String stationId, List<String> weapons, DnvStationEngagementCore.StationGeometryProfile geometry,
      DnvStationEngagementCore.StationEngagementPolicy policy, DnvSbwStationRuntime state, int tick) {
    return new StationInput(stationId, !weapons.isEmpty(), geometry, policy,
        DnvSbwStationRuntime.Readiness.inspect(vehicle, weapons, state, tick),
        state == null ? Map.of() : state.observedLines(tick));
  }

  DnvStationEngagementCore.Decision decide(
      DnvVehicleCrewRuntime runtime,
      List<StationInput> stationsInput,
      List<Entity> sharedCandidates,
      Optional<UUID> designatedTarget,
      Predicate<Entity> nativeEligible,
      long gameTime) {
    Objects.requireNonNull(runtime, "runtime");
    Objects.requireNonNull(stationsInput, "stationsInput");
    Objects.requireNonNull(sharedCandidates, "sharedCandidates");
    Objects.requireNonNull(designatedTarget, "designatedTarget");
    Objects.requireNonNull(nativeEligible, "nativeEligible");

    List<DnvStationEngagementCore.TargetSnapshot> targets = new ArrayList<>();
    for (Entity candidate : sharedCandidates) {
      boolean designated = designatedTarget.filter(candidate.getUUID()::equals).isPresent();
      boolean ambientEligible = nativeEligible.test(candidate);
      if (!ambientEligible && !designated) {
        continue;
      }
      targets.add(target(candidate, ambientEligible));
    }
    Map<String, DnvStationEngagementCore.StationSnapshot> stations = new LinkedHashMap<>();
    for (StationInput input : stationsInput) {
      DnvVehicleCrewController.StationDecision station =
          runtime.stationDecision(input.stationId(), gameTime);
      stations.put(input.stationId(), new DnvStationEngagementCore.StationSnapshot(
          owner(station.owner()),
          station.mayTrack() && input.weaponAvailable(),
          input.geometry(),
          input.policy(),
          input.readiness(),
          input.lineOfSight()));
    }
    return core.decide(new DnvStationEngagementCore.Request(
        gameTime,
        vehicle.getId(),
        vehicle.getX(),
        vehicle.getY(),
        vehicle.getZ(),
        vehicle.getYRot(),
        designatedTarget,
        targets,
        stations));
  }

  DnvAutonomyCore.TargetObservation observeCoreTarget(Entity target) {
    Vec3 delta = target.position().subtract(vehicle.position());
    double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
    float bearing = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
    double threat = target instanceof LivingEntity living
        ? Math.max(1.0D, living.getHealth())
        : 1.0D;
    return new DnvAutonomyCore.TargetObservation(target.getUUID(), target.getX(), target.getY(), target.getZ(),
        horizontalDistance, bearing, threat, !target.isRemoved(), target.isAlive(), true);
  }

  void clear(long gameTime) {
    core.clear(gameTime);
  }

  String revalidateFire(DnvVehicleCrewRuntime runtime, String stationId, List<String> weapons,
      DnvStationEngagementCore.StationGeometryProfile geometry, DnvStationEngagementCore.StationEngagementPolicy policy,
      DnvSbwStationRuntime state, DnvSbwStationRuntime.WeaponFrame frame, Entity target,
      Predicate<Entity> hostile, Predicate<Entity> protectedEntity) {
    if (!(vehicle.level() instanceof net.minecraft.server.level.ServerLevel level)) return "SOURCE_UNLOADED";
    long now = level.getGameTime();
    int tick = vehicle.tickCount;
    var authority = runtime.stationDecision(stationId, now);
    if (authority.owner() != DnvControlAuthority.ControlOwner.AI || !authority.mayFire()) return "AUTHORITY_DENIED";
    var gun = vehicle.getGunData(frame.weaponName());
    if (gun == null || !gun.canShoot(vehicle.getAmmoSupplier()) || !frame.state().cooldownReady(tick)) return "NOT_READY";
    var muzzle = vehicle.getShootPos(frame.weaponName(), 1.0F);
    var desired = frame.aimPoint().subtract(muzzle);
    if (!DnvSbwWeaponScheduler.isPointAligned(vehicle.getShootVec(frame.weaponName(), 1.0F), desired)) return "NOT_ALIGNED";
    frame.state().revalidateForFire(level, vehicle, muzzle, target, frame.aimPoint(), protectedEntity, tick);
    if (!frame.state().fireCorridorClear()) return "FIRING_CORRIDOR_BLOCKED";
    // Fresh station policy/geometry and target observation, without another target scan or aim write.
    var decision = decide(runtime, List.of(input(stationId, weapons, geometry, policy, state, tick)),
        List.of(target), Optional.of(target.getUUID()), hostile, now).stationIntents().get(stationId);
    if (decision == null || decision.command() != DnvAutonomyCore.DomainCommand.FIRE
        || decision.targetId().filter(target.getUUID()::equals).isEmpty()) return "STATION_POLICY_DENIED";
    return null;
  }

  record StationInput(
      String stationId,
      boolean weaponAvailable,
      DnvStationEngagementCore.StationGeometryProfile geometry,
      DnvStationEngagementCore.StationEngagementPolicy policy,
      DnvStationEngagementCore.WeaponReadiness readiness,
      Map<UUID, Boolean> lineOfSight) {
    StationInput {
      Objects.requireNonNull(stationId, "stationId");
      Objects.requireNonNull(geometry, "geometry");
      Objects.requireNonNull(policy, "policy");
      Objects.requireNonNull(readiness, "readiness");
      lineOfSight = Map.copyOf(lineOfSight);
    }
  }

  private static DnvStationEngagementCore.TargetSnapshot target(
      Entity target, boolean ambientEligible) {
    double threat = target instanceof LivingEntity living
        ? Math.max(1.0D, living.getHealth())
        : 1.0D;
    return new DnvStationEngagementCore.TargetSnapshot(
        target.getUUID(),
        kind(target),
        target.getX(),
        target.getY(),
        target.getZ(),
        threat,
        !target.isRemoved(),
        target.isAlive(),
        false,
        ambientEligible);
  }

  private static DnvStationEngagementCore.TargetKind kind(Entity target) {
    if (target instanceof ServerPlayer) {
      return DnvStationEngagementCore.TargetKind.PLAYER;
    }
    if (target instanceof VehicleEntity
        && target.getPassengers().stream().anyMatch(ServerPlayer.class::isInstance)) {
      return DnvStationEngagementCore.TargetKind.PLAYER_OCCUPIED_VEHICLE;
    }
    if (target instanceof VehicleEntity) {
      return DnvStationEngagementCore.TargetKind.VEHICLE;
    }
    if (target instanceof LivingEntity) {
      return DnvStationEngagementCore.TargetKind.LIVING;
    }
    return DnvStationEngagementCore.TargetKind.UNKNOWN;
  }

  static DnvAutonomyCore.DomainOwner owner(
      DnvControlAuthority.ControlOwner owner) {
    return switch (owner) {
      case AI -> DnvAutonomyCore.DomainOwner.AI;
      case PLAYER -> DnvAutonomyCore.DomainOwner.PLAYER;
      case REMOTE_MANUAL_FIRE -> DnvAutonomyCore.DomainOwner.REMOTE_MANUAL_FIRE;
      case NONE -> DnvAutonomyCore.DomainOwner.NONE;
    };
  }
}
