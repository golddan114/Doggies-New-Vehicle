package com.liucodex.silentheaven.entity;

import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.atsuishio.superbwarfare.entity.projectile.JavelinMissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.RpgRocketStandardEntity;
import com.atsuishio.superbwarfare.tools.RangeTool;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import com.liucodex.doggiesnewvehicle.compat.DnvSightObservation;
import com.liucodex.doggiesnewvehicle.core.DnvLazyCandidateFrame;
import com.liucodex.doggiesnewvehicle.core.DnvMultiStationTargetAllocator;
import com.liucodex.silentheaven.init.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

abstract class AbstractSilentHeavenVehicleWeapons extends AbstractSilentHeavenVehicleNavigation {
  protected AbstractSilentHeavenVehicleWeapons(
      EntityType<? extends SilentHeavenVehicleEntity> type, Level level) {
    super(type, level);
  }

  protected void tickCoreAim(@Nullable Entity target) {
    UUID next = target == null ? null : target.getUUID();
    if (!java.util.Objects.equals(mainAimTargetId, next)) mainSettledTicks = 0;
    mainAimTargetId = next;
    mainAimTick = tickCount;
    if (target == null) {
      tickIdleCoreScan();
      return;
    }
    idleCoreYawOffset = 0.0F;
    idleCorePauseTicks = IDLE_CORE_INITIAL_PAUSE;
    Vec3 centerMuzzle = mainGunMuzzle(true).add(mainGunMuzzle(false)).scale(0.5D);
    Vec3 solution = firingSolution(centerMuzzle, target, MAIN_VELOCITY, MAIN_GRAVITY);
    if (!isFinite(solution)) {
      mainAim = Vec3.ZERO;
      mainSettledTicks = 0;
      return;
    }
    mainAim = solution.normalize();
    float desiredYaw = (float) (Mth.atan2(mainAim.z, mainAim.x) * Mth.RAD_TO_DEG) - 90.0F;
    double horizontal = Math.sqrt(mainAim.x * mainAim.x + mainAim.z * mainAim.z);
    float desiredPitch =
        Mth.clamp((float) (Mth.atan2(mainAim.y, horizontal) * Mth.RAD_TO_DEG), -12.0F, 48.0F);
    float oldYaw = getCoreYaw();
    float oldPitch = getMainGunPitch();
    setCoreYaw(Mth.approachDegrees(oldYaw, desiredYaw, CORE_TURN_SPEED));
    setMainGunPitch(Mth.approachDegrees(oldPitch, desiredPitch, GUN_PITCH_SPEED));
    float yawError = Math.abs(Mth.wrapDegrees(desiredYaw - getCoreYaw()));
    float pitchError = Math.abs(desiredPitch - getMainGunPitch());
    float turn =
        Math.max(
            Math.abs(Mth.wrapDegrees(getCoreYaw() - oldYaw)),
            Math.abs(getMainGunPitch() - oldPitch));
    boolean clearShot = hasCachedMainLineOfSight(centerMuzzle, target);
    if (yawError <= 0.2F && pitchError <= 0.2F && turn <= 0.05F && clearShot) {
      mainSettledTicks = Math.min(5, mainSettledTicks + 1);
    } else if (yawError > 0.75F || pitchError > 0.75F || !clearShot) {
      mainSettledTicks = 0;
    }
  }

  protected boolean hasCachedMainLineOfSight(Vec3 muzzle, Entity target) {
    UUID targetId = target.getUUID();
    long pose = stationSightPoseKey(5);
    if (mainSight == null || !mainSight.reusable(targetId, muzzle, target.getBoundingBox(),
        target.getEyeY(), pose, tickCount)) {
      if (!(level() instanceof ServerLevel serverLevel)
          || !DnvTargetingBudget.admitGeometryCheck(serverLevel, stationGeometryOwner(5))) {
        return false;
      }
      mainSight = new DnvSightObservation(targetId, muzzle, target.getBoundingBox(),
          target.getEyeY(), pose, tickCount,
          DnvTargetingBudget.nextCheckTick(tickCount, getId(),
              DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL, 61), hasClearShot(muzzle, target));
    }
    return mainSight.clear();
  }

