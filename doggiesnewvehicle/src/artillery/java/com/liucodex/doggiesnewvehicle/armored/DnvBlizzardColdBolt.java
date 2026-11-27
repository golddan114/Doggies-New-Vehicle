package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.entity.projectile.JavelinMissileEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/** Detached, straight, eight-block/tick cold pulse. Never invokes native missile guidance/blast. */
public final class DnvBlizzardColdBolt extends JavelinMissileEntity implements IEntityAdditionalSpawnData {
    private static final ResourceKey<DamageType> COLD = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "blizzard_cold"));
    private double travelled;
    private Vec3 flightVelocity;
    java.util.function.DoubleConsumer presentationEnd;
    public DnvBlizzardColdBolt(EntityType<? extends DnvBlizzardColdBolt> type, Level level) { super(type, level); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public void writeSpawnData(FriendlyByteBuf buffer) {
        Vec3 velocity = getDeltaMovement();
        buffer.writeDouble(velocity.x);
        buffer.writeDouble(velocity.y);
        buffer.writeDouble(velocity.z);
    }
    @Override public void readSpawnData(FriendlyByteBuf buffer) {
        flightVelocity = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        setDeltaMovement(flightVelocity);
    }
    @Override public void lerpMotion(double x, double y, double z) {
        // Both native fast sync and vanilla clamped packets arrive here. A detached pulse keeps its spawn ray.
        if (flightVelocity == null) super.lerpMotion(x, y, z);
        else setDeltaMovement(flightVelocity);
    }
    @Override public void tick() {
        baseTick();
        if (tickCount > BlizzardRules.BOLT_LIFE || travelled >= BlizzardRules.RANGE) { discard(); return; }
        var from = position();
        if (flightVelocity == null) flightVelocity = getDeltaMovement();
        var velocity = flightVelocity;
        if (!Double.isFinite(velocity.lengthSqr()) || velocity.lengthSqr() < .0001) { discard(); return; }
        setDeltaMovement(velocity);
        var to = from.add(velocity.normalize().scale(Math.min(BlizzardRules.BOLT_SPEED, BlizzardRules.RANGE - travelled)));
        if (level().isClientSide) { setPos(to); travelled += from.distanceTo(to); return; }
        Vec3 loadedEnd = BlizzardLoadedPath.clipStep(this, from, to);
        if (loadedEnd == null) {
            if (presentationEnd != null) presentationEnd.accept(travelled);
            discard(); return;
        }
        boolean boundary = loadedEnd != to;
        to = loadedEnd;
        var wall = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
        Entity owner = getOwner();
        var hit = BlizzardTrace.nearest(level(), from, to, target -> target.isAlive() && target.isPickable()
                && !target.isSpectator() && target != this && target != owner
                && (owner == null || target.getRootVehicle() != owner.getRootVehicle()));
        if (hit != null) to = hit.position();
        travelled += from.distanceTo(to); setPos(to);
        if (hit != null) {
            hit.entity().hurt(new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(COLD), this, owner), BlizzardColdDamage.amount(hit.entity(), false));
        }
        if (boundary || hit != null || wall.getType() != HitResult.Type.MISS || travelled >= BlizzardRules.RANGE) {
            if (presentationEnd != null) presentationEnd.accept(travelled);
            discard();
        }
        else syncMotion(); // Preserve native uncapped motion packets without native flight/damage.
    }
    @Override public boolean forceLoadChunk() { return false; }
    @Override public int getHiddenTicks() { return 0; }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.m_7378_(tag);
        // A bounded visual pulse has no recoverable command; never replay a saved shot.
        travelled = BlizzardRules.RANGE;
    }
}
