package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.liucodex.doggiesnewvehicle.core.DnvTurretTargetMemory;
import com.liucodex.doggiesnewvehicle.core.DnvDirectArmorPenetration;
import com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

abstract class AbstractJingzheTurretEffects extends AbstractJingzheTurretDeployment {
  protected AbstractJingzheTurretEffects(
      EntityType<? extends JingzheTurretEntity> type, Level level) {
    super(type, level);
  }

  protected boolean releaseAttack(int module, Entity target) {
    if (!(level() instanceof ServerLevel serverLevel) || !hasVerticalDropPath(target)) {
      resetModuleToReady(module);
      return false;
    }

    // Commit the prepaid module before world/damage callbacks. A callback failure cannot replay it.
    lastReleaseTick = tickCount;
    moduleTarget[module] = NO_TARGET;
    moduleState[module] = MODULE_FADING;
    moduleTimer[module] = 0;
    moduleCharge[module] = 1000;
    invalidateModuleTargetMemory(
        module, DnvTurretTargetMemory.InvalidationReason.TARGET_UNAVAILABLE);
    publishModule(module);

    Vec3 dischargeOrigin =
        moduleFxUpWorldPosition(module, MODULE_OUTWARD_DISTANCE, MODULE_UPWARD_DISTANCE);
    spawnModuleSkyDischarge(serverLevel, dischargeOrigin);
    serverLevel.playSound(
        null,
        dischargeOrigin.x,
        dischargeOrigin.y,
        dischargeOrigin.z,
        SoundEvents.LIGHTNING_BOLT_THUNDER,
        SoundSource.WEATHER,
        0.75F,
        1.15F);

    AABB bounds = target.getBoundingBox();
    double x = bounds.getCenter().x;
    double z = bounds.getCenter().z;
    double bottom = bounds.maxY + 0.05D;
    double top = bottom + DROP_SPAWN_HEIGHT;
    for (int step = 0; step <= 16; step += 2) {
      double y = Mth.lerp(step / 16.0D, top, bottom);
      serverLevel.sendParticles(
          ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.06D, 0.08D, 0.06D, 0.015D);
    }
    serverLevel.sendParticles(ParticleTypes.END_ROD, x, bottom, z, 6, 0.18D, 0.18D, 0.18D, 0.025D);
    serverLevel.sendParticles(
        ParticleTypes.FLASH, x, bottom + 0.20D, z, 3, 0.08D, 0.12D, 0.08D, 0.0D);

    LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
    if (lightning != null) {
      lightning.moveTo(x, bounds.minY, z);
      lightning.setVisualOnly(true);
      serverLevel.addFreshEntity(lightning);
    }

    // The first projectile is direct-only. The second projectile is
    // resolved later so a boss can receive both hits without Minecraft's
    // short hurt-invulnerability window discarding the smaller explosion.
    var lightningType = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
        .getHolderOrThrow(DamageTypes.LIGHTNING_BOLT);
    target.hurt(new DnvDirectArmorPenetration(lightningType, this, this, 0.60F,
        com.liucodex.doggiesnewvehicle.artillery.ArtilleryPercentDamage.fraction("jingzhe")), ATTACK_DAMAGE);
    Vec3 impactCenter = new Vec3(x, bounds.minY + 0.06D, z);
    pendingSecondaryStrikes.add(
        new SecondaryStrike(target.getStringUUID(), impactCenter, SECONDARY_PROJECTILE_TICKS));

    return true;
  }

  protected void spawnModuleSkyDischarge(ServerLevel serverLevel, Vec3 origin) {
    for (int step = 0; step <= 24; step += 2) {
      double height = step * 0.65D;
      serverLevel.sendParticles(
          ParticleTypes.ELECTRIC_SPARK,
          origin.x,
          origin.y + height,
          origin.z,
          2,
          0.07D + step * 0.002D,
          0.10D,
          0.07D + step * 0.002D,
          0.018D);
      if ((step & 3) == 0) {
        serverLevel.sendParticles(
            ParticleTypes.END_ROD,
            origin.x,
            origin.y + height,
            origin.z,
            1,
            0.045D,
            0.08D,
            0.045D,
            0.012D);
      }
    }
    serverLevel.sendParticles(
        ParticleTypes.FLASH, origin.x, origin.y + 15.6D, origin.z, 2, 0.1D, 0.1D, 0.1D, 0.0D);
  }