  /**
   * Gives the unengaged core a restrained Red Alert-style sentry motion. The hull keeps ownership
   * of navigation; only the core slews, pauses and selects another small offset relative to the
   * current hull heading.
   */
  protected void tickIdleCoreScan() {
    mainAim = Vec3.ZERO;
    mainSettledTicks = 0;
    setMainGunPitch(Mth.approachDegrees(getMainGunPitch(), 0.0F, IDLE_CORE_TURN_SPEED));

    if (idleCorePauseTicks > 0) {
      idleCorePauseTicks--;
      return;
    }

    float desiredYaw = getYRot() + idleCoreYawOffset;
    setCoreYaw(Mth.approachDegrees(getCoreYaw(), desiredYaw, IDLE_CORE_TURN_SPEED));
    if (Math.abs(Mth.wrapDegrees(desiredYaw - getCoreYaw())) > 0.35F) {
      return;
    }

    float nextOffset = Mth.lerp(random.nextFloat(), -IDLE_CORE_MAX_OFFSET, IDLE_CORE_MAX_OFFSET);
    if (Math.abs(nextOffset - idleCoreYawOffset) < 9.0F) {
      float direction =
          Math.abs(idleCoreYawOffset) < 0.35F
              ? (random.nextBoolean() ? 1.0F : -1.0F)
              : -Math.signum(idleCoreYawOffset);
      nextOffset = direction * (9.0F + random.nextFloat() * (IDLE_CORE_MAX_OFFSET - 9.0F));
    }
    idleCoreYawOffset = Mth.clamp(nextOffset, -IDLE_CORE_MAX_OFFSET, IDLE_CORE_MAX_OFFSET);
    idleCorePauseTicks = IDLE_CORE_MIN_PAUSE + random.nextInt(IDLE_CORE_RANDOM_PAUSE);
  }

  protected void tickMainGuns(@Nullable Entity target) {
    if (leftMainReload > 0) leftMainReload--;
    if (rightMainReload > 0) rightMainReload--;
    if (!autonomyMayFire(CORE_MAIN)
        || target == null
        || mainSettledTicks < 5
        || horizontalDistance(target.position()) < MAIN_MIN_RANGE
        || horizontalDistance(target.position()) > MAIN_MAX_RANGE
        || !isFinite(mainAim)) {
      return;
    }
    if (leftMainReload == 0 && fireMainGun(true, target)) {
      leftMainReload = MAIN_RELOAD;
    }
    if (rightMainReload == 0 && fireMainGun(false, target)) {
      rightMainReload = MAIN_RELOAD;
    }
  }

  protected boolean mainAimReady(Entity target, Vec3 muzzle, Vec3 direction) {
    return target != null && mainAimTick == tickCount && mainSettledTicks >= 5
        && target.getUUID().equals(mainAimTargetId)
        && com.liucodex.doggiesnewvehicle.boss.DnvBossMainAim.aligned(
            getCoreYaw(), getMainGunPitch(), direction, muzzle.distanceTo(target.getBoundingBox().getCenter()));
  }

  protected boolean fireMainGun(boolean left, Entity target) {
    if (!(level() instanceof ServerLevel serverLevel)) return false;
    if (!com.liucodex.doggiesnewvehicle.compat.DnvCombatCoordinator.fireAuthorizedBoss(this, target)) {
      return false;
    }
    Vec3 muzzle = mainGunMuzzle(left);
    if (clearAimPoint(muzzle, target).isEmpty()) return false;
    Vec3 direction = firingSolution(muzzle, target, MAIN_VELOCITY, MAIN_GRAVITY);
    if (!isFinite(direction)) return false;
    direction = direction.normalize();
    if (!mainAimReady(target, muzzle, direction)) return false;
    CannonShellEntity shell =
        com.atsuishio.superbwarfare.init.ModEntities.CANNON_SHELL.get().create(serverLevel);
    if (shell == null) return false;
    shell.setOwner(this);
    com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects.suppressCameraShake(shell);
    shell.setType(CannonShellEntity.Type.HE);
    shell.setDamage(MAIN_DAMAGE);
    shell.setExplosionDamage(MAIN_EXPLOSION_DAMAGE);
    shell.setExplosionRadius(MAIN_EXPLOSION_RADIUS);
    shell.setCustomGravity((float) MAIN_GRAVITY);
    com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.mark(shell, 0.35F);
    com.liucodex.doggiesnewvehicle.core.DnvPercentHits.mark(shell, com.liucodex.doggiesnewvehicle.combat.BossPercentDamage.fraction("silent_heaven_he"));
    shell.moveTo(muzzle.x, muzzle.y, muzzle.z, getCoreYaw(), getMainGunPitch());
    shell.shoot(direction.x, direction.y, direction.z, (float) MAIN_VELOCITY, 0.05F);
    shell.setDeltaMovement(direction.scale(MAIN_VELOCITY));
    shell.getPersistentData().putBoolean(OWNED_ENTITY_TAG, true);
    com.liucodex.doggiesnewvehicle.api.combat.DnvProjectileLifetime.trackKinetic(shell, shell.getLife());
    serverLevel.addFreshEntity(shell);
    playExternalSound("doggiesnewvehicle:cannon_fire_01", muzzle, 12.0F, 0.92F);
    tryPlayAttackVoice();
    return true;
  }

