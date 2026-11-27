package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

/** Executes the production post-blast ignition method; height queries must never acquire missing chunks. */
class MothershipIgnitionExecutionTest {
    @Test void unloadedColumnsNeverReadHeightOrWriteFire() throws Exception {
        World world=new World();world.loaded=false;
        ignite(world);
        assertEquals(0,world.heights);assertTrue(world.fire.isEmpty());
    }

    @Test void loadedIgnitionHonorsGameruleProtectionSupportAndBoundedRadius() throws Exception {
        World disabled=new World();disabled.griefing=false;ignite(disabled);
        assertEquals(0,disabled.heights);assertTrue(disabled.fire.isEmpty());
        World protectedWorld=new World();protectedWorld.protectedBlock=true;ignite(protectedWorld);
        assertTrue(protectedWorld.heights>0);assertTrue(protectedWorld.fire.isEmpty());
        World unsupported=new World();unsupported.support=false;ignite(unsupported);assertTrue(unsupported.fire.isEmpty());
        World loaded=new World();ignite(loaded);
        assertFalse(loaded.fire.isEmpty());assertTrue(loaded.fire.size()<=MothershipDoomsday.MAX_FIRE_SITES);
        assertEquals(loaded.fire.size(),loaded.fire.stream().distinct().count());
        for(BlockPos pos:loaded.fire)assertTrue(pos.getCenter().distanceToSqr(new Vec3(0,64,0))<=16*16);
    }

    private static final Class<?> SUBJECT=load();
    private static void ignite(World world)throws Exception {
        SUBJECT.getMethod("igniteGround",World.class,Vec3.class,double.class)
                .invoke(SUBJECT.getConstructor().newInstance(),world,new Vec3(0,64,0),16D);
    }
    private static Class<?> load() {
        String source="com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity";
        String target="com/liucodex/doggiesnewvehicle/boss/ExecutedIgnition";
        try(var input=MothershipIgnitionExecutionTest.class.getResourceAsStream('/'+source+".class")) {
            assertNotNull(input);ClassNode node=new ClassNode();new ClassReader(input).accept(node,0);
            node.methods.removeIf(method->!method.name.equals("igniteGround"));
            node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;
            node.superName=Type.getInternalName(Ship.class);node.access=Opcodes.ACC_PUBLIC;
            node.methods.get(0).access=Opcodes.ACC_PUBLIC;
            MethodNode constructor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
            constructor.instructions.add(new InsnNode(Opcodes.RETURN));constructor.maxStack=1;constructor.maxLocals=1;
            node.methods.add(constructor);
            Map<String,String> names=new HashMap<>();names.put(source,target);
            for(String type:List.of("Level","LevelReader","LevelAccessor","BlockGetter"))
                names.put("net/minecraft/world/level/"+type,Type.getInternalName(World.class));
            for(var entry:Map.of(
                    "net/minecraft/server/level/ServerLevel",World.class,"net/minecraft/world/entity/Entity",Ship.class,
                    "net/minecraft/world/level/levelgen/Heightmap$Types",HeightType.class,
                    "net/minecraft/world/level/border/WorldBorder",Border.class,
                    "net/minecraft/world/level/block/state/BlockState",State.class,
                    "net/minecraft/world/level/block/BaseFireBlock",Fire.class,
                    "net/minecraftforge/event/ForgeEventFactory",Events.class,
                    "net/minecraftforge/common/util/BlockSnapshot",Snapshot.class).entrySet())
                names.put(entry.getKey(),Type.getInternalName(entry.getValue()));
            names.put("com/liucodex/doggiesnewvehicle/compat/DnvBlockDamagePolicy",Type.getInternalName(Events.class));
            names.put("com/liucodex/doggiesnewvehicle/world/DnvControlledFire",Type.getInternalName(Fire.class));
            names.put("net/minecraft/world/level/block/Block",Type.getInternalName(Fire.class));
            names.put("net/minecraftforge/registries/RegistryObject",Type.getInternalName(Ref.class));
            names.put("net/neoforged/neoforge/registries/DeferredHolder",Type.getInternalName(Ref.class));
            ClassWriter output=new ClassWriter(0);node.accept(new ClassRemapper(output,new SimpleRemapper(names)));
            byte[] bytes=output.toByteArray();
            return new ClassLoader(MothershipIgnitionExecutionTest.class.getClassLoader()) {
                Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}
            }.define();
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    public static class Ship {public final MothershipDoomsday doomsday=new MothershipDoomsday();}
    public enum HeightType {MOTION_BLOCKING_NO_LEAVES}
    public static class World {
        boolean loaded=true,griefing=true,protectedBlock,support=true;int heights;
        final List<BlockPos> fire=new ArrayList<>();
        public boolean hasChunk(int x,int z){return loaded;}
        public int getHeight(HeightType type,int x,int z){assertTrue(loaded,"Height lookup would load a missing chunk");heights++;return 64;}
        public Border getWorldBorder(){return new Border();}
        public int getMinBuildHeight(){return -64;}public int getMaxBuildHeight(){return 320;}
        public boolean isEmptyBlock(BlockPos pos){return true;}
        public State getBlockState(BlockPos pos){assertTrue(loaded);return new State();}
        public ResourceKey<?> dimension(){return null;}
        public boolean setBlock(BlockPos pos,State state,int flags){assertTrue(loaded);fire.add(pos);return true;}
    }
    public static class Border {public boolean isWithinBounds(BlockPos pos){return true;}}
    public static class State {
        public boolean isFaceSturdy(World world,BlockPos pos,Direction direction){return world.support;}
        public boolean canSurvive(World world,BlockPos pos){return true;}
    }
    public static class Ref {public Object get(){return new Fire();}}
    public static class Fire {public static final Ref FIRE=new Ref();
        public static void place(World world,BlockPos pos,Ship ship,Fire fire){
            if(world.griefing&&!world.protectedBlock&&world.support)world.setBlock(pos,new State(),3);
        }}
    public record Snapshot(World world) {
        public static Snapshot create(ResourceKey<?> dimension,World world,BlockPos pos){return new Snapshot(world);}
    }
    public static class Events {
        public static boolean allowed(World world,Ship ship){return world.griefing;}
        public static boolean getMobGriefingEvent(World world,Ship ship){return world.griefing;}
        public static boolean onBlockPlace(Ship ship,Snapshot snapshot,Direction direction){return snapshot.world.protectedBlock;}
    }
}
