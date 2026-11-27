package com.liucodex.noise.entity;

import com.liucodex.doggiesnewvehicle.core.DnvAutonomyMemoryCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Public registry boundary for the Noise boss vehicle. */
public final class NoiseVehicleEntity extends AbstractNoiseVehicleCombat
    implements com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget,
        com.liucodex.doggiesnewvehicle.api.combat.DnvColdArmor {
  @Override public Tier coldArmorTier() { return Tier.HEAVY; }
  private final com.liucodex.doggiesnewvehicle.boss.DnvTrackedBossHull meshHull =
      new com.liucodex.doggiesnewvehicle.boss.DnvTrackedBossHull(false);
  public NoiseVehicleEntity(EntityType<? extends NoiseVehicleEntity> type, Level level) {
    super(type, level);
    updateOBB();refreshBoundingBoxFromOBBs();
  }

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
  protected NoiseVehicleEntity self() {
    return this;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.m_7380_(tag);
    tag.putFloat("NoiseCoreYaw", getCoreYaw());
    tag.putFloat("NoiseMainPitch", getMainGunPitch());
    tag.putInt("NoiseMainLReload", leftMainReload);
    tag.putInt("NoiseMainRReload", rightMainReload);
    tag.putInt("NoiseMainLAmmo", leftMainAmmo);
    tag.putInt("NoiseMainRAmmo", rightMainAmmo);
    tag.putFloat("NoiseAutocannonLYaw", getAutocannonLYaw());
    tag.putFloat("NoiseAutocannonLPitch", getAutocannonLPitch());
    tag.putFloat("NoiseAutocannonRYaw", getAutocannonRYaw());
    tag.putFloat("NoiseAutocannonRPitch", getAutocannonRPitch());
    tag.putFloat("NoiseRoadWheelAngleL", getRoadWheelAngleL());
    tag.putFloat("NoiseRoadWheelAngleR", getRoadWheelAngleR());
    tag.putFloat("NoiseMissilePodLYaw", getMissilePodLYaw());
    tag.putFloat("NoiseMissilePodLPitch", getMissilePodLPitch());
    tag.putFloat("NoiseMissilePodRYaw", getMissilePodRYaw());
    tag.putFloat("NoiseMissilePodRPitch", getMissilePodRPitch());
    tag.putInt("NoiseVlsTube", vlsTubeIndex);
    tag.putInt("NoiseObstructedTicks", obstructedTargetTicks);
    tag.putInt("NoiseOutOfCombat", outOfCombatTicks);
    tag.putInt("NoiseVoiceStage", startupVoiceStage);
    tag.putInt("NoiseVoiceDelay", startupVoiceDelay);
    tag.putInt("NoiseVoiceCooldown", generalVoiceCooldown);
    tag.putInt("NoiseAttackVoiceCooldown", attackVoiceCooldown);
    tag.putInt("NoiseDamageVoiceCooldown", damageVoiceCooldown);
    tag.putInt("NoiseTargetVoiceCooldown", targetAcquiredVoiceCooldown);
    tag.putInt("NoiseDestructionTicks", getDestructionTicks());
    tag.putInt("NoiseIdleVoiceCooldown", idleVoiceCooldown);
    tag.putBoolean("NoiseHoldPosition", holdPosition);
    tag.putBoolean("NoiseEntranceShown", entranceShown);
    tag.putInt("NoiseEntranceTicks", getEntranceTicks());
    tag.putFloat("NoiseMothershipDropStart", getMothershipDropStart());
    tag.put("NoiseAutocannonL", writeAutocannonStation(autocannonLeft));
    tag.put("NoiseAutocannonR", writeAutocannonStation(autocannonRight));
    tag.put("NoiseMissileL", writeWeaponStation(missilePodLeft));
    tag.put("NoiseMissileR", writeWeaponStation(missilePodRight));
    tag.put("NoiseVls", writeWeaponStation(vlsBattery));
    tag.put("NoiseVlsR", writeWeaponStation(vlsBatteryRight));
    tag.put("NoiseAutonomy", DnvAutonomyMemoryCodec.encode(
        autonomyCore.memorySnapshot()));
    if (combatTargetId != null) tag.putUUID("NoiseTarget", combatTargetId);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.m_7378_(tag);
    resetTargetingForLoad();
    // Clamp dev.9 worlds that may still contain the former 25,000 health value.
    setHealth(Math.min(MAX_HEALTH, getHealth()));
    coreOrientationInitialized = tag.contains("NoiseCoreYaw", Tag.TAG_FLOAT);
    if (coreOrientationInitialized) {
      setCoreYaw(tag.getFloat("NoiseCoreYaw"));
    }
    setMainGunPitch(tag.getFloat("NoiseMainPitch"));
    leftMainReload = Math.max(0, tag.getInt("NoiseMainLReload"));
    rightMainReload = Math.max(0, tag.getInt("NoiseMainRReload"));
    leftMainAmmo = Mth.clamp(tag.getInt("NoiseMainLAmmo"), 0, MAIN_MAGAZINE);
    rightMainAmmo = Mth.clamp(tag.getInt("NoiseMainRAmmo"), 0, MAIN_MAGAZINE);
    if (tag.contains("NoiseAutocannonLYaw", Tag.TAG_FLOAT)) {
      entityData.set(AUTOCANNON_L_YAW, tag.getFloat("NoiseAutocannonLYaw"));
      entityData.set(AUTOCANNON_L_PITCH, tag.getFloat("NoiseAutocannonLPitch"));
      entityData.set(AUTOCANNON_R_YAW, tag.getFloat("NoiseAutocannonRYaw"));
      entityData.set(AUTOCANNON_R_PITCH, tag.getFloat("NoiseAutocannonRPitch"));
      entityData.set(MISSILE_POD_L_YAW, tag.getFloat("NoiseMissilePodLYaw"));
      entityData.set(MISSILE_POD_L_PITCH, tag.getFloat("NoiseMissilePodLPitch"));
      entityData.set(MISSILE_POD_R_YAW, tag.getFloat("NoiseMissilePodRYaw"));
      entityData.set(MISSILE_POD_R_PITCH, tag.getFloat("NoiseMissilePodRPitch"));
    }
    entityData.set(
        ROAD_WHEEL_ANGLE_L, Mth.positiveModulo(tag.getFloat("NoiseRoadWheelAngleL"), 360.0F));
    entityData.set(
        ROAD_WHEEL_ANGLE_R, Mth.positiveModulo(tag.getFloat("NoiseRoadWheelAngleR"), 360.0F));
    vlsTubeIndex = tag.getInt("NoiseVlsTube") & 1;
    obstructedTargetTicks = Math.max(0, tag.getInt("NoiseObstructedTicks"));
    outOfCombatTicks = Math.max(0, tag.getInt("NoiseOutOfCombat"));
    startupVoiceStage = Mth.clamp(tag.getInt("NoiseVoiceStage"), 0, 2);
    startupVoiceDelay = Math.max(0, tag.getInt("NoiseVoiceDelay"));
    generalVoiceCooldown = Math.max(0, tag.getInt("NoiseVoiceCooldown"));
    attackVoiceCooldown = Math.max(0, tag.getInt("NoiseAttackVoiceCooldown"));
    damageVoiceCooldown = Math.max(0, tag.getInt("NoiseDamageVoiceCooldown"));
    targetAcquiredVoiceCooldown = Math.max(0, tag.getInt("NoiseTargetVoiceCooldown"));
    int savedDestructionTicks =
        tag.contains("NoiseDestructionTicks", Tag.TAG_INT)
            ? Mth.clamp(tag.getInt("NoiseDestructionTicks"), 0, DESTRUCTION_SEQUENCE_TICKS)
            : (isWreck() || getHealth() <= 0.0F ? 1 : 0);
    setDestructionTicks(savedDestructionTicks);
    idleVoiceCooldown =
        tag.contains("NoiseIdleVoiceCooldown", Tag.TAG_INT)
            ? Math.max(0, tag.getInt("NoiseIdleVoiceCooldown"))
            : 120;
    holdPosition = tag.getBoolean("NoiseHoldPosition");
    entranceShown = tag.getBoolean("NoiseEntranceShown");
    // Summon NBT may omit both fields. Preserve the airborne birth pose before
    // the first server tick publishes the countdown; completed saves stay at 0.
    setEntranceTicks(entranceShown
        ? Mth.clamp(tag.getInt("NoiseEntranceTicks"), 0, ENTRANCE_EFFECT_TICKS)
        : ENTRANCE_EFFECT_TICKS);
    setMothershipDropStart(tag.contains("NoiseMothershipDropStart", Tag.TAG_FLOAT)
        ? tag.getFloat("NoiseMothershipDropStart") : -1F);
    pendingVoice = PENDING_VOICE_NONE;
    wasMoving = false;
    if (tag.contains("NoiseAutocannonL", Tag.TAG_COMPOUND)) {
      readAutocannonStation(autocannonLeft, tag.getCompound("NoiseAutocannonL"));
      readAutocannonStation(autocannonRight, tag.getCompound("NoiseAutocannonR"));
      readWeaponStation(missilePodLeft, tag.getCompound("NoiseMissileL"), MISSILE_MAGAZINE);
      readWeaponStation(missilePodRight, tag.getCompound("NoiseMissileR"), MISSILE_MAGAZINE);
      readWeaponStation(vlsBattery, tag.getCompound("NoiseVls"), VLS_MAGAZINE);
      if (tag.contains("NoiseVlsR", Tag.TAG_COMPOUND)) {
        readWeaponStation(vlsBatteryRight, tag.getCompound("NoiseVlsR"), VLS_MAGAZINE);
      } else {
        vlsBatteryRight.arm(VLS_MAGAZINE);
      }
    }
    combatTargetId = tag.hasUUID("NoiseTarget") ? tag.getUUID("NoiseTarget") : null;
    if (!level().isClientSide && tag.contains("NoiseAutonomy", Tag.TAG_COMPOUND)) {
      DnvAutonomyMemoryCodec.decode(tag.getCompound("NoiseAutonomy"))
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
    // Aim and lock observations belong to the old world tick and need fresh validation.
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
      station.cadenceIndex = NoiseAutocannonCadence.normalizeIndex(tag.getInt("Cadence"));
    }
  }

  @Override
  public Entity asEntity() {
    return this;
  }

  @Override
  public int getEntranceTicks() {
    return entityData.get(ENTRANCE_TICKS);
  }

  public float getMothershipDropStart() { return entityData.get(MOTHERSHIP_DROP_START); }

  public void setMothershipDropStart(float value) {
    entityData.set(MOTHERSHIP_DROP_START, NoiseDropTimeline.sanitizeStartOffset(value));
  }

  public double presentedVehicleOffset(double elapsed) {
    return NoiseDropTimeline.vehicleVerticalOffset(elapsed, getMothershipDropStart());
  }

  public double presentedRigOffset(double elapsed) {
    return NoiseDropTimeline.rigVerticalOffset(elapsed, getMothershipDropStart());
  }

  /** Behavior-controller compatibility: Noise intentionally has no teleport cooldown. */
  public int getTeleportCooldownTicks() {
    return 0;
  }

  public int getDestructionTicks() {
    return entityData.get(DESTRUCTION_TICKS);
  }

  protected void setDestructionTicks(int value) {
    entityData.set(DESTRUCTION_TICKS, Mth.clamp(value, 0, DESTRUCTION_SEQUENCE_TICKS));
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
    return getHealth() <= MAX_HEALTH * 0.25F;
  }

}
