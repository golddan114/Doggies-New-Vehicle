package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.boss.mesh.DnvMeshCodec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes compiled render/culling methods against captured vertex and frustum ports; GPU appearance is a field check. */
public class BossRenderExecutionTest {
    public enum Layer {
        BODY,LIGHT;
        public static Layer eyes(ResourceLocation texture){return LIGHT;}
        public static Layer entityCutoutNoCull(ResourceLocation texture){return BODY;}
        public static Layer entityTranslucent(ResourceLocation texture){return BODY;}
    }
    public interface Buffers {VertexConsumer getBuffer(Layer type);}
    public static class Frustum {
        public final AABB visible;public Frustum(AABB visible){this.visible=visible;}
        public boolean isVisible(AABB box){return visible.intersects(box);}
    }
    public static class Ship {
        public Vec3 origin=new Vec3(0,90,0),offset=new Vec3(0,0,512);
        public Vec3 position(){return origin;}public Vec3 renderBeamOffset(){return offset;}
        public long renderShotStarted(){return offset.equals(Vec3.ZERO)?-1:0;}
        public long renderAbortStarted(){return -1;}
        public boolean entryActive(){return true;}
        public Vec3 renderBeaconAnchor(){return origin.add(0,-35,0);}
        public double distanceToSqr(double x,double y,double z){return origin.distanceToSqr(x,y,z);}
        public AABB getBoundingBox(){return new AABB(origin.add(-45,0,-45),origin.add(45,10,45));}
    }
    public static class Laser extends Ship {
        public Vec3 beamEnd(){return origin.add(offset);}
        public AABB getBoundingBoxForCulling(){return BeamVisibility.bounds(origin,beamEnd());}
    }
    public static class LaserData {
        public float length;
        public void set(Object key,Object value){length=(Float)value;}
    }
    @Test void actualSideLaserLengthSetterKeepsOffsetMuzzleDistancesAndBoundsInvalidInput()throws Exception {
        Class<?> entity=load("com/liucodex/noise/entity/NoiseLaserBoltEntity");
        Object instance=entity.getConstructor().newInstance();LaserData data=new LaserData();
        entity.getField("entityData").set(instance,data);
        var setter=entity.getMethod("setBeamLength",float.class);
        setter.invoke(instance,145F);assertEquals(145,data.length);
        setter.invoke(instance,600F);assertEquals(256,data.length);
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1}) {
            setter.invoke(instance,invalid);assertEquals(.05F,data.length);
        }
    }
    @Test void deathBlackensBodyVerticesAndSkipsEveryEmissiveVertex()throws Exception {
        Class<?> renderer=load("com/liucodex/doggiesnewvehicle/boss/client/DnvMothershipRenderer");
        var draw=renderer.getDeclaredMethod("draw",PoseStack.class,Buffers.class,DnvMeshCodec.Part.class,int.class,boolean.class,int.class);draw.setAccessible(true);
        for(boolean wreck:new boolean[]{false,true}) {
            List<Integer> colors=new ArrayList<>();List<Layer> layers=new ArrayList<>();int[] vertices={0};
            VertexConsumer vertex=(VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{VertexConsumer.class},(self,method,args)->{
                if(method.getName().equals("color"))colors.add((Integer)args[0]);
                if(method.getName().equals("endVertex"))vertices[0]++;
                return method.getReturnType()==VertexConsumer.class?self:null;
            });
            Buffers buffers=layer->{layers.add(layer);return vertex;};
            draw.invoke(null,new PoseStack(),buffers,new DnvMeshCodec.Part(0,new float[24]),0,wreck,255);
            draw.invoke(null,new PoseStack(),buffers,new DnvMeshCodec.Part(1,new float[24]),0,wreck,255);
            assertEquals(wreck?4:8,vertices[0]);assertTrue(colors.stream().allMatch(c->c==(wreck?45:255)));
            assertEquals(wreck?List.of(Layer.BODY):List.of(Layer.BODY,Layer.LIGHT),layers);
        }
    }
    @Test void motherRendererAcceptsTheBeamEndInViewWhenTheShipOriginIsOver256BlocksAway()throws Exception {
        Class<?> renderer=load("com/liucodex/doggiesnewvehicle/boss/client/DnvMothershipRenderer");
        Ship ship=new Ship();Vec3 camera=new Vec3(10,90,500);Frustum view=new Frustum(new AABB(camera,camera).inflate(20));
        assertTrue(ship.distanceToSqr(camera.x,camera.y,camera.z)>256*256);
        var predicate=renderer.getMethod("shouldRender",Ship.class,Frustum.class,double.class,double.class,double.class);
        assertEquals(true,predicate.invoke(renderer.getConstructor().newInstance(),ship,view,camera.x,camera.y,camera.z));
        ship.offset=Vec3.ZERO;
        assertEquals(false,predicate.invoke(renderer.getConstructor().newInstance(),ship,view,camera.x,camera.y,camera.z));
    }
    @Test void sideLaserUsesFullRayBoundsAt128BlocksAndStillRejectsAHiddenOrDistantBeam()throws Exception {
        Class<?> renderer=load("com/liucodex/noise/client/NoiseLaserBoltRenderer");Laser laser=new Laser();laser.offset=new Vec3(0,-30,128);
        Vec3 camera=new Vec3(0,60,128);Frustum view=new Frustum(new AABB(camera,camera).inflate(4));
        var predicate=renderer.getMethod("shouldRender",Laser.class,Frustum.class,double.class,double.class,double.class);
        Object instance=renderer.getConstructor().newInstance();
        assertEquals(true,predicate.invoke(instance,laser,view,camera.x,camera.y,camera.z));
        assertEquals(false,predicate.invoke(instance,laser,view,600D,60D,128D));
        assertEquals(false,predicate.invoke(instance,laser,new Frustum(new AABB(900,900,900,901,901,901)),camera.x,camera.y,camera.z));
    }
    private static Class<?> load(String original)throws Exception {
        String generated=original+"DrawProbe";ClassNode node=new ClassNode();
        try(var input=BossRenderExecutionTest.class.getClassLoader().getResourceAsStream(original+".class")){new ClassReader(input).accept(node,0);}
        boolean entity=original.endsWith("NoiseLaserBoltEntity");
        node.methods.removeIf(m->!(entity?Set.of("setBeamLength"):Set.of("draw","shouldRender","<clinit>")).contains(m.name)||(m.access&Opcodes.ACC_BRIDGE)!=0);
        node.fields.removeIf(field->(field.access&Opcodes.ACC_STATIC)==0);
        if(entity)node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"entityData",Type.getDescriptor(LaserData.class),null,null));
        node.superName="java/lang/Object";node.signature=null;node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;
        MethodNode ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));ctor.maxStack=1;ctor.maxLocals=1;node.methods.add(ctor);
        Map<String,String> map=new HashMap<>();map.put(original,generated);
        map.put("net/minecraft/network/syncher/SynchedEntityData",Type.getInternalName(LaserData.class));
        map.put("net/minecraft/network/syncher/EntityDataAccessor","java/lang/Object");
        map.put("net/minecraft/client/renderer/RenderType",Type.getInternalName(Layer.class));map.put("net/minecraft/client/renderer/MultiBufferSource",Type.getInternalName(Buffers.class));
        map.put("net/minecraft/client/renderer/culling/Frustum",Type.getInternalName(Frustum.class));
        map.put("com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity",Type.getInternalName(Ship.class));
        if(!entity)map.put("com/liucodex/noise/entity/NoiseLaserBoltEntity",Type.getInternalName(Laser.class));
        ClassWriter writer=new ClassWriter(0);node.accept(new ClassRemapper(writer,new SimpleRemapper(map)));byte[] bytes=writer.toByteArray();
        return new ClassLoader(BossRenderExecutionTest.class.getClassLoader()){Class<?> define(){return defineClass(generated.replace('/','.'),bytes,0,bytes.length);}}.define();
    }
}
