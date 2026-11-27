package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the production effect/loop methods. Only Minecraft audio/entity ports are replaced. */
class SelectedAudioLifecycleExecutionTest {
    @Test void nuclearIdleUsesJingzheOnceAndStopsOnPowerMotionDistanceRemovalReloadAndDisconnect()throws Exception{
        Client.current=new Client();var mc=Client.current;var type=load("NuclearSiloSoundEffects",List.of("","$Observer","$MechanicalSound")).get("");
        var join=type.getMethod("loaded",Join.class);var tick=type.getMethod("tick",Tick.class);var silo=new Body(mc.level);silo.energy=100;
        silo.state.putString("Phase","READY");join.invoke(null,new Join(mc.level,silo));
        tick.invoke(null,new Tick());tick.invoke(null,new Tick());assertEquals(1,mc.manager.plays);
        var idle=mc.manager.live.iterator().next();assertEquals("jingzhe_idle",idle.event.id);assertTrue(idle.looping);
        assertEquals(.65F,idle.volume);assertEquals(1,idle.pitch);
        silo.energy=0;tick.invoke(null,new Tick());assertTrue(mc.manager.live.isEmpty());
        silo.energy=100;tick.invoke(null,new Tick());assertEquals(2,mc.manager.plays);
        silo.state.putString("Phase","OPENING");silo.state.putInt("Remaining",300);tick.invoke(null,new Tick());
        assertTrue(mc.manager.live.isEmpty());
        silo.state.putString("Phase","READY");tick.invoke(null,new Tick());assertEquals(1,mc.manager.live.size());
        silo.distance=65;tick.invoke(null,new Tick());assertTrue(mc.manager.live.isEmpty());
        silo.distance=0;tick.invoke(null,new Tick());type.getMethod("reload").invoke(null);assertTrue(mc.manager.live.isEmpty());
        tick.invoke(null,new Tick());silo.removed=true;tick.invoke(null,new Tick());assertTrue(mc.manager.live.isEmpty());
        silo.removed=false;join.invoke(null,new Join(mc.level,silo));tick.invoke(null,new Tick());
        mc.level=null;tick.invoke(null,new Tick());assertTrue(mc.manager.live.isEmpty());
    }
    @Test void blizzardIdleSurvivesModeChangesAndBothLayersStopOnPowerLossWorldChangeAndDistance()throws Exception{
        Client.current=new Client();var mc=Client.current;var tank=new Body(mc.level);
        var classes=load("BlizzardClientEffects",List.of("$Effect","$HoverSound"));
        var effect=classes.get("$Effect").getConstructor(Body.class).newInstance(tank);
        var sounds=effect.getClass().getMethod("sounds",Client.class);var stop=effect.getClass().getMethod("stop");
        sounds.invoke(effect,mc);assertEquals(1,mc.manager.live.size());var idle=mc.manager.live.iterator().next();
        assertEquals("blizzard_idle",idle.event.id);assertEquals(1,idle.pitch);
        tank.hover=true;sounds.invoke(effect,mc);sounds.invoke(effect,mc);assertEquals(2,mc.manager.plays);assertEquals(2,mc.manager.live.size());
        tank.hover=false;sounds.invoke(effect,mc);assertEquals(Set.of(idle),mc.manager.live);
        tank.powered=false;sounds.invoke(effect,mc);assertTrue(mc.manager.live.isEmpty());
        tank.powered=true;sounds.invoke(effect,mc);assertEquals(3,mc.manager.plays);
        var resumed=mc.manager.live.iterator().next();tank.distance=65;resumed.getClass().getMethod("tick").invoke(resumed);assertTrue(resumed.stopped);
        tank.distance=0;stop.invoke(effect);sounds.invoke(effect,mc);resumed=mc.manager.live.iterator().next();
        mc.level=new World();resumed.getClass().getMethod("tick").invoke(resumed);assertTrue(resumed.stopped);
        stop.invoke(effect);assertTrue(mc.manager.live.isEmpty());
    }
    @Test void nuclearMotionStartsAtTheEdgeOnlyAndStopsOnCancellationReloadRemovalAndDisconnect()throws Exception{
        Client.current=new Client();var mc=Client.current;var types=load("NuclearSiloSoundEffects",List.of("","$Observer","$MechanicalSound"));
        var type=types.get("");var join=type.getMethod("loaded",Join.class);var tick=type.getMethod("tick",Tick.class);
        Body silo=new Body(mc.level);silo.id=4;silo.state.putString("Phase","RELOADING");silo.state.putInt("Remaining",1701);
        join.invoke(null,new Join(mc.level,silo));tick.invoke(null,new Tick());assertEquals(0,mc.manager.plays);
        mc.level.time++;tick.invoke(null,new Tick());tick.invoke(null,new Tick());assertEquals(1,mc.manager.plays);
        var motion=mc.manager.live.iterator().next();assertEquals("nuclear_turntable",motion.event.id);assertFalse(motion.looping);assertEquals(1,motion.pitch);
        silo.state.putString("Phase","EMPTY");tick.invoke(null,new Tick());assertTrue(mc.manager.live.isEmpty());assertTrue(motion.stopped);
        for(int reason=0;reason<3;reason++){
            silo.state.putString("Phase","RELOADING");silo.state.putLong("Observed",mc.level.time);silo.state.putInt("Remaining",1701);
            tick.invoke(null,new Tick());mc.level.time++;tick.invoke(null,new Tick());assertEquals(1,mc.manager.live.size());
            if(reason==0)type.getMethod("reload").invoke(null);
            else if(reason==1){silo.removed=true;tick.invoke(null,new Tick());}
            else {mc.level=null;tick.invoke(null,new Tick());}
            assertTrue(mc.manager.live.isEmpty());
            if(reason<2){silo.removed=false;silo.state.putString("Phase","EMPTY");join.invoke(null,new Join(mc.level,silo));tick.invoke(null,new Tick());}
        }
    }
    @Test void nuclearJoiningMidMotionCannotReplayAndWorldReplacementCleansTheOldObserver()throws Exception{
        Client.current=new Client();var mc=Client.current;var type=load("NuclearSiloSoundEffects",List.of("","$Observer","$MechanicalSound")).get("");
        var silo=new Body(mc.level);silo.state.putString("Phase","RELOADING");silo.state.putInt("Remaining",500);
        type.getMethod("loaded",Join.class).invoke(null,new Join(mc.level,silo));
        for(int i=0;i<20;i++){mc.level.time++;type.getMethod("tick",Tick.class).invoke(null,new Tick());}
        assertEquals(0,mc.manager.plays);mc.level=new World();type.getMethod("tick",Tick.class).invoke(null,new Tick());
        assertTrue(((Map<?,?>)type.getField("ACTIVE").get(null)).isEmpty());
    }
    @Test void nuclearWreckStopsIdleAndMechanicalAudioEvenWithPositiveNativeHealth()throws Exception{
        for(boolean motion:new boolean[]{false,true}){
            Client.current=new Client();var mc=Client.current;
            var type=load("NuclearSiloSoundEffects",List.of("","$Observer","$MechanicalSound")).get("");
            var silo=new Body(mc.level);silo.energy=motion?0:100;
            silo.state.putString("Phase",motion?"RELOADING":"READY");silo.state.putInt("Remaining",motion?1701:0);
            type.getMethod("loaded",Join.class).invoke(null,new Join(mc.level,silo));
            var tick=type.getMethod("tick",Tick.class);tick.invoke(null,new Tick());mc.level.time++;tick.invoke(null,new Tick());
            assertEquals(1,mc.manager.live.size());silo.wreck=true;tick.invoke(null,new Tick());
            assertTrue(mc.manager.live.isEmpty(),"A wreck must stop its current sound regardless of native health");
            int plays=mc.manager.plays;mc.level.time+=500;tick.invoke(null,new Tick());assertEquals(plays,mc.manager.plays);
        }
    }
    public enum Phase {START,END}
    public static class Tick {public Phase phase=Phase.END;}
    public record Join(World level,Body entity){public World getLevel(){return level;}public Body getEntity(){return entity;}}
    public static class World {
        public boolean isClientSide=true;public long time;
        public long getGameTime(){return time;}
        public void playLocalSound(double x,double y,double z,Event event,SoundSource source,float volume,float pitch,boolean delay){}
    }
    public static class Body {
        public World world;public boolean removed,powered=true,hover,wreck;public int id=1,energy;public double distance;public CompoundTag state=new CompoundTag();
        public Body(World world){this.world=world;}public World level(){return world;}public int getId(){return id;}
        public boolean isRemoved(){return removed;}public boolean presentationPowered(){return powered;}public boolean hovering(){return hover;}
        public boolean mode2(){return !hover;}public int coldPhase(){return 0;}public boolean isAlive(){return true;}public boolean isWreck(){return wreck;}
        public Vec3 getShootPos(String weapon,float partial){return new Vec3(getX(),getY(),getZ());}
        public Vec3 presentationVelocity(){return Vec3.ZERO;}public double distanceToSqr(Body other){return distance*distance;}
        public double getX(){return 1;}public double getY(){return 64;}public double getZ(){return 3;}public float getHealth(){return 800;}
        public CompoundTag visualSnapshot(){return state.copy();}
        public boolean mechanicsRunning(){return !wreck;}
        public int getEnergy(){return energy;}
    }
    public record Event(String id){}
    public record Ref(String id){public Object get(){return new Event(id);}}
    public static class Sounds {
        public static final Ref BLIZZARD_CHARGE=new Ref("blizzard_charge");
        public static final Ref JINGZHE_IDLE=new Ref("jingzhe_idle"),BLIZZARD_IDLE=new Ref("blizzard_idle"),BLIZZARD_HOVER=new Ref("blizzard_hover"),NUCLEAR_TURNTABLE=new Ref("nuclear_turntable"),NUCLEAR_COVER=new Ref("nuclear_cover"),NUCLEAR_LIFT=new Ref("nuclear_lift");
        public static final Event FIRE_EXTINGUISH=new Event("gas");
    }
    public static class Sound {
        public boolean looping,stopped;public int delay;public float volume,pitch;public double x,y,z;
        public net.minecraft.client.resources.sounds.SoundInstance.Attenuation attenuation;public final Event event;
        public Sound(Event event,SoundSource source,RandomSource random){this.event=event;}
        public void stop(){stopped=true;}public boolean isStopped(){return stopped;}
    }
    public static class Manager {
        public final Set<Sound> live=new LinkedHashSet<>();public int plays;
        public void play(Sound sound){plays++;live.add(sound);}public void stop(Sound sound){sound.stop();live.remove(sound);}
    }
    public static class Client {
        public static Client current;public World level=new World();public Body player=new Body(level);public Manager manager=new Manager();
        public static Client getInstance(){return current;}public Manager getSoundManager(){return manager;}public boolean isPaused(){return false;}
    }
    private Map<String,Class<?>> load(String simple,List<String> suffixes)throws Exception{
        if(simple.equals("NuclearSiloSoundEffects")){suffixes=new ArrayList<>(suffixes);suffixes.add("$1");suffixes.add("$IdleSound");}
        if(simple.equals("BlizzardClientEffects")){suffixes=new ArrayList<>(suffixes);suffixes.add("$ChargeSound");}
        String source="com/liucodex/doggiesnewvehicle/client/"+simple,target=source+"AudioProbe";
        Map<String,String> names=new HashMap<>();names.put(source,target);for(String s:suffixes)names.put(source+s,target+s);
        Map.ofEntries(Map.entry("net/minecraft/client/Minecraft",Client.class),Map.entry("net/minecraft/client/multiplayer/ClientLevel",World.class),
            Map.entry("net/minecraft/world/level/Level",World.class),Map.entry("net/minecraft/world/entity/Entity",Body.class),Map.entry("net/minecraft/client/player/LocalPlayer",Body.class),
            Map.entry("net/minecraft/client/sounds/SoundManager",Manager.class),Map.entry("net/minecraft/client/resources/sounds/AbstractTickableSoundInstance",Sound.class),
            Map.entry("net/minecraft/client/resources/sounds/SoundInstance",Sound.class),Map.entry("net/minecraft/sounds/SoundEvent",Event.class),Map.entry("net/minecraft/sounds/SoundEvents",Sounds.class),
            Map.entry("com/liucodex/doggiesnewvehicle/artillery/DnvArtillerySounds",Sounds.class),Map.entry("net/minecraftforge/registries/RegistryObject",Ref.class),Map.entry("net/neoforged/neoforge/registries/DeferredHolder",Ref.class),
            Map.entry("com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity",Body.class),Map.entry("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",Body.class),
            Map.entry("net/minecraftforge/event/entity/EntityJoinLevelEvent",Join.class),Map.entry("net/neoforged/neoforge/event/entity/EntityJoinLevelEvent",Join.class),
            Map.entry("net/minecraftforge/event/TickEvent$ClientTickEvent",Tick.class),Map.entry("net/minecraftforge/event/TickEvent$Phase",Phase.class),
            Map.entry("net/neoforged/neoforge/client/event/ClientTickEvent$Post",Tick.class),
            Map.entry("com/liucodex/doggiesnewvehicle/neoforge/DnvNeoTicks$ClientTickEvent",Tick.class),Map.entry("com/liucodex/doggiesnewvehicle/neoforge/DnvNeoTicks$Phase",Phase.class))
            .forEach((n,c)->names.put(n,Type.getInternalName(c)));
        Map<String,byte[]> definitions=new HashMap<>();
        for(String s:suffixes){
            var node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+s+".class")){new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
            node.access=Opcodes.ACC_PUBLIC;node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;
            node.fields.forEach(f->f.access=(f.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
            node.methods.removeIf(m->m.name.equals("particles"));node.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
            var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String n){return names.getOrDefault(n,n);}}));definitions.put(target+s,writer.toByteArray());
        }
        var loader=new ExactQueuedShotFixture.Loader(definitions);Map<String,Class<?>> result=new HashMap<>();
        for(String s:suffixes)result.put(s,loader.loadClass((target+s).replace('/','.')));return result;
    }
}
