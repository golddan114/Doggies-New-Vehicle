package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.boss.MothershipEntityExecutionFixture.Actor;
import com.liucodex.doggiesnewvehicle.boss.MothershipEntityExecutionFixture.World;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

/** Real lease/event method bodies with entity/level ports; no Minecraft bootstrap. */
class MothershipGlowTest {
    public enum Phase {START,END}
    public static class Tick {public Phase phase=Phase.END;public World level;public Tick(World world){level=world;}}
    public static class Join {
        final Actor entity;public Join(Actor entity){this.entity=entity;}
        public Actor getEntity(){return entity;}public World getLevel(){return entity.world;}
    }
    public static class Leave extends Join {public Leave(Actor entity){super(entity);}}
    public static class Unload {final World world;public Unload(World world){this.world=world;}public World getLevel(){return world;}}
    private static Class<?> implementation;
    private static Class<?> actual() throws Exception {
        if(implementation!=null)return implementation;
        String original="com/liucodex/doggiesnewvehicle/boss/MothershipGlow",renamed=original+"Executed";
        Map<String,String> mapping=new HashMap<>();mapping.put(original,renamed);mapping.put(original+"$Lease",renamed+"$Lease");
        mapping.put("net/minecraft/world/entity/Entity",Type.getInternalName(Actor.class));
        for(String type:new String[]{"net/minecraft/server/level/ServerLevel","net/minecraft/world/level/Level","net/minecraft/world/level/LevelAccessor"})mapping.put(type,Type.getInternalName(World.class));
        mapping.put("net/minecraftforge/event/entity/EntityJoinLevelEvent",Type.getInternalName(Join.class));
        mapping.put("net/minecraftforge/event/entity/EntityLeaveLevelEvent",Type.getInternalName(Leave.class));
        mapping.put("net/minecraftforge/event/level/LevelEvent$Unload",Type.getInternalName(Unload.class));
        mapping.put("net/minecraftforge/event/TickEvent$LevelTickEvent",Type.getInternalName(Tick.class));
        mapping.put("net/minecraftforge/event/TickEvent$Phase",Type.getInternalName(Phase.class));
        Map<String,byte[]> definitions=new HashMap<>();
        for(String suffix:new String[]{"","$Lease"})try(var input=MothershipGlowTest.class.getClassLoader().getResourceAsStream(original+suffix+".class")) {
            assertNotNull(input);ClassWriter out=new ClassWriter(0);new ClassReader(input).accept(new ClassRemapper(out,new SimpleRemapper(mapping)),0);
            definitions.put((renamed+suffix).replace('/','.'),out.toByteArray());
        }
        implementation=new ClassLoader(MothershipGlowTest.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes=definitions.get(name);if(bytes==null)return super.findClass(name);return defineClass(name,bytes,0,bytes.length);
            }
        }.loadClass(renamed.replace('/','.'));
        return implementation;
    }
    private static Actor actor(World world){Actor actor=new Actor();actor.world=world;world.actors.put(actor.uuid,actor);return actor;}
    private static void acquire(Actor target,UUID ship,long tick) throws Exception {actual().getMethod("acquire",Actor.class,UUID.class,long.class).invoke(null,target,ship,tick);}
    private static void release(Actor target,UUID ship) throws Exception {actual().getMethod("release",World.class,UUID.class,UUID.class).invoke(null,target.world,target.uuid,ship);}
    @Test void oneShipsCancellationDoesNotClearAnotherShipsLease() throws Exception {
        Actor target=actor(new World());UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        acquire(target,first,0);acquire(target,second,0);assertTrue(target.glowing);
        release(target,first);assertTrue(target.glowing);release(target,second);assertFalse(target.glowing);assertTrue(target.persistent.isEmpty());
    }
    @Test void existingGlowTagsAndPotionGlowSurviveTheWeaponLease() throws Exception {
        Actor original=actor(new World());original.glowing=true;UUID owner=UUID.randomUUID();
        acquire(original,owner,0);release(original,owner);assertTrue(original.glowing);
        Actor potion=actor(new World());potion.potionGlow=true;acquire(potion,owner,0);release(potion,owner);
        assertFalse(potion.glowing);assertTrue(potion.potionGlow);
    }
    @Test void savedOwnedGlowClearsOnJoinAndLevelIsolationSurvivesSameTargetUuid() throws Exception {
        UUID owner=UUID.randomUUID();Actor first=actor(new World()),second=actor(new World());
        second.world.actors.remove(second.uuid);second.uuid=first.uuid;second.world.actors.put(second.uuid,second);
        acquire(first,owner,0);acquire(second,owner,0);release(first,owner);assertFalse(first.glowing);assertTrue(second.glowing);
        actual().getMethod("joined",Join.class).invoke(null,new Join(second));assertFalse(second.glowing);assertTrue(second.persistent.isEmpty());
        actual().getMethod("unloaded",Unload.class).invoke(null,new Unload(second.world));
    }
    @Test void expiryDeathEntityLeaveAndWorldUnloadCannotLeaveOwnedGlowBehind() throws Exception {
        for(String route:new String[]{"expiry","death","leave","unload"}) {
            Actor target=actor(new World());acquire(target,UUID.randomUUID(),0);
            switch(route) {
                case "expiry" -> {target.world.now=101;actual().getMethod("tick",Tick.class).invoke(null,new Tick(target.world));}
                case "death" -> {target.valid=false;actual().getMethod("tick",Tick.class).invoke(null,new Tick(target.world));}
                case "leave" -> actual().getMethod("left",Leave.class).invoke(null,new Leave(target));
                case "unload" -> actual().getMethod("unloaded",Unload.class).invoke(null,new Unload(target.world));
            }
            assertFalse(target.glowing,route);assertTrue(target.persistent.isEmpty(),route);
        }
    }
}
