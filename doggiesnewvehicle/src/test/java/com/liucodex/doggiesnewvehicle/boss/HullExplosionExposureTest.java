package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Actual posed native OBBs and compiled exposure sampler; terrain visibility is the test boundary. */
class HullExplosionExposureTest {
    @Test void repeatedExplosionTargetPairsExposeLinearSamplerCostWithoutAClaimAboutServerTickTime() throws Exception {
        var loader=new Loader();
        var boxes=NativeHullFixture.motherPose(new Vec3(31,110,-43),47,null).stream().map(NativeHullFixture.Box::value).toList();
        for(int pairs:new int[]{1,10,50}) {
            int[] samples={0};
            for(int i=0;i<pairs;i++)assertEquals(1,loader.sample(boxes,point->{samples[0]++;return true;}));
            assertEquals(105*pairs,samples[0]);
        }
    }
    @Test void ninetyBlockHullNeeds105RaysWithFullPartialAndZeroExposure() throws Exception {
        var loader = new Loader();
        var boxes = NativeHullFixture.motherPose(new Vec3(31, 110, -43), 47, null).stream().map(NativeHullFixture.Box::value).toList();
        int[] rays = {0};
        assertEquals(1, loader.sample(boxes, point -> { rays[0]++; return true; }));
        assertEquals(105, rays[0]);
        assertEquals(0, loader.sample(boxes, point -> false));
        float partial = loader.sample(boxes, point -> point.x > 31);
        assertTrue(partial > 0 && partial < 1);
        var many = new ArrayList<>(); for (int i = 0; i < 100; i++) many.addAll(boxes);
        rays[0] = 0; assertEquals(1, loader.sample(many, point -> { rays[0]++; return true; }));
        assertEquals(224, rays[0], "Hard ray cap is independent of hull extent and OBB count");
        assertEquals(0, loader.sample(List.of(), point -> fail("Empty hull")));
    }

    @Test void chunkTraversalChecksDiagonalSliversAndNeverReadsOrLoadsAnUnavailableChunk() throws Exception {
        var loader = new Loader(); var world = new World();
        assertTrue(loader.loaded(world, new Vec3(-17, 80, -17), new Vec3(70, 81, 49)));
        assertTrue(world.visits.size() < 20);
        world.missing = "1:0";
        assertFalse(loader.loaded(world, new Vec3(15.9, 80, 15), new Vec3(17, 80, 17)));
        world.visits.clear();
        assertFalse(loader.loaded(world, new Vec3(Double.NaN, 0, 0), Vec3.ZERO));
        assertFalse(loader.loaded(world, Vec3.ZERO, new Vec3(1025, 0, 0)));
        assertTrue(world.visits.isEmpty());
    }

    public static class World {
        String missing; final Set<String> visits = new HashSet<>();
        public boolean hasChunk(int x, int z) { String key=x+":"+z; visits.add(key); return !key.equals(missing); }
    }
    private static class Loader extends ClassLoader {
        final Class<?> type;
        Loader() throws Exception {
            super(NativeHullFixture.loader());
            String old="com/liucodex/doggiesnewvehicle/compat/DnvExplosionExposure", name="com/liucodex/doggiesnewvehicle/boss/exposureprobe/Sampler";
            ClassNode node=new ClassNode();
            try(var input=getParent().getResourceAsStream(old+".class")){new ClassReader(input).accept(node,0);}
            node.methods.removeIf(m -> !Set.of("sample","loadedRay","finite").contains(m.name));
            node.innerClasses.clear(); node.nestMembers=null;
            for(var method:node.methods)method.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC;
            Map<String,String> names=Map.of(old,name,"net/minecraft/world/level/Level",Type.getInternalName(World.class),
                    "com/atsuishio/superbwarfare/tools/OBB",Type.getInternalName(NativeHullFixture.type("OBB")));
            ClassWriter out=new ClassWriter(0);node.accept(new ClassRemapper(out,new SimpleRemapper(names)));
            byte[] bytes=out.toByteArray();type=defineClass(name.replace('/','.'),bytes,0,bytes.length);
        }
        float sample(List<?> boxes,Predicate<Vec3> visible)throws Exception{return (float)type.getMethod("sample",List.class,Predicate.class).invoke(null,boxes,visible);}
        boolean loaded(World world,Vec3 from,Vec3 to)throws Exception{return (boolean)type.getMethod("loadedRay",World.class,Vec3.class,Vec3.class).invoke(null,world,from,to);}
    }
}
