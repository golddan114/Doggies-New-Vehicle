package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

/** Execute the production facility tick; the native vehicle and world callbacks are observable edges. */
class NuclearDestructionExecutionTest {
    @Test void deadFacilityCannotAdvanceCycleRackLaunchOrAnnounceInAnyPhase() throws Exception {
        for(var phase:SiloCycle.Phase.values())for(boolean ammunition:new boolean[]{false,true}){
            var fixture=new Fixture();var body=fixture.body;body.ammo=ammunition?1:0;body.health=0;
            body.cycle.restore(snapshot(phase));var before=body.cycle.snapshot();
            for(int tick=0;tick<20;tick++){body.world.time++;fixture.tick();}
            assertEquals(before,body.cycle.snapshot(),"Wreck advanced "+phase+"; ammo="+ammunition);
            assertEquals(0,body.rackUpdates,"Wreck must not create or move a rack");
            assertEquals(0,body.launches,"Wreck must not enter the launch transaction");
            assertEquals(0,body.sounds,"Wreck must not announce readiness");
            assertEquals(ammunition?1:0,body.ammo);assertEquals(20_000_000,body.energy);
        }
        var fixture=new Fixture();fixture.body.health=0;
        fixture.body.cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.OPENING,0,UUID.randomUUID(),new SiloCycle.Target(10,64,20)));
        fixture.tick();assertEquals(0,fixture.body.launches,"Destruction at the last launch gate cannot launch");
    }
    @Test void healthyFacilityStillAdvancesOneTickAndUpdatesItsRack() throws Exception {
        var fixture=new Fixture();fixture.body.cycle.restore(snapshot(SiloCycle.Phase.RELOADING));
        int remaining=fixture.body.cycle.snapshot().remaining();fixture.tick();
        assertEquals(remaining-1,fixture.body.cycle.snapshot().remaining());assertEquals(1,fixture.body.rackUpdates);
        assertEquals(1,fixture.body.nativeTicks);
    }
    @Test void aNativeWreckCannotResumeMechanicsEvenIfHealthWasExternallyRaised() throws Exception {
        var fixture=new Fixture();fixture.body.wreck=true;fixture.body.health=1;
        fixture.body.cycle.restore(snapshot(SiloCycle.Phase.OPENING));var before=fixture.body.cycle.snapshot();
        fixture.tick();assertEquals(before,fixture.body.cycle.snapshot());assertEquals(0,fixture.body.rackUpdates);
    }
    @Test void destructionInEveryPhaseConsumesLoadedStockOnceAndNeverDebitsFe() throws Exception {
        for(var phase:SiloCycle.Phase.values())for(boolean loaded:new boolean[]{false,true}){
            var fixture=new DestructionFixture();var body=fixture.body;body.health=0;body.wreck=true;
            body.cycle.restore(snapshot(phase));var before=body.cycle.snapshot();
            if(!loaded)body.clearNuclearStock();
            var rack=new Missile();rack.silo=body.id;body.rackId=rack.id;body.world.entities.put(rack.id,rack);
            fixture.destroy();
            assertEquals(before,body.cycle.snapshot());assertEquals(100,body.haltedAt);assertEquals(1,body.nativeDestroys);
            assertEquals(0,body.nuclearStock());assertEquals(10,body.items[1].count);assertEquals(20_000_000,body.energy);
            assertEquals(0,body.nuclearAtNativeDestroy,"No recoverable nuclear stock may enter native wreck processing");
            assertEquals(1,rack.discards);assertNull(body.rackId);assertEquals(0,body.launches);
            var store=body.world.store.real;assertEquals(loaded?1:0,store.pendingImpacts().size());
            if(loaded){var impact=store.pendingImpacts().get(0);assertEquals(body.id,impact.id());assertEquals(body.owner,impact.actor());assertEquals(body.anchor,impact.position());}
            fixture.destroy();assertEquals(loaded?1:0,store.pendingImpacts().size(),"Repeated native callback must not enqueue another blast");
        }
    }
    @Test void healthyOrClientSideCallbacksCannotQueueASiloDetonation() throws Exception {
        for(boolean client:new boolean[]{false,true}){
            var fixture=new DestructionFixture();if(client)fixture.body.world=new World();fixture.body.health=client?0:800;
            fixture.destroy();assertTrue(fixture.body.world.store.real.pendingImpacts().isEmpty());assertEquals(5,fixture.body.nuclearStock());
        }
    }
    @Test void loadedDestructionNeedsNoPowerAndCannotRefundOrDropConsumedMissiles() throws Exception {
        var fixture=new DestructionFixture();fixture.body.health=0;fixture.body.wreck=true;fixture.body.energy=0;
        fixture.destroy();assertEquals(1,fixture.body.world.store.real.pendingImpacts().size());
        assertEquals(0,fixture.body.energy);assertEquals(0,fixture.body.nuclearStock());assertEquals(0,fixture.body.nuclearAtNativeDestroy);
    }
    @Test void haltedClockAndConsumedStockSurviveNbtWhileAnOlderSnapshotCannotReplayTheBlast() throws Exception {
        var fixture=new DestructionFixture();var body=fixture.body;body.health=0;body.wreck=true;
        body.cycle.restore(snapshot(SiloCycle.Phase.OPENING));var old=fixture.save();
        fixture.destroy();var dead=fixture.save();var real=body.world.store.real;
        assertEquals(100,dead.getCompound("NuclearSilo").getLong("HaltedAt"));
        var restoredStore=NuclearSiloStore.load(real.save(new CompoundTag()));assertTrue(restoredStore.writable());
        var restored=new DestructionFixture();restored.body.world.store.real=restoredStore;restored.load(dead);
        assertEquals(body.cycle.snapshot(),restored.body.cycle.snapshot());assertEquals(100,restored.body.haltedAt);
        assertEquals(0,restored.body.nuclearStock());assertNull(restored.body.invalidData);
        restored.body.world.time=200;restored.destroy();assertEquals(1,restoredStore.pendingImpacts().size());
        restored.load(old);assertEquals(5,restored.body.nuclearStock());restored.destroy();
        assertEquals(0,restored.body.nuclearStock());assertEquals(1,restoredStore.pendingImpacts().size());
        var starts=new int[1];var port=new NuclearPayloadEngine.World(){
            public boolean begin(NuclearSiloStore.Impact impact){starts[0]++;return false;}
            public boolean mayModify(NuclearSiloStore.Impact impact){return false;}
            public boolean eligible(NuclearSiloStore.Impact impact,BlockPos pos,NuclearPayloadEngine.Phase phase){return false;}
            public void apply(NuclearSiloStore.Impact impact,List<BlockPos> pos,NuclearPayloadEngine.Phase phase){fail("Cancelled blast mutated terrain");}
        };
        NuclearPayloadEngine.tick(restoredStore,port,201);assertEquals(1,starts[0]);assertTrue(restoredStore.pendingImpacts().isEmpty());
        restored.load(old);restored.destroy();NuclearPayloadEngine.tick(restoredStore,port,202);
        assertEquals(1,starts[0]);assertEquals(0,restored.body.nuclearStock());
    }
    @Test void removingTheDestroyedFacilityNeverCancelsAnAlreadyCommittedMissile() throws Exception {
        for(boolean spare:new boolean[]{false,true}){
            var fixture=new DestructionFixture();var body=fixture.body;body.health=0;body.wreck=true;
            if(!spare)body.clearNuclearStock();
            var flight=new Missile();flight.silo=body.id;flight.armed=true;body.rackId=flight.id;body.world.entities.put(flight.id,flight);
            body.cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.DEPARTING,50,flight.id,new SiloCycle.Target(10,64,20)));
            var store=body.world.store.real;
            assertTrue(store.reserve(new NuclearSiloStore.Flight(flight.id,body.owner,body.id,48000,BlockPos.ZERO,false)));
            store.commit(flight.id);store.tickets(flight.id,Set.of(91L));
            fixture.destroy();fixture.type.getMethod("onRemovedFromWorld").invoke(body);
            assertFalse(flight.removed);assertEquals(0,flight.discards);assertNotNull(store.flight(flight.id));
            assertTrue(store.committed(flight.id));assertEquals(Set.of(91L),store.tickets(flight.id));assertNull(store.facility());
            assertEquals(spare?1:0,store.pendingImpacts().size());
        }
    }
    @Test void reloadedWreckPublishesItsFrozenSnapshotBeforeClientsStartTracking() throws Exception {
        var original=new DestructionFixture();original.body.health=0;original.body.wreck=true;
        original.body.cycle.restore(snapshot(SiloCycle.Phase.OPENING));original.destroy();
        var restored=new DestructionFixture();restored.load(original.save());restored.body.world.time=1000;
        var before=restored.body.cycle.snapshot();
        restored.type.getMethod("onAddedToWorld").invoke(restored.body);
        assertEquals(1,restored.body.published,"A persisted halted clock must still publish the restored mechanical pose");
        assertEquals(100,restored.body.haltedAt);assertEquals(before,restored.body.cycle.snapshot());
        assertEquals(0,restored.body.nuclearStock());assertEquals(0,restored.body.launches);
    }
    @Test void emergencyPayloadHasOneBoundedSlotAndFacilityTicketsSurviveThePendingBlastReload() {
        var store=new NuclearSiloStore();var silo=UUID.randomUUID();var actor=UUID.randomUUID();
        assertTrue(store.claim(silo,BlockPos.ZERO));store.tickets(silo,Set.of(7L));
        for(int i=0;i<NuclearSiloStore.MAX_FLIGHTS;i++){
            var id=UUID.randomUUID();assertTrue(store.reserve(new NuclearSiloStore.Flight(id,actor,silo,48000,BlockPos.ZERO,false)));
            store.commit(id);assertTrue(store.impact(id,BlockPos.ZERO));
        }
        assertTrue(store.detonateFacility(silo,actor,BlockPos.ZERO));assertEquals(65,store.pendingImpacts().size());
        assertFalse(store.detonateFacility(silo,actor,BlockPos.ZERO));
        var restored=NuclearSiloStore.load(store.save(new CompoundTag()));assertTrue(restored.writable());
        assertEquals(Set.of(7L),restored.tickets(silo));assertEquals(65,restored.pendingImpacts().size());
        assertTrue(restored.committed(silo));restored.releaseFacility(silo);var next=UUID.randomUUID();
        assertFalse(restored.canPlace());assertFalse(restored.claim(next,BlockPos.ZERO));
        assertFalse(restored.detonateFacility(next,actor,BlockPos.ZERO));assertEquals(65,restored.pendingImpacts().size());
        restored.finishImpact(restored.pendingImpacts().get(0).id());assertTrue(restored.canPlace());assertTrue(restored.claim(next,BlockPos.ZERO));
        assertTrue(restored.detonateFacility(next,actor,BlockPos.ZERO));assertEquals(65,restored.pendingImpacts().size());
    }
    @Test void foreignUnknownOrStillUnfoldingFacilitiesCannotQueueAPayload() {
        var store=new NuclearSiloStore();var silo=UUID.randomUUID();var actor=UUID.randomUUID();
        assertFalse(store.detonateFacility(silo,actor,BlockPos.ZERO));assertTrue(store.beginUnfold(silo,BlockPos.ZERO));
        assertFalse(store.detonateFacility(silo,actor,BlockPos.ZERO));store.finishUnfold(silo);
        assertFalse(store.detonateFacility(UUID.randomUUID(),actor,BlockPos.ZERO));
        assertFalse(store.detonateFacility(silo,null,BlockPos.ZERO));assertFalse(store.detonateFacility(silo,actor,BlockPos.ZERO.above()));
        assertTrue(store.pendingImpacts().isEmpty());
        var unknown=new CompoundTag();unknown.putInt("Schema",987);var invalid=NuclearSiloStore.load(unknown);
        assertFalse(invalid.detonateFacility(silo,actor,BlockPos.ZERO));assertEquals(unknown,invalid.save(new CompoundTag()));
    }
    static SiloCycle.Snapshot snapshot(SiloCycle.Phase phase){
        boolean launching=phase==SiloCycle.Phase.OPENING||phase==SiloCycle.Phase.RETURNING
                ||phase==SiloCycle.Phase.DEPARTING||phase==SiloCycle.Phase.CLOSING;
        int remaining=switch(phase){case RELOADING->3000;case OPENING,RETURNING->44;case DEPARTING->50;case CLOSING->187;default->0;};
        return new SiloCycle.Snapshot(phase,remaining,launching?UUID.randomUUID():null,launching?new SiloCycle.Target(10,64,20):null);
    }
    static class Fixture {
        final Class<?> type;final Body body;
        Fixture() throws Exception {
            var names=new HashMap<String,String>();
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",World.class);
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",ServerWorld.class);
            NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity",Body.class);
            type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",
                    Set.of("tick","halt","mechanicsRunning","releaseClearance"),names,Body.class);
            body=(Body)type.getConstructor().newInstance();
        }
        void tick() throws Exception {type.getMethod("tick").invoke(body);}
    }
    static class DestructionFixture {
        final Class<?> type;final Body body;
        DestructionFixture() throws Exception {
            var names=new HashMap<String,String>();
            Map.ofEntries(Map.entry("net/minecraft/world/level/Level",World.class),Map.entry("net/minecraft/server/level/ServerLevel",ServerWorld.class),
                    Map.entry("net/minecraft/world/entity/Entity",Body.class),Map.entry("net/minecraft/world/item/ItemStack",Stack.class),
                    Map.entry("net/minecraft/world/item/Item",Item.class),Map.entry("net/minecraftforge/registries/RegistryObject",Ref.class),
                    Map.entry("net/neoforged/neoforge/registries/DeferredHolder",Ref.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity",Body.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearMissileEntity",Missile.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloStore",Store.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/artillery/silo/SiloChunkTickets",Tickets.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/artillery/DnvArtilleryItems",Items.class))
                    .forEach((name,target)->names.put(name,Type.getInternalName(target)));
            type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",
                    Set.of("destroy","halt","mechanicsRunning","ammoSlot","rack","ownsRack","onAddedToWorld","onRemovedFromWorld","releaseRegistration","releaseClearance",
                            "addAdditionalSaveData","readAdditionalSaveData"),names,Body.class);
            body=(Body)type.getConstructor().newInstance();assertTrue(body.world.store.real.claim(body.id,body.anchor));
        }
        void destroy() throws Exception {type.getMethod("destroy").invoke(body);}
        CompoundTag save() throws Exception {var tag=new CompoundTag();type.getMethod("addAdditionalSaveData",CompoundTag.class).invoke(body,tag);return tag;}
        void load(CompoundTag tag) throws Exception {type.getMethod("readAdditionalSaveData",CompoundTag.class).invoke(body,tag);}
    }
    public static class Body {
        public boolean dogEgg(){return false;}
        public SiloPayloadKind payloadKind(){return SiloPayloadKind.NUCLEAR;}
        public Item ammunition(){return Items.NUCLEAR;}
        public NuclearClearance clearance;public boolean clearanceFinal;
        public CompoundTag invalidData;public BlockPos anchor=BlockPos.ZERO;
        public final SiloCycle cycle=new SiloCycle(NuclearRules.DEFAULT);
        public final NuclearTickGate clock=new NuclearTickGate();public final NuclearReadyCue readyCue=new NuclearReadyCue();
        public World world=new ServerWorld();public long haltedAt=-1;public UUID id=UUID.randomUUID(),owner=UUID.randomUUID(),rackId,lastDebit;
        public BlockPos indicatorTarget;public boolean removed;
        public Stack[] items={new Stack(Items.NUCLEAR,2),new Stack(new Item(),10),new Stack(Items.NUCLEAR,3)};
        public int nativeDestroys,nuclearAtNativeDestroy,nativeRemovals;
        public int ammo=1,energy=20_000_000,rackUpdates,launches,sounds,published,nativeTicks;public float health=800;public boolean wreck;
        public World level(){return world;}public boolean isRemoved(){return removed;}public boolean ensureClaim(){return true;}
        public int ammoSlot(){return ammo>0?0:-1;}public int getEnergy(){return energy;}public float getHealth(){return health;}
        public boolean isWreck(){return wreck;}
        public void setDeltaMovement(Vec3 ignored){ }public void setPos(double x,double y,double z){ }
        public void tick(){nativeTicks++;}public void updateRack(ServerWorld ignored){rackUpdates++;}
        public void launch(ServerWorld ignored){launches++;}public void publish(){published++;}public void nuclearSound(SoundEvent ignored){sounds++;}
        public UUID getUUID(){return id;}public Optional<UUID> getOptionalOwnerUUID(){return Optional.ofNullable(owner);}
        public int getContainerSize(){return items.length;}public Stack getItem(int slot){return items[slot];}
        public Stack removeItem(int slot,int count){var stack=items[slot];int n=Math.min(count,stack.count);stack.count-=n;return new Stack(stack.item,n);}
        public int nuclearStock(){return Arrays.stream(items).filter(s->s.item==Items.NUCLEAR).mapToInt(s->s.count).sum();}
        public void clearNuclearStock(){for(var stack:items)if(stack.item==Items.NUCLEAR)stack.count=0;}
        public void destroy(){nativeDestroys++;nuclearAtNativeDestroy=nuclearStock();}
        public void onRemovedFromWorld(){nativeRemovals++;}
        public void onAddedToWorld(){ }
        public net.minecraft.world.entity.Entity.RemovalReason getRemovalReason(){return net.minecraft.world.entity.Entity.RemovalReason.KILLED;}
        public boolean validTarget(BlockPos ignored){return true;}
        public void m_7380_(CompoundTag tag){tag.putUUID("UUID",id);tag.putUUID("Owner",owner);tag.putFloat("Health",health);tag.putBoolean("Wreck",wreck);
            tag.putInt("PhysicalNuclearCount",nuclearStock());}
        public void m_7378_(CompoundTag tag){id=tag.getUUID("UUID");owner=tag.getUUID("Owner");health=tag.getFloat("Health");wreck=tag.getBoolean("Wreck");
            clearNuclearStock();items[0].count=tag.getInt("PhysicalNuclearCount");}
    }
    public static class World {public boolean isClientSide;public long time=100;public long getGameTime(){return time;}
        public final Store store=new Store();public final Map<UUID,Body> entities=new HashMap<>();public Body getEntity(UUID id){return entities.get(id);}}
    public static class ServerWorld extends World { }
    public static class Item { }
    public static class Stack {public final Item item;public int count;public Stack(Item item,int count){this.item=item;this.count=count;}
        public boolean is(Item value){return item==value;}public int getCount(){return count;}}
    public static class Ref {public Object get(){return Items.NUCLEAR;}}
    public static class Items {public static final Item NUCLEAR=new Item();public static final Ref NUCLEAR_MISSILE=new Ref();}
    public static class Missile extends Body {public UUID silo;public boolean armed;public int discards;
        public boolean belongsToRack(UUID owner){return !armed&&!removed&&owner.equals(silo);}public void discard(){removed=true;discards++;}}
    public static class Store {public NuclearSiloStore real=new NuclearSiloStore();public static Store get(ServerWorld world){return world.store;}
        public boolean detonateFacility(UUID id,UUID actor,BlockPos pos,SiloPayloadKind kind){return real.detonateFacility(id,actor,pos,kind);}
        public boolean writable(){return real.writable();}public UUID facility(){return real.facility();}public boolean committed(UUID id){return real.committed(id);}
        public boolean detonateFacility(UUID id,UUID actor,BlockPos pos){return real.detonateFacility(id,actor,pos);}public void releaseFacility(UUID id){real.releaseFacility(id);}
        public void finishUnfold(UUID id){real.finishUnfold(id);}}
    public static class Tickets {public static void release(ServerWorld world,UUID id){world.store.real.tickets(id,Set.of());}}
}
