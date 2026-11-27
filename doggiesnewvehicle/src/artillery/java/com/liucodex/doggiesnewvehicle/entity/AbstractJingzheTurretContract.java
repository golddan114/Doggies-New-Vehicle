package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.data.vehicle.subdata.SeekInfo;
import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Compile-time contract between the Jingzhe turret runtime layers. */
abstract class AbstractJingzheTurretContract extends AutoAimableEntity {
  protected AbstractJingzheTurretContract(
      EntityType<? extends JingzheTurretEntity> type, Level level) {
    super(type, level);
  }

  @Override
  public final void vehicleShoot(LivingEntity living, String weaponName, Vec3 targetPos) {
    // Existing autonomous units have no validated remote weapon-station lease.
  }

  @Override
  public final void vehicleShoot(LivingEntity living, UUID targetUuid, Vec3 targetPos) {
    // Existing autonomous units have no validated remote weapon-station lease.
  }

  @Override
  public final void vehicleShoot(
      LivingEntity living, String weaponName, UUID targetUuid, Vec3 targetPos) {
    // Existing autonomous units have no validated remote weapon-station lease.
  }

  protected abstract void tickCombatSystems();

  protected abstract void tickDeployment();

  protected abstract void tickRetraction();

  protected abstract void beginRetractionFromCurrentPhase(int phase);

  protected abstract void tickModule(int module, SeekInfo seekInfo);

  protected abstract void startAffordableReloads();

  protected abstract void refreshSharedCandidates(SeekInfo seekInfo);

  protected abstract boolean needsTargetScan();

  protected abstract Entity acquireTarget(int module, SeekInfo seekInfo);

  protected abstract boolean isCandidate(Entity target, Vec3 origin, SeekInfo seekInfo);

  protected abstract boolean isAttackTargetValid(Entity target, SeekInfo seekInfo);

  protected abstract boolean isModuleTargetValid(int module, Entity target, SeekInfo seekInfo);

  protected abstract boolean isCheapModuleTargetValid(Entity target, SeekInfo seekInfo);

  protected abstract void initializeModuleValidation(int module, Entity target);

  public abstract boolean hasVerticalDropPath(Entity target);

  protected abstract boolean releaseAttack(int module, Entity target);

  protected abstract void invalidateExecution();

  protected abstract void invalidateModuleExecution(int module);

  protected abstract boolean isPvpProtected(Entity target);

  protected abstract void spawnModuleSkyDischarge(ServerLevel serverLevel, Vec3 origin);

  protected abstract void applyImpactAreaDamage(ServerLevel serverLevel, Vec3 center);

  protected abstract void spawnModuleWindupParticle(int module, int windupTick);

  protected abstract Vec3 moduleWorldPosition(int module, double radialExtension, double upward);

  protected abstract Vec3 moduleFxUpWorldPosition(int module, double radialExtension, double upward);

  protected abstract void playModuleLockSound(int module);

  protected abstract Entity resolveModuleTarget(int module);

  protected abstract void resetModuleToReady(int module);

  protected abstract void clearTransientTargets();

  protected abstract void clearCombatStateForWreck(ServerLevel serverLevel);

  protected abstract void tickPendingSecondaryStrikes();

  protected abstract void tickGroundFlameEffects();

  protected abstract void clearModuleValidation(int module);

  protected abstract void publishModule(int module);

  protected abstract void updateClientOrbitInterpolation();

  protected abstract void updateClientVisualInterpolation();

  protected abstract boolean advancePhaseTick(int duration);

  protected abstract float phaseProgress(int duration);

  protected abstract void setPhase(int phase);

  protected abstract void setPhaseAndTick(int phase, int phaseTick);

  protected abstract void addOrbitAngle(float amount);

  public abstract boolean isTurretActivated();

  public abstract boolean hasOperationalPower();

  public abstract int getDeployPhase();

  public abstract float getVisualOrbitAngle(float partialTicks);

  public abstract float getKeyDropProgress(float partialTicks);

  public abstract float getScrollOutwardProgress(float partialTicks);

  public abstract float getScrollUpwardProgress(float partialTicks);

  public abstract boolean isCombatDeployed();

  public abstract float getModuleChargeRatio(int module);

  public abstract float getModuleChargeRatio(int module, float partialTicks);

  public abstract float getCoreEnergyRatio();

  protected abstract float interpolatedPhaseProgress(int duration, float partialTicks);

  protected abstract int getVisualDeployPhase();

  protected abstract float unpackModuleCharge(int module);
}
