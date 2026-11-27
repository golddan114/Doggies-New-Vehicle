package com.liucodex.doggiesnewvehicle.entity;

import com.liucodex.doggiesnewvehicle.core.DnvTurretTargetMemory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Public registry boundary for the Jingzhe turret. */
public final class JingzheTurretEntity extends AbstractJingzheTurretEffects
    implements com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor,
        com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle {
  @Override public float ordinaryArmorReduction() { return 0.20F; }
  public JingzheTurretEntity(EntityType<? extends JingzheTurretEntity> type, Level level) {
    super(type, level);
  }

  protected void updateClientOrbitInterpolation() {
    float synced = getEntityData().get(ORBIT_ANGLE);
    if (!clientOrbitInitialized) {
      clientPreviousOrbitAngle = synced;
      clientCurrentOrbitAngle = synced;
      clientOrbitInitialized = true;
      return;
    }
    clientPreviousOrbitAngle = clientCurrentOrbitAngle;
    clientCurrentOrbitAngle = synced;
  }

  protected void updateClientVisualInterpolation() {
    updateClientOrbitInterpolation();
    int syncedPhase = getEntityData().get(DEPLOY_PHASE);
    float syncedPhaseTick = getEntityData().get(PHASE_TICK);
    if (!clientAnimationInitialized || clientVisualPhase != syncedPhase) {
      clientVisualPhase = syncedPhase;
      clientPreviousPhaseTick = syncedPhaseTick;
      clientCurrentPhaseTick = syncedPhaseTick;
      for (int module = 0; module < MODULE_COUNT; module++) {
        float charge = unpackModuleCharge(module);
        clientPreviousModuleCharge[module] = charge;
        clientCurrentModuleCharge[module] = charge;
      }
      clientAnimationInitialized = true;
      return;
    }

    clientPreviousPhaseTick = clientCurrentPhaseTick;
    clientCurrentPhaseTick = syncedPhaseTick;
    for (int module = 0; module < MODULE_COUNT; module++) {
      clientPreviousModuleCharge[module] = clientCurrentModuleCharge[module];
      clientCurrentModuleCharge[module] = unpackModuleCharge(module);
    }
  }

  protected boolean advancePhaseTick(int duration) {
    int next = getEntityData().get(PHASE_TICK) + 1;
    getEntityData().set(PHASE_TICK, next);
    return next >= duration;
  }

  protected float phaseProgress(int duration) {
    return Mth.clamp(getEntityData().get(PHASE_TICK) / (float) duration, 0.0F, 1.0F);
  }

  protected void setPhase(int phase) {
    setPhaseAndTick(phase, 0);
  }

  protected void setPhaseAndTick(int phase, int phaseTick) {
    getEntityData().set(DEPLOY_PHASE, phase);
    getEntityData().set(PHASE_TICK, Math.max(0, phaseTick));
  }

  protected void addOrbitAngle(float amount) {
    float angle = Mth.wrapDegrees(getEntityData().get(ORBIT_ANGLE) + amount);
    getEntityData().set(ORBIT_ANGLE, angle);
  }

  public boolean isTurretActivated() {
    return getEntityData().get(ACTIVE);
  }

  public boolean hasOperationalPower() {
    return getEnergy() > 0;
  }

  public int getDeployPhase() {
    return getEntityData().get(DEPLOY_PHASE);
  }

  public float getVisualOrbitAngle(float partialTicks) {
    if (!clientOrbitInitialized) {
      return getEntityData().get(ORBIT_ANGLE);
    }
    return Mth.rotLerp(partialTicks, clientPreviousOrbitAngle, clientCurrentOrbitAngle);
  }

  public float getKeyDropProgress(float partialTicks) {
    int phase = getVisualDeployPhase();
    if (phase == PHASE_KEY_DOWN) {
      return smoothMotion(interpolatedPhaseProgress(KEY_TICKS, partialTicks));
    }
    if (phase >= PHASE_CORE_HOLD && phase <= PHASE_INWARD) {
      return 1.0F;
    }
    if (phase == PHASE_KEY_UP) {
      return 1.0F - smoothMotion(interpolatedPhaseProgress(KEY_TICKS, partialTicks));
    }
    return 0.0F;
  }

  public float getScrollOutwardProgress(float partialTicks) {
    int phase = getVisualDeployPhase();
    if (phase == PHASE_OUTWARD) {
      return smoothMotion(interpolatedPhaseProgress(OUTWARD_TICKS, partialTicks));
    }
    if (phase >= PHASE_UPWARD && phase <= PHASE_DOWN) {
      return 1.0F;
    }
    if (phase == PHASE_INWARD) {
      return 1.0F - smoothMotion(interpolatedPhaseProgress(OUTWARD_TICKS, partialTicks));
    }
    return 0.0F;
  }

  public float getScrollUpwardProgress(float partialTicks) {
    int phase = getVisualDeployPhase();
    if (phase == PHASE_UPWARD) {
      return smoothMotion(interpolatedPhaseProgress(UPWARD_TICKS, partialTicks));
    }
    if (phase >= PHASE_ACCEL && phase <= PHASE_DECEL) {
      return 1.0F;
    }
    if (phase == PHASE_DOWN) {
      return 1.0F - smoothMotion(interpolatedPhaseProgress(UPWARD_TICKS, partialTicks));
    }
    return 0.0F;
  }

  public boolean isCombatDeployed() {
    return getDeployPhase() == PHASE_ACTIVE;
  }

  public float getModuleChargeRatio(int module) {
    return getModuleChargeRatio(module, 1.0F);
  }

  public float getModuleChargeRatio(int module, float partialTicks) {
    if (module < 0 || module >= MODULE_COUNT) {
      return 0.0F;
    }
    if (level().isClientSide && clientAnimationInitialized) {
      return Mth.clamp(
          Mth.lerp(
              partialTicks, clientPreviousModuleCharge[module], clientCurrentModuleCharge[module]),
          0.0F,
          1.0F);
    }
    return unpackModuleCharge(module);
  }

  public float getCoreEnergyRatio() {
    return getMaxEnergy() <= 0 ? 0.0F : Mth.clamp(getEnergy() / (float) getMaxEnergy(), 0.0F, 1.0F);
  }

  protected float interpolatedPhaseProgress(int duration, float partialTicks) {
    if (level().isClientSide && clientAnimationInitialized) {
      return Mth.clamp(
          Mth.lerp(partialTicks, clientPreviousPhaseTick, clientCurrentPhaseTick) / duration,
          0.0F,
          1.0F);
    }
    return Mth.clamp(getEntityData().get(PHASE_TICK) / (float) duration, 0.0F, 1.0F);
  }

  protected int getVisualDeployPhase() {
    return level().isClientSide && clientAnimationInitialized
        ? clientVisualPhase
        : getDeployPhase();
  }

  protected float unpackModuleCharge(int module) {
    return (getEntityData().get(MODULE_DATA.get(module)) & 1023) / 1000.0F;
  }

  protected static float smoothMotion(float progress) {
    float clamped = Mth.clamp(progress, 0.0F, 1.0F);
    return clamped * clamped * (3.0F - 2.0F * clamped);
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.m_7380_(tag);
    tag.putInt("JingzheDeployPhase", getDeployPhase());
    tag.putInt("JingzhePhaseTick", getEntityData().get(PHASE_TICK));
    tag.putFloat("JingzheOrbitAngle", getEntityData().get(ORBIT_ANGLE));
    tag.putInt("JingzheNextReload", nextReloadIndex);
    for (int module = 0; module < MODULE_COUNT; module++) {
      CompoundTag moduleTag = new CompoundTag();
      moduleTag.putInt("State", moduleState[module]);
      moduleTag.putInt("Timer", moduleTimer[module]);
      moduleTag.putInt("Charge", moduleCharge[module]);
      moduleTag.putBoolean("EnergyReserved", moduleEnergyReserved[module]);
      tag.put("JingzheModule" + module, moduleTag);
    }
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    invalidateExecution();
    invalidateTargetMemories(DnvTurretTargetMemory.InvalidationReason.ENTITY_INVALIDATED);
    targetCandidates.clear();
    releaseModuleGeometryOwners();
    candidateProbeCursor = 0;
    super.m_7378_(tag);
    getEntityData().set(DEPLOY_PHASE, Mth.clamp(tag.getInt("JingzheDeployPhase"), 0, PHASE_KEY_UP));
    getEntityData().set(PHASE_TICK, Math.max(0, tag.getInt("JingzhePhaseTick")));
    getEntityData().set(ORBIT_ANGLE, Mth.wrapDegrees(tag.getFloat("JingzheOrbitAngle")));
    nextReloadIndex = Math.floorMod(tag.getInt("JingzheNextReload"), MODULE_COUNT);
    // Release cadence is transient; absolute entity tick values are unsafe
    // across a save/reload because tickCount is not persisted as this timer.
    lastLockStartTick = Integer.MIN_VALUE / 2;
    lastReleaseTick = Integer.MIN_VALUE / 2;
    pendingSecondaryStrikes.clear();
    groundFlameEffects.clear();
    for (int module = 0; module < MODULE_COUNT; module++) {
      CompoundTag moduleTag = tag.getCompound("JingzheModule" + module);
      int state = Mth.clamp(moduleTag.getInt("State"), MODULE_EMPTY, MODULE_FADING);
      int timer = Math.max(0, moduleTag.getInt("Timer"));
      int charge = Mth.clamp(moduleTag.getInt("Charge"), 0, 1000);
      boolean reserved = moduleTag.getBoolean("EnergyReserved");
      if (state == MODULE_LOCKING || state == MODULE_WINDUP) {
        state = MODULE_READY;
        timer = 0;
        charge = 1000;
      }
      if (state == MODULE_RELOADING && !reserved) {
        state = MODULE_EMPTY;
        timer = 0;
        charge = 0;
      }
      moduleState[module] = state;
      moduleTimer[module] = timer;
      moduleCharge[module] = charge;
      moduleEnergyReserved[module] = state == MODULE_RELOADING && reserved;
      moduleTarget[module] = NO_TARGET;
      clearModuleValidation(module);
      publishModule(module);
    }
  }

    @Override public void remove(RemovalReason reason) {
        releaseExternalState(reason);
        super.remove(reason);
    }

    @Override public void releaseExternalState(RemovalReason reason) {
    targetCandidates.clear();
    releaseModuleGeometryOwners();
    invalidateExecution();
    }


}
