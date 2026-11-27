package com.liucodex.noise.entity;

import com.atsuishio.superbwarfare.entity.vehicle.damage.DamageModifier;
import com.atsuishio.superbwarfare.init.ModTags;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetLineOfSight;
import com.liucodex.doggiesnewvehicle.entity.DnvVehicleCombatRules;
import com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor;
import com.liucodex.doggiesnewvehicle.core.DnvStarProjectileHits;
import com.liucodex.noise.init.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

abstract class AbstractNoiseVehicleCombat extends AbstractNoiseVehicleWeapons
    implements com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle {
  protected AbstractNoiseVehicleCombat(
      EntityType<? extends NoiseVehicleEntity> type, Level level) {
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

  @Override
  public boolean hurt(DamageSource source, float amount) {
    if (isWreck() || getDestructionTicks() > 0 || getHealth() <= 0.0F) {
      return false;
    }
    if (source.getEntity() == this
        || source.getDirectEntity() != null && isNoiseOwned(source.getDirectEntity())) {
      return false;
    }
    if (isEntering()) return false;
    if (amount <= 0.0F || isDamageImmune(source)) {
      return false;
    }
    if (DnvStarProjectileHits.starAlreadyHit(source, getUUID())) return false;

    var category = classifyDamage(source);
    float mitigated = DnvVehicleCombatRules.applyBaseDamage(category, amount);
    float appliedDamage = category == DnvVehicleCombatRules.DamageCategory.OTHER ? mitigated
        : DnvTaggedProjectileArmor.apply(source, amount, mitigated);
    appliedDamage = com.liucodex.doggiesnewvehicle.core.DnvPercentHits.apply(this, source, amount, appliedDamage);
    if (appliedDamage <= 0.0F) {
      return false;
    }
    outOfCombatTicks = 0;
    tryPlayDamageVoice();
    float before = getHealth();
    boolean result = super.hurt(source, appliedDamage);
    if (getHealth() < before) DnvStarProjectileHits.recordStarHit(source, getUUID());
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
    // Noise's single-phase damage has already been calculated in hurt(). Returning
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
    bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
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
    return Component.translatable("bossbar.noise.noise");
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
    if (heldItem.is(ModItems.NOISE_BEHAVIOR_CONTROLLER.get())) {
      return ModItems.NOISE_BEHAVIOR_CONTROLLER.get()
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
      resetObstructionTracking();
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
    // Native setRemoved and other direct world removals can bypass remove().
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
