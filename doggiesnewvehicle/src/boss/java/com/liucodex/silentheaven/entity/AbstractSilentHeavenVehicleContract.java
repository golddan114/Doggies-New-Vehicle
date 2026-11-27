package com.liucodex.silentheaven.entity;

import com.liucodex.doggiesnewvehicle.core.DnvLazyCandidateFrame;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.damage.DamageModifier;
import com.liucodex.doggiesnewvehicle.entity.DnvVehicleCombatRules;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/** Compile-time contract between the Silent Heaven vehicle runtime layers. */
abstract class AbstractSilentHeavenVehicleContract extends VehicleEntity {
  @Override
  public final net.minecraft.world.InteractionResult onCrowbarInteract(net.minecraft.world.item.ItemStack stack,
      Player player, net.minecraft.world.InteractionHand hand) {
    return com.liucodex.doggiesnewvehicle.boss.DnvBossInteraction.rejectRetrieval(player);
  }

  protected AbstractSilentHeavenVehicleContract(
      EntityType<? extends SilentHeavenVehicleEntity> type, Level level) {
    super(type, level);
  }

  @Override
  public final void vehicleShoot(
      @Nullable LivingEntity living, @Nullable UUID targetUuid, @Nullable Vec3 targetPos) {
    // Existing autonomous units have no validated remote weapon-station lease.
  }

  @Override
  public final void vehicleShoot(
      @Nullable LivingEntity living,
      String weaponName,
      @Nullable UUID targetUuid,
      @Nullable Vec3 targetPos) {
    // Existing autonomous units have no validated remote weapon-station lease.
  }

  protected abstract void clearCombatStateForWreck();

  protected abstract void tickDestructionSequence();

  protected abstract void lockForEntrance();

  protected abstract void lockForTransition();

  protected abstract Entity resolveOrAcquireTarget();

  protected abstract Entity resolveCurrentTarget();

  protected abstract boolean isCheapTargetValid(@Nullable Entity entity);

  protected abstract boolean isAcquisitionTarget(@Nullable Entity entity);

  protected abstract boolean isTrackedTargetValid(@Nullable Entity entity);

  protected abstract void updateAutonomousControls(@Nullable Entity target);

  protected abstract void tickVoiceState();

  protected abstract void queueVoice(int voice);

  protected abstract void queueTargetAcquiredVoice();

  protected abstract void playTargetAcquiredVoice();

  protected abstract void playMovementVoice();

  protected abstract void tryPlayAttackVoice();

  protected abstract void tryPlayDamageVoice();

  protected abstract void tickCombatRecovery(@Nullable Entity target);

  protected abstract void tickObstructionTeleport(@Nullable Entity target);

  protected abstract boolean hasUsableCurrentAttackLine(Entity target);

  protected abstract boolean trySwitchObstructedTarget(Entity current);

  protected abstract void cancelTeleportSequence();

  protected abstract void resetObstructionTracking();

  protected abstract int obstructionRecoveryTicks();

  protected abstract boolean hasAnyAttackLine(Entity target);

  protected abstract boolean hasCachedAttackLine(Entity target);

  protected abstract void spawnTeleportChargeParticles();

  protected abstract Vec3 findTeleportDestinationNearTarget(Entity target);

  public abstract boolean commandTeleportTo(ServerLevel requestedLevel, Vec3 destination);

  protected abstract boolean beginTeleportSequence(Vec3 destination);

  protected abstract void tickTeleportSequence();

  protected abstract void lockForTeleport();

  protected abstract void performTeleportJump();

  protected abstract void finishTeleportSequence();

  protected abstract void spawnTeleportBurst(ServerLevel serverLevel);

  protected abstract void playEndermanTeleportSound();

  protected abstract void tickEntrancePresentation();

  protected abstract void playPhaseTwoPresentation();

  protected abstract void spawnParticleRing( ServerLevel serverLevel, SimpleParticleType particle, double radius, int count, double yOffset);

  protected abstract void announceBossTitle( Component title, Component subtitle, int fadeIn, int stay, int fadeOut);

  protected abstract boolean isSafeTeleportDestination(ServerLevel level, Vec3 destination);

  protected abstract Vec3 updatePatrolDestination();

  protected abstract double horizontalDistance(Vec3 point);

  public abstract void travel();

  protected abstract void tickCoreAim(@Nullable Entity target);

  protected abstract boolean hasCachedMainLineOfSight(Vec3 muzzle, Entity target);

  protected abstract void tickIdleCoreScan();

  protected abstract void tickMainGuns(@Nullable Entity target);

  protected abstract boolean fireMainGun(boolean left, Entity target);

  protected abstract Vec3 mainGunMuzzle(boolean left);

  protected abstract Vec3 firingSolution(Vec3 muzzle, Entity target, double velocity, double gravity);

  protected abstract void prepareSecondaryAllocation(
      DnvLazyCandidateFrame<Entity> frame, boolean candidateScanCompleted);

  protected abstract void tickAutocannons();

  protected abstract void tickMissilePods();

  protected abstract void tickVlsBattery();