  protected Vec3 mainGunMuzzle(boolean left) {
    double pivotX = left ? 1.880550D : -1.880550D;
    double muzzleX = left ? 2.273122D : -2.273121D;
    Vec3 pivot = new Vec3(pivotX, 3.574424D, -1.085218D);
    Vec3 rest = new Vec3(muzzleX, 3.574424D, 2.219845D);
    Vec3 pitched = pivot.add(rest.subtract(pivot).xRot(-getMainGunPitch() * Mth.DEG_TO_RAD));
    return position().add(pitched.yRot(-getCoreYaw() * Mth.DEG_TO_RAD));
  }

  protected Vec3 firingSolution(Vec3 muzzle, Entity target, double velocity, double gravity) {
    Vec3 aimPoint = target.getBoundingBox().getCenter();
    Vec3 toward = aimPoint.subtract(muzzle);
    Vec3 solution =
        RangeTool.calculateFiringSolution(
            muzzle, aimPoint, target.getDeltaMovement(), velocity, gravity);
    if (!isFinite(solution)) solution = toward;
    if (isFinite(solution) && solution.dot(toward) < 0.0D) solution = solution.scale(-1.0D);
    return solution;
  }

  protected static boolean isFinite(Vec3 vector) {
    return vector != null
        && Double.isFinite(vector.x)
        && Double.isFinite(vector.y)
        && Double.isFinite(vector.z)
        && vector.lengthSqr() > 1.0E-8D;
  }

  private final class SecondarySlot {
    final String id;
    final int geometrySlot;
    final AutocannonStation autocannon;
    final WeaponStation weapon;
    final Vec3 origin;
    final double minRange;
    final double maxRange;

    SecondarySlot(String id, int geometrySlot, AutocannonStation autocannon,
        WeaponStation weapon, Vec3 origin, double minRange, double maxRange) {
      this.id = id;
      this.geometrySlot = geometrySlot;
      this.autocannon = autocannon;
      this.weapon = weapon;
      this.origin = origin;
      this.minRange = minRange;
      this.maxRange = maxRange;
    }

    UUID targetId() { return autocannon != null ? autocannon.targetId : weapon.targetId; }
    DnvSightObservation sight() { return autocannon != null ? autocannon.sight : weapon.sight; }

    boolean eligible(Entity candidate) {
      if (!isTrackedTargetValid(candidate)) return false;
      double distance = candidate.getBoundingBox().getCenter().distanceToSqr(origin);
      return distance >= minRange * minRange && distance <= maxRange * maxRange
          && (autocannon == null || withinAutocannonArc(candidate, origin));
    }

    boolean knownBlocked(Entity candidate) {
      DnvSightObservation observation = sight();
      return observation != null && !observation.clear()
          && observation.reusable(candidate.getUUID(), origin, candidate.getBoundingBox(),
              candidate.getEyeY(), stationSightPoseKey(geometrySlot), tickCount);
    }

    void select(UUID chosen) {
      if (Objects.equals(targetId(), chosen)) return;
      if (autocannon != null) {
        autocannon.targetId = chosen;
        autocannon.settledTicks = 0;
        if (chosen != null) autocannon.sight = null;
      } else {
        weapon.targetId = chosen;
        weapon.settledTicks = 0;
        if (chosen != null) weapon.sight = null;
      }
    }
  }

