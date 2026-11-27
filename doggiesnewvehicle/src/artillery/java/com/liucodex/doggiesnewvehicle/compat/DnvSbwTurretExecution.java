package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.vehicle.subdata.SeekInfo;
import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.atsuishio.superbwarfare.tools.VectorTool;
import com.liucodex.doggiesnewvehicle.core.DnvDeferredShot;
import com.liucodex.doggiesnewvehicle.core.DnvIntrinsicCoreRegistry;
import com.liucodex.doggiesnewvehicle.core.DnvPlatformCapabilities.BallisticCapability;
import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
import com.liucodex.doggiesnewvehicle.core.DnvStationEngagementCore.WeaponReadiness;
import com.liucodex.doggiesnewvehicle.core.DnvTurretDefinition;
import com.liucodex.doggiesnewvehicle.core.DnvTurretExecutionPort;
import com.liucodex.doggiesnewvehicle.core.DnvTurretTargetMemory;
import com.liucodex.doggiesnewvehicle.core.DnvWeaponFireGate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Entity-scoped execution adapter. No global entity cache, saved permission or projectile owner. */
public final class DnvSbwTurretExecution implements DnvTurretExecutionPort {
  private final AutoAimableEntity vehicle;
  private final DnvTurretDefinition definition;
  private final String station;
  private final String weapon;
  private final double alignmentDegrees;
  private final Hooks<Entity, Vec3> hooks;
  private final DnvIntrinsicCoreRegistry profiles = new DnvIntrinsicCoreRegistry();
  private final DnvSbwShotExecution shots = new DnvSbwShotExecution();
  private DnvIntrinsicCoreRegistry.InstalledCore installed;
  private GunData profileGun;
  private SeekInfo profileSeek;
  private long profileRevision;
  private long controlRevision = 1;
  private double nextShotTick = Double.NaN;
  private long lastObservedTick = -1;
  private Frame issued;
  private Entity frameOwner;
  private Entity frameTarget;
  private Object frameLevel;
  private Object frameProvider;
  private WeaponConfiguration frameConfiguration;
  private Vec3 aim;
  private boolean pending;

  public DnvSbwTurretExecution(AutoAimableEntity vehicle, DnvTurretDefinition definition,
      String station, String weapon, double alignmentDegrees, Hooks<Entity, Vec3> hooks) {
    this.vehicle = Objects.requireNonNull(vehicle);
    this.definition = Objects.requireNonNull(definition);
    if (definition.projectile().isEmpty() || !definition.owns(station, weapon)
        || !Double.isFinite(alignmentDegrees) || alignmentDegrees <= 0) {
      throw new IllegalArgumentException("invalid turret execution binding");
    }
    this.station = station;
    this.weapon = weapon;
    this.alignmentDegrees = alignmentDegrees;
    this.hooks = Objects.requireNonNull(hooks);
  }

  @Override
  public Frame inspect(UUID targetId) {
    long now = vehicle.level().getGameTime();
    if (now < lastObservedTick) {
      invalidate();
      // A clock rollback cannot bypass the cadence of an already accepted shot.
      if (Double.isFinite(nextShotTick)) nextShotTick = now + fireInterval();
    }
    lastObservedTick = now;
    shots.expire(now);
    refreshProfile();
    frameConfiguration = profileGun == null ? null : WeaponConfiguration.capture(profileGun, vehicle.vehicleWeaponRpm(weapon));
    frameOwner = vehicle.getOwner();
    frameLevel = vehicle.level();
    frameTarget = targetId != null && vehicle.level() instanceof ServerLevel level ? level.getEntity(targetId) : null;
    frameProvider = DnvCombatCoordinator.executionStamp(vehicle);
    aim = null;
    String reason = liveReason(targetId, frameOwner, frameProvider);
    WeaponReadiness readiness = readiness(now);
    if (reason == null) {
      reason = !readiness.ammoAvailable() ? "OUT_OF_AMMO" : !readiness.cooldownReady() ? "COOLDOWN"
          : !readiness.baseReady() ? "WEAPON_NOT_READY" : "READY";
    }
    issued = new Frame(vehicle.getUUID(), vehicle.level().dimension().location().toString(),
        definition.profileId(), profileRevision, controlRevision, now, station, weapon, targetId,
        readiness, reason, Optional.ofNullable(shots.lastReceipt()));
    return issued;
  }

