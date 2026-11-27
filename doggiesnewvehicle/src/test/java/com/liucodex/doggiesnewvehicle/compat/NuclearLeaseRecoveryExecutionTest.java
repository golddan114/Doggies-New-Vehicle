package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearSiloStore;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class NuclearLeaseRecoveryExecutionTest {
    @Test void destroyedExcavatingOrOpeningBoxReleasesOnlyItsLeaseAfterSavedDataReload() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Body.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/block/entity/BlockEntity",Block.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/block/entity/ContainerBlockEntity",Box.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/EntityType",Kind.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraftforge/registries/RegistryObject",Ref.class);
        NuclearRecoveryExecutionTest.map(names,"net/neoforged/neoforge/registries/DeferredHolder",Ref.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/DnvArtilleryEntities",Entities.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/SiloChunkTickets",Tickets.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearContainerDeployment",Set.of("recoverLease","isSiloType"),names,null);
        var recover=type.getMethod("recoverLease",World.class,NuclearSiloStore.class);
        for(int cursor:new int[]{0,128,3034,6069}){
            var world=new World();var id=UUID.randomUUID();assertTrue(world.store.beginUnfold(id,BlockPos.ZERO));
            world.store.tickets(id,Set.of(0L));var box=new Box();box.entityTag=new CompoundTag();box.entityTag.putUUID("UUID",id);
            var job=new CompoundTag();job.putBoolean("Unfolding",true);job.putInt("UnfoldCursor",cursor);box.entityTag.put("NuclearSilo",job);
            world.block=box;world.store=NuclearSiloStore.load(world.store.save(new CompoundTag()));
            recover.invoke(null,world,world.store);assertEquals(id,world.store.facility());
            world.block=null;world.blocksLoaded=false;recover.invoke(null,world,world.store);assertEquals(id,world.store.facility());
            world.blocksLoaded=true;recover.invoke(null,world,world.store);assertTrue(world.store.canPlace());
            assertFalse(world.store.unfolding());assertTrue(world.store.tickets(id).isEmpty());assertTrue(world.store.pendingImpacts().isEmpty());
            assertEquals(1,world.releases);recover.invoke(null,world,world.store);assertEquals(1,world.releases);
        }
    }
    @Test void absentFacilityReleasesOnlyAfterItsEntitiesFinishLoadingAndNeverTouchesFlights() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Body.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/block/entity/BlockEntity",Block.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/block/entity/ContainerBlockEntity",Box.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/EntityType",Kind.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraftforge/registries/RegistryObject",Ref.class);
        NuclearRecoveryExecutionTest.map(names,"net/neoforged/neoforge/registries/DeferredHolder",Ref.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/DnvArtilleryEntities",Entities.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/SiloChunkTickets",Tickets.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearContainerDeployment",Set.of("recoverLease","isSiloType"),names,null);
        var recover=type.getMethod("recoverLease",World.class,NuclearSiloStore.class);
        var world=new World();var store=world.store;UUID facility=UUID.randomUUID(),flight=UUID.randomUUID();
        store.claim(facility,BlockPos.ZERO);store.tickets(facility,Set.of(0L));
        store.reserve(new NuclearSiloStore.Flight(flight,UUID.randomUUID(),facility,1000,BlockPos.ZERO,false));store.commit(flight);
        world.blocksLoaded=false;recover.invoke(null,world,store);assertEquals(facility,store.facility());
        world.blocksLoaded=true;world.entitiesLoaded=false;recover.invoke(null,world,store);assertEquals(facility,store.facility());
        world.entitiesLoaded=true;world.body=new Body();recover.invoke(null,world,store);assertEquals(facility,store.facility());
        world.body=null;recover.invoke(null,world,store);assertTrue(store.canPlace());assertEquals(1,world.releases);
        assertTrue(store.tickets(facility).isEmpty());assertTrue(store.committed(flight));assertNotNull(store.flight(flight));
        recover.invoke(null,world,store);assertEquals(1,world.releases);
        assertTrue(store.claim(UUID.randomUUID(),BlockPos.ZERO));
        store.releaseFacility(store.facility());UUID unfolding=UUID.randomUUID();assertTrue(store.beginUnfold(unfolding,BlockPos.ZERO));
        var box=new Box();box.entityTag=new CompoundTag();box.entityTag.putUUID("UUID",unfolding);
        var job=new CompoundTag();job.putBoolean("Unfolding",true);box.entityTag.put("NuclearSilo",job);
        world.block=box;world.entitiesLoaded=false;recover.invoke(null,world,store);assertEquals(unfolding,store.facility());
        world.block=null;recover.invoke(null,world,store);assertTrue(store.canPlace());
    }
    public static class Body { }
    public static class Block { }
    public static class Kind { }
    public static class Ref {public Object get(){return Entities.KIND;}}
    public static class Entities {static final Kind KIND=new Kind();public static final Ref NUCLEAR_SILO=new Ref();}
    public static class Box extends Block {public Kind entityType=Entities.KIND;public CompoundTag entityTag;}
    public static class World {
        NuclearSiloStore store=new NuclearSiloStore();boolean blocksLoaded=true,entitiesLoaded=true;Body body;Block block;int releases;
        public boolean hasChunkAt(BlockPos position){return blocksLoaded;}
        public boolean areEntitiesLoaded(long chunk){return entitiesLoaded;}
        public Body getEntity(UUID id){return body;}
        public Block getBlockEntity(BlockPos position){return block;}
    }
    public static class Tickets {public static void release(World world,UUID id){world.releases++;world.store.tickets(id,Set.of());}}
}
