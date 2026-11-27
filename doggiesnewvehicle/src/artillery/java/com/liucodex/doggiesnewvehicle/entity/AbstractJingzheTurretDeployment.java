package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.data.vehicle.subdata.SeekInfo;
import com.atsuishio.superbwarfare.entity.living.TargetEntity;
import com.atsuishio.superbwarfare.entity.vehicle.ai.TowerAI;
import com.atsuishio.superbwarfare.tools.SeekTool;
import com.liucodex.doggiesnewvehicle.compat.DnvJingzheTurretExecution;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetExclusions;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
import com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore.WeaponReadiness;
import com.liucodex.doggiesnewvehicle.core.DnvTurretExecutionPort;
import com.liucodex.doggiesnewvehicle.core.DnvTurretTargetMemory;
import com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

abstract class AbstractJingzheTurretDeployment extends AbstractJingzheTurretState {
  private final DnvTurretTargetMemory[] targetMemories = createTargetMemories();
  protected int candidateProbeCursor;
  private DnvTurretTargetMemory.CommandObservation<Entity> targetCommand =
      new DnvTurretTargetMemory.CommandObservation<>(Optional.empty(), -1L, -1L, false,
          DnvTurretTargetMemory.CommandMode.NONE, "NO_ACTIVE_ASSIGNMENT");
  private long targetProfileRevision;
  private long autonomousObservationRevision;
  private List<Entity> activeCandidateView;
  private final DnvJingzheTurretExecution execution = new DnvJingzheTurretExecution(
      this, new DnvTurretExecutionPort.EffectHooks<Entity>() {
        @Override public boolean active() {
          return isTurretActivated() && isCombatDeployed() && hasOperationalPower();
        }
        @Override public DnvTurretExecutionPort.EffectState state(int module) {
          var ready = new WeaponReadiness(moduleCharge[module] == 1000,
              (long) tickCount - lastReleaseTick >= RELEASE_INTERVAL_TICKS,
              getFirstPassenger() == null && active(), true,
              moduleState[module] == MODULE_WINDUP && moduleTimer[module] >= WINDUP_TICKS
                  && !moduleEnergyReserved[module]);
          return new DnvTurretExecutionPort.EffectState(moduleTarget[module], moduleState[module],
              moduleTimer[module], moduleCharge[module], moduleEnergyReserved[module], lastReleaseTick, ready);
        }
        @Override public boolean targetAllowed(Entity target) {
          SeekInfo seek = data().compute().getSeekInfo();
          return seek != null && isAttackTargetValid(target, seek);
        }
        @Override public boolean targetStillValid(Entity target) {
          SeekInfo seek = data().compute().getSeekInfo();
          return seek != null && isCheapModuleTargetValid(target, seek);
        }
        @Override public boolean pathClear(Entity target) {
          return hasAttackPath(target,
              execution.targetCommand().commandMode() == DnvTurretTargetMemory.CommandMode.NONE);
        }
        @Override public boolean release(int module, Entity target) { return releaseAttack(module, target); }
      });

  protected void invalidateExecution() { execution.invalidate(); }
  protected void invalidateModuleExecution(int module) { execution.module(module).invalidate(); }
  protected boolean isPvpProtected(Entity target) { return execution.isPvpProtected(target); }

  public DnvTurretTargetMemory.Decision targetDecisionSnapshot(int module) {
    return targetMemories[checkedModule(module)].current();
  }

  protected AbstractJingzheTurretDeployment(
      EntityType<? extends JingzheTurretEntity> type, Level level) {
    super(type, level);
  }

