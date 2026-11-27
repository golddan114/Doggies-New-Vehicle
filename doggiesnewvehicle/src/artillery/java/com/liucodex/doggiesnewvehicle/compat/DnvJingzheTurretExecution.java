package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.data.vehicle.subdata.SeekInfo;
import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.liucodex.doggiesnewvehicle.core.DnvIntrinsicCoreRegistry;
import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
import com.liucodex.doggiesnewvehicle.core.DnvTurretDefinition;
import com.liucodex.doggiesnewvehicle.core.DnvTurretExecutionPort;
import com.liucodex.doggiesnewvehicle.core.DnvTurretTargetMemory;
import com.liucodex.doggiesnewvehicle.core.DnvWeaponFireGate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Five immediate entity-effect consumers. No SBW gun, queued projectile or second charge owner. */
public final class DnvJingzheTurretExecution {
  public static final DnvTurretDefinition DEFINITION = DnvTurretDefinition.entityEffect(
      "doggievehicle:jingzhe_turret", "doggiesnewvehicle:jingzhe",
      Map.of("scroll_0", Set.of("lightning"), "scroll_1", Set.of("lightning"),
          "scroll_2", Set.of("lightning"), "scroll_3", Set.of("lightning"), "scroll_4", Set.of("lightning")));
  private final AutoAimableEntity vehicle;
  private final DnvTurretExecutionPort.EffectHooks<Entity> hooks;
  private final ModulePort[] ports = new ModulePort[5];
  private final DnvIntrinsicCoreRegistry profiles = new DnvIntrinsicCoreRegistry();
  private DnvIntrinsicCoreRegistry.InstalledCore installed;
  private SeekInfo profileSeek;
  private double minRange;
  private double maxRange;
  private long profileRevision;
  private long controlRevision = 1;
  private long lastObservedTick = -1;
  private boolean executing;

  public DnvJingzheTurretExecution(AutoAimableEntity vehicle, DnvTurretExecutionPort.EffectHooks<Entity> hooks) {
    this.vehicle = Objects.requireNonNull(vehicle);
    this.hooks = Objects.requireNonNull(hooks);
    for (int module = 0; module < ports.length; module++) ports[module] = new ModulePort(module);
  }

  public DnvTurretExecutionPort module(int module) { return ports[module]; }

  public void invalidate() {
    controlRevision++;
    for (ModulePort port : ports) port.invalidate();
  }

  public boolean allowsTargetRelation(Entity target, boolean nativeEligible) {
    return DnvCombatPolicyRuntime.allowsNativeOrActiveAssignment(vehicle, target, nativeEligible);
  }

  public boolean isPvpProtected(Entity target) { return DnvCombatPolicyRuntime.isPvpProtected(target); }

  /** Refreshes the intrinsic effect profile before target selection without issuing a frame. */
  public DnvTurretTargetMemory.Configuration targetingContext() {
    refreshProfile();
    return new DnvTurretTargetMemory.Configuration(
        installed != null && installed.active(profiles), profileRevision, controlRevision);
  }

  public DnvTurretTargetMemory.CommandObservation<Entity> targetCommand() {
    DnvCombatCoordinator.TargetCommand command = DnvCombatCoordinator.targetCommand(vehicle);
    DnvTurretTargetMemory.CommandMode mode;
    if (command.target().isPresent()) {
      mode = DnvTurretTargetMemory.CommandMode.ACTIVE;
    } else if (!command.liveLease()) {
      mode = DnvTurretTargetMemory.CommandMode.NONE;
    } else {
      mode = switch (command.lossBehavior()) {
        case NATIVE_FALLBACK -> DnvTurretTargetMemory.CommandMode.NONE;
        case HOLD_LAST_TARGET -> DnvTurretTargetMemory.CommandMode.HOLD;
        case DISABLE -> DnvTurretTargetMemory.CommandMode.DISABLE;
      };
    }
    return new DnvTurretTargetMemory.CommandObservation<>(command.target(), command.revision(),
        command.validUntilTick(), command.explicitDesignation(), mode, command.reason());
  }

  private void refreshProfile() {
    SeekInfo seek = vehicle.data().compute().getSeekInfo();
    if (seek == profileSeek && installed != null && rangesMatch(seek)) return;
    invalidate();
    installed = null;
    profileSeek = seek;
    if (seek == null) return;
    minRange = seek.getMinSeekRange();
    maxRange = seek.getMaxSeekRange();
    if (!Double.isFinite(minRange) || !Double.isFinite(maxRange) || minRange < 0 || maxRange <= minRange) return;
    var profile = DEFINITION.profile(++profileRevision, minRange, maxRange);
    if (profiles.publish(profile).accepted()) installed = profiles.install(profile.profileId()).orElseThrow();
  }

  private boolean rangesMatch(SeekInfo seek) {
    return seek != null && Double.compare(minRange, seek.getMinSeekRange()) == 0
        && Double.compare(maxRange, seek.getMaxSeekRange()) == 0;
  }

  private final class ModulePort implements DnvTurretExecutionPort {
    private final int module;
    private final String station;
    private Frame issued;
    private Object frameLevel;
    private Object frameProvider;
    private Entity frameOwner;
    private Entity frameTarget;
    private EffectState frameState;
    private boolean aimed;

    private ModulePort(int module) {
      this.module = module;
      this.station = "scroll_" + module;
    }