  /** One allocation for the active phase; firing and pose remain station-owned. */
  protected void prepareSecondaryAllocation(DnvLazyCandidateFrame<Entity> frame,
      boolean candidateScanCompleted) {
    if (!(level() instanceof ServerLevel serverLevel)) return;
    List<SecondarySlot> slots = new ArrayList<>(5);
    slots.add(new SecondarySlot("auto-left", 0, autocannonLeft, null,
        stationPitchPivotToWorld(AUTOCANNON_L_YAW_PIVOT_LOCAL,
            AUTOCANNON_L_PITCH_PIVOT_LOCAL, entityData.get(AUTOCANNON_L_YAW)),
        0.0D, AUTOCANNON_RANGE));
    slots.add(new SecondarySlot("auto-right", 1, autocannonRight, null,
        stationPitchPivotToWorld(AUTOCANNON_R_YAW_PIVOT_LOCAL,
            AUTOCANNON_R_PITCH_PIVOT_LOCAL, entityData.get(AUTOCANNON_R_YAW)),
        0.0D, AUTOCANNON_RANGE));
    slots.add(new SecondarySlot("pod-left", 2, null, missilePodLeft,
        stationPitchPivotToWorld(MISSILE_POD_L_YAW_PIVOT_LOCAL,
            MISSILE_POD_L_PITCH_PIVOT_LOCAL, entityData.get(MISSILE_POD_L_YAW), true),
        MISSILE_MIN_RANGE, MISSILE_MAX_RANGE));
    slots.add(new SecondarySlot("pod-right", 3, null, missilePodRight,
        stationPitchPivotToWorld(MISSILE_POD_R_YAW_PIVOT_LOCAL,
            MISSILE_POD_R_PITCH_PIVOT_LOCAL, entityData.get(MISSILE_POD_R_YAW), true),
        MISSILE_MIN_RANGE, MISSILE_MAX_RANGE));
    if (getPhase() == PHASE_TWO) {
      boolean left = (vlsTubeIndex & 1) == 0;
      slots.add(new SecondarySlot("vls", 4, null, vlsBattery,
          corePointToWorld(new Vec3(left ? 1.410375D : -1.410375D,
              4.552500D, -2.111190D)), VLS_MIN_RANGE, VLS_MAX_RANGE));
    }
    Map<String, Entity> old = new HashMap<>();
    Map<String, Boolean> retained = new HashMap<>();
    boolean needsFrame = candidateScanCompleted;
    for (SecondarySlot slot : slots) {
      Entity current = slot.targetId() == null ? null : serverLevel.getEntity(slot.targetId());
      old.put(slot.id, current);
      boolean legal = slot.eligible(current) && !slot.knownBlocked(current);
      retained.put(slot.id, legal);
      needsFrame |= !legal;
    }
    if (!needsFrame) return;

    List<Entity> live = frame.get();
    List<DnvMultiStationTargetAllocator.Candidate> ranked = new ArrayList<>(live.size());
    for (Entity candidate : live) {
      double priority = distanceToSqr(candidate);
      if (Double.isFinite(priority) && priority >= 0.0D) {
        ranked.add(new DnvMultiStationTargetAllocator.Candidate(candidate.getUUID(), priority));
      }
    }
    List<DnvMultiStationTargetAllocator.Station> requests = new ArrayList<>(slots.size());
    for (SecondarySlot slot : slots) {
      Entity previous = old.get(slot.id);
      boolean keep = retained.get(slot.id);
      Set<UUID> eligible = new HashSet<>();
      if (keep) eligible.add(previous.getUUID());
      if (!keep || candidateScanCompleted) {
        for (Entity candidate : live) {
          if (slot.eligible(candidate) && !slot.knownBlocked(candidate)) {
            eligible.add(candidate.getUUID());
          }
        }
      }
      requests.add(new DnvMultiStationTargetAllocator.Station(slot.id,
          keep ? previous.getUUID() : null, eligible, null, !keep,
          keep && candidateScanCompleted));
    }
    Map<String, UUID> assigned = DnvMultiStationTargetAllocator.assign(ranked, requests);
    for (SecondarySlot slot : slots) slot.select(assigned.get(slot.id));
  }

  protected void tickAutocannons() {
    tickAutocannon(
        autocannonLeft, true, AUTOCANNON_L_YAW, AUTOCANNON_L_PITCH, autocannonRight.targetId);
    tickAutocannon(
        autocannonRight, false, AUTOCANNON_R_YAW, AUTOCANNON_R_PITCH, autocannonLeft.targetId);
  }

  protected void tickMissilePods() {
    tickMissilePod(missilePodLeft, true, MISSILE_POD_L_YAW, MISSILE_POD_L_PITCH);
    tickMissilePod(missilePodRight, false, MISSILE_POD_R_YAW, MISSILE_POD_R_PITCH);
  }

  protected void tickMissilePod(
      WeaponStation station,
      boolean left,
      EntityDataAccessor<Float> yawAccessor,
      EntityDataAccessor<Float> pitchAccessor) {
    station.tickTimers(MISSILE_MAGAZINE);
    Vec3 localYawPivot = left ? MISSILE_POD_L_YAW_PIVOT_LOCAL : MISSILE_POD_R_YAW_PIVOT_LOCAL;
    Vec3 localPitchPivot = left ? MISSILE_POD_L_PITCH_PIVOT_LOCAL : MISSILE_POD_R_PITCH_PIVOT_LOCAL;
    Vec3 localSocket = left ? MISSILE_POD_L_LAUNCH_LOCAL : MISSILE_POD_R_LAUNCH_LOCAL;
    Vec3 worldPivot =
        stationPitchPivotToWorld(localYawPivot, localPitchPivot, entityData.get(yawAccessor), true);
    Entity target = resolveStationTarget(station, worldPivot, MISSILE_MIN_RANGE, MISSILE_MAX_RANGE);
    boolean aligned =
        aimStation(
            station,
            worldPivot,
            target,
            MISSILE_TURN_SPEED,
            SIDE_ROCKET_VELOCITY,
            SIDE_ROCKET_GRAVITY,
            localElevation(localSocket.subtract(localPitchPivot)),
            yawAccessor,
            pitchAccessor);
    if (!aligned || target == null || station.cooldown > 0 || station.reload > 0) {
      return;
    }
    float yaw = entityData.get(yawAccessor);
    float pitch = entityData.get(pitchAccessor);
    Vec3 muzzle =
        stationSocketToWorld(localYawPivot, localPitchPivot, localSocket, yaw, pitch, true);
    if (!isValidStationTarget(station, target, muzzle, MISSILE_MIN_RANGE, MISSILE_MAX_RANGE)) {
      station.settledTicks = 0;
      return;
    }
    Vec3 direction = stationAxisToWorld(localPitchPivot, localSocket, yaw, pitch);
    if (autonomyMayFire(left ? CORE_MISSILE_LEFT : CORE_MISSILE_RIGHT)
        && fireUnguidedSideRocket(muzzle, direction, target)) {
      station.ammo = 0;
      station.reload = MISSILE_RELOAD;
    }
  }

