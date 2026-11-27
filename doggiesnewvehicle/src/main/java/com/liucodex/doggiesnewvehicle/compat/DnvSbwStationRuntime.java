package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore.WeaponReadiness;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded per-station tracking memory; it owns no entity or authority lease. */
final class DnvSbwStationRuntime {
  private static final int REQUIRED_ALIGNED_TICKS = 3;

  private final int channelOrder;
  private final Map<String, WeaponState> weaponStates = new LinkedHashMap<>();
  private int nextWeaponIndex;

  DnvSbwStationRuntime(int channelOrder, List<String> weapons) {
    if (channelOrder < 0) {
      throw new IllegalArgumentException("channelOrder must not be negative");
    }
    this.channelOrder = channelOrder;
    for (String weapon : weapons) {
      weaponStates.put(weapon, new WeaponState());
    }
  }

  int channelOrder() {
    return channelOrder;
  }

  WeaponState weapon(String weaponName) {
    return weaponStates.get(weaponName);
  }

  int nextWeaponIndex() {
    return nextWeaponIndex;
  }

  void advanceAfter(int firedIndex) {
    nextWeaponIndex = weaponStates.isEmpty()
        ? 0
        : Math.floorMod(firedIndex + 1, weaponStates.size());
  }

  void resetTracking() {
    weaponStates.values().forEach(WeaponState::resetTracking);
  }

  Map<UUID, Boolean> observedLines(int tick) {
    Map<UUID, Boolean> lines = new LinkedHashMap<>();
    for (WeaponState weapon : weaponStates.values()) {
      if (weapon.lineOfSightTarget != null
          && weapon.observedLineClear(weapon.lineOfSightTarget, tick)) {
        lines.put(weapon.lineOfSightTarget, true);
      }
    }
    return Map.copyOf(lines);
  }

  /** Same runtime owner as cooldown/visibility; observations never consume ammunition. */
  static final class Readiness {
    private Readiness() {}

    static WeaponReadiness inspect(
        VehicleEntity vehicle, List<String> weapons, DnvSbwStationRuntime state, int tick) {
      List<Sample> samples = new ArrayList<>(weapons.size());
      for (String name : weapons) {
        try {
          GunData gun = vehicle.getGunData(name);
          WeaponState weapon = state == null ? null : state.weapon(name);
          samples.add(gun == null || weapon == null ? Sample.unavailable()
              : new Sample(true, Math.max(0, vehicle.getAmmo(gun)),
                  gun.canShoot(vehicle.getAmmoSupplier()), weapon.cooldownReady(tick)));
        } catch (RuntimeException | LinkageError exception) {
          samples.add(Sample.unavailable());
        }
      }
      return aggregate(samples);
    }

    static WeaponReadiness aggregate(List<Sample> samples) {
      // One coherent observation: usable now, usable after cooldown, then explain why blocked.
      Sample best = Sample.unavailable();
      for (Sample sample : samples) {
        if (sample.rank() > best.rank()) best = sample;
      }
      return new WeaponReadiness(best.ammoCount() > 0 || best.canShoot(),
          best.cooldownReady(), true, best.available(), best.canShoot());
    }

    record Sample(boolean available, int ammoCount, boolean canShoot, boolean cooldownReady) {
      Sample {
        if (ammoCount < 0 || (!available && (ammoCount != 0 || canShoot))) {
          throw new IllegalArgumentException("inconsistent weapon observation");
        }
      }

      static Sample unavailable() {
        return new Sample(false, 0, false, false);
      }

      private int rank() {
        if (!available) return 0;
        if (canShoot) return cooldownReady ? 4 : 3;
        return ammoCount > 0 ? 2 : 1;
      }
    }
  }

  record WeaponFrame(
      String weaponName,
      GunData gunData,
      Vec3 aimPoint,
      Vec3 desired,
      WeaponState state) {}

  static final class WeaponState {
    private UUID lineOfSightTarget;
    private Vec3 aimOffset;
    private boolean blockLineClear;
    private boolean fireCorridorClear;
    private int nextLineOfSightTick;
    private int lastLineOfSightTick = -1;
    private int alignedTicks;
    private int nextFireTick;
    private UUID geometryOwner;
    private ServerLevel geometryLevel;
    private boolean geometryDeferred;
    private UUID lastCheckedTarget;
    private boolean lastCompletedLineUsable;

    boolean refreshLineOfSight(
        ServerLevel level,
        Entity source,
        Vec3 muzzle,
        Entity target,
        Predicate<Entity> protectedObstruction,
        int tick,
        int refreshedUntilTick,
        String stationId,
        String weaponName) {
      if (geometryLevel != level) {
        resetTracking();
        geometryLevel = level;
      }
      if (geometryOwner == null) {
        geometryOwner = UUID.nameUUIDFromBytes((source.getUUID() + ":" + stationId + ":" + weaponName)
            .getBytes(StandardCharsets.UTF_8));
      }
      if (needsLineOfSightRefresh(target.getUUID(), tick)) {
        if (!DnvTargetingBudget.admitGeometryCheck(level, geometryOwner)) {
          deferLineOfSight(target.getUUID());
          return false;
        }
        lineOfSightTarget = target.getUUID();
        geometryDeferred = false;
        aimOffset = DnvTargetLineOfSight.clearAimPoint(level, source, muzzle, target)
            .map(point -> point.subtract(target.position()))
            .orElse(null);
        blockLineClear = aimOffset != null;
        fireCorridorClear = blockLineClear && hasClearEntityCorridor(
            level,
            source,
            muzzle,
            target.position().add(aimOffset),
            protectedObstruction);
        lastCheckedTarget = target.getUUID();
        lastCompletedLineUsable = blockLineClear && fireCorridorClear;
        nextLineOfSightTick = refreshedUntilTick;
        lastLineOfSightTick = tick;
      }
      return DnvSbwCrewAutonomy.targetLineUsable(blockLineClear, fireCorridorClear);
    }

