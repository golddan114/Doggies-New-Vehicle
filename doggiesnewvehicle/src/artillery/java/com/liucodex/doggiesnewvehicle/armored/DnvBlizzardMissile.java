package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.entity.projectile.JavelinMissileEntity;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Server-owned TV flight. The operator stays seated; authenticated commands only request angles. */
public final class DnvBlizzardMissile extends JavelinMissileEntity implements net.minecraftforge.entity.IEntityAdditionalSpawnData {
    private UUID vehicleId, sessionId;
    private boolean impacted, controlled;
    private float desiredYaw, desiredPitch;
    private long lastSteerTick = Long.MIN_VALUE;
    private int flightAge;
    private double travelled;
    public DnvBlizzardMissile(EntityType<? extends DnvBlizzardMissile> type, Level level) { super(type, level); }
    @Override public boolean shouldRenderAtSqrDistance(double distanceSquared) {
        // A quarter-block collision box must not cull the authored rocket after only sixteen blocks.
        return distanceSquared >= 0 && distanceSquared < 256 * 256;
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
    @Override public void writeSpawnData(FriendlyByteBuf buffer) {
        Vec3 velocity = getDeltaMovement();
        buffer.writeDouble(velocity.x); buffer.writeDouble(velocity.y); buffer.writeDouble(velocity.z);
        buffer.writeFloat(getYRot()); buffer.writeFloat(getXRot());
    }
    @Override public void readSpawnData(FriendlyByteBuf buffer) {
        setDeltaMovement(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        setYRot(buffer.readFloat()); setXRot(buffer.readFloat()); yRotO = getYRot(); xRotO = getXRot();
    }
    void bind(DnvBlizzardEntity vehicle, UUID session) {
        vehicleId = vehicle.getUUID(); sessionId = session; controlled = true;
        lastSteerTick = level().getGameTime();
        Vec3 direction = getDeltaMovement().normalize();
        setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        setXRot((float) -Math.toDegrees(Math.asin(Mth.clamp(direction.y, -1, 1))));
        setDeltaMovement(BlizzardMissileFlight.velocity(getYRot(), getXRot()));
        desiredYaw = getYRot(); desiredPitch = getXRot();
    }
    BlizzardTvNetwork.State state(boolean active) { return new BlizzardTvNetwork.State(vehicleId, getUUID(), sessionId, getId(), active); }
    void steer(ServerPlayer player, UUID session, float yaw, float pitch, boolean exit) {
        if (!controlled || !java.util.Objects.equals(sessionId, session) || getOwner() != player
                || !player.isAlive() || player.isSpectator() || !(player.getVehicle() instanceof DnvBlizzardEntity tank)
                || !tank.ownsTv(this) || !tank.getUUID().equals(vehicleId)) return;
        if (exit) { releaseControl(); return; }
        if (!BlizzardMissileFlight.validAngles(yaw, pitch) || lastSteerTick == level().getGameTime()) return;
        lastSteerTick = level().getGameTime();
        desiredYaw = Mth.wrapDegrees(yaw); desiredPitch = Mth.clamp(pitch, -85, 85);
    }
    void releaseControl() {
        if (!controlled) return;
        controlled = false;
        var tank = source();
        if (tank != null) tank.endTv(this);
        else if (getOwner() instanceof ServerPlayer player) BlizzardTvNetwork.sendState(player, state(false));
    }
    private DnvBlizzardEntity source() {
        if (!(level() instanceof ServerLevel server) || vehicleId == null) return null;
        Entity entity = server.getEntity(vehicleId);
        return entity instanceof DnvBlizzardEntity tank ? tank : null;
    }
    @Override public void tick() {
        // Calling the native Javelin tick would add guidance, velocity and blast a second time.
        baseTick();
        if (level().isClientSide) {
            Vec3 direction = getDeltaMovement().normalize();
            yRotO = getYRot(); xRotO = getXRot();
            setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            setXRot((float) -Math.toDegrees(Math.asin(Mth.clamp(direction.y, -1, 1))));
            setPos(position().add(getDeltaMovement())); return;
        }
        if (BlizzardMissileFlight.expired(flightAge, travelled)) { releaseControl(); discard(); return; }
        var tank = source();
        if (controlled && (tank == null || !tank.ownsTv(this) || tank.isWreck()
                || !(getOwner() instanceof ServerPlayer player) || player.getVehicle() != tank
                || player.serverLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != player
                || !player.isAlive() || player.isSpectator() || level().getGameTime() - lastSteerTick > 20)) releaseControl();
        if (controlled) {
            setYRot(BlizzardMissileFlight.turn(getYRot(), desiredYaw));
            setXRot(getXRot() + Mth.clamp(desiredPitch - getXRot(), -6, 6));
        }
        Vec3 from = position(), velocity = BlizzardMissileFlight.velocity(getYRot(), getXRot());
        Vec3 to = from.add(velocity.scale(Math.min(1, Math.max(0, BlizzardMissileFlight.DISTANCE - travelled) / BlizzardMissileFlight.SPEED)));
        Vec3 loadedEnd = BlizzardLoadedPath.clipStep(this, from, to);
        if (loadedEnd == null) { releaseControl(); discard(); return; }
        boolean boundary = loadedEnd != to;
        to = loadedEnd;
        var wall = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
        var hit = BlizzardTrace.nearest(level(), from, to, entity -> entity.isAlive() && entity.isPickable()
                && !entity.isSpectator() && entity != this && entity != getOwner()
                && (tank == null || entity.getRootVehicle() != tank));
        if (hit != null) to = hit.position();
        travelled += from.distanceTo(to); flightAge++;
        setDeltaMovement(velocity); setPos(to);
        if (hit != null || wall.getType() != HitResult.Type.MISS) impact();
        else if (boundary || BlizzardMissileFlight.expired(flightAge, travelled)) { releaseControl(); discard(); }
        else syncMotion();
    }
    private void impact() {
        if (impacted || !(level() instanceof ServerLevel server)) return;
        impacted = true; releaseControl();
        // Native damage stays singular; terrain gets a separate, strictly bounded four-block phase.
        setExplosionDamage(50); setExplosionRadius(8); setExplosionDestroy(false);
        if (com.liucodex.doggiesnewvehicle.api.combat.DnvProjectilePath.blastLoaded(server, position(), getExplosionRadius())) {
            causeExplode(position());
        }
        if (!BlizzardTerrainBlast.apply(server, this, position())) { discard(); return; }
        BlizzardTerrainBlast.clearLiquids(server,this,position());
        com.liucodex.doggiesnewvehicle.artillery.compat.DnvSporeScent.clear(server, this, position(), BlizzardMissileFlight.SCENT_RADIUS);
        discard();
    }
    @Override public boolean forceLoadChunk() { return false; }
    @Override public int getHiddenTicks() { return 0; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.m_7380_(tag); tag.putInt("BlizzardFlightAge", flightAge); tag.putDouble("BlizzardTravelled", travelled);
        if (vehicleId != null) tag.putUUID("BlizzardVehicle", vehicleId);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.m_7378_(tag); flightAge = Mth.clamp(tag.getInt("BlizzardFlightAge"), 0, BlizzardMissileFlight.LIFE);
        double value = tag.getDouble("BlizzardTravelled");
        travelled = Double.isFinite(value) ? Mth.clamp(value, 0, BlizzardMissileFlight.DISTANCE) : BlizzardMissileFlight.DISTANCE;
        vehicleId = tag.hasUUID("BlizzardVehicle") ? tag.getUUID("BlizzardVehicle") : null;
        controlled = false; sessionId = null; impacted = false;
    }
    @Override public void remove(Entity.RemovalReason reason) { releaseControl(); super.m_142687_(reason); }
}
