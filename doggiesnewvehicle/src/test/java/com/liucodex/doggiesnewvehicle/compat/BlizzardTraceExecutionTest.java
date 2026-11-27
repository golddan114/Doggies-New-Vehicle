package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the compiled broad-phase and damage-box clipping; the world and native OBB are explicit ports. */
class BlizzardTraceExecutionTest {
    @Test void piercingIncludesCollinearBodiesOncePerEntityAndExcludesCollisionOnlyAndOffAxis() throws Exception {
        var world=new World();var first=new Entity(3);var second=new Entity(9);var beyond=new Entity(81);
        var hull=new Vehicle(20);hull.boxes.add(new Box(Part.BODY,19));hull.boxes.add(new Box(Part.BODY,21));
        hull.boxes.add(new Box(Part.COLLISION,2));
        var collisionOnly=new Vehicle(30);collisionOnly.boxes.add(new Box(Part.COLLISION,30));
        var offAxis=new Entity(40);offAxis.box=offAxis.box.move(3,0,0);
        var denied=new Entity(50);denied.allowed=false;
        world.entities.addAll(List.of(second,hull,first,hull,beyond,collisionOnly,offAxis,denied));
        var hits=hits(world,new Vec3(0,0,80));
        assertEquals(List.of(first,second,hull),hits.stream().map(this::entity).toList());
        assertEquals(new Vec3(0,0,18.5),position(hits.get(2)));
    }
    @Test void wallBoundAndOriginInsideAreRespectedAndNearestProjectileBehaviorStaysSingular() throws Exception {
        var world=new World();var near=new Entity(4);var behind=new Entity(14);var inside=new Entity(0);
        world.entities.addAll(List.of(behind,near,inside));
        var hits=hits(world,new Vec3(0,0,12));
        assertEquals(List.of(inside,near),hits.stream().map(this::entity).toList());assertEquals(Vec3.ZERO,position(hits.get(0)));
        Class<?> trace=load();Object nearest=trace.getMethod("nearest",World.class,Vec3.class,Vec3.class,Predicate.class)
                .invoke(null,world,Vec3.ZERO,new Vec3(0,0,80),(Predicate<Entity>)e->e.allowed);
        assertSame(inside,entity(nearest));
        world.entities.clear();assertTrue(hits(world,new Vec3(0,0,80)).isEmpty());
    }
    private List<?> hits(World world,Vec3 end)throws Exception {
        return (List<?>)load().getMethod("piercing",World.class,Vec3.class,Vec3.class,Predicate.class)
                .invoke(null,world,Vec3.ZERO,end,(Predicate<Entity>)e->e.allowed);
    }
    private Entity entity(Object hit){try{return (Entity)hit.getClass().getMethod("entity").invoke(hit);}catch(Exception e){throw new AssertionError(e);}}
    private Vec3 position(Object hit)throws Exception{return (Vec3)hit.getClass().getMethod("position").invoke(hit);}
    private Class<?> load()throws Exception {
        String source="com/liucodex/doggiesnewvehicle/armored/BlizzardTrace",target=source+"Execution";
        Map<String,String> names=new HashMap<>();names.put(source,target);names.put(source+"$Hit",target+"$Hit");
        for(var e:Map.ofEntries(Map.entry("net/minecraft/world/entity/Entity",Entity.class),
                Map.entry("net/minecraft/world/level/Level",World.class),
                Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Vehicle.class),
                Map.entry("com/atsuishio/superbwarfare/tools/OBB",Box.class),
                Map.entry("com/atsuishio/superbwarfare/tools/OBB$Part",Part.class)).entrySet())names.put(e.getKey(),Type.getInternalName(e.getValue()));
        Map<String,byte[]> classes=new HashMap<>();
        for(String type:List.of(source,source+"$Hit")){
            var node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+type+".class")){new ClassReader(in.readAllBytes()).accept(node,0);}
            node.access=(node.access&~Opcodes.ACC_PRIVATE)|Opcodes.ACC_PUBLIC;node.innerClasses.clear();
            node.methods.forEach(m->m.access=(m.access&~Opcodes.ACC_PRIVATE)|Opcodes.ACC_PUBLIC);
            var out=new ClassWriter(0);node.accept(new ClassRemapper(out,new SimpleRemapper(names)));classes.put(names.get(type),out.toByteArray());
        }
        return new ExactQueuedShotFixture.Loader(classes).loadClass(target.replace('/','.'));
    }
    public static class Entity {
        public AABB box;public boolean allowed=true;
        public Entity(double z){box=new AABB(-.5,-.5,z-.5,.5,.5,z+.5);}
        public AABB getBoundingBox(){return box;}public float getPickRadius(){return 0;}
    }
    public static class Vehicle extends Entity {public final List<Box> boxes=new ArrayList<>();public Vehicle(double z){super(z);}public List<Box> getOBBs(){return boxes;}}
    public enum Part {BODY,COLLISION}
    public static class Box {
        public final Part part;private final AABB bounds;
        public Box(Part part,double z){this.part=part;bounds=new AABB(-1,-1,z-.5,1,1,z+.5);}
        public boolean contains(Vec3 point){return bounds.contains(point);}
        public Optional<Vector3d> clip(Vector3d from,Vector3d to){return bounds.clip(new Vec3(from.x,from.y,from.z),new Vec3(to.x,to.y,to.z)).map(p->new Vector3d(p.x,p.y,p.z));}
    }
    public static class World {
        public final List<Entity> entities=new ArrayList<>();
        public List<Entity> getEntities(Entity excluded,AABB bounds,Predicate<Entity> filter){return entities.stream().filter(filter).filter(e->e.box.intersects(bounds)).toList();}
    }
}
