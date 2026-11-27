package com.liucodex.doggiesnewvehicle.entity;

import com.liucodex.doggiesnewvehicle.platform.DnvHoverPlatformStations;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** DNV-owned route platform shell, separate from the autonomous cube boss. */
public final class DnvHoverRoutePlatformEntity extends Mob implements com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle {
    private static final String OWNER_KEY = "DnvHoverRouteOwner";
    private static final String BOARD_ANCHOR_KEY = "DnvBoardAnchor";
    private static final EntityDataAccessor<Float> AI_GUN_YAW =
            SynchedEntityData.defineId(DnvHoverRoutePlatformEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AI_GUN_PITCH =
            SynchedEntityData.defineId(DnvHoverRoutePlatformEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PLAYER_GUN_YAW =
            SynchedEntityData.defineId(DnvHoverRoutePlatformEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PLAYER_GUN_PITCH =
            SynchedEntityData.defineId(DnvHoverRoutePlatformEntity.class, EntityDataSerializers.FLOAT);
    @Nullable private UUID ownerId;
    @Nullable private Vec3 boardAnchor;
    private final DnvHoverPlatformStations stations = new DnvHoverPlatformStations();
    private long nextAiShotTick;
    private long nextPlayerShotTick;

    public DnvHoverRoutePlatformEntity(
            EntityType<? extends DnvHoverRoutePlatformEntity> type,
            Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        // A route actuator is the only future movement writer.
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(AI_GUN_YAW, 0.0F);
        entityData.define(AI_GUN_PITCH, 0.0F);
        entityData.define(PLAYER_GUN_YAW, 0.0F);
        entityData.define(PLAYER_GUN_PITCH, 0.0F);
    }

    @Override
    public void travel(Vec3 input) {
        // Suppress vanilla ground navigation and gravity.
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            setNoGravity(true);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity other) {
        // Keep the route actuator as the only position writer.
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty()
                && (level().isClientSide || ownerId != null
                        && ownerId.equals(passenger.getUUID()));
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (hasPassenger(passenger)) {
            move.accept(passenger, getX(), getY() + getBbHeight() + 0.1D, getZ());
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return com.liucodex.doggiesnewvehicle.compat.DnvHoverPlatformCrew
                .safeExit(this, passenger).orElse(passenger.position());
    }

    public void setBoardAnchor(Vec3 anchor) {
        boardAnchor = java.util.Objects.requireNonNull(anchor, "anchor");
    }

    public Optional<Vec3> boardAnchor() {
        return Optional.ofNullable(boardAnchor);
    }

    public void clearBoardAnchor() {
        boardAnchor = null;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (getPassengers().isEmpty()) {
            clearBoardAnchor();
        }
    }

    @Override public void remove(RemovalReason reason) {
        releaseExternalState(reason);
        super.remove(reason);
    }

    @Override public void releaseExternalState(RemovalReason reason) {
        if (!level().isClientSide) {
            // Unload/restart can sever the ride too. Unresolved safety moves to
            // a durable player recovery ticket before the platform disappears.
            com.liucodex.doggiesnewvehicle.compat.DnvHoverPlatformCrew
                    .releaseOnPlatformRemoval(this);
            com.liucodex.doggiesnewvehicle.compat.DnvHoverPlatformWeapons
                    .dropPlatform(this);
            com.liucodex.doggiesnewvehicle.compat.DnvHoverRoutePlatformRuntime.stop(this);
        }
        if (reason != null && reason.shouldDestroy() && level() instanceof net.minecraft.server.level.ServerLevel level) {
            UUID owner = ownerId().orElse(null);
            if (owner != null) com.liucodex.doggiesnewvehicle.compat.DnvHoverRoutePlatformServices
                    .platformRemoved(level.getServer(), owner, getUUID());
        }
    }


    public Optional<UUID> ownerId() {
        return Optional.ofNullable(ownerId);
    }

    public boolean mayBind(UUID playerId) {
        return ownerId == null || ownerId.equals(playerId);
    }

    public void bindOwner(UUID playerId) {
        if (ownerId != null && !ownerId.equals(playerId)) {
            stations.clearAll();
        }
        ownerId = java.util.Objects.requireNonNull(playerId, "playerId");
    }

    public DnvHoverPlatformStations stations() {
        return stations;
    }

    public boolean gunReady(String station, long gameTick) {
        if (DnvHoverPlatformStations.AI_STATION.equals(station)) {
            return gameTick >= nextAiShotTick;
        }
        if (DnvHoverPlatformStations.PLAYER_STATION.equals(station)) {
            return gameTick >= nextPlayerShotTick;
        }
        return false;
    }

    public void recordGunShot(String station, long gameTick, int cooldownTicks) {
        if (DnvHoverPlatformStations.AI_STATION.equals(station)) {
            nextAiShotTick = gameTick + cooldownTicks;
        } else if (DnvHoverPlatformStations.PLAYER_STATION.equals(station)) {
            nextPlayerShotTick = gameTick + cooldownTicks;
        } else {
            throw new IllegalArgumentException("unknown platform gun station");
        }
    }

    public void aimGun(String station, float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("invalid platform gun pose");
        }
        if (DnvHoverPlatformStations.AI_STATION.equals(station)) {
            entityData.set(AI_GUN_YAW, yaw);
            entityData.set(AI_GUN_PITCH, pitch);
        } else if (DnvHoverPlatformStations.PLAYER_STATION.equals(station)) {
            entityData.set(PLAYER_GUN_YAW, yaw);
            entityData.set(PLAYER_GUN_PITCH, pitch);
        } else {
            throw new IllegalArgumentException("unknown platform gun station");
        }
    }

    public float gunYaw(String station) {
        if (DnvHoverPlatformStations.AI_STATION.equals(station)) {
            return entityData.get(AI_GUN_YAW);
        }
        if (DnvHoverPlatformStations.PLAYER_STATION.equals(station)) {
            return entityData.get(PLAYER_GUN_YAW);
        }
        throw new IllegalArgumentException("unknown platform gun station");
    }

    public float gunPitch(String station) {
        if (DnvHoverPlatformStations.AI_STATION.equals(station)) {
            return entityData.get(AI_GUN_PITCH);
        }
        if (DnvHoverPlatformStations.PLAYER_STATION.equals(station)) {
            return entityData.get(PLAYER_GUN_PITCH);
        }
        throw new IllegalArgumentException("unknown platform gun station");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) {
            tag.putUUID(OWNER_KEY, ownerId);
        }
        if (boardAnchor != null) {
            CompoundTag anchor = new CompoundTag();
            anchor.putDouble("X", boardAnchor.x);
            anchor.putDouble("Y", boardAnchor.y);
            anchor.putDouble("Z", boardAnchor.z);
            tag.put(BOARD_ANCHOR_KEY, anchor);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID(OWNER_KEY) ? tag.getUUID(OWNER_KEY) : null;
        stations.clearAll();
        nextAiShotTick = 0L;
        nextPlayerShotTick = 0L;
        boardAnchor = null;
        if (tag.contains(BOARD_ANCHOR_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag anchor = tag.getCompound(BOARD_ANCHOR_KEY);
            if (anchor.contains("X", Tag.TAG_ANY_NUMERIC) && anchor.contains("Y", Tag.TAG_ANY_NUMERIC)
                    && anchor.contains("Z", Tag.TAG_ANY_NUMERIC)) {
                Vec3 point = new Vec3(anchor.getDouble("X"), anchor.getDouble("Y"), anchor.getDouble("Z"));
                if (Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z)) {
                    boardAnchor = point;
                }
            }
        }
        setNoAi(true);
        setNoGravity(true);
    }
}