  protected void tickVlsBattery() {
    vlsBattery.tickTimers(VLS_MAGAZINE);
    boolean left = (vlsTubeIndex & 1) == 0;
    Vec3 worldSocket =
        corePointToWorld(new Vec3(left ? 1.410375D : -1.410375D, 4.552500D, -2.111190D));
    Entity target = resolveStationTarget(vlsBattery, worldSocket, VLS_MIN_RANGE, VLS_MAX_RANGE);
    if (target == null) {
      if (!vlsBattery.geometryDeferred) vlsBattery.settledTicks = 0;
      return;
    }
    vlsBattery.settledTicks = Math.min(VLS_LOCK_TICKS, vlsBattery.settledTicks + 1);
    if (vlsBattery.settledTicks < VLS_LOCK_TICKS
        || vlsBattery.ammo <= 0
        || vlsBattery.cooldown > 0
        || vlsBattery.reload > 0
        || !isValidStationTarget(vlsBattery, target, worldSocket, VLS_MIN_RANGE, VLS_MAX_RANGE)) {
      return;
    }
    if (!autonomyMayFire(CORE_VLS)
        || !fireTrackingMissile(worldSocket, new Vec3(0.0D, 1.0D, 0.0D), target)) {
      return;
    }
    vlsBattery.ammo--;
    vlsTubeIndex ^= 1;
    if (vlsBattery.ammo <= 0) {
      vlsBattery.reload = VLS_RELOAD;
    } else {
      vlsBattery.cooldown = VLS_INTERVAL;
    }
  }

  @Nullable
  protected Entity resolveStationTarget(
      WeaponStation station, Vec3 muzzle, double minimumRange, double maximumRange) {
    Entity current = null;
    if (station.targetId != null && level() instanceof ServerLevel serverLevel) {
      current = serverLevel.getEntity(station.targetId);
    }
    station.geometryDeferred = false;
    if (isValidStationTarget(station, current, muzzle, minimumRange, maximumRange)) return current;
    if (station.geometryDeferred) return null;
    station.targetId = null;
    station.settledTicks = 0;
    return null;
  }

  protected boolean isValidStationTarget(
      WeaponStation station,
      @Nullable Entity target,
      Vec3 muzzle,
      double minimumRange,
      double maximumRange) {
    if (!isTrackedTargetValid(target)) {
      return false;
    }
    double distanceSqr = target.getBoundingBox().getCenter().distanceToSqr(muzzle);
    return distanceSqr >= minimumRange * minimumRange
        && distanceSqr <= maximumRange * maximumRange
        && hasCachedStationLineOfSight(station, muzzle, target);
  }

  protected boolean hasCachedStationLineOfSight(WeaponStation station, Vec3 muzzle, Entity target) {
    UUID targetId = target.getUUID();
    long pose = stationSightPoseKey(station.scheduleSlot);
    if (station.sight == null || !station.sight.reusable(targetId, muzzle,
        target.getBoundingBox(), target.getEyeY(), pose, tickCount)) {
      if (!(level() instanceof ServerLevel serverLevel)
          || !DnvTargetingBudget.admitGeometryCheck(
              serverLevel, stationGeometryOwner(station.scheduleSlot))) {
        station.geometryDeferred = true;
        return false;
      }
      station.geometryDeferred = false;
      station.sight = new DnvSightObservation(targetId, muzzle, target.getBoundingBox(),
          target.getEyeY(), pose, tickCount,
          DnvTargetingBudget.nextCheckTick(tickCount, getId(),
              DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL, 70 + station.scheduleSlot),
          hasClearShot(muzzle, target));
    }
    return station.sight.clear();
  }

