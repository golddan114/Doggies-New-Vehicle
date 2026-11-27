package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.*;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Execute compiled crowbar transaction; native box serialization/spawn is the replaceable edge. */
public class NuclearPackingExecutionTest {
    @Test void cancelledAndThrowingSpawnRestoreProgressFeAndInventory() throws Exception {
        for(int failure=1;failure<=4;failure++){
            var f=new Fixture();f.body.failure=failure;var before=f.body.cycle.snapshot();
            if(failure==1||failure==4)assertEquals(Result.FAIL,f.pack());
            else assertThrows(InvocationTargetException.class,f::pack);
            assertEquals(before,f.body.cycle.snapshot());assertEquals(12345,f.body.energy);
            assertEquals(4,f.body.inventory);assertFalse(f.body.removed);assertFalse(f.body.rack.removed);assertEquals(1,f.body.published);
        }
    }
    @Test void acceptedPackSnapshotsEmptyProgressAndZeroFeThenRemovesOnlyTheOldFacilityAndRack() throws Exception {
        var f=new Fixture();assertEquals(Result.SUCCESS,f.pack());assertTrue(f.body.removed);assertTrue(f.body.rack.removed);
        assertEquals(SiloCycle.Phase.EMPTY,f.body.box.snapshot.phase());assertEquals(0,f.body.box.energy);
        assertEquals(4,f.body.box.inventory);assertEquals(4,f.body.inventory);assertEquals(0,f.body.published);
    }
    @Test void busyAndUnownedFacilitiesRefuseBeforeSerializationOrSpawn() throws Exception {
        var f=new Fixture();f.body.allowed=false;assertEquals(Result.FAIL,f.pack());assertNull(f.body.box);
        f=new Fixture();f.body.cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.OPENING,5,UUID.randomUUID(),new SiloCycle.Target(1,64,2)));
        assertEquals(Result.FAIL,f.pack());assertNull(f.body.box);assertEquals(12345,f.body.energy);
    }
    static class Fixture {
        final Body body;final Class<?> type;
        Fixture() throws Exception {
            String source="com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",target="com/liucodex/doggiesnewvehicle/compat/packprobe/Facility";
            var node=new ClassNode();try(var input=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(input.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
            node.access=Opcodes.ACC_PUBLIC;node.superName=Type.getInternalName(Body.class);node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;
            node.methods.removeIf(m->!m.name.equals("onCrowbarInteract"));
            var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
            var names=new HashMap<String,String>();names.put(source,target);
            names.put("net/minecraft/world/entity/Entity",Type.getInternalName(Body.class));
            names.put("net/minecraft/world/level/Level",Type.getInternalName(World.class));names.put("net/minecraft/world/item/ItemStack",Type.getInternalName(Stack.class));
            names.put("net/minecraft/server/level/ServerLevel",Type.getInternalName(World.class));
            names.put("net/minecraft/world/entity/player/Player",Type.getInternalName(Player.class));names.put("net/minecraft/world/InteractionHand",Type.getInternalName(Hand.class));
            names.put("net/minecraft/world/InteractionResult",Type.getInternalName(Result.class));names.put("net/minecraft/network/chat/Component",Type.getInternalName(Text.class));
            names.put("net/minecraft/network/chat/MutableComponent",Type.getInternalName(MutableText.class));names.put("net/minecraft/world/entity/item/ItemEntity",Type.getInternalName(Drop.class));
            names.put("com/atsuishio/superbwarfare/item/container/ContainerBlockItem",Type.getInternalName(Box.class));
            names.put("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Type.getInternalName(Body.class));
            names.put("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearMissileEntity",Type.getInternalName(Rack.class));
            var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String name){return names.getOrDefault(name,name);}}));
            type=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));body=(Body)type.getConstructor().newInstance();
            body.cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.RELOADING,3123,null,null));
        }
        Object pack() throws Exception {return type.getMethod("onCrowbarInteract",Stack.class,Player.class,Hand.class).invoke(body,new Stack(),new Player(),Hand.MAIN_HAND);}
    }
    public static class Body {public SiloCycle cycle=new SiloCycle(NuclearRules.DEFAULT);public UUID rackId=UUID.randomUUID();public Rack rack=new Rack();
        public int energy=12345,inventory=4,published,failure;public boolean allowed=true,removed;public Stack box;
        public World world=new World();public UUID id=UUID.randomUUID();public boolean isRemoved(){return removed;}public UUID getUUID(){return id;}
        public boolean canBindIndicator(Player player){return allowed;}public World level(){return world;}public int getEnergy(){return energy;}public void setEnergy(int value){energy=value;}
        public Drop spawnAtLocation(Stack value){box=value;if(failure==2)throw new IllegalStateException("spawn event");
            if(failure==1)return null;var drop=new Drop();if(failure!=4)world.drop=drop;return drop;}
        public Rack rack(){return rack;}public void discard(){removed=true;}public void publish(){published++;}}
    public static class World {public boolean isClientSide;public Drop drop;public Body getEntity(UUID id){return drop!=null&&drop.id.equals(id)?drop:null;}}
    public static class Player {public void displayClientMessage(Text value,boolean actionBar){}}
    public enum Hand {MAIN_HAND}
    public enum Result {FAIL,SUCCESS;public static Result sidedSuccess(boolean client){return SUCCESS;}}
    public interface Text {static MutableText translatable(String key){return new MutableText();}static MutableText translatable(String key,Object... arguments){return new MutableText();}}
    public static class MutableText implements Text { }
    public static class Stack {public SiloCycle.Snapshot snapshot;public int energy,inventory;}
    public static class Drop extends Body { }
    public static class Rack {public boolean removed;public void discard(){removed=true;}}
    public static class Box {public static Stack createInstance(Body body){if(body.failure==3)throw new IllegalStateException("serialize");var value=new Stack();value.snapshot=body.cycle.snapshot();value.energy=body.energy;value.inventory=body.inventory;return value;}}
}
