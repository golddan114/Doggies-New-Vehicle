package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.core.DnvControlAuthority;
import com.liucodex.doggiesnewvehicle.core.DnvPlatformWeaponAccess;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore;
import com.liucodex.doggiesnewvehicle.core.DnvEntityDesignationSession;
import com.liucodex.doggiesnewvehicle.core.DnvPhysicalAimChannelAuthority;
import com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore;
import com.liucodex.doggiesnewvehicle.core.DnvSbwWeaponDiagnostics;
import com.liucodex.doggiesnewvehicle.core.DnvWeaponFireGate;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewController;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewRuntime;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Per-vehicle station decision owner; bounded candidates, independent poses and no fake passengers. */
public final class DnvSbwCrewAutonomy {
  static final double TARGET_RANGE = DnvCombatPolicyRuntime.CREW_TARGET_RANGE;
  static final int TARGET_REJECT_TICKS = 40;
  static final double MIN_ALIGNMENT_DOT = Math.cos(Math.toRadians(5.0D));
  static final int MAX_FIRE_AUDITS = 64;

  private final VehicleEntity vehicle;
  private final DnvAutonomyCore autonomyCore = new DnvAutonomyCore(32.0D, 80.0D);
  private final DnvSbwStationEngagementAdapter stationEngagement;
  private final DnvSbwAimChannelRuntime aimChannels;
  private final DnvSbwWeaponFeedback weaponFeedback = new DnvSbwWeaponFeedback();
  private final DnvSbwWeaponDiagnostics diagnostics = new DnvSbwWeaponDiagnostics();
  private final DnvSbwCrewDiagnosticProjector diagnosticProjector;
  private final DnvTargetCandidateCache candidates =
      new DnvTargetCandidateCache(DnvTargetingBudget.MIN_FULL_SCAN_INTERVAL);
  private final Map<String, DnvSbwStationRuntime> stationStates = new LinkedHashMap<>();
  private final Map<UUID, FireAudit> fireAudits = new LinkedHashMap<>();
  private List<DnvSbwVehicleCrewAdapter.StationActuator> actuators = List.of();
  private UUID targetId;
  private Optional<UUID> localDesignation = Optional.empty();
  private long localOrderRevision = -1L;
  private UUID rejectedTargetId;
  private int rejectedUntilTick;
  private int nextRelationCheckTick;
  private int blindTicks;
  private long nextCoreDecisionGameTime;
  private UUID coreDecisionTargetId;
  private Optional<UUID> coreDecisionForcedTarget = Optional.empty();
  private Map<String, DnvAutonomyCore.DomainStatus> coreDecisionDomains = Map.of();

  public DnvSbwCrewAutonomy(VehicleEntity vehicle, DnvPlatformWeaponAccess platformAccess) {
    this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
    Objects.requireNonNull(platformAccess, "platformAccess");
    this.stationEngagement = new DnvSbwStationEngagementAdapter(vehicle);
    this.aimChannels = new DnvSbwAimChannelRuntime(vehicle, platformAccess);
    this.diagnosticProjector =
        new DnvSbwCrewDiagnosticProjector(vehicle, aimChannels, diagnostics);
  }

