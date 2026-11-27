package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Runs actual preflight/repeated per-cell admission; transaction ownership and existing storage protection stay intact. */
class NuclearExcavationRuleExecutionTest {
    @Test void disablingGriefingStopsAutomaticDiggingButDoesNotConsumeOrBlockTheOwnersContainer()throws Exception{
        var map=new HashMap<String,String>();
        for(String type:List.of("world/level/Level","server/level/ServerLevel","world/level/BlockGetter"))
            NuclearRecoveryExecutionTest.map(map,"net/minecraft/"+type,World.class);
        for(String type:List.of("world/entity/Entity","world/entity/LivingEntity","world/entity/player/Player","server/level/ServerPlayer"))
            NuclearRecoveryExecutionTest.map(map,"net/minecraft/"+type,Actor.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/entity/player/Abilities",Abilities.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/item/ItemStack",Stack.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/block/state/BlockState",State.class);
        NuclearRecoveryExecutionTest.map(map,"net/minecraft/world/level/block/entity/BlockEntity",Object.class);
        NuclearRecoveryExecutionTest.map(map,"com/liucodex/doggiesnewvehicle/compat/DnvBlockDamagePolicy",Policy.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearContainerDeployment",
                Set.of("problem"),map,Ports.class,false);
        var admission=type.getMethod("problem",World.class,BlockPos.class,State.class,BlockPos.class,Actor.class);
        var world=new World();var actor=new Actor();var center=BlockPos.ZERO;var pos=center.offset(1,-1,0);var state=new State();
        assertNull(admission.invoke(null,world,pos,state,center,actor));
        world.modify=false;assertEquals("protected",admission.invoke(null,world,pos,state,center,actor));
        assertNull(admission.invoke(null,world,center,state,center,actor),"Converting the player's own box is not environmental excavation");
        state.air=true;assertNull(admission.invoke(null,world,pos,state,center,actor));
        world.protectedCell=true;assertEquals("protected",admission.invoke(null,world,center,state,center,actor));
    }
    public static class Ports {public static boolean permittedBreak(World w,BlockPos p,State state,Actor actor){return !w.protectedCell;}}
    public static class Policy {public static boolean terrainAllowed(World w,Actor actor){return w.modify;}}
    public static class Abilities {public boolean mayBuild=true;}
    public static class Stack { }
    public static class Actor {
        public Abilities getAbilities(){return new Abilities();}public boolean isSpectator(){return false;}
        public boolean mayUseItemAt(BlockPos p,Direction face,Stack stack){return true;}public Stack getMainHandItem(){return new Stack();}
    }
    public static class State {public boolean air;public boolean isAir(){return air;}
        public float getDestroySpeed(World w,BlockPos p){return 1;}public boolean hasBlockEntity(){return false;}}
    public static class World {public boolean modify=true,protectedCell;public boolean mayInteract(Actor actor,BlockPos p){return true;}
        public Object getBlockEntity(BlockPos p){return null;}}
}
