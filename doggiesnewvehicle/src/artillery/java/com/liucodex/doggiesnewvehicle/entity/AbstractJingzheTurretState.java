package com.liucodex.doggiesnewvehicle.entity;

import com.liucodex.doggiesnewvehicle.compat.DnvTargetCandidateCache;
import com.liucodex.doggiesnewvehicle.compat.DnvSightObservation;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * FE-powered five-scroll lightning tower.
 *
 * <p>Acquisition deliberately ignores terrain. A shot is legal only when the local three-block
 * vertical drop path immediately above the target is clear; distant cave ceilings and the Nether
 * roof therefore do not cause false negatives.
 */
abstract class AbstractJingzheTurretState extends AbstractJingzheTurretContract {
  public static final int MODULE_COUNT = 5;
  public static final float ATTACK_DAMAGE = 225.0F;
  public static final double DROP_SPAWN_HEIGHT = 3.0D;
  public static final int RELOAD_ENERGY_COST = 90_000;

  protected static final int KEY_TICKS = 12;
  protected static final int CORE_HOLD_TICKS = 12;
  protected static final int OUTWARD_TICKS = 30;
  protected static final int UPWARD_TICKS = 30;
  protected static final int ACCEL_TICKS = 36;
  protected static final int DECEL_TICKS = 36;
  protected static final int LOCK_TICKS = 60;
  protected static final int WINDUP_TICKS = 12;
  protected static final int RELEASE_INTERVAL_TICKS = 40;
  protected static final int FADE_TICKS = 20;
  protected static final int RELOAD_TICKS = 100;
  protected static final int TARGET_SCAN_INTERVAL = DnvTargetingBudget.MIN_FULL_SCAN_INTERVAL;
  protected static final int SECONDARY_PROJECTILE_TICKS = 12;
  protected static final int GROUND_FLAME_TICKS = 60;
  protected static final int GROUND_FLAME_EXPAND_TICKS = 24;
  protected static final float FULL_ORBIT_SPEED_DEGREES = 3.0F;
  protected static final float IMPACT_AOE_DAMAGE = 45.0F;
  protected static final double IMPACT_AOE_RADIUS = 3.0D;
  protected static final double TARGET_ORIGIN_HEIGHT = 2.413222D;
  protected static final double MODULE_ORBIT_HEIGHT = 2.639705D;
  protected static final double MODULE_FX_UP_HEIGHT = 3.987857D;
  protected static final double MODULE_BASE_RADIUS = 1.274673D;
  protected static final double MODULE_OUTWARD_DISTANCE = 1.256428D;
  protected static final double MODULE_UPWARD_DISTANCE = 1.256428D;
  protected static final String NO_TARGET = "none";

  protected static final int PHASE_INACTIVE = 0;
  protected static final int PHASE_KEY_DOWN = 1;
  protected static final int PHASE_CORE_HOLD = 2;
  protected static final int PHASE_OUTWARD = 3;
  protected static final int PHASE_UPWARD = 4;
  protected static final int PHASE_ACCEL = 5;
  protected static final int PHASE_ACTIVE = 6;
  protected static final int PHASE_DECEL = 7;
  protected static final int PHASE_DOWN = 8;
  protected static final int PHASE_INWARD = 9;
  protected static final int PHASE_KEY_UP = 10;

  protected static final int MODULE_EMPTY = 0;
  protected static final int MODULE_RELOADING = 1;
  protected static final int MODULE_READY = 2;
  protected static final int MODULE_LOCKING = 3;
  protected static final int MODULE_WINDUP = 4;
  protected static final int MODULE_FADING = 5;

