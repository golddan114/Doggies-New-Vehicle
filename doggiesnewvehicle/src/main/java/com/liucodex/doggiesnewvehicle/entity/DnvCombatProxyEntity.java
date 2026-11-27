package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.liucodex.doggiesnewvehicle.compat.DnvVanillaMobRetaliationBridge;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Invisible LivingEntity relay that lets vanilla hostile mobs target DNV. */
public final class DnvCombatProxyEntity extends Mob
        implements com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle {
    private static final int USE_CHECK_INTERVAL = 20;
    private static final int UNUSED_DISCARD_TICKS = 40;
    private static final EntityDataAccessor<Optional<UUID>> TARGET_ID =
            SynchedEntityData.defineId(
                    DnvCombatProxyEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private int unusedTicks;

    public DnvCombatProxyEntity(
            EntityType<? extends DnvCombatProxyEntity> type,
            Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setInvisible(true);
        setSilent(true);
        noPhysics = true;
    }

    @Override
    protected void registerGoals() {
        // Relay only; the attacking vanilla mob owns all movement and combat AI.
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TARGET_ID, Optional.empty());
    }

    public void bind(Entity target) {
        entityData.set(TARGET_ID, Optional.of(target.getUUID()));
        follow(target.getBoundingBox());
    }

    public boolean represents(Entity target) {
        return entityData.get(TARGET_ID)
                .map(target.getUUID()::equals)
                .orElse(false);
    }

    @Nullable
    public Entity realTarget() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return entityData.get(TARGET_ID)
                .map(serverLevel::getEntity)
                .orElse(null);
    }

    @Override
    public void tick() {
        super.tick();
        setInvisible(true);
        setNoGravity(true);
        noPhysics = true;
        if (level().isClientSide) {
            return;
        }
        Entity target = realTarget();
        if (target == null || target.isRemoved() || !target.isAlive()) {
            discard();
            return;
        }
        follow(target.getBoundingBox());
        if (tickCount % USE_CHECK_INTERVAL != 0) {
            return;
        }
        List<Mob> users = level().getEntitiesOfClass(
                Mob.class,
                getBoundingBox().inflate(96.0D),
                mob -> mob.getTarget() == this);
        boolean inUse = false;
        for (Mob user : users) {
            inUse = true;
        }
        unusedTicks = inUse ? 0 : unusedTicks + USE_CHECK_INTERVAL;
        if (unusedTicks >= UNUSED_DISCARD_TICKS) {
            discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !Float.isFinite(amount) || amount <= 0.0F) {
            return false;
        }
        // Explosions already visit the real vehicle independently. Relaying doubles damage and
        // makes the native LivingEntity path sample this proxy's potentially enormous hull box.
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(ModDamageTypes.CUSTOM_EXPLOSION)
                || source.is(ModDamageTypes.PROJECTILE_EXPLOSION) || source.is(ModDamageTypes.VEHICLE_EXPLOSION)) {
            return false;
        }
        Entity attacker = source.getEntity();
        Entity target = realTarget();
        if (attacker == null || target == null) {
            return false;
        }
        return target.hurt(source, amount);
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
        // Proxy collision must never move the mob or represented DNV entity.
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        releaseExternalState(reason);
        super.remove(reason);
    }

    @Override public void releaseExternalState(RemovalReason reason) {
        DnvVanillaMobRetaliationBridge.forgetProxy(getUUID());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        // Proxies are reconstructed from the next retaliation event.
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        // Proxies are never loaded from disk.
    }

    private void follow(AABB bounds) {
        Vec3 center = bounds.getCenter();
        setPos(center.x, bounds.minY, center.z);
        setBoundingBox(bounds);
        setDeltaMovement(Vec3.ZERO);
    }
}
