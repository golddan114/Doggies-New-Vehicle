package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/** Executes the real bounded maintenance loop; world registry and domain cleanup are observable ports. */
class DnvRemovalLifecycleExecutionTest {
    Class<?> target() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Body.class);
        return NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/lifecycle/DnvRemovalLifecycleEvents",
                Set.of("maintainTracked","missingReason"),names,null);
    }
    void maintain(World world,LinkedHashMap<UUID,Body> tracked) throws Exception {
        target().getMethod("maintainTracked",World.class,LinkedHashMap.class).invoke(null,world,tracked);
    }
    @Test void directEraseAndRawRegistryLossReleaseOnceWhileLiveOwnersRemainTracked() throws Exception {
        var world=new World();var live=new Body(world);var erased=new Body(world);var raw=new Body(world);
        erased.reason=RemovalReason.KILLED;world.registered.put(live.id,live);
        var tracked=new LinkedHashMap<UUID,Body>();for(var body:List.of(live,erased,raw))tracked.put(body.id,body);
        maintain(world,tracked);assertEquals(List.of(live.id),new ArrayList<>(tracked.keySet()));
        assertEquals(List.of(RemovalReason.KILLED),erased.releases);assertEquals(List.of(RemovalReason.DISCARDED),raw.releases);
        maintain(world,tracked);assertEquals(1,erased.releases.size());assertEquals(1,raw.releases.size());assertTrue(live.releases.isEmpty());
    }
    @Test void chunkUnloadAndPendingEntityLoadNeverBecomeTerminalDestruction() throws Exception {
        for(boolean ready:List.of(false,true)){
            var world=new World();world.ready=ready;var body=new Body(world);var tracked=new LinkedHashMap<UUID,Body>();
            tracked.put(body.id,body);if(ready)body.reason=RemovalReason.UNLOADED_TO_CHUNK;
            maintain(world,tracked);assertTrue(tracked.isEmpty());assertEquals(List.of(RemovalReason.UNLOADED_TO_CHUNK),body.releases);
            assertTrue(body.presence,"Unload releases transient state, never a persistent uniqueness slot");
        }
    }
    @Test void eachPassIsBoundedAndRotatesPastLiveEntriesToReachEveryMissingOwner() throws Exception {
        var world=new World();var tracked=new LinkedHashMap<UUID,Body>();var missing=new ArrayList<Body>();
        for(int i=0;i<400;i++){
            var body=new Body(world);tracked.put(body.id,body);
            if(i<390)world.registered.put(body.id,body);else missing.add(body);
        }
        maintain(world,tracked);assertEquals(128,world.lookups);assertTrue(missing.stream().allMatch(b->b.releases.isEmpty()));
        for(int i=0;i<3;i++)maintain(world,tracked);
        assertEquals(390,tracked.size());assertTrue(missing.stream().allMatch(b->b.releases.size()==1));
        assertEquals(512,world.lookups);
    }
    @Test void sameObjectTransferredToAnotherDimensionIsNotClearedFromTheOldTracker() throws Exception {
        var world=new World();var body=new Body(new World());var tracked=new LinkedHashMap<UUID,Body>();tracked.put(body.id,body);
        maintain(world,tracked);assertTrue(tracked.isEmpty());assertTrue(body.releases.isEmpty());assertEquals(0,world.lookups);
    }
    @Test void sameUuidReplacementKeepsPersistentIdentityAndRemainsTrackedAfterTheStaleObjectIsReleased() throws Exception {
        for(boolean killed:List.of(false,true)){
            var world=new World();var stale=new Body(world);var replacement=new Body(world);replacement.id=stale.id;
            if(killed)stale.reason=RemovalReason.KILLED;
            world.registered.put(stale.id,replacement);var tracked=new LinkedHashMap<UUID,Body>();tracked.put(stale.id,stale);
            maintain(world,tracked);assertEquals(List.of(RemovalReason.UNLOADED_TO_CHUNK),stale.releases);
            assertTrue(stale.presence);assertSame(replacement,tracked.get(stale.id));
            maintain(world,tracked);assertTrue(replacement.releases.isEmpty());
        }
    }
    @Test void removedCombatProxyClearsOnlyItsOwnReverseLinksUsingTheActualRegistryMethod() throws Exception {
        var bridge=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/compat/DnvVanillaMobRetaliationBridge",
                Set.of("forgetProxy"),new HashMap<>(),ProxyLinks.class);
        var names=new HashMap<String,String>();
        ProxyLinks.forget=bridge.getMethod("forgetProxy",UUID.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/compat/DnvVanillaMobRetaliationBridge",ProxyLinks.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Body.class);
        var proxy=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/entity/DnvCombatProxyEntity",
                Set.of("releaseExternalState"),names,Body.class);
        var body=(Body)proxy.getConstructor().newInstance();var retained=UUID.randomUUID();
        ProxyLinks.PROXIES_BY_TARGET.clear();ProxyLinks.PROXIES_BY_TARGET.put(UUID.randomUUID(),body.id);
        ProxyLinks.PROXIES_BY_TARGET.put(UUID.randomUUID(),retained);
        proxy.getMethod("releaseExternalState",RemovalReason.class).invoke(body,RemovalReason.DISCARDED);
        assertEquals(List.of(retained),new ArrayList<>(ProxyLinks.PROXIES_BY_TARGET.values()));
    }
    public static class ProxyLinks {
        public static final Map<UUID,UUID> PROXIES_BY_TARGET=new HashMap<>();public static java.lang.reflect.Method forget;
        public static void forgetProxy(UUID id){try{forget.invoke(null,id);}catch(Exception failure){throw new AssertionError(failure);}}
    }
    public static class World {
        public final Map<UUID,Body> registered=new HashMap<>();public boolean ready=true;public int lookups;
        public Body getEntity(UUID id){lookups++;return registered.get(id);}
        public boolean hasChunkAt(BlockPos p){return ready;}public boolean areEntitiesLoaded(long chunk){return ready;}
    }
    public static class Body implements DnvRemovalLifecycle {
        public UUID id=UUID.randomUUID();public World world;public RemovalReason reason;public boolean presence=true;
        public final List<RemovalReason> releases=new ArrayList<>();public Body(){this(new World());}public Body(World world){this.world=world;}
        public UUID getUUID(){return id;}public World level(){return world;}
        public BlockPos blockPosition(){return BlockPos.ZERO;}public ChunkPos chunkPosition(){return new ChunkPos(0,0);}
        public boolean isRemoved(){return reason!=null;}public RemovalReason getRemovalReason(){return reason;}
        public void releaseExternalState(RemovalReason reason){releases.add(reason);if(reason.shouldDestroy())presence=false;}
    }
}