  protected void tickCombatSystems() {
    activeCandidateView = null;
    if (!(level() instanceof ServerLevel)
        || getHealth() <= 0.0F
        || getFirstPassenger() != null
        || !isTurretActivated()
        || getDeployPhase() != PHASE_ACTIVE
        || !hasOperationalPower()) {
      invalidateTargetMemories(DnvTurretTargetMemory.InvalidationReason.ENTITY_INVALIDATED);
      invalidateExecution();
      return;
    }
    SeekInfo seekInfo = data().compute().getSeekInfo();
    if (seekInfo == null) {
      invalidateTargetMemories(DnvTurretTargetMemory.InvalidationReason.CORE_INACTIVE);
      invalidateExecution();
      return;
    }

    DnvTurretTargetMemory.Configuration context = execution.targetingContext();
    if (!context.coreActive()) {
      invalidateTargetMemories(DnvTurretTargetMemory.InvalidationReason.CORE_INACTIVE);
      invalidateExecution();
      return;
    }
    targetProfileRevision = context.profileRevision();
    targetCommand = execution.targetCommand();
    if (targetCommand.commandMode() == DnvTurretTargetMemory.CommandMode.NONE
        && needsTargetScan()) {
      refreshSharedCandidates(seekInfo);
    }
    try {
      startAffordableReloads();
      for (int module = 0; module < MODULE_COUNT; module++) {
        if (!isTargetingState(moduleState[module])
            && targetMemories[module].current().target().isPresent()) {
          invalidateModuleTargetMemory(
              module, DnvTurretTargetMemory.InvalidationReason.TARGET_UNAVAILABLE);
        }
        tickModule(module, seekInfo);
      }
    } finally {
      activeCandidateView = null;
    }
  }

  protected void tickDeployment() {
    if (getHealth() <= 0.0F) {
      // Damage state is intentionally a frozen pose with red blinking lamps.
      return;
    }

    int phase = getDeployPhase();
    if (!isTurretActivated()) {
      if (phase != PHASE_INACTIVE && !isRetracting(phase)) {
        beginRetractionFromCurrentPhase(phase);
      }
      if (isRetracting(getDeployPhase())) {
        tickRetraction();
      }
      return;
    }

    if (phase == PHASE_INACTIVE) {
      if (hasOperationalPower()) {
        level()
            .playSound(
                null,
                blockPosition(),
                DnvArtillerySounds.JINGZHE_ACTIVATE.get(),
                SoundSource.HOSTILE,
                1.0F,
                1.0F);
        setPhase(PHASE_KEY_DOWN);
      }
      return;
    }
    if (isRetracting(phase)) {
      // Finish a clean retract before accepting another activation cycle.
      tickRetraction();
      return;
    }
    if (!hasOperationalPower()) {
      // Power loss preserves the exact pose, charge and lock timers.
      return;
    }

    switch (phase) {
      case PHASE_KEY_DOWN -> {
        if (advancePhaseTick(KEY_TICKS)) {
          setPhase(PHASE_CORE_HOLD);
        }
      }
      case PHASE_CORE_HOLD -> {
        if (advancePhaseTick(CORE_HOLD_TICKS)) {
          setPhase(PHASE_OUTWARD);
        }
      }
      case PHASE_OUTWARD -> {
        if (advancePhaseTick(OUTWARD_TICKS)) {
          setPhase(PHASE_UPWARD);
        }
      }
      case PHASE_UPWARD -> {
        if (advancePhaseTick(UPWARD_TICKS)) {
          setPhase(PHASE_ACCEL);
        }
      }
      case PHASE_ACCEL -> {
        float progress = phaseProgress(ACCEL_TICKS);
        addOrbitAngle(FULL_ORBIT_SPEED_DEGREES * progress);
        if (advancePhaseTick(ACCEL_TICKS)) {
          setPhase(PHASE_ACTIVE);
        }
      }
      case PHASE_ACTIVE -> addOrbitAngle(FULL_ORBIT_SPEED_DEGREES);
      default -> {}
    }
  }

  protected void tickRetraction() {
    switch (getDeployPhase()) {
      case PHASE_DECEL -> {
        float progress = phaseProgress(DECEL_TICKS);
        addOrbitAngle(FULL_ORBIT_SPEED_DEGREES * (1.0F - progress));
        if (advancePhaseTick(DECEL_TICKS)) {
          setPhase(PHASE_DOWN);
        }
      }
      case PHASE_DOWN -> {
        if (advancePhaseTick(UPWARD_TICKS)) {
          setPhase(PHASE_INWARD);
        }
      }
      case PHASE_INWARD -> {
        if (advancePhaseTick(OUTWARD_TICKS)) {
          setPhase(PHASE_KEY_UP);
        }
      }
      case PHASE_KEY_UP -> {
        if (advancePhaseTick(KEY_TICKS)) {
          setPhase(PHASE_INACTIVE);
          clearTransientTargets();
        }
      }
      default -> setPhase(PHASE_DECEL);
    }
  }

