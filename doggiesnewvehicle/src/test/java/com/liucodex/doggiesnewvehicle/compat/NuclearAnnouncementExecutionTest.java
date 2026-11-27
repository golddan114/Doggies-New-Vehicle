package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NuclearAnnouncementExecutionTest {
    @ParameterizedTest @ValueSource(strings={"announceReady","announceLaunch"})
    void productionAnnouncementNotifiesEveryPlayerInTheLevelWithoutDistanceOrTeamFiltering(String method) throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerPlayer",Listener.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/sounds/SoundEvent",Sound.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraftforge/registries/RegistryObject",Ref.class);
        NuclearRecoveryExecutionTest.map(names,"net/neoforged/neoforge/registries/DeferredHolder",Ref.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/DnvArtillerySounds",Sounds.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",
                Set.of(method),names,null);
        var announce=type.getMethod(method,World.class);boolean ready=method.equals("announceReady");
        var world=new World();var otherDimension=new World();
        var near=new Listener(0,"owner");var far=new Listener(1_000_000,"enemy");
        var spectator=new Listener(-1_000_000,"spectator");var other=new Listener(0,"owner");
        world.listeners.addAll(List.of(near,far,spectator));otherDimension.listeners.add(other);
        announce.invoke(null,world);
        for(var player:world.listeners){
            assertEquals(1,player.heard.size());var event=player.heard.get(0);
            assertSame(ready?Sounds.VOICE:Sounds.SIREN,event.sound());
            assertEquals(ready?SoundSource.VOICE:SoundSource.BLOCKS,event.channel());
            assertEquals(ready?1F:1.6F,event.volume());assertEquals(1,event.pitch());
        }
        assertTrue(other.heard.isEmpty());world.listeners.clear();announce.invoke(null,world);
        assertEquals(1,near.heard.size());
    }
    public record Sound(String id) { }
    public record Heard(Sound sound,SoundSource channel,float volume,float pitch) { }
    public static class Ref {private final Sound sound;Ref(Sound sound){this.sound=sound;}public Object get(){return sound;}}
    public static class Sounds {
        public static final Sound VOICE=new Sound("nuclear_ready_voice");
        public static final Sound SIREN=new Sound("nuclear_siren");
        public static final Ref NUCLEAR_READY_VOICE=new Ref(VOICE),NUCLEAR_SIREN=new Ref(SIREN);
    }
    public static class World {final List<Listener> listeners=new ArrayList<>();public List<Listener> players(){return listeners;}}
    public static class Listener {
        final double x;final String team;final List<Heard> heard=new ArrayList<>();
        Listener(double x,String team){this.x=x;this.team=team;}
        public void playNotifySound(Sound sound,SoundSource channel,float volume,float pitch){heard.add(new Heard(sound,channel,volume,pitch));}
    }
}