  public void configure(List<DnvSbwVehicleCrewAdapter.StationActuator> nextActuators) {
    List<DnvSbwVehicleCrewAdapter.StationActuator> immutable =
        List.copyOf(Objects.requireNonNull(nextActuators, "nextActuators"));
    if (immutable.equals(actuators)) {
      return;
    }
    clearActuatorState();
    invalidateCoreDecision();
    actuators = immutable;
    aimChannels.configure(actuators.stream()
        .map(actuator -> new DnvSbwAimChannelRuntime.ActuatorBinding(
            actuator.stationId(),
            actuator.seatIndex(),
            DnvSbwAimChannelRuntime.AimChannel.valueOf(actuator.aimChannel().name()),
            actuator.aimChannelId(),
            actuator.sharedWith()))
        .toList());
    for (DnvSbwVehicleCrewAdapter.StationActuator actuator : actuators) {
      stationStates.put(
          actuator.stationId(),
          new DnvSbwStationRuntime(actuator.aimChannel().ordinal(), actuator.weapons()));
    }
  }
  public void tick(DnvVehicleCrewRuntime runtime) {
    tick(runtime, null);
  }
  public void tick(
      DnvVehicleCrewRuntime runtime,
      DnvEntityDesignationSession.View orders) {
    long nextRevision = orders == null ? -1L : orders.orders().revision();
    if (localOrderRevision != nextRevision) {
      clearTargetAndActuators();
      candidates.clearPinnedExternal();
      localOrderRevision = nextRevision;
    }
    localDesignation = orders == null ? Optional.empty() : orders.targetId();
    Objects.requireNonNull(runtime, "runtime");
    if (!(vehicle.level() instanceof ServerLevel level)
        || vehicle.isRemoved()
        || vehicle.isWreck()
        || runtime.state() != DnvVehicleCrewRuntime.State.ACTIVE
        || actuators.isEmpty()) {
      clearTargetAndActuators();
      return;
    }
    long gameTime = level.getGameTime();
    int tick = vehicle.tickCount;
    Optional<UUID> configuredBy = runtime.configuredBy();
    Entity target = selectTarget(level, configuredBy, tick);
    DnvAutonomyCore.DecisionCycle coreDecision = decideCore(
        runtime, target, gameTime);
    Optional<UUID> designatedTarget = target != null
            && (localDesignation.filter(target.getUUID()::equals).isPresent()
                || DnvCombatCoordinator.isActiveAssignment(vehicle, target))
        ? Optional.of(target.getUUID())
        : Optional.empty();
    DnvStationEngagementCore.Decision stationPlan = stationEngagement.decide(
        runtime,
        actuators.stream()
            .map(actuator -> stationEngagement.input(
                actuator.stationId(), actuator.weapons(), actuator.geometry(), actuator.policy(),
                stationStates.get(actuator.stationId()), tick))
            .toList(),
        candidates.candidates(vehicle),
        designatedTarget,
        candidate -> isHostile(candidate, configuredBy),
        gameTime);
    boolean diagnosticFrame = diagnostics.beginFrame(
        gameTime, stationPlan.candidateCount(),
        runtime.revision().coreRevision(), runtime.revision().controlRevision());
    Map<String, Boolean> aimRequests = new LinkedHashMap<>();
    for (DnvSbwVehicleCrewAdapter.StationActuator actuator : actuators) {
      DnvVehicleCrewController.StationDecision decision =
          runtime.stationDecision(actuator.stationId(), gameTime);
      DnvStationEngagementCore.StationIntent intent =
          stationPlan.stationIntents().get(actuator.stationId());
      Entity stationTarget = intent == null
          ? null
          : intent.targetId().map(level::getEntity).orElse(null);
      aimRequests.put(
          actuator.stationId(),
          decision.owner() == DnvControlAuthority.ControlOwner.AI
              && decision.mayTrack()
              && stationTarget != null
              && isBroadCandidate(stationTarget, configuredBy));
    }
    aimChannels.beginTick(aimRequests, gameTime);
    boolean anyAiStation = false;
    boolean anyClearOrPendingLine = false;
    for (DnvSbwVehicleCrewAdapter.StationActuator actuator : actuators) {
      DnvVehicleCrewController.StationDecision decision =
          runtime.stationDecision(actuator.stationId(), gameTime);
      DnvAutonomyCore.DomainIntent coreIntent = coreDecision.stationIntents()
          .getOrDefault(
              actuator.stationId(),
              new DnvAutonomyCore.DomainIntent(
                  DnvAutonomyCore.DomainCommand.HOLD,
                  DnvAutonomyCore.DecisionReason.DOMAIN_NOT_AUTHORIZED));
      DnvStationEngagementCore.StationIntent stationIntent = stationPlan.stationIntents()
          .get(actuator.stationId());
      DnvSbwStationRuntime state = stationStates.get(actuator.stationId());
      DnvSbwAimChannelRuntime.ChannelSnapshot aimChannel = aimChannels.snapshot(
          actuator.stationId(), actuator.aimChannelId());
      Entity stationTarget = stationIntent == null
          ? null
          : stationIntent.targetId().map(level::getEntity).orElse(null);
      if (state == null
          || stationIntent == null
          || decision.owner() != DnvControlAuthority.ControlOwner.AI
          || !decision.mayTrack()
          || aimChannel.owner() != DnvPhysicalAimChannelAuthority.Owner.AI
          || !aimChannel.selectedStation()) {
        if (state != null) {
          state.resetTracking();
        }
        if (diagnosticFrame) {
          diagnosticProjector.recordInactive(
              actuator.stationId(), actuator.weapons(), state,
              stationIntent, aimChannel, stationTarget, tick,
              DnvWeaponFireGate.Reason.AUTHORITY_DENIED);
        }
        continue;
      }
      if (stationTarget == null || !isBroadCandidate(stationTarget, configuredBy)
          || !DnvCombatPolicyRuntime.crewExecutionAllowed(vehicle, stationTarget, configuredBy)) {
        state.resetTracking();
        if (diagnosticFrame) {
          diagnosticProjector.recordInactive(
              actuator.stationId(), actuator.weapons(), state,
              stationIntent, aimChannel, stationTarget, tick,
              DnvWeaponFireGate.Reason.TARGET_INVALID);
        }
        continue;
      }
      anyAiStation = true;
      anyClearOrPendingLine |= tickStation(
          actuator,
          state,
          decision,
          stationTarget,
          level,
          configuredBy,
          tick,
          gameTime,
          coreIntent,
          stationIntent,
          runtime,
          aimChannel,
          diagnosticFrame);
    }

    diagnostics.completeFrame();
    if (target == null || !anyAiStation || anyClearOrPendingLine) {
      blindTicks = 0;
      return;
    }
    blindTicks++;
    if (blindTicks >= DnvTargetingBudget.MIN_FULL_SCAN_INTERVAL) {
      rejectedTargetId = target.getUUID();
      rejectedUntilTick = saturatingAdd(tick, TARGET_REJECT_TICKS);
      targetId = null;
      blindTicks = 0;
      clearActuatorTargets();
    }
  }
  public void clear() {
    localDesignation = Optional.empty();
    localOrderRevision = -1L;
    clearTargetAndActuators();
    candidates.clear();
    rejectedTargetId = null;
    rejectedUntilTick = 0;
    actuators = List.of();
    stationStates.clear();
    fireAudits.clear();
    stationEngagement.clear(0L);
    weaponFeedback.clear();
    aimChannels.clear();
    diagnostics.clear();
  }
  List<FireAudit> fireAudits() {
    return List.copyOf(fireAudits.values());
  }
  public DnvSbwWeaponDiagnostics.Snapshot diagnostics() {
    return diagnostics.snapshot();
  }