  protected void beginRetractionFromCurrentPhase(int phase) {
    int currentTick = getEntityData().get(PHASE_TICK);
    switch (phase) {
      case PHASE_KEY_DOWN ->
          setPhaseAndTick(PHASE_KEY_UP, Mth.clamp(KEY_TICKS - currentTick, 0, KEY_TICKS));
      case PHASE_CORE_HOLD -> setPhase(PHASE_KEY_UP);
      case PHASE_OUTWARD ->
          setPhaseAndTick(PHASE_INWARD, Mth.clamp(OUTWARD_TICKS - currentTick, 0, OUTWARD_TICKS));
      case PHASE_UPWARD ->
          setPhaseAndTick(PHASE_DOWN, Mth.clamp(UPWARD_TICKS - currentTick, 0, UPWARD_TICKS));
      default -> setPhase(PHASE_DECEL);
    }
  }

  protected void tickModule(int module, SeekInfo seekInfo) {
    switch (moduleState[module]) {
      case MODULE_RELOADING -> {
        moduleTimer[module]++;
        moduleCharge[module] =
            Mth.clamp(Math.round(moduleTimer[module] * 1000.0F / RELOAD_TICKS), 0, 1000);
        if (moduleTimer[module] >= RELOAD_TICKS) {
          moduleState[module] = MODULE_READY;
          moduleTimer[module] = 0;
          moduleCharge[module] = 1000;
          moduleEnergyReserved[module] = false;
        }
        publishModule(module);
      }
      case MODULE_READY -> {
        if (tickCount - lastLockStartTick < RELEASE_INTERVAL_TICKS) {
          return;
        }
        Entity target = acquireTarget(module, seekInfo);
        if (target != null) {
          moduleTarget[module] = target.getStringUUID();
          initializeModuleValidation(module, target);
          moduleState[module] = MODULE_LOCKING;
          moduleTimer[module] = 0;
          lastLockStartTick = tickCount;
          playModuleLockSound(module);
          publishModule(module);
        }
      }
      case MODULE_LOCKING -> {
        Entity selected = acquireTarget(module, seekInfo);
        Entity target = resolveModuleTarget(module);
        if (selected == null || target == null
            || !selected.getUUID().equals(target.getUUID())) {
          resetModuleToReady(module);
          return;
        }
        if (!isModuleTargetValid(module, target, seekInfo)) {
          if (!modulePathDeferred[module]) resetModuleToReady(module);
          return;
        }
        moduleTimer[module]++;
        if (moduleTimer[module] >= LOCK_TICKS) {
          moduleState[module] = MODULE_WINDUP;
          moduleTimer[module] = 0;
        }
        publishModule(module);
      }
      case MODULE_WINDUP -> {
        Entity selected = acquireTarget(module, seekInfo);
        Entity target = resolveModuleTarget(module);
        if (selected == null || target == null
            || !selected.getUUID().equals(target.getUUID())) {
          resetModuleToReady(module);
          return;
        }
        if (!isModuleTargetValid(module, target, seekInfo)) {
          if (!modulePathDeferred[module]) resetModuleToReady(module);
          return;
        }
        if (moduleTimer[module] < WINDUP_TICKS) {
          moduleTimer[module]++;
          if ((moduleTimer[module] & 1) == 0) {
            spawnModuleWindupParticle(module, moduleTimer[module]);
          }
        }
        if (moduleTimer[module] >= WINDUP_TICKS
            && (long) tickCount - lastReleaseTick >= RELEASE_INTERVAL_TICKS) {
          DnvTurretExecutionPort port = execution.module(module);
          var frame = port.inspect(target.getUUID());
          if (port.applyAim(frame, new Point(0, -1, 0)).accepted()
              && port.tryFire(frame).accepted()) return;
        }
        publishModule(module);
      }
      case MODULE_FADING -> {
        moduleTimer[module]++;
        moduleCharge[module] =
            Mth.clamp(
                Math.round(1000.0F * (1.0F - moduleTimer[module] / (float) FADE_TICKS)), 0, 1000);
        if (moduleTimer[module] >= FADE_TICKS) {
          moduleState[module] = MODULE_EMPTY;
          moduleTimer[module] = 0;
          moduleCharge[module] = 0;
        }
        publishModule(module);
      }
      default -> {}
    }
  }

