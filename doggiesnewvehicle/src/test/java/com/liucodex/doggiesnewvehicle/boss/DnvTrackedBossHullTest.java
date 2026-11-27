package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.boss.mesh.DnvMeshCodec;
import com.liucodex.doggiesnewvehicle.entity.DnvVehiclePresentation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

public class DnvTrackedBossHullTest {
    @Test void nativeMovementChassisIsIndependentOfTurretsAndExcludedFromHitShapes()throws Exception {
        for(boolean heaven:new boolean[]{false,true})for(double yaw:new double[]{0,47,138}) {
            net.minecraft.world.phys.AABB resting=null;
            for(int pose=0;pose<3;pose++) {
                Object hull=NativeHullFixture.trackedHull(heaven,yaw,state(pose,(float)yaw));
                Object movement=hull.getClass().getMethod("movement").invoke(hull);
                var bounds=new NativeHullFixture.Box(movement).bounds();
                if(resting==null)resting=bounds;else assertEquals(resting,bounds);
                assertEquals(93,bounds.minY,1e-8);
                assertFalse(((List<?>)hull.getClass().getMethod("boxes").invoke(hull)).contains(movement));
                var info=(NativeHullFixture.MovementInfo)hull.getClass().getMethod("movementInfo").invoke(hull);
                assertEquals(info.getPosition().y,info.getSize().y,1e-10);
                assertTrue(info.getSize().x<2.4,"old oversized chassis retained");
            }
        }
    }
    @Test void nativeCollisionCoversActualRendererVerticesAtRestAndIndependentWeaponPoses()throws Exception {
        for(boolean heaven:new boolean[]{false,true}) {
            String namespace=heaven?"silent_heaven":"noise";
            var mesh=DnvMeshCodec.parse(Files.readAllBytes(Path.of("src/boss/resources/assets/"+namespace+"/meshes/"+namespace+".shm")));
            Method render=renderer(heaven);
            for(int scenario=0;scenario<3;scenario++) {
                double yaw=scenario*71;
                DnvVehiclePresentation state=state(scenario,(float)yaw);
                var boxes=NativeHullFixture.tracked(heaven,yaw,state);
                assertTrue(boxes.size()<=16);
                for(var entry:mesh.entrySet()) {
                    String group=entry.getKey().split("/")[0];
                    if(group.startsWith("halo_")||group.startsWith("road_wheel_")||group.startsWith("main_gun_")
                            ||group.startsWith("autocannon_")&&group.endsWith("_pitch")
                            ||entry.getValue().material()!=0&&!group.startsWith("track_shoe_"))continue;
                    PoseStack stack=new PoseStack();stack.translate(271,93,-185);
                    stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)-yaw));
                    render.invoke(null,new Actor((float)yaw),state,0F,group,stack);
                    float[] vertices=entry.getValue().stream();
                    for(int i=0;i<vertices.length;i+=8) {
                        Vector3f p=stack.last().pose().transformPosition(new Vector3f(vertices[i],vertices[i+1],vertices[i+2]));
                        boolean covered=false;
                        for(var box:boxes)if(box.inflate(.002).contains(new Vec3(p.x,p.y,p.z))){covered=true;break;}
                        assertTrue(covered,namespace+" "+group+" pose "+scenario+" vertex "+i/8);
                    }
                }
                // Old 6.3-wide body accepted these empty-space hits on both sides of the chassis.
                for(double side:new double[]{-2.8,2.8}) {
                    Vec3 local=new Vec3(side,.5,0).yRot((float)-Math.toRadians(yaw)).add(271,93,-185);
                    for(var box:boxes)assertFalse(box.contains(local),"chassis air remained solid");
                }
            }
        }
        for(boolean heaven:new boolean[]{false,true})assertTrue(DnvTrackedBossHull.model(heaven).boxes().stream()
                .noneMatch(b->b.group().startsWith("main_gun_")||b.group().startsWith("autocannon_")&&b.group().endsWith("_pitch")));
    }
    static DnvVehiclePresentation state(int phase,float bodyYaw) {
        return (DnvVehiclePresentation)Proxy.newProxyInstance(DnvVehiclePresentation.class.getClassLoader(),new Class[]{DnvVehiclePresentation.class},(p,m,a)->{
            return switch(m.getName()) {
                case "getCoreYaw"->bodyYaw+phase*35F;
                case "getMainGunPitch"->phase*12F;
                case "getAutocannonLYaw"->bodyYaw-phase*28F;
                case "getAutocannonRYaw"->bodyYaw+phase*17F;
                case "getMissilePodLYaw"->bodyYaw+phase*60F;
                case "getMissilePodRYaw"->bodyYaw-phase*23F;
                case "getAutocannonLPitch","getAutocannonRPitch","getMissilePodLPitch","getMissilePodRPitch"->phase*10F;
                case "isPhaseTwo"->phase==2;
                case "isTransitioning"->phase==1;
                case "getTransitionTick"->phase==1?100:0;
                default->{
                    if(m.getReturnType()==boolean.class)yield false;
                    if(m.getReturnType()==int.class)yield 0;
                    if(m.getReturnType()==float.class)yield 0F;
                    yield null;
                }
            };
        });
    }
    public static class Actor {public float yRotO;Actor(float yaw){yRotO=yaw;}public float getYRot(){return yRotO;}}
    private static Method renderer(boolean heaven)throws Exception {
        String original=heaven?"com/liucodex/silentheaven/client/SilentHeavenMeshLayer":"com/liucodex/noise/client/NoiseMeshLayer";
        String generated=original+"HullProbe";
        ClassNode node=new ClassNode();
        try(var input=DnvTrackedBossHullTest.class.getClassLoader().getResourceAsStream(original+".class")){new ClassReader(input).accept(node,0);}
        node.superName="java/lang/Object";node.interfaces.clear();node.nestMembers=null;node.nestHostClass=null;node.innerClasses.clear();
        node.methods.removeIf(m->m.name.equals("<clinit>")||(m.access&Opcodes.ACC_STATIC)==0||m.name.startsWith("render"));
        node.methods.forEach(m->m.access=(m.access&~Opcodes.ACC_PRIVATE)|Opcodes.ACC_PUBLIC);
        ClassWriter out=new ClassWriter(0);node.accept(new ClassRemapper(out,new SimpleRemapper(Map.of(original,generated,"net/minecraft/world/entity/Entity",Type.getInternalName(Actor.class)))));
        byte[] bytes=out.toByteArray();Class<?> type=new ClassLoader(DnvTrackedBossHullTest.class.getClassLoader()) {Class<?> define(){return defineClass(generated.replace('/','.'),bytes,0,bytes.length);}}.define();
        return type.getMethod("applyGroupTransform",Actor.class,DnvVehiclePresentation.class,float.class,String.class,PoseStack.class);
    }
}