  public void setDiagnosticsEnabled(boolean enabled) {
    diagnostics.setEnabled(enabled);
  }

  private Entity selectTarget(ServerLevel level, Optional<UUID> configuredBy, int tick) {
    boolean refreshed = candidates.refreshIfDue(
        vehicle, TARGET_RANGE, candidate -> isHostile(candidate, configuredBy));
    Entity assigned = DnvCombatCoordinator.assignedTarget(vehicle)
        .filter(candidate -> isBroadCandidate(candidate, configuredBy))
        .orElse(null);
    if (assigned != null) {
      candidates.pinExternal(assigned);
      targetId = assigned.getUUID();
      return assigned;
    }
    Entity designated = localDesignation.map(level::getEntity)
        .filter(candidate -> isHostile(candidate, configuredBy)).orElse(null);
    if (designated != null) {
      candidates.pinExternal(designated);
      targetId = designated.getUUID();
      return designated;
    }
    candidates.clearPinnedExternal();
    DnvCombatPolicyProvider.LossBehavior lossBehavior =
        DnvCombatCoordinator.lossBehavior(vehicle);
    Entity retained = targetId == null ? null : level.getEntity(targetId);
    if (lossBehavior == DnvCombatPolicyProvider.LossBehavior.DISABLE) {
      targetId = null;
      return null;
    }
    if (lossBehavior == DnvCombatPolicyProvider.LossBehavior.HOLD_LAST_TARGET) {
      return retained != null && isBroadCandidate(retained, configuredBy) ? retained : null;
    }
    Entity current = retained;
    boolean currentUnavailable = current == null
        || !candidates.isAuthorized(current)
        || !isBroadCandidate(current, configuredBy);
    if (!refreshed && !currentUnavailable && tick < nextRelationCheckTick) {
      return current;
    }

    Entity selected = candidates.select(
        vehicle,
        currentUnavailable ? null : current,
        candidate -> isHostile(candidate, configuredBy),
        candidate -> vehicle.distanceToSqr(candidate),
        false);
    targetId = selected == null ? null : selected.getUUID();
    nextRelationCheckTick = DnvTargetingBudget.nextCheckTick(
        tick,
        vehicle.getId(),
        DnvTargetingBudget.RELATION_RECHECK_INTERVAL,
        71);
    if (rejectedTargetId != null && tick >= rejectedUntilTick) {
      rejectedTargetId = null;
    }
    return selected;
  }