  protected void tickPendingSecondaryStrikes() {
    if (!(level() instanceof ServerLevel serverLevel)) {
      pendingSecondaryStrikes.clear();
      return;
    }
    for (int index = pendingSecondaryStrikes.size() - 1; index >= 0; index--) {
      SecondaryStrike strike = pendingSecondaryStrikes.get(index);
      Entity target = EntityFindUtil.findEntity(level(), strike.targetId);
      if (target != null && target.isAlive()) {
        AABB bounds = target.getBoundingBox();
        strike.impact = new Vec3(bounds.getCenter().x, bounds.minY + 0.06D, bounds.getCenter().z);
      }

      double progress = 1.0D - strike.ticks / (double) SECONDARY_PROJECTILE_TICKS;
      double projectileY =
          strike.impact.y + DROP_SPAWN_HEIGHT * (1.0D - Mth.clamp(progress, 0.0D, 1.0D));
      serverLevel.sendParticles(
          ParticleTypes.ELECTRIC_SPARK,
          strike.impact.x,
          projectileY,
          strike.impact.z,
          4,
          0.05D,
          0.08D,
          0.05D,
          0.018D);
      serverLevel.sendParticles(
          ParticleTypes.END_ROD,
          strike.impact.x,
          projectileY,
          strike.impact.z,
          1,
          0.025D,
          0.04D,
          0.025D,
          0.01D);

      strike.ticks--;
      if (strike.ticks <= 0) {
        detonateSecondaryStrike(serverLevel, strike.impact);
        pendingSecondaryStrikes.remove(index);
      }
    }
  }

  protected void detonateSecondaryStrike(ServerLevel serverLevel, Vec3 center) {
    serverLevel.playSound(
        null,
        center.x,
        center.y,
        center.z,
        SoundEvents.GENERIC_EXPLODE,
        SoundSource.HOSTILE,
        0.8F,
        1.15F);
    serverLevel.sendParticles(
        ParticleTypes.EXPLOSION_EMITTER,
        center.x,
        center.y + 0.12D,
        center.z,
        1,
        0.0D,
        0.0D,
        0.0D,
        0.0D);
    serverLevel.sendParticles(
        ParticleTypes.FLAME, center.x, center.y + 0.16D, center.z, 18, 0.52D, 0.18D, 0.52D, 0.035D);
    serverLevel.sendParticles(
        ParticleTypes.CLOUD, center.x, center.y + 0.10D, center.z, 18, 0.50D, 0.12D, 0.50D, 0.04D);
    applyImpactAreaDamage(serverLevel, center);
    groundFlameEffects.add(new GroundFlameEffect(center, GROUND_FLAME_TICKS));
  }

