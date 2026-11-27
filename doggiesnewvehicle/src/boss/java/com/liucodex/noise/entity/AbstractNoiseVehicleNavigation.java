package com.liucodex.noise.entity;

import com.liucodex.doggiesnewvehicle.compat.DnvSightObservation;

import com.liucodex.doggiesnewvehicle.boss.DnvBossGroundDrive;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvObstructionPolicy;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import com.liucodex.doggiesnewvehicle.core.DnvCandidateProbeOrder;
import com.liucodex.noise.init.ModSounds;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

abstract class AbstractNoiseVehicleNavigation extends AbstractNoiseVehicleLifecycle {
  protected AbstractNoiseVehicleNavigation(
      EntityType<? extends NoiseVehicleEntity> type, Level level) {
    super(type, level);
  }

  protected void tickCombatRecovery(@Nullable Entity target) {
    if (target != null) {
      outOfCombatTicks = 0;
      return;
    }
    outOfCombatTicks = Math.min(Integer.MAX_VALUE - 1, outOfCombatTicks + 1);
    if (outOfCombatTicks >= OUT_OF_COMBAT_HEAL_DELAY && getHealth() < MAX_HEALTH) {
      setHealth(Math.min(MAX_HEALTH, getHealth() + OUT_OF_COMBAT_HEAL_PER_TICK));
    }
  }

  protected void tickObstructionRecovery(@Nullable Entity target) {
    if (holdPosition || target == null) {
      resetObstructionTracking();
      return;
    }
    boolean unableToAttack = !hasUsableCurrentAttackLine(target);
    if (attackLineDeferred) return;
    if (!unableToAttack) {
      lastAttackLineTick = tickCount;
      resetObstructionTracking();
      return;
    }
    obstructedTargetTicks =
        Math.min(DnvObstructionPolicy.TELEPORT_AFTER_TICKS, obstructedTargetTicks + 1);
    int recoveryTicks = obstructionRecoveryTicks();
    if (recoveryTicks == DnvObstructionPolicy.REPOSITION_AFTER_TICKS
        && level() instanceof ServerLevel serverLevel) {
      autonomyCore.acceptFeedback(
          new com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore.ExecutionFeedback(
              serverLevel.getGameTime(),
              "movement",
              java.util.Optional.of(target.getUUID()),
              com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore.ExecutionOutcome.BLOCKED,
              "attack_line_blocked"));
    }
    if (DnvObstructionPolicy.shouldRetarget(recoveryTicks)
        && tickCount >= nextObstructionRetargetTick) {
      nextObstructionRetargetTick =
          DnvTargetingBudget.nextCheckTick(
              tickCount, getId(), DnvObstructionPolicy.RETARGET_INTERVAL_TICKS, 73);
      if (trySwitchObstructedTarget(target)) {
        return;
      }
    }
  }

  protected boolean hasUsableCurrentAttackLine(Entity target) {
    attackLineDeferred = false;
    return !isInWaterOrBubble()
        && Math.abs(target.getBoundingBox().getCenter().y - getBoundingBox().getCenter().y)
            <= EXCESSIVE_HEIGHT_DIFFERENCE
        && hasCachedAttackLine(target);
  }

  protected boolean trySwitchObstructedTarget(Entity current) {
    if (!(level() instanceof ServerLevel serverLevel) || isInWaterOrBubble()) return false;
    // Only one expensive candidate sight task is admitted for this vehicle this tick.
    for (Entity candidate : DnvCandidateProbeOrder.startingAt(
        targetCandidates.candidates(this), obstructionProbeCursor)) {
      if (candidate == current || !isTrackedTargetValid(candidate)
          || Math.abs(candidate.getBoundingBox().getCenter().y - getBoundingBox().getCenter().y)
              > EXCESSIVE_HEIGHT_DIFFERENCE) continue;
      if (!DnvTargetingBudget.admitGeometryCheck(serverLevel, stationGeometryOwner(7))) {
        return false;
      }
      obstructionProbeCursor++;
      if (!hasAnyAttackLine(candidate)) return false;
      combatTargetId = candidate.getUUID();
      attackSight = null;
      mainSight = null;
      resetObstructionTracking();
      lastAttackLineTick = DnvTargetingBudget.NEVER_SCANNED;
      queueTargetAcquiredVoice();
      return true;
    }
    return false;
  }