  protected static final EntityDataAccessor<Integer> DEPLOY_PHASE =
      SynchedEntityData.defineId(JingzheTurretEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Integer> PHASE_TICK =
      SynchedEntityData.defineId(JingzheTurretEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Float> ORBIT_ANGLE =
      SynchedEntityData.defineId(JingzheTurretEntity.class, EntityDataSerializers.FLOAT);
  protected static final EntityDataAccessor<Integer> MODULE_0 =
      SynchedEntityData.defineId(JingzheTurretEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Integer> MODULE_1 =
      SynchedEntityData.defineId(JingzheTurretEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Integer> MODULE_2 =
      SynchedEntityData.defineId(JingzheTurretEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Integer> MODULE_3 =
      SynchedEntityData.defineId(JingzheTurretEntity.class, EntityDataSerializers.INT);
  protected static final EntityDataAccessor<Integer> MODULE_4 =
      SynchedEntityData.defineId(JingzheTurretEntity.class, EntityDataSerializers.INT);
  protected static final List<EntityDataAccessor<Integer>> MODULE_DATA =
      List.of(MODULE_0, MODULE_1, MODULE_2, MODULE_3, MODULE_4);

  protected final int[] moduleState = new int[MODULE_COUNT];
  protected final int[] moduleTimer = new int[MODULE_COUNT];
  protected final int[] moduleCharge = new int[MODULE_COUNT];
  protected final boolean[] moduleEnergyReserved = new boolean[MODULE_COUNT];
  protected final String[] moduleTarget = {NO_TARGET, NO_TARGET, NO_TARGET, NO_TARGET, NO_TARGET};
  protected final String[] moduleValidationTarget = {
    NO_TARGET, NO_TARGET, NO_TARGET, NO_TARGET, NO_TARGET
  };
  protected final int[] nextModuleRelationCheckTick = new int[MODULE_COUNT];
  protected final boolean[] cachedModuleRelationAllowed = new boolean[MODULE_COUNT];
  protected final DnvSightObservation[] moduleSight = new DnvSightObservation[MODULE_COUNT];
  protected final boolean[] modulePathDeferred = new boolean[MODULE_COUNT];
  private final UUID[] geometryOwners = new UUID[MODULE_COUNT];
  private ServerLevel geometryLevel;
  protected final DnvTargetCandidateCache targetCandidates =
      new DnvTargetCandidateCache(TARGET_SCAN_INTERVAL);
  protected final List<SecondaryStrike> pendingSecondaryStrikes = new ArrayList<>();
  protected final List<GroundFlameEffect> groundFlameEffects = new ArrayList<>();
  protected int lastLockStartTick = Integer.MIN_VALUE / 2;
  protected int lastReleaseTick = Integer.MIN_VALUE / 2;
  protected int nextReloadIndex;
  protected boolean clientOrbitInitialized;
  protected float clientPreviousOrbitAngle;
  protected float clientCurrentOrbitAngle;
  protected boolean clientAnimationInitialized;
  protected int clientVisualPhase;
  protected float clientPreviousPhaseTick;
  protected float clientCurrentPhaseTick;
  protected final float[] clientPreviousModuleCharge = new float[MODULE_COUNT];
  protected final float[] clientCurrentModuleCharge = new float[MODULE_COUNT];
  protected boolean wreckCombatStateCleared;

  protected AbstractJingzheTurretState(EntityType<? extends JingzheTurretEntity> type, Level level) {
    super(type, level);
  }

  protected UUID moduleGeometryOwner(int module) {
    if (!(level() instanceof ServerLevel serverLevel)) {
      throw new IllegalStateException("module geometry requires a server level");
    }
    if (geometryLevel != serverLevel) {
      releaseModuleGeometryOwners();
      geometryLevel = serverLevel;
    }
    if (geometryOwners[module] == null) {
      geometryOwners[module] = UUID.nameUUIDFromBytes(
          (getUUID() + ":jingzhe:module:" + module).getBytes(StandardCharsets.UTF_8));
    }
    return geometryOwners[module];
  }

  protected void releaseModuleGeometryOwners() {
    if (geometryLevel != null) {
      for (UUID owner : geometryOwners) {
        if (owner != null) DnvTargetingBudget.releaseGeometryOwner(geometryLevel, owner);
      }
    }
    java.util.Arrays.fill(geometryOwners, null);
    geometryLevel = null;
  }

  @Override
  protected void defineSynchedData() {
    // Superb Warfare 0.8.9 retains this runtime name in the local jar.
    super.m_8097_();
    getEntityData().define(DEPLOY_PHASE, PHASE_INACTIVE);
    getEntityData().define(PHASE_TICK, 0);
    getEntityData().define(ORBIT_ANGLE, 0.0F);
    for (EntityDataAccessor<Integer> accessor : MODULE_DATA) {
      getEntityData().define(accessor, packModule(MODULE_EMPTY, 0, 0));
    }
  }

  @Override
  public void tick() {
    super.tick();
    if (level().isClientSide) {
      updateClientVisualInterpolation();
    } else if (isWreck() || getHealth() <= 0.0F) {
      clearCombatStateForWreck((ServerLevel) level());
    } else {
      wreckCombatStateCleared = false;
      tickPendingSecondaryStrikes();
      tickGroundFlameEffects();
      tickDeployment();
      tickCombatSystems();
    }
  }

  @Override
  public void autoAim() {
    // Jingzhe has five independent target controllers. They are advanced
    // from tick() so deployment, charging and lamps never depend on SBW's
    // auto-aim callback cadence.
  }

  protected static int packModule(int state, int timer, int charge) {
    return (Mth.clamp(state, 0, 15) << 20)
        | (Mth.clamp(timer, 0, 1023) << 10)
        | Mth.clamp(charge, 0, 1000);
  }

  protected static boolean isRetracting(int phase) {
    return phase >= PHASE_DECEL && phase <= PHASE_KEY_UP;
  }

  protected static final class GroundFlameEffect {
    final Vec3 center;
    final Set<BlockPos> fireBlocks = new HashSet<>();
    int age;
    int ticks;

    GroundFlameEffect(Vec3 center, int ticks) {
      this.center = center;
      this.ticks = ticks;
    }
  }

  protected static final class SecondaryStrike {
    final String targetId;
    Vec3 impact;
    int ticks;

    SecondaryStrike(String targetId, Vec3 impact, int ticks) {
      this.targetId = targetId;
      this.impact = impact;
      this.ticks = ticks;
    }
  }}
