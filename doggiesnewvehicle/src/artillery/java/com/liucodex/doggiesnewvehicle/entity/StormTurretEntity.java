package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.vehicle.subdata.SeekInfo;
import com.atsuishio.superbwarfare.entity.living.TargetEntity;
import com.atsuishio.superbwarfare.entity.vehicle.ai.TowerAI;
import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.RangeTool;
import com.atsuishio.superbwarfare.tools.SeekTool;
import com.atsuishio.superbwarfare.tools.VectorTool;
import com.liucodex.doggiesnewvehicle.compat.DnvSbwTurretExecution;
import com.liucodex.doggiesnewvehicle.core.DnvTurretDefinition;
import com.liucodex.doggiesnewvehicle.core.DnvTurretExecutionPort;
import com.liucodex.doggiesnewvehicle.core.DnvTurretTargetMemory;
import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
import com.liucodex.doggiesnewvehicle.core.DnvPlatformCapabilities.BallisticCapability;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetCandidateCache;
import com.liucodex.doggiesnewvehicle.compat.DnvSightObservation;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetExclusions;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Rotations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fast, FE-powered twin machine-gun turret.
 *
 * <p>This deliberately has its own lightweight tracking loop instead of the Changfeng heavy
 * cannon's latch/settle/recoil state machine. At 480 RPM the weapon must retain a fractional
 * 2.5-tick cadence. The shared execution adapter retains its fractional phase and settles
 * FE and visual feedback only after native world admission.
 */
