package com.liucodex.silentheaven.entity;

import com.liucodex.doggiesnewvehicle.boss.DnvBossGroundDrive;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvObstructionPolicy;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import com.liucodex.doggiesnewvehicle.compat.DnvSightObservation;
import com.liucodex.doggiesnewvehicle.core.DnvCandidateProbeOrder;
import com.liucodex.doggiesnewvehicle.entity.DnvVehicleTeleportTimeline;
import com.liucodex.silentheaven.init.ModSounds;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

abstract class AbstractSilentHeavenVehicleNavigation extends AbstractSilentHeavenVehicleLifecycle {
  protected AbstractSilentHeavenVehicleNavigation(
      EntityType<? extends SilentHeavenVehicleEntity> type, Level level) {
    super(type, level);
  }

  protected void tickCombatRecovery(@Nullable Entity target) {
    if (target != null || isTransitioning()) {
      outOfCombatTicks = 0;
      return;
    }
    outOfCombatTicks = Math.min(Integer.MAX_VALUE - 1, outOfCombatTicks + 1);
    float capacity = SilentHeavenPhaseHealth.capacity(getPhase());
    if (outOfCombatTicks >= OUT_OF_COMBAT_HEAL_DELAY && getHealth() < capacity) {
      setHealth(Math.min(capacity, getHealth() + OUT_OF_COMBAT_HEAL_PER_TICK));
    }
  }

