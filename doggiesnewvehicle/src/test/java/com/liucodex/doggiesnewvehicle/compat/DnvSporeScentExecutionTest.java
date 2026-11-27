package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.level.Explosion.BlockInteraction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Executes exact identity, sphere and callback admission; no live Spore world is claimed. */
class DnvSporeScentExecutionTest {
    @Test void clearsOnlyExactScentInsideSixBlockSphereIncludingBoundary()throws Exception{
        var f=fixture();var world=new World();
        var center=f.scent(world,Vec3.ZERO,"spore:scent");var edge=f.scent(world,new Vec3(6,0,0),"spore:scent");
        var outside=f.scent(world,new Vec3(6.01,0,0),"spore:scent");var diagonal=f.scent(world,new Vec3(5,0,5),"spore:scent");
        var wrongId=f.scent(world,Vec3.ZERO,"spore:other");var wrongClass=new Entity();wrongClass.world=world;world.entities.add(wrongClass);
        f.clear(world);
        assertEquals(1,center.discards);assertEquals(1,edge.discards);
        for(var e:List.of(outside,diagonal,wrongId,wrongClass))assertEquals(0,e.discards);
        assertEquals(1,world.callbacks);
    }
    @Test void canceledOrFilteredImpactCannotRemoveProtectedOrInjectedEntities()throws Exception{
        var f=fixture();var world=new World();var kept=f.scent(world,Vec3.ZERO,"spore:scent");
        world.cancel=true;f.clear(world);assertEquals(0,kept.discards);assertEquals(0,world.callbacks);
        world.cancel=false;world.protectedEntity=kept;
        var outside=f.scent(world,new Vec3(8,0,0),"spore:scent");world.inject=outside;f.clear(world);
        assertEquals(0,kept.discards);assertEquals(0,outside.discards);
    }
    @Test void callbackDuplicatesMovesAndCrossWorldChangesAreRechecked()throws Exception{
        var f=fixture();var world=new World();var once=f.scent(world,Vec3.ZERO,"spore:scent");
        var moved=f.scent(world,new Vec3(2,0,0),"spore:scent");var foreign=f.scent(world,new Vec3(3,0,0),"spore:scent");
        world.duplicate=once;world.after=()->{moved.point=new Vec3(7,0,0);foreign.world=new World();};f.clear(world);
        assertEquals(1,once.discards);assertEquals(0,moved.discards);assertEquals(0,foreign.discards);
    }
    private record Fixture(Class<?> type,Class<?> scentType){
        Entity scent(World world,Vec3 point,String id)throws Exception{var e=(Entity)scentType.getConstructor().newInstance();e.world=world;e.point=point;e.kind=new Kind(id);world.entities.add(e);return e;}
        void clear(World world)throws Exception{type.getMethod("clear",World.class,Entity.class,Vec3.class,int.class).invoke(null,world,new Entity(),Vec3.ZERO,6);}
    }
    private Fixture fixture()throws Exception{
        String source="com/liucodex/doggiesnewvehicle/artillery/compat/DnvSporeScent",target=source+"Probe",scent="com/Harbinger/Spore/Sentities/Utility/ScentEntity";
        Map<String,String> names=new HashMap<>();names.put(source,target);
        for(var e:Map.ofEntries(Map.entry("net/minecraft/server/level/ServerLevel",World.class),Map.entry("net/minecraft/world/level/Level",World.class),
                Map.entry("net/minecraft/world/entity/Entity",Entity.class),Map.entry("net/minecraft/world/entity/EntityType",Kind.class),
                Map.entry("net/minecraft/world/level/Explosion",Blast.class),Map.entry("net/minecraft/core/registries/BuiltInRegistries",Catalog.class),
                Map.entry("net/minecraft/core/DefaultedRegistry",Registry.class),Map.entry("net/minecraft/core/Registry",Registry.class),
                Map.entry("net/minecraft/resources/ResourceLocation",Id.class),Map.entry("net/minecraftforge/event/ForgeEventFactory",Hooks.class),
                Map.entry("net/neoforged/neoforge/event/EventHooks",Hooks.class)).entrySet())names.put(e.getKey(),Type.getInternalName(e.getValue()));
        var out=new ClassWriter(0);try(var in=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(in.readAllBytes()).accept(new ClassRemapper(out,new SimpleRemapper(names)),0);}
        var subclass=new ClassWriter(0);subclass.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,scent,null,Type.getInternalName(Entity.class),null);
        var constructor=subclass.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);constructor.visitCode();constructor.visitVarInsn(Opcodes.ALOAD,0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,Type.getInternalName(Entity.class),"<init>","()V",false);constructor.visitInsn(Opcodes.RETURN);constructor.visitMaxs(1,1);constructor.visitEnd();subclass.visitEnd();
        var loader=new ExactQueuedShotFixture.Loader(Map.of(target,out.toByteArray(),scent,subclass.toByteArray()));
        return new Fixture(loader.loadClass(target.replace('/','.')),loader.loadClass(scent.replace('/','.')));
    }
    public record Kind(String id) { }
    public record Id(String value){@Override public String toString(){return value;}}
    public interface Registry {default Id getKey(Object kind){return new Id(((Kind)kind).id());}}
    public static class Catalog {public static final Registry ENTITY_TYPE=new Registry(){};}
    public static class Entity {
        public World world;public Vec3 point=Vec3.ZERO;public Kind kind=new Kind("spore:scent");public int discards;
        public Entity(){}public World level(){return world;}public Vec3 position(){return point;}public Kind getType(){return kind;}
        public boolean isRemoved(){return discards>0;}public void discard(){discards++;}
    }
    public static class World {
        public List<Entity> entities=new ArrayList<>();public boolean cancel;public int callbacks;
        public Entity protectedEntity,inject,duplicate;public Runnable after=()->{};
        public List<Entity> getEntities(Entity excluded,AABB box,Predicate<Entity> filter){return entities.stream().filter(filter).toList();}
    }
    public static class Blast {public Blast(World world,Entity source,double x,double y,double z,float radius,boolean fire,BlockInteraction interaction){assertEquals(6,radius);assertFalse(fire);assertEquals(BlockInteraction.KEEP,interaction);}}
    public static class Hooks {
        public static boolean onExplosionStart(World world,Blast blast){return world.cancel;}
        public static void onExplosionDetonate(World world,Blast blast,List<Entity> targets,double radius){
            assertEquals(6,radius);world.callbacks++;targets.remove(world.protectedEntity);
            if(world.inject!=null)targets.add(world.inject);if(world.duplicate!=null)targets.add(world.duplicate);world.after.run();
        }
    }
}
