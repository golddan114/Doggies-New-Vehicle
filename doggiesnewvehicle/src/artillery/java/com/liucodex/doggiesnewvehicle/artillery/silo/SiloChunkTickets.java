package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.eventbus.api.IEventBus;

/** Forge platform edge; Neo has an explicit same-boundary TicketController adapter. */
public final class SiloChunkTickets {
    private SiloChunkTickets() { }
    public static void register(IEventBus bus) {
        ForgeChunkManager.setForcedChunkLoadingCallback(DoggiesNewVehicle.MOD_ID,(level,helper)->{
            NuclearSiloStore store=NuclearSiloStore.get(level);
            helper.getEntityTickets().forEach((id,pair)->{
                if(!store.validTicketOwner(id,level.getGameTime())) { helper.removeAllTickets(id);return; }
                Set<Long> retained=new HashSet<>();
                for(long c:pair.getFirst().toLongArray())helper.removeTicket(id,c,false);
                for(long c:pair.getSecond().toLongArray()){
                    if(store.tickets(id).contains(c))retained.add(c);else helper.removeTicket(id,c,true);
                }
                store.tickets(id,retained);
            });
        });
    }
    public static void update(ServerLevel level,UUID owner,Set<Long> desired) {
        if(desired.size()>3)throw new IllegalArgumentException("Nuclear chunk budget");
        NuclearSiloStore store=NuclearSiloStore.get(level);if(!store.writable())return;Set<Long> before=store.tickets(owner);
        NuclearTicketPlan.apply(before,desired,c->{ChunkPos p=new ChunkPos(c);ForgeChunkManager.forceChunk(level,DoggiesNewVehicle.MOD_ID,owner,p.x,p.z,false,true);},
                c->{ChunkPos p=new ChunkPos(c);ForgeChunkManager.forceChunk(level,DoggiesNewVehicle.MOD_ID,owner,p.x,p.z,true,true);},current->store.tickets(owner,current));
        store.tickets(owner,desired);
    }
    public static void release(ServerLevel level,UUID owner){update(level,owner,Set.of());}
}
