package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotAdmission;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNativeProjectilePenetration;
import com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor;
import com.liucodex.doggiesnewvehicle.core.DnvDeferredShot;
import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Exact named SBW queue/admission adapter. No global entity map, projectile factory or ammo writer. */
public final class DnvSbwShotExecution {
  private static final ThreadLocal<Request> SUBMITTING = new ThreadLocal<>();
  private static final ThreadLocal<Request> EXECUTING = new ThreadLocal<>();
  private final java.util.Map<String, Request> pending = new java.util.LinkedHashMap<>();
  private DnvDeferredShot.Receipt lastReceipt;

  public DnvDeferredShot.Receipt lastReceipt() { return lastReceipt; }

  /** A built-in turret supplies its guarded super call; the same native queue/admission owns fire. */
  void submitTurret(VehicleEntity vehicle, LivingEntity shooter, String projectileType,
      DnvDeferredShot.Intent intent, Supplier<String> revalidate,
      Consumer<DnvDeferredShot.Receipt> completed, Runnable nativeSubmission) {
    submit(vehicle, intent, revalidate, completed, projectileType, shooter, nativeSubmission);
  }

  private void submit(VehicleEntity vehicle, DnvDeferredShot.Intent intent, Supplier<String> revalidate,
      Consumer<DnvDeferredShot.Receipt> completed, String projectileType,
      LivingEntity shooter, Runnable nativeSubmission) {
    Request previous = pending.get(intent.station());
    if (previous != null && !previous.shot.terminal()) {
      var rejected = new DnvDeferredShot(intent);
      rejected.cancel("SHOT_ALREADY_PENDING");
      lastReceipt = rejected.receipt();
      completed.accept(lastReceipt);
      return;
    }
    Request request = new Request(vehicle, intent, revalidate, receipt -> {
      lastReceipt = receipt;
      completed.accept(receipt);
    }, projectileType, shooter);
    pending.put(intent.station(), request);
    if (SUBMITTING.get() != null || EXECUTING.get() != null) {
      request.shot.cancel("NESTED_SUBMISSION");
      request.finish();
      return;
    }
    SUBMITTING.set(request);
    try {
      nativeSubmission.run();
      if (!request.queueIntercepted) request.shot.cancel("QUEUE_NOT_INTERCEPTED");
    } catch (RuntimeException | LinkageError exception) {
      request.shot.cancel("SUBMISSION_FAILED");
    } finally {
      SUBMITTING.remove();
      if (request.shot.terminal()) request.finish();
    }
  }

  void expire(long now) {
    for (Request request : java.util.List.copyOf(pending.values())) {
      if (!request.shot.terminal()
          && (now < request.intent.issuedTick() || now - request.intent.issuedTick() > DnvDeferredShot.MAX_AGE_TICKS)) {
        request.shot.cancel("EXPIRED", now);
        request.finish();
      }
    }
    pending.values().removeIf(request -> request.shot.terminal());
  }

  void clear() {
    var cancelled = java.util.List.copyOf(pending.values());
    pending.clear();
    for (Request request : cancelled) {
      request.shot.cancel("LIFECYCLE_INVALIDATED", request.level.getGameTime());
      request.finish();
    }
  }

  public static boolean submitting(VehicleEntity vehicle) {
    Request request = SUBMITTING.get();
    return request != null && request.vehicle == vehicle;
  }

  public static boolean queue(VehicleEntity vehicle, int delay, Runnable nativeWork,
      BiPredicate<Integer, Runnable> nativeQueue) {
    Request request = SUBMITTING.get();
    if (request == null || request.vehicle != vehicle) return nativeQueue.test(delay, nativeWork);
    if (request.queueIntercepted) return false;
    request.queueIntercepted = true;
    if (delay < 0 || delay > DnvDeferredShot.MAX_AGE_TICKS) {
      request.shot.cancel("DELAY_UNSUPPORTED");
      return false;
    }
    request.shot.queued(true);
    boolean queued = nativeQueue.test(delay, () -> request.execute(nativeWork));
    if (!queued) request.shot.cancel("QUEUE_REJECTED");
    return queued;
  }

  /** Invoked in the real queued callback, after it resamples GunData and ShootParameters. */
  public static void shoot(GunData data, ShootParameters parameters) {
    Request request = EXECUTING.get();
    if (request == null) {
      data.shoot(parameters);
      return;
    }
    if (request.parameters != null || data != request.gun || parameters.data != data
        || parameters.level != request.level || parameters.ammoSupplier != request.vehicle.getAmmoSupplier()
        || parameters.shooter != request.shooter || !java.util.Objects.equals(request.intent.targetId(), parameters.targetEntityUUID)
        || !parameters.zoom || Double.compare(parameters.spread, data.get(GunProp.SPREAD)) != 0
        || !parameters.targetPos.equals(new Vec3(request.intent.aimPoint().x(), request.intent.aimPoint().y(), request.intent.aimPoint().z()))
        || request.level.getGameTime() != request.executionTick
        || parameters.shootPosition.distanceToSqr(request.muzzle) > 1.0E-12D
        || parameters.shootDirection.distanceToSqr(request.direction) > 1.0E-12D) {
      request.shot.cancel("PARAMETERS_CHANGED");
      return;
    }
    request.parameters = parameters;
    try {
      data.shoot(parameters);
    } finally {
      request.parameters = null;
    }
  }

