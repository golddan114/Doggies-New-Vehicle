package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Compiled placement admission must honor protection and callback mutations before the first world write. */
class NuclearResidualPlacementExecutionTest {
    static final String ROOT="com/liucodex/doggiesnewvehicle/world/DnvControlledFire";
    Map<String,String> names(){
        var map=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(map,"com/liucodex/doggiesnewvehicle/compat/DnvBlockDamagePolicy",Policy.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/server/level/ServerLevel",World.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/Level",World.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/BlockGetter",World.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/LevelAccessor",World.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/block/Block",Block.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/entity/Entity",Actor.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/block/state/BlockState",State.class);
        for(var prefix:List.of("net/minecraftforge/","net/neoforged/neoforge/")){
            NuclearRecoveryExecutionTest.map(map,prefix+"common/util/BlockSnapshot",Snapshot.class);
            NuclearRecoveryExecutionTest.map(map,prefix+"registries/RegistryObject",Ref.class);
            NuclearRecoveryExecutionTest.map(map,prefix+"registries/DeferredHolder",Ref.class);
            NuclearRecoveryExecutionTest.map(map,prefix+"event/level/BlockEvent$EntityPlaceEvent",Placement.class);
            NuclearRecoveryExecutionTest.map(map,prefix+"common/MinecraftForge",BusHolder.class);
            NuclearRecoveryExecutionTest.map(map,prefix+"common/NeoForge",BusHolder.class);
        }
        NuclearRecoveryExecutionTest.map(map,"net/minecraftforge/eventbus/api/IEventBus",Bus.class);
        NuclearRecoveryExecutionTest.map(map,"net/neoforged/bus/api/IEventBus",Bus.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraftforge/eventbus/api/Event",Placement.class);
        NuclearRecoveryExecutionTest.map(map,"net/neoforged/bus/api/Event",Placement.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/resources/ResourceKey",Dimension.class);
        return map;
    }
    @Test void proposedFireMetadataRemainsCorrectForAnOfflineActorAndOriginalAirSnapshot() throws Exception {
        var map=names();NuclearRecoveryExecutionTest.map(map,ROOT,Catalog.class);
        var type=NuclearRecoveryExecutionTest.extract(ROOT+"$ResiduePlacement",Set.of("getPlacedBlock","getState"),map,Placement.class);
        var event=type.getConstructor().newInstance();assertSame(((Placement)event).proposed,type.getMethod("getPlacedBlock").invoke(event));
        assertSame(((Placement)event).proposed,type.getMethod("getState").invoke(event));
    }
    @Test void cancelledOrMutatedPreflightDoesNotPlaceFireAndOnlyLoadedSturdyAirCanPass() throws Exception {
        var map=names();NuclearRecoveryExecutionTest.map(map,ROOT+"$ResiduePlacement",Placement.class);
        var type=NuclearRecoveryExecutionTest.extract(ROOT,Set.of("eligible","place"),map,Catalog.class);
        var place=type.getMethod("place",World.class,BlockPos.class,Actor.class,Block.class);BlockPos pos=BlockPos.ZERO;
        var world=new World();var bus=(TestBus)BusHolder.EVENT_BUS;bus.world=world;bus.cancel=true;
        place.invoke(null,world,pos,null,Catalog.FIRE.get());assertEquals(0,world.writes);
        bus.cancel=false;bus.mutate=true;
        place.invoke(null,world,pos,null,Catalog.FIRE.get());assertEquals(0,world.writes);
        bus.mutate=false;world.air=true;world.loaded=false;
        place.invoke(null,world,pos,new Actor(),Catalog.FIRE.get());assertEquals(0,world.writes);
        world.loaded=true;world.sturdy=false;place.invoke(null,world,pos,null,Catalog.FIRE.get());assertEquals(0,world.writes);
        world.sturdy=true;place.invoke(null,world,pos,null,Catalog.FIRE.get());assertEquals(1,world.writes);assertSame(((Block)Catalog.FIRE.get()).defaultBlockState(),world.placed);
    }
    public static class Actor { }
    public static class Policy {public static boolean allowed(World w,Actor actor){return w.allowed;}}
    public static class Dimension { }
    public static class State {boolean air,sturdy;State(boolean air,boolean sturdy){this.air=air;this.sturdy=sturdy;}
        public boolean isAir(){return air;}public boolean isFaceSturdy(World world,BlockPos pos,Direction face){return sturdy;}}
    public static class Block {final State state=new State(false,false);public State defaultBlockState(){return state;}}
    public static class Ref {final Block block=new Block();public Object get(){return block;}}
    public static class Catalog {public static final Ref FIRE=new Ref();}
    public static class World {public boolean air=true,loaded=true,sturdy=true,allowed=true;public int writes;public State placed;
        public State getBlockState(BlockPos pos){return new State(pos.getY()==0?air:false,pos.getY()<0&&sturdy);}
        public boolean hasChunkAt(BlockPos pos){return loaded;}public Dimension dimension(){return new Dimension();}
        public boolean setBlock(BlockPos pos,State state,int flags){assertEquals(3,flags);placed=state;writes++;return true;}}
    public static class Snapshot {public static Snapshot create(Dimension dimension,World world,BlockPos pos){return new Snapshot();}}
    public static class Placement {public boolean canceled;public final State proposed=((Block)Catalog.FIRE.get()).defaultBlockState();
        public Placement(){ }public Placement(Snapshot snapshot,State against,Actor actor,State proposed){ }
        public boolean isCanceled(){return canceled;}}
    public interface Bus {boolean post(Placement event);Placement postNeo(Placement event);}
    public static class TestBus implements Bus {public World world;public boolean cancel,mutate;
        public boolean post(Placement event){event.canceled=cancel;if(mutate)world.air=false;return cancel;}
        public Placement postNeo(Placement event){post(event);return event;}}
    public static class BusHolder {public static final Bus EVENT_BUS=new TestBus();}
}
