package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Actual Sponge-transformed upstream projection and the production center helper, with moving entity storage. */
public class DnvHullTargetProjectionTest {
    @TempDir Path temp;
    @Test void hullCenterFollowsThePosedBoundsAtEveryPartialTickWhileOrdinaryEntitiesKeepNativeBehavior()throws Exception {
        var transformer=new DnvMuzzleMixinApplicationTest();transformer.temp=temp;
        byte[][] bytecode;
        try(var loader=transformer.loader()){bytecode=DnvMuzzleMixinApplicationTest.transform(loader,"tools/VectorTool","HullTargetProjectionMixin","CLIENT",false);}
        var old=new Loader(bytecode[0]);var fixed=new Loader(bytecode[1]);
        Hull hull=new Hull();Body ordinary=new Body();
        for(float partial:new float[]{0,.25F,.5F,1}) {
            Vec3 projected=fixed.center(hull,partial);
            Vec3 translation=new Vec3((hull.xo-hull.getX())*(1-partial),(hull.yo-hull.getY())*(1-partial),(hull.zo-hull.getZ())*(1-partial));
            assertEquals(hull.box.getCenter().add(translation),projected);
            assertTrue(projected.y-old.center(hull,partial).y>10,"The old anchor-height center missed the authored hull");
            assertEquals(old.center(ordinary,partial),fixed.center(ordinary,partial));
        }
        hull.box=new AABB(10,110,30,100,145,65);
        assertEquals(hull.box.getCenter(),fixed.center(hull,1),"No cached dimensions after pose changes");
    }
    public static class Body {
        public double xo=1,yo=70,zo=4;public AABB box=new AABB(0,88,-30,90,108,30);
        public double getX(){return 5;}public double getY(){return 72;}public double getZ(){return 8;}
        public float getBbHeight(){return 6.706F;}public AABB getBoundingBox(){return box;}
    }
    public static class Hull extends Body implements DnvHullTarget { }
    private static class Loader extends ClassLoader {
        static final String TOOL="com/atsuishio/superbwarfare/tools/VectorTool",HELPER="com/liucodex/doggiesnewvehicle/compat/DnvTargetPoint";
        static final String PREFIX="com/liucodex/doggiesnewvehicle/compat/pointprobe/";
        final Map<String,String> types=Map.of(TOOL,PREFIX+"Projection",HELPER,PREFIX+"Center","net/minecraft/world/entity/Entity",Type.getInternalName(Body.class));
        final Map<String,String> methods=new HashMap<>(),fields=new HashMap<>();final byte[] tool;
        Loader(byte[] tool)throws Exception {
            this.tool=tool;
            for(String line:Files.readAllLines(Path.of("build/createSrgToMcp/output.srg"))) {
                String[] p=line.split(" ");if(p[0].equals("MD:"))methods.put(shortName(p[1]),shortName(p[3]));
                if(p[0].equals("FD:"))fields.put(shortName(p[1]),shortName(p[2]));
            }
        }
        static String shortName(String value){return value.substring(value.lastIndexOf('/')+1);}
        Vec3 center(Body body,float partial)throws Exception{return (Vec3)loadClass((PREFIX+"Projection").replace('/','.')).getMethod("lerpGetEntityBoundingBoxCenter",Body.class,float.class).invoke(null,body,partial);}
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            boolean isTool=name.equals((PREFIX+"Projection").replace('/','.'));
            if(!isTool&&!name.equals((PREFIX+"Center").replace('/','.')))throw new ClassNotFoundException(name);
            try {
                ClassNode node=new ClassNode();
                if(isTool)new ClassReader(tool).accept(node,0);
                else try(var in=getParent().getResourceAsStream(HELPER+".class")){new ClassReader(in).accept(node,0);}
                node.methods.removeIf(m->isTool?!m.name.equals("lerpGetEntityBoundingBoxCenter")&&!m.name.contains("dnv$"):!m.name.equals("center"));
                node.fields.clear();node.innerClasses.clear();node.visibleAnnotations=null;
                ClassWriter output=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(new ClassRemapper(output,new Remapper(){
                    @Override public String map(String value){return types.getOrDefault(value,value);}
                    @Override public String mapMethodName(String o,String value,String d){return methods.getOrDefault(value,value);}
                    @Override public String mapFieldName(String o,String value,String d){return fields.getOrDefault(value,value);}
                }));
                byte[] bytes=output.toByteArray();return defineClass(name,bytes,0,bytes.length);
            }catch(Exception e){throw new ClassNotFoundException(name,e);}
        }
    }
}
