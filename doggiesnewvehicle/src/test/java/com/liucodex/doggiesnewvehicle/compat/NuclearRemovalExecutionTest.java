package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloStore;
import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearClearance;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.junit.jupiter.api.Test;

class NuclearRemovalExecutionTest {
    @Test void directWorldRemovalReleasesFacilityAndRackButOrdinaryUnloadRetainsRegistration() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",World.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Body.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity",Body.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/NuclearMissileEntity",Missile.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloStore",Store.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/SiloChunkTickets",Tickets.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",Set.of("onRemovedFromWorld","releaseRegistration","releaseClearance"),names,Body.class);
        for(var reason:new RemovalReason[]{RemovalReason.KILLED,RemovalReason.DISCARDED,RemovalReason.UNLOADED_TO_CHUNK}){
            var body=(Body)type.getConstructor().newInstance();body.reason=reason;
            var inspectionWorld=new NuclearLaunchClearanceExecutionTest.World();inspectionWorld.loaded=false;
            body.clearance=new NuclearClearance(new NuclearLaunchClearanceExecutionTest.Port(inspectionWorld),
                    NuclearLaunchClearanceExecutionTest.ORIGIN,NuclearLaunchClearanceExecutionTest.TARGET,0,0);
            body.clearance.tick(1);assertTrue(inspectionWorld.held);
            body.world.store.real.claim(body.id,BlockPos.ZERO);body.world.store.real.tickets(body.id,Set.of(0L));
            type.getMethod("onRemovedFromWorld").invoke(body);assertEquals(1,body.nativeRemovals);
            assertNull(body.clearance);assertFalse(inspectionWorld.held,"Even ordinary unload must release transient inspection tickets");
            assertEquals(reason.shouldDestroy(),body.world.store.real.canPlace());
            assertEquals(reason.shouldDestroy()?1:0,body.missile.discards);
            if(reason.shouldDestroy())assertTrue(body.world.store.real.tickets(body.id).isEmpty());
        }
    }
    public static class Body {
        public NuclearClearance clearance;public boolean clearanceFinal;
        public UUID id=UUID.randomUUID();public World world=new World();public Missile missile=new Missile();public RemovalReason reason;public int nativeRemovals;
        public World level(){return world;}public UUID getUUID(){return id;}public RemovalReason getRemovalReason(){return reason;}
        public Missile rack(){return missile;}public void onRemovedFromWorld(){nativeRemovals++;}
    }
    public static class Missile {int discards;public void discard(){discards++;}}
    public static class World {final Store store=new Store();}
    public static class Store {
        final NuclearSiloStore real=new NuclearSiloStore();public static Store get(World world){return world.store;}
        public boolean writable(){return real.writable();}public void releaseFacility(UUID id){real.releaseFacility(id);}
    }
    public static class Tickets {public static void release(World world,UUID id){world.store.real.tickets(id,Set.of());}}
}
