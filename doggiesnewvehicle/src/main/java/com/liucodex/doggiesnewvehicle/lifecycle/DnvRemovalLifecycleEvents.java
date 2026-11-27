package com.liucodex.doggiesnewvehicle.lifecycle;

import com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only registered DNV cleanup owners are checked; no loaded-entity scan or chunk load is performed. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle")
public final class DnvRemovalLifecycleEvents {
    private static final Map<ServerLevel, LinkedHashMap<UUID, Entity>> LEVELS = new IdentityHashMap<>();
    public static final int CHECKS_PER_SECOND = 128;
    private DnvRemovalLifecycleEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof DnvRemovalLifecycle)
            LEVELS.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).putIfAbsent(event.getEntity().getUUID(), event.getEntity());
    }

    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof DnvRemovalLifecycle boss)) return;
        boss.releaseExternalState(event.getEntity().getRemovalReason());
        var tracked = LEVELS.get(level);
        if (tracked != null) {
            tracked.remove(event.getEntity().getUUID(), event.getEntity());
            if (tracked.isEmpty()) LEVELS.remove(level);
        }
    }

    /** Returns the actual removal kind, preserving an ordinary unloaded entity's persistent state. */
    public static RemovalReason missingReason(Entity entity, Entity registered, boolean entitiesLoaded) {
        // A replacement with the same UUID owns the persistent identity now. Clear only the old object's transients.
        if (registered != null && registered != entity) return RemovalReason.UNLOADED_TO_CHUNK;
        if (entity.isRemoved()) return entity.getRemovalReason();
        if (registered == entity) return null;
        return entitiesLoaded ? RemovalReason.DISCARDED : RemovalReason.UNLOADED_TO_CHUNK;
    }

    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        maintain(level);
    }

    static void maintain(ServerLevel level) {
        var tracked = LEVELS.get(level);
        if (tracked == null) return;
        maintainTracked(level, tracked);
        if (tracked.isEmpty()) LEVELS.remove(level);
    }

    static void maintainTracked(ServerLevel level, LinkedHashMap<UUID, Entity> tracked) {
        int count = Math.min(CHECKS_PER_SECOND, tracked.size());
        for (int checked = 0; checked < count && !tracked.isEmpty(); checked++) {
            var entry = tracked.entrySet().iterator().next();
            Entity entity = entry.getValue();
            tracked.remove(entry.getKey());
            if (entity.level() != level) {
                // A dimension transfer may already have attached the same object in the destination.
                continue;
            }
            boolean ready = level.hasChunkAt(entity.blockPosition()) && level.areEntitiesLoaded(entity.chunkPosition().toLong());
            Entity registered = level.getEntity(entity.getUUID());
            RemovalReason reason = missingReason(entity, registered, ready);
            if (reason == null) tracked.put(entity.getUUID(), entity);
            else {
                ((DnvRemovalLifecycle) entity).releaseExternalState(reason);
                if (registered != null && registered != entity && registered instanceof DnvRemovalLifecycle)
                    tracked.put(registered.getUUID(), registered);
            }
        }
    }

    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var tracked = LEVELS.remove(level);
        if (tracked != null) for (Entity entity : tracked.values())
            if (entity.level() == level) ((DnvRemovalLifecycle) entity).releaseExternalState(RemovalReason.UNLOADED_TO_CHUNK);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LEVELS.clear(); }
}