  protected boolean aimStation(
      WeaponStation station,
      Vec3 worldPivot,
      @Nullable Entity target,
      float maxTurn,
      float projectileVelocity,
      float projectileGravity,
      float restElevation,
      EntityDataAccessor<Float> yawAccessor,
      EntityDataAccessor<Float> pitchAccessor) {
    if (target == null) {
      station.aimVector = Vec3.ZERO;
      if (!station.geometryDeferred) station.settledTicks = 0;
      return false;
    }
    Vec3 direction = firingSolution(worldPivot, target, projectileVelocity, projectileGravity);
    if (!isFinite(direction)) {
      station.aimVector = Vec3.ZERO;
      station.settledTicks = 0;
      return false;
    }
    station.aimVector = direction.normalize();
    double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
    float desiredYaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
    float desiredPitch =
        Mth.clamp(
            (float) (Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG) - restElevation,
            -15.0F,
            55.0F);
    float oldYaw = entityData.get(yawAccessor);
    float oldPitch = entityData.get(pitchAccessor);
    float newYaw = Mth.wrapDegrees(Mth.approachDegrees(oldYaw, desiredYaw, maxTurn));
    float newPitch = Mth.approachDegrees(oldPitch, desiredPitch, maxTurn);
    entityData.set(yawAccessor, newYaw);
    entityData.set(pitchAccessor, newPitch);
    float yawError = Math.abs(Mth.wrapDegrees(desiredYaw - newYaw));
    float pitchError = Math.abs(desiredPitch - newPitch);
    float turn =
        Math.max(Math.abs(Mth.wrapDegrees(newYaw - oldYaw)), Math.abs(newPitch - oldPitch));
    if (yawError <= 0.75F
        && pitchError <= 0.75F
        && turn <= 0.10F
        && hasCachedStationLineOfSight(station, worldPivot, target)) {
      station.settledTicks = Math.min(SECONDARY_SETTLED_TICKS, station.settledTicks + 1);
    } else {
      station.settledTicks = 0;
    }
    return station.settledTicks >= SECONDARY_SETTLED_TICKS;
  }

  protected void tickAutocannon(
      AutocannonStation station,
      boolean left,
      EntityDataAccessor<Float> yawAccessor,
      EntityDataAccessor<Float> pitchAccessor,
      @Nullable UUID reservedTargetId) {
    station.tick();
    Vec3 localYawPivot = left ? AUTOCANNON_L_YAW_PIVOT_LOCAL : AUTOCANNON_R_YAW_PIVOT_LOCAL;
    Vec3 localPitchPivot = left ? AUTOCANNON_L_PITCH_PIVOT_LOCAL : AUTOCANNON_R_PITCH_PIVOT_LOCAL;
    Vec3 localSocket = left ? AUTOCANNON_L_MUZZLE_LOCAL : AUTOCANNON_R_MUZZLE_LOCAL;
    Vec3 worldPitchPivot =
        stationPitchPivotToWorld(localYawPivot, localPitchPivot, entityData.get(yawAccessor));
    Entity target = station.resolveTarget(worldPitchPivot, left, reservedTargetId);
    if (target == null) {
      if (!station.geometryDeferred) station.settledTicks = 0;
      return;
    }

    Vec3 direction = firingSolution(worldPitchPivot, target, AUTOCANNON_VELOCITY, 0.0D);
    if (!isFinite(direction)) {
      station.settledTicks = 0;
      return;
    }
    direction = direction.normalize();
    double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
    float desiredYaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
    float desiredPitch =
        Mth.clamp(
            (float) (Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG),
            AUTOCANNON_MIN_PITCH,
            AUTOCANNON_MAX_PITCH);
    float oldYaw = entityData.get(yawAccessor);
    float oldPitch = entityData.get(pitchAccessor);
    float newYaw = Mth.approachDegrees(oldYaw, desiredYaw, 3.0F);
    float newPitch = Mth.approachDegrees(oldPitch, desiredPitch, 3.0F);
    entityData.set(yawAccessor, Mth.wrapDegrees(newYaw));
    entityData.set(pitchAccessor, newPitch);
    float turn =
        Math.max(Math.abs(Mth.wrapDegrees(newYaw - oldYaw)), Math.abs(newPitch - oldPitch));
    if (Math.abs(Mth.wrapDegrees(desiredYaw - newYaw)) <= 0.75F
        && Math.abs(desiredPitch - newPitch) <= 0.75F
        && turn <= 0.1F
        && station.hasCachedLineOfSight(worldPitchPivot, target, left)) {
      station.settledTicks = Math.min(3, station.settledTicks + 1);
    } else {
      station.settledTicks = 0;
    }
    if (station.settledTicks < 3 || station.cooldown > 0 || station.reload > 0) {
      return;
    }

    float yaw = entityData.get(yawAccessor);
    float pitch = entityData.get(pitchAccessor);
    Vec3 muzzle = stationSocketToWorld(localYawPivot, localPitchPivot, localSocket, yaw, pitch);
    if (!station.validFrom(target, muzzle, left)) {
      station.settledTicks = 0;
      return;
    }
    Vec3 barrelDirection = stationAxisToWorld(localPitchPivot, localSocket, yaw, pitch);
    muzzle = muzzle.add(barrelDirection.scale(AUTOCANNON_MUZZLE_CLEARANCE));
    if (autonomyMayFire(left ? CORE_AUTOCANNON_LEFT : CORE_AUTOCANNON_RIGHT)
        && fireAutocannon(muzzle, barrelDirection, target)) {
      int nextInterval = station.nextFireInterval();
      station.ammo--;
      if (station.ammo <= 0) station.reload = AUTOCANNON_RELOAD;
      else station.cooldown = nextInterval;
    }
  }

