package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.vehicle.subdata.SeekInfo;
import com.atsuishio.superbwarfare.entity.living.TargetEntity;
import com.atsuishio.superbwarfare.entity.vehicle.ai.TowerAI;
import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.ParticleTool;
import com.atsuishio.superbwarfare.tools.RangeTool;
import com.atsuishio.superbwarfare.tools.SeekTool;
import com.atsuishio.superbwarfare.tools.VectorTool;
import com.liucodex.doggiesnewvehicle.compat.DnvSbwTurretExecution;
import com.liucodex.doggiesnewvehicle.core.DnvTurretDefinition;
import com.liucodex.doggiesnewvehicle.core.DnvPlatformCapabilities.BallisticCapability;
import com.liucodex.doggiesnewvehicle.core.DnvTurretExecutionPort;
import com.liucodex.doggiesnewvehicle.core.DnvTurretTargetMemory;
import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
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

public final class ChangfengCannonEntity extends AutoAimableEntity
    implements com.liucodex.doggiesnewvehicle.api.combat.DnvNativeProjectilePercent,
    com.liucodex.doggiesnewvehicle.api.combat.DnvNativeProjectilePenetration,
    com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor,
        com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle {
  @Override public float ordinaryArmorReduction() { return 0.20F; }
  @Override public float directPercent(String weapon) {
    return MAIN_WEAPON.equals(weapon)
        ? com.liucodex.doggiesnewvehicle.artillery.ArtilleryPercentDamage.fraction("changfeng") : 0;
  }
  @Override public float directPenetration(String weapon) { return MAIN_WEAPON.equals(weapon) ? 0.35F : 0.0F; }
  private static final String MAIN_WEAPON = "Main";
  private static final String NO_TARGET_ID = "none";
  private static final double TRACKING_ALIGNMENT_DEGREES = 0.75D;
  private static final double FIRE_ALIGNMENT_DEGREES = 0.20D;
  private static final double MAX_LATCH_DRIFT_DEGREES = 0.75D;
  private static final double MAX_SETTLE_TURN_DEGREES = 0.05D;
  private static final int REQUIRED_TRACKING_TICKS = 4;
  private static final int REQUIRED_SETTLED_TICKS = 5;
  private static final int MIN_STUCK_TARGET_TICKS = 160;
  private static final int REJECTED_TARGET_TICKS = 100;
  private static final double VELOCITY_SMOOTHING = 0.25D;
  private static final double HIGH_HEALTH_REFERENCE = 200.0D;
  private static final double SLOW_SPEED_REFERENCE = 0.08D;
  private static final double SECONDARY_PRIORITY_WEIGHT = 0.25D;
  private static final double DISTANCE_TIEBREAKER_WEIGHT = 0.02D;
  private static final EntityDataAccessor<Float> SHOT_TURRET_YAW =
      SynchedEntityData.defineId(ChangfengCannonEntity.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> SHOT_TURRET_PITCH =
      SynchedEntityData.defineId(ChangfengCannonEntity.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Rotations> SYNCED_TURRET_POSE =
      SynchedEntityData.defineId(ChangfengCannonEntity.class, EntityDataSerializers.ROTATIONS);

  private String trackedTargetId = NO_TARGET_ID;
  private String rejectedTargetId = NO_TARGET_ID;
  private int alignedTicks;
  private int settledTicks;
  private int rejectedUntilTick;
  private Vec3 smoothedTargetVelocity = Vec3.ZERO;
  private Vec3 currentAimVector = Vec3.ZERO;
  private Vec3 latchedAimVector = Vec3.ZERO;
  private Vec3 postShotAimVector = Vec3.ZERO;
  private final DnvTargetCandidateCache targetCandidates =
      new DnvTargetCandidateCache(DnvTargetingBudget.MIN_FULL_SCAN_INTERVAL);
  private String relationTargetId = NO_TARGET_ID;
  private int nextRelationCheckTick;
  private boolean cachedRelationAllowed = true;
  private DnvSightObservation geometrySight;
  private boolean geometryDeferred;
  private long autonomousObservationRevision;
  private int candidateProbeCursor;
  private final DnvTurretPoseInterpolation clientTurretPose = new DnvTurretPoseInterpolation();
  private final DnvTurretTargetMemory targetMemory = new DnvTurretTargetMemory();

  private final DnvSbwTurretExecution turretExecution = new DnvSbwTurretExecution(
      this, new DnvTurretDefinition("doggievehicle:50_changfeng_cannon", "doggiesnewvehicle:changfeng",
          Map.of("vehicle", Set.of(MAIN_WEAPON)), false, "superbwarfare:cannon_shell", BallisticCapability.ARC), "vehicle", MAIN_WEAPON,
      FIRE_ALIGNMENT_DEGREES, new DnvTurretExecutionPort.Hooks<Entity, Vec3>() {
        @Override public boolean active() { return isTurretActivated(); }
        @Override public boolean settled() { return settledTicks >= REQUIRED_SETTLED_TICKS; }
        @Override public boolean targetAllowed(Entity target) {
          SeekInfo seek = data().compute().getSeekInfo();
          return seek != null && target.getStringUUID().equals(getEntityData().get(TARGET_UUID))
              && isCandidate(target, getShootPos(MAIN_WEAPON, 1.0F), seek);
        }
        @Override public void submitNative(Entity owner, UUID targetId, Vec3 targetPos) {
          ChangfengCannonEntity.super.vehicleShoot((LivingEntity) owner, MAIN_WEAPON, targetId, targetPos);
        }
        @Override public void accepted(Vec3 direction) { recordAcceptedShot(direction); }
      });

  public ChangfengCannonEntity(EntityType<? extends ChangfengCannonEntity> type, Level level) {
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

    if (getHealth() > 0.0F || tickCount % 6 != 0) return;

    double smokeX = getX() + (getRandom().nextDouble() - 0.5D) * 0.7D;
    double smokeY = getY() + 1.9D + getRandom().nextDouble() * 0.35D;
    double smokeZ = getZ() + (getRandom().nextDouble() - 0.5D) * 0.7D;
    level().addParticle(ParticleTypes.LARGE_SMOKE, smokeX, smokeY, smokeZ, 0.0D, 0.035D, 0.0D);
    if (tickCount % 18 == 0) {
      level()
          .addParticle(
              ParticleTypes.CAMPFIRE_COSY_SMOKE, smokeX, smokeY, smokeZ, 0.0D, 0.025D, 0.0D);
    }
  }

  @Override
  protected void defineSynchedData() {
    // Superb Warfare 0.8.9 is supplied as an obfuscated local dependency,
    // so its Entity override retains the runtime name in the dev compile classpath.
    super.m_8097_();
    getEntityData().define(SHOT_TURRET_YAW, 0.0F);
    getEntityData().define(SHOT_TURRET_PITCH, 0.0F);
    getEntityData().define(SYNCED_TURRET_POSE, new Rotations(0.0F, 0.0F, 0.0F));
  }

  public float getVisualTurretYaw(float partialTicks) {
    return getEntityData().get(CANNON_RECOIL_TIME) > 0
        ? getEntityData().get(SHOT_TURRET_YAW)
        : level().isClientSide
            ? clientTurretPose.yaw(partialTicks, getEntityData().get(SYNCED_TURRET_POSE))
            : getTurretYaw(partialTicks);
  }

  public float getVisualTurretPitch(float partialTicks) {
    return getEntityData().get(CANNON_RECOIL_TIME) > 0
        ? getEntityData().get(SHOT_TURRET_PITCH)
        : level().isClientSide
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

  public DnvTurretTargetMemory.Decision targetDecisionSnapshot() {
    return targetMemory.current();
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
    // Human control preempts both pending fire and recoil-hold AI motion.
    if (getFirstPassenger() != null || !getEntityData().get(ACTIVE)) {
      clearTracking();
      return;
    }
    if (turretExecution.pending()) return;

    turretExecution.prepareVirtualRound();
    if (isFiniteDirection(postShotAimVector)) {
      if (getEntityData().get(CANNON_RECOIL_TIME) > 0) {
        currentAimVector = postShotAimVector;
        turretAutoAimFromVector(postShotAimVector);
        syncAutonomousTurretPose();
        setChangeTargetTimer(0);
        return;
      }
      postShotAimVector = Vec3.ZERO;
    }
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

    if (!target.getStringUUID().equals(trackedTargetId)) {
      beginTracking(target);
    }

    if (!hasCachedLineOfSight(target, muzzle)) {
      cancelFiringSolution();
      return;
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
      cancelFiringSolution();
      countBlockedTarget(target, seekInfo);
      return;
    }

    boolean firingSolutionLatched = isFiniteDirection(latchedAimVector);
    Vec3 commandedAimVector = firingSolutionLatched ? latchedAimVector : liveAimVector;
    Vec3 previousAimVector = currentAimVector;
    Vec3 barrelVectorBeforeTurn = getShootVec(MAIN_WEAPON, 1.0F);
    float turretYawBeforeTurn = getTurretYRot();
    float turretPitchBeforeTurn = getTurretXRot();
    currentAimVector = commandedAimVector;
    boolean alignedBeforeTurn =
        isFiniteDirection(previousAimVector)
            && isFiniteDirection(barrelVectorBeforeTurn)
            && VectorTool.calculateAngle(barrelVectorBeforeTurn, previousAimVector)
                <= TRACKING_ALIGNMENT_DEGREES;
    DnvTurretExecutionPort execution = turretExecution;
    var frame = execution.inspect(target.getUUID());
    var aimResult = execution.applyAim(frame,
        new Point(currentAimVector.x, currentAimVector.y, currentAimVector.z));
    if (!aimResult.accepted()) {
      cancelFiringSolution();
      return;
    }
    syncAutonomousTurretPose();

    Vec3 visibleBarrelVector = getShootVec(MAIN_WEAPON, 1.0F);
    double alignmentError =
        isFiniteDirection(visibleBarrelVector)
            ? VectorTool.calculateAngle(visibleBarrelVector, currentAimVector)
            : 180.0D;
    double turretTurnThisTick =
        Math.max(
            Math.abs(Mth.wrapDegrees(getTurretYRot() - turretYawBeforeTurn)),
            Math.abs(getTurretXRot() - turretPitchBeforeTurn));
    Vec3 currentMuzzle = getShootPos(MAIN_WEAPON, 1.0F);
    boolean hasLineOfSight = hasCachedLineOfSight(target, currentMuzzle);

    boolean cooldownReady = turretExecution.cooldownReady(0);
    boolean weaponReady =
        !gunData.overHeat.get()
            && getAmmoCount(MAIN_WEAPON) > 0
            && gunData.canShoot(getAmmoSupplier());

    if (firingSolutionLatched) {
      double solutionDrift = VectorTool.calculateAngle(latchedAimVector, liveAimVector);
      if (!hasLineOfSight || solutionDrift > MAX_LATCH_DRIFT_DEGREES) {
        cancelFiringSolution();
        currentAimVector = liveAimVector;
        setChangeTargetTimer(0);
        return;
      }
      if (!weaponReady) {
        cancelFiringSolution();
        countBlockedTarget(target, seekInfo);
        return;
      }

      if (alignmentError <= FIRE_ALIGNMENT_DEGREES
          && turretTurnThisTick <= MAX_SETTLE_TURN_DEGREES) {
        settledTicks = Math.min(REQUIRED_SETTLED_TICKS, settledTicks + 1);
      } else {
        settledTicks = 0;
      }

      boolean canFire = settledTicks >= REQUIRED_SETTLED_TICKS && cooldownReady && hasLineOfSight;
      if (canFire) {
        var result = execution.tryFire(frame);
        if (result.accepted()) setChangeTargetTimer(0);
        else countBlockedTarget(target, seekInfo);
        cancelFiringSolution();
      } else {
        setChangeTargetTimer(0);
      }
      return;
    }

    if (alignedBeforeTurn && alignmentError <= TRACKING_ALIGNMENT_DEGREES && hasLineOfSight) {
      alignedTicks = Math.min(REQUIRED_TRACKING_TICKS, alignedTicks + 1);
    } else {
      alignedTicks = 0;
    }

    boolean cooldownReadySoon = turretExecution.cooldownReady(REQUIRED_SETTLED_TICKS);
    boolean canLatch =
        alignedTicks >= REQUIRED_TRACKING_TICKS
            && hasLineOfSight
            && !gunData.overHeat.get()
            && cooldownReadySoon
            && getAmmoCount(MAIN_WEAPON) > 0
            && gunData.canShoot(getAmmoSupplier());

    if (canLatch) {
      latchedAimVector = liveAimVector;
      currentAimVector = latchedAimVector;
      alignedTicks = 0;
      settledTicks = 0;
      setChangeTargetTimer(0);
    } else if (!cooldownReadySoon) {
      // The chamber is already loaded. Waiting for the configured cadence
      // is not a target-tracking failure.
      setChangeTargetTimer(0);
    } else {
      countBlockedTarget(target, seekInfo);
    }
  }

  @Override
  public int getAmmoCount(String weaponName) {
    GunData gunData = getGunData(weaponName);
    if (gunData == null) {
      return 0;
    }
    int energyCost = energyCostPerShot(gunData);
    return energyCost == 0 ? 1 : getEnergy() / energyCost;
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

  private void recordAcceptedShot(Vec3 direction) {
    getEntityData().set(SHOT_TURRET_YAW, getTurretYRot());
    getEntityData().set(SHOT_TURRET_PITCH, getTurretXRot());
    postShotAimVector = direction;
    setChangeTargetTimer(0);
    if (level() instanceof ServerLevel serverLevel) {
      ParticleTool.spawnMediumCannonMuzzleParticles(direction, getShootPos(MAIN_WEAPON, 1.0F), serverLevel, this);
    }
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
      // Continue a budget-deferred probe between full discovery updates.
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
        .sorted(
            Comparator.<Entity>comparingDouble(
                    target -> targetPriorityScore(target, muzzle, seekInfo))
                .reversed()
                .thenComparingDouble(target -> target.distanceToSqr(muzzle))
                .thenComparing(Entity::getUUID, DnvTargetCandidateCache::compareStableIds))
        .toList();
    int start = candidateProbeCursor;
    for (int offset = 0; offset < candidates.size(); offset++) {
      int index = (start + offset) % candidates.size();
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

  /**
   * Long-range HE fire is most valuable against durable or slow targets. The dominant trait wins,
   * while the other trait and distance only break close calls. Keeping this ordering inside
   * acquisition also preserves the existing target latch after lock-on.
   */
  private double targetPriorityScore(Entity target, Vec3 muzzle, SeekInfo seekInfo) {
    double healthScore = Mth.clamp(targetHealth(target) / HIGH_HEALTH_REFERENCE, 0.0D, 1.0D);
    Vec3 velocity = finiteVectorOrZero(target.getDeltaMovement());
    double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    double slowScore = SLOW_SPEED_REFERENCE / (SLOW_SPEED_REFERENCE + horizontalSpeed);
    double dominantTrait = Math.max(healthScore, slowScore);
    double secondaryTrait = Math.min(healthScore, slowScore);

    double rangeSpan = Math.max(1.0D, seekInfo.getMaxSeekRange() - seekInfo.getMinSeekRange());
    double distance = Math.sqrt(target.distanceToSqr(muzzle));
    double distanceScore =
        1.0D - Mth.clamp((distance - seekInfo.getMinSeekRange()) / rangeSpan, 0.0D, 1.0D);
    return dominantTrait
        + secondaryTrait * SECONDARY_PRIORITY_WEIGHT
        + distanceScore * DISTANCE_TIEBREAKER_WEIGHT;
  }

  private static double targetHealth(Entity target) {
    if (target instanceof LivingEntity living) {
      return Math.max(0.0D, living.getHealth());
    }
    if (target instanceof VehicleEntity vehicle) {
      return Math.max(0.0D, vehicle.getHealth());
    }
    return 0.0D;
  }

  private boolean isCandidate(Entity target, Vec3 muzzle, SeekInfo seekInfo) {
    if (DnvTargetExclusions.isUnavailableCombatTarget(target)) {
      return false;
    }
    if (tickCount < rejectedUntilTick && target.getStringUUID().equals(rejectedTargetId)) {
      return false;
    }

    // This is a long-range HE support cannon, not a point-defense weapon.
    // Fast projectiles produce a rapidly changing lead solution and can make
    // the turret abandon real combatants to chase missiles or Wither skulls.
    if (target instanceof Projectile) {
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
        || target.isInWater()
        || SeekTool.IS_INVULNERABLE.test(target)
        || !SeekTool.NOT_IN_SMOKE.test(target)) {
      return false;
    }
    if (target instanceof LivingEntity living && living.getHealth() <= 0.0F) {
      return false;
    }
    if (target instanceof Projectile) {
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
            tickCount, getId(), DnvTargetingBudget.RELATION_RECHECK_INTERVAL, 1);
    if (geometrySight != null && !geometrySight.targetId().equals(target.getUUID())) {
      geometrySight = null;
    }
    cancelFiringSolution();
    setChangeTargetTimer(0);
    smoothedTargetVelocity = finiteVectorOrZero(target.getDeltaMovement());
    currentAimVector = Vec3.ZERO;
  }

  private void countBlockedTarget(Entity target, SeekInfo seekInfo) {
    if (geometryDeferred) {
      setChangeTargetTimer(0);
      return;
    }
    setChangeTargetTimer(getChangeTargetTimer() + 1);
    int fireInterval = fireIntervalTicks();
    int timeout =
        Math.max(
            MIN_STUCK_TARGET_TICKS, Math.max(seekInfo.getChangeTargetTime(), fireInterval + 40));
    if (getChangeTargetTimer() > timeout) {
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
    cancelFiringSolution();
    setChangeTargetTimer(0);
    smoothedTargetVelocity = Vec3.ZERO;
    currentAimVector = Vec3.ZERO;
    postShotAimVector = Vec3.ZERO;
    relationTargetId = NO_TARGET_ID;
    cachedRelationAllowed = true;
    nextRelationCheckTick = 0;
    geometrySight = null;
    geometryDeferred = false;
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
              tickCount, getId(), DnvTargetingBudget.RELATION_RECHECK_INTERVAL, 1);
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
              DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL, 2),
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
    if (level() instanceof ServerLevel serverLevel) {
      DnvTargetingBudget.releaseGeometryOwner(serverLevel, getUUID());
    }
    }


  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.m_7378_(tag);
    if (level() instanceof ServerLevel serverLevel) {
      DnvTargetingBudget.releaseGeometryOwner(serverLevel, getUUID());
    }
    clearTracking();
  }

  private void cancelFiringSolution() {
    latchedAimVector = Vec3.ZERO;
    alignedTicks = 0;
    settledTicks = 0;
  }

  private Entity findTarget(String targetId) {
    return targetId == null || targetId.isBlank() || NO_TARGET_ID.equals(targetId)
        ? null
        : EntityFindUtil.findEntity(level(), targetId);
  }

  private int fireIntervalTicks() {
    return Math.max(1, (int) Math.ceil(1200.0D / vehicleWeaponRpm(MAIN_WEAPON)));
  }

  private static int energyCostPerShot(GunData gunData) {
    return Math.max(0, gunData.get(GunProp.AMMO_COST_PER_SHOOT));
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
