package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Runs the pinned SBW resolved-motion position write. Collision is an explicit world port. */
public class TrackedBossNativeMoveTest {
    public static class Body {
        public Vec3 position=new Vec3(271,93,-185),resolved=Vec3.ZERO;
        public AABB broad=new AABB(267,93,-193,274,102,-180);
        public Vec3 vCollide(Vec3 requested){return resolved;}
        public double getX(){return position.x;}public double getY(){return position.y;}public double getZ(){return position.z;}
        public void setPos(double x,double y,double z){position=new Vec3(x,y,z);}
    }
    @Test void actualNativePositionWriteIgnoresGunOverhangAndUsesOnlyResolvedMovement()throws Exception {
        Class<?> type=nativeMove();Body body=(Body)type.getConstructor().newInstance();
        var move=type.getMethod("resolvedMove",MoverType.class,Vec3.class);
        for(int turn=0;turn<1200;turn++) {
            body.broad=turn%2==0?new AABB(266,93,-190,273,102,-178):new AABB(269,93,-193,278,101,-183);
            move.invoke(body,MoverType.SELF,new Vec3(0,-.08,0));
        }
        assertEquals(new Vec3(271,93,-185),body.position,"stationary turret rotation moved the chassis");
        body.resolved=new Vec3(.02,0,-.01);
        move.invoke(body,MoverType.SELF,new Vec3(.1,-.08,-.1));
        assertEquals(new Vec3(271.02,93,-185.01),body.position);
        body.resolved=Vec3.ZERO;move.invoke(body,MoverType.SELF,new Vec3(.1,0,0));
        assertEquals(new Vec3(271.02,93,-185.01),body.position,"blocked move drifted");
    }
    @Test void bothConcreteVehiclesOptIntoTheNativeObbMotionBranch()throws Exception {
        for(String owner:List.of("com/liucodex/noise/entity/NoiseVehicleEntity","com/liucodex/silentheaven/entity/SilentHeavenVehicleEntity")) {
            ClassNode node=read(owner);
            for(String method:List.of("getCollisionOBBInfo","getCollisionOBB")) {
                var code=node.methods.stream().filter(m->m.name.equals(method)).findFirst().orElseThrow();
                String expected=method.equals("getCollisionOBB")?"movement":"movementInfo";
                assertTrue(Arrays.stream(code.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode call
                        &&call.owner.endsWith("/DnvTrackedBossHull")&&call.name.equals(expected)));
            }
        }
    }
    @Test void apocalypseUsesTheNativePhysicsRegionIndependentlyOfItsDamageList() throws Exception {
        ClassNode node = read("com/liucodex/doggiesnewvehicle/armored/DnvApocalypseEntity");
        var collision = node.methods.stream().filter(m -> m.name.equals("getCollisionOBB")).findFirst().orElseThrow();
        var calls = Arrays.stream(collision.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).map(call -> call.name).toList();
        assertTrue(calls.contains("getCollisionOBBInfo"));
        assertTrue(calls.contains("getOBB"));
        assertFalse(calls.contains("getOBBs"));
        assertTrue(node.methods.stream().noneMatch(m -> m.name.equals("move") || m.name.equals("vMove")),
                "Tank must retain the exact SBW resolved-motion anchor exercised above");
    }
    private static ClassNode read(String name)throws Exception {
        ClassNode node=new ClassNode();
        try(var in=TrackedBossNativeMoveTest.class.getClassLoader().getResourceAsStream(name+".class")){new ClassReader(in).accept(node,0);}return node;
    }
    private static Class<?> nativeMove()throws Exception {
        String parent="com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",target=Type.getInternalName(Body.class)+"NativeMove";
        ClassNode source=new ClassNode();
        try(ZipFile zip=new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
            new ClassReader(zip.getInputStream(zip.getEntry(parent+".class"))).accept(source,0);
        }
        MethodNode nativeMove=source.methods.stream().filter(m->m.name.equals("vMove")).findFirst().orElseThrow();
        AbstractInsnNode start=null,last=null;
        for(var ins:nativeMove.instructions)if(ins instanceof MethodInsnNode call) {
            if(call.name.equals("vCollide"))start=ins.getPrevious().getPrevious();
            if(start!=null&&call.name.equals("m_6034_")){last=ins;break;}
        }
        assertNotNull(start);assertNotNull(last);
        LabelNode stop=null;
        for(var ins=start;ins!=last;ins=ins.getNext())if(ins instanceof JumpInsnNode jump)stop=jump.label;
        assertNotNull(stop);
        MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC,"resolvedMove",nativeMove.desc,null,null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,2));method.instructions.add(new VarInsnNode(Opcodes.ASTORE,3));
        Map<LabelNode,LabelNode> labels=new HashMap<>();
        for(var ins=start;;ins=ins.getNext()){if(ins instanceof LabelNode label)labels.put(label,new LabelNode());if(ins==stop)break;}
        for(var ins=start;;ins=ins.getNext()) {
            if(!(ins instanceof FrameNode)&&!(ins instanceof LineNumberNode))method.instructions.add(ins.clone(labels));
            if(ins==stop)break;
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        ClassNode node=new ClassNode();node.version=Opcodes.V17;node.access=Opcodes.ACC_PUBLIC;node.name=target;node.superName=Type.getInternalName(Body.class);
        node.methods.add(method);
        MethodNode ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
        ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(out,new Remapper(){
            @Override public String map(String value){return value.equals(parent)?Type.getInternalName(Body.class):value;}
            @Override public String mapMethodName(String owner,String name,String descriptor) {
                return switch(name){case "m_20185_"->"getX";case "m_20186_"->"getY";case "m_20189_"->"getZ";case "m_6034_"->"setPos";case "m_82556_"->"length";default->name;};
            }
            @Override public String mapFieldName(String owner,String name,String descriptor){return switch(name){case "f_82479_"->"x";case "f_82480_"->"y";case "f_82481_"->"z";default->name;};}
        }));
        byte[] bytes=out.toByteArray();return new ClassLoader(TrackedBossNativeMoveTest.class.getClassLoader()) {Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}}.define();
    }
}
