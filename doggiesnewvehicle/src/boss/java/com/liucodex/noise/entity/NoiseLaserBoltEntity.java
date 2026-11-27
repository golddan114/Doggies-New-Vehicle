package com.liucodex.noise.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

/** Cosmetic high-speed light bar; damage is resolved authoritatively at fire time. */
public final class NoiseLaserBoltEntity extends Projectile {
    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(NoiseLaserBoltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BEAM_LENGTH =
            SynchedEntityData.defineId(NoiseLaserBoltEntity.class, EntityDataSerializers.FLOAT);

    public NoiseLaserBoltEntity(EntityType<? extends NoiseLaserBoltEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(LIFETIME, 5);
        entityData.define(BEAM_LENGTH, 1.0F);
    }

    public void setLifetime(int ticks) {
        entityData.set(LIFETIME, Math.max(1, Math.min(8, ticks)));
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    public void setBeamLength(float length) {
        // Mothership range is measured from its center; an offset muzzle can be farther from the target.
        entityData.set(BEAM_LENGTH, Float.isFinite(length) ? Math.max(0.05F, Math.min(256.0F, length)) : 0.05F);
    }

    public float getBeamLength() {
        return entityData.get(BEAM_LENGTH);
    }

    public net.minecraft.world.phys.Vec3 beamEnd() {
        return position().add(getDeltaMovement().normalize().scale(getBeamLength()));
    }

    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return com.liucodex.doggiesnewvehicle.boss.BeamVisibility.bounds(position(), beamEnd());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= entityData.get(LIFETIME)) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(net.minecraft.world.entity.Entity entity) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setLifetime(tag.getInt("Lifetime"));
        setBeamLength(tag.getFloat("BeamLength"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Lifetime", entityData.get(LIFETIME));
        tag.putFloat("BeamLength", entityData.get(BEAM_LENGTH));
    }
}