  protected boolean fireAutocannon(Vec3 muzzle, Vec3 direction, Entity target) {
    if (!(level() instanceof ServerLevel serverLevel)) return false;
    if (!com.liucodex.doggiesnewvehicle.compat.DnvCombatCoordinator.fireAuthorizedBoss(this, target)) {
      return false;
    }
    if (!isTrackedTargetValid(target) || !hasClearShot(muzzle, target)) return false;
    // Use an invisible server-side hitscan round.  Superb Warfare's normal
    // projectile tick always emits a yellow/fire tracer, so no projectile
    // entity is created here.
    target.hurt(createAutocannonDamageSource(serverLevel), 10.0F);
    spawnAutocannonMuzzleEffect(serverLevel, muzzle, direction);
    level()
        .playSound(
            null,
            muzzle.x,
            muzzle.y,
            muzzle.z,
            ModSounds.STORM_AUTOCANNON_FIRE.get(),
            SoundSource.BLOCKS,
            3.6F,
            0.88F);
    tryPlayAttackVoice();
    return true;
  }

  protected DamageSource createAutocannonDamageSource(ServerLevel serverLevel) {
    Holder<DamageType> type =
        serverLevel
            .registryAccess()
            .registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(DamageTypes.MOB_ATTACK);
    return new com.liucodex.doggiesnewvehicle.core.DnvDirectArmorPenetration(
        type, this, this, 0.10F, com.liucodex.doggiesnewvehicle.combat.BossPercentDamage.fraction("silent_heaven_autocannon"));
  }

  protected static void spawnAutocannonMuzzleEffect(ServerLevel level, Vec3 muzzle, Vec3 direction) {
    Vec3 normalized = direction.normalize();
    for (int index = 0; index < AUTOCANNON_MUZZLE_PARTICLE_POINTS; index++) {
      double distance = 0.10D + index * AUTOCANNON_MUZZLE_PARTICLE_SPACING;
      Vec3 point = muzzle.add(normalized.scale(distance));
      level.sendParticles(
          AUTOCANNON_MUZZLE_PARTICLE, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }
    level.sendParticles(
        ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.025D, 0.025D, 0.025D, 0.008D);
  }

  protected boolean fireUnguidedSideRocket(Vec3 muzzle, Vec3 direction, Entity target) {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return false;
    }
    if (!com.liucodex.doggiesnewvehicle.compat.DnvCombatCoordinator.fireAuthorizedBoss(this, target)) {
      return false;
    }
    if (!isTrackedTargetValid(target) || !hasClearShot(muzzle, target)) return false;
    RpgRocketStandardEntity rocket =
        new RpgRocketStandardEntity(
            com.atsuishio.superbwarfare.init.ModEntities.RPG_ROCKET_STANDARD.get(),
            muzzle.x,
            muzzle.y,
            muzzle.z,
            serverLevel,
            SIDE_ROCKET_DAMAGE,
            SIDE_ROCKET_EXPLOSION_DAMAGE,
            SIDE_ROCKET_EXPLOSION_RADIUS);
    rocket.setOwner(this);
    com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects.suppressCameraShake(rocket);
    rocket.shoot(direction.x, direction.y, direction.z, SIDE_ROCKET_VELOCITY, 0.35F);
    rocket.setDeltaMovement(direction.scale(SIDE_ROCKET_VELOCITY));
    rocket.getPersistentData().putBoolean(OWNED_ENTITY_TAG, true);
    com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.mark(rocket, 0.20F);
    com.liucodex.doggiesnewvehicle.core.DnvPercentHits.mark(rocket, com.liucodex.doggiesnewvehicle.combat.BossPercentDamage.fraction("silent_heaven_rocket"));
    com.liucodex.doggiesnewvehicle.api.combat.DnvProjectileLifetime.track(rocket, rocket.getLife());
    serverLevel.addFreshEntity(rocket);
    playExternalSound("superbwarfare:rpg_fire_3p", muzzle, 6.0F, 0.86F);
    tryPlayAttackVoice();
    return true;
  }