  private boolean isBroadCandidate(Entity target, Optional<UUID> configuredBy) {
    return DnvCombatPolicyRuntime.crewCandidate(vehicle, target, configuredBy)
        && !(rejectedTargetId != null && rejectedTargetId.equals(target.getUUID())
            && vehicle.tickCount < rejectedUntilTick);
  }

  private boolean isHostile(Entity target, Optional<UUID> configuredBy) {
    return isBroadCandidate(target, configuredBy)
        && DnvCombatPolicyRuntime.crewHostile(vehicle, target, configuredBy);
  }

  private boolean tickStation(
      DnvSbwVehicleCrewAdapter.StationActuator actuator,
      DnvSbwStationRuntime state,
      DnvVehicleCrewController.StationDecision decision,
      Entity target,
      ServerLevel level,
      Optional<UUID> configuredBy,
      int tick,
      long gameTime,
      DnvAutonomyCore.DomainIntent coreIntent,
      DnvStationEngagementCore.StationIntent stationIntent,
      DnvVehicleCrewRuntime runtime,
      DnvSbwAimChannelRuntime.ChannelSnapshot aimChannel,
      boolean diagnosticFrame) {
    if (!DnvStationEngagementCore.withinExecutionEnvelope(
        vehicle.getX(), vehicle.getY(), vehicle.getZ(), vehicle.getYRot(),
        actuator.geometry(), actuator.policy(), target.getX(), target.getY(), target.getZ())) {
      state.resetTracking();
      if (diagnosticFrame) {
        diagnosticProjector.recordInactive(
            actuator.stationId(), actuator.weapons(), state,
            stationIntent, aimChannel, target, tick, DnvWeaponFireGate.Reason.POLICY_HOLD);
      }
      return false;
    }
    List<DnvSbwStationRuntime.WeaponFrame> frames =
        new ArrayList<>(actuator.weapons().size());
    List<Boolean> ready = new ArrayList<>(actuator.weapons().size());
    List<Boolean> trackable = new ArrayList<>(actuator.weapons().size());
    List<DnvWeaponFireGate.Decision> gates = new ArrayList<>(actuator.weapons().size());
    boolean anyClearLine = false;
    boolean anyDeferredLine = false;
    for (int index = 0; index < actuator.weapons().size(); index++) {
      String weaponName = actuator.weapons().get(index);
      DnvSbwStationRuntime.WeaponState weaponState = state.weapon(weaponName);
      GunData gunData;
      Vec3 muzzle;
      try {
        gunData = vehicle.getGunData(weaponName);
        muzzle = gunData == null ? null : vehicle.getShootPos(weaponName, 1.0F);
      } catch (RuntimeException | LinkageError exception) {
        gunData = null;
        muzzle = null;
      }
      if (weaponState == null || gunData == null || muzzle == null) {
        if (weaponState != null) {
          weaponState.resetTracking();
        }
        frames.add(null);
        ready.add(false);
        trackable.add(false);
        gates.add(new DnvWeaponFireGate.Decision(
            false, DnvWeaponFireGate.Reason.WEAPON_UNAVAILABLE,
            DnvAutonomyCore.ExecutionOutcome.UNAVAILABLE));
        continue;
      }
      if (!weaponState.refreshLineOfSight(
          level,
          vehicle,
          muzzle,
          target,
          candidate -> isProtectedObstruction(candidate, target, configuredBy),
          tick,
          DnvTargetingBudget.nextCheckTick(
              tick,
              vehicle.getId(),
              DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL,
              state.channelOrder() * DnvSbwVehicleCrewAdapter.MAX_WEAPONS_PER_STATION
                  + index
                  + 83),
          actuator.stationId(), weaponName)) {
        anyDeferredLine |= weaponState.geometryPendingWithoutBlockedObservation(target.getUUID());
        weaponState.resetAlignment();
        frames.add(null);
        ready.add(false);
        trackable.add(false);
        gates.add(new DnvWeaponFireGate.Decision(
            false, DnvWeaponFireGate.Reason.FIRING_CORRIDOR_BLOCKED,
            DnvAutonomyCore.ExecutionOutcome.BLOCKED));
        continue;
      }
      anyClearLine = true;
      Vec3 aimPoint = weaponState.currentAimPoint(target);
      Vec3 desired = aimPoint.subtract(muzzle);
      if (desired.lengthSqr() <= 1.0E-8D) {
        weaponState.resetAlignment();
        frames.add(null);
        ready.add(false);
        trackable.add(false);
        gates.add(new DnvWeaponFireGate.Decision(
            false, DnvWeaponFireGate.Reason.TARGET_INVALID,
            DnvAutonomyCore.ExecutionOutcome.TARGET_INVALID));
        continue;
      }
      DnvSbwStationRuntime.WeaponFrame frame = new DnvSbwStationRuntime.WeaponFrame(
          weaponName, gunData, aimPoint, desired, weaponState);
      frames.add(frame);
      trackable.add(true);
      ready.add(false);
      gates.add(new DnvWeaponFireGate.Decision(
          false,
          DnvWeaponFireGate.Reason.AUTHORITY_DENIED,
          DnvAutonomyCore.ExecutionOutcome.AUTHORITY_DENIED));
    }

    int aimWeaponIndex = selectReadyWeapon(trackable, state.nextWeaponIndex());
    if (aimWeaponIndex < 0) {
      if (diagnosticFrame) {
        diagnosticProjector.recordStation(
            actuator.stationId(), actuator.weapons(), actuator.aimChannelId(),
            state, stationIntent, target, frames, gates, tick);
      }
      weaponFeedback.report(
          autonomyCore, gameTime, actuator.stationId(), actuator.weapons(), gates, target.getUUID());
      return anyClearLine || anyDeferredLine;
    }
    DnvSbwStationRuntime.WeaponFrame aimFrame = frames.get(aimWeaponIndex);
    DnvSbwAimChannelRuntime.ApplyResult aimResult = aimChannels.apply(
        actuator.stationId(), actuator.aimChannelId(), aimFrame.weaponName(), aimFrame.desired(), target.getUUID(),
        decision.revision(), runtime.revision(), gameTime);
    if (!aimResult.applied()) {
      for (int index = 0; index < frames.size(); index++) {
        DnvSbwStationRuntime.WeaponFrame frame = frames.get(index);
        if (frame != null) {
          frame.state().resetAlignment();
          gates.set(index, new DnvWeaponFireGate.Decision(
              false,
              DnvWeaponFireGate.Reason.AUTHORITY_DENIED,
              DnvAutonomyCore.ExecutionOutcome.AUTHORITY_DENIED));
        }
      }
      if (diagnosticFrame) {
        diagnosticProjector.recordStation(
            actuator.stationId(), actuator.weapons(), actuator.aimChannelId(),
            state, stationIntent, target, frames, gates, tick);
      }
      weaponFeedback.report(
          autonomyCore, gameTime, actuator.stationId(), actuator.weapons(), gates, target.getUUID());
      return anyClearLine || anyDeferredLine;
    }

    for (int index = 0; index < frames.size(); index++) {
      DnvSbwStationRuntime.WeaponFrame frame = frames.get(index);
      if (frame == null) {
        continue;
      }
      DnvSbwStationRuntime.WeaponState weaponState = frame.state();
      GunData gunData = frame.gunData();
      boolean crewCoreInstalled = runtime.configuredCoreId().isPresent();
      boolean canShoot;
      try {
        weaponState.updateAlignment(isAligned(vehicle.getShootVec(frame.weaponName(), 1.0F), frame.desired()));
        canShoot = gunData.canShoot(vehicle.getAmmoSupplier());
        if (canShoot && weaponState.cooldownReady(tick) && weaponState.alignedForFire()
            && stationIntent.command() == DnvAutonomyCore.DomainCommand.FIRE
            && coreIntent.command() == DnvAutonomyCore.DomainCommand.FIRE
            && crewCoreInstalled && decision.mayFire()) {
          weaponState.revalidateForFire(level, vehicle,
              vehicle.getShootPos(frame.weaponName(), 1.0F), target, frame.aimPoint(),
              candidate -> isProtectedObstruction(candidate, target, configuredBy), tick);
        }
      } catch (RuntimeException | LinkageError exception) {
        weaponState.resetAlignment();
        gates.set(index, new DnvWeaponFireGate.Decision(
            false,
            DnvWeaponFireGate.Reason.WEAPON_UNAVAILABLE,
            DnvAutonomyCore.ExecutionOutcome.UNAVAILABLE));
        ready.set(index, false);
        continue;
      }
      boolean localReady = coreIntent.command() == DnvAutonomyCore.DomainCommand.FIRE
          && stationIntent.command() == DnvAutonomyCore.DomainCommand.FIRE
          && crewCoreInstalled
          && decision.mayFire() && weaponState.fireCorridorClear()
          && weaponState.alignedForFire() && weaponState.cooldownReady(tick)
          && canShoot;
      boolean externalAllowed = !localReady || DnvCombatCoordinator.fireAuthorized(
          vehicle, target, actuator.stationId(), frame.weaponName(), gameTime);
      DnvWeaponFireGate.Decision gate = DnvWeaponFireGate.evaluate(
          new DnvWeaponFireGate.Request(
              coreIntent.command() == DnvAutonomyCore.DomainCommand.FIRE,
              stationIntent.command() == DnvAutonomyCore.DomainCommand.FIRE,
              crewCoreInstalled,
              decision.mayFire(), externalAllowed, weaponState.fireCorridorClear(),
              weaponState.alignedForFire(), weaponState.cooldownReady(tick),
              canShoot));
      gates.set(index, gate);
      ready.set(index, gate.allowed());
    }

    if (diagnosticFrame) {
      diagnosticProjector.recordStation(
          actuator.stationId(), actuator.weapons(), actuator.aimChannelId(),
          state, stationIntent, target, frames, gates, tick);
    }

    int fireWeaponIndex = selectReadyWeapon(ready, state.nextWeaponIndex());
    if (fireWeaponIndex < 0) {
      weaponFeedback.report(
          autonomyCore, gameTime, actuator.stationId(), actuator.weapons(), gates, target.getUUID());
      return anyClearLine || anyDeferredLine;
    }

    DnvSbwStationRuntime.WeaponFrame fireFrame = frames.get(fireWeaponIndex);
    int fireIndex = DnvSbwWeaponScheduler.currentFireIndex(fireFrame.gunData());
    UUID coreId = runtime.configuredCoreId().orElseThrow();
    long coreRevision = runtime.revision().coreRevision();
    UUID operationId = fireOperationId(coreId, coreRevision, actuator.stationId(), fireFrame.weaponName(), target.getUUID(), gameTime, fireIndex);
    if (!rememberFireAttempt(fireAudits, new FireAudit(operationId, coreId, coreRevision,
        actuator.stationId(), fireFrame.weaponName(), target.getUUID(), gameTime, fireIndex))) {
      diagnostics.recordFireResult(
          actuator.stationId(), fireFrame.weaponName(), "DUPLICATE_SUPPRESSED", gameTime);
      return anyClearLine || anyDeferredLine;
    }
    try {
      vehicle.vehicleShoot((LivingEntity) null, fireFrame.weaponName(), target.getUUID(), fireFrame.aimPoint());
    } catch (RuntimeException | LinkageError exception) {
      diagnostics.recordFireResult(
          actuator.stationId(), fireFrame.weaponName(), "SHOOT_FAILED", gameTime);
      autonomyCore.acceptFeedback(new DnvAutonomyCore.ExecutionFeedback(
          gameTime,
          actuator.stationId(),
          Optional.of(target.getUUID()),
          DnvAutonomyCore.ExecutionOutcome.UNAVAILABLE,
          "weapon_fire_failed"));
      return anyClearLine || anyDeferredLine;
    }
    autonomyCore.acceptFeedback(new DnvAutonomyCore.ExecutionFeedback(gameTime, actuator.stationId(),
        Optional.of(target.getUUID()), DnvAutonomyCore.ExecutionOutcome.REQUESTED, "weapon_fire_requested"));
    DnvVehicleAutonomousFireNetwork.send(vehicle, fireFrame.weaponName(), fireIndex);
    fireFrame.state().onFired(tick, vehicle.vehicleWeaponRpm(fireFrame.weaponName()));
    diagnostics.recordFireResult(
        actuator.stationId(), fireFrame.weaponName(), "FIRE_REQUESTED", gameTime);
    state.advanceAfter(fireWeaponIndex);
    weaponFeedback.fired(actuator.stationId());
    return anyClearLine || anyDeferredLine;
  }