  @Override
  public Result applyAim(Frame frame, Point direction) {
    String rejection = frameReason(frame);
    if (rejection != null) return Result.rejected(rejection);
    Vec3 vector = new Vec3(direction.x(), direction.y(), direction.z());
    if (!finiteDirection(vector)) return Result.rejected("INVALID_DIRECTION");
    vehicle.turretAutoAimFromVector(vector);
    aim = vector;
    return new Result(true, "AIM_APPLIED");
  }

  @Override
  public Result tryFire(Frame frame) {
    String rejection = frameReason(frame);
    if (rejection != null) return Result.rejected(rejection);
    if (pending) return Result.rejected("SHOT_ALREADY_PENDING");
    Vec3 direction = aim;
    if (!hooks.settled()) return Result.rejected("NOT_SETTLED");
    rejection = fireReason(frame.targetId(), frameOwner, frameProvider, direction);
    if (rejection != null) return Result.rejected(rejection);
    rejection = frameReason(frame);
    if (rejection != null) return Result.rejected(rejection);
    Entity target = ((ServerLevel) vehicle.level()).getEntity(frame.targetId());
    Vec3 targetPos = target.getBoundingBox().getCenter();
    GunData gun = profileGun;
    SeekInfo seek = profileSeek;
    Entity owner = frameOwner;
    Object provider = frameProvider;
    long revision = controlRevision;
    long profile = profileRevision;
    int cost = energyCost(gun);
    WeaponConfiguration configuration = frameConfiguration;
    var intent = new DnvDeferredShot.Intent(UUID.randomUUID(), vehicle.getUUID(), station, weapon,
        target.getUUID(), vehicle.getUUID(), 0, profile, revision, 0, frame.tick(),
        gun.fireIndex.get(), point(targetPos));
    // Consume the current frame before any external callback; one request, one native queue slot.
    issued = null;
    pending = true;
    shots.submitTurret(vehicle, (LivingEntity) owner, definition.projectileType(), intent, () -> {
      if (revision != controlRevision || profile != profileRevision || gun != vehicle.getGunData(weapon)
          || seek != vehicle.data().compute().getSeekInfo() || !configuration.matches(gun, vehicle.vehicleWeaponRpm(weapon))) {
        return "CONFIG_OR_CONTROL_CHANGED";
      }
      if (((ServerLevel) vehicle.level()).getEntity(intent.targetId()) != target) return "TARGET_CHANGED";
      String reason = fireReason(intent.targetId(), owner, provider, direction);
      return reason != null ? reason : configuration.matches(gun, vehicle.vehicleWeaponRpm(weapon)) ? null : "CONFIG_CHANGED";
    }, receipt -> {
      pending = false;
      if (receipt.admission().filter(DnvDeferredShot.Admission::accepted).isEmpty()) return;
      // Admission survives even a later native callback failure. Charge exactly the accepted shot.
      vehicle.consumeEnergy(cost);
      scheduleAcceptedShot(receipt.intent().issuedTick(), receipt.evaluatedTick());
      primeVirtualRound(gun);
      hooks.accepted(direction);
      vehicle.afterShoot(gun, direction);
      vehicle.playShootSound3p((LivingEntity) owner, weapon);
    }, () -> hooks.submitNative(owner, intent.targetId(), targetPos));
    var receipt = shots.lastReceipt();
    return pending ? new Result(true, "QUEUED")
        : new Result(receipt != null && receipt.admission().filter(DnvDeferredShot.Admission::accepted).isPresent(),
            receipt == null ? "QUEUE_REJECTED" : receipt.reason());
  }

  @Override
  public void invalidate() {
    controlRevision++;
    issued = null;
    aim = null;
    shots.clear();
    pending = false;
  }

  @Override
  public boolean pending() {
    shots.expire(vehicle.level().getGameTime());
    return pending;
  }

  /** Refreshes the intrinsic profile before target selection without issuing an execution frame. */
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

  public boolean cooldownReady(int earlyTicks) {
    return !Double.isFinite(nextShotTick) || vehicle.level().getGameTime() + 1.0E-9D
        >= nextShotTick - Math.max(0, earlyTicks);
  }

