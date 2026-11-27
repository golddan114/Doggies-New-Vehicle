package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes compiled tracking/tick/reload logic; renderer and sound backend are absent. */
public class BlizzardEffectsLifecycleExecutionTest {
    @Test void offscreenReturnReloadAndPassengerHaveOneSoundOwner() throws Exception {
        var f=new Fixture();var tank=new Tank(1,10);f.load(tank);
        f.tick();var effect=f.effects().get(1);assertNotNull(effect);assertEquals(1,effect.sounds);
        tank.distance=100;f.tick();assertSame(effect,f.effects().get(1));assertEquals(1,effect.stops);
        tank.distance=10;f.tick();assertEquals(2,effect.sounds);
        f.type.getMethod("reload").invoke(null);assertSame(effect,f.effects().get(1));assertEquals(2,effect.stops);
        MC.INSTANCE.player.vehicle=tank;f.tick();assertEquals(3,effect.sounds);assertEquals(1,f.effects().size());
        tank.removed=true;MC.INSTANCE.player.vehicle=null;f.tick();assertTrue(f.effects().isEmpty());
        MC.INSTANCE.level=null;f.tick();assertTrue(f.effects().isEmpty());
    }
    @Test void boundedDiscoveryFindsUnrenderedVehiclesAfterTheFirstBatchAndReusesIdsSafely() throws Exception {
        var f=new Fixture();for(int i=0;i<80;i++)f.load(new Tank(i,100));
        var tank=new Tank(99,1000);f.load(tank);tank.distance=5;
        f.tick();f.tick();assertFalse(f.effects().containsKey(99));f.tick();assertTrue(f.effects().containsKey(99));
        var previous=f.effects().get(99);var replacement=new Tank(99,5);f.type.getDeclaredMethod("track",Tank.class).invoke(null,replacement);
        assertEquals(1,previous.stops);assertSame(replacement,f.effects().get(99).tank);
        for(int i=0;i<80;i++)f.type.getDeclaredMethod("track",Tank.class).invoke(null,new Tank(i,40+i));
        assertEquals(32,f.effects().size());assertTrue(f.effects().containsKey(99));
    }
    @Test void killAllAndWorldChangeClearEffectsWithoutRetainingNativeEntityIterators() throws Exception {
        var f=new Fixture();var tanks=new ArrayList<Tank>();
        for(int i=0;i<300;i++){var tank=new Tank(i,10);tanks.add(tank);f.load(tank);}
        f.tick();for(var tank:tanks)f.unload(tank);f.tick();assertTrue(f.effects().isEmpty());
        var replacement=new Tank(284,10);f.load(replacement);f.unload(tanks.get(284));f.tick();
        assertSame(replacement,f.effects().get(284).tank);
        var old=f.effects().get(284);MC.INSTANCE.level=new World();f.tick();assertTrue(f.effects().isEmpty());assertTrue(old.stops>0);
        MC.INSTANCE.player=null;f.load(new Tank(1,5));f.tick();
        MC.INSTANCE.player=new Player();f.tick();assertEquals(1,f.effects().get(1).sounds);
    }
    static class Fixture {
        final Class<?> type;
        Fixture() throws Exception {
            MC.INSTANCE.level=new World();MC.INSTANCE.player=new Player();
            String source="com/liucodex/doggiesnewvehicle/client/BlizzardClientEffects",target="com/liucodex/doggiesnewvehicle/compat/fxprobe/Lifecycle";
            var node=new ClassNode();try(var stream=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(stream.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
            node.access=Opcodes.ACC_PUBLIC;node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;
            node.methods.removeIf(m->!(List.of("<clinit>","track","loaded","unloaded","reset","reload","tick").contains(m.name)||m.name.startsWith("lambda$")));
            node.methods.forEach(m->m.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC);
            var names=new HashMap<String,String>();names.put(source,target);names.put(source+"$Effect",Type.getInternalName(Effect.class));
            names.put("com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity",Type.getInternalName(Tank.class));
            names.put("net/minecraft/client/Minecraft",Type.getInternalName(MC.class));names.put("net/minecraft/client/player/LocalPlayer",Type.getInternalName(Player.class));
            names.put("net/minecraft/client/multiplayer/ClientLevel",Type.getInternalName(World.class));names.put("net/minecraft/world/level/Level",Type.getInternalName(World.class));
            names.put("net/minecraft/world/entity/Entity",Type.getInternalName(Entity.class));
            names.put("net/minecraftforge/event/TickEvent$ClientTickEvent",Type.getInternalName(Tick.class));names.put("net/minecraftforge/event/TickEvent$Phase",Type.getInternalName(Phase.class));
            names.put("net/neoforged/neoforge/client/event/ClientTickEvent$Post",Type.getInternalName(Tick.class));
            names.put("net/minecraftforge/event/entity/EntityJoinLevelEvent",Type.getInternalName(Event.class));
            names.put("net/minecraftforge/event/entity/EntityLeaveLevelEvent",Type.getInternalName(Event.class));
            names.put("net/neoforged/neoforge/event/entity/EntityJoinLevelEvent",Type.getInternalName(Event.class));
            names.put("net/neoforged/neoforge/event/entity/EntityLeaveLevelEvent",Type.getInternalName(Event.class));
            String cycle="com/liucodex/doggiesnewvehicle/client/ClientEntityCycle",cycleTarget=target+"Cycle";
            names.put(cycle,cycleTarget);
            var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String name){return names.getOrDefault(name,name);}}));
            var cycleWriter=new ClassWriter(0);
            try(var stream=getClass().getResourceAsStream("/"+cycle+".class")){
                new ClassReader(stream.readAllBytes()).accept(new ClassRemapper(cycleWriter,new SimpleRemapper(cycle,cycleTarget)),0);
            }
            type=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray(),cycleTarget,cycleWriter.toByteArray())).loadClass(target.replace('/','.'));
        }
        void load(Tank tank) throws Exception {type.getMethod("loaded",Event.class).invoke(null,new Event(tank));}
        void unload(Tank tank) throws Exception {type.getMethod("unloaded",Event.class).invoke(null,new Event(tank));}
        void tick() throws Exception {type.getMethod("tick",Tick.class).invoke(null,new Tick());}
        @SuppressWarnings("unchecked") Map<Integer,Effect> effects() throws Exception {var field=type.getDeclaredField("ACTIVE");field.setAccessible(true);return (Map<Integer,Effect>)field.get(null);}
    }
    public static class MC {public static final MC INSTANCE=new MC();public World level;public Player player;public static MC getInstance(){return INSTANCE;}public boolean isPaused(){return false;}}
    public static class Entity { }
    public static class Player extends Entity {public Entity vehicle;public Entity getVehicle(){return vehicle;}}
    public static class World {public boolean isClientSide=true;public Iterable<Entity> entitiesForRendering(){throw new AssertionError("Mutable world iteration must never escape a tick");}}
    public record Event(Entity entity){public World getLevel(){return MC.INSTANCE.level;}public Entity getEntity(){return entity;}}
    public static class Tank extends Entity {public final int id;public double distance;public boolean removed;public Tank(int id,double distance){this.id=id;this.distance=distance;}
        public int getId(){return id;}public double distanceToSqr(Entity player){return distance*distance;}public boolean isRemoved(){return removed;}
        public World level(){return MC.INSTANCE.level;}public boolean presentationPowered(){return true;}public boolean hovering(){return true;}}
    public static class Effect {public final Tank tank;public int sounds,stops;public Effect(Tank tank){this.tank=tank;}public void sounds(MC mc){sounds++;}public void stop(){stops++;}public int particles(int budget){return 0;}}
    public enum Phase {END}
    public static class Tick {public Phase phase=Phase.END;}
}