  protected void resetObstructionTracking() {
    obstructedTargetTicks = 0;
    nextObstructionRetargetTick = 0;
    obstructionProbeCursor = 0;
  }

  protected int obstructionRecoveryTicks() {
    if (lastAttackLineTick == DnvTargetingBudget.NEVER_SCANNED) {
      return obstructedTargetTicks;
    }
    return Math.min(obstructedTargetTicks, Math.max(0, tickCount - lastAttackLineTick));
  }

  protected boolean hasAnyAttackLine(Entity target) {
    double distance = horizontalDistance(target.position());
    if (distance <= MAIN_MAX_RANGE && hasClearShot(position().add(0.0D, 3.58D, 0.0D), target)) {
      return true;
    }
    if (distance <= AUTOCANNON_RANGE) {
      Vec3 left =
          stationSocketToWorld(
              AUTOCANNON_L_YAW_PIVOT_LOCAL,
              AUTOCANNON_L_PITCH_PIVOT_LOCAL,
              AUTOCANNON_L_MUZZLE_LOCAL,
              getAutocannonLYaw(),
              getAutocannonLPitch());
      Vec3 right =
          stationSocketToWorld(
              AUTOCANNON_R_YAW_PIVOT_LOCAL,
              AUTOCANNON_R_PITCH_PIVOT_LOCAL,
              AUTOCANNON_R_MUZZLE_LOCAL,
              getAutocannonRYaw(),
              getAutocannonRPitch());
      if (hasClearShot(left, target) || hasClearShot(right, target)) {
        return true;
      }
    }
    return distance <= VLS_MAX_RANGE
        && hasClearShot(position().add(0.0D, 4.55D, 0.0D), target);
  }

  protected boolean hasCachedAttackLine(Entity target) {
    UUID targetId = target.getUUID();
    Vec3 origin = position().add(0.0D, 3.58D, 0.0D);
    long pose = attackSightPoseKey();
    if (attackSight == null || !attackSight.reusable(targetId, origin,
        target.getBoundingBox(), target.getEyeY(), pose, tickCount)) {
      if (!(level() instanceof ServerLevel serverLevel)
          || !DnvTargetingBudget.admitGeometryCheck(serverLevel, stationGeometryOwner(7))) {
        attackLineDeferred = true;
        return false;
      }
      attackSight = new DnvSightObservation(targetId, origin, target.getBoundingBox(),
          target.getEyeY(), pose, tickCount,
          DnvTargetingBudget.nextCheckTick(tickCount, getId(),
              DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL, 30), hasAnyAttackLine(target));
    }
    attackLineDeferred = false;
    return attackSight.clear();
  }

  private long attackSightPoseKey() {
    return (31L * (31L * stationSightPoseKey(6) + stationSightPoseKey(0)))
        + stationSightPoseKey(1);
  }

  public boolean commandTeleportTo(ServerLevel requestedLevel, Vec3 destination) {
    return false;
  }

  protected void tickEntrancePresentation() {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return;
    }
    if (!entranceShown) {
      entranceShown = true;
      setEntranceTicks(ENTRANCE_EFFECT_TICKS);
      level()
          .playSound(
              null,
              getX(),
              getY() + self().presentedRigOffset(0),
              getZ(),
              ModSounds.DROP_RIG_ENGINE.get(),
              SoundSource.HOSTILE,
              5.0F,
              1.0F);
    }
    if (getEntranceTicks() <= 0) {
      return;
    }

