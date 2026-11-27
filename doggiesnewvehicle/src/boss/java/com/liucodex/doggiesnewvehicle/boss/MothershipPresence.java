package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvMothershipEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Persistent per-dimension lease; unloading the ship does not free its place. */
public final class MothershipPresence extends SavedData {
    private static final String DATA_NAME = "doggiesnewvehicle_mothership_presence_v1";
    private UUID owner;
    private Long ownerChunk;
    private long firstMissingTick = -1;

    public static MothershipPresence get(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Mothership lease requires server thread");
        return level.getDataStorage().computeIfAbsent(
                MothershipPresence::load, MothershipPresence::new, DATA_NAME);
    }

    public boolean permitsFresh(UUID candidate) {
        return owner == null || owner.equals(candidate);
    }

    public boolean permitsFresh(ServerLevel level, UUID candidate) {
        reconcile(level);
        return permitsFresh(candidate);
    }

    public UUID owner() { return owner; }
    public Long ownerChunk() { return ownerChunk; }

    /** Explicit operator recovery for old UUID-only records; never called by automatic admission. */
    public boolean forgetConfirmedAbsent(ServerLevel level, UUID expected) {
        if (!expected.equals(owner)) return false;
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            var entity = dimension.getEntity(expected);
            if (entity != null && !entity.isRemoved()) return false;
        }
        release(expected);
        return true;
    }

    public boolean active(UUID candidate, BlockPos position) {
        if (!active(candidate)) return false;
        long chunk = new ChunkPos(position).toLong();
        if (ownerChunk == null || ownerChunk != chunk) { ownerChunk = chunk; setDirty(); }
        firstMissingTick = -1;
        return true;
    }

    /** Missing UUIDs are conclusive only after the recorded chunk's entities finished loading. */
    public boolean reconcile(ServerLevel level) {
        if (owner == null) return false;
        var current = level.getEntity(owner);
        if (current instanceof DnvMothershipEntity ship && !ship.isRemoved()) {
            active(owner, ship.blockPosition());
            return false;
        }
        if (current != null || ownerChunk == null || !level.hasChunk(ChunkPos.getX(ownerChunk), ChunkPos.getZ(ownerChunk))
                || !level.areEntitiesLoaded(ownerChunk)) { firstMissingTick = -1; return false; }
        long now = level.getGameTime();
        if (firstMissingTick < 0 || now < firstMissingTick) { firstMissingTick = now; return false; }
        if (now - firstMissingTick < 20) return false;
        release(owner);
        return true;
    }

    public boolean active(UUID candidate) {
        if (owner == null) {
            owner = candidate;
            ownerChunk = null;
            setDirty();
        }
        return owner.equals(candidate);
    }

    public void release(UUID candidate) {
        if (candidate.equals(owner)) {
            owner = null;
            ownerChunk = null;
            firstMissingTick = -1;
            setDirty();
        }
    }

    @Override public CompoundTag save(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        if (owner != null && ownerChunk != null) tag.putLong("OwnerChunk", ownerChunk);
        return tag;
    }

    public static MothershipPresence load(CompoundTag tag) {
        MothershipPresence state = new MothershipPresence();
        if (tag.hasUUID("Owner")) state.owner = tag.getUUID("Owner");
        if (state.owner != null && tag.contains("OwnerChunk", Tag.TAG_LONG)) state.ownerChunk = tag.getLong("OwnerChunk");
        return state;
    }

    @Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        private Events() { }

        @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
            if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                    || !(event.getEntity() instanceof DnvMothershipEntity ship)) return;
            if (!get(level).permitsFresh(level, ship.getUUID())) event.setCanceled(true);
        }
    }
}