  public boolean allowsTargetRelation(Entity target, boolean nativeEligible) {
    return DnvCombatPolicyRuntime.allowsNativeOrActiveAssignment(vehicle, target, nativeEligible);
  }

  public void prepareVirtualRound() {
    GunData gun = vehicle.getGunData(weapon);
    if (gun != null) primeVirtualRound(gun);
  }

  private String frameReason(Frame frame) {
    if (frame == null || frame != issued || frameLevel != vehicle.level() || frame.tick() != vehicle.level().getGameTime()
        || !frame.dimension().equals(vehicle.level().dimension().location().toString())
        || frame.controlRevision() != controlRevision || frame.profileRevision() != profileRevision) {
      return "STALE_FRAME";
    }
    if (!(vehicle.level() instanceof ServerLevel level) || level.getEntity(frame.targetId()) != frameTarget) return "TARGET_CHANGED";
    String reason = liveReason(frame.targetId(), frameOwner, frameProvider);
    return reason != null ? reason : frameConfiguration != null
        && frameConfiguration.matches(profileGun, vehicle.vehicleWeaponRpm(weapon)) ? null : "CONFIG_CHANGED";
  }

  private String liveReason(UUID targetId, Entity owner, Object provider) {
    if (!(vehicle.level() instanceof ServerLevel level) || vehicle.isRemoved() || vehicle.isWreck()
        || vehicle.getHealth() <= 0 || level.getEntity(vehicle.getUUID()) != vehicle
        || !definition.entityType().equals(BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString())) {
      return "SOURCE_UNAVAILABLE";
    }
    if (installed == null || !installed.active(profiles) || profileGun != vehicle.getGunData(weapon)
        || profileSeek != vehicle.data().compute().getSeekInfo()) return "PROFILE_CHANGED";
    if (!definition.projectileType().equals(profileGun.get(GunProp.PROJECTILE).getItemId())
        || profileGun.get(GunProp.PROJECTILE_AMOUNT) != 1) return "PROJECTILE_UNSUPPORTED";
    double gravity = profileGun.get(GunProp.GRAVITY);
    if (!Double.isFinite(gravity) || gravity < 0
        || definition.ballistic() == BallisticCapability.DIRECT && gravity != 0) return "BALLISTICS_UNSUPPORTED";
    if (!(owner instanceof LivingEntity) || !owner.isAlive() || owner != vehicle.getOwner()
        || owner.level() != level || vehicle.getFirstPassenger() != null || !hooks.active()) {
      return "AUTHORITY_DENIED";
    }
    if (!Objects.equals(provider, DnvCombatCoordinator.executionStamp(vehicle))) return "PROVIDER_CHANGED";
    Entity target = targetId == null ? null : level.getEntity(targetId);
    if (target == null || target.isRemoved() || !target.isAlive() || target.level() != level
        || DnvCombatPolicyRuntime.isPvpProtected(target) || !hooks.targetAllowed(target)) return "TARGET_INVALID";
    if (vehicle.getFirstPassenger() != null || owner != vehicle.getOwner() || !hooks.active()
        || target.isRemoved() || !target.isAlive() || DnvCombatPolicyRuntime.isPvpProtected(target)
        || !Objects.equals(provider, DnvCombatCoordinator.executionStamp(vehicle))) return "AUTHORITY_CHANGED";
    return null;
  }

  private String fireReason(UUID targetId, Entity owner, Object provider, Vec3 direction) {
    String rejection = liveReason(targetId, owner, provider);
    if (rejection != null) return rejection;
    Entity target = ((ServerLevel) vehicle.level()).getEntity(targetId);
    boolean externalAllowed = DnvCombatCoordinator.fireAuthorized(
        vehicle, target, station, weapon, vehicle.level().getGameTime());
    // Providers are external callbacks and may change control or target facts during authorization.
    rejection = liveReason(targetId, owner, provider);
    if (rejection != null) return rejection;
    Vec3 muzzle = vehicle.getShootPos(weapon, 1.0F);
    Vec3 barrel = vehicle.getShootVec(weapon, 1.0F);
    var ready = readiness(vehicle.level().getGameTime());
    var gate = DnvWeaponFireGate.evaluate(new DnvWeaponFireGate.Request(
        true, definition.owns(station, weapon), true, ready.authorityValid(),
        externalAllowed,
        vehicle.checkNoClip(target, muzzle), finiteDirection(direction) && finiteDirection(barrel)
            && VectorTool.calculateAngle(barrel, direction) <= alignmentDegrees,
        ready.cooldownReady(), ready.ammoAvailable()));
    if (!gate.allowed()) return gate.reason().name();
    return ready.baseReady() ? null : "WEAPON_NOT_READY";
  }

