package com.liucodex.silentheaven.entity;

import com.liucodex.doggiesnewvehicle.entity.DnvVehicleTeleportTimeline;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyMemoryCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Public registry boundary for the Silent Heaven boss vehicle. */
public final class SilentHeavenVehicleEntity extends AbstractSilentHeavenVehicleCombat
    implements com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget,
        com.liucodex.doggiesnewvehicle.api.combat.DnvCameraShakeSource,
        com.liucodex.doggiesnewvehicle.api.combat.DnvColdArmor {
  @Override public Tier coldArmorTier() { return getPhase() == PHASE_ONE ? Tier.MEDIUM : Tier.HEAVY; }
  private final com.liucodex.doggiesnewvehicle.boss.DnvTrackedBossHull meshHull =
      new com.liucodex.doggiesnewvehicle.boss.DnvTrackedBossHull(true);
  public SilentHeavenVehicleEntity(
      EntityType<? extends SilentHeavenVehicleEntity> type, Level level) {
    super(type, level);
    updateOBB();refreshBoundingBoxFromOBBs();
  }

  @Override public boolean dnvCameraShakeAllowed() { return false; }

  @Override public void updateOBB() {
    if(meshHull==null)super.updateOBB();else meshHull.update(this,this);
  }
  @Override public java.util.List<com.atsuishio.superbwarfare.tools.OBB> getOBBs() {
    return meshHull==null?super.getOBBs():meshHull.boxes();
  }
  @Override public void refreshBoundingBoxFromOBBs() {
    if(meshHull==null)super.refreshBoundingBoxFromOBBs();
    else setBoundingBox(com.liucodex.doggiesnewvehicle.boss.MothershipHull.bounds(meshHull.boxes()));
  }
  @Override public com.atsuishio.superbwarfare.tools.OBB getCollisionOBB() {
    return meshHull==null?super.getCollisionOBB():meshHull.movement();
  }
  @Override public com.atsuishio.superbwarfare.data.vehicle.subdata.OBBInfo getCollisionOBBInfo() {
    return meshHull==null?super.getCollisionOBBInfo():meshHull.movementInfo();
  }
  @Override public void tick() {
    com.liucodex.doggiesnewvehicle.compat.DnvBossTeamMembership.initializeIfNeeded(this);
    super.tick();
    if(!isRemoved()){updateOBB();refreshBoundingBoxFromOBBs();}
  }

  @Override
  public void remove(Entity.RemovalReason reason) {
    releaseStationGeometryOwners();
    targetCandidates.clear();
    super.remove(reason);
  }

  @Override
  protected SilentHeavenVehicleEntity self() {
    return this;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.m_7380_(tag);
    tag.putInt("SilentHeavenPhase", getPhase());
    tag.putInt("SilentHeavenTransition", getTransitionTick());
    tag.putFloat("SilentHeavenCoreYaw", getCoreYaw());
    tag.putFloat("SilentHeavenMainPitch", getMainGunPitch());
    tag.putFloat("SilentHeavenHalo", getHaloAngle());
    tag.putInt("SilentHeavenMainLReload", leftMainReload);
    tag.putInt("SilentHeavenMainRReload", rightMainReload);
    tag.putFloat("SilentHeavenAutocannonLYaw", getAutocannonLYaw());
    tag.putFloat("SilentHeavenAutocannonLPitch", getAutocannonLPitch());
    tag.putFloat("SilentHeavenAutocannonRYaw", getAutocannonRYaw());
    tag.putFloat("SilentHeavenAutocannonRPitch", getAutocannonRPitch());
    tag.putFloat("SilentHeavenRoadWheelAngleL", getRoadWheelAngleL());
    tag.putFloat("SilentHeavenRoadWheelAngleR", getRoadWheelAngleR());
    tag.putFloat("SilentHeavenMissilePodLYaw", getMissilePodLYaw());
    tag.putFloat("SilentHeavenMissilePodLPitch", getMissilePodLPitch());
    tag.putFloat("SilentHeavenMissilePodRYaw", getMissilePodRYaw());
    tag.putFloat("SilentHeavenMissilePodRPitch", getMissilePodRPitch());
    tag.putInt("SilentHeavenHaloAmmo", entityData.get(HALO_AMMO));
    tag.putInt("SilentHeavenHaloReload", entityData.get(HALO_RELOAD));
    tag.putInt("SilentHeavenHaloCooldown", haloShotCooldown);
    tag.putInt("SilentHeavenVlsTube", vlsTubeIndex);
    tag.putInt("SilentHeavenDroneCooldown", droneLaunchCooldown);
    tag.putBoolean("SilentHeavenDroneLeft", nextDroneLaunchLeft);
    tag.putInt("SilentHeavenObstructedTicks", obstructedTargetTicks);
    tag.putInt("SilentHeavenTeleportCooldown", teleportCooldownTicks);
    tag.putInt("SilentHeavenTeleportTicks", getTeleportTicks());
    if (teleportDestination != null) {
      tag.putDouble("SilentHeavenTeleportX", teleportDestination.x);
      tag.putDouble("SilentHeavenTeleportY", teleportDestination.y);
      tag.putDouble("SilentHeavenTeleportZ", teleportDestination.z);
    }
    tag.putInt("SilentHeavenOutOfCombat", outOfCombatTicks);
    tag.putInt("SilentHeavenVoiceStage", startupVoiceStage);
    tag.putInt("SilentHeavenVoiceDelay", startupVoiceDelay);
    tag.putInt("SilentHeavenVoiceCooldown", generalVoiceCooldown);
    tag.putInt("SilentHeavenAttackVoiceCooldown", attackVoiceCooldown);
    tag.putInt("SilentHeavenDamageVoiceCooldown", damageVoiceCooldown);
    tag.putInt("SilentHeavenTargetVoiceCooldown", targetAcquiredVoiceCooldown);
    tag.putInt("SilentHeavenDestructionTicks", getDestructionTicks());
    tag.putBoolean("SilentHeavenHoldPosition", holdPosition);
    tag.putBoolean("SilentHeavenEntranceShown", entranceShown);
    tag.putInt("SilentHeavenEntranceTicks", getEntranceTicks());
    if (haloTargetId != null) tag.putUUID("SilentHeavenHaloTarget", haloTargetId);
    tag.put("SilentHeavenAutocannonL", writeAutocannonStation(autocannonLeft));
    tag.put("SilentHeavenAutocannonR", writeAutocannonStation(autocannonRight));
    tag.put("SilentHeavenMissileL", writeWeaponStation(missilePodLeft));
    tag.put("SilentHeavenMissileR", writeWeaponStation(missilePodRight));
    tag.put("SilentHeavenVls", writeWeaponStation(vlsBattery));
    ListTag strikes = new ListTag();
    for (PendingStrike strike : pendingStrikes) {
      CompoundTag strikeTag = new CompoundTag();
      strikeTag.putDouble("X", strike.impact.x);
      strikeTag.putDouble("Y", strike.impact.y);
      strikeTag.putDouble("Z", strike.impact.z);
      strikeTag.putInt("Ticks", strike.ticks);
      strikes.add(strikeTag);
    }
    tag.put("SilentHeavenPendingStrikes", strikes);
    tag.put("SilentHeavenAutonomy", DnvAutonomyMemoryCodec.encode(
        autonomyCore.memorySnapshot()));
    if (combatTargetId != null) tag.putUUID("SilentHeavenTarget", combatTargetId);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.m_7378_(tag);
    resetTargetingForLoad();
    // Clamp dev.9 worlds that may still contain the former 25,000 health
    // value before restoring the new phase state.
    setPhase(Mth.clamp(tag.getInt("SilentHeavenPhase"), PHASE_ONE, PHASE_TWO));
    setTransitionTick(Mth.clamp(tag.getInt("SilentHeavenTransition"), 0, TRANSITION_TICKS));
    float phaseCapacity = SilentHeavenPhaseHealth.capacity(getPhase());
    setHealth(Math.min(phaseCapacity, getHealth()));
    if (getPhase() == PHASE_TRANSITION && getHealth() <= 0.0F) {
      setHealth(TRANSITION_SURVIVAL_HEALTH);
    }
    bossEvent.setColor(
        getPhase() == PHASE_ONE ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
    coreOrientationInitialized = tag.contains("SilentHeavenCoreYaw", Tag.TAG_FLOAT);
    if (coreOrientationInitialized) {
      setCoreYaw(tag.getFloat("SilentHeavenCoreYaw"));
    }
    setMainGunPitch(tag.getFloat("SilentHeavenMainPitch"));
    setHaloAngle(tag.getFloat("SilentHeavenHalo"));
    leftMainReload = Math.max(0, tag.getInt("SilentHeavenMainLReload"));
    rightMainReload = Math.max(0, tag.getInt("SilentHeavenMainRReload"));
    if (tag.contains("SilentHeavenAutocannonLYaw", Tag.TAG_FLOAT)) {
      entityData.set(AUTOCANNON_L_YAW, tag.getFloat("SilentHeavenAutocannonLYaw"));
      entityData.set(AUTOCANNON_L_PITCH, tag.getFloat("SilentHeavenAutocannonLPitch"));
      entityData.set(AUTOCANNON_R_YAW, tag.getFloat("SilentHeavenAutocannonRYaw"));
      entityData.set(AUTOCANNON_R_PITCH, tag.getFloat("SilentHeavenAutocannonRPitch"));
      entityData.set(MISSILE_POD_L_YAW, tag.getFloat("SilentHeavenMissilePodLYaw"));
      entityData.set(MISSILE_POD_L_PITCH, tag.getFloat("SilentHeavenMissilePodLPitch"));
      entityData.set(MISSILE_POD_R_YAW, tag.getFloat("SilentHeavenMissilePodRYaw"));
      entityData.set(MISSILE_POD_R_PITCH, tag.getFloat("SilentHeavenMissilePodRPitch"));
    }
    entityData.set(
        ROAD_WHEEL_ANGLE_L,
        Mth.positiveModulo(tag.getFloat("SilentHeavenRoadWheelAngleL"), 360.0F));
    entityData.set(
        ROAD_WHEEL_ANGLE_R,
        Mth.positiveModulo(tag.getFloat("SilentHeavenRoadWheelAngleR"), 360.0F));
    if (tag.contains("SilentHeavenHaloAmmo", Tag.TAG_INT)) {
      entityData.set(HALO_AMMO, Mth.clamp(tag.getInt("SilentHeavenHaloAmmo"), 0, HALO_MAGAZINE));
      entityData.set(HALO_RELOAD, Math.max(0, tag.getInt("SilentHeavenHaloReload")));
      haloShotCooldown = Math.max(0, tag.getInt("SilentHeavenHaloCooldown"));
    }
    vlsTubeIndex = tag.getInt("SilentHeavenVlsTube") & 1;
    droneLaunchCooldown = Math.max(0, tag.getInt("SilentHeavenDroneCooldown"));
    nextDroneLaunchLeft =
        !tag.contains("SilentHeavenDroneLeft", Tag.TAG_BYTE)
            || tag.getBoolean("SilentHeavenDroneLeft");
    obstructedTargetTicks = Math.max(0, tag.getInt("SilentHeavenObstructedTicks"));
    teleportCooldownTicks = Math.max(0, tag.getInt("SilentHeavenTeleportCooldown"));
    int savedTeleportTicks =
        DnvVehicleTeleportTimeline.clampRemaining(tag.getInt("SilentHeavenTeleportTicks"));
    boolean hasTeleportDestination =
        tag.contains("SilentHeavenTeleportX", Tag.TAG_DOUBLE)
            && tag.contains("SilentHeavenTeleportY", Tag.TAG_DOUBLE)
            && tag.contains("SilentHeavenTeleportZ", Tag.TAG_DOUBLE);
    if (savedTeleportTicks > 0 && hasTeleportDestination) {
      teleportDestination =
          new Vec3(
              tag.getDouble("SilentHeavenTeleportX"),
              tag.getDouble("SilentHeavenTeleportY"),
              tag.getDouble("SilentHeavenTeleportZ"));
      setTeleportTicks(savedTeleportTicks);
    } else {
      teleportDestination = null;
      setTeleportTicks(0);
    }
    outOfCombatTicks = Math.max(0, tag.getInt("SilentHeavenOutOfCombat"));
    startupVoiceStage = Mth.clamp(tag.getInt("SilentHeavenVoiceStage"), 0, 2);
    startupVoiceDelay = Math.max(0, tag.getInt("SilentHeavenVoiceDelay"));
    generalVoiceCooldown = Math.max(0, tag.getInt("SilentHeavenVoiceCooldown"));
    attackVoiceCooldown = Math.max(0, tag.getInt("SilentHeavenAttackVoiceCooldown"));
    damageVoiceCooldown = Math.max(0, tag.getInt("SilentHeavenDamageVoiceCooldown"));
    targetAcquiredVoiceCooldown = Math.max(0, tag.getInt("SilentHeavenTargetVoiceCooldown"));
    int savedDestructionTicks =
        tag.contains("SilentHeavenDestructionTicks", Tag.TAG_INT)
            ? Mth.clamp(tag.getInt("SilentHeavenDestructionTicks"), 0, DESTRUCTION_SEQUENCE_TICKS)
            : (isWreck() || getHealth() <= 0.0F ? 1 : 0);
    setDestructionTicks(savedDestructionTicks);
    holdPosition = tag.getBoolean("SilentHeavenHoldPosition");
    entranceShown = tag.getBoolean("SilentHeavenEntranceShown");
    setEntranceTicks(Mth.clamp(tag.getInt("SilentHeavenEntranceTicks"), 0, ENTRANCE_EFFECT_TICKS));
    pendingVoice = PENDING_VOICE_NONE;
    wasMoving = false;
    haloTargetId =
        tag.hasUUID("SilentHeavenHaloTarget") ? tag.getUUID("SilentHeavenHaloTarget") : null;
    if (tag.contains("SilentHeavenAutocannonL", Tag.TAG_COMPOUND)) {
      readAutocannonStation(autocannonLeft, tag.getCompound("SilentHeavenAutocannonL"));
      readAutocannonStation(autocannonRight, tag.getCompound("SilentHeavenAutocannonR"));
      readWeaponStation(missilePodLeft, tag.getCompound("SilentHeavenMissileL"), MISSILE_MAGAZINE);
      readWeaponStation(missilePodRight, tag.getCompound("SilentHeavenMissileR"), MISSILE_MAGAZINE);
      readWeaponStation(vlsBattery, tag.getCompound("SilentHeavenVls"), VLS_MAGAZINE);
    }
    if (getPhase() != PHASE_TWO) {
      vlsBattery.disableAndEmpty();
    }
    pendingStrikes.clear();
    ListTag strikes = tag.getList("SilentHeavenPendingStrikes", Tag.TAG_COMPOUND);
    for (int index = 0; index < strikes.size(); index++) {
      CompoundTag strikeTag = strikes.getCompound(index);
      pendingStrikes.add(
          new PendingStrike(
              new Vec3(
                  strikeTag.getDouble("X"), strikeTag.getDouble("Y"), strikeTag.getDouble("Z")),
              Math.max(1, strikeTag.getInt("Ticks"))));
    }
    combatTargetId = tag.hasUUID("SilentHeavenTarget") ? tag.getUUID("SilentHeavenTarget") : null;
    if (!level().isClientSide
        && tag.contains("SilentHeavenAutonomy", Tag.TAG_COMPOUND)) {
      DnvAutonomyMemoryCodec.decode(tag.getCompound("SilentHeavenAutonomy"))
          .ifPresent(memory -> autonomyCore.restore(
              memory,
              level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                  ? serverLevel.getGameTime()
                  : memory.lastGameTime()));
    }
  }

  protected static CompoundTag writeWeaponStation(WeaponStation station) {
    CompoundTag tag = new CompoundTag();
    if (station.targetId != null) tag.putUUID("Target", station.targetId);
    tag.putInt("Ammo", station.ammo);
    tag.putInt("Cooldown", station.cooldown);
    tag.putInt("Reload", station.reload);
    return tag;
  }

  protected static void readWeaponStation(WeaponStation station, CompoundTag tag, int magazine) {
    station.targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
    station.ammo = Mth.clamp(tag.getInt("Ammo"), 0, magazine);
    station.cooldown = Math.max(0, tag.getInt("Cooldown"));
    station.reload = Math.max(0, tag.getInt("Reload"));
    // Old world-tick observations must be reacquired before firing.
    station.settledTicks = 0;
    station.aimVector = Vec3.ZERO;
  }

  protected static CompoundTag writeAutocannonStation(AutocannonStation station) {
    CompoundTag tag = new CompoundTag();
    if (station.targetId != null) tag.putUUID("Target", station.targetId);
    tag.putInt("Ammo", station.ammo);
    tag.putInt("Cooldown", station.cooldown);
    tag.putInt("Reload", station.reload);
    tag.putInt("Cadence", station.cadenceIndex);
    return tag;
  }

  protected static void readAutocannonStation(AutocannonStation station, CompoundTag tag) {
    station.targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
    station.ammo = Mth.clamp(tag.getInt("Ammo"), 0, AUTOCANNON_MAGAZINE);
    station.cooldown = Math.max(0, tag.getInt("Cooldown"));
    station.reload = Math.max(0, tag.getInt("Reload"));
    station.settledTicks = 0;
    if (tag.contains("Cadence", Tag.TAG_INT)) {
      station.cadenceIndex = SilentHeavenAutocannonCadence.normalizeIndex(tag.getInt("Cadence"));
    }
  }

  @Override
  public Entity asEntity() {
    return this;
  }

  public int getPhase() {
    return entityData.get(PHASE);
  }

  protected void setPhase(int value) {
    entityData.set(PHASE, value);
  }

  @Override
  public int getTransitionTick() {
    return entityData.get(TRANSITION_TICK);
  }

  protected void setTransitionTick(int value) {
    entityData.set(TRANSITION_TICK, value);
  }

  @Override
  public int getEntranceTicks() {
    return entityData.get(ENTRANCE_TICKS);
  }

  @Override
  public int getTeleportTicks() {
    return entityData.get(TELEPORT_TICKS);
  }

  protected void setTeleportTicks(int value) {
    entityData.set(TELEPORT_TICKS, DnvVehicleTeleportTimeline.clampRemaining(value));
  }

  public int getDestructionTicks() {
    return entityData.get(DESTRUCTION_TICKS);
  }

  protected void setDestructionTicks(int value) {
    entityData.set(DESTRUCTION_TICKS, Mth.clamp(value, 0, DESTRUCTION_SEQUENCE_TICKS));
  }

  public boolean isTeleporting() {
    return DnvVehicleTeleportTimeline.isActive(getTeleportTicks());
  }

  protected void setEntranceTicks(int value) {
    entityData.set(ENTRANCE_TICKS, Mth.clamp(value, 0, ENTRANCE_EFFECT_TICKS));
  }

  @Override
  public boolean isEntering() {
    return getEntranceTicks() > 0;
  }

  @Override
  public float getCoreYaw() {
    return entityData.get(CORE_YAW);
  }

  protected void setCoreYaw(float value) {
    entityData.set(CORE_YAW, Mth.wrapDegrees(value));
  }

  public void alignCoreToHull() {
    setCoreYaw(getYRot());
    setMainGunPitch(0.0F);
    idleCoreYawOffset = 0.0F;
    idleCorePauseTicks = IDLE_CORE_INITIAL_PAUSE;
    coreOrientationInitialized = true;
  }

  public int getTeleportCooldownTicks() {
    return teleportCooldownTicks;
  }

  @Override
  public float getMainGunPitch() {
    return entityData.get(MAIN_GUN_PITCH);
  }

  protected void setMainGunPitch(float value) {
    entityData.set(MAIN_GUN_PITCH, Mth.clamp(value, -12.0F, 48.0F));
  }

  @Override
  public float getAutocannonLYaw() {
    return entityData.get(AUTOCANNON_L_YAW);
  }

  @Override
  public float getAutocannonLPitch() {
    return entityData.get(AUTOCANNON_L_PITCH);
  }

  @Override
  public float getAutocannonRYaw() {
    return entityData.get(AUTOCANNON_R_YAW);
  }

  @Override
  public float getAutocannonRPitch() {
    return entityData.get(AUTOCANNON_R_PITCH);
  }

  @Override
  public float getMissilePodLYaw() {
    return entityData.get(MISSILE_POD_L_YAW);
  }

  @Override
  public float getMissilePodLPitch() {
    return entityData.get(MISSILE_POD_L_PITCH);
  }

  @Override
  public float getMissilePodRYaw() {
    return entityData.get(MISSILE_POD_R_YAW);
  }

  @Override
  public float getMissilePodRPitch() {
    return entityData.get(MISSILE_POD_R_PITCH);
  }

  @Override
  public float getHaloAngle() {
    return entityData.get(HALO_ANGLE);
  }

  protected void setHaloAngle(float value) {
    entityData.set(HALO_ANGLE, Mth.wrapDegrees(value));
  }

  @Override
  public boolean isPhaseTwo() {
    return getPhase() == PHASE_TWO;
  }

  @Override
  public boolean isTransitioning() {
    return getPhase() == PHASE_TRANSITION;
  }

  @Override
  public boolean isHaloVisible() {
    return isPhaseTwo() || isTransitioning() && getTransitionTick() >= 330;
  }

  @Override
  public float getDriveIntensity() {
    return entityData.get(DRIVE_INTENSITY);
  }

  @Override
  public float getTrackPhaseL() {
    return entityData.get(TRACK_PHASE_L);
  }

  @Override
  public float getTrackPhaseR() {
    return entityData.get(TRACK_PHASE_R);
  }

  @Override
  public float getTrackSpeedL() {
    return entityData.get(TRACK_SPEED_L);
  }

  @Override
  public float getTrackSpeedR() {
    return entityData.get(TRACK_SPEED_R);
  }

  @Override
  public float getRoadWheelAngleL() {
    return entityData.get(ROAD_WHEEL_ANGLE_L);
  }

  @Override
  public float getRoadWheelAngleR() {
    return entityData.get(ROAD_WHEEL_ANGLE_R);
  }

  @Override
  public boolean hasCombatTarget() {
    return combatTargetId != null;
  }

  @Override
  public boolean isCriticalState() {
    return getHealth() <= PHASE_THRESHOLD || isTransitioning();
  }
}