  protected boolean fireTrackingMissile(Vec3 muzzle, Vec3 direction, Entity target) {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return false;
    }
    if (!com.liucodex.doggiesnewvehicle.compat.DnvCombatCoordinator.fireAuthorizedBoss(this, target)) {
      return false;
    }
    if (!isTrackedTargetValid(target) || !hasClearShot(muzzle, target)
        || !com.liucodex.doggiesnewvehicle.boss.DnvBossVlsGuidance.clearLaunch(this, target, muzzle, stationGeometryOwner(7))) return false;
    JavelinMissileEntity missile =
        new JavelinMissileEntity(
            this,
            serverLevel,
            MISSILE_DAMAGE,
            MISSILE_EXPLOSION_DAMAGE,
            MISSILE_EXPLOSION_RADIUS,
            1,
            target.getBoundingBox().getCenter());
    missile.setTargetUuid("none");
    com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects.suppressCameraShake(missile);
    missile.setGuideType(2);
    missile.setAttackMode(false);
    missile.moveTo(muzzle.x, muzzle.y, muzzle.z);
    missile.shoot(direction.x, direction.y, direction.z, MISSILE_INITIAL_VELOCITY, 0.1F);
    missile.setDeltaMovement(direction.scale(MISSILE_INITIAL_VELOCITY));
    missile.getPersistentData().putBoolean(OWNED_ENTITY_TAG, true);
    missile.getPersistentData().putUUID(OWNER_UUID_TAG, getUUID());
    com.liucodex.doggiesnewvehicle.boss.DnvBossVlsGuidance.arm(
        missile, target, getBoundingBox().maxY, MISSILE_INITIAL_VELOCITY);
    com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor.mark(missile, 0.70F);
    com.liucodex.doggiesnewvehicle.core.DnvPercentHits.mark(missile, com.liucodex.doggiesnewvehicle.combat.BossPercentDamage.fraction("silent_heaven_vls"));
    if (!serverLevel.addFreshEntity(missile)) {
      return false;
    }
    playExternalSound("superbwarfare:javelin_fire_3p", muzzle, 7.0F, 0.86F);
    tryPlayAttackVoice();
    return true;
  }

  protected Vec3 chassisPointToWorld(Vec3 local) {
    return position().add(local.yRot(-getYRot() * Mth.DEG_TO_RAD));
  }

  protected Vec3 stationPitchPivotToWorld(Vec3 localYawPivot, Vec3 localPitchPivot, float worldYaw) {
    return stationPitchPivotToWorld(localYawPivot, localPitchPivot, worldYaw, false);
  }

  protected Vec3 stationPitchPivotToWorld(
      Vec3 localYawPivot, Vec3 localPitchPivot, float worldYaw, boolean coreMounted) {
    Vec3 worldYawPivot =
        coreMounted ? corePointToWorld(localYawPivot) : chassisPointToWorld(localYawPivot);
    return worldYawPivot.add(
        localPitchPivot.subtract(localYawPivot).yRot(-worldYaw * Mth.DEG_TO_RAD));
  }

  protected Vec3 stationSocketToWorld(
      Vec3 localYawPivot, Vec3 localPitchPivot, Vec3 localSocket, float worldYaw, float pitch) {
    return stationSocketToWorld(
        localYawPivot, localPitchPivot, localSocket, worldYaw, pitch, false);
  }

  protected Vec3 stationSocketToWorld(
      Vec3 localYawPivot,
      Vec3 localPitchPivot,
      Vec3 localSocket,
      float worldYaw,
      float pitch,
      boolean coreMounted) {
    Vec3 worldPitchPivot =
        stationPitchPivotToWorld(localYawPivot, localPitchPivot, worldYaw, coreMounted);
    Vec3 socketOffset =
        localSocket
            .subtract(localPitchPivot)
            .xRot(pitch * Mth.DEG_TO_RAD)
            .yRot(-worldYaw * Mth.DEG_TO_RAD);
    return worldPitchPivot.add(socketOffset);
  }

  protected static Vec3 stationAxisToWorld(
      Vec3 localPitchPivot, Vec3 localSocket, float worldYaw, float pitch) {
    return localSocket
        .subtract(localPitchPivot)
        .xRot(pitch * Mth.DEG_TO_RAD)
        .yRot(-worldYaw * Mth.DEG_TO_RAD)
        .normalize();
  }

  protected static float localElevation(Vec3 direction) {
    double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
    return (float) (Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG);
  }

  protected Vec3 corePointToWorld(Vec3 local) {
    return position().add(local.yRot(-getCoreYaw() * Mth.DEG_TO_RAD));
  }

}