  protected void startAffordableReloads() {
    int firstModule = nextReloadIndex;
    for (int offset = 0; offset < MODULE_COUNT; offset++) {
      int module = (firstModule + offset) % MODULE_COUNT;
      if (moduleState[module] != MODULE_EMPTY || getEnergy() < RELOAD_ENERGY_COST) {
        continue;
      }
      consumeEnergy(RELOAD_ENERGY_COST);
      moduleState[module] = MODULE_RELOADING;
      moduleTimer[module] = 0;
      moduleCharge[module] = 0;
      moduleEnergyReserved[module] = true;
      nextReloadIndex = (module + 1) % MODULE_COUNT;
      publishModule(module);
    }
  }

  protected void refreshSharedCandidates(SeekInfo seekInfo) {
    Vec3 origin = position().add(0.0D, TARGET_ORIGIN_HEIGHT, 0.0D);
    if (targetCandidates.refreshIfDue(this,
        seekInfo.getMaxSeekRange() + TARGET_ORIGIN_HEIGHT,
        target -> isCandidate(target, origin, seekInfo))) {
      autonomousObservationRevision++;
    }
  }

  protected boolean needsTargetScan() {
    if (tickCount - lastLockStartTick < RELEASE_INTERVAL_TICKS) {
      return false;
    }
    for (int module = 0; module < MODULE_COUNT; module++) {
      if (moduleState[module] == MODULE_READY) {
        return true;
      }
    }
    return false;
  }

  protected Entity acquireTarget(int module, SeekInfo seekInfo) {
    checkedModule(module);
    long now = level().getGameTime();
    Entity commanded = targetCommand.target()
        .filter(target -> isAttackTargetValid(target, seekInfo))
        .filter(target -> moduleState[module] != MODULE_READY
            || Boolean.TRUE.equals(exploratoryPathClear(module, target)))
        .orElse(null);
    Entity autonomous = targetCommand.commandMode() == DnvTurretTargetMemory.CommandMode.NONE
        ? selectAutonomousTarget(module, seekInfo) : null;
    Optional<DnvTurretTargetMemory.Candidate> commandCandidate = commanded == null
        ? Optional.empty()
        : Optional.of(DnvTurretTargetMemory.Candidate.command(
            commanded.getUUID(), targetCommand.explicitDesignation(), targetCommand.revision(),
            now, targetCommand.validUntilTick(), targetPosition(commanded)));
    Optional<DnvTurretTargetMemory.Candidate> autonomousCandidate = autonomous == null
        ? Optional.empty()
        : Optional.of(DnvTurretTargetMemory.Candidate.autonomous(
            autonomous.getUUID(), autonomousObservationRevision, now,
            later(now, TARGET_SCAN_INTERVAL), targetPosition(autonomous)));
    DnvTurretTargetMemory.Decision decision = targetMemories[module].decide(
        new DnvTurretTargetMemory.Input(now, targetProfileRevision, true,
            targetCommand.commandMode(), commandCandidate, autonomousCandidate));
    if (!(level() instanceof ServerLevel serverLevel)) {
      return null;
    }
    Entity selected = decision.target().map(DnvTurretTargetMemory.TargetSnapshot::targetId)
        .map(serverLevel::getEntity).orElse(null);
    if (selected == null && decision.target().isPresent()) {
      invalidateModuleTargetMemory(
          module, DnvTurretTargetMemory.InvalidationReason.TARGET_UNAVAILABLE);
    }
    return selected;
  }

  private Entity selectAutonomousTarget(int module, SeekInfo seekInfo) {
    if (moduleState[module] == MODULE_LOCKING || moduleState[module] == MODULE_WINDUP) {
      Entity current = resolveModuleTarget(module);
      if (isAttackTargetValid(current, seekInfo)) {
        return current;
      }
    }
    Set<String> alreadyAssigned = new HashSet<>();
    for (int index = 0; index < MODULE_COUNT; index++) {
      if (index != module
          && (moduleState[index] == MODULE_LOCKING || moduleState[index] == MODULE_WINDUP)) {
        alreadyAssigned.add(moduleTarget[index]);
      }
    }

    List<Entity> candidates = candidateView();
    Entity fallback = null;
    int start = candidateProbeCursor;
    for (int offset = 0; offset < candidates.size(); offset++) {
      int index = (start + offset) % candidates.size();
      Entity target = candidates.get(index);
      if (!isAttackTargetValid(target, seekInfo)) {
        continue;
      }
      Boolean clear = exploratoryPathClear(module, target);
      if (clear == null) {
        candidateProbeCursor = index;
        break;
      }
      candidateProbeCursor = (index + 1) % candidates.size();
      if (!clear) continue;
      if (fallback == null) {
        fallback = target;
      }
      if (!alreadyAssigned.contains(target.getStringUUID())) {
        return target;
      }
    }
    return fallback;
  }

