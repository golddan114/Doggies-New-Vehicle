package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.client.weapons.Dsx1Mesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Executes real leaf submission and authored mesh vertices, with GPU buffers as capture ports. */
class Dsx1LampRenderExecutionTest {
    @Test void lampsSubmitAllTwentySixOriginalFacesToTheNativeEyesLayerAndRestoreTheBaseBuffer() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"software/bernie/geckolib/cache/object/GeoBone",Bone.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/client/weapons/Dsx1MeshCache",Cache.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/renderer/MultiBufferSource",Buffers.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/renderer/RenderType",Layer.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/resources/ResourceLocation",Location.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/client/weapons/Dsx1Renderer",
            Set.of("renderCubesOfBone","emit"),names,Host.class,false);
        var host=(Host)type.getConstructor().newInstance();
        var requests=new ArrayList<Layer>();var vertices=new HashMap<Layer,Integer>();var lights=new HashMap<Layer,List<Integer>>();
        host.currentBuffer=layer->{requests.add(layer);return consumer(layer,vertices,lights);};
        var method=Arrays.stream(type.getMethods()).filter(m->m.getName().equals("renderCubesOfBone")).findFirst().orElseThrow();
        draw(method,host,new Bone("dsx_lamps",false),consumer(Layer.BASE,vertices,lights),0);
        assertEquals(List.of(Layer.EYES,Layer.BASE),requests);
        assertEquals(26*4,vertices.get(Layer.BASE));assertEquals(26*4,vertices.get(Layer.EYES));
        assertTrue(lights.values().stream().flatMap(List::stream).allMatch(v->v==15728880));
        requests.clear();vertices.clear();lights.clear();
        draw(method,host,new Bone("dsx_body",false),consumer(Layer.BASE,vertices,lights),48);
        assertTrue(requests.isEmpty());assertTrue(vertices.get(Layer.BASE)>0);assertFalse(vertices.containsKey(Layer.EYES));
        assertTrue(lights.get(Layer.BASE).stream().allMatch(v->v==48));
        vertices.clear();draw(method,host,new Bone("dsx_lamps",true),consumer(Layer.BASE,vertices,lights),0);
        assertTrue(vertices.isEmpty());
    }
    private static void draw(java.lang.reflect.Method method,Host host,Bone bone,VertexConsumer consumer,int light)throws Exception{
        // GeckoLib 1.20.1 takes four floats; 1.21.1 takes one packed ARGB color.
        if(method.getParameterCount()==9)method.invoke(host,new PoseStack(),bone,consumer,light,0,.1F,.2F,.3F,1F);
        else {assertEquals(6,method.getParameterCount());method.invoke(host,new PoseStack(),bone,consumer,light,0,0xFF19334D);}
    }
    private VertexConsumer consumer(Layer layer,Map<Layer,Integer> vertices,Map<Layer,List<Integer>> lights){
        return (VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{VertexConsumer.class},(self,method,args)->{
            String name=method.getName();
            if(Set.of("vertex","addVertex").contains(name)&&args.length==4)vertices.merge(layer,1,Integer::sum);
            if(Set.of("uv2","setLight").contains(name)&&args.length==1)lights.computeIfAbsent(layer,k->new ArrayList<>()).add((Integer)args[0]);
            return method.getReturnType()==VertexConsumer.class?self:null;
        });
    }
    public record Bone(String name,boolean hidden){public String getName(){return name;}public boolean isHidden(){return hidden;}}
    public static class Location { }
    public static class Layer {public static final Layer BASE=new Layer(),EYES=new Layer();public static Layer eyes(Location texture){assertSame(Host.EMISSION,texture);return EYES;}}
    public interface Buffers {VertexConsumer getBuffer(Layer layer);}
    public static class Host {
        public static final Location EMISSION=new Location();public Buffers currentBuffer;public Layer renderType=Layer.BASE;
        public boolean activeFirstPerson(){return false;}public void alignPupil(PoseStack stack){}public void renderReticle(PoseStack stack,int overlay,float alpha){}
    }
    public static class Cache {
        public static Dsx1Mesh get(){try(var input=Files.newInputStream(Path.of("src/main/resources/assets/doggiesnewvehicle/models/item/dsx1.dsm"))){return Dsx1Mesh.read(input);}catch(Exception e){throw new IllegalStateException(e);}}
    }
}
