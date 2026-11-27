package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Executes the compiled shared manager and both loops with an audio backend port. */
public class MothershipLoopSoundExecutionTest {
    @Test void optionalIdleOnlyConsumerNeverCreatesDriveAudioEvenAtFullThrottleAndStopsOnUnload()throws Exception {
        Client client=Client.INSTANCE;client.level=new World();client.player=new Actor(client.level);client.sound=new Audio();
        Actor ship=new Actor(client.level);client.level.entities.add(ship);
        Class<?> type=new Loader().loadClass(Loader.GENERATED.replace('/','.'));
        Object manager=type.getConstructor(Class.class,int.class,Sound.class,Sound.class,Sound.class,Predicate.class,ToDoubleFunction.class,double.class,double.class)
                .newInstance(Actor.class,0,new Sound(),null,null,(Predicate<Actor>)a->!a.removed,(ToDoubleFunction<Actor>)a->a.throttle,96D,112D);
        var tick=type.getMethod("tick");tick.invoke(manager);assertEquals(1,client.sound.loops.size());
        ship.throttle=1;for(int i=0;i<120;i++){client.sound.tick();tick.invoke(manager);}
        assertEquals(1,client.sound.loops.size());assertTrue(client.sound.loops.get(0).volume>0);
        client.level=null;tick.invoke(manager);assertTrue(client.sound.loops.get(0).isStopped());
    }
    @Test void stationaryStartupCanBecomeAudibleThenFadeAndAllLoopsStopAcrossRemovalDimensionAndLogout()throws Exception {
        Client client=Client.INSTANCE;client.level=new World();client.player=new Actor(client.level);
        client.sound=new Audio();Actor ship=new Actor(client.level);client.level.entities.add(ship);
        Class<?> type=new Loader().loadClass(Loader.GENERATED.replace('/','.'));
        Object manager=type.getConstructor(Class.class,int.class,Sound.class,Sound.class,Sound.class,Predicate.class,ToDoubleFunction.class,double.class,double.class,boolean.class)
                .newInstance(Actor.class,0,new Sound(),new Sound(),null,(Predicate<Actor>)a->!a.removed,(ToDoubleFunction<Actor>)a->a.throttle,96D,112D,true);
        var tick=type.getMethod("tick");tick.invoke(manager);assertEquals(2,client.sound.loops.size());
        Loop idle=client.sound.loops.get(0),engine=client.sound.loops.get(1);
        assertTrue(idle.volume>0);assertEquals(0,engine.volume);assertTrue(engine.canStartSilent());
        ship.throttle=1;for(int i=0;i<30;i++)client.sound.tick();assertEquals(1,engine.volume,1e-5);
        assertEquals(1,idle.pitch);assertEquals(1,engine.pitch,"Accepted propulsion keeps its original pitch");
        ship.throttle=0;for(int i=0;i<30;i++)client.sound.tick();assertEquals(0,engine.volume);assertFalse(idle.isStopped());
        tick.invoke(manager);assertEquals(2,client.sound.loops.size(),"discovery must not duplicate loops");
        client.level=new World();client.player.world=client.level;client.sound.tick();assertTrue(idle.isStopped());assertTrue(engine.isStopped());
        tick.invoke(manager);ship.world=client.level;client.level.entities.add(ship);tick.invoke(manager);assertEquals(4,client.sound.loops.size());
        ship.removed=true;client.sound.tick();assertTrue(client.sound.loops.stream().allMatch(Loop::isStopped));
        ship.removed=false;tick.invoke(manager);assertEquals(6,client.sound.loops.size());
        client.level=null;tick.invoke(manager);assertTrue(client.sound.loops.stream().allMatch(Loop::isStopped));
    }
    public static class Sound { }
    public static class Actor {
        public World world;public boolean removed;public float throttle;public int tickCount;
        Actor(World world){this.world=world;}public World level(){return world;}public boolean isRemoved(){return removed;}
        public int getId(){return 42;}public double getX(){return 0;}public double getY(){return 84;}public double getZ(){return 0;}
        public double distanceToSqr(Actor other){return 0;}public AABB getBoundingBox(){return new AABB(0,80,0,1,85,1);}
    }
    public static class World {
        public final List<Actor> entities=new ArrayList<>();
        public <T> List<T> getEntitiesOfClass(Class<T> type,AABB area){return entities.stream().map(type::cast).toList();}
    }
    public static class Client {
        static final Client INSTANCE=new Client();public World level;public Actor player;public Audio sound;
        public static Client getInstance(){return INSTANCE;}public Audio getSoundManager(){return sound;}
    }
    public static class Audio {
        final List<Loop> loops=new ArrayList<>();
        public void play(Loop loop){if(loop.volume>0||loop.canStartSilent())loops.add(loop);}
        public void stop(Loop loop){loop.stop();}void tick(){loops.forEach(Loop::tick);}
    }
    public static class Loop {
        public boolean looping,stopped;public int delay;public float volume,pitch;public double x,y,z;public Attenuation attenuation;
        public Loop(Sound sound,SoundSource source,RandomSource random){}
        public void tick(){}public void stop(){stopped=true;}public boolean isStopped(){return stopped;}public boolean canStartSilent(){return false;}
    }
    static class Loader extends ClassLoader {
        static final String ORIGINAL="com/liucodex/doggiesnewvehicle/client/DnvVehicleLoopSoundManager";
        static final String GENERATED="com/liucodex/doggiesnewvehicle/boss/soundprobe/Manager";
        final Map<String,String> names=new HashMap<>();
        Loader(){
            super(MothershipLoopSoundExecutionTest.class.getClassLoader());
            for(String suffix:new String[]{"","$IdleLoop","$DriveLoop"})names.put(ORIGINAL+suffix,GENERATED+suffix);
            names.put("net/minecraft/world/entity/Entity",Type.getInternalName(Actor.class));
            names.put("net/minecraft/client/player/LocalPlayer",Type.getInternalName(Actor.class));
            names.put("net/minecraft/world/level/Level",Type.getInternalName(World.class));
            names.put("net/minecraft/client/multiplayer/ClientLevel",Type.getInternalName(World.class));
            names.put("net/minecraft/client/Minecraft",Type.getInternalName(Client.class));
            names.put("net/minecraft/client/sounds/SoundManager",Type.getInternalName(Audio.class));
            names.put("net/minecraft/client/resources/sounds/AbstractTickableSoundInstance",Type.getInternalName(Loop.class));
            names.put("net/minecraft/client/resources/sounds/SoundInstance",Type.getInternalName(Loop.class));
            names.put("net/minecraft/sounds/SoundEvent",Type.getInternalName(Sound.class));
        }
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            String source=ORIGINAL+name.replace('.','/').substring(GENERATED.length());
            try(var input=getParent().getResourceAsStream(source+".class")) {
                ClassWriter out=new ClassWriter(0);new ClassReader(input).accept(new ClassRemapper(out,new SimpleRemapper(names)),0);
                byte[] bytes=out.toByteArray();return defineClass(name,bytes,0,bytes.length);
            }catch(Exception e){throw new ClassNotFoundException(name,e);}
        }
    }
}