  private DnvAutonomyCore.DecisionCycle decideCore(
      DnvVehicleCrewRuntime runtime,
      Entity target,
      long gameTime) {
    Map<String, DnvAutonomyCore.DomainStatus> stations = new LinkedHashMap<>();
    for (DnvSbwVehicleCrewAdapter.StationActuator actuator : actuators) {
      DnvVehicleCrewController.StationDecision decision =
          runtime.stationDecision(actuator.stationId(), gameTime);
      stations.put(
          actuator.stationId(),
          new DnvAutonomyCore.DomainStatus(
              DnvSbwStationEngagementAdapter.owner(decision.owner()),
              decision.mayTrack() && !actuator.weapons().isEmpty(),
              true));
    }
    List<DnvAutonomyCore.TargetObservation> observations = target == null
        ? List.of()
        : List.of(stationEngagement.observeCoreTarget(target));
    Optional<UUID> forcedTarget = target != null
            && (localDesignation.filter(target.getUUID()::equals).isPresent()
                || DnvCombatCoordinator.isActiveAssignment(vehicle, target))
        ? Optional.of(target.getUUID())
        : Optional.empty();
    UUID currentTargetId = target == null ? null : target.getUUID();
    boolean decisionInvalidated =
        !Objects.equals(coreDecisionTargetId, currentTargetId)
            || !coreDecisionForcedTarget.equals(forcedTarget)
            || !coreDecisionDomains.equals(stations);
    if (!DnvTargetingBudget.isDecisionDue(
        gameTime, nextCoreDecisionGameTime, decisionInvalidated)) {
      return autonomyCore.lastDecision();
    }
    DnvAutonomyCore.DecisionCycle decision = autonomyCore.decide(
        new DnvAutonomyCore.PerceptionSnapshot(
        gameTime,
        true,
        false,
        forcedTarget,
        observations,
        Optional.empty(),
        vehicle.getYRot(),
        DnvAutonomyCore.DomainOwner.NONE,
        stations));
    coreDecisionTargetId = currentTargetId;
    coreDecisionForcedTarget = forcedTarget;
    coreDecisionDomains = Map.copyOf(stations);
    nextCoreDecisionGameTime = DnvTargetingBudget.nextDecisionGameTime(
        gameTime, vehicle.getId(), DnvTargetingBudget.MIN_DECISION_INTERVAL, 163);
    return decision;
  }

