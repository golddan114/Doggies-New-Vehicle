package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the production travel limiter and AI virtual overrides around the native physics seam. */
class ApocalypseMobilityExecutionTest {
    @Test void nativeTravelUsesImprovedPlayerCapsAndRetainsAiAndTurretLimits() throws Exception {
        for(boolean ai:List.of(false,true))for(int sign:new int[]{-1,1}) {
            var tank=create(ai);tank.yaw=179*sign;tank.nativeTurn=12*sign;
            tank.nativeVelocity=new Vec3(0,.2,2*sign);tank.travel();
            assertEquals((ai?1:.65)*sign+179*sign,tank.yaw,1E-5);
            assertEquals(sign<0?(ai?.2:.15):(ai?.4:.30),tank.velocity.horizontalDistance(),1E-8);
            assertEquals(.2,tank.velocity.y,1E-8);assertFalse(tank.sprint);
            assertEquals(ai?2.25F:.9F,(float)tank.getClass().getMethod("getTurretTurnXSpeed").invoke(tank));
            assertEquals(ai?2.25F:.9F,(float)tank.getClass().getMethod("getTurretTurnYSpeed").invoke(tank));
        }
    }
    @Test void subLimitNativeMotionAndAirborneVelocityArePreservedForBothKinds() throws Exception {
        for (boolean ai:List.of(false,true)) {
            var tank=create(ai);tank.nativeTurn=.25F;tank.nativeVelocity=new Vec3(.01,-.3,.02);tank.travel();
            assertEquals(.25F,tank.yaw);assertEquals(tank.nativeVelocity,tank.velocity);
            tank.grounded=false;tank.nativeVelocity=new Vec3(1,-.3,2);tank.travel();
            assertEquals(tank.nativeVelocity,tank.velocity);
        }
    }
    @Test void playerRemainsExactlyTwoAndOneBlocksPerSecondSlowerThanAlliedAi() throws Exception {
        var player=create(false);var ai=create(true);
        double forward=(double)ai.getClass().getMethod("forwardSpeedLimit").invoke(ai)
                -(double)player.getClass().getMethod("forwardSpeedLimit").invoke(player);
        double reverse=(double)ai.getClass().getMethod("reverseSpeedLimit").invoke(ai)
                -(double)player.getClass().getMethod("reverseSpeedLimit").invoke(player);
        assertEquals(2,forward*20,1E-8);assertEquals(1,reverse*20,1E-8);
        assertEquals(13,20*(float)player.getClass().getMethod("hullTurnLimit").invoke(player),1E-5);
    }

    private NativeVehicle create(boolean ai)throws Exception {
        String root="com/liucodex/doggiesnewvehicle/armored/",out="com/liucodex/doggiesnewvehicle/compat/executionprobe/Mobility";
        Map<String,String> names=Map.of(root+"DnvApocalypseEntity",out+"Player",root+"DnvAlliedApocalypseEntity",out+"Ai",
                "com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Type.getInternalName(NativeVehicle.class));
        Map<String,byte[]> definitions=new HashMap<>();
        for(String source:List.of("DnvApocalypseEntity","DnvAlliedApocalypseEntity")) {
            ClassNode n=new ClassNode();try(var in=getClass().getResourceAsStream("/"+root+source+".class")) {
                new ClassReader(in.readAllBytes()).accept(n,ClassReader.SKIP_FRAMES);
            }
            boolean child=source.equals("DnvAlliedApocalypseEntity");
            n.interfaces.clear();n.fields.clear();
            Set<String> methods=new HashSet<>(List.of("forwardSpeedLimit","reverseSpeedLimit","hullTurnLimit","getTurretTurnXSpeed","getTurretTurnYSpeed"));
            if(!child)methods.add("travel");
            n.methods.removeIf(m->!methods.contains(m.name));
            n.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
            MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,n.superName,"<init>","()V",false));
            init.instructions.add(new InsnNode(Opcodes.RETURN));n.methods.add(init);
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
            n.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}));
            definitions.put(names.get(root+source),writer.toByteArray());
        }
        return (NativeVehicle)new ExactQueuedShotFixture.Loader(definitions).loadClass((out+(ai?"Ai":"Player")).replace('/','.'))
                .getConstructor().newInstance();
    }
    public static class NativeVehicle {
        public float yaw,nativeTurn;public boolean grounded=true,sprint=true;
        public Vec3 nativeVelocity=Vec3.ZERO,velocity=Vec3.ZERO;
        public void travel(){yaw+=nativeTurn;velocity=nativeVelocity;}
        public float getYRot(){return yaw;}public void setYRot(float value){yaw=value;}
        public void setSprintInputDown(boolean value){sprint=value;}
        public Vec3 getDeltaMovement(){return velocity;}public Vec3 getViewVector(float partial){return new Vec3(0,0,1);}
        public boolean onGround(){return grounded;}public void setDeltaMovement(double x,double y,double z){velocity=new Vec3(x,y,z);}
    }
}