  protected void tickObstructionTeleport(@Nullable Entity target) {
    if (holdPosition || target == null || isTransitioning()) {
      cancelTeleportSequence();
      return;
    }
    boolean unableToAttack = !hasUsableCurrentAttackLine(target);
    if (attackLineDeferred) return;
    if (!unableToAttack) {
      lastAttackLineTick = tickCount;
      cancelTeleportSequence();
      return;
    }
    obstructedTargetTicks = Math.min(OBSTRUCTED_TELEPORT_TICKS, obstructedTargetTicks + 1);
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
    if (!DnvObstructionPolicy.shouldTeleport(recoveryTicks) || teleportCooldownTicks > 0) {
      return;
    }
    Vec3 destination = findTeleportDestinationNearTarget(target);
    if (destination != null) {
      beginTeleportSequence(destination);
    } else {
      // No safe full-vehicle landing point: never force the teleport.
      // Keep most of the obstruction timer so another search begins
      // after a short grace period instead of immediately spamming.
      obstructedTargetTicks = OBSTRUCTED_TELEPORT_TICKS - 40;
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
    for (Entity candidate : DnvCandidateProbeOrder.startingAt(
        targetCandidates.candidates(this), obstructionProbeCursor)) {
      if (candidate == current || !isTrackedTargetValid(candidate)
          || Math.abs(candidate.getBoundingBox().getCenter().y - getBoundingBox().getCenter().y)
              > EXCESSIVE_HEIGHT_DIFFERENCE) continue;
      if (!DnvTargetingBudget.admitGeometryCheck(serverLevel, stationGeometryOwner(6))) {
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

  protected void cancelTeleportSequence() {
    resetObstructionTracking();
    setTeleportTicks(0);
  }

  protected void resetObstructionTracking() {
    obstructedTargetTicks = 0;
    nextObstructionRetargetTick = 0;
    teleportDestination = null;
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
    return getPhase() == PHASE_TWO
        && distance <= VLS_MAX_RANGE
        && hasClearShot(position().add(0.0D, 4.55D, 0.0D), target);
  }

  protected boolean hasCachedAttackLine(Entity target) {
    UUID targetId = target.getUUID();
    Vec3 origin = position().add(0.0D, 3.58D, 0.0D);
    long pose = attackSightPoseKey();
    if (attackSight == null || !attackSight.reusable(targetId, origin,
        target.getBoundingBox(), target.getEyeY(), pose, tickCount)) {
      if (!(level() instanceof ServerLevel serverLevel)
          || !DnvTargetingBudget.admitGeometryCheck(serverLevel, stationGeometryOwner(6))) {
        attackLineDeferred = true;
        return false;
      }
      attackSight = new DnvSightObservation(targetId, origin, target.getBoundingBox(),
          target.getEyeY(), pose, tickCount,
          DnvTargetingBudget.nextCheckTick(tickCount, getId(),
              DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL, 60), hasAnyAttackLine(target));
    }
    attackLineDeferred = false;
    return attackSight.clear();
  }

  private long attackSightPoseKey() {
    return 31L * (31L * (31L * stationSightPoseKey(5) + stationSightPoseKey(0))
        + stationSightPoseKey(1)) + getPhase();
  }

  protected void spawnTeleportChargeParticles() {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return;
    }
    int remaining = getTeleportTicks();
    if (DnvVehicleTeleportTimeline.isHidden(remaining)) {
      return;
    }
    double progress = DnvVehicleTeleportTimeline.whiteOverlayProgress(remaining);
    int count = 3 + (int) Math.floor(progress * 9.0D);
    serverLevel.sendParticles(
        ParticleTypes.REVERSE_PORTAL,
        getX(),
        getY() + 2.0D,
        getZ(),
        count,
        2.2D,
        1.8D,
        2.2D,
        0.08D);
  }

  @Nullable
  protected Vec3 findTeleportDestinationNearTarget(Entity target) {
    if (!(level() instanceof ServerLevel serverLevel) || !isTrackedTargetValid(target)) {
      return null;
    }
    double seedAngle = random.nextDouble() * Mth.TWO_PI;
    for (int ring = 0; ring < 3; ring++) {
      double radius = TELEPORT_MAX_RANGE - ring * 4.5D;
      for (int index = 0; index < 16; index++) {
        double angle = seedAngle + index * Mth.TWO_PI / 16.0D;
        int x = Mth.floor(target.getX() + Math.cos(angle) * radius);
        int z = Mth.floor(target.getZ() + Math.sin(angle) * radius);
        int y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Vec3 destination = new Vec3(x + 0.5D, y, z + 0.5D);
        if (isSafeTeleportDestination(serverLevel, destination)) {
          return destination;
        }
      }
    }
    return null;
  }

  public boolean commandTeleportTo(ServerLevel requestedLevel, Vec3 destination) {
    return level() == requestedLevel
        && isAlive()
        && !isEntering()
        && !isTransitioning()
        && !isTeleporting()
        && teleportCooldownTicks <= 0
        && isSafeTeleportDestination(requestedLevel, destination)
        && beginTeleportSequence(destination);
  }

  protected boolean beginTeleportSequence(Vec3 destination) {
    if (!(level() instanceof ServerLevel) || teleportCooldownTicks > 0) {
      return false;
    }
    teleportDestination = destination;
    obstructedTargetTicks = 0;
    setTeleportTicks(DnvVehicleTeleportTimeline.TOTAL_TICKS);
    lockForTeleport();
    playVoice(ModSounds.VOICE_RESETTING.get());
    generalVoiceCooldown = DnvVehicleTeleportTimeline.TOTAL_TICKS;
    return true;
  }

  protected void tickTeleportSequence() {
    int remaining = getTeleportTicks();
    if (remaining <= 0) {
      return;
    }
    lockForTeleport();
    spawnTeleportChargeParticles();
    int next = remaining - 1;
    setTeleportTicks(next);
    if (next == DnvVehicleTeleportTimeline.DISAPPEAR_REMAINING_TICKS
        && level() instanceof ServerLevel serverLevel) {
      spawnTeleportBurst(serverLevel);
      playEndermanTeleportSound();
    }
    if (next == DnvVehicleTeleportTimeline.TELEPORT_REMAINING_TICKS) {
      performTeleportJump();
    }
    if (next <= 0) {
      finishTeleportSequence();
    }
  }

  protected void lockForTeleport() {
    groundDrive.reset();
    driveBrake = true;
    requestedThrottle = 0.0F;
    requestedSteering = 0.0F;
    mainAim = Vec3.ZERO;
    mainSettledTicks = 0;
    entityData.set(POWER, 0.0F);
    entityData.set(DELTA_ROT, 0.0F);
    entityData.set(DRIVE_INTENSITY, 0.0F);
    entityData.set(TRACK_SPEED_L, 0.0F);
    entityData.set(TRACK_SPEED_R, 0.0F);
    Vec3 motion = getDeltaMovement();
    setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
  }

  protected void performTeleportJump() {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return;
    }
    Vec3 destination = teleportDestination;
    if (destination == null || !isSafeTeleportDestination(serverLevel, destination)) {
      // The destination changed while the machine was hidden. Run the
      // same recovery at the origin instead of clipping into blocks.
      spawnTeleportBurst(serverLevel);
      playEndermanTeleportSound();
      return;
    }
    teleportTo(destination.x, destination.y, destination.z);
    teleportCooldownTicks = TELEPORT_COOLDOWN_TICKS;
    setDeltaMovement(Vec3.ZERO);
    requestedThrottle = 0.0F;
    requestedSteering = 0.0F;
    spawnTeleportBurst(serverLevel);
    playEndermanTeleportSound();
    playVoice(ModSounds.VOICE_HELLO.get());
    generalVoiceCooldown = DnvVehicleTeleportTimeline.RECOVERY_TICKS;
  }

  protected void finishTeleportSequence() {
    teleportDestination = null;
    obstructedTargetTicks = 0;
    setTeleportTicks(0);
  }

  protected void spawnTeleportBurst(ServerLevel serverLevel) {
    serverLevel.sendParticles(
        ParticleTypes.REVERSE_PORTAL, getX(), getY() + 2.0D, getZ(), 36, 2.5D, 2.0D, 2.5D, 0.18D);
    serverLevel.sendParticles(
        ParticleTypes.FLASH, getX(), getY() + 2.0D, getZ(), 2, 0.2D, 0.2D, 0.2D, 0.0D);
  }

  protected void playEndermanTeleportSound() {
    level()
        .playSound(
            null,
            getX(),
            getY() + 2.0D,
            getZ(),
            SoundEvents.ENDERMAN_TELEPORT,
            SoundSource.HOSTILE,
            2.5F,
            1.0F);
  }

  protected void tickEntrancePresentation() {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return;
    }
    if (!entranceShown) {
      entranceShown = true;
      setEntranceTicks(ENTRANCE_EFFECT_TICKS);
      announceBossTitle(
          Component.translatable("title.silent_heaven.guardian"),
          Component.translatable("subtitle.silent_heaven.guardian"),
          10,
          50,
          20);
      playExternalSound(
          "minecraft:entity.warden.emerge", position().add(0.0D, 1.0D, 0.0D), 3.0F, 0.78F);
    }
    if (getEntranceTicks() <= 0) {
      return;
    }

    int elapsed = ENTRANCE_EFFECT_TICKS - getEntranceTicks();
    if (elapsed % 2 == 0) {
      serverLevel.sendParticles(
          ParticleTypes.REVERSE_PORTAL, getX(), getY() + 2.1D, getZ(), 10, 2.4D, 1.8D, 2.4D, 0.08D);
      serverLevel.sendParticles(
          ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.4D, getZ(), 7, 2.1D, 1.1D, 2.1D, 0.025D);
    }
    if (elapsed == 18 || elapsed == 38) {
      spawnParticleRing(
          serverLevel,
          elapsed == 18 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
          elapsed == 18 ? 2.8D : 4.6D,
          elapsed == 18 ? 28 : 40,
          0.25D);
    }
    setEntranceTicks(getEntranceTicks() - 1);
  }

  protected void playPhaseTwoPresentation() {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return;
    }
    announceBossTitle(
        Component.translatable("title.silent_heaven.silent_heaven"),
        Component.translatable("subtitle.silent_heaven.silent_heaven"),
        8,
        65,
        28);
    serverLevel.sendParticles(
        ParticleTypes.FLASH, getX(), getY() + 4.8D, getZ(), 3, 0.2D, 0.2D, 0.2D, 0.0D);
    serverLevel.sendParticles(
        ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 4.0D, getZ(), 180, 3.2D, 3.1D, 3.2D, 0.12D);
    serverLevel.sendParticles(
        ParticleTypes.END_ROD, getX(), getY() + 3.2D, getZ(), 90, 2.8D, 2.4D, 2.8D, 0.09D);
    spawnParticleRing(serverLevel, ParticleTypes.ELECTRIC_SPARK, 4.0D, 48, 0.35D);
    spawnParticleRing(serverLevel, ParticleTypes.END_ROD, 6.2D, 64, 0.20D);
    playExternalSound(
        "minecraft:entity.lightning_bolt.thunder", position().add(0.0D, 4.0D, 0.0D), 5.0F, 0.78F);
    playExternalSound(
        "doggiesnewvehicle:jingzhe_attack", position().add(0.0D, 4.0D, 0.0D), 8.0F, 0.90F);
  }

