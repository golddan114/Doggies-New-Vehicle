package com.liucodex.noise.entity;

import com.liucodex.doggiesnewvehicle.compat.DnvSightObservation;

import com.liucodex.doggiesnewvehicle.boss.DnvBossRunningGear;

import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore;
import com.liucodex.doggiesnewvehicle.core.DnvLazyCandidateFrame;
import com.atsuishio.superbwarfare.entity.vehicle.damage.DamageModifier;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetCandidateCache;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import com.liucodex.doggiesnewvehicle.entity.DnvVehiclePresentation;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A real Superb Warfare vehicle with a server-authoritative autonomous tracked controller. This is
 * the sole production Noise entity.
 */
abstract class AbstractNoiseVehicleState extends AbstractNoiseVehicleContract
    implements DnvVehiclePresentation {
  public static final float MAX_HEALTH = 4_000.0F;
  protected static final double TARGET_RANGE = 80.0D;
  protected static final double PRESSURE_MIN_RANGE = 30.0D;
  protected static final double PRESSURE_MAX_RANGE = 40.0D;
  protected static final int TARGET_SCAN_INTERVAL = DnvTargetingBudget.MIN_FULL_SCAN_INTERVAL;
  protected static final float CORE_TURN_SPEED = 1.8F;
  protected static final float IDLE_CORE_TURN_SPEED = 0.28F;
  protected static final float IDLE_CORE_MAX_OFFSET = 28.0F;
  protected static final int IDLE_CORE_INITIAL_PAUSE = 40;
  protected static final int IDLE_CORE_MIN_PAUSE = 50;
  protected static final int IDLE_CORE_RANDOM_PAUSE = 91;
  protected static final float GUN_PITCH_SPEED = 1.0F;
  protected static final float HULL_TURN_SPEED = 0.72F;
  protected static final float TRACK_SHOE_COUNT = DnvBossRunningGear.PHASE_PERIOD;
  protected static final double TRACK_SHOE_SPACING = DnvBossRunningGear.PHASE_DISTANCE;
  protected static final double TRACK_HALF_WIDTH = DnvBossRunningGear.HALF_WIDTH;
  protected static final float ROAD_WHEEL_DEGREES_PER_TRACK_PHASE =
      DnvBossRunningGear.WHEEL_DEGREES_PER_PHASE;
  protected static final double MAX_FORWARD_SPEED = 0.220D;
  protected static final int MAIN_MAGAZINE = 6;
  protected static final int MAIN_INTERVAL = 20;
  protected static final int MAIN_RELOAD = 160;
  protected static final double MAIN_MIN_RANGE = 8.0D;
  protected static final double MAIN_MAX_RANGE = 60.0D;
  protected static final double MAIN_VELOCITY = 24.0D;
  protected static final double MAIN_GRAVITY = 0.0D;
  protected static final float MAIN_DAMAGE = 60.0F;
  protected static final float MAIN_EXPLOSION_DAMAGE = 0.0F;
  protected static final float MAIN_EXPLOSION_RADIUS = 0.0F;
  protected static final int AUTOCANNON_MAGAZINE = 40;
  protected static final int AUTOCANNON_RELOAD = 100;
  protected static final double AUTOCANNON_RANGE = 40.0D;
  protected static final float AUTOCANNON_VELOCITY = 48.0F;
  protected static final double AUTOCANNON_MUZZLE_CLEARANCE = 0.22D;
  protected static final float AUTOCANNON_MAX_LOCAL_YAW = 82.0F;
  protected static final float AUTOCANNON_MIN_PITCH = -8.0F;
  protected static final float AUTOCANNON_MAX_PITCH = 60.0F;
  protected static final DustParticleOptions AUTOCANNON_MUZZLE_PARTICLE =
      new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.06F), 0.72F);
  protected static final int AUTOCANNON_MUZZLE_PARTICLE_POINTS = 9;
  protected static final double AUTOCANNON_MUZZLE_PARTICLE_SPACING = 0.42D;
  protected static final int MISSILE_MAGAZINE = 3;
  protected static final int MISSILE_INTERVAL = 10;
  protected static final int MISSILE_RELOAD = 240;
  protected static final double MISSILE_MIN_RANGE = 8.0D;
  protected static final double MISSILE_MAX_RANGE = 40.0D;
  protected static final float MISSILE_TURN_SPEED = 2.0F;
  protected static final float SIDE_ROCKET_DAMAGE = 40.0F;
  protected static final float SIDE_ROCKET_EXPLOSION_DAMAGE = 30.0F;
  protected static final float SIDE_ROCKET_EXPLOSION_RADIUS = 2.0F;
  protected static final float SIDE_ROCKET_VELOCITY = 3.2F;
  protected static final float SIDE_ROCKET_GRAVITY = 0.015F;
  protected static final int VLS_MAGAZINE = 2;
  protected static final int VLS_INTERVAL = 20;
  protected static final int VLS_RELOAD = 200;
  protected static final int VLS_LOCK_TICKS = 20;
  protected static final double VLS_MIN_RANGE = 18.0D;
  protected static final double VLS_MAX_RANGE = 72.0D;
  protected static final float MISSILE_DAMAGE = 60.0F;
  protected static final float MISSILE_EXPLOSION_DAMAGE = 140.0F;
  protected static final float MISSILE_EXPLOSION_RADIUS = 4.0F;
  protected static final float MISSILE_INITIAL_VELOCITY = 2.0F;
  protected static final int SECONDARY_SETTLED_TICKS = 3;
  protected static final int OUT_OF_COMBAT_HEAL_DELAY = 1_200;
  protected static final float OUT_OF_COMBAT_HEAL_PER_TICK = 1.0F;
  protected static final double EXCESSIVE_HEIGHT_DIFFERENCE = 16.0D;
  protected static final int STARTUP_READY_VOICE_DELAY = 36;
  protected static final int GENERAL_VOICE_COOLDOWN = 50;
  protected static final int ATTACK_VOICE_COOLDOWN = 160;
  protected static final int DAMAGE_VOICE_COOLDOWN = 100;
  protected static final int TARGET_ACQUIRED_VOICE_COOLDOWN = 240;
  protected static final int DESTRUCTION_SEQUENCE_TICKS = 60;
  public static final int ENTRANCE_EFFECT_TICKS = NoiseDropTimeline.TOTAL_TICKS;
  protected static final double PRESENTATION_RANGE = 128.0D;
  protected static final int PENDING_VOICE_NONE = 0;
  protected static final int PENDING_VOICE_MOVEMENT = 1;
  protected static final int PENDING_VOICE_OUT_OF_RANGE = 2;
  protected static final int PENDING_VOICE_TARGET_ACQUIRED = 3;
  protected static final DamageModifier IDENTITY_DAMAGE_MODIFIER = new DamageModifier();
  protected static final String OWNED_ENTITY_TAG = "noise_owned";
  protected static final String OWNER_UUID_TAG = "NoiseOwner";
  protected static final Vec3 AUTOCANNON_L_YAW_PIVOT_LOCAL =
      new Vec3(1.705207D, 1.555138D, 1.050610D);
  protected static final Vec3 AUTOCANNON_R_YAW_PIVOT_LOCAL =
      new Vec3(-1.705810D, 1.555138D, 1.050610D);
  protected static final Vec3 AUTOCANNON_L_PITCH_PIVOT_LOCAL =
      new Vec3(1.705206D, 1.901002D, 1.050822D);
  protected static final Vec3 AUTOCANNON_R_PITCH_PIVOT_LOCAL =
      new Vec3(-1.705810D, 1.901002D, 1.050822D);
  protected static final Vec3 AUTOCANNON_L_MUZZLE_LOCAL = new Vec3(1.705207D, 1.901002D, 1.926614D);
  protected static final Vec3 AUTOCANNON_R_MUZZLE_LOCAL = new Vec3(-1.705810D, 1.901002D, 1.926614D);
  protected static final Vec3 MISSILE_POD_L_YAW_PIVOT_LOCAL =
      new Vec3(1.345815D, 4.678438D, -0.797716D);
  protected static final Vec3 MISSILE_POD_R_YAW_PIVOT_LOCAL =
      new Vec3(-1.325815D, 4.678438D, -0.797716D);
  protected static final Vec3 MISSILE_POD_L_PITCH_PIVOT_LOCAL =
      new Vec3(1.349663D, 4.890142D, -0.799954D);
  protected static final Vec3 MISSILE_POD_R_PITCH_PIVOT_LOCAL =
      new Vec3(-1.329664D, 4.890142D, -0.799954D);
  protected static final Vec3 MISSILE_POD_L_LAUNCH_LOCAL = new Vec3(1.349663D, 4.910265D, -0.588821D);
  protected static final Vec3 MISSILE_POD_R_LAUNCH_LOCAL =
      new Vec3(-1.329664D, 4.910265D, -0.588821D);

  protected static final EntityDataAccessor<Integer> ENTRANCE_TICKS =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Integer> DESTRUCTION_TICKS =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Float> CORE_YAW =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> MAIN_GUN_PITCH =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> AUTOCANNON_L_YAW =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> AUTOCANNON_L_PITCH =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> AUTOCANNON_R_YAW =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> AUTOCANNON_R_PITCH =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> MISSILE_POD_L_YAW =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> MISSILE_POD_L_PITCH =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> MISSILE_POD_R_YAW =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> MISSILE_POD_R_PITCH =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> DRIVE_INTENSITY =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> TRACK_PHASE_L =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> TRACK_PHASE_R =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> TRACK_SPEED_L =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> TRACK_SPEED_R =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> ROAD_WHEEL_ANGLE_L =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> ROAD_WHEEL_ANGLE_R =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> MOTHERSHIP_DROP_START =
      SynchedEntityData.defineId(NoiseVehicleEntity.class, EntityDataSerializers.FLOAT);

  protected final ServerBossEvent bossEvent =
      new ServerBossEvent(
          Component.translatable("bossbar.noise.noise"),
          BossEvent.BossBarColor.RED,
          BossEvent.BossBarOverlay.PROGRESS);
  protected final DnvAutonomyCore autonomyCore =
      new DnvAutonomyCore(PRESSURE_MIN_RANGE, PRESSURE_MAX_RANGE);
  protected final DnvTargetCandidateCache targetCandidates =
      new DnvTargetCandidateCache(TARGET_SCAN_INTERVAL);
  protected final AutocannonStation autocannonLeft = new AutocannonStation(0, 0);
  protected final AutocannonStation autocannonRight = new AutocannonStation(3, 1);
  protected final WeaponStation missilePodLeft = new WeaponStation(MISSILE_MAGAZINE, 2);
  protected final WeaponStation missilePodRight = new WeaponStation(MISSILE_MAGAZINE, 3);
  protected final WeaponStation vlsBattery = new WeaponStation(VLS_MAGAZINE, 4);
  protected final WeaponStation vlsBatteryRight = new WeaponStation(VLS_MAGAZINE, 5);
  // Six secondaries, main-gun aim and obstruction recovery each have a distinct queue owner.
  private final UUID[] stationGeometryOwners = new UUID[9];
  @Nullable private ServerLevel stationGeometryLevel;

  @Nullable protected UUID combatTargetId;
  @Nullable protected Vec3 patrolDestination;
  protected int patrolCooldown = 40;
  protected int patrolTravelTicks;
  protected float requestedThrottle;
  protected float requestedSteering;
  protected int leftMainReload;
  protected int rightMainReload = 15;
  protected int leftMainAmmo = MAIN_MAGAZINE;
  protected int rightMainAmmo = MAIN_MAGAZINE;
  protected int mainSettledTicks;
  protected Vec3 mainAim = Vec3.ZERO;
  @Nullable protected UUID mainAimTargetId;
  protected int mainAimTick = Integer.MIN_VALUE;
  protected float idleCoreYawOffset;
  protected int idleCorePauseTicks = IDLE_CORE_INITIAL_PAUSE;
  protected int vlsTubeIndex;
  protected int obstructedTargetTicks;
  protected int lastAttackLineTick = DnvTargetingBudget.NEVER_SCANNED;
  protected int nextObstructionRetargetTick;
  @Nullable protected DnvSightObservation mainSight;
  @Nullable protected DnvSightObservation attackSight;
  protected boolean attackLineDeferred;
  protected int obstructionProbeCursor;
  protected int outOfCombatTicks;
  protected int startupVoiceStage;
  protected int startupVoiceDelay;
  protected int generalVoiceCooldown;
  protected int attackVoiceCooldown;
  protected int damageVoiceCooldown;
  protected int targetAcquiredVoiceCooldown;
  protected int idleVoiceCooldown = 120;
  protected int pendingVoice;
  protected boolean wasMoving;
  protected boolean coreOrientationInitialized;
  protected boolean holdPosition;
  protected boolean entranceShown;
  protected boolean wreckCombatStateCleared;

  protected AbstractNoiseVehicleState(EntityType<? extends NoiseVehicleEntity> type, Level level) {
    super(type, level);
  }

  /**
   * Boss health is an entity invariant, not a best-effort datapack value. This also protects newly
   * spawned bosses if a future SBW resource loader temporarily falls back to its 50-health default
   * during reload.
   */
  @Override
  public float getMaxHealth() {
    return MAX_HEALTH;
  }

  @Override
  protected void defineSynchedData() {
    // SBW 0.8.9 is an obfuscated local dependency in this workspace.
    // Its mapped Entity override therefore keeps the runtime method name
    // on the development compile classpath (same bridge used by our
    // Changfeng and Jingzhe entities).
    super.m_8097_();
    entityData.define(ENTRANCE_TICKS, ENTRANCE_EFFECT_TICKS);
    entityData.define(DESTRUCTION_TICKS, 0);
    entityData.define(CORE_YAW, 0.0F);
    entityData.define(MAIN_GUN_PITCH, 0.0F);
    entityData.define(AUTOCANNON_L_YAW, 0.0F);
    entityData.define(AUTOCANNON_L_PITCH, 0.0F);
    entityData.define(AUTOCANNON_R_YAW, 0.0F);
    entityData.define(AUTOCANNON_R_PITCH, 0.0F);
    entityData.define(MISSILE_POD_L_YAW, 0.0F);
    entityData.define(MISSILE_POD_L_PITCH, 0.0F);
    entityData.define(MISSILE_POD_R_YAW, 0.0F);
    entityData.define(MISSILE_POD_R_PITCH, 0.0F);
    entityData.define(DRIVE_INTENSITY, 0.0F);
    entityData.define(TRACK_PHASE_L, 0.0F);
    entityData.define(TRACK_PHASE_R, 0.0F);
    entityData.define(TRACK_SPEED_L, 0.0F);
    entityData.define(TRACK_SPEED_R, 0.0F);
    entityData.define(ROAD_WHEEL_ANGLE_L, 0.0F);
    entityData.define(ROAD_WHEEL_ANGLE_R, 0.0F);
    entityData.define(MOTHERSHIP_DROP_START, -1.0F);
  }


  protected static final class WeaponStation {
    @Nullable protected UUID targetId;
    Vec3 aimVector = Vec3.ZERO;
    int ammo;
    int cooldown;
    int reload;
    int settledTicks;
    @Nullable DnvSightObservation sight;
    boolean geometryDeferred;
    final int scheduleSlot;

    WeaponStation(int magazine, int scheduleSlot) {
      ammo = magazine;
      this.scheduleSlot = scheduleSlot;
    }

    void tickTimers(int magazine) {
      if (cooldown > 0) cooldown--;
      if (reload > 0 && --reload == 0) ammo = magazine;
    }

    void disable() {
      targetId = null;
      aimVector = Vec3.ZERO;
      settledTicks = 0;
      sight = null;
      geometryDeferred = false;
    }

    void disableAndEmpty() {
      disable();
      ammo = 0;
      cooldown = 0;
      reload = 0;
    }

    void arm(int magazine) {
      disable();
      ammo = magazine;
      cooldown = 0;
      reload = 0;
    }
  }

  protected final class AutocannonStation {
    @Nullable protected UUID targetId;
    int ammo = AUTOCANNON_MAGAZINE;
    int cooldown;
    int reload;
    int settledTicks;
    int cadenceIndex;
    @Nullable DnvSightObservation sight;
    boolean geometryDeferred;

    AutocannonStation(int initialCooldown, int initialCadenceIndex) {
      cooldown = Math.max(0, initialCooldown);
      cadenceIndex = NoiseAutocannonCadence.normalizeIndex(initialCadenceIndex);
    }

    void tick() {
      if (cooldown > 0) cooldown--;
      if (reload > 0 && --reload == 0) ammo = AUTOCANNON_MAGAZINE;
    }

    void disable() {
      targetId = null;
      settledTicks = 0;
      sight = null;
      geometryDeferred = false;
    }

    void arm(int initialCooldown, int initialCadenceIndex) {
      disable();
      ammo = AUTOCANNON_MAGAZINE;
      cooldown = initialCooldown;
      reload = 0;
      cadenceIndex = NoiseAutocannonCadence.normalizeIndex(initialCadenceIndex);
    }

    int nextFireInterval() {
      int interval = NoiseAutocannonCadence.intervalForIndex(cadenceIndex);
      cadenceIndex = NoiseAutocannonCadence.nextIndex(cadenceIndex);
      return interval;
    }

    @Nullable
    Entity resolveTarget(Vec3 muzzle, boolean left, @Nullable UUID reservedTargetId,
        DnvLazyCandidateFrame<Entity> frame) {
      Entity current = null;
      if (targetId != null && level() instanceof ServerLevel serverLevel) {
        current = serverLevel.getEntity(targetId);
      }
      geometryDeferred = false;
      if (validFrom(current, muzzle, left)) return current;
      if (geometryDeferred) return null;
      targetId = null;
      settledTicks = 0;
      return null;
    }

    boolean validFrom(@Nullable Entity entity, Vec3 muzzle, boolean left) {
      return isTrackedTargetValid(entity)
          && entity.getBoundingBox().getCenter().distanceToSqr(muzzle)
              <= AUTOCANNON_RANGE * AUTOCANNON_RANGE
          && withinAutocannonArc(entity, muzzle)
          && hasCachedLineOfSight(muzzle, entity, left);
    }

    boolean hasCachedLineOfSight(Vec3 muzzle, Entity target, boolean left) {
      UUID candidateId = target.getUUID();
      if (sight == null || !sight.reusable(candidateId, muzzle, target.getBoundingBox(),
          target.getEyeY(), stationSightPoseKey(left ? 0 : 1), tickCount)) {
        if (!(level() instanceof ServerLevel serverLevel)
            || !DnvTargetingBudget.admitGeometryCheck(
                serverLevel, stationGeometryOwner(left ? 0 : 1))) {
          geometryDeferred = true;
          return false;
        }
        geometryDeferred = false;
        sight = new DnvSightObservation(candidateId, muzzle, target.getBoundingBox(),
            target.getEyeY(), stationSightPoseKey(left ? 0 : 1), tickCount,
            DnvTargetingBudget.nextCheckTick(tickCount, getId(),
                DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL, left ? 50 : 51),
            hasClearShot(muzzle, target));
      }
      return sight.clear();
    }
  }

  protected UUID stationGeometryOwner(int slot) {
    if (!(level() instanceof ServerLevel serverLevel)) {
      throw new IllegalStateException("station geometry requires a server level");
    }
    if (stationGeometryLevel != serverLevel) {
      releaseStationGeometryOwners();
      stationGeometryLevel = serverLevel;
    }
    if (stationGeometryOwners[slot] == null) {
      stationGeometryOwners[slot] = UUID.nameUUIDFromBytes(
          (getUUID() + ":noise:station:" + slot).getBytes(StandardCharsets.UTF_8));
    }
    return stationGeometryOwners[slot];
  }

  protected long stationSightPoseKey(int slot) {
    float yaw;
    float pitch;
    switch (slot) {
      case 0 -> { yaw = getAutocannonLYaw(); pitch = getAutocannonLPitch(); }
      case 1 -> { yaw = getAutocannonRYaw(); pitch = getAutocannonRPitch(); }
      case 2 -> { yaw = getMissilePodLYaw(); pitch = getMissilePodLPitch(); }
      case 3 -> { yaw = getMissilePodRYaw(); pitch = getMissilePodRPitch(); }
      case 4, 5 -> { yaw = getCoreYaw(); pitch = 0.0F; }
      case 6 -> { yaw = getCoreYaw(); pitch = getMainGunPitch(); }
      default -> throw new IllegalArgumentException("unknown Noise station");
    }
    return ((long) Math.round(yaw * 2.0F) << 32)
        ^ (Math.round(pitch * 2.0F) & 0xffffffffL);
  }

  protected void releaseStationGeometryOwners() {
    if (stationGeometryLevel != null) {
      for (int slot = 0; slot < stationGeometryOwners.length; slot++) {
        if (stationGeometryOwners[slot] != null) {
          DnvTargetingBudget.releaseGeometryOwner(stationGeometryLevel, stationGeometryOwners[slot]);
          stationGeometryOwners[slot] = null;
        }
      }
    }
    stationGeometryLevel = null;
  }
}
