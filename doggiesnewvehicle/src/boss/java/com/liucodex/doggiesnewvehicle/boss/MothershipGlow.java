package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Shared leases for the vanilla glow tag; potion glow and a pre-existing tag remain untouched. */
@Mod.EventBusSubscriber(modid= DoggiesNewVehicle.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class MothershipGlow {
    private static final String MARKER="DnvMothershipGlowOwned";
    private static final Map<ServerLevel,Map<UUID,Lease>> LEVELS=new WeakHashMap<>();
    private static final class Lease {
        final boolean original;
        final Map<UUID,Long> owners=new HashMap<>();
        Lease(boolean original){this.original=original;}
    }
    private MothershipGlow() { }

    public static void acquire(Entity target,UUID ship,long now) {
        if(!(target.level() instanceof ServerLevel level))return;
        var leases=LEVELS.computeIfAbsent(level,ignored->new HashMap<>());
        Lease lease=leases.computeIfAbsent(target.getUUID(),ignored->new Lease(target.hasGlowingTag()));
        lease.owners.put(ship,now+100);
        if(!lease.original) {
            target.getPersistentData().putBoolean(MARKER,true);
            target.setGlowingTag(true);
        }
    }
    public static void release(ServerLevel level,UUID target,UUID ship) {
        Map<UUID,Lease> leases=LEVELS.get(level);
        if(leases==null)return;
        Lease lease=leases.get(target);
        if(lease==null)return;
        lease.owners.remove(ship);
        if(lease.owners.isEmpty()){restore(level.getEntity(target),lease);leases.remove(target);}
        if(leases.isEmpty())LEVELS.remove(level);
    }
    private static void restore(Entity target,Lease lease) {
        if(target==null)return;
        if(!lease.original && target.getPersistentData().getBoolean(MARKER))target.setGlowingTag(false);
        target.getPersistentData().remove(MARKER);
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        Entity entity=event.getEntity();
        if(!event.getLevel().isClientSide && entity.getPersistentData().getBoolean(MARKER)) {
            // The previous process's leases cannot survive a load; a live ship will acquire a fresh lease.
            entity.setGlowingTag(false);entity.getPersistentData().remove(MARKER);
        }
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var leases=LEVELS.get(level);if(leases==null)return;
        Lease removed=leases.remove(event.getEntity().getUUID());
        if(removed!=null)restore(event.getEntity(),removed);
        if(leases.isEmpty())LEVELS.remove(level);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level))return;
        var leases=LEVELS.get(level);if(leases==null)return;
        var iterator=leases.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();Lease lease=entry.getValue();
            lease.owners.values().removeIf(expiry->expiry<level.getGameTime());
            Entity target=level.getEntity(entry.getKey());
            if(target==null || !target.isAlive() || lease.owners.isEmpty()){restore(target,lease);iterator.remove();}
        }
        if(leases.isEmpty())LEVELS.remove(level);
    }
    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var leases=LEVELS.remove(level);if(leases!=null)leases.forEach((id,lease)->restore(level.getEntity(id),lease));
    }
}