  protected List<Entity> candidateView() {
    if (activeCandidateView == null) activeCandidateView = resolveCandidateView();
    return activeCandidateView;
  }

  protected List<Entity> resolveCandidateView() {
    return targetCandidates.candidates(this);
  }

  protected void invalidateModuleTargetMemory(
      int module, DnvTurretTargetMemory.InvalidationReason reason) {
    long now = level() == null ? 0L : Math.max(0L, level().getGameTime());
    targetMemories[checkedModule(module)].invalidate(now, reason);
  }

  protected void invalidateTargetMemories(DnvTurretTargetMemory.InvalidationReason reason) {
    for (int module = 0; module < MODULE_COUNT; module++) {
      invalidateModuleTargetMemory(module, reason);
    }
  }

  private static boolean isTargetingState(int state) {
    return state == MODULE_READY || state == MODULE_LOCKING || state == MODULE_WINDUP;
  }

  private static int checkedModule(int module) {
    if (module < 0 || module >= MODULE_COUNT) {
      throw new IndexOutOfBoundsException("module " + module);
    }
    return module;
  }

  private static DnvTurretTargetMemory[] createTargetMemories() {
    DnvTurretTargetMemory[] result = new DnvTurretTargetMemory[MODULE_COUNT];
    for (int module = 0; module < MODULE_COUNT; module++) {
      result[module] = new DnvTurretTargetMemory();
    }
    return result;
  }

  private static DnvTurretTargetMemory.Position targetPosition(Entity target) {
    Vec3 position = target.getBoundingBox().getCenter();
    return new DnvTurretTargetMemory.Position(position.x, position.y, position.z);
  }

  private static long later(long now, int ticks) {
    return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks;
  }

  protected boolean isCandidate(Entity target, Vec3 origin, SeekInfo seekInfo) {
    if (target == this
        || DnvTargetExclusions.isUnavailableCombatTarget(target)
        || target instanceof Projectile
        || target instanceof TargetEntity
        || SeekTool.IS_INVULNERABLE.test(target)
        || SeekTool.IN_BLACKLIST.test(target)) {
      return false;
    }
    Entity owner = getOwner();
    if (target == owner || isAlliedTo(target) || owner != null && owner.isAlliedTo(target)) {
      return false;
    }
    double distanceSqr = target.distanceToSqr(this);
    if (distanceSqr < seekInfo.getMinSeekRange() * seekInfo.getMinSeekRange()
        || distanceSqr > seekInfo.getMaxSeekRange() * seekInfo.getMaxSeekRange()) {
      return false;
    }
    boolean legacyHostile =
        target instanceof LivingEntity living
            && living instanceof Enemy
            && living.getHealth() > 0.0F;
    // Discovery stays cheap; autonomous visibility is checked by the budgeted selector.
    return execution.allowsTargetRelation(
        target,
        legacyHostile || TowerAI.TeamResolver.isHostile(this, target));
  }

  protected boolean isAttackTargetValid(Entity target, SeekInfo seekInfo) {
    Vec3 origin = position().add(0.0D, TARGET_ORIGIN_HEIGHT, 0.0D);
    return target != null && isCandidate(target, origin, seekInfo);
  }

  protected boolean isModuleTargetValid(int module, Entity target, SeekInfo seekInfo) {
    modulePathDeferred[module] = false;
    if (target == null || !isCheapModuleTargetValid(target, seekInfo)) {
      return false;
    }
    String targetId = target.getStringUUID();
    if (!targetId.equals(moduleValidationTarget[module])) {
      moduleValidationTarget[module] = targetId;
      nextModuleRelationCheckTick[module] = 0;
      moduleSight[module] = null;
    }
    if (tickCount >= nextModuleRelationCheckTick[module]) {
      boolean nativeHostile = target instanceof LivingEntity living
          && living instanceof Enemy
          && living.getHealth() > 0.0F;
      cachedModuleRelationAllowed[module] = execution.allowsTargetRelation(
          target,
          nativeHostile || TowerAI.TeamResolver.isHostile(this, target));
      nextModuleRelationCheckTick[module] =
          DnvTargetingBudget.nextCheckTick(
              tickCount, getId(), DnvTargetingBudget.RELATION_RECHECK_INTERVAL, 10 + module);
    }
    if (!cachedModuleRelationAllowed[module]) {
      return false;
    }
    Boolean clear = exploratoryPathClear(module, target);
    if (clear == null) {
      modulePathDeferred[module] = true;
      return false;
    }
    return clear;
  }