    int elapsed = ENTRANCE_EFFECT_TICKS - getEntranceTicks();
    if (NoiseDropTimeline.isRigVisible(elapsed)) {
      spawnDropRigExhaust(serverLevel, elapsed);
    }
    if (NoiseDropTimeline.shouldActivate(elapsed)) {
      announceBossTitle(
          Component.translatable("title.noise.noise"),
          Component.translatable("subtitle.noise.noise"),
          8,
          55,
          18);
      playVoice(ModSounds.VOICE_SYSTEMS_ONLINE.get());
      startupVoiceStage = 2;
      generalVoiceCooldown = GENERAL_VOICE_COOLDOWN;
    }
    setEntranceTicks(getEntranceTicks() - 1);
  }

  protected void spawnDropRigExhaust(ServerLevel serverLevel, int elapsedTicks) {
    if ((elapsedTicks & 1) != 0) return;
    double rigOffset = self().presentedRigOffset(elapsedTicks);
    double strength = NoiseDropTimeline.exhaustStrength(elapsedTicks);
    int flameCount = strength >= 0.72D ? 2 : 1;
    int nozzleIndex = 0;
    for (double x : new double[] {-3.936225D, 3.936225D}) {
      for (double z : new double[] {-4.303072D, 4.303072D}) {
        Vec3 localExhaust = new Vec3(x, 1.325606D, z).yRot(-getYRot() * Mth.DEG_TO_RAD);
        Vec3 exhaust = position().add(localExhaust).add(0.0D, rigOffset, 0.0D);
        for (int burst = 0; burst < flameCount; burst++) {
          double phase = elapsedTicks * 0.71D + nozzleIndex * 1.57D + burst * 2.31D;
          double radius = 0.018D + burst * 0.012D;
          double velocityY = -(0.18D + strength * 0.58D + burst * 0.025D);
          serverLevel.sendParticles(
              ParticleTypes.FLAME,
              exhaust.x + Math.cos(phase) * radius,
              exhaust.y - 0.18D,
              exhaust.z + Math.sin(phase) * radius,
              0,
              Math.cos(phase) * 0.012D,
              velocityY,
              Math.sin(phase) * 0.012D,
              1.0D);
        }
        if (elapsedTicks % 12 == 0) {
          serverLevel.sendParticles(
              ParticleTypes.SMOKE,
              exhaust.x,
              exhaust.y - 0.40D,
              exhaust.z,
              0,
              0.0D,
              -(0.05D + strength * 0.10D),
              0.0D,
              1.0D);
        }
        nozzleIndex++;
      }
    }
  }

  /** Drop-rig root offset; X/Z and yaw remain unchanged for the full intro. */
  public static double dropRigVerticalOffset(double elapsedTicks) {
    return NoiseDropTimeline.rigVerticalOffset(elapsedTicks);
  }

  /** Suspended vehicle offset used by the client renderer. */
  public static double entranceVehicleVerticalOffset(double elapsedTicks) {
    return NoiseDropTimeline.vehicleVerticalOffset(elapsedTicks);
  }

  protected void announceBossTitle(
      Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return;
    }
    double rangeSqr = PRESENTATION_RANGE * PRESENTATION_RANGE;
    for (ServerPlayer player : serverLevel.players()) {
      if (!player.isSpectator() && distanceToSqr(player) <= rangeSqr) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
      }
    }
  }

  @Nullable
  protected Vec3 updatePatrolDestination() {
    if (patrolDestination != null
        && (horizontalDistance(patrolDestination) < 2.5D || --patrolTravelTicks <= 0)) {
      patrolDestination = null;
      patrolCooldown = 80 + random.nextInt(121);
    }
    if (patrolDestination == null && patrolCooldown-- <= 0) {
      int x = Mth.floor(getX()) + random.nextInt(37) - 18;
      int z = Mth.floor(getZ()) + random.nextInt(37) - 18;
      int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      patrolDestination = Vec3.atBottomCenterOf(new BlockPos(x, y, z));
      patrolTravelTicks = 400;
    }
    return patrolDestination;
  }

  protected double horizontalDistance(Vec3 point) {
    double x = point.x - getX();
    double z = point.z - getZ();
    return Math.sqrt(x * x + z * z);
  }

  /**
   * Replaces SBW's passenger-gated track engine with a virtual driver. All collision, vehicle
   * health, OBB and mass behavior still comes from the real VehicleEntity base.
   */
  @Override
  public void travel() {
    if (level().isClientSide || isRemoved() || getHealth() <= 0.0F) {
      setDeltaMovement(Vec3.ZERO);
      return;
    }

    double startX = getX(), startZ = getZ();
    float startYaw = getYRot();
    float power = driveBrake ? 0.0F
        : DnvBossGroundDrive.powerStep(entityData.get(POWER), requestedThrottle);
    float steering = onGround()
        ? DnvBossGroundDrive.steeringStep(entityData.get(DELTA_ROT), requestedSteering) : 0.0F;
    entityData.set(POWER, power);
    entityData.set(DELTA_ROT, steering);
    float yawDelta = DnvBossGroundDrive.yawStep(steering, power, HULL_TURN_SPEED);
    setYRot(Mth.wrapDegrees(startYaw + yawDelta));
    setXRot(0.0F);

    Vec3 oldMotion = getDeltaMovement();
    var velocity = DnvBossGroundDrive.velocity(oldMotion.x, oldMotion.z, getYRot(),
        onGround() ? power : 0.0F, MAX_FORWARD_SPEED);
    // Keep the heavy vehicle's impulse cap and gravity; Entity.move owns collisions and steps.
    double y = Math.min(0.0D, oldMotion.y) - (isNoGravity() ? 0.0D : 0.08D);
    Vec3 motion = new Vec3(driveBrake ? 0 : velocity.x(), y, driveBrake ? 0 : velocity.z());
    setDeltaMovement(motion);
    move(MoverType.SELF, motion);
    double actualX = getX() - startX, actualZ = getZ() - startZ;
    // Retain only resolved motion. Never reintroduce the velocity rejected by collision.
    setDeltaMovement(actualX, onGround() ? 0.0D : Math.min(0.0D, getDeltaMovement().y) * 0.98D, actualZ);
    if (horizontalCollision) entityData.set(POWER, 0.0F);

    yawDelta = Mth.wrapDegrees(getYRot() - startYaw);
    double forwardTravel = DnvBossGroundDrive.forwardTravel(
        actualX, actualZ, startYaw + yawDelta * 0.5F);
    double turnTravel = yawDelta * Mth.DEG_TO_RAD * TRACK_HALF_WIDTH;
    double leftTravel = forwardTravel - turnTravel;
    double rightTravel = forwardTravel + turnTravel;
    float leftSpeed = (float) (leftTravel / TRACK_SHOE_SPACING);
    float rightSpeed = (float) (rightTravel / TRACK_SHOE_SPACING);
    entityData.set(TRACK_SPEED_L, leftSpeed);
    entityData.set(TRACK_SPEED_R, rightSpeed);
    entityData.set(
        TRACK_PHASE_L,
        Mth.positiveModulo(entityData.get(TRACK_PHASE_L) + leftSpeed, TRACK_SHOE_COUNT));
    entityData.set(
        TRACK_PHASE_R,
        Mth.positiveModulo(entityData.get(TRACK_PHASE_R) + rightSpeed, TRACK_SHOE_COUNT));
    entityData.set(
        ROAD_WHEEL_ANGLE_L,
        Mth.positiveModulo(
            entityData.get(ROAD_WHEEL_ANGLE_L) + leftSpeed * ROAD_WHEEL_DEGREES_PER_TRACK_PHASE,
            360.0F));
    entityData.set(
        ROAD_WHEEL_ANGLE_R,
        Mth.positiveModulo(
            entityData.get(ROAD_WHEEL_ANGLE_R) + rightSpeed * ROAD_WHEEL_DEGREES_PER_TRACK_PHASE,
            360.0F));
    setLeftTrack(getLeftTrack() + leftSpeed * 12.0F);
    setRightTrack(getRightTrack() + rightSpeed * 12.0F);
    entityData.set(
        DRIVE_INTENSITY,
        Mth.clamp(Math.max(Math.abs(leftSpeed), Math.abs(rightSpeed)) * 2.0F, 0.0F, 1.0F));
  }

}
