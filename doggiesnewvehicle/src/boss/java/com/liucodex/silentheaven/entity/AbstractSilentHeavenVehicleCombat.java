package com.liucodex.silentheaven.entity;

import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.atsuishio.superbwarfare.entity.projectile.SwarmDroneEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.damage.DamageModifier;
import com.atsuishio.superbwarfare.init.ModTags;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetLineOfSight;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import com.liucodex.doggiesnewvehicle.entity.DnvVehicleCombatRules;
import com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor;
import com.liucodex.doggiesnewvehicle.core.DnvStarProjectileHits;
import com.liucodex.doggiesnewvehicle.combat.DnvDamageTypes;
import com.liucodex.silentheaven.init.ModItems;
import com.liucodex.silentheaven.init.ModSounds;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

abstract class AbstractSilentHeavenVehicleCombat extends AbstractSilentHeavenVehicleWeapons
    implements com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle {
  protected AbstractSilentHeavenVehicleCombat(
      EntityType<? extends SilentHeavenVehicleEntity> type, Level level) {
    super(type, level);
  }

  protected boolean hasClearShot(Vec3 from, Entity target) {
    return clearAimPoint(from, target).isPresent();
  }

  protected Optional<Vec3> clearAimPoint(Vec3 from, Entity target) {
    return DnvTargetLineOfSight.clearAimPoint(level(), this, from, target);
  }

  protected boolean withinAutocannonArc(Entity target, Vec3 muzzle) {
    Vec3 direction = target.getBoundingBox().getCenter().subtract(muzzle);
    return DnvVehicleCombatRules.withinArc(
        direction.x,
        direction.y,
        direction.z,
        getYRot(),
        AUTOCANNON_MAX_LOCAL_YAW,
        AUTOCANNON_MIN_PITCH,
        AUTOCANNON_MAX_PITCH);
  }

  protected void tickDroneLauncher(@Nullable Entity target) {
    if (droneLaunchCooldown > 0) {
      droneLaunchCooldown--;
    }
    if (droneLaunchCooldown > 0
        || !isTrackedTargetValid(target)
        || !(level() instanceof ServerLevel serverLevel)
        || countActiveDrones(serverLevel) >= MAX_ACTIVE_DRONES) {
      return;
    }
    boolean left = nextDroneLaunchLeft;
    nextDroneLaunchLeft = !nextDroneLaunchLeft;
    Vec3 spawn = corePointToWorld(new Vec3(left ? 3.324988D : -3.324988D, 3.574424D, -1.117110D));
    if (!hasClearShot(spawn, target)) return;
    Vec3 toward = target.getBoundingBox().getCenter().subtract(spawn).normalize();
    Vec3 launchDirection =
        new Vec3(toward.x, Math.max(0.45D, toward.y + 0.35D), toward.z).normalize();
    SwarmDroneEntity drone =
        com.atsuishio.superbwarfare.init.ModEntities.SWARM_DRONE.get().create(serverLevel);
    if (drone == null) {
      droneLaunchCooldown = 20;
      return;
    }
    drone.setOwner(this);
    com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects.suppressCameraShake(drone);
    drone.setTargetUuid(target.getStringUUID());
    drone.setGuideType(0);
    drone.setTargetVec(target.getBoundingBox().getCenter());
    drone.moveTo(spawn.x, spawn.y, spawn.z);
    drone.shoot(
        launchDirection.x, launchDirection.y, launchDirection.z, DRONE_INITIAL_VELOCITY, 1.0F);
    drone.setDeltaMovement(launchDirection.scale(DRONE_INITIAL_VELOCITY));
    drone.getPersistentData().putBoolean(OWNED_ENTITY_TAG, true);
    drone.getPersistentData().putUUID(OWNER_UUID_TAG, getUUID());
    serverLevel.addFreshEntity(drone);
    playExternalSound("superbwarfare:yx_100_swarm_drone_release", spawn, 8.0F, 0.90F);
    droneLaunchCooldown = DRONE_LAUNCH_COOLDOWN;
  }

  protected int countActiveDrones(ServerLevel serverLevel) {
    return serverLevel
        .getEntitiesOfClass(
            SwarmDroneEntity.class,
            getBoundingBox().inflate(TARGET_RANGE * 2.0D),
            drone ->
                drone.isAlive()
                    && drone.getPersistentData().hasUUID(OWNER_UUID_TAG)
                    && getUUID().equals(drone.getPersistentData().getUUID(OWNER_UUID_TAG)))
        .size();
  }

  protected void tickHaloWeapon(@Nullable Entity target) {
    int reload = entityData.get(HALO_RELOAD);
    if (reload > 0) {
      reload--;
      entityData.set(HALO_RELOAD, reload);
      if (reload == 0) {
        entityData.set(HALO_AMMO, HALO_MAGAZINE);
        haloTargetId = null;
      }
    }
    if (haloShotCooldown > 0) {
      haloShotCooldown--;
    }
    if (reload > 0 || haloShotCooldown > 0 || entityData.get(HALO_AMMO) <= 0) {
      return;
    }
    Entity haloTarget = resolveHaloTarget(target);
    if (haloTarget == null) {
      return;
    }
    if (!com.liucodex.doggiesnewvehicle.compat.DnvCombatCoordinator.fireAuthorizedBoss(
        this, haloTarget)) {
      return;
    }
    fireHaloShot(haloTarget);
    int remaining = entityData.get(HALO_AMMO) - 1;
    entityData.set(HALO_AMMO, remaining);
    haloShotCooldown = HALO_INTERVAL;
    if (remaining <= 0) {
      entityData.set(HALO_RELOAD, HALO_RELOAD_TICKS);
      haloTargetId = null;
    }
  }

  @Nullable
  protected Entity resolveHaloTarget(@Nullable Entity preferredTarget) {
    Entity current = null;
    if (haloTargetId != null && level() instanceof ServerLevel serverLevel) {
      current = serverLevel.getEntity(haloTargetId);
    }
    if (canHaloAttack(current)) {
      return current;
    }
    haloTargetId = null;
    if (canHaloAttack(preferredTarget)) {
      haloTargetId = preferredTarget.getUUID();
      return preferredTarget;
    }
    return null;
  }

  protected boolean canHaloAttack(@Nullable Entity target) {
    if (!isTrackedTargetValid(target)) {
      return false;
    }
    double distance = distanceTo(target);
    if (distance < HALO_MIN_RANGE || distance > HALO_MAX_RANGE) {
      return false;
    }
    return hasClearShot(position().add(0.0D, 7.84D, 0.0D), target);
  }

  protected void fireHaloShot(Entity target) {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return;
    }
    Vec3 center = target.getBoundingBox().getCenter();
    Vec3 impact = new Vec3(center.x, target.getBoundingBox().minY, center.z);
    target.hurt(createHaloDamageSource(serverLevel), HALO_DIRECT_DAMAGE);
    LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
    if (lightning != null) {
      lightning.moveTo(impact.x, impact.y, impact.z);
      lightning.setVisualOnly(true);
      serverLevel.addFreshEntity(lightning);
    }
    playExternalSound("doggiesnewvehicle:jingzhe_attack", impact, 9.0F, 0.88F);
    pendingStrikes.add(new PendingStrike(impact, HALO_STRIKE_DELAY));
  }

  protected DamageSource createHaloDamageSource(ServerLevel serverLevel) {
    Holder<DamageType> type =
        serverLevel
            .registryAccess()
            .registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(DnvDamageTypes.SILENT_HEAVEN_HALO_PIERCING);
    return new com.liucodex.doggiesnewvehicle.core.DnvDirectHitSource(
        type, this, this, com.liucodex.doggiesnewvehicle.combat.BossPercentDamage.fraction("silent_heaven_halo"));
  }

  protected void tickPendingStrikes() {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return;
    }
    for (int index = pendingStrikes.size() - 1; index >= 0; index--) {
      PendingStrike strike = pendingStrikes.get(index);
      strike.ticks--;
      if (strike.ticks <= 0) {
        spawnFallingWarhead(serverLevel, strike.impact);
        pendingStrikes.remove(index);
      }
    }
  }

  protected void stabilizeFallingWarheads() {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return;
    }
    recoverFallingWarheads(serverLevel);
    for (int index = fallingWarheads.size() - 1; index >= 0; index--) {
      CannonShellEntity warhead = fallingWarheads.get(index);
      CompoundTag data = warhead.getPersistentData();
      if (warhead.isRemoved()
          || !data.contains("SilentHeavenStrikeX", Tag.TAG_DOUBLE)
          || !data.contains("SilentHeavenStrikeZ", Tag.TAG_DOUBLE)) {
        fallingWarheads.remove(index);
        continue;
      }
      warhead.setPos(
          data.getDouble("SilentHeavenStrikeX"),
          warhead.getY(),
          data.getDouble("SilentHeavenStrikeZ"));
      warhead.setYRot(0.0F);
      // FastThrowableProjectile derives -90 degrees from a straight
      // downward velocity. Keeping both current and previous rotation
      // on that same value prevents the renderer interpolating across
      // a 180-degree disagreement every tick.
      warhead.setXRot(-90.0F);
      warhead.yRotO = 0.0F;
      warhead.xRotO = -90.0F;
      warhead.setDeltaMovement(0.0D, -2.5D, 0.0D);
    }
  }

  protected void recoverFallingWarheads(ServerLevel serverLevel) {
    if (!DnvTargetingBudget.isScanDue(
        tickCount, getId() + 47, lastWarheadRecoveryTick, OWNED_PROJECTILE_RECOVERY_INTERVAL)) {
      return;
    }
    lastWarheadRecoveryTick = tickCount;
    for (CannonShellEntity warhead :
        serverLevel.getEntitiesOfClass(
            CannonShellEntity.class,
            getBoundingBox().inflate(TARGET_RANGE),
            candidate ->
                candidate.getPersistentData().hasUUID(OWNER_UUID_TAG)
                    && getUUID().equals(candidate.getPersistentData().getUUID(OWNER_UUID_TAG))
                    && candidate
                        .getPersistentData()
                        .contains("SilentHeavenStrikeX", Tag.TAG_DOUBLE))) {
      if (!fallingWarheads.contains(warhead)) {
        fallingWarheads.add(warhead);
      }
    }
  }

  protected void spawnFallingWarhead(ServerLevel serverLevel, Vec3 impact) {
    // A compact cannon shell is deliberately used instead of MK-82 here.
    // The former bomb mesh had a strong authored forward axis and appeared
    // sideways even while its physics velocity was perfectly vertical.
    // This smaller, near-round projectile keeps the strike readable from
    // every camera angle while preserving the requested 5-block/200-damage
    // delayed impact.
    CannonShellEntity warhead =
        com.atsuishio.superbwarfare.init.ModEntities.CANNON_SHELL.get().create(serverLevel);
    if (warhead == null) {
      return;
    }
    double spawnY = Math.min(serverLevel.getMaxBuildHeight() - 2.0D, impact.y + 24.0D);
    warhead.setOwner(this);
    com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects.suppressCameraShake(warhead);
    warhead.setDamage(0.0F);
    warhead.setExplosionDamage(HALO_EXPLOSION_DAMAGE);
    warhead.setExplosionRadius(HALO_EXPLOSION_RADIUS);
    warhead.setType(CannonShellEntity.Type.HE);
    warhead.setCustomGravity(0.08F);
    warhead.moveTo(impact.x, spawnY, impact.z, 0.0F, -90.0F);
    warhead.shoot(0.0D, -1.0D, 0.0D, 2.5F, 0.0F);
    warhead.setDeltaMovement(0.0D, -2.5D, 0.0D);
    warhead.getPersistentData().putUUID(OWNER_UUID_TAG, getUUID());
    warhead.getPersistentData().putDouble("SilentHeavenStrikeX", impact.x);
    warhead.getPersistentData().putDouble("SilentHeavenStrikeZ", impact.z);
    com.liucodex.doggiesnewvehicle.api.combat.DnvProjectileLifetime.track(warhead, warhead.getLife());
    if (serverLevel.addFreshEntity(warhead)) {
      fallingWarheads.add(warhead);
    }
  }

  protected void beginTransition() {
    if (getPhase() != PHASE_ONE) return;
    // The first 4,000 health has been exhausted. Keep one internal health point
    // while SBW ticks the transition; the bar presents this as zero.
    setHealth(TRANSITION_SURVIVAL_HEALTH);
    setPhase(PHASE_TRANSITION);
    setTransitionTick(0);
    setHaloAngle(0.0F);
    combatTargetId = null;
    requestedThrottle = 0.0F;
    requestedSteering = 0.0F;
    entityData.set(DRIVE_INTENSITY, 0.0F);
    entityData.set(HALO_AMMO, 0);
    entityData.set(HALO_RELOAD, 0);
    haloShotCooldown = 0;
    autocannonLeft.disable();
    autocannonRight.disable();
    missilePodLeft.disable();
    missilePodRight.disable();
    vlsBattery.disableAndEmpty();
    haloTargetId = null;
    lockForTransition();
    bossEvent.setColor(BossEvent.BossBarColor.RED);
    playVoice(ModSounds.VOICE_SYSTEMS_DAMAGED.get());
    generalVoiceCooldown = GENERAL_VOICE_COOLDOWN;
    playExternalSound(
        "doggiesnewvehicle:jingzhe_activate", position().add(0.0D, 3.0D, 0.0D), 8.0F, 0.82F);
  }

  protected void tickTransition() {
    int tick = getTransitionTick() + 1;
    setTransitionTick(tick);
    lockForTransition();
    if (tick >= TRANSITION_HALO_START_TICK) {
      setHaloAngle(getHaloAngle() + 4.0F);
    }
    if (tick % 4 == 0 && level() instanceof ServerLevel serverLevel) {
      serverLevel.sendParticles(
          ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.4D, getZ(), 5, 1.6D, 0.7D, 1.6D, 0.025D);
    }
    if (tick >= TRANSITION_HEAL_START_TICK) {
      float progress =
          Mth.clamp(
              (tick - TRANSITION_HEAL_START_TICK)
                  / (float) (TRANSITION_TICKS - TRANSITION_HEAL_START_TICK),
              0.0F,
              1.0F);
      setHealth(Mth.lerp(progress, TRANSITION_SURVIVAL_HEALTH, PHASE_TWO_START_HEALTH));
    }
    if (tick >= TRANSITION_TICKS) {
      setHealth(PHASE_TWO_START_HEALTH);
      setPhase(PHASE_TWO);
      setTransitionTick(TRANSITION_TICKS);
      entityData.set(HALO_AMMO, HALO_MAGAZINE);
      entityData.set(HALO_RELOAD, 0);
      haloShotCooldown = 5;
      droneLaunchCooldown = Math.min(droneLaunchCooldown, 40);
      vlsBattery.arm(VLS_MAGAZINE);
      playPhaseTwoPresentation();
    }
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    if (isWreck() || getDestructionTicks() > 0 || getHealth() <= 0.0F) {
      return false;
    }
    if (source.getEntity() == this
        || source.getDirectEntity() != null && isSilentHeavenOwned(source.getDirectEntity())) {
      return false;
    }
    if (isEntering() || isTransitioning()) return false;
    if (amount <= 0.0F || isDamageImmune(source)) {
      return false;
    }
    if (DnvStarProjectileHits.starAlreadyHit(source, getUUID())) return false;

    var category = classifyDamage(source);
    float mitigated = DnvVehicleCombatRules.applyPhaseDamage(getPhase(), category, amount);
    float appliedDamage = category == DnvVehicleCombatRules.DamageCategory.OTHER ? mitigated
        : DnvTaggedProjectileArmor.apply(source, amount, mitigated);
    appliedDamage = com.liucodex.doggiesnewvehicle.core.DnvPercentHits.apply(this, source, amount, appliedDamage);
    if (appliedDamage <= 0.0F) {
      return false;
    }
    outOfCombatTicks = 0;
    if (getPhase() == PHASE_ONE && getHealth() - appliedDamage <= PHASE_THRESHOLD) {
      float before = getHealth();
      float damageToThreshold = SilentHeavenPhaseHealth.beforeTransitionDamage(getHealth());
      if (damageToThreshold <= 0.0F) return false;
      boolean result = super.hurt(source, damageToThreshold);
      // SBW can subtract health in onHurt while Entity.hurt still returns false.
      // A rejected native hit leaves health unchanged and cannot start phase two.
      if (getHealth() >= before) return false;
      DnvStarProjectileHits.recordStarHit(source, getUUID());
      beginTransition();
      return result || getPhase() == PHASE_TRANSITION;
    }

    tryPlayDamageVoice();
    float before = getHealth();
    boolean result = super.hurt(source, appliedDamage);
    if (getHealth() < before) DnvStarProjectileHits.recordStarHit(source, getUUID());
    if (getPhase() == PHASE_ONE && getHealth() <= PHASE_THRESHOLD) {
      beginTransition();
    }
    return result;
  }

  protected boolean isDamageImmune(DamageSource source) {
    return source.is(ModTags.DamageTypes.VEHICLE_IMMUNE)
        || source.is(DamageTypes.ARROW)
        || source.is(DamageTypes.TRIDENT)
        || source.is(DamageTypes.MOB_ATTACK)
        || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
        || source.is(DamageTypes.PLAYER_ATTACK);
  }

  protected DnvVehicleCombatRules.DamageCategory classifyDamage(DamageSource source) {
    if (source instanceof com.liucodex.doggiesnewvehicle.core.DnvDirectArmorPenetration)
      return DnvVehicleCombatRules.DamageCategory.BULLET;
    if (source.is(com.liucodex.doggiesnewvehicle.core.DnvArmorDamageTags.SPECIAL))
      return source.is(DamageTypeTags.IS_EXPLOSION)
          ? DnvVehicleCombatRules.DamageCategory.SPECIAL_EXPLOSION
          : DnvVehicleCombatRules.DamageCategory.OTHER;
    if (source.is(DamageTypeTags.IS_EXPLOSION)
        || source.is(com.atsuishio.superbwarfare.init.ModDamageTypes.CUSTOM_EXPLOSION)
        || source.is(com.atsuishio.superbwarfare.init.ModDamageTypes.PROJECTILE_EXPLOSION)
        || source.is(com.atsuishio.superbwarfare.init.ModDamageTypes.VEHICLE_EXPLOSION)) {
      return DnvVehicleCombatRules.DamageCategory.EXPLOSION;
    }
    if (source.is(com.atsuishio.superbwarfare.init.ModDamageTypes.PROJECTILE_HIT)
        || source.is(com.atsuishio.superbwarfare.init.ModDamageTypes.PROJECTILE_HIT_HEADSHOT)
        || source.is(com.atsuishio.superbwarfare.init.ModDamageTypes.GRAPESHOT_HIT)) {
      return DnvVehicleCombatRules.DamageCategory.HEAVY_DIRECT;
    }
    if (source.is(ModTags.DamageTypes.PROJECTILE)
        || source.is(ModTags.DamageTypes.PROJECTILE_ABSOLUTE)
        || source.is(DamageTypeTags.IS_PROJECTILE)) {
      return DnvVehicleCombatRules.DamageCategory.BULLET;
    }
    return DnvVehicleCombatRules.DamageCategory.OTHER;
  }

  @Override
  public DamageModifier getDamageModifier() {
    // Phase-aware damage has already been calculated in hurt(). Returning
    // an identity modifier prevents SBW's JSON modifier chain from
    // applying a second multiplier or the former flat armor subtraction.
    return IDENTITY_DAMAGE_MODIFIER;
  }

  protected void updateBossBar() {
    if (isRemoved() || getHealth() <= 0.0F) {
      clearBossBar();
      return;
    }
    bossEvent.setVisible(true);
    bossEvent.setProgress(SilentHeavenPhaseHealth.barProgress(
        getPhase(), getTransitionTick(), TRANSITION_HEAL_START_TICK, getHealth()));
    bossEvent.setName(getBossBarName());
    if (!(level() instanceof ServerLevel serverLevel) || tickCount % 10 != 0) return;
    List<ServerPlayer> allPlayers = serverLevel.getServer().getPlayerList().getPlayers();
    for (ServerPlayer player : allPlayers) {
      boolean visible =
          player.level() == level()
              && !player.isSpectator()
              && distanceToSqr(player) <= 192.0D * 192.0D;
      if (visible) bossEvent.addPlayer(player);
      else bossEvent.removePlayer(player);
    }
  }

  protected void clearBossBar() {
    bossEvent.setVisible(false);
    bossEvent.removeAllPlayers();
  }

  protected Component getBossBarName() {
    return switch (getPhase()) {
      case PHASE_TRANSITION -> Component.translatable("bossbar.silent_heaven.unknown");
      case PHASE_TWO -> Component.translatable("bossbar.silent_heaven.silent_heaven");
      default -> Component.translatable("bossbar.silent_heaven.guardian");
    };
  }

  protected void playExternalSound(String id, Vec3 source, float volume, float pitch) {
    SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.parse(id));
    if (event != null) {
      level()
          .playSound(null, source.x, source.y, source.z, event, SoundSource.BLOCKS, volume, pitch);
    }
  }

  protected void playVoice(SoundEvent event) {
    level().playSound(null, getX(), getY() + 3.0D, getZ(), event, SoundSource.HOSTILE, 2.0F, 1.0F);
  }

  @Override
  public boolean engineRunning() {
    return getDriveIntensity() > 0.015F;
  }

  @Override
  public InteractionResult interact(Player player, InteractionHand hand) {
    ItemStack heldItem = player.getItemInHand(hand);
    if (com.liucodex.doggiesnewvehicle.boss.DnvBossInteraction.isRetrievalTool(heldItem))
      return com.liucodex.doggiesnewvehicle.boss.DnvBossInteraction.rejectRetrieval(player);
    if (heldItem.is(ModItems.SILENT_HEAVEN_BEHAVIOR_CONTROLLER.get())) {
      return ModItems.SILENT_HEAVEN_BEHAVIOR_CONTROLLER.get()
          .bind(heldItem, self(), player);
    }
    return super.interact(player, hand);
  }

  public boolean isHoldingPosition() {
    return holdPosition;
  }

  public void setHoldingPosition(boolean hold) {
    holdPosition = hold;
    patrolDestination = null;
    requestedThrottle = 0.0F;
    requestedSteering = 0.0F;
    if (hold) {
      cancelTeleportSequence();
      entityData.set(POWER, 0.0F);
      entityData.set(DELTA_ROT, 0.0F);
      Vec3 motion = getDeltaMovement();
      setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
    }
  }

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  public void push(double x, double y, double z) {
    // 300-ton autonomous platform: ordinary entity collision cannot move it.
  }

  @Override
  public void preventStacking() {
    // SBW's generic vehicle overlap resolver bypasses push() through
    // pushNew() and can accumulate a large three-axis impulse when several
    // boss-sized collision boxes overlap. Boss platforms resolve movement
    // against blocks normally, but never receive that stacking impulse.
  }

  @Override
  public void pushNew(double x, double y, double z) {
    // Keep the same immovable contract for direct SBW collision impulses.
  }

  @Override
  public PushReaction getPistonPushReaction() {
    return PushReaction.BLOCK;
  }

  @Override
  public void openCustomInventoryScreen(Player player) {
    // Autonomous boss vehicle has no player-facing inventory. This method
    // deliberately satisfies the VehicleEntity container contract without
    // opening a vehicle menu.
  }

  @Override
  public void remove(RemovalReason reason) {
    releaseExternalState(reason);
    super.remove(reason);
  }

  @Override public void releaseExternalState(RemovalReason reason) {
    com.liucodex.doggiesnewvehicle.compat.DnvBossTeamMembership
        .releaseOnTerminalRemoval(this, reason);
    clearBossBar();
    clearCombatStateForWreck();
  }

  @Override
  public void onRemovedFromWorld() {
    releaseExternalState(getRemovalReason());
    super.onRemovedFromWorld();
  }

  @Override
  public void destroy() {
    if (isRemoved() || getDestructionTicks() > 0) {
      return;
    }
    clearCombatStateForWreck();
    setWreck(true);
    setHealth(0.0F);
    setDeltaMovement(Vec3.ZERO);
    setDestructionTicks(DESTRUCTION_SEQUENCE_TICKS);
  }

}