public final class StormTurretEntity extends AutoAimableEntity
    implements com.liucodex.doggiesnewvehicle.api.combat.DnvNativeProjectilePercent,
    com.liucodex.doggiesnewvehicle.api.combat.DnvNativeProjectilePenetration,
    com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor,
        com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle {
  @Override public float ordinaryArmorReduction() { return 0.10F; }
  @Override public float directPercent(String weapon) {
    return MAIN_WEAPON.equals(weapon)
        ? com.liucodex.doggiesnewvehicle.artillery.ArtilleryPercentDamage.fraction("storm") : 0;
  }
  @Override public float directPenetration(String weapon) { return MAIN_WEAPON.equals(weapon) ? 0.10F : 0.0F; }
  private static final String MAIN_WEAPON = "Main";
  private static final String NO_TARGET_ID = "none";
  private static final double TRACKING_ALIGNMENT_DEGREES = 0.70D;
  private static final double FIRE_ALIGNMENT_DEGREES = 0.85D;
  private static final double MAX_FIRE_TURN_DEGREES = 0.90D;
  private static final int REQUIRED_ALIGNED_TICKS = 2;
  private static final int MIN_STUCK_TARGET_TICKS = 80;
  private static final int REJECTED_TARGET_TICKS = 60;
  private static final double VELOCITY_SMOOTHING = 0.40D;
  private static final double SMOKE_BASE_HEIGHT = 0.316903D;
  private static final EntityDataAccessor<Integer> SHOT_SEQUENCE =
      SynchedEntityData.defineId(StormTurretEntity.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Rotations> SYNCED_TURRET_POSE =
      SynchedEntityData.defineId(StormTurretEntity.class, EntityDataSerializers.ROTATIONS);

  private String trackedTargetId = NO_TARGET_ID;
  private String rejectedTargetId = NO_TARGET_ID;
  private int alignedTicks;
  private int rejectedUntilTick;
  private int clientObservedShotSequence = -1;
  private int clientMuzzleFlashTicks;
  private Vec3 smoothedTargetVelocity = Vec3.ZERO;
  private final DnvTargetCandidateCache targetCandidates =
      new DnvTargetCandidateCache(DnvTargetingBudget.MIN_FULL_SCAN_INTERVAL);
  private String relationTargetId = NO_TARGET_ID;
  private int nextRelationCheckTick;
  private boolean cachedRelationAllowed = true;
  private DnvSightObservation geometrySight;
  private int candidateProbeCursor;
  private boolean geometryDeferred;
  private long autonomousObservationRevision;
  private final DnvTurretPoseInterpolation clientTurretPose = new DnvTurretPoseInterpolation();
  private final DnvTurretTargetMemory targetMemory = new DnvTurretTargetMemory();

  private final DnvSbwTurretExecution turretExecution = new DnvSbwTurretExecution(
      this, new DnvTurretDefinition("doggievehicle:storm_turret", "doggiesnewvehicle:storm",
          Map.of("vehicle", Set.of(MAIN_WEAPON)), false, "doggievehicle:storm_bullet", BallisticCapability.DIRECT),
      "vehicle", MAIN_WEAPON, FIRE_ALIGNMENT_DEGREES, new DnvTurretExecutionPort.Hooks<Entity, Vec3>() {
        @Override public boolean active() { return isTurretActivated(); }
        @Override public boolean settled() { return alignedTicks >= REQUIRED_ALIGNED_TICKS; }
        @Override public boolean targetAllowed(Entity target) {
          SeekInfo seek = data().compute().getSeekInfo();
          return seek != null && target.getStringUUID().equals(getEntityData().get(TARGET_UUID))
              && isTargetStillValid(target, getShootPos(MAIN_WEAPON, 1.0F), seek)
              && isCandidate(target, getShootPos(MAIN_WEAPON, 1.0F), seek);
        }
        @Override public void submitNative(Entity owner, UUID targetId, Vec3 targetPos) {
          StormTurretEntity.super.vehicleShoot((LivingEntity) owner, MAIN_WEAPON, targetId, targetPos);
        }
        @Override public void accepted(Vec3 direction) {
          getEntityData().set(SHOT_SEQUENCE, getShotSequence() + 1);
          setChangeTargetTimer(0);
        }
      });

  public StormTurretEntity(EntityType<? extends StormTurretEntity> type, Level level) {
    super(type, level);
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide) {
      return;
    }

    Rotations syncedPose = getEntityData().get(SYNCED_TURRET_POSE);
    var motion = clientTurretPose.advance(syncedPose);
    setTurretYRot(clientTurretPose.currentYaw(syncedPose));
    setTurretXRot(clientTurretPose.currentPitch(syncedPose));
    turretTurnSound(motion.pitchDelta(), motion.yawDelta(), 0.95F);

    if (clientMuzzleFlashTicks > 0) {
      clientMuzzleFlashTicks--;
    }

    int shotSequence = getShotSequence();
    if (clientObservedShotSequence < 0) {
      clientObservedShotSequence = shotSequence;
    } else if (shotSequence != clientObservedShotSequence) {
      clientObservedShotSequence = shotSequence;
      clientMuzzleFlashTicks = 2;
      if (getHealth() > 0.0F) {
        spawnBaseSmoke(false);
      }
    }

    if (getHealth() <= 0.0F && tickCount % 6 == 0) {
      spawnBaseSmoke(true);
    }
  }

  private void spawnBaseSmoke(boolean wrecked) {
    double spread = wrecked ? 0.34D : 0.18D;
    double smokeX = getX() + (getRandom().nextDouble() - 0.5D) * spread;
    double smokeY = getY() + SMOKE_BASE_HEIGHT + getRandom().nextDouble() * 0.08D;
    double smokeZ = getZ() + (getRandom().nextDouble() - 0.5D) * spread;
    level()
        .addParticle(
            wrecked ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
            smokeX,
            smokeY,
            smokeZ,
            0.0D,
            wrecked ? 0.035D : 0.022D,
            0.0D);
  }

  @Override
  protected void defineSynchedData() {
    // Superb Warfare 0.8.9 retains the runtime method name in this local
    // obfuscated dependency, matching the already verified Changfeng path.
    super.m_8097_();
    getEntityData().define(SHOT_SEQUENCE, 0);
    getEntityData().define(SYNCED_TURRET_POSE, new Rotations(0.0F, 0.0F, 0.0F));
  }

  public float getVisualTurretYaw(float partialTicks) {
    return level().isClientSide
        ? clientTurretPose.yaw(partialTicks, getEntityData().get(SYNCED_TURRET_POSE))
        : getTurretYaw(partialTicks);
  }

  public float getVisualTurretPitch(float partialTicks) {
    return level().isClientSide
        ? clientTurretPose.pitch(partialTicks, getEntityData().get(SYNCED_TURRET_POSE))
        : getTurretPitch(partialTicks);
  }

  public boolean isTurretActivated() {
    return getEntityData().get(ACTIVE);
  }

  public boolean hasOperationalPower() { return getEnergy() > 0; }

  public void syncAutonomousTurretPose() {
    if (!level().isClientSide) {
      getEntityData().set(SYNCED_TURRET_POSE, new Rotations(getTurretYRot(), getTurretXRot(), 0.0F));
    }
  }

  public int getShotSequence() {
    return getEntityData().get(SHOT_SEQUENCE);
  }

  public DnvTurretTargetMemory.Decision targetDecisionSnapshot() {
    return targetMemory.current();
  }

  public boolean hasClientMuzzleFlash() {
    return clientMuzzleFlashTicks > 0;
  }

  @Override
  public void autoAim() {
    if (!(level() instanceof ServerLevel serverLevel)) return;
    geometryDeferred = false;
    if (isWreck() || getHealth() <= 0.0F) {
      clearTracking();
      return;
    }

    DnvTurretTargetMemory.Configuration targetingContext =
        turretExecution.targetingContext();
    if (!targetingContext.coreActive()) {
      clearTracking(DnvTurretTargetMemory.InvalidationReason.CORE_INACTIVE);
      return;
    }
    GunData gunData = getGunData(MAIN_WEAPON);
    if (gunData == null) {
      clearTracking();
      return;
    }

    if (getFirstPassenger() != null || !isTurretActivated()) {
      clearTracking();
      return;
    }
    if (turretExecution.pending()) return;
    turretExecution.prepareVirtualRound();

    SeekInfo seekInfo = data().compute().getSeekInfo();
    Entity ownerEntity = getOwner();
    if (seekInfo == null || !(ownerEntity instanceof LivingEntity)) {
      clearTracking();
      return;
    }
    if (getEnergy() < seekInfo.getSeekEnergyCost()) {
      clearTracking();
      return;
    }

    Vec3 muzzle = getShootPos(MAIN_WEAPON, 1.0F);
    Entity target = resolveOrAcquireTarget(
        serverLevel, muzzle, seekInfo, targetingContext.profileRevision());
    if (target == null) {
      return;
    }
    if (!isTargetStillValid(target, muzzle, seekInfo)) {
      rejectAndClear(target);
      return;
    }
    if (!hasCachedLineOfSight(target, muzzle)) {
      resetAlignmentClock();
      return;
    }
    if (!target.getStringUUID().equals(trackedTargetId)) {
      beginTracking(target);
    }

    Vec3 targetVelocity = finiteVectorOrZero(target.getDeltaMovement());
    smoothedTargetVelocity =
        finiteVectorOrZero(smoothedTargetVelocity)
            .scale(1.0D - VELOCITY_SMOOTHING)
            .add(targetVelocity.scale(VELOCITY_SMOOTHING));
    Vec3 liveAimVector =
        RangeTool.calculateFiringSolution(
            muzzle,
            target.getBoundingBox().getCenter(),
            smoothedTargetVelocity,
            getProjectileVelocity(MAIN_WEAPON),
            getProjectileGravity(MAIN_WEAPON));
    if (!isFiniteDirection(liveAimVector)) {
      resetAlignmentClock();
      countBlockedTarget(target, seekInfo);
      return;
    }

    float yawBeforeTurn = getTurretYRot();
    float pitchBeforeTurn = getTurretXRot();
    DnvTurretExecutionPort execution = turretExecution;
    var frame = execution.inspect(target.getUUID());
    var aimResult = execution.applyAim(frame, new Point(liveAimVector.x, liveAimVector.y, liveAimVector.z));
    if (!aimResult.accepted()) {
      resetAlignmentClock();
      countBlockedTarget(target, seekInfo);
      return;
    }
    syncAutonomousTurretPose();

    Vec3 barrelVector = getShootVec(MAIN_WEAPON, 1.0F);
    double alignmentError =
        isFiniteDirection(barrelVector)
            ? VectorTool.calculateAngle(barrelVector, liveAimVector)
            : 180.0D;
    double turretTurnThisTick =
        Math.max(
            Math.abs(Mth.wrapDegrees(getTurretYRot() - yawBeforeTurn)),
            Math.abs(getTurretXRot() - pitchBeforeTurn));
    Vec3 currentMuzzle = getShootPos(MAIN_WEAPON, 1.0F);
    boolean hasLineOfSight = hasCachedLineOfSight(target, currentMuzzle);

    if (alignmentError <= TRACKING_ALIGNMENT_DEGREES
        && turretTurnThisTick <= MAX_FIRE_TURN_DEGREES
        && hasLineOfSight) {
      alignedTicks = Math.min(REQUIRED_ALIGNED_TICKS, alignedTicks + 1);
    } else {
      resetAlignmentClock();
    }

    boolean weaponReady =
        !gunData.overHeat.get()
            && getAmmoCount(MAIN_WEAPON) > 0
            && gunData.canShoot(getAmmoSupplier());
    boolean cadenceReady = turretExecution.cooldownReady(0);
    if (alignedTicks >= REQUIRED_ALIGNED_TICKS
        && alignmentError <= FIRE_ALIGNMENT_DEGREES
        && hasLineOfSight
        && weaponReady
        && cadenceReady) {
      var result = execution.tryFire(frame);
      if (result.accepted()) {
        setChangeTargetTimer(0);
      } else {
        countBlockedTarget(target, seekInfo);
      }
    } else if (!weaponReady && cadenceReady) {
      countBlockedTarget(target, seekInfo);
    } else {
      setChangeTargetTimer(0);
    }
  }

  private void resetAlignmentClock() {
    alignedTicks = 0;
  }

  @Override
  public int getAmmoCount(String weaponName) {
    GunData gunData = getGunData(weaponName);
    if (gunData == null) {
      return 0;
    }
    int energyCost = Math.max(0, gunData.get(GunProp.AMMO_COST_PER_SHOOT));
    return getEnergy() >= energyCost ? Math.max(1, energyCost) : 0;
  }

  @Override
  public void vehicleShoot(LivingEntity living, UUID targetUuid, Vec3 targetPos) {
    // Existing autonomous units have no validated remote weapon-station lease.
  }

  @Override
  public void vehicleShoot(
      LivingEntity living, String weaponName, UUID targetUuid, Vec3 targetPos) {
    // Existing autonomous units have no validated remote weapon-station lease.
  }

  @Override
  public void vehicleShoot(LivingEntity living, String weaponName, Vec3 targetPos) {
    // Autonomous fire uses the private execution port; native/manual entry is not an authorization.
  }

  private Entity resolveOrAcquireTarget(
      ServerLevel serverLevel,
      Vec3 muzzle,
      SeekInfo seekInfo,
      long configurationRevision) {
    long now = serverLevel.getGameTime();
    Entity current = findTarget(getEntityData().get(TARGET_UUID));
    DnvTurretTargetMemory.CommandObservation<Entity> command = turretExecution.targetCommand();
    Entity commanded = command.target()
        .filter(candidate -> isCandidate(candidate, muzzle, seekInfo))
        .orElse(null);
    DnvTurretTargetMemory.CommandMode commandMode = command.commandMode();

    int scanInterval = DnvTargetingBudget.fullScanInterval(seekInfo.getSeekIterative());
    Entity autonomous = null;
    if (commandMode == DnvTurretTargetMemory.CommandMode.NONE) {
      if (targetCandidates.refreshIfDue(this,
          seekInfo.getMaxSeekRange() + muzzle.distanceTo(position()) + 1.0D,
          candidate -> isCandidate(candidate, muzzle, seekInfo), scanInterval)) {
        autonomousObservationRevision++;
      }
      // Continue budget-deferred visibility probes between discovery updates.
      autonomous = seekCandidate(muzzle, seekInfo, current);
    }

    Optional<DnvTurretTargetMemory.Candidate> commandCandidate = commanded == null
        ? Optional.empty()
        : Optional.of(DnvTurretTargetMemory.Candidate.command(
            commanded.getUUID(), command.explicitDesignation(), command.revision(), now,
            command.validUntilTick(), targetPosition(commanded)));
    Optional<DnvTurretTargetMemory.Candidate> autonomousCandidate = autonomous == null
        ? Optional.empty()
        : Optional.of(DnvTurretTargetMemory.Candidate.autonomous(
            autonomous.getUUID(), autonomousObservationRevision, now,
            later(now, scanInterval), targetPosition(autonomous)));
    DnvTurretTargetMemory.Decision decision = targetMemory.decide(
        new DnvTurretTargetMemory.Input(now, configurationRevision, true, commandMode,
            commandCandidate, autonomousCandidate));
    Entity selected = decision.target().map(DnvTurretTargetMemory.TargetSnapshot::targetId)
        .map(serverLevel::getEntity).orElse(null);
    if (selected == null) {
      clearExecutionTracking();
      return null;
    }
    if (!selected.getStringUUID().equals(getEntityData().get(TARGET_UUID))) {
      getEntityData().set(TARGET_UUID, selected.getStringUUID());
      if (decision.target().orElseThrow().source() == DnvTurretTargetMemory.Source.AUTONOMOUS
          && current == null) {
        consumeEnergy(seekInfo.getSeekEnergyCost());
      }
      beginTracking(selected);
    } else if (!selected.getStringUUID().equals(trackedTargetId)) {
      beginTracking(selected);
    }
    return selected;
  }

  private static DnvTurretTargetMemory.Position targetPosition(Entity target) {
    Vec3 position = target.getBoundingBox().getCenter();
    return new DnvTurretTargetMemory.Position(position.x, position.y, position.z);
  }

  private static long later(long now, int ticks) {
    return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks;
  }

  private Entity seekCandidate(Vec3 muzzle, SeekInfo seekInfo, Entity current) {
    if (current != null && isCandidate(current, muzzle, seekInfo)) {
      if (hasCachedLineOfSight(current, muzzle) || geometryDeferred) return current;
    }
    var candidates = targetCandidates.candidates(this).stream()
        .filter(target -> target != current)
        .filter(target -> isCandidate(target, muzzle, seekInfo))
        .sorted(Comparator.comparingDouble((Entity target) -> target.distanceToSqr(muzzle))
            .thenComparing(Entity::getUUID, DnvTargetCandidateCache::compareStableIds))
        .toList();
    int start = candidateProbeCursor;
    for (int offset = 0; offset < candidates.size(); offset++) {
      int index = Math.floorMod(start + offset, candidates.size());
      Entity target = candidates.get(index);
      if (hasCachedLineOfSight(target, muzzle)) {
        candidateProbeCursor = 0;
        return target;
      }
      if (geometryDeferred) {
        candidateProbeCursor = index;
        return null;
      }
      candidateProbeCursor = (index + 1) % candidates.size();
    }
    return null;
  }

  private boolean isCandidate(Entity target, Vec3 muzzle, SeekInfo seekInfo) {
    if (DnvTargetExclusions.isUnavailableCombatTarget(target)) {
      return false;
    }
    if (tickCount < rejectedUntilTick && target.getStringUUID().equals(rejectedTargetId)) {
      return false;
    }
    // The requested experimental Storm is a conventional anti-entity gun,
    // not the projectile-interception point-defense concept.
    if (target instanceof Projectile || target instanceof TargetEntity) {
      return false;
    }

    double distanceSqr = target.distanceToSqr(this);
    double minRangeSqr = seekInfo.getMinSeekRange() * seekInfo.getMinSeekRange();
    double maxRangeSqr = seekInfo.getMaxSeekRange() * seekInfo.getMaxSeekRange();
    boolean legacyHostile =
        target instanceof LivingEntity living
            && living instanceof Enemy
            && living.getHealth() > 0.0F;
    boolean acceptedEnemy = turretExecution.allowsTargetRelation(
        target,
        legacyHostile || TowerAI.TeamResolver.isHostile(this, target));
    return acceptedEnemy
        && !SeekTool.IN_BLACKLIST.test(target)
        && distanceSqr > minRangeSqr
        && distanceSqr <= maxRangeSqr
        && canAim(muzzle, target, getTurretMinPitch(), getTurretMaxPitch())
        && VehicleVecUtils.getSubmergedHeight(target) <= target.getBbHeight()
        && SeekTool.NOT_IN_SMOKE.test(target);
  }

  private boolean isTargetStillValid(Entity target, Vec3 muzzle, SeekInfo seekInfo) {
    if (DnvTargetExclusions.isUnavailableCombatTarget(target)
        || target == this
        || target instanceof TargetEntity
        || target instanceof Projectile
        || target.isInWater()
        || SeekTool.IS_INVULNERABLE.test(target)
        || !SeekTool.NOT_IN_SMOKE.test(target)) {
      return false;
    }
    if (target instanceof LivingEntity living && living.getHealth() <= 0.0F) {
      return false;
    }
    if (!passesCachedCombatRelation(target)) {
      return false;
    }
    double distance = target.distanceTo(this);
    return distance >= seekInfo.getMinSeekRange()
        && distance <= seekInfo.getMaxSeekRange()
        && canAim(muzzle, target, getTurretMinPitch(), getTurretMaxPitch());
  }

  private void beginTracking(Entity target) {
    trackedTargetId = target.getStringUUID();
    relationTargetId = trackedTargetId;
    cachedRelationAllowed = true;
    nextRelationCheckTick =
        DnvTargetingBudget.nextCheckTick(
            tickCount, getId(), DnvTargetingBudget.RELATION_RECHECK_INTERVAL, 3);
    geometrySight = null;
    alignedTicks = 0;
    setChangeTargetTimer(0);
    smoothedTargetVelocity = finiteVectorOrZero(target.getDeltaMovement());
  }

  private void countBlockedTarget(Entity target, SeekInfo seekInfo) {
    if (geometryDeferred) {
      setChangeTargetTimer(0);
      return;
    }
    setChangeTargetTimer(getChangeTargetTimer() + 1);
    if (getChangeTargetTimer() > Math.max(MIN_STUCK_TARGET_TICKS, seekInfo.getChangeTargetTime())) {
      rejectAndClear(target);
    }
  }

  private void rejectAndClear(Entity target) {
    rejectedTargetId = target == null ? NO_TARGET_ID : target.getStringUUID();
    rejectedUntilTick = tickCount + REJECTED_TARGET_TICKS;
    clearTracking(DnvTurretTargetMemory.InvalidationReason.TARGET_UNAVAILABLE);
  }

  private void clearTracking() {
    clearTracking(DnvTurretTargetMemory.InvalidationReason.ENTITY_INVALIDATED);
  }

  private void clearTracking(DnvTurretTargetMemory.InvalidationReason reason) {
    targetCandidates.clear();
    long now = level() == null ? 0L : Math.max(0L, level().getGameTime());
    targetMemory.invalidate(now, reason);
    clearExecutionTracking();
  }

  private void clearExecutionTracking() {
    turretExecution.invalidate();
    getEntityData().set(TARGET_UUID, NO_TARGET_ID);
    trackedTargetId = NO_TARGET_ID;
    alignedTicks = 0;
    setChangeTargetTimer(0);
    smoothedTargetVelocity = Vec3.ZERO;
    relationTargetId = NO_TARGET_ID;
    cachedRelationAllowed = true;
    nextRelationCheckTick = 0;
    geometrySight = null;
    geometryDeferred = false;
  }

  private Entity findTarget(String targetId) {
    return targetId == null || targetId.isBlank() || NO_TARGET_ID.equals(targetId)
        ? null
        : EntityFindUtil.findEntity(level(), targetId);
  }

  private boolean passesCachedCombatRelation(Entity target) {
    String targetId = target.getStringUUID();
    if (!targetId.equals(relationTargetId) || tickCount >= nextRelationCheckTick) {
      relationTargetId = targetId;
      boolean nativeHostile = target instanceof LivingEntity living
          && living instanceof Enemy
          && living.getHealth() > 0.0F;
      cachedRelationAllowed = turretExecution.allowsTargetRelation(
          target,
          nativeHostile || TowerAI.TeamResolver.isHostile(this, target));
      nextRelationCheckTick =
          DnvTargetingBudget.nextCheckTick(
              tickCount, getId(), DnvTargetingBudget.RELATION_RECHECK_INTERVAL, 3);
    }
    return cachedRelationAllowed;
  }

  private boolean hasCachedLineOfSight(Entity target, Vec3 muzzle) {
    geometryDeferred = false;
    if (!level().hasChunksAt(net.minecraft.core.BlockPos.containing(muzzle),
        net.minecraft.core.BlockPos.containing(target.position()))) return false;
    long pose = ((long) Math.round(getTurretYRot() * 2.0F) << 32)
        ^ (Math.round(getTurretXRot() * 2.0F) & 0xffffffffL)
        ^ (31L * Math.round(getYRot() * 2.0F));
    if (geometrySight == null || !geometrySight.reusable(target.getUUID(), muzzle,
        target.getBoundingBox(), target.getEyeY(), pose, tickCount)) {
      if (!(level() instanceof ServerLevel serverLevel)
          || !DnvTargetingBudget.admitGeometryCheck(serverLevel, getUUID())) {
        geometryDeferred = true;
        return false;
      }
      geometrySight = new DnvSightObservation(target.getUUID(), muzzle,
          target.getBoundingBox(), target.getEyeY(), pose, tickCount,
          DnvTargetingBudget.nextCheckTick(tickCount, getId(),
              DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL, 4),
          checkNoClip(target, muzzle));
    }
    return geometrySight.clear();
  }

    @Override public void remove(RemovalReason reason) {
        releaseExternalState(reason);
        super.remove(reason);
    }

    @Override public void releaseExternalState(RemovalReason reason) {
    turretExecution.invalidate();
    targetCandidates.clear();
    candidateProbeCursor = 0;
    if (level() instanceof ServerLevel serverLevel) {
      DnvTargetingBudget.releaseGeometryOwner(serverLevel, getUUID());
    }
    }


  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.m_7378_(tag);
    candidateProbeCursor = 0;
    if (level() instanceof ServerLevel serverLevel) {
      DnvTargetingBudget.releaseGeometryOwner(serverLevel, getUUID());
    }
    clearTracking();
  }

  private static boolean isFiniteDirection(Vec3 direction) {
    return isFiniteVector(direction) && direction.lengthSqr() > 1.0e-8D;
  }

  private static Vec3 finiteVectorOrZero(Vec3 vector) {
    return isFiniteVector(vector) ? vector : Vec3.ZERO;
  }

  private static boolean isFiniteVector(Vec3 vector) {
    return vector != null
        && Double.isFinite(vector.x)
        && Double.isFinite(vector.y)
        && Double.isFinite(vector.z);
  }
}