    @Override
    public Frame inspect(UUID targetId) {
      long now = vehicle.level().getGameTime();
      if (now < lastObservedTick) DnvJingzheTurretExecution.this.invalidate();
      lastObservedTick = now;
      refreshProfile();
      frameLevel = vehicle.level();
      frameOwner = vehicle.getOwner();
      frameProvider = DnvCombatCoordinator.executionStamp(vehicle);
      frameTarget = targetId != null && frameLevel instanceof ServerLevel level ? level.getEntity(targetId) : null;
      frameState = hooks.state(module);
      aimed = false;
      String reason = liveReason();
      var ready = frameState.readiness();
      if (reason == null) reason = !ready.ammoAvailable() ? "OUT_OF_AMMO" : !ready.cooldownReady() ? "COOLDOWN"
          : !ready.baseReady() ? "WEAPON_NOT_READY" : "READY";
      issued = new Frame(vehicle.getUUID(), vehicle.level().dimension().location().toString(),
          DEFINITION.profileId(), profileRevision, controlRevision, now, station, "lightning", targetId,
          ready, reason, Optional.empty());
      return issued;
    }

    @Override
    public Result applyAim(Frame frame, Point direction) {
      String rejection = frameReason(frame);
      if (rejection != null) return Result.rejected(rejection);
      // Jingzhe drops vertically above a locked target. Barrel yaw/pitch and arc solvers are unrelated.
      if (direction == null || direction.x() != 0 || direction.y() != -1 || direction.z() != 0) {
        return Result.rejected("INVALID_EFFECT_DIRECTION");
      }
      if (!hooks.pathClear(frameTarget)) return Result.rejected("FIRING_CORRIDOR_BLOCKED");
      rejection = frameReason(frame);
      if (rejection != null) return Result.rejected(rejection);
      aimed = true;
      return new Result(true, "VERTICAL_DROP_LOCKED");
    }

    @Override
    public Result tryFire(Frame frame) {
      String rejection = frameReason(frame);
      if (rejection != null) return Result.rejected(rejection);
      if (!aimed) return Result.rejected("NOT_ALIGNED");
      Entity target = frameTarget;
      boolean allowed = DnvCombatCoordinator.fireAuthorized(vehicle, target, station, "lightning", frame.tick());
      rejection = frameReason(frame);
      if (rejection != null) return Result.rejected(rejection);
      boolean clear = hooks.pathClear(target);
      rejection = frameReason(frame);
      if (rejection != null) return Result.rejected(rejection);
      var ready = hooks.state(module).readiness();
      var gate = DnvWeaponFireGate.evaluate(new DnvWeaponFireGate.Request(
          true, DEFINITION.owns(station, "lightning"), true, ready.authorityValid(), allowed,
          clear, aimed, ready.cooldownReady(), ready.ammoAvailable()));
      if (!gate.allowed()) return Result.rejected(gate.reason().name());
      if (!ready.baseReady()) return Result.rejected("WEAPON_NOT_READY");
      // Consume permission before damage callbacks. Other modules cannot re-enter the same release.
      issued = null;
      aimed = false;
      executing = true;
      try {
        return hooks.release(module, target) ? new Result(true, "RELEASED") : Result.rejected("EFFECT_REJECTED");
      } finally {
        executing = false;
        DnvJingzheTurretExecution.this.invalidate();
      }
    }

    private String frameReason(Frame frame) {
      if (!current(frame)) return "STALE_FRAME";
      String reason = liveReason();
      return reason != null ? reason : current(frame) ? null : "STALE_FRAME";
    }

    private boolean current(Frame frame) {
      return frame != null && frame == issued && frameLevel == vehicle.level()
          && frame.tick() == vehicle.level().getGameTime() && frame.controlRevision() == controlRevision
          && frame.profileRevision() == profileRevision
          && frame.dimension().equals(vehicle.level().dimension().location().toString());
    }

    private String liveReason() {
      String rejection = factsReason();
      if (rejection != null) return rejection;
      if (!hooks.targetAllowed(frameTarget)) return "TARGET_INVALID";
      // Native/external relation providers may mutate control, configuration or module state.
      return factsReason();
    }

    private String factsReason() {
      if (executing) return "RELEASE_IN_PROGRESS";
      if (!(vehicle.level() instanceof ServerLevel level) || frameLevel != level || vehicle.isRemoved()
          || vehicle.isWreck() || vehicle.getHealth() <= 0 || level.getEntity(vehicle.getUUID()) != vehicle
          || !DEFINITION.entityType().equals(BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString())) {
        return "SOURCE_UNAVAILABLE";
      }
      if (installed == null || !installed.active(profiles) || profileSeek != vehicle.data().compute().getSeekInfo()
          || !rangesMatch(profileSeek)) return "PROFILE_CHANGED";
      if (vehicle.getFirstPassenger() != null || frameOwner != vehicle.getOwner() || !hooks.active()) return "AUTHORITY_DENIED";
      if (!Objects.equals(frameProvider, DnvCombatCoordinator.executionStamp(vehicle))) return "PROVIDER_CHANGED";
      if (frameTarget == null || frameTarget.isRemoved() || !frameTarget.isAlive() || frameTarget.level() != level
          || level.getEntity(frameTarget.getUUID()) != frameTarget || isPvpProtected(frameTarget)
          || !hooks.targetStillValid(frameTarget)) return "TARGET_INVALID";
      if (!frameState.equals(hooks.state(module)) || !frameTarget.getStringUUID().equals(frameState.targetId())) {
        return "MODULE_CHANGED";
      }
      return null;
    }

    @Override public void invalidate() { issued = null; aimed = false; }
    @Override public boolean pending() { return executing; }
  }
}