  static boolean rememberFireAttempt(
      Map<UUID, FireAudit> audits, FireAudit audit) {
    return DnvSbwWeaponScheduler.rememberFireAttempt(audits, audit, MAX_FIRE_AUDITS);
  }

  static UUID fireOperationId(
      UUID coreId,
      long coreRevision,
      String stationId,
      String weaponName,
      UUID targetId,
      long gameTime,
      int fireIndex) {
    return DnvSbwWeaponScheduler.fireOperationId(coreId, coreRevision, stationId, weaponName, targetId, gameTime, fireIndex);
  }

  static int selectReadyWeapon(List<Boolean> ready, int startIndex) {
    return DnvSbwWeaponScheduler.selectReadyWeapon(ready, startIndex);
  }

  private boolean isProtectedObstruction(
      Entity candidate, Entity target, Optional<UUID> configuredBy) {
    if (candidate == target
        || candidate == vehicle
        || DnvTargetExclusions.isUnavailableCombatTarget(candidate)
        || (!(candidate instanceof LivingEntity)
            && !(candidate instanceof VehicleEntity))) {
      return false;
    }
    return !isHostile(candidate, configuredBy);
  }

  static boolean isAligned(Vec3 current, Vec3 desired) {
    return DnvSbwWeaponScheduler.isAligned(current, desired, MIN_ALIGNMENT_DOT);
  }

