package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/** Persistent occupancy methods run unchanged; loaded-chunk and UUID lookups are controlled world edges. */
class MothershipPresenceRecoveryExecutionTest {
    Class<?> target() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/MinecraftServer",Server.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Body.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity",Ship.class);
        return NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/boss/MothershipPresence",
                Set.of("active","reconcile","release","permitsFresh","forgetConfirmedAbsent"),names,State.class);
    }
    boolean reconcile(Class<?> type,State state,World world) throws Exception {
        return (boolean)type.getMethod("reconcile",World.class).invoke(state,world);
    }
    @Test void onlyLoadedEntityCompleteChunksCanReleaseAMissingOwnerAfterTheGraceWindow() throws Exception {
        var type=target();var state=(State)type.getConstructor().newInstance();var world=new World();
        state.owner=UUID.randomUUID();state.ownerChunk=new ChunkPos(0,0).toLong();world.loaded=false;
        assertFalse(reconcile(type,state,world));world.time=200;assertFalse(reconcile(type,state,world));
        world.loaded=true;world.entitiesLoaded=false;assertFalse(reconcile(type,state,world));
        world.entitiesLoaded=true;assertFalse(reconcile(type,state,world));world.time=219;assertFalse(reconcile(type,state,world));
        world.time=220;assertTrue(reconcile(type,state,world));assertNull(state.owner);assertNull(state.ownerChunk);
        assertFalse(reconcile(type,state,world));
    }
    @Test void returningLiveShipRefreshesItsAnchorAndRestartsMissingGraceInsteadOfUnlockingItsSlot() throws Exception {
        var type=target();var state=(State)type.getConstructor().newInstance();var world=new World();var ship=new Ship();
        state.owner=ship.id;state.ownerChunk=0L;assertFalse(reconcile(type,state,world));world.time=19;
        ship.position=new BlockPos(96,80,48);world.entities.put(ship.id,ship);assertFalse(reconcile(type,state,world));
        assertEquals(new ChunkPos(ship.position).toLong(),state.ownerChunk);assertEquals(-1,state.firstMissingTick);
        world.entities.clear();world.time=20;assertFalse(reconcile(type,state,world));world.time=39;assertFalse(reconcile(type,state,world));
        world.time=40;assertTrue(reconcile(type,state,world));
    }
    @Test void oldUuidOnlyRecordsStayConservativeAndExplicitRecoveryChecksAllDimensionsAndExactOwner() throws Exception {
        var type=target();var state=(State)type.getConstructor().newInstance();var world=new World();var other=new World();
        world.server.levels.addAll(List.of(world,other));var owner=UUID.randomUUID();state.owner=owner;
        for(int i=0;i<5;i++){world.time+=100;assertFalse(reconcile(type,state,world));}
        var forget=type.getMethod("forgetConfirmedAbsent",World.class,UUID.class);
        assertFalse((boolean)forget.invoke(state,world,UUID.randomUUID()));
        var ship=new Ship();other.entities.put(owner,ship);assertFalse((boolean)forget.invoke(state,world,owner));
        other.entities.clear();assertTrue((boolean)forget.invoke(state,world,owner));assertNull(state.owner);
        assertFalse((boolean)forget.invoke(state,world,owner));
    }
    public static class State {
        public UUID owner;public Long ownerChunk;public long firstMissingTick=-1;public int dirty;
        public void setDirty(){dirty++;}
    }
    public static class Body {
        public UUID id=UUID.randomUUID();public boolean removed;public BlockPos position=BlockPos.ZERO;
        public boolean isRemoved(){return removed;}public BlockPos blockPosition(){return position;}
    }
    public static class Ship extends Body { }
    public static class Server {public final List<World> levels=new ArrayList<>();public Iterable<World> getAllLevels(){return levels;}}
    public static class World {
        public final Server server=new Server();public final Map<UUID,Body> entities=new HashMap<>();
        public long time;public boolean loaded=true,entitiesLoaded=true;
        public Server getServer(){return server;}public Body getEntity(UUID id){return entities.get(id);}
        public boolean hasChunk(int x,int z){return loaded;}public boolean areEntitiesLoaded(long chunk){return entitiesLoaded;}
        public long getGameTime(){return time;}
    }
}