  protected void applyImpactAreaDamage(ServerLevel serverLevel, Vec3 center) {
    AABB area = new AABB(center, center).inflate(IMPACT_AOE_RADIUS);
    Entity owner = getOwner();
    for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
      if (entity == owner
          || !entity.isAlive()
          || isPvpProtected(entity)
          || isAlliedTo(entity)
          || owner != null && owner.isAlliedTo(entity)) {
        continue;
      }
      double distance = distanceToAabb(center, entity.getBoundingBox());
      if (distance >= IMPACT_AOE_RADIUS) {
        continue;
      }
      float falloff = (float) (1.0D - distance / IMPACT_AOE_RADIUS);
      entity.hurt(damageSources().explosion(this, owner), IMPACT_AOE_DAMAGE * falloff);
    }
  }

  protected static double distanceToAabb(Vec3 point, AABB bounds) {
    double dx = Math.max(Math.max(bounds.minX - point.x, 0.0D), point.x - bounds.maxX);
    double dy = Math.max(Math.max(bounds.minY - point.y, 0.0D), point.y - bounds.maxY);
    double dz = Math.max(Math.max(bounds.minZ - point.z, 0.0D), point.z - bounds.maxZ);
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  protected void tickGroundFlameEffects() {
    if (!(level() instanceof ServerLevel serverLevel)) {
      groundFlameEffects.clear();
      return;
    }
    for (int index = groundFlameEffects.size() - 1; index >= 0; index--) {
      GroundFlameEffect effect = groundFlameEffects.get(index);
      effect.age++;
      if ((effect.age & 1) == 0) {
        double expansion = Mth.clamp(effect.age / (double) GROUND_FLAME_EXPAND_TICKS, 0.0D, 1.0D);
        double radius = IMPACT_AOE_RADIUS * expansion;
        int points = 24;
        for (int particle = 0; particle < points; particle++) {
          double angle = Math.PI * 2.0D * particle / points;
          double x = effect.center.x + Math.cos(angle) * radius;
          double z = effect.center.z + Math.sin(angle) * radius;
          serverLevel.sendParticles(
              particle % 4 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
              x,
              effect.center.y + 0.08D,
              z,
              1,
              0.025D,
              0.04D,
              0.025D,
              0.003D);
        }
      }
      if (effect.age % 4 == 0 && effect.age <= GROUND_FLAME_EXPAND_TICKS) {
        clearGeneratedFire(serverLevel, effect);
        double expansion = Mth.clamp(effect.age / (double) GROUND_FLAME_EXPAND_TICKS, 0.0D, 1.0D);
        placeFireRing(serverLevel, effect, IMPACT_AOE_RADIUS * expansion);
      }
      effect.ticks--;
      if (effect.ticks <= 0) {
        clearGeneratedFire(serverLevel, effect);
        groundFlameEffects.remove(index);
      }
    }
  }

  protected void placeFireRing(ServerLevel serverLevel, GroundFlameEffect effect, double radius) {
    if (!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(serverLevel, this)) return;
    if (radius < 0.35D) {
      return;
    }
    int points = 24;
    for (int point = 0; point < points; point++) {
      double angle = Math.PI * 2.0D * point / points;
      double x = effect.center.x + Math.cos(angle) * radius;
      double z = effect.center.z + Math.sin(angle) * radius;
      BlockPos firePos = findFirePlacement(serverLevel, x, effect.center.y, z);
      if (firePos == null || !serverLevel.getBlockState(firePos).isAir()) {
        continue;
      }
      BlockPos supportPos = firePos.below();
      if (!serverLevel
          .getBlockState(supportPos)
          .isFaceSturdy(serverLevel, supportPos, Direction.UP)) {
        continue;
      }
      com.liucodex.doggiesnewvehicle.world.DnvControlledFire.place(serverLevel, firePos, this,
          com.liucodex.doggiesnewvehicle.world.DnvControlledFire.FIRE.get());
      if (serverLevel.getBlockState(firePos).is(com.liucodex.doggiesnewvehicle.world.DnvControlledFire.FIRE.get()))
        effect.fireBlocks.add(firePos.immutable());
    }
  }

  protected static BlockPos findFirePlacement(ServerLevel serverLevel, double x, double y, double z) {
    BlockPos origin = BlockPos.containing(x, y + 0.20D, z);
    for (int offset = 1; offset >= -3; offset--) {
      BlockPos candidate = origin.offset(0, offset, 0);
      if (serverLevel.getBlockState(candidate).isAir()) {
        BlockPos support = candidate.below();
        if (serverLevel.getBlockState(support).isFaceSturdy(serverLevel, support, Direction.UP)) {
          return candidate;
        }
      }
    }
    return null;
  }

  protected static void clearGeneratedFire(ServerLevel serverLevel, GroundFlameEffect effect) {
    for (BlockPos firePos : effect.fireBlocks) {
      if (serverLevel.getBlockState(firePos).is(com.liucodex.doggiesnewvehicle.world.DnvControlledFire.FIRE.get())) {
        serverLevel.removeBlock(firePos, false);
      }
    }
    effect.fireBlocks.clear();
  }

  protected void spawnModuleWindupParticle(int module, int windupTick) {
    if (!(level() instanceof ServerLevel serverLevel)) {
      return;
    }
    double progress = Mth.clamp(windupTick / (double) WINDUP_TICKS, 0.0D, 1.0D);
    Vec3 position =
        moduleWorldPosition(module, MODULE_OUTWARD_DISTANCE, MODULE_UPWARD_DISTANCE)
            .add(0.0D, -0.48D + progress * 0.96D, 0.0D);
    serverLevel.sendParticles(
        ParticleTypes.ELECTRIC_SPARK,
        position.x,
        position.y,
        position.z,
        3,
        0.08D,
        0.06D,
        0.08D,
        0.01D);
  }

  protected Vec3 moduleWorldPosition(int module, double radialExtension, double upward) {
    // These are the five corrected 72-degree Blender pivots after MC axis conversion.
    double[] baseAngles = {92.0D, 20.0D, 308.0D, 236.0D, 164.0D};
    double angle = Math.toRadians(baseAngles[module] - getEntityData().get(ORBIT_ANGLE));
    double radius = MODULE_BASE_RADIUS + radialExtension;
    Vec3 local =
        new Vec3(Math.cos(angle) * radius, MODULE_ORBIT_HEIGHT + upward, Math.sin(angle) * radius);
    return position().add(local.yRot((float) Math.toRadians(-getYRot())));
  }

  protected Vec3 moduleFxUpWorldPosition(int module, double radialExtension, double upward) {
    double[] baseAngles = {92.0D, 20.0D, 308.0D, 236.0D, 164.0D};
    double angle = Math.toRadians(baseAngles[module] - getEntityData().get(ORBIT_ANGLE));
    double radius = MODULE_BASE_RADIUS + radialExtension;
    Vec3 local =
        new Vec3(Math.cos(angle) * radius, MODULE_FX_UP_HEIGHT + upward, Math.sin(angle) * radius);
    return position().add(local.yRot((float) Math.toRadians(-getYRot())));
  }

  protected void playModuleLockSound(int module) {
    Vec3 soundPosition =
        moduleWorldPosition(module, MODULE_OUTWARD_DISTANCE, MODULE_UPWARD_DISTANCE);
    level()
        .playSound(
            null,
            soundPosition.x,
            soundPosition.y,
            soundPosition.z,
            DnvArtillerySounds.JINGZHE_ATTACK.get(),
            SoundSource.HOSTILE,
            1.0F,
            0.98F + random.nextFloat() * 0.04F);
  }

  protected Entity resolveModuleTarget(int module) {
    String targetId = moduleTarget[module];
    return targetId == null || targetId.isBlank() || NO_TARGET.equals(targetId)
        ? null
        : EntityFindUtil.findEntity(level(), targetId);
  }

  protected void resetModuleToReady(int module) {
    invalidateModuleExecution(module);
    moduleTarget[module] = NO_TARGET;
    clearModuleValidation(module);
    moduleState[module] = MODULE_READY;
    moduleTimer[module] = 0;
    moduleCharge[module] = 1000;
    publishModule(module);
  }

  protected void clearTransientTargets() {
    invalidateExecution();
    invalidateTargetMemories(DnvTurretTargetMemory.InvalidationReason.ENTITY_INVALIDATED);
    for (int module = 0; module < MODULE_COUNT; module++) {
      if (moduleState[module] == MODULE_LOCKING || moduleState[module] == MODULE_WINDUP) {
        resetModuleToReady(module);
      }
      moduleTarget[module] = NO_TARGET;
      clearModuleValidation(module);
    }
    targetCandidates.clear();
    releaseModuleGeometryOwners();
  }

  protected void clearCombatStateForWreck(ServerLevel serverLevel) {
    if (wreckCombatStateCleared) {
      return;
    }
    wreckCombatStateCleared = true;
    clearTransientTargets();
    pendingSecondaryStrikes.clear();
    for (GroundFlameEffect effect : groundFlameEffects) {
      clearGeneratedFire(serverLevel, effect);
    }
    groundFlameEffects.clear();
  }

  protected void clearModuleValidation(int module) {
    moduleValidationTarget[module] = NO_TARGET;
    nextModuleRelationCheckTick[module] = 0;
    cachedModuleRelationAllowed[module] = false;
    moduleSight[module] = null;
    modulePathDeferred[module] = false;
  }

  protected void publishModule(int module) {
    getEntityData()
        .set(
            MODULE_DATA.get(module),
            packModule(moduleState[module], moduleTimer[module], moduleCharge[module]));
  }

}
