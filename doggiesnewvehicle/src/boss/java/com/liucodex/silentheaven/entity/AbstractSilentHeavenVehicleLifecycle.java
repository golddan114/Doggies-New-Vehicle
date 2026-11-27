package com.liucodex.silentheaven.entity;

import com.liucodex.doggiesnewvehicle.core.DnvGroundDrive;
import com.liucodex.doggiesnewvehicle.compat.DnvGroundTerrainProbe;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore;
import com.liucodex.doggiesnewvehicle.core.DnvLazyCandidateFrame;
import com.liucodex.doggiesnewvehicle.compat.DnvCombatPolicyRuntime;
import com.liucodex.doggiesnewvehicle.compat.DnvObstructionPolicy;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetExclusions;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicy;
import com.liucodex.silentheaven.init.ModSounds;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

abstract class AbstractSilentHeavenVehicleLifecycle extends AbstractSilentHeavenVehicleState
    implements DnvCombatPolicy.BossAutonomy {
  protected static final String CORE_MAIN = "main";
  protected static final String CORE_AUTOCANNON_LEFT = "autocannon_left";
  protected static final String CORE_AUTOCANNON_RIGHT = "autocannon_right";
  protected static final String CORE_MISSILE_LEFT = "missile_left";
  protected static final String CORE_MISSILE_RIGHT = "missile_right";
  protected static final String CORE_VLS = "vls";
  protected final DnvGroundDrive groundDrive = new DnvGroundDrive();
  protected boolean driveBrake;
  private long nextAutonomyDecisionGameTime;
  @Nullable private UUID autonomyDecisionTargetId;
  private Optional<UUID> autonomyDecisionForcedTarget = Optional.empty();
  private boolean autonomyDecisionHold;
  private boolean autonomyDecisionMovementLocked;
  private boolean autonomyDecisionHasDestination;
  private Map<String, DnvAutonomyCore.DomainStatus> autonomyDecisionDomains = Map.of();

  private final com.liucodex.doggiesnewvehicle.compat.DnvTargetSight acquisitionSight =
      new com.liucodex.doggiesnewvehicle.compat.DnvTargetSight(0);

  protected AbstractSilentHeavenVehicleLifecycle(
      EntityType<? extends SilentHeavenVehicleEntity> type, Level level) {
    super(type, level);
  }

  @Override
  public void tick() {
    if (getDestructionTicks() > 0) {
      tickDestructionSequence();
      return;
    }
    if (isWreck() || getHealth() <= 0.0F) {
      if (!level().isClientSide) {
        clearCombatStateForWreck();
      }
      // SBW 0.8.9 keeps a destroyed vehicle as a persistent wreck. Its
      // base tick still owns wreck physics and particles, but none of
      // the expensive autonomous combat path may run afterwards.
      super.tick();
      return;
    }
    wreckCombatStateCleared = false;
    Entity target = null;
    boolean candidateScanCompleted = false;
    if (!level().isClientSide) {
      if (teleportCooldownTicks > 0) {
        teleportCooldownTicks--;
      }
      if (!coreOrientationInitialized) {
        alignCoreToHull();
      }
      tickEntrancePresentation();
      if (isEntering()) {
        lockForEntrance();
        tickVoiceState();
      } else {
        if (getPhase() == PHASE_ONE && getHealth() <= PHASE_THRESHOLD) {
          beginTransition();
        }
        if (isTransitioning()) {
          lockForTransition();
        } else if (isTeleporting()) {
          tickTeleportSequence();
        } else {
          target = resolveOrAcquireTarget();
          candidateScanCompleted = targetCandidates.refreshedAt(tickCount);
          tickCombatRecovery(target);
          tickObstructionTeleport(target);
          target = resolveCurrentTarget(); // Recovery may have selected a different enemy.
          updateAutonomousControls(target);
        }
        tickVoiceState();
      }
    }

    super.tick();
    if (level().isClientSide) {
      return;
    }
    if (isRemoved() || getHealth() <= 0.0F) {
      clearBossBar();
      return;
    }

    if (isEntering()) {
      updateBossBar();
      return;
    }

    tickPendingStrikes();
    stabilizeFallingWarheads();
    if (isTeleporting()) {
      updateBossBar();
      return;
    }
    if (getPhase() == PHASE_TRANSITION) {
      tickTransition();
    } else {
      target = resolveCurrentTarget();
      tickCoreAim(target);
      tickMainGuns(target);
      try (var frame = new DnvLazyCandidateFrame<Entity>(() -> targetCandidates.candidates(this))) {
        prepareSecondaryAllocation(frame, candidateScanCompleted);
        tickAutocannons();
        tickMissilePods();
        if (getPhase() == PHASE_TWO) tickVlsBattery();
      }
      if (getPhase() == PHASE_TWO) {
        setHaloAngle(getHaloAngle() + 4.0F);
        tickHaloWeapon(target);
        tickDroneLauncher(target);
      } else {
        vlsBattery.disableAndEmpty();
      }
    }
    updateBossBar();
  }

  protected void clearCombatStateForWreck() {
    if (wreckCombatStateCleared) {
      return;
    }
    wreckCombatStateCleared = true;
    clearAutonomyDecision();
    clearBossBar();
    targetCandidates.clear();
    acquisitionSight.clear();
    releaseStationGeometryOwners();
    combatTargetId = null;
    haloTargetId = null;
    patrolDestination = null;
    mainSight = null;
    attackSight = null;
    attackLineDeferred = false;
    obstructionProbeCursor = 0;
    requestedThrottle = 0.0F;
    requestedSteering = 0.0F;
    mainAim = Vec3.ZERO;
    mainSettledTicks = 0;
    pendingStrikes.clear();
    fallingWarheads.clear();
    autocannonLeft.disable();
    autocannonRight.disable();
    missilePodLeft.disable();
    missilePodRight.disable();
    vlsBattery.disable();
    cancelTeleportSequence();
    entityData.set(POWER, 0.0F);
    entityData.set(DELTA_ROT, 0.0F);
    entityData.set(DRIVE_INTENSITY, 0.0F);
    entityData.set(TRACK_SPEED_L, 0.0F);
    entityData.set(TRACK_SPEED_R, 0.0F);
  }

  /** Re-reading NBT replaces short-lived target observations and budget identities. */
  protected void resetTargetingForLoad() {
    clearAutonomyDecision();
    targetCandidates.clear();
    acquisitionSight.clear();
    releaseStationGeometryOwners();
    pendingStrikes.clear();
    fallingWarheads.clear();
    lastWarheadRecoveryTick = DnvTargetingBudget.NEVER_SCANNED;
    mainSight = null;
    attackSight = null;
    attackLineDeferred = false;
    obstructionProbeCursor = 0;
    lastAttackLineTick = DnvTargetingBudget.NEVER_SCANNED;
    combatTargetId = null;
    haloTargetId = null;
    mainAim = Vec3.ZERO;
    mainSettledTicks = 0;
    resetObstructionTracking();
    autocannonLeft.arm(0, 0);
    autocannonRight.arm(3, 1);
    missilePodLeft.arm(MISSILE_MAGAZINE);
    missilePodRight.arm(MISSILE_MAGAZINE);
    vlsBattery.arm(VLS_MAGAZINE);
    wreckCombatStateCleared = false;
  }

  protected void tickDestructionSequence() {
    clearCombatStateForWreck();
    setDeltaMovement(Vec3.ZERO);
    super.tick();
    if (!(level() instanceof ServerLevel serverLevel)) {
      return;
    }

    int remaining = getDestructionTicks();
    if ((remaining & 1) == 0) {
      serverLevel.sendParticles(
          ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.2D, getZ(), 5, 1.9D, 1.0D, 1.9D, 0.035D);
    }
    if (remaining % 8 == 0) {
      serverLevel.sendParticles(
          ParticleTypes.FLAME, getX(), getY() + 1.5D, getZ(), 3, 1.4D, 0.55D, 1.4D, 0.025D);
    }

    int next = remaining - 1;
    setDestructionTicks(next);
    if (next <= 0) {
      super.destroy();
      if (!isRemoved()) {
        discard();
      }
    }
  }

  protected void lockForEntrance() {
    clearAutonomyDecision();
    combatTargetId = null;
    haloTargetId = null;
    patrolDestination = null;
    requestedThrottle = 0.0F;
    requestedSteering = 0.0F;
    mainAim = Vec3.ZERO;
    mainSettledTicks = 0;
    mainSight = null;
    attackSight = null;
    cancelTeleportSequence();
    entityData.set(POWER, 0.0F);
    entityData.set(DELTA_ROT, 0.0F);
    entityData.set(DRIVE_INTENSITY, 0.0F);
    entityData.set(TRACK_SPEED_L, 0.0F);
    entityData.set(TRACK_SPEED_R, 0.0F);
    Vec3 motion = getDeltaMovement();
    setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
  }

  protected void lockForTransition() {
    clearAutonomyDecision();
    requestedThrottle = 0.0F;
    requestedSteering = 0.0F;
    mainAim = Vec3.ZERO;
    mainSettledTicks = 0;
    mainSight = null;
    attackSight = null;
    cancelTeleportSequence();
    entityData.set(POWER, 0.0F);
    entityData.set(DELTA_ROT, 0.0F);
    entityData.set(DRIVE_INTENSITY, 0.0F);
    entityData.set(TRACK_SPEED_L, 0.0F);
    entityData.set(TRACK_SPEED_R, 0.0F);
    Vec3 motion = getDeltaMovement();
    setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
  }

  @Nullable
  protected Entity resolveOrAcquireTarget() {
    Entity assigned =
        com.liucodex.doggiesnewvehicle.compat.DnvCombatCoordinator.assignedTarget(this)
            .filter(this::isCheapTargetValid)
            .orElse(null);
    if (assigned != null) {
      targetCandidates.pinExternal(assigned);
      if (!assigned.getUUID().equals(combatTargetId)) {
        mainSight = null;
        attackSight = null;
        resetObstructionTracking();
      }
      Boolean assignedVisible = acquisitionSight.visible(this, position().add(0, 3.58, 0), assigned);
      if (!Boolean.TRUE.equals(assignedVisible)) {
        if (Boolean.FALSE.equals(assignedVisible)) combatTargetId = null;
        return null;
      }
      combatTargetId = assigned.getUUID();
      return assigned;
    }
    targetCandidates.clearPinnedExternal();
    Entity current = resolveCurrentTarget();
    var lossBehavior =
        com.liucodex.doggiesnewvehicle.compat.DnvCombatCoordinator.lossBehavior(this);
    if (lossBehavior
        == com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider.LossBehavior.DISABLE) {
      combatTargetId = null;
      mainSight = null;
      attackSight = null;
      resetObstructionTracking();
      lastAttackLineTick = DnvTargetingBudget.NEVER_SCANNED;
      return null;
    }
    if (lossBehavior
        == com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider.LossBehavior
            .HOLD_LAST_TARGET) {
      return current;
    }
    if (current == null && combatTargetId != null) return null; // Observation deferred: retain identity only.
    targetCandidates.refreshIfDue(this, TARGET_RANGE, this::isAcquisitionTarget);
    Entity selected =
        acquisitionSight.select(this, position().add(0, 3.58, 0),
            targetCandidates.candidates(this), current, this::isTrackedTargetValid);
    UUID previousId = combatTargetId;
    combatTargetId = selected == null ? null : selected.getUUID();
    boolean targetChanged =
        previousId == null
            ? selected != null
            : selected == null || !previousId.equals(selected.getUUID());
    if (targetChanged) {
      mainSight = null;
      attackSight = null;
      resetObstructionTracking();
      lastAttackLineTick = DnvTargetingBudget.NEVER_SCANNED;
    }
    if (selected != null && !selected.getUUID().equals(previousId)) {
      queueTargetAcquiredVoice();
    }
    return selected;
  }

  @Nullable
  protected Entity resolveCurrentTarget() {
    if (combatTargetId == null || !(level() instanceof ServerLevel serverLevel)) {
      return null;
    }
    Entity target = serverLevel.getEntity(combatTargetId);
    if (!isTrackedTargetValid(target)) {
      if (target != null) {
        autonomyCore.acceptFeedback(new DnvAutonomyCore.ExecutionFeedback(
            serverLevel.getGameTime(),
            "movement",
            Optional.of(target.getUUID()),
            DnvAutonomyCore.ExecutionOutcome.TARGET_INVALID,
            "target_validation_failed"));
      }
      if (target != null
          && target.isAlive()
          && distanceToSqr(target) > TARGET_RANGE * TARGET_RANGE) {
        queueVoice(PENDING_VOICE_OUT_OF_RANGE);
      }
      combatTargetId = null;
      mainSight = null;
      attackSight = null;
      resetObstructionTracking();
      lastAttackLineTick = DnvTargetingBudget.NEVER_SCANNED;
      return null;
    }
    Boolean visible = acquisitionSight.visible(this, position().add(0, 3.58, 0), target);
    if (!Boolean.TRUE.equals(visible)) {
      if (Boolean.FALSE.equals(visible)) combatTargetId = null;
      return null;
    }
    return target;
  }

  protected boolean isCheapTargetValid(@Nullable Entity entity) {
    if (entity == null
        || entity == this
        || !entity.isAlive()
        || entity instanceof ItemEntity
        || entity instanceof Projectile
        || DnvTargetExclusions.isUnavailableCombatTarget(entity)
        || isSilentHeavenOwned(entity)
        || distanceToSqr(entity) > TARGET_RANGE * TARGET_RANGE) {
      return false;
    }
    if (!DnvCombatPolicyRuntime.bossRelationsAllow(this, entity)) {
      return false;
    }
    return true;
  }

  protected boolean isAcquisitionTarget(@Nullable Entity entity) {
    if (!isCheapTargetValid(entity)) {
      return false;
    }
    return DnvCombatPolicyRuntime.allowsBossNativeTarget(
        this,
        entity,
        entity instanceof LivingEntity || entity instanceof VehicleEntity);
  }

  protected boolean isTrackedTargetValid(@Nullable Entity entity) {
    return isCheapTargetValid(entity) && targetCandidates.isAuthorized(entity);
  }

  protected static boolean isSilentHeavenOwned(Entity entity) {
    return entity.getTags().contains(OWNED_ENTITY_TAG)
        || entity.getPersistentData().getBoolean(OWNED_ENTITY_TAG);
  }

  protected void updateAutonomousControls(@Nullable Entity target) {
    Vec3 destination;
    boolean repositionForLine =
        target != null && DnvObstructionPolicy.shouldReposition(obstructionRecoveryTicks());
    boolean movementLocked = isTransitioning() || isTeleporting()
        || DnvObstructionPolicy.shouldRetarget(obstructionRecoveryTicks());
    if (holdPosition || movementLocked) {
      destination = null;
    } else if (target != null) {
      destination = target.position();
      patrolDestination = null;
    } else {
      destination = updatePatrolDestination();
    }

    float destinationYaw = getYRot();
    if (destination != null) {
      Vec3 delta = destination.subtract(position());
      destinationYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    long gameTime = ((ServerLevel) level()).getGameTime();
    UUID targetId = target == null ? null : target.getUUID();
    Optional<UUID> forcedTarget = target != null
            && com.liucodex.doggiesnewvehicle.compat.DnvCombatCoordinator
                .isActiveAssignment(this, target)
        ? Optional.of(target.getUUID())
        : Optional.empty();
    Map<String, DnvAutonomyCore.DomainStatus> domains =
        coreDomains(target != null && !repositionForLine);
    boolean decisionInvalidated =
        !java.util.Objects.equals(autonomyDecisionTargetId, targetId)
            || !autonomyDecisionForcedTarget.equals(forcedTarget)
            || autonomyDecisionHold != holdPosition
            || autonomyDecisionMovementLocked != movementLocked
            || autonomyDecisionHasDestination != (destination != null)
            || !autonomyDecisionDomains.equals(domains);
    if (!java.util.Objects.equals(autonomyDecisionTargetId, targetId)) groundDrive.reset();
    DnvAutonomyCore.DecisionCycle decision = autonomyCore.lastDecision();
    if (DnvTargetingBudget.isDecisionDue(
        gameTime, nextAutonomyDecisionGameTime, decisionInvalidated)) {
      decision = autonomyCore.decide(
          new DnvAutonomyCore.PerceptionSnapshot(
            gameTime,
            holdPosition,
            movementLocked,
            forcedTarget,
            target == null ? List.of() : List.of(observeTarget(target, !repositionForLine)),
            destination == null
                ? Optional.empty()
                : Optional.of(new DnvAutonomyCore.DestinationObservation(destinationYaw)),
            getYRot(),
            DnvAutonomyCore.DomainOwner.AI,
            domains));
      autonomyDecisionTargetId = targetId;
      autonomyDecisionForcedTarget = forcedTarget;
      autonomyDecisionHold = holdPosition;
      autonomyDecisionMovementLocked = movementLocked;
      autonomyDecisionHasDestination = destination != null;
      autonomyDecisionDomains = Map.copyOf(domains);
      nextAutonomyDecisionGameTime = DnvTargetingBudget.nextDecisionGameTime(
          gameTime, getId(), DnvTargetingBudget.MIN_DECISION_INTERVAL, 131);
    }
    DnvAutonomyCore.ActionIntent intent = decision.movement();
    if (target != null && decision.targetVisible() && !repositionForLine) {
      Vec3 muzzle = mainGunMuzzle(true).add(mainGunMuzzle(false)).scale(0.5D);
      double minimum = com.liucodex.doggiesnewvehicle.core.DnvFiringRange.minimum(
          target.getBoundingBox().getCenter().y - muzzle.y, MAIN_VELOCITY, MAIN_GRAVITY,
          -10, 46, PRESSURE_MIN_RANGE, MAIN_MAX_RANGE);
      if (minimum > PRESSURE_MIN_RANGE) minimum = Math.min(MAIN_MAX_RANGE,
          minimum + muzzle.subtract(position()).horizontalDistance());
      intent = autonomyCore.decideMovement(new DnvAutonomyCore.SensorSnapshot(
          holdPosition, movementLocked, true, destination != null, false,
          horizontalDistance(target.position()), getYRot(), destinationYaw), minimum);
    }
    if (decision.targetVisible()) {
      combatTargetId = decision.targetId().orElseThrow();
    }
    var drive = groundDrive.command(
        new DnvGroundDrive.Input(intent, getYRot(), destinationYaw, getX(), getZ(),
            destination == null ? 0.0D : horizontalDistance(destination),
            onGround(), horizontalCollision, repositionForLine),
        new DnvGroundTerrainProbe(this));
    requestedThrottle = drive.throttle();
    requestedSteering = drive.steering();
    driveBrake = drive.brake() || movementLocked;
    if (intent.clearRoute()) {
      patrolDestination = null;
    }
    if (intent.hardStop() || movementLocked) {
      entityData.set(POWER, 0.0F);
      entityData.set(DELTA_ROT, 0.0F);
      Vec3 motion = getDeltaMovement();
      setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
    }
  }

  protected boolean autonomyMayFire(String domain) {
    return autonomyCore.lastDecision().stationIntents().getOrDefault(
        domain,
        new DnvAutonomyCore.DomainIntent(
            DnvAutonomyCore.DomainCommand.HOLD,
            DnvAutonomyCore.DecisionReason.DOMAIN_NOT_AUTHORIZED))
        .command() == DnvAutonomyCore.DomainCommand.FIRE;
  }

  private void clearAutonomyDecision() {
    groundDrive.reset();
    driveBrake = true;
    long gameTime = level() instanceof ServerLevel serverLevel
        ? serverLevel.getGameTime()
        : 0L;
    autonomyCore.clear(gameTime);
    nextAutonomyDecisionGameTime = 0L;
    autonomyDecisionTargetId = null;
    autonomyDecisionForcedTarget = Optional.empty();
    autonomyDecisionHold = false;
    autonomyDecisionMovementLocked = false;
    autonomyDecisionHasDestination = false;
    autonomyDecisionDomains = Map.of();
  }

  private DnvAutonomyCore.TargetObservation observeTarget(Entity target, boolean lineOfSight) {
    Vec3 delta = target.position().subtract(position());
    double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
    float bearing = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
    double threat = target instanceof LivingEntity living
        ? Math.max(1.0D, living.getHealth())
        : 1.0D;
    return new DnvAutonomyCore.TargetObservation(
        target.getUUID(), target.getX(), target.getY(), target.getZ(), horizontal,
        bearing, threat, !target.isRemoved(), target.isAlive(), lineOfSight);
  }

  private Map<String, DnvAutonomyCore.DomainStatus> coreDomains(boolean lineOfSight) {
    Map<String, DnvAutonomyCore.DomainStatus> domains = new LinkedHashMap<>();
    domains.put(CORE_MAIN, aiDomain(true, lineOfSight));
    domains.put(CORE_AUTOCANNON_LEFT, aiDomain(autocannonLeft.ammo > 0, lineOfSight));
    domains.put(CORE_AUTOCANNON_RIGHT, aiDomain(autocannonRight.ammo > 0, lineOfSight));
    domains.put(CORE_MISSILE_LEFT, aiDomain(missilePodLeft.ammo > 0, lineOfSight));
    domains.put(CORE_MISSILE_RIGHT, aiDomain(missilePodRight.ammo > 0, lineOfSight));
    domains.put(CORE_VLS, aiDomain(vlsBattery.ammo > 0, lineOfSight));
    return domains;
  }

  private static DnvAutonomyCore.DomainStatus aiDomain(
      boolean available, boolean lineOfSight) {
    return new DnvAutonomyCore.DomainStatus(
        DnvAutonomyCore.DomainOwner.AI, available, lineOfSight);
  }

  protected void tickVoiceState() {
    if (generalVoiceCooldown > 0) generalVoiceCooldown--;
    if (attackVoiceCooldown > 0) attackVoiceCooldown--;
    if (damageVoiceCooldown > 0) damageVoiceCooldown--;
    if (targetAcquiredVoiceCooldown > 0) targetAcquiredVoiceCooldown--;

    boolean moving = Math.abs(requestedThrottle) > 0.05F;
    if (moving && !wasMoving) {
      queueVoice(PENDING_VOICE_MOVEMENT);
    } else if (!moving && pendingVoice == PENDING_VOICE_MOVEMENT) {
      pendingVoice = PENDING_VOICE_NONE;
    }
    wasMoving = moving;

    if (startupVoiceStage == 0) {
      playVoice(ModSounds.VOICE_SYSTEMS_ONLINE.get());
      startupVoiceStage = 1;
      startupVoiceDelay = STARTUP_READY_VOICE_DELAY;
      generalVoiceCooldown = STARTUP_READY_VOICE_DELAY;
      return;
    }
    if (startupVoiceStage == 1) {
      if (startupVoiceDelay > 0) startupVoiceDelay--;
      if (startupVoiceDelay <= 0) {
        playVoice(
            random.nextBoolean()
                ? ModSounds.VOICE_READY_FOR_BATTLE.get()
                : ModSounds.VOICE_I_AM_READY.get());
        startupVoiceStage = 2;
        generalVoiceCooldown = GENERAL_VOICE_COOLDOWN;
      }
      return;
    }
    if (generalVoiceCooldown > 0 || pendingVoice == PENDING_VOICE_NONE) {
      return;
    }

    int voice = pendingVoice;
    pendingVoice = PENDING_VOICE_NONE;
    if (voice == PENDING_VOICE_TARGET_ACQUIRED) {
      playTargetAcquiredVoice();
    } else if (voice == PENDING_VOICE_OUT_OF_RANGE) {
      playVoice(ModSounds.VOICE_ENEMY_OUT_OF_RANGE.get());
    } else if (voice == PENDING_VOICE_MOVEMENT && moving) {
      playMovementVoice();
    }
    generalVoiceCooldown = GENERAL_VOICE_COOLDOWN;
  }

  protected void queueVoice(int voice) {
    if (voice == PENDING_VOICE_MOVEMENT) {
      if (pendingVoice == PENDING_VOICE_NONE) {
        pendingVoice = voice;
      }
    } else {
      // The newest combat-state transition supersedes an older queued
      // acquisition/loss announcement while another line is playing.
      pendingVoice = voice;
    }
  }

  protected void queueTargetAcquiredVoice() {
    if (targetAcquiredVoiceCooldown > 0) {
      return;
    }
    targetAcquiredVoiceCooldown = TARGET_ACQUIRED_VOICE_COOLDOWN;
    queueVoice(PENDING_VOICE_TARGET_ACQUIRED);
  }

  protected void playTargetAcquiredVoice() {
    switch (random.nextInt(3)) {
      case 0 -> playVoice(ModSounds.VOICE_FOCUS_ON_TARGET.get());
      case 1 -> playVoice(ModSounds.VOICE_TARGET_ACQUIRED.get());
      default -> playVoice(ModSounds.VOICE_ENEMY_ENGAGED.get());
    }
  }

  protected void playMovementVoice() {
    switch (random.nextInt(3)) {
      case 0 -> playVoice(ModSounds.VOICE_MOVING.get());
      case 1 -> playVoice(ModSounds.VOICE_DESTINATION_SET.get());
      default -> playVoice(ModSounds.VOICE_I_MOVE.get());
    }
  }

  protected void tryPlayAttackVoice() {
    if (startupVoiceStage < 2 || generalVoiceCooldown > 0 || attackVoiceCooldown > 0) {
      return;
    }
    switch (random.nextInt(4)) {
      case 0 -> playVoice(ModSounds.VOICE_ATTACK.get());
      case 1 -> playVoice(ModSounds.VOICE_DESTROY.get());
      case 2 -> playVoice(ModSounds.VOICE_TERMINATE.get());
      default -> playVoice(ModSounds.VOICE_SET_TO_KILL.get());
    }
    attackVoiceCooldown = ATTACK_VOICE_COOLDOWN;
    generalVoiceCooldown = GENERAL_VOICE_COOLDOWN;
  }

  protected void tryPlayDamageVoice() {
    if (startupVoiceStage < 2 || generalVoiceCooldown > 0 || damageVoiceCooldown > 0) {
      return;
    }
    playVoice(
        random.nextBoolean()
            ? ModSounds.VOICE_RECEIVING_DAMAGE.get()
            : ModSounds.VOICE_UNDER_ATTACK.get());
    damageVoiceCooldown = DAMAGE_VOICE_COOLDOWN;
    generalVoiceCooldown = GENERAL_VOICE_COOLDOWN;
  }

}