  private WeaponReadiness readiness(long now) {
    GunData gun = vehicle.getGunData(weapon);
    return new WeaponReadiness(gun != null && vehicle.getEnergy() >= energyCost(gun),
        !Double.isFinite(nextShotTick) || now + 1.0E-9D >= nextShotTick,
        vehicle.getFirstPassenger() == null && hooks.active(), gun != null,
        gun != null && !gun.overHeat.get() && gun.canShoot(vehicle.getAmmoSupplier()));
  }

  private double fireInterval() {
    return Math.max(1.0D, 1200.0D / Math.max(1, vehicle.vehicleWeaponRpm(weapon)));
  }

  private void scheduleAcceptedShot(long issuedTick, long now) {
    double interval = fireInterval();
    // Retain the fractional phase for rapid guns (480 RPM = alternating 3/2 ticks).
    // Integer-period weapons still wait their full interval after actual admission.
    if (interval == Math.rint(interval)) {
      nextShotTick = now;
    } else if (!Double.isFinite(nextShotTick) || nextShotTick < issuedTick - interval) {
      // Queue latency must not be added to the initial fractional cadence phase.
      nextShotTick = issuedTick;
    }
    nextShotTick += interval;
  }

  private void refreshProfile() {
    GunData gun = vehicle.getGunData(weapon);
    SeekInfo seek = vehicle.data().compute().getSeekInfo();
    if (gun == profileGun && seek == profileSeek && installed != null) return;
    invalidate();
    installed = null;
    profileGun = gun;
    profileSeek = seek;
    if (gun == null || seek == null || !Double.isFinite(seek.getMinSeekRange())
        || !Double.isFinite(seek.getMaxSeekRange()) || seek.getMinSeekRange() < 0
        || seek.getMaxSeekRange() <= seek.getMinSeekRange()) return;
    var profile = definition.profile(++profileRevision, seek.getMinSeekRange(), seek.getMaxSeekRange());
    if (profiles.publish(profile).accepted()) installed = profiles.install(profile.profileId()).orElseThrow();
  }

  private void primeVirtualRound(GunData gun) {
    int cost = energyCost(gun);
    if (vehicle.getEnergy() >= cost) gun.ammo.set(Math.max(gun.ammo.get(), Math.max(1, cost)));
    else gun.ammo.reset();
  }

  private static int energyCost(GunData gun) { return Math.max(0, gun.get(GunProp.AMMO_COST_PER_SHOOT)); }
  private record WeaponConfiguration(String projectile, int amount, int cost, int rpm, int fireIndex,
      double velocity, double gravity, double spread) {
    static WeaponConfiguration capture(GunData gun, int rpm) {
      return new WeaponConfiguration(gun.get(GunProp.PROJECTILE).getItemId(), gun.get(GunProp.PROJECTILE_AMOUNT),
          energyCost(gun), rpm, gun.fireIndex.get(), gun.get(GunProp.VELOCITY), gun.get(GunProp.GRAVITY), gun.get(GunProp.SPREAD));
    }
    boolean matches(GunData gun, int currentRpm) {
      return projectile.equals(gun.get(GunProp.PROJECTILE).getItemId()) && amount == gun.get(GunProp.PROJECTILE_AMOUNT)
          && cost == energyCost(gun) && rpm == currentRpm && fireIndex == gun.fireIndex.get()
          && Double.compare(velocity, gun.get(GunProp.VELOCITY)) == 0
          && Double.compare(gravity, gun.get(GunProp.GRAVITY)) == 0
          && Double.compare(spread, gun.get(GunProp.SPREAD)) == 0;
    }
  }
  private static Point point(Vec3 vector) { return new Point(vector.x, vector.y, vector.z); }
  private static boolean finiteDirection(Vec3 vector) {
    return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y)
        && Double.isFinite(vector.z) && vector.lengthSqr() > 1.0E-8;
  }

}
