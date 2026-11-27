package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes compiled discovery, bounded audio lifetime and loop positioning with explicit client ports. */
class BlizzardMissileSoundExecutionTest {
    public enum Phase { START, END }
    public static class Tick { public Phase phase=Phase.END; }
    public record Join(World level, Missile entity) { public World getLevel(){return level;} public Missile getEntity(){return entity;} }
    public static class World { public boolean isClientSide=true; }
    public static class Missile {
        public final int id; public World world; public boolean removed; public Vec3 position=Vec3.ZERO;
        public Missile(int id,World world){this.id=id;this.world=world;}
        public int getId(){return id;}public World level(){return world;}public boolean isRemoved(){return removed;}
        public Vec3 position(){return position;}public double getX(){return position.x;}public double getY(){return position.y;}public double getZ(){return position.z;}
    }
    public static class Event { }
    public static class Ref { public Object get(){return new Event();} }
    public static class Sounds { public static final Ref ROCKET_FLY=new Ref(); }
    public static class Sound {
        public boolean looping,stopped;public int delay;public float volume,pitch;public double x,y,z;
        public Sound(Event event,SoundSource source,RandomSource random){assertEquals(SoundSource.BLOCKS,source);}
        public void stop(){stopped=true;}
    }
    public static class Manager {
        public final Set<Sound> live=new HashSet<>();public int plays,stops;
        public void play(Sound sound){plays++;assertFalse(sound.stopped);live.add(sound);}
        public void stop(Sound sound){stops++;live.remove(sound);}
    }
    public static class Camera { public Vec3 position=Vec3.ZERO;public Vec3 getPosition(){return position;} }
    public static class Renderer { public Camera camera=new Camera();public Camera getMainCamera(){return camera;} }
    public static class Client {
        public static Client current;public World level=new World();public Object player=new Object();public boolean paused;
        public Renderer gameRenderer=new Renderer();public Manager manager=new Manager();
        public static Client getInstance(){return current;}public boolean isPaused(){return paused;}public Manager getSoundManager(){return manager;}
    }
    @Test void tvCameraFindsFlightSoundOnceAndImpactReloadDisconnectAndDistanceStopIt() throws Exception {
        var type=load();Client.current=new Client();var mc=Client.current;
        var join=type.getMethod("joined",Join.class);var tick=type.getMethod("tick",Tick.class);var reset=type.getMethod("reset");
        var missile=new Missile(1,mc.level);missile.position=new Vec3(500,30,2);
        join.invoke(null,new Join(mc.level,missile));tick.invoke(null,new Tick());assertEquals(0,mc.manager.plays);
        mc.gameRenderer.camera.position=missile.position;tick.invoke(null,new Tick());tick.invoke(null,new Tick());
        assertEquals(1,mc.manager.plays);var sound=mc.manager.live.iterator().next();
        assertTrue(sound.looping);assertEquals(.85F,sound.volume);assertEquals(1F,sound.pitch);
        missile.position=new Vec3(501,31,3);sound.getClass().getMethod("tick").invoke(sound);
        assertEquals(501,sound.x);assertEquals(31,sound.y);assertEquals(3,sound.z);
        mc.paused=true;missile.removed=true;tick.invoke(null,new Tick());assertFalse(sound.stopped);
        mc.paused=false;tick.invoke(null,new Tick());assertTrue(sound.stopped);assertTrue(mc.manager.live.isEmpty());
        for(int reason=0;reason<3;reason++) {
            missile=new Missile(2+reason,mc.level);mc.gameRenderer.camera.position=Vec3.ZERO;
            join.invoke(null,new Join(mc.level,missile));tick.invoke(null,new Tick());assertEquals(1,mc.manager.live.size());
            if(reason==0)mc.gameRenderer.camera.position=new Vec3(49,0,0);
            if(reason==1)mc.level=new World();
            if(reason==2)mc.level=null;
            tick.invoke(null,new Tick());assertTrue(mc.manager.live.isEmpty());if(mc.level==null)mc.level=new World();
        }
        join.invoke(null,new Join(mc.level,new Missile(10,mc.level)));mc.gameRenderer.camera.position=Vec3.ZERO;
        tick.invoke(null,new Tick());reset.invoke(null);assertTrue(mc.manager.live.isEmpty());
    }
    @Test void spawnDiscoveryCapsLoopsAndSameIdReplacementStopsOldSound() throws Exception {
        var type=load();Client.current=new Client();var mc=Client.current;
        var join=type.getMethod("joined",Join.class);var tick=type.getMethod("tick",Tick.class);
        for(int i=0;i<25;i++)join.invoke(null,new Join(mc.level,new Missile(i,mc.level)));
        tick.invoke(null,new Tick());assertEquals(24,mc.manager.live.size());assertEquals(24,mc.manager.plays);
        join.invoke(null,new Join(mc.level,new Missile(3,mc.level)));assertEquals(23,mc.manager.live.size());
        tick.invoke(null,new Tick());assertEquals(24,mc.manager.live.size());assertEquals(25,mc.manager.plays);
        type.getMethod("reset").invoke(null);assertTrue(mc.manager.live.isEmpty());
    }
    private Class<?> load() throws Exception {
        String source="com/liucodex/doggiesnewvehicle/client/BlizzardMissileEffects",target=source+"Probe";
        Map<String,String> names=new HashMap<>();names.put(source,target);names.put(source+"$FlightSound",target+"$FlightSound");
        Map.ofEntries(Map.entry("net/minecraft/client/Minecraft",Client.class),Map.entry("net/minecraft/client/multiplayer/ClientLevel",World.class),
                Map.entry("net/minecraft/world/level/Level",World.class),Map.entry("net/minecraft/world/entity/Entity",Missile.class),
                Map.entry("net/minecraft/client/player/LocalPlayer",Object.class),Map.entry("net/minecraft/client/renderer/GameRenderer",Renderer.class),
                Map.entry("net/minecraft/client/Camera",Camera.class),Map.entry("net/minecraft/client/sounds/SoundManager",Manager.class),
                Map.entry("net/minecraft/client/resources/sounds/AbstractTickableSoundInstance",Sound.class),
                Map.entry("net/minecraft/client/resources/sounds/SoundInstance",Sound.class),Map.entry("net/minecraft/sounds/SoundEvent",Event.class),
                Map.entry("com/atsuishio/superbwarfare/init/ModSounds",Sounds.class),
                Map.entry("net/minecraftforge/registries/RegistryObject",Ref.class),Map.entry("net/neoforged/neoforge/registries/DeferredHolder",Ref.class),
                Map.entry("com/liucodex/doggiesnewvehicle/armored/DnvBlizzardMissile",Missile.class),
                Map.entry("net/minecraftforge/event/entity/EntityJoinLevelEvent",Join.class),Map.entry("net/neoforged/neoforge/event/entity/EntityJoinLevelEvent",Join.class),
                Map.entry("net/minecraftforge/event/TickEvent$ClientTickEvent",Tick.class),Map.entry("net/minecraftforge/event/TickEvent$Phase",Phase.class),
                Map.entry("com/liucodex/doggiesnewvehicle/neoforge/DnvNeoTicks$ClientTickEvent",Tick.class),Map.entry("com/liucodex/doggiesnewvehicle/neoforge/DnvNeoTicks$Phase",Phase.class))
                .forEach((n,c)->names.put(n,Type.getInternalName(c)));
        var definitions=new HashMap<String,byte[]>();
        for(String suffix:new String[]{"","$FlightSound"}) {
            var node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+suffix+".class")){new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
            node.access=Opcodes.ACC_PUBLIC;node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;
            node.fields.forEach(f->f.access=(f.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
            node.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
            var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String n){return names.getOrDefault(n,n);}}));
            definitions.put(target+suffix,writer.toByteArray());
        }
        return new ExactQueuedShotFixture.Loader(definitions).loadClass(target.replace('/','.'));
    }
}