    boolean blockLineClear() {
      return blockLineClear;
    }

    boolean geometryDeferred() {
      return geometryDeferred;
    }

    boolean geometryPendingWithoutBlockedObservation(UUID targetId) {
      return geometryDeferred && (!targetId.equals(lastCheckedTarget) || lastCompletedLineUsable);
    }

    boolean needsLineOfSightRefresh(UUID targetId, int tick) {
      return !targetId.equals(lineOfSightTarget)
          || tick < lastLineOfSightTick || tick >= nextLineOfSightTick || geometryDeferred;
    }

    void deferLineOfSight(UUID targetId) {
      lineOfSightTarget = targetId;
      aimOffset = null;
      blockLineClear = false;
      fireCorridorClear = false;
      geometryDeferred = true;
    }

    boolean observedLineClear(UUID targetId, int tick) {
      return targetId.equals(lineOfSightTarget)
          && tick >= lastLineOfSightTick && tick < nextLineOfSightTick
          && blockLineClear && fireCorridorClear;
    }

    void revalidateForFire(
        ServerLevel level, Entity source, Vec3 muzzle, Entity target, Vec3 aimPoint,
        Predicate<Entity> protectedObstruction, int tick) {
      // Validate the exact point this attempt will use, not a newly chosen body point.
      boolean blockClear = level.clip(new ClipContext(muzzle, aimPoint,
          ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)).getType() == HitResult.Type.MISS;
      boolean corridorClear = blockClear
          && hasClearEntityCorridor(level, source, muzzle, aimPoint, protectedObstruction);
      recordLineOfSight(target.getUUID(), aimPoint.subtract(target.position()),
          blockClear, corridorClear, tick);
    }

    void recordLineOfSight(
        UUID targetId, Vec3 offset, boolean blockClear, boolean corridorClear, int tick) {
      lineOfSightTarget = targetId;
      aimOffset = blockClear ? offset : null;
      blockLineClear = blockClear;
      fireCorridorClear = blockClear && corridorClear;
      lastLineOfSightTick = tick;
      nextLineOfSightTick = saturatingAdd(tick, DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL);
      geometryDeferred = false;
      lastCheckedTarget = targetId;
      lastCompletedLineUsable = blockClear && corridorClear;
    }

    boolean fireCorridorClear() {
      return fireCorridorClear;
    }

    Vec3 currentAimPoint(Entity target) {
      return target.position().add(aimOffset);
    }

    void updateAlignment(boolean aligned) {
      alignedTicks = aligned
          ? Math.min(REQUIRED_ALIGNED_TICKS, alignedTicks + 1)
          : 0;
    }

    int alignedTicks() {
      return alignedTicks;
    }

    boolean alignedForFire() {
      return alignedTicks >= REQUIRED_ALIGNED_TICKS;
    }

    boolean cooldownReady(int tick) {
      return tick >= nextFireTick;
    }

    int cooldownRemaining(int tick) {
      return Math.max(0, nextFireTick - tick);
    }

    void onFired(int tick, int rpm) {
      nextFireTick = saturatingAdd(tick, DnvSbwCrewAutonomy.fireIntervalTicks(rpm));
    }

    void resetAlignment() {
      alignedTicks = 0;
    }

    void resetTracking() {
      if (geometryLevel != null && geometryOwner != null) {
        DnvTargetingBudget.releaseGeometryOwner(geometryLevel, geometryOwner);
      }
      geometryLevel = null;
      geometryOwner = null;
      geometryDeferred = false;
      lastCheckedTarget = null;
      lastCompletedLineUsable = false;
      lineOfSightTarget = null;
      aimOffset = null;
      blockLineClear = false;
      fireCorridorClear = false;
      nextLineOfSightTick = 0;
      lastLineOfSightTick = -1;
      resetAlignment();
    }

    private static boolean hasClearEntityCorridor(
        ServerLevel level,
        Entity source,
        Vec3 from,
        Vec3 to,
        Predicate<Entity> protectedObstruction) {
      AABB corridor = new AABB(from, to).inflate(0.35D);
      return level.getEntities(source, corridor, protectedObstruction).stream()
          .noneMatch(entity -> entity.getBoundingBox().inflate(0.2D).clip(from, to).isPresent());
    }

    private static int saturatingAdd(int value, int delta) {
      return value > Integer.MAX_VALUE - delta ? Integer.MAX_VALUE : value + delta;
    }
  }
}