  protected void spawnParticleRing(
      ServerLevel serverLevel,
      SimpleParticleType particle,
      double radius,
      int count,
      double yOffset) {
    for (int index = 0; index < count; index++) {
      double angle = index * Mth.TWO_PI / count;
      serverLevel.sendParticles(
          particle,
          getX() + Math.cos(angle) * radius,
          getY() + yOffset,
          getZ() + Math.sin(angle) * radius,
          1,
          0.0D,
          0.02D,
          0.0D,
          0.01D);
    }
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

  protected boolean isSafeTeleportDestination(ServerLevel level, Vec3 destination) {
    if (horizontalDistance(destination) < 2.0D) {
      return false;
    }
    BlockPos feet = BlockPos.containing(destination);
    BlockPos ground = feet.below();
    if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)
        || !level.getFluidState(ground).isEmpty()
        || !level.getFluidState(feet).isEmpty()
        || !level.getFluidState(feet.above()).isEmpty()
        || !hasStableTeleportFootprint(level, destination)) {
      return false;
    }
    Vec3 offset = destination.subtract(position());
    AABB movedBounds = getBoundingBox().move(offset);
    return level.getWorldBorder().isWithinBounds(movedBounds)
        && level.noCollision(this, movedBounds)
        && level
            .getEntities(
                this, movedBounds.inflate(0.05D), entity -> entity.isAlive() && entity.isPickable())
            .isEmpty();
  }

  protected static boolean hasStableTeleportFootprint(ServerLevel level, Vec3 destination) {
    double[] offsets = {-2.35D, 0.0D, 2.35D};
    for (double xOffset : offsets) {
      for (double zOffset : offsets) {
        BlockPos support =
            BlockPos.containing(
                destination.x + xOffset, destination.y - 0.1D, destination.z + zOffset);
        if (!level.getFluidState(support).isEmpty()
            || !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
          return false;
        }
      }
    }
    return true;
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
    float yawDelta = DnvBossGroundDrive.yawStep(steering, power, HULL_TURN_SPEED * (getPhase() == PHASE_TWO ? PHASE_TWO_HULL_TURN_MULTIPLIER : 1.0F));
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