  protected boolean isCheapModuleTargetValid(Entity target, SeekInfo seekInfo) {
    if (target == this
        || DnvTargetExclusions.isUnavailableCombatTarget(target)
        || target instanceof Projectile
        || target instanceof TargetEntity
        || SeekTool.IS_INVULNERABLE.test(target)
        || SeekTool.IN_BLACKLIST.test(target)) {
      return false;
    }
    if (isPvpProtected(target)) return false;
    Entity owner = getOwner();
    if (target == owner || isAlliedTo(target) || owner != null && owner.isAlliedTo(target)) {
      return false;
    }
    double distanceSqr = target.distanceToSqr(this);
    return distanceSqr >= seekInfo.getMinSeekRange() * seekInfo.getMinSeekRange()
        && distanceSqr <= seekInfo.getMaxSeekRange() * seekInfo.getMaxSeekRange();
  }

  protected void initializeModuleValidation(int module, Entity target) {
    moduleValidationTarget[module] = target.getStringUUID();
    cachedModuleRelationAllowed[module] = true;
    modulePathDeferred[module] = false;
    nextModuleRelationCheckTick[module] =
        DnvTargetingBudget.nextCheckTick(
            tickCount, getId(), DnvTargetingBudget.RELATION_RECHECK_INTERVAL, 10 + module);
  }

  protected Boolean exploratoryPathClear(int module, Entity target) {
    if (target == null || !(level() instanceof ServerLevel serverLevel)) return false;
    AABB bounds = target.getBoundingBox();
    Vec3 origin = position().add(0.0D, TARGET_ORIGIN_HEIGHT, 0.0D);
    boolean autonomous = targetCommand.commandMode() == DnvTurretTargetMemory.CommandMode.NONE;
    long sightMode = autonomous ? 1L : 0L;
    var sight = moduleSight[module];
    if (sight != null && sight.reusable(target.getUUID(), origin, bounds,
        target.getEyeY(), sightMode, tickCount)) return sight.clear();
    if (!DnvTargetingBudget.admitGeometryCheck(serverLevel, moduleGeometryOwner(module))) return null;
    boolean clear = hasAttackPath(target, autonomous);
    moduleSight[module] = new com.liucodex.doggiesnewvehicle.compat.DnvSightObservation(
        target.getUUID(), origin, bounds, target.getEyeY(), sightMode, tickCount,
        DnvTargetingBudget.nextCheckTick(tickCount, getId(),
            DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL, 20 + module), clear);
    return clear;
  }

  protected boolean hasAttackPath(Entity target, boolean requireVisibility) {
    // Jingzhe drops above the target; both autonomous and commanded fire ignore intervening walls.
    return hasVerticalDropPath(target);
  }

  public boolean hasVerticalDropPath(Entity target) {
    if (target == null || !target.isAlive() || target.level() != level()) {
      return false;
    }
    AABB bounds = target.getBoundingBox();
    Vec3 impact = new Vec3(bounds.getCenter().x, bounds.maxY + 0.04D, bounds.getCenter().z);
    double spawnY = Math.min(level().getMaxBuildHeight() - 0.01D, impact.y + DROP_SPAWN_HEIGHT);
    if (spawnY <= impact.y + 0.01D) {
      return false;
    }
    Vec3 spawn = new Vec3(impact.x, spawnY, impact.z);
    // A three-block clip must not load terrain as a side effect of autonomous fire.
    if (!level().hasChunkAt(net.minecraft.core.BlockPos.containing(impact))
        || !level().hasChunkAt(net.minecraft.core.BlockPos.containing(spawn))) return false;
    return level()
            .clip(
                new ClipContext(
                    impact, spawn, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
            .getType()
        == HitResult.Type.MISS;
  }

}
