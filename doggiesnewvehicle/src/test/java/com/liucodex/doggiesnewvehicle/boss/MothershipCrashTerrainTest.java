package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Production loaded-world crash queries with exact native OBBs; only terrain storage is an explicit port. */
public class MothershipCrashTerrainTest {
    @Test void tiltedHullTouchesRealGroundAtThePlannedHeightAndUnloadedVoidNeverCountsAsImpact()throws Exception {
        Loader loader=new Loader();Ship ship=new Ship();ship.world.blocks.add(new AABB(-1000,63,-1000,1000,64,1000));
        for(float yaw:new float[]{0,47,90,165}) {
            ship.yaw=yaw;
            double landing=(double)loader.call("wreckLandingY",new Class<?>[]{Body.class},ship);
            assertTrue(Double.isFinite(landing));
            assertEquals(1,pose(loader,ship,new Vec3(0,landing+.05,0),12,8));
            assertEquals(0,pose(loader,ship,new Vec3(0,landing,0),12,8));
            assertEquals(-1,pose(loader,ship,new Vec3(0,-100,0),12,8));
        }
        ship.world.loaded=false;ship.world.queries=0;
        assertEquals(-1,pose(loader,ship,new Vec3(0,64,0),0,0));assertEquals(0,ship.world.queries);
        assertTrue(Double.isNaN((double)loader.call("wreckLandingY",new Class<?>[]{Body.class},ship)));
    }
    @Test void solidTerrainStopsTheActualHullButBlocksInWingGapsDoNotTriggerAnAirborneExplosion()throws Exception {
        Loader loader=new Loader();Ship ship=new Ship();Vec3 bottom=new Vec3(0,90,0);
        var boxes=NativeHullFixture.motherPose(bottom,0,null);var center=boxes.get(0).center();
        ship.world.blocks.add(new AABB(center.x-.1,center.y-.05,center.z-.1,center.x+.1,center.y+.05,center.z+.1));
        assertEquals(0,pose(loader,ship,bottom,0,0));
        ship.world.blocks.clear();
        var p=MothershipRig.get().modelToWorld(new MothershipRig.V(15,0,30),new MothershipRig.V(0,90,0),0);
        ship.world.blocks.add(new AABB(p.x()-.1,p.y()-.1,p.z()-.1,p.x()+.1,p.y()+.1,p.z()+.1));
        assertEquals(1,pose(loader,ship,bottom,0,0));
        assertTrue(ship.world.queries<32,"Bounded region queries; no world scan");
    }
    @Test void sweptCandidatesMatchEveryNativePoseAndAvoidRepeatedTerrainQueries() throws Exception {
        Loader loader=new Loader();Ship ship=new Ship();
        Vec3 from=ship.position(),to=from.add(0,-2,0);
        var boxes=NativeHullFixture.motherPose(from,0,null);
        var random=new Random(7159);
        for(var box:boxes) {
            var p=box.center();
            for(int i=0;i<20;i++) {
                double x=p.x+random.nextDouble()*10-5,y=p.y+random.nextDouble()*7-4,z=p.z+random.nextDouble()*10-5;
                ship.world.blocks.add(new AABB(x,y,z,x+.5,y+.05,z+.5));
            }
        }
        @SuppressWarnings("unchecked") var candidates=(List<AABB>)loader.call("wreckCandidates",
                new Class<?>[]{Body.class,Vec3.class,double.class,double.class},ship,to,.12,.08);
        assertNotNull(candidates);int collectionQueries=ship.world.queries;
        for(int i=0;i<=64;i++) {
            double t=i/64.0;Vec3 position=from.lerp(to,t);
            int expected=pose(loader,ship,position,.12*t,.08*t);
            int before=ship.world.queries;
            int actual=(int)loader.call("wreckPose",new Class<?>[]{Body.class,Vec3.class,double.class,double.class,List.class},
                    ship,position,.12*t,.08*t,candidates);
            assertEquals(expected,actual,"changed terrain contact at fraction "+t);
            assertEquals(before,ship.world.queries,"narrow phase repeated a world scan");
        }
        assertTrue(collectionQueries>0&&collectionQueries<ship.world.queries);
        ship.world.loaded=false;
        assertNull(loader.call("wreckCandidates",new Class<?>[]{Body.class,Vec3.class,double.class,double.class},ship,to,.12,.08));
    }
    private static int pose(Loader loader,Ship ship,Vec3 bottom,double pitch,double roll)throws Exception {
        return (int)loader.call("wreckPose",new Class<?>[]{Body.class,Vec3.class,double.class,double.class},ship,bottom,pitch,roll);
    }
    public static class Body {
        public final World world=new World();public float yaw;public World level(){return world;}
        public float getYRot(){return yaw;}public float getXRot(){return 0;}public float getRoll(){return 0;}public Vec3 position(){return new Vec3(0,99,0);}
    }
    public static class Ship extends Body {
        public List<?> collisionPose(Vec3 bottom,double yaw,double pitch,double roll) {
            try{return (List<?>)NativeHullFixture.type("MothershipHull").getMethod("pose",Vec3.class,double.class,double.class,double.class,MothershipRig.Angles[].class)
                    .invoke(null,bottom,yaw,pitch,roll,null);}catch(Exception e){throw new AssertionError(e);}
        }
    }
    public enum Height {MOTION_BLOCKING_NO_LEAVES}
    public record Shape(AABB box) {public List<AABB> toAabbs(){return List.of(box);}}
    public static class Border {public boolean isWithinBounds(AABB box){return true;}}
    public static class World {
        public final List<AABB> blocks=new ArrayList<>();public boolean loaded=true;public int queries;
        public boolean hasChunk(int x,int z){return loaded;}public int getMinBuildHeight(){return -64;}
        public Border getWorldBorder(){return new Border();}public int getHeight(Height type,int x,int z){return 64;}
        public Iterable<Shape> getBlockCollisions(Body ship,AABB box){queries++;return blocks.stream().filter(box::intersects).map(Shape::new).toList();}
    }
    private static class Loader extends ClassLoader {
        static final String ORIGINAL="com/liucodex/doggiesnewvehicle/boss/MothershipWorld",GENERATED="com/liucodex/doggiesnewvehicle/boss/terrainprobe/Queries";
        final Map<String,String> types=new HashMap<>();
        Loader()throws Exception {
            super(NativeHullFixture.loader());types.put(ORIGINAL,GENERATED);
            types.put("com/atsuishio/superbwarfare/tools/OBB",Type.getInternalName(NativeHullFixture.type("OBB")));
            types.put("com/atsuishio/superbwarfare/tools/OBB$Companion",Type.getInternalName(NativeHullFixture.type("OBB$Companion")));
            types.put("com/liucodex/doggiesnewvehicle/boss/MothershipHull",Type.getInternalName(NativeHullFixture.type("MothershipHull")));
            for(var entry:Map.of("net/minecraft/world/entity/Entity",Body.class,"net/minecraft/server/level/ServerLevel",World.class,
                    "net/minecraft/world/level/Level",World.class,"net/minecraft/world/level/border/WorldBorder",Border.class,
                    "net/minecraft/world/phys/shapes/VoxelShape",Shape.class,"net/minecraft/world/level/levelgen/Heightmap$Types",Height.class,
                    "com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity",Ship.class).entrySet())types.put(entry.getKey(),Type.getInternalName(entry.getValue()));
        }
        Object call(String method,Class<?>[] parameters,Object...args)throws Exception{return loadClass(GENERATED.replace('/','.')).getMethod(method,parameters).invoke(null,args);}
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            if(!name.equals(GENERATED.replace('/','.')))throw new ClassNotFoundException(name);
            try(var in=getParent().getResourceAsStream(ORIGINAL+".class")) {
                ClassNode node=new ClassNode();new ClassReader(in).accept(node,0);
                node.methods.removeIf(m->!Set.of("wreckCandidates","wreckPose","wreckLandingY","groundBelow","envelope","loaded").contains(m.name));
                node.fields.clear();node.innerClasses.clear();node.nestMembers=null;
                ClassWriter output=new ClassWriter(0);node.accept(new ClassRemapper(output,new SimpleRemapper(types)));
                byte[] bytes=output.toByteArray();return defineClass(name,bytes,0,bytes.length);
            }catch(Exception e){throw new ClassNotFoundException(name,e);}
        }
    }
}
