package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;

/** Semantic platform adapter only; all nuclear state is still owned by the shared artillery domain. */
public final class SiloChunkTickets {
    private static final TicketController CONTROLLER=new TicketController(
            ResourceLocation.fromNamespaceAndPath(DoggiesNewVehicle.MOD_ID,"nuclear"),(level,helper)->{
                NuclearSiloStore store=NuclearSiloStore.get(level);
                helper.getEntityTickets().forEach((id,pair)->{
                    if(!store.validTicketOwner(id,level.getGameTime())){helper.removeAllTickets(id);return;}
                    Set<Long> retained=new java.util.HashSet<>();
                    for(long c:pair.nonTicking().toLongArray())helper.removeTicket(id,c,false);
                    for(long c:pair.ticking().toLongArray()){
                        if(store.tickets(id).contains(c))retained.add(c);else helper.removeTicket(id,c,true);
                    }
                    store.tickets(id,retained);
                });
            });
    private SiloChunkTickets() { }
    public static void register(IEventBus bus){bus.addListener((RegisterTicketControllersEvent e)->e.register(CONTROLLER));}
    public static void update(ServerLevel level,UUID owner,Set<Long> desired) {
        if(desired.size()>3)throw new IllegalArgumentException("Nuclear chunk budget");
        NuclearSiloStore store=NuclearSiloStore.get(level);if(!store.writable())return;Set<Long> before=store.tickets(owner);
        NuclearTicketPlan.apply(before,desired,c->{ChunkPos p=new ChunkPos(c);CONTROLLER.forceChunk(level,owner,p.x,p.z,false,true);},
                c->{ChunkPos p=new ChunkPos(c);CONTROLLER.forceChunk(level,owner,p.x,p.z,true,true);},current->store.tickets(owner,current));
        store.tickets(owner,desired);
    }
    public static void release(ServerLevel level,UUID owner){update(level,owner,Set.of());}
}
