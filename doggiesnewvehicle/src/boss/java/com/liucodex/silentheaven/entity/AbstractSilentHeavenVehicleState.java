package com.liucodex.silentheaven.entity;

import com.liucodex.doggiesnewvehicle.boss.DnvBossRunningGear;

import com.liucodex.doggiesnewvehicle.entity.DnvVehiclePresentation;
import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.atsuishio.superbwarfare.entity.vehicle.damage.DamageModifier;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore;
import com.liucodex.doggiesnewvehicle.compat.DnvObstructionPolicy;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetCandidateCache;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import com.liucodex.doggiesnewvehicle.compat.DnvSightObservation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 * the sole production Silent Heaven entity.
 */
abstract class AbstractSilentHeavenVehicleState extends AbstractSilentHeavenVehicleContract
    implements DnvVehiclePresentation {
  public static final float MAX_HEALTH = SilentHeavenPhaseHealth.FIRST_CAPACITY;
  protected static final float PHASE_THRESHOLD = 0.0F;
  // SBW treats zero vehicle health as destruction during its next tick.
  protected static final float TRANSITION_SURVIVAL_HEALTH = SilentHeavenPhaseHealth.TRANSITION_SURVIVAL;
  protected static final float PHASE_TWO_START_HEALTH = SilentHeavenPhaseHealth.SECOND_CAPACITY;
  protected static final int TRANSITION_TICKS = 370;
  protected static final int TRANSITION_HEAL_START_TICK = 80;
  protected static final int TRANSITION_HALO_START_TICK = 330;
  protected static final int PHASE_ONE = 0;
  protected static final int PHASE_TRANSITION = 1;
  protected static final int PHASE_TWO = 2;
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
  protected static final float PHASE_TWO_HULL_TURN_MULTIPLIER = 1.50F;
  protected static final float TRACK_SHOE_COUNT = DnvBossRunningGear.PHASE_PERIOD;
  protected static final double TRACK_SHOE_SPACING = DnvBossRunningGear.PHASE_DISTANCE;
  protected static final double TRACK_HALF_WIDTH = DnvBossRunningGear.HALF_WIDTH;
  protected static final float ROAD_WHEEL_DEGREES_PER_TRACK_PHASE =
      DnvBossRunningGear.WHEEL_DEGREES_PER_PHASE;
  protected static final double MAX_FORWARD_SPEED = 0.220D;
  protected static final int MAIN_RELOAD = 100;
  protected static final double MAIN_MIN_RANGE = 8.0D;
  protected static final double MAIN_MAX_RANGE = 60.0D;
  protected static final double MAIN_VELOCITY = 5.0D;
  protected static final double MAIN_GRAVITY = 0.06D;
  protected static final float MAIN_DAMAGE = 220.0F;
  protected static final float MAIN_EXPLOSION_DAMAGE = 40.0F;
  protected static final float MAIN_EXPLOSION_RADIUS = 10.0F;
  protected static final int AUTOCANNON_MAGAZINE = 15;
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
  protected static final int MISSILE_MAGAZINE = 1;
  protected static final int MISSILE_RELOAD = 160;
  protected static final double MISSILE_MIN_RANGE = 8.0D;
  protected static final double MISSILE_MAX_RANGE = 40.0D;
  protected static final float MISSILE_TURN_SPEED = 2.0F;
  protected static final float SIDE_ROCKET_DAMAGE = 80.0F;
  protected static final float SIDE_ROCKET_EXPLOSION_DAMAGE = 45.0F;
  protected static final float SIDE_ROCKET_EXPLOSION_RADIUS = 2.0F;
  protected static final float SIDE_ROCKET_VELOCITY = 3.2F;
  protected static final float SIDE_ROCKET_GRAVITY = 0.015F;
  protected static final int VLS_MAGAZINE = 2;
  protected static final int VLS_INTERVAL = 20;
  protected static final int VLS_RELOAD = 400;
  protected static final int VLS_LOCK_TICKS = 20;
  protected static final double VLS_MIN_RANGE = 18.0D;
  protected static final double VLS_MAX_RANGE = 72.0D;
  protected static final float MISSILE_DAMAGE = 160.0F;
  protected static final float MISSILE_EXPLOSION_DAMAGE = 160.0F;
  protected static final float MISSILE_EXPLOSION_RADIUS = 4.0F;
  protected static final float MISSILE_INITIAL_VELOCITY = 2.0F;
  protected static final int MAX_ACTIVE_DRONES = 2;
  protected static final int DRONE_LAUNCH_COOLDOWN = 200;
  protected static final float DRONE_INITIAL_VELOCITY = 1.2F;
  protected static final int HALO_MAGAZINE = 3;
  protected static final int HALO_INTERVAL = 20;
  protected static final int HALO_RELOAD_TICKS = 160;
  protected static final int HALO_STRIKE_DELAY = 60;
  protected static final float HALO_DIRECT_DAMAGE = 20.0F;
  protected static final float HALO_EXPLOSION_DAMAGE = 200.0F;
  protected static final float HALO_EXPLOSION_RADIUS = 5.0F;
  protected static final double HALO_MIN_RANGE = 2.0D;
  protected static final double HALO_MAX_RANGE = 80.0D;
  protected static final int SECONDARY_SETTLED_TICKS = 3;
  protected static final int OBSTRUCTED_TELEPORT_TICKS = DnvObstructionPolicy.TELEPORT_AFTER_TICKS;
  protected static final int TELEPORT_COOLDOWN_TICKS = 1_800;
  protected static final int OUT_OF_COMBAT_HEAL_DELAY = 1_200;
  protected static final float OUT_OF_COMBAT_HEAL_PER_TICK = 1.0F;
  protected static final double TELEPORT_MIN_RANGE = 20.0D;
  protected static final double TELEPORT_MAX_RANGE = 30.0D;
  protected static final double EXCESSIVE_HEIGHT_DIFFERENCE = 16.0D;
  protected static final int STARTUP_READY_VOICE_DELAY = 36;
  protected static final int GENERAL_VOICE_COOLDOWN = 50;
  protected static final int ATTACK_VOICE_COOLDOWN = 160;
  protected static final int DAMAGE_VOICE_COOLDOWN = 100;
  protected static final int TARGET_ACQUIRED_VOICE_COOLDOWN = 240;
  protected static final int DESTRUCTION_SEQUENCE_TICKS = 60;
  protected static final int ENTRANCE_EFFECT_TICKS = 60;
  protected static final double PRESENTATION_RANGE = 128.0D;
  protected static final int PENDING_VOICE_NONE = 0;
  protected static final int PENDING_VOICE_MOVEMENT = 1;
  protected static final int PENDING_VOICE_OUT_OF_RANGE = 2;
  protected static final int PENDING_VOICE_TARGET_ACQUIRED = 3;
  protected static final DamageModifier IDENTITY_DAMAGE_MODIFIER = new DamageModifier();
  protected static final String OWNED_ENTITY_TAG = "silent_heaven_owned";
  protected static final String OWNER_UUID_TAG = "SilentHeavenOwner";
  protected static final int OWNED_PROJECTILE_RECOVERY_INTERVAL = 100;
  protected static final Vec3 AUTOCANNON_L_YAW_PIVOT_LOCAL =
      new Vec3(1.705815D, 1.528245D, 1.050607D);
  protected static final Vec3 AUTOCANNON_R_YAW_PIVOT_LOCAL =
      new Vec3(-1.705815D, 1.528245D, 1.050607D);
  protected static final Vec3 AUTOCANNON_L_PITCH_PIVOT_LOCAL =
      new Vec3(1.705815D, 1.709482D, 1.330202D);
  protected static final Vec3 AUTOCANNON_R_PITCH_PIVOT_LOCAL =
      new Vec3(-1.705815D, 1.709482D, 1.330202D);
  protected static final Vec3 AUTOCANNON_L_MUZZLE_LOCAL = new Vec3(1.705815D, 1.709482D, 1.887713D);
  protected static final Vec3 AUTOCANNON_R_MUZZLE_LOCAL = new Vec3(-1.705815D, 1.709482D, 1.887713D);
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

  protected static final EntityDataAccessor<Integer> PHASE =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Integer> TRANSITION_TICK =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Integer> ENTRANCE_TICKS =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Integer> TELEPORT_TICKS =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Integer> DESTRUCTION_TICKS =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Float> CORE_YAW =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> MAIN_GUN_PITCH =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> HALO_ANGLE =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> AUTOCANNON_L_YAW =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> AUTOCANNON_L_PITCH =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> AUTOCANNON_R_YAW =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> AUTOCANNON_R_PITCH =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> MISSILE_POD_L_YAW =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> MISSILE_POD_L_PITCH =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> MISSILE_POD_R_YAW =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> MISSILE_POD_R_PITCH =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Integer> HALO_AMMO =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Integer> HALO_RELOAD =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Float> DRIVE_INTENSITY =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> TRACK_PHASE_L =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> TRACK_PHASE_R =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> TRACK_SPEED_L =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> TRACK_SPEED_R =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> ROAD_WHEEL_ANGLE_L =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Float> ROAD_WHEEL_ANGLE_R =
      SynchedEntityData.defineId(SilentHeavenVehicleEntity.class, EntityDataSerializers.FLOAT);

  protected final ServerBossEvent bossEvent =
      new ServerBossEvent(
          Component.translatable("bossbar.silent_heaven.guardian"),
          BossEvent.BossBarColor.YELLOW,
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
  protected final List<PendingStrike> pendingStrikes = new ArrayList<>();
  protected final List<CannonShellEntity> fallingWarheads = new ArrayList<>();
  // Five secondaries, main-gun aim and obstruction recovery each queue independently.
  private final UUID[] stationGeometryOwners = new UUID[8];
  @Nullable private ServerLevel stationGeometryLevel;
  protected int lastWarheadRecoveryTick = DnvTargetingBudget.NEVER_SCANNED;

  @Nullable protected UUID combatTargetId;
  @Nullable protected UUID haloTargetId;
  @Nullable protected Vec3 patrolDestination;
  protected int patrolCooldown = 40;
  protected int patrolTravelTicks;
  protected float requestedThrottle;
  protected float requestedSteering;
  protected int leftMainReload;
  protected int rightMainReload = 15;
  protected int mainSettledTicks;
  protected Vec3 mainAim = Vec3.ZERO;
  @Nullable protected UUID mainAimTargetId;
  protected int mainAimTick = Integer.MIN_VALUE;
  protected float idleCoreYawOffset;
  protected int idleCorePauseTicks = IDLE_CORE_INITIAL_PAUSE;
  protected int vlsTubeIndex;
  protected int droneLaunchCooldown = 80;
  protected boolean nextDroneLaunchLeft = true;
  protected int haloShotCooldown;
  protected int obstructedTargetTicks;
  protected int lastAttackLineTick = DnvTargetingBudget.NEVER_SCANNED;
  protected int nextObstructionRetargetTick;
  protected int teleportCooldownTicks;
  @Nullable protected Vec3 teleportDestination;
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
  protected int pendingVoice;
  protected boolean wasMoving;
  protected boolean coreOrientationInitialized;
  protected boolean holdPosition;
  protected boolean entranceShown;
  protected boolean wreckCombatStateCleared;
  // The native constructor can query max health before our phase data is defined.
  protected boolean phaseDataReady;

  protected AbstractSilentHeavenVehicleState(
      EntityType<? extends SilentHeavenVehicleEntity> type, Level level) {
    super(type, level);
  }

  /** Keep the boss invariant independent of SBW's 50-health fallback data. */
  @Override
  public float getMaxHealth() {
    return phaseDataReady ? SilentHeavenPhaseHealth.capacity(getPhase()) : MAX_HEALTH;
  }

  @Override
  protected void defineSynchedData() {
    // SBW 0.8.9 is an obfuscated local dependency in this workspace.
    // Its mapped Entity override therefore keeps the runtime method name
    // on the development compile classpath (same bridge used by our
    // Changfeng and Jingzhe entities).
    super.m_8097_();
    entityData.define(PHASE, PHASE_ONE);
    entityData.define(TRANSITION_TICK, 0);
    entityData.define(ENTRANCE_TICKS, ENTRANCE_EFFECT_TICKS);
    entityData.define(TELEPORT_TICKS, 0);
    entityData.define(DESTRUCTION_TICKS, 0);
    entityData.define(CORE_YAW, 0.0F);
    entityData.define(MAIN_GUN_PITCH, 0.0F);
    entityData.define(HALO_ANGLE, 0.0F);
    entityData.define(AUTOCANNON_L_YAW, 0.0F);
    entityData.define(AUTOCANNON_L_PITCH, 0.0F);
    entityData.define(AUTOCANNON_R_YAW, 0.0F);
    entityData.define(AUTOCANNON_R_PITCH, 0.0F);
    entityData.define(MISSILE_POD_L_YAW, 0.0F);
    entityData.define(MISSILE_POD_L_PITCH, 0.0F);
    entityData.define(MISSILE_POD_R_YAW, 0.0F);
    entityData.define(MISSILE_POD_R_PITCH, 0.0F);
    entityData.define(HALO_AMMO, HALO_MAGAZINE);
    entityData.define(HALO_RELOAD, 0);
    entityData.define(DRIVE_INTENSITY, 0.0F);
    entityData.define(TRACK_PHASE_L, 0.0F);
    entityData.define(TRACK_PHASE_R, 0.0F);
    entityData.define(TRACK_SPEED_L, 0.0F);
    entityData.define(TRACK_SPEED_R, 0.0F);
    entityData.define(ROAD_WHEEL_ANGLE_L, 0.0F);
    entityData.define(ROAD_WHEEL_ANGLE_R, 0.0F);
    phaseDataReady = true;
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

  protected static final class PendingStrike {
    final Vec3 impact;
    int ticks;

    PendingStrike(Vec3 impact, int ticks) {
      this.impact = impact;
      this.ticks = ticks;
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
      cadenceIndex = SilentHeavenAutocannonCadence.normalizeIndex(initialCadenceIndex);
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
      cadenceIndex = SilentHeavenAutocannonCadence.normalizeIndex(initialCadenceIndex);
    }

    int nextFireInterval() {
      int interval = SilentHeavenAutocannonCadence.intervalForIndex(cadenceIndex);
      cadenceIndex = SilentHeavenAutocannonCadence.nextIndex(cadenceIndex);
      return interval;
    }

    @Nullable
    Entity resolveTarget(Vec3 muzzle, boolean left, @Nullable UUID reservedTargetId) {
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
      int slot = left ? 0 : 1;
      long pose = stationSightPoseKey(slot);
      if (sight == null || !sight.reusable(candidateId, muzzle, target.getBoundingBox(),
          target.getEyeY(), pose, tickCount)) {
        if (!(level() instanceof ServerLevel serverLevel)
            || !DnvTargetingBudget.admitGeometryCheck(serverLevel, stationGeometryOwner(slot))) {
          geometryDeferred = true;
          return false;
        }
        geometryDeferred = false;
        sight = new DnvSightObservation(candidateId, muzzle, target.getBoundingBox(),
            target.getEyeY(), pose, tickCount,
            DnvTargetingBudget.nextCheckTick(tickCount, getId(),
                DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL, left ? 80 : 81),
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
          (getUUID() + ":silent-heaven:station:" + slot).getBytes(StandardCharsets.UTF_8));
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
      case 4 -> { yaw = getCoreYaw(); pitch = getPhase(); }
      case 5 -> { yaw = getCoreYaw(); pitch = getMainGunPitch(); }
      default -> throw new IllegalArgumentException("unknown Silent Heaven station");
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
