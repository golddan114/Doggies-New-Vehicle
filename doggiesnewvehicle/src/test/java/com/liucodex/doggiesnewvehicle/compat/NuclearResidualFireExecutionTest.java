package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** Executes production propagation, protection and expiry with explicit block/world edges. */
class NuclearResidualFireExecutionTest {
    static final String ROOT="com/liucodex/doggiesnewvehicle/world/DnvControlledFire";
    Class<?> type()throws Exception {
        var names=new HashMap<String,String>();
        Map.ofEntries(Map.entry("world/level/block/Block",Block.class),Map.entry("world/level/block/FireBlock",Block.class),
            Map.entry("world/level/block/Blocks",Catalog.class),Map.entry("world/level/block/state/BlockState",State.class),
            Map.entry("world/level/block/state/BlockBehaviour$BlockStateBase",State.class),
            Map.entry("world/level/Level",World.class),Map.entry("server/level/ServerLevel",World.class),
            Map.entry("world/level/BlockGetter",World.class),Map.entry("world/level/LevelAccessor",World.class),
            Map.entry("world/level/LevelReader",World.class),Map.entry("world/level/GameRules",Rules.class),
            Map.entry("world/level/GameRules$Key",Key.class),Map.entry("world/level/border/WorldBorder",Border.class),
            Map.entry("world/level/material/FluidState",Fluid.class),Map.entry("world/entity/LivingEntity",Actor.class),
            Map.entry("world/level/block/state/properties/Property",Property.class),
            Map.entry("world/level/block/state/properties/IntegerProperty",Property.class),Map.entry("resources/ResourceKey",Dimension.class))
            .forEach((path,cls)->NuclearRecoveryExecutionTest.map(names,"net/minecraft/"+path,cls));
        for(String prefix:List.of("net/minecraftforge/","net/neoforged/neoforge/")){
            NuclearRecoveryExecutionTest.map(names,prefix+"common/util/BlockSnapshot",Snapshot.class);
            NuclearRecoveryExecutionTest.map(names,prefix+"common/MinecraftForge",BusHolder.class);
            NuclearRecoveryExecutionTest.map(names,prefix+"common/NeoForge",BusHolder.class);
            NuclearRecoveryExecutionTest.map(names,prefix+"event/level/BlockEvent$EntityPlaceEvent",Placement.class);
        }
        NuclearRecoveryExecutionTest.map(names,"net/minecraftforge/eventbus/api/IEventBus",Bus.class);
        NuclearRecoveryExecutionTest.map(names,"net/neoforged/bus/api/IEventBus",Bus.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraftforge/eventbus/api/Event",Placement.class);
        NuclearRecoveryExecutionTest.map(names,"net/neoforged/bus/api/Event",Placement.class);
        NuclearRecoveryExecutionTest.map(names,ROOT+"$SpreadPlacement",Placement.class);
        NuclearRecoveryExecutionTest.map(names,DnvBlockDamagePolicyExecutionTest.ROOT,Policy.class);
        return NuclearRecoveryExecutionTest.extract(ROOT,Set.of("tick","onPlace","safe","updateShape"),names,Block.class);
    }
    @Test void fiveSecondCountdownAndChildrenExpireTogetherWithoutResetOrForeignFireRemoval()throws Exception {
        var type=type();var block=(Block)type.getConstructor().newInstance();var world=new World();
        BlockPos origin=new BlockPos(-17,60,-33),fuel=origin.east(),ordinary=origin.west();
        world.cells.put(origin,block.defaultBlockState());world.cells.put(fuel,new State(new Block(),300,100));
        world.cells.put(ordinary,Catalog.FIRE.defaultBlockState());
        var place=type.getMethod("onPlace",State.class,World.class,BlockPos.class,State.class,boolean.class);
        var tick=type.getMethod("tick",State.class,World.class,BlockPos.class,RandomSource.class);
        place.invoke(block,world.getBlockState(origin),world,origin,Catalog.AIR.defaultBlockState(),false);
        assertEquals(20,world.scheduled.get(origin));
        var random=RandomSource.create(4);
        tick.invoke(block,world.getBlockState(origin),world,origin,random);
        assertEquals(4,world.getBlockState(origin).seconds);assertEquals(4,world.getBlockState(fuel).seconds);
        assertSame(block,world.getBlockState(fuel).block);assertEquals(1,world.burns);
        var shape=type.getMethod("updateShape",State.class,Direction.class,State.class,World.class,BlockPos.class,BlockPos.class);
        State child=(State)shape.invoke(block,world.getBlockState(fuel),Direction.DOWN,Catalog.AIR.defaultBlockState(),world,fuel,fuel.below());
        assertEquals(4,child.seconds);assertSame(block,child.block);
        world.cells.put(fuel,child);
        // Four further 20-tick steps. Children do not get another five seconds.
        for(int step=0;step<4;step++)for(BlockPos pos:List.copyOf(world.cells.keySet()))
            if(world.getBlockState(pos).is(block))tick.invoke(block,world.getBlockState(pos),world,pos,random);
        assertSame(Catalog.AIR,world.getBlockState(origin).block);assertSame(Catalog.AIR,world.getBlockState(fuel).block);
        assertSame(Catalog.FIRE,world.getBlockState(ordinary).block);
        var replacement=new Block().defaultBlockState();world.cells.put(origin,replacement);int writes=world.writes;
        tick.invoke(block,block.defaultBlockState(),world,origin,random);assertEquals(writes,world.writes);assertSame(replacement,world.getBlockState(origin));
        world.isClientSide=true;world.scheduled.clear();place.invoke(block,block.defaultBlockState(),world,origin,Catalog.AIR.defaultBlockState(),false);
        assertTrue(world.scheduled.isEmpty());
    }
    @Test void protectionGameRulesRainWaterAndUnloadedNeighborsPreventFuelAndSpreadWrites()throws Exception {
        var type=type();var block=(Block)type.getConstructor().newInstance();
        var tick=type.getMethod("tick",State.class,World.class,BlockPos.class,RandomSource.class);
        for(int mode=0;mode<7;mode++){
            var world=new World();var pos=BlockPos.ZERO;var fuel=pos.east();var state=new State(new Block(),300,100);
            world.cells.put(pos,block.defaultBlockState());world.cells.put(fuel,state);
            world.cancel=mode==0;world.rules.fire=mode!=1;world.rules.grief=mode!=2;
            if(mode==3)world.rain.add(fuel);if(mode==4)world.unloaded.add(fuel);if(mode==5)state.wet=true;
            if(mode==6)world.explosions=false;
            tick.invoke(block,world.getBlockState(pos),world,pos,RandomSource.create(4));
            assertSame(state,world.getBlockState(fuel));assertEquals(0,world.burns);assertEquals(4,world.getBlockState(pos).seconds);
        }
        var world=new World();world.cells.put(BlockPos.ZERO,block.defaultBlockState());world.rain.add(BlockPos.ZERO);
        tick.invoke(block,world.getBlockState(BlockPos.ZERO),world,BlockPos.ZERO,RandomSource.create());assertSame(Catalog.AIR,world.getBlockState(BlockPos.ZERO).block);
    }
    public static class Property { }
    public static class Block {
        public static final Property SECONDS_LEFT=new Property(),AGE=new Property();
        public State defaultBlockState(){return new State(this,0,0);}
        public State getStateForPlacement(World level,BlockPos pos){return defaultBlockState();}
    }
    public static class State {
        final Block block;int seconds=5,flammability,spread,age;boolean wet;
        State(Block block,int flame,int spread){this.block=block;flammability=flame;this.spread=spread;}
        public boolean is(Block b){return block==b;}public boolean isAir(){return block==Catalog.AIR;}
        public Comparable<?> getValue(Property p){return p==Block.SECONDS_LEFT?seconds:age;}
        public Object setValue(Property p,Comparable<?> value){var next=new State(block,flammability,spread);next.seconds=seconds;next.age=age;next.wet=wet;
            if(p==Block.SECONDS_LEFT)next.seconds=(Integer)value;else next.age=(Integer)value;return next;}
        public Fluid getFluidState(){return new Fluid(wet);}
        public int getFlammability(World w,BlockPos p,Direction face){return flammability;}
        public int getFireSpreadSpeed(World w,BlockPos p,Direction face){return spread;}
        public void onCaughtFire(World w,BlockPos p,Direction face,Actor actor){w.burns++;}
        public boolean canSurvive(World w,BlockPos p){return true;}
    }
    public static class Catalog {public static final Block AIR=new Block(),FIRE=new Block();}
    public static class Fluid {final boolean wet;Fluid(boolean wet){this.wet=wet;}public boolean isEmpty(){return !wet;}}
    public static class Key { }
    public static class Rules {
        public static final Key RULE_DOFIRETICK=new Key(),RULE_MOBGRIEFING=new Key();boolean fire=true,grief=true;
        public boolean getBoolean(Key key){return key==RULE_DOFIRETICK?fire:grief;}
    }
    public static class Border {public boolean isWithinBounds(BlockPos p){return true;}}
    public static class Policy {
        public static boolean allowed(World world, net.minecraft.world.entity.Entity actor) {
            return world.explosions && world.rules.grief;
        }
    }
    public static class World {
        public boolean isClientSide,cancel,explosions=true;public int writes,burns;final Rules rules=new Rules();
        final Map<BlockPos,State> cells=new HashMap<>();final Map<BlockPos,Integer> scheduled=new HashMap<>();
        final Set<BlockPos> rain=new HashSet<>(),unloaded=new HashSet<>();
        public State getBlockState(BlockPos pos){return cells.computeIfAbsent(pos,p->Catalog.AIR.defaultBlockState());}
        public boolean setBlock(BlockPos pos,State state,int flags){assertEquals(3,flags);cells.put(pos,state);writes++;return true;}
        public void scheduleTick(BlockPos pos,Block block,int delay){scheduled.put(pos,delay);}
        public boolean isRainingAt(BlockPos pos){return rain.contains(pos);}public Rules getGameRules(){return rules;}
        public boolean hasChunkAt(BlockPos pos){return !unloaded.contains(pos);}public boolean isOutsideBuildHeight(BlockPos pos){return false;}
        public Border getWorldBorder(){return new Border();}public Dimension dimension(){return new Dimension();}
    }
    public static class Actor { }
    public static class Dimension { }
    public static class Snapshot {
        final World world;Snapshot(World world){this.world=world;}
        public static Snapshot create(Dimension dimension,World world,BlockPos pos){return new Snapshot(world);}
    }
    public static class Placement {
        public final Snapshot snapshot;public final State proposed;boolean canceled;
        public Placement(Snapshot snapshot,State against,State proposed){this.snapshot=snapshot;this.proposed=proposed;}
        public boolean isCanceled(){return canceled;}
    }
    public interface Bus {boolean post(Placement p);Placement postNeo(Placement p);}
    public static class BusHolder {public static final Bus EVENT_BUS=new Bus(){
        public boolean post(Placement p){p.canceled=p.snapshot.world.cancel;return p.canceled;}
        public Placement postNeo(Placement p){post(p);return p;}
    };}
}