  /** Returns the actual boolean from the single original world call, never GunItem's boolean. */
  public static boolean admit(ServerLevel level, Entity projectile, ShootParameters parameters) {
    DnvBlockDamagePolicy.markShot(projectile, parameters);
    DnvTurretShells.mark(projectile, parameters);
    Request request = EXECUTING.get();
    if (request == null || request.parameters != parameters) {
      if (parameters.ammoSupplier instanceof VehicleEntity vehicle
          && vehicle instanceof com.liucodex.doggiesnewvehicle.api.combat.DnvNativeProjectilePercent profile
          && parameters.data == vehicle.getGunData("Main"))
        com.liucodex.doggiesnewvehicle.core.DnvPercentHits.mark(projectile, profile.directPercent("Main"));
      if (parameters.ammoSupplier instanceof DnvNativeShotAdmission host)
        return host.admitNativeProjectile(level, projectile, parameters);
      return level.addFreshEntity(projectile);
    }
    // ShootEvent.Pre is an external callback inside SBW; it may revoke permission or move the gun.
    String rejection = request.revalidate == null ? "LIFECYCLE_INVALIDATED" : request.revalidate.get();
    if (rejection != null) {
      request.shot.cancel(rejection);
      return false;
    }
    if (level != request.level || level.getGameTime() != request.executionTick
        || projectile.level() != level || projectile.isRemoved()
        || !request.projectileType.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).toString())
        || projectile.position().distanceToSqr(request.muzzle) > 1.0E-10D
        || request.vehicle.getGunData(request.intent.weapon()) != request.gun
        || request.vehicle.getShootPos(request.intent.weapon(), 1.0F).distanceToSqr(request.muzzle) > 1.0E-12D
        || request.vehicle.getShootVec(request.intent.weapon(), 1.0F).distanceToSqr(request.direction) > 1.0E-12D) {
      request.shot.cancel("ADMISSION_CONTEXT_CHANGED");
      return false;
    }
    // Admission records the native world result. Once spawned, SBW owns the projectile.
    if (request.vehicle instanceof DnvNativeProjectilePenetration profile)
      DnvTaggedProjectileArmor.mark(projectile, profile.directPenetration(request.intent.weapon()));
    if (request.vehicle instanceof com.liucodex.doggiesnewvehicle.api.combat.DnvNativeProjectilePercent profile)
      com.liucodex.doggiesnewvehicle.core.DnvPercentHits.mark(projectile, profile.directPercent(request.intent.weapon()));
    return request.shot.admit(projectile.getUUID(), point(projectile.position()), point(projectile.getDeltaMovement()),
        () -> level.addFreshEntity(projectile));
  }

  public static boolean bulletResult(ShootParameters parameters, boolean nativeResult) {
    Request request = EXECUTING.get();
    if (request == null || request.parameters != parameters) {
      return nativeResult && (!(parameters.ammoSupplier instanceof DnvNativeShotAdmission host)
          || host.nativeProjectileAccepted(parameters));
    }
    return nativeResult && request.shot.receipt().admission().filter(DnvDeferredShot.Admission::accepted).isPresent();
  }

  private static Point point(Vec3 vector) { return new Point(vector.x, vector.y, vector.z); }

  private static final class Request {
    private final VehicleEntity vehicle;
    private final ServerLevel level;
    private final GunData gun;
    private final DnvDeferredShot.Intent intent;
    private final DnvDeferredShot shot;
    private final String projectileType;
    private final LivingEntity shooter;
    private Supplier<String> revalidate;
    private Consumer<DnvDeferredShot.Receipt> completed;
    private boolean queueIntercepted;
    private boolean running;
    private long executionTick;
    private Vec3 muzzle;
    private Vec3 direction;
    private ShootParameters parameters;

    private Request(VehicleEntity vehicle, DnvDeferredShot.Intent intent, Supplier<String> revalidate,
        Consumer<DnvDeferredShot.Receipt> completed, String projectileType, LivingEntity shooter) {
      this.vehicle = vehicle;
      this.level = (ServerLevel) vehicle.level();
      this.gun = vehicle.getGunData(intent.weapon());
      this.intent = intent;
      this.shot = new DnvDeferredShot(intent);
      this.revalidate = revalidate;
      this.completed = completed;
      this.projectileType = java.util.Objects.requireNonNull(projectileType);
      this.shooter = shooter;
    }

    private void execute(Runnable nativeWork) {
      if (EXECUTING.get() != null) {
        shot.cancel("NESTED_EXECUTION");
        finish();
        return;
      }
      EXECUTING.set(this);
      running = true;
      try {
        executionTick = level.getGameTime();
        shot.execute(executionTick, () -> {
          if (vehicle.level() != level || vehicle.isRemoved() || vehicle.isWreck()
              || level.getEntity(intent.platformId()) != vehicle
              || vehicle.getGunData(intent.weapon()) != gun) return "SOURCE_CHANGED";
          String rejection = revalidate.get();
          if (rejection != null) return rejection;
          muzzle = vehicle.getShootPos(intent.weapon(), 1.0F);
          direction = vehicle.getShootVec(intent.weapon(), 1.0F);
          return null;
        }, nativeWork);
      } finally {
        EXECUTING.remove();
        parameters = null;
        running = false;
        finish();
      }
    }

    private void finish() {
      // Lifecycle hooks may run inside addFreshEntity: wait for its actual return before publishing.
      if (running || !shot.terminal() || completed == null) return;
      Consumer<DnvDeferredShot.Receipt> sink = completed;
      completed = null;
      revalidate = null;
      sink.accept(shot.receipt());
    }
  }
}