  static boolean targetLineUsable(boolean blockLineClear, boolean entityCorridorClear) {
    return blockLineClear && entityCorridorClear;
  }

  static int fireIntervalTicks(int rpm) {
    return DnvSbwWeaponScheduler.fireIntervalTicks(rpm);
  }

  private void clearTargetAndActuators() {
    targetId = null;
    blindTicks = 0;
    clearActuatorTargets();
    long gameTime = vehicle.level() instanceof ServerLevel level
        ? level.getGameTime()
        : 0L;
    autonomyCore.clear(gameTime);
    stationEngagement.clear(gameTime);
    weaponFeedback.clear();
    aimChannels.disarm();
    diagnostics.clear();
    invalidateCoreDecision();
  }

  private void clearActuatorTargets() {
    for (DnvSbwStationRuntime state : stationStates.values()) {
      state.resetTracking();
    }
  }

  private void clearActuatorState() {
    clearActuatorTargets();
    stationStates.clear();
  }

  private void invalidateCoreDecision() {
    nextCoreDecisionGameTime = 0L;
    coreDecisionTargetId = null;
    coreDecisionForcedTarget = Optional.empty();
    coreDecisionDomains = Map.of();
  }

  private static int saturatingAdd(int value, int delta) {
    return value > Integer.MAX_VALUE - delta ? Integer.MAX_VALUE : value + delta;
  }

  record FireAudit(
      UUID operationId,
      UUID coreId,
      long coreRevision,
      String stationId,
      String weaponName,
      UUID targetId,
      long gameTime,
      int fireIndex) {
    FireAudit {
      Objects.requireNonNull(operationId, "operationId");
      Objects.requireNonNull(coreId, "coreId");
      Objects.requireNonNull(stationId, "stationId");
      Objects.requireNonNull(weaponName, "weaponName");
      Objects.requireNonNull(targetId, "targetId");
      if (coreRevision < 0L || gameTime < 0L || fireIndex < 0) {
        throw new IllegalArgumentException("fire audit values must not be negative");
      }
    }
  }
}