  protected abstract boolean fireAutocannon(Vec3 muzzle, Vec3 direction, Entity target);

  protected abstract DamageSource createAutocannonDamageSource(ServerLevel serverLevel);

  protected abstract boolean fireUnguidedSideRocket(Vec3 muzzle, Vec3 direction, Entity target);

  protected abstract boolean fireTrackingMissile(Vec3 muzzle, Vec3 direction, Entity target);

  protected abstract Vec3 chassisPointToWorld(Vec3 local);

  protected abstract Vec3 stationPitchPivotToWorld(Vec3 localYawPivot, Vec3 localPitchPivot, float worldYaw);

  protected abstract Vec3 stationPitchPivotToWorld( Vec3 localYawPivot, Vec3 localPitchPivot, float worldYaw, boolean coreMounted);

  protected abstract Vec3 stationSocketToWorld( Vec3 localYawPivot, Vec3 localPitchPivot, Vec3 localSocket, float worldYaw, float pitch);

  protected abstract Vec3 stationSocketToWorld( Vec3 localYawPivot, Vec3 localPitchPivot, Vec3 localSocket, float worldYaw, float pitch, boolean coreMounted);

  protected abstract Vec3 corePointToWorld(Vec3 local);

  protected abstract boolean hasClearShot(Vec3 from, Entity target);

  protected abstract Optional<Vec3> clearAimPoint(Vec3 from, Entity target);

  protected abstract boolean withinAutocannonArc(Entity target, Vec3 muzzle);

  protected abstract void tickDroneLauncher(@Nullable Entity target);

  protected abstract int countActiveDrones(ServerLevel serverLevel);

  protected abstract void tickHaloWeapon(@Nullable Entity target);

  protected abstract Entity resolveHaloTarget(@Nullable Entity preferredTarget);

  protected abstract boolean canHaloAttack(@Nullable Entity target);

  protected abstract void fireHaloShot(Entity target);

  protected abstract DamageSource createHaloDamageSource(ServerLevel serverLevel);

  protected abstract void tickPendingStrikes();

  protected abstract void stabilizeFallingWarheads();

  protected abstract void recoverFallingWarheads(ServerLevel serverLevel);

  protected abstract void spawnFallingWarhead(ServerLevel serverLevel, Vec3 impact);

  protected abstract void beginTransition();

  protected abstract void tickTransition();

  protected abstract boolean isDamageImmune(DamageSource source);

  protected abstract DnvVehicleCombatRules.DamageCategory classifyDamage(DamageSource source);

  public abstract DamageModifier getDamageModifier();

  protected abstract void updateBossBar();

  protected abstract void clearBossBar();

  protected abstract Component getBossBarName();

  protected abstract void playExternalSound(String id, Vec3 source, float volume, float pitch);

  protected abstract void playVoice(SoundEvent event);

  public abstract boolean engineRunning();

  public abstract boolean isHoldingPosition();

  public abstract void setHoldingPosition(boolean hold);

  public abstract boolean isPushable();

  public abstract void push(double x, double y, double z);

  public abstract void preventStacking();

  public abstract void pushNew(double x, double y, double z);

  public abstract PushReaction getPistonPushReaction();

  public abstract void openCustomInventoryScreen(Player player);

  public abstract Entity asEntity();

  public abstract int getPhase();

  protected abstract void setPhase(int value);

  public abstract int getTransitionTick();

  protected abstract void setTransitionTick(int value);

  public abstract int getEntranceTicks();

  public abstract int getTeleportTicks();

  protected abstract void setTeleportTicks(int value);

  public abstract int getDestructionTicks();

  protected abstract void setDestructionTicks(int value);

  public abstract boolean isTeleporting();

  protected abstract void setEntranceTicks(int value);

  public abstract boolean isEntering();

  public abstract float getCoreYaw();

  protected abstract void setCoreYaw(float value);

  public abstract void alignCoreToHull();

  public abstract int getTeleportCooldownTicks();

  public abstract float getMainGunPitch();

  protected abstract void setMainGunPitch(float value);

  public abstract float getAutocannonLYaw();

  public abstract float getAutocannonLPitch();

  public abstract float getAutocannonRYaw();

  public abstract float getAutocannonRPitch();

  public abstract float getMissilePodLYaw();

  public abstract float getMissilePodLPitch();

  public abstract float getMissilePodRYaw();

  public abstract float getMissilePodRPitch();

  public abstract float getHaloAngle();

  protected abstract void setHaloAngle(float value);

  public abstract boolean isPhaseTwo();

  public abstract boolean isTransitioning();

  public abstract boolean isHaloVisible();

  public abstract float getDriveIntensity();

  public abstract float getTrackPhaseL();

  public abstract float getTrackPhaseR();

  public abstract float getTrackSpeedL();

  public abstract float getTrackSpeedR();

  public abstract float getRoadWheelAngleL();

  public abstract float getRoadWheelAngleR();

  public abstract boolean hasCombatTarget();

  public abstract boolean isCriticalState();

  protected abstract SilentHeavenVehicleEntity self();
}
