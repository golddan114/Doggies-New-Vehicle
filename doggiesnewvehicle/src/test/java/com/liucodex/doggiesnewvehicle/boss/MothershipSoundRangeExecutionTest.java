package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Runs the real registration suppliers and checks the effective vanilla fade distance and positional audio assets. */
public class MothershipSoundRangeExecutionTest {
    @Test void serverBroadcastAndClientSamplesShareDoomsday256Station192AndIdle96()throws Exception {
        var type=load();Registrar register=(Registrar)type.getField("SOUNDS").get(null);
        var json=JsonParser.parseString(Files.readString(Path.of("src/boss/resources/assets/dnv_mothership/sounds.json"))).getAsJsonObject();
        assertEquals(json.keySet(),register.entries.keySet());assertEquals(11,register.entries.size());
        float doomsdayVolume=type.getField("DOOMSDAY_VOLUME").getFloat(null);
        float lockVolume=type.getField("LOCK_VOLUME").getFloat(null);
        float abortVolume=type.getField("ABORT_VOLUME").getFloat(null);
        register.entries.forEach((name,cue)->{
            boolean doomsday=Set.of("lock","charge","fire","cool","blast","abort").contains(name);
            int expected=doomsday?256:Set.of("idle","propulsion").contains(name)?96:192;
            assertEquals(expected,cue.range);assertEquals("dnv_mothership",cue.name.getNamespace());assertEquals(name,cue.name.getPath());
            for(var sample:json.getAsJsonObject(name).getAsJsonArray("sounds"))
                assertEquals(expected-(doomsday&&!Set.of("lock","abort").contains(name)?1:0),
                        Math.max(name.equals("lock")?lockVolume:name.equals("abort")?abortVolume:doomsday?doomsdayVolume:1F,1F)
                        *sample.getAsJsonObject().get("attenuation_distance").getAsInt(),0.001,"Vanilla fade endpoint: "+name);
        });
    }
    @Test void everyAuthoredDoomsdaySampleIsMonoForOpenAlDistanceAttenuation()throws Exception {
        for(String name:List.of("charge","doomsday_charge","doomsday_fire","doomsday_cool","doomsday_abort")) {
            byte[] bytes=Files.readAllBytes(Path.of("src/boss/resources/assets/dnv_mothership/sounds/"+name+".ogg"));
            assertEquals("OggS",new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII));
            int packet=27+Byte.toUnsignedInt(bytes[26]);
            assertEquals(1,bytes[packet]);
            assertEquals("vorbis",new String(bytes,packet+1,6,java.nio.charset.StandardCharsets.US_ASCII));
            assertEquals(1,Byte.toUnsignedInt(bytes[packet+11]),name+" must be mono for positional playback");
        }
    }
    @Test void cancellationStopsOnlyMothershipPhaseSoundsForEveryDimensionListener() throws Exception {
        var type=load();var world=new World();var first=new Listener();var second=new Listener();
        world.listeners.addAll(List.of(first,second));
        type.getMethod("stopDoomsday",Actor.class).invoke(null,new Actor(world));
        for(var player:world.listeners) {
            assertEquals(List.of("lock","charge","fire","cool","abort"),
                    player.connection.packets.stream().map(p->p.name.getPath()).toList());
            assertTrue(player.connection.packets.stream().allMatch(p->p.name.getNamespace().equals("dnv_mothership")
                    && p.source==net.minecraft.sounds.SoundSource.HOSTILE));
        }
    }
    public record Actor(World world) {public World level(){return world;}}
    public static class World {public final List<Listener> listeners=new ArrayList<>();public List<Listener> players(){return listeners;}}
    public static class Listener {public final Connection connection=new Connection();}
    public static class Connection {public final List<Packet> packets=new ArrayList<>();public void send(Packet p){packets.add(p);}}
    public record Packet(ResourceLocation name,net.minecraft.sounds.SoundSource source) { }
    public record Cue(ResourceLocation name,float range){
        public static Cue createFixedRangeEvent(ResourceLocation name,float range){return new Cue(name,range);}
    }
    public interface Registry { }
    public static class Registries {public static final Registry SOUND_EVENTS=new Registry(){};}
    public record Holder(Object value) { }
    public static class Registrar {
        public final Map<String,Cue> entries=new LinkedHashMap<>();
        public static Registrar create(Registry registry,String namespace){assertEquals("dnv_mothership",namespace);return new Registrar();}
        public Holder register(String name,Supplier<Cue> supplier){var cue=supplier.get();assertNull(entries.put(name,cue));return new Holder(cue);}
    }
    private static Class<?> load()throws Exception {
        String source="com/liucodex/doggiesnewvehicle/boss/MothershipSounds",target="com/liucodex/doggiesnewvehicle/boss/ExecutedSounds";
        Map<String,String> mapping=new HashMap<>();mapping.put(source,target);
        mapping.put("net/minecraft/world/entity/Entity",Type.getInternalName(Actor.class));
        mapping.put("net/minecraft/world/level/Level",Type.getInternalName(World.class));
        mapping.put("net/minecraft/server/level/ServerLevel",Type.getInternalName(World.class));
        mapping.put("net/minecraft/server/level/ServerPlayer",Type.getInternalName(Listener.class));
        mapping.put("net/minecraft/server/network/ServerGamePacketListenerImpl",Type.getInternalName(Connection.class));
        mapping.put("net/minecraft/network/protocol/game/ClientboundStopSoundPacket",Type.getInternalName(Packet.class));
        mapping.put("net/minecraft/network/protocol/Packet",Type.getInternalName(Packet.class));
        for(var entry:Map.of("net/minecraft/sounds/SoundEvent",Cue.class,"net/minecraftforge/registries/DeferredRegister",Registrar.class,
                "net/minecraftforge/registries/ForgeRegistries",Registries.class,"net/minecraftforge/registries/IForgeRegistry",Registry.class,
                "net/minecraftforge/registries/RegistryObject",Holder.class).entrySet())mapping.put(entry.getKey(),Type.getInternalName(entry.getValue()));
        byte[] bytes;
        try(var in=MothershipSoundRangeExecutionTest.class.getClassLoader().getResourceAsStream(source+".class")) {
            assertNotNull(in);var out=new ClassWriter(0);new ClassReader(in).accept(new ClassRemapper(out,new SimpleRemapper(mapping)),0);bytes=out.toByteArray();
        }
        return new ClassLoader(MothershipSoundRangeExecutionTest.class.getClassLoader()){
            Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}
        }.define();
    }
}
