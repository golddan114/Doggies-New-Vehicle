package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloStore;
import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearTicketPlan;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Executes the compiled land entry with the real store and incremental ticket checkpoints. */
class NuclearLandingExecutionTest {
    @Test void landingReleasesEveryTicketBeforeRetiringFlightAndQueuesPayloadOnce() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloStore",Store.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/SiloChunkTickets",Tickets.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearMissileEntity",Set.of("land"),names,Body.class);
        var land=type.getMethod("land",World.class,Vec3.class);
        var entity=(Body)type.getConstructor().newInstance();var world=new World();world.prepare(entity.id);
        land.invoke(entity,world,new Vec3(10,64,10));
        assertTrue(world.nativeTickets.isEmpty());assertTrue(world.store.real.tickets(entity.id).isEmpty());
        assertNull(world.store.real.flight(entity.id));assertEquals(1,world.store.real.pendingImpacts().size());assertEquals(1,entity.discards);
        assertEquals(new BlockPos(10,64,10),world.store.real.pendingImpacts().get(0).position());
        land.invoke(entity,world,new Vec3(20,64,20));assertEquals(1,world.store.real.pendingImpacts().size());
    }
    @Test void failingReleaseRetainsFlightAndDoesNotCommitPayload() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloStore",Store.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/SiloChunkTickets",Tickets.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearMissileEntity",Set.of("land"),names,Body.class);
        var entity=(Body)type.getConstructor().newInstance();var world=new World();world.prepare(entity.id);world.failAfter=2;
        assertThrows(java.lang.reflect.InvocationTargetException.class,()->type.getMethod("land",World.class,Vec3.class).invoke(entity,world,Vec3.ZERO));
        assertNotNull(world.store.real.flight(entity.id));assertTrue(world.store.real.pendingImpacts().isEmpty());assertEquals(0,entity.discards);
        assertTrue(world.store.real.tickets(entity.id).containsAll(world.nativeTickets));
    }
    public static class Body { public UUID id=UUID.randomUUID();public int discards;public UUID getUUID(){return id;}public void discard(){discards++;} }
    public static class World {
        final Store store=new Store();final Set<Long> nativeTickets=new HashSet<>(Set.of(1L,2L,3L));int releases,failAfter=-1;
        void prepare(UUID id){var silo=UUID.randomUUID();store.real.claim(silo,BlockPos.ZERO);
            store.real.reserve(new NuclearSiloStore.Flight(id,UUID.randomUUID(),silo,48000,BlockPos.ZERO,false));
            store.real.commit(id);store.real.tickets(id,Set.copyOf(nativeTickets));}
    }
    public static class Store {
        final NuclearSiloStore real=new NuclearSiloStore();public static Store get(World world){return world.store;}
        public boolean impact(UUID id,BlockPos pos){return real.impact(id,pos);}
    }
    public static class Tickets {
        public static void release(World world,UUID id){
            NuclearTicketPlan.apply(world.store.real.tickets(id),Set.of(),c->{
                world.nativeTickets.remove(c);if(++world.releases==world.failAfter)throw new IllegalStateException("Native release");
            },c->fail("Landing acquired ticket"),chunks->world.store.real.tickets(id,chunks));
        }
    }
}
