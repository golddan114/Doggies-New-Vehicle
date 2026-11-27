package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the compiled missile renderer and ETM emitter; camera, resource and GPU buffers are ports. */
class BlizzardMissileRenderExecutionTest {
    public static class Entity { }
    public static class Missile extends Entity {
        public float yRotO=10,xRotO=-5;
        public float getYRot(){return 20;} public float getXRot(){return -10;}
        public Vec3 getDeltaMovement(){return new Vec3(0,0,.6);}
    }
    public static class Context { }
    public static class Base {
        public Base(Context context) { }
        public void render(Entity e,float yaw,float partial,PoseStack pose,Buffers buffers,int light) { }
    }
    public static class Client {
        public static final Client INSTANCE=new Client();public Entity camera;
        public static Client getInstance(){return INSTANCE;} public Entity getCameraEntity(){return camera;}
    }
    public enum Layer {
        ARMOR,GLOW;
        public static Layer entityCutoutNoCull(ResourceLocation resource){return ARMOR;}
        public static Layer eyes(ResourceLocation resource){return GLOW;}
    }
    public interface Buffers {VertexConsumer getBuffer(Layer layer);}
    public static class Resources {
        public static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","textures/entity/"+name+".png");}
    }
    public static class Mesh {
        public static final Mesh BLIZZARD_MISSILE=new Mesh();public static DnvTurretMesh.MeshData data;
        public DnvTurretMesh.MeshData get(){return data;}
    }
    public static class Effects {
        public static void coldBolt(PoseStack pose,Buffers buffers,Vec3 velocity,double scale) { }
    }
    @Test void observersReceiveOpaqueAuthoredVerticesAndOnlyMissileCameraHidesItsOwnBody() throws Exception {
        var parse=DnvTurretMesh.class.getDeclaredMethod("parse",byte[].class);parse.setAccessible(true);
        try(var in=getClass().getResourceAsStream("/assets/doggiesnewvehicle/meshes/blizzard_missile.etm")) {
            Mesh.data=(DnvTurretMesh.MeshData)parse.invoke(DnvTurretMesh.BLIZZARD_MISSILE,in.readAllBytes());
        }
        try(var in=getClass().getResourceAsStream("/assets/doggiesnewvehicle/textures/entity/blizzard_missile.png")) {
            var image=javax.imageio.ImageIO.read(in);var stream=Mesh.data.part("Missile").main();
            for(int i=0;i<stream.length;i+=24) {
                int x=(int)((stream[i+6]+stream[i+14]+stream[i+22])/3*image.getWidth());
                int y=(int)((stream[i+7]+stream[i+15]+stream[i+23])/3*image.getHeight());
                assertEquals(255,image.getRGB(x,y)>>>24,"Authored skin must be opaque at each face");
            }
        }
        String root="com/liucodex/doggiesnewvehicle/client/",source=root+"BlizzardRenderer$MissileRenderer",target=root+"MissileRenderProbe",helper=root+"DnvTurretMeshRenderSupport";
        Map<String,String> names=new HashMap<>(Map.of(source,target,helper,helper+"Probe"));
        names.put(root+"BlizzardRenderer",Type.getInternalName(Resources.class));
        names.put(root+"BlizzardFxRenderer",Type.getInternalName(Effects.class));
        names.put(root+"mesh/DnvTurretMesh",Type.getInternalName(Mesh.class));
        names.put("net/minecraft/client/Minecraft",Type.getInternalName(Client.class));
        names.put("net/minecraft/client/renderer/entity/EntityRenderer",Type.getInternalName(Base.class));
        names.put("net/minecraft/client/renderer/entity/EntityRendererProvider$Context",Type.getInternalName(Context.class));
        names.put("net/minecraft/client/renderer/MultiBufferSource",Type.getInternalName(Buffers.class));
        names.put("net/minecraft/client/renderer/RenderType",Type.getInternalName(Layer.class));
        names.put("net/minecraft/world/entity/Entity",Type.getInternalName(Entity.class));
        names.put("com/liucodex/doggiesnewvehicle/armored/DnvBlizzardMissile",Type.getInternalName(Missile.class));
        Map<String,byte[]> definitions=new HashMap<>();
        for(String name:List.of(source,helper)) {
            var node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+name+".class")){new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
            node.access=Opcodes.ACC_PUBLIC;node.nestHostClass=null;node.innerClasses.clear();
            node.methods.removeIf(m->(m.access&Opcodes.ACC_BRIDGE)!=0||name.equals(helper)&&!Set.of("renderStream","renderQuads","renderTintedQuads","emitVertex").contains(m.name));
            node.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
            var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String n){return names.getOrDefault(n,n);}}));
            definitions.put(names.get(name),writer.toByteArray());
        }
        var renderer=new ExactQueuedShotFixture.Loader(definitions).loadClass(target.replace('/','.'));
        Object instance=renderer.getConstructor(Context.class).newInstance(new Context());
        var draw=renderer.getMethod("render",Missile.class,float.class,float.class,PoseStack.class,Buffers.class,int.class);
        var counts=new EnumMap<Layer,Integer>(Layer.class);Layer[] active={null};
        Buffers buffers=layer->{active[0]=layer;return (VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{VertexConsumer.class},(self,method,args)->{
            assertEquals(layer,active[0],"Finish one render batch before changing its material");
            if(Set.of("vertex","addVertex").contains(method.getName()))counts.merge(layer,1,Integer::sum);
            if(Set.of("color","setColor").contains(method.getName())&&args.length==4)assertEquals(255,args[3]);
            return method.getReturnType()==VertexConsumer.class?self:null;
        });};
        var missile=new Missile();Client.INSTANCE.camera=new Entity();
        draw.invoke(instance,missile,0F,.5F,new PoseStack(),buffers,15728880);
        assertEquals(216,counts.get(Layer.ARMOR),"104 authored triangles merge into 54 compatible quads");
        assertTrue(counts.get(Layer.ARMOR)>0);
        counts.clear();Client.INSTANCE.camera=missile;
        draw.invoke(instance,missile,0F,.5F,new PoseStack(),buffers,15728880);assertTrue(counts.isEmpty());
        Client.INSTANCE.camera=new Entity();
        draw.invoke(instance,missile,0F,.5F,new PoseStack(),buffers,15728880);assertTrue(counts.get(Layer.ARMOR)>0);
    }
}
