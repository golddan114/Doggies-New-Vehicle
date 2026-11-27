package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the exact pinned upstream gravity/move tail with the actual compiled subclass overrides. */
public class MothershipNativePhysicsTest {
    public static final Gravity DATA=new Gravity();
    public static class Gravity {public double getGravity(){return .08;}}
    public static class Body {
        public Vec3 position=new Vec3(0,84,0),velocity=Vec3.ZERO;
        public int supports,crushes;public boolean active=true;
        public boolean entryActive(){return active;}
        public void move(MoverType type,Vec3 delta){position=position.add(delta);}
        public Vec3 getDeltaMovement(){return velocity;}
        public void setDeltaMovement(Vec3 value){velocity=value;}
        public void supportEntities(){supports++;}
        public void crushEntities(){crushes++;}
    }
    @Test void upstreamStillMovesANoGravityVehicleButTheMothershipHasOnePositionWriter() throws Exception {
        for(boolean fixed:new boolean[]{false,true}) {
            Class<?> type=compile(fixed);Body body=(Body)type.getConstructor().newInstance();
            for(int tick=0;tick<1200;tick++) {
                body.setDeltaMovement(Vec3.ZERO);
                type.getMethod("nativePhysics").invoke(body);
            }
            if(fixed) {
                assertEquals(new Vec3(0,84,0),body.position);
                assertEquals(0,body.supports);assertEquals(0,body.crushes);
            } else {
                assertEquals(-12,body.position.y,1e-8);
                assertEquals(1200,body.supports);assertEquals(1200,body.crushes);
            }
        }
    }
    @Test void committedFlightPoseInvokesNativeSupportAndCrushExactlyOnceWithoutAnotherMove() throws Exception {
        Class<?> type=compile(true);Body body=(Body)type.getConstructor().newInstance();
        var support=type.getDeclaredMethod("supportAfterMovement");support.setAccessible(true);
        for(int tick=0;tick<100;tick++) {
            body.velocity=Vec3.ZERO;type.getMethod("nativePhysics").invoke(body);
            body.position=body.position.add(.03,0,.01);
            support.invoke(body);
        }
        assertEquals(new Vec3(3,84,1).x,body.position.x,1e-8);
        assertEquals(84,body.position.y,1e-8);
        assertEquals(100,body.supports);assertEquals(100,body.crushes);
        body.active=false;support.invoke(body);
        assertEquals(100,body.supports);assertEquals(100,body.crushes);
    }
    private static Class<?> compile(boolean fixed) throws Exception {
        String original="com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity";
        String upstream="com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity";
        ClassNode owner=new ClassNode();
        try(var in=MothershipNativePhysicsTest.class.getClassLoader().getResourceAsStream(original+".class")){new ClassReader(in).accept(owner,0);}
        Set<String> keep=Set.of("move","supportEntities","crushEntities","supportAfterMovement");
        owner.methods.removeIf(m->!fixed||!keep.contains(m.name));
        owner.fields.clear();owner.interfaces.clear();owner.innerClasses.clear();owner.nestMembers=null;owner.nestHostClass=null;
        owner.superName=Type.getInternalName(Body.class);
        MethodNode ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,owner.superName,"<init>","()V",false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));owner.methods.add(ctor);
        ClassNode parent=new ClassNode();
        try(ZipFile zip=new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
            new ClassReader(zip.getInputStream(zip.getEntry(upstream+".class"))).accept(parent,0);
        }
        MethodNode base=parent.methods.stream().filter(m->m.name.equals("m_6075_")).findFirst().orElseThrow();
        AbstractInsnNode start=null,end=null;
        for(var ins:base.instructions)if(ins instanceof MethodInsnNode call) {
            if(call.name.equals("supportEntities"))start=ins.getPrevious();
            if(start!=null&&call.name.equals("m_6478_")){end=ins;break;}
        }
        assertNotNull(start);assertNotNull(end);
        MethodNode physics=new MethodNode(Opcodes.ACC_PUBLIC,"nativePhysics","()V",null,null);
        physics.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,Type.getInternalName(MothershipNativePhysicsTest.class),"DATA",Type.getDescriptor(Gravity.class)));
        physics.instructions.add(new VarInsnNode(Opcodes.ASTORE,1));
        var labels=new HashMap<LabelNode,LabelNode>();
        for(var ins=start;;ins=ins.getNext()) {
            if(ins.getOpcode()>=0)physics.instructions.add(ins.clone(labels));
            if(ins==end)break;
        }
        physics.instructions.add(new InsnNode(Opcodes.RETURN));owner.methods.add(physics);
        String target=original+"NativePhysics"+(fixed?"Fixed":"Negative");
        Map<String,String> map=Map.of(original,target,upstream,Type.getInternalName(Body.class),
                "com/atsuishio/superbwarfare/data/vehicle/DefaultVehicleData",Type.getInternalName(Gravity.class),
                upstream+".m_20184_()Lnet/minecraft/world/phys/Vec3;","getDeltaMovement",
                upstream+".m_20256_(Lnet/minecraft/world/phys/Vec3;)V","setDeltaMovement",
                upstream+".m_6478_(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V","move",
                "net/minecraft/world/phys/Vec3.m_82520_(DDD)Lnet/minecraft/world/phys/Vec3;","add");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);owner.accept(new ClassRemapper(writer,new SimpleRemapper(map)));
        byte[] bytes=writer.toByteArray();
        return new ClassLoader(MothershipNativePhysicsTest.class.getClassLoader()) {
            Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}
        }.define();
    }
}
