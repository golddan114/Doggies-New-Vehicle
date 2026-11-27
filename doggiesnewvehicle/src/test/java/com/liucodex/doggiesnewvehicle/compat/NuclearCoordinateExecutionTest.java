package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Execute production coordinate commands and save/load; native/world/feedback services are explicit edges. */
class NuclearCoordinateExecutionTest {
    @Test void settingAcknowledgesExactCoordinatesWithoutSpendingAndRejectsBusyOrInvalidOrders() throws Exception {
        var body=body();var actor=new Actor();var target=new BlockPos(123,64,-456);
        var set=body.getClass().getMethod("setIndicatorTarget",Actor.class,BlockPos.class,int.class,boolean.class);
        assertEquals(true,set.invoke(body,actor,target,256,true));assertEquals(target,body.indicatorTarget);
        assertEquals(1,body.publishes);assertEquals(1,actor.messages.size());assertFalse(actor.overlay);
        for(int radius:new int[]{-1,257})assertEquals(false,set.invoke(body,actor,BlockPos.ZERO,radius,false));
        body.valid=false;assertEquals(false,set.invoke(body,actor,BlockPos.ZERO,0,false));body.valid=true;
        body.allowed=false;assertEquals(false,set.invoke(body,actor,BlockPos.ZERO,0,false));body.allowed=true;
        actor.spectator=true;assertEquals(false,set.invoke(body,actor,BlockPos.ZERO,0,false));actor.spectator=false;
        actor.alive=false;assertEquals(false,set.invoke(body,actor,BlockPos.ZERO,0,false));actor.alive=true;
        for(var phase:List.of(SiloCycle.Phase.OPENING,SiloCycle.Phase.RETURNING,SiloCycle.Phase.DEPARTING,SiloCycle.Phase.CLOSING)){
            body.cycle.restore(new SiloCycle.Snapshot(phase,1,UUID.randomUUID(),new SiloCycle.Target(1,64,1)));
            assertEquals(false,set.invoke(body,actor,BlockPos.ZERO,0,false));
        }
        assertEquals(target,body.indicatorTarget);assertEquals(1,body.publishes);assertEquals(1,actor.messages.size());
    }
    @Test void coordinateSurvivesActualSaveLoadAndClearPublishesWithoutChangingFrozenLaunch() throws Exception {
        var body=body();body.anchor=new BlockPos(-10,62,20);body.indicatorTarget=new BlockPos(1000,70,-2000);
        body.rackId=UUID.randomUUID();var frozen=new SiloCycle.Target(300,64,500);
        body.cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.OPENING,10,UUID.randomUUID(),frozen));
        var save=body.getClass().getMethod("addAdditionalSaveData",CompoundTag.class);var tag=new CompoundTag();save.invoke(body,tag);
        var restored=body();restored.getClass().getMethod("readAdditionalSaveData",CompoundTag.class).invoke(restored,tag);
        assertEquals(body.indicatorTarget,restored.indicatorTarget);assertEquals(body.anchor,restored.anchor);
        assertEquals(body.rackId,restored.rackId);assertEquals(body.cycle.snapshot(),restored.cycle.snapshot());
        var actor=new Actor();restored.getClass().getMethod("clearIndicatorTarget",Actor.class).invoke(restored,actor);
        assertNull(restored.indicatorTarget);assertEquals(frozen,restored.cycle.snapshot().target());
        assertEquals(1,restored.publishes);assertEquals(1,actor.messages.size());
        restored.valid=false;restored.getClass().getMethod("readAdditionalSaveData",CompoundTag.class).invoke(restored,tag);
        assertNull(restored.indicatorTarget);assertNull(restored.invalidData,"Out-of-bounds selection clears only the selection");
    }
    static Body body() throws Exception {
        String source="com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",target="com/liucodex/doggiesnewvehicle/compat/coordinateprobe/Entity";
        var node=new ClassNode();try(var in=NuclearCoordinateExecutionTest.class.getResourceAsStream("/"+source+".class")){
            new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);
        }
        node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;
        node.methods.removeIf(m->!Set.of("setIndicatorTarget","clearIndicatorTarget","addAdditionalSaveData","readAdditionalSaveData").contains(m.name));
        for(var m:node.methods){m.access=Opcodes.ACC_PUBLIC;
            for(var instruction:m.instructions)if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.owner.equals(node.superName)){
                call.owner=Type.getInternalName(Body.class);call.name=m.name.equals("addAdditionalSaveData")?"baseSave":"baseRead";
            }
        }
        node.access=Opcodes.ACC_PUBLIC;node.superName=Type.getInternalName(Body.class);
        var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
        var names=Map.of(source,target,"net/minecraft/server/level/ServerPlayer",Type.getInternalName(Actor.class),
                "net/minecraft/world/entity/player/Player",Type.getInternalName(Actor.class));
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String n){return names.getOrDefault(n,n);}}));
        return (Body)new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.')).getConstructor().newInstance();
    }
    public static class Body {
        public final SiloCycle cycle=new SiloCycle(NuclearRules.DEFAULT);public BlockPos anchor,indicatorTarget;
        public UUID lastDebit,rackId;public CompoundTag invalidData;public boolean allowed=true,valid=true;public int publishes;
        public long haltedAt=-1;
        public boolean canBindIndicator(Actor actor){return allowed;}public boolean validTarget(BlockPos target){return valid&&target!=null;}
        public void publish(){publishes++;}public Component statusMessage(){return Component.literal("status");}
        public void baseSave(CompoundTag tag){}public void baseRead(CompoundTag tag){}public BlockPos blockPosition(){return BlockPos.ZERO;}
    }
    public static class Actor {public boolean alive=true,spectator,overlay;public List<Component> messages=new ArrayList<>();
        public boolean isAlive(){return alive;}public boolean isSpectator(){return spectator;}
        public void displayClientMessage(Component message,boolean value){messages.add(message);overlay=value;}}
}
