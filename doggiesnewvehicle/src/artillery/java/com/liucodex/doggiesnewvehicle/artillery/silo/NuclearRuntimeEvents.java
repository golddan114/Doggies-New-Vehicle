package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded high-altitude fallback. Native ticking and fallback share the missile's game-time guard. */
@Mod.EventBusSubscriber(modid=DoggiesNewVehicle.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class NuclearRuntimeEvents {
    private static final Map<ServerLevel,Map<UUID,WeakReference<NuclearMissileEntity>>> FLIGHTS=new WeakHashMap<>();
    private NuclearRuntimeEvents(){ }
    public static void track(ServerLevel level,NuclearMissileEntity entity){
        var map=FLIGHTS.computeIfAbsent(level,k->new LinkedHashMap<>());
        if(map.size()<NuclearSiloStore.MAX_FLIGHTS||map.containsKey(entity.getUUID()))map.put(entity.getUUID(),new WeakReference<>(entity));
    }
    public static void untrack(ServerLevel level,UUID id){var map=FLIGHTS.get(level);if(map!=null)map.remove(id);}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
        var map=FLIGHTS.get(level);
        if(map!=null)for(var entry:new ArrayList<>(map.entrySet())){
            NuclearMissileEntity missile=entry.getValue().get();
            if(missile==null||missile.isRemoved()){map.remove(entry.getKey());continue;}
            if(missile.lastServerTick()!=level.getGameTime())missile.tick();
        }
        NuclearSiloStore store=NuclearSiloStore.get(level);if(!store.writable())return;
        NuclearPayloadEngine.tick(store,new NuclearPayloadWorld(level),level.getGameTime());
        VacuumPayloadEngine.tick(store,new VacuumPayloadWorld(level),level.getGameTime());
        if(level.getGameTime()%20!=0)return;
        NuclearContainerDeployment.recoverLease(level,store);
        // Restore missing tickets from the saved locator before relying on entity callbacks.
        if(store.facility()!=null)SiloChunkTickets.update(level,store.facility(),java.util.Set.of(new net.minecraft.world.level.ChunkPos(store.anchor()).toLong()));
        for(var flight:store.flights())if(flight.expires()<=level.getGameTime()){
            var entity=level.getEntity(flight.id());if(entity!=null)entity.discard();
            SiloChunkTickets.release(level,flight.id());store.forgetFlight(flight.id());
        }else if(store.tickets(flight.id()).isEmpty()){
            SiloChunkTickets.update(level,flight.id(),java.util.Set.of(new net.minecraft.world.level.ChunkPos(flight.lastPosition()).toLong()));
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)FLIGHTS.remove(level);}
}
