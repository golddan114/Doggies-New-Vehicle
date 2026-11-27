package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes shared request and key entry methods against authenticated seating and client-input ports. */
class DnvVehicleModeExecutionTest {
    @Test void sharedDispatcherAcceptsAnyCapableDriverAndKeepsVehicleReadinessAuthoritative()throws Exception{
        var accept=network();var actor=new Actor();var vehicle=new Capable();seat(actor,vehicle);
        assertTrue((boolean)accept.invoke(null,actor,vehicle.id));assertEquals(1,vehicle.requests);
        vehicle.ready=false;assertFalse((boolean)accept.invoke(null,actor,vehicle.id));assertEquals(2,vehicle.requests);
    }
    @Test void stalePassengerDeadSpectatorForeignWorldAndMissingCapabilityNeverReachModeWriter()throws Exception{
        var accept=network();assertFalse((boolean)accept.invoke(null,null,UUID.randomUUID()));
        for(int reason=0;reason<10;reason++){
            var actor=new Actor();var vehicle=new Capable();seat(actor,vehicle);UUID requested=vehicle.id;
            if(reason==0)requested=UUID.randomUUID();
            if(reason==1)vehicle.driver=new Actor();
            if(reason==2)actor.alive=false;
            if(reason==3)actor.spectator=true;
            if(reason==4)vehicle.alive=false;
            if(reason==5)vehicle.removed=true;
            if(reason==6)vehicle.world=new World();
            if(reason==7)actor.world.isClientSide=true;
            if(reason==8)actor.vehicle=null;
            if(reason==9)seat(actor,new Entity());
            assertFalse((boolean)accept.invoke(null,actor,requested),"reason="+reason);assertEquals(0,vehicle.requests);
        }
    }
    @Test void remappedKeyDrainsClicksOnceAndRejectsMenusPauseFocusLossAndUnsupportedVehicles()throws Exception{
        var tick=key();assertEquals("key.doggiesnewvehicle.blizzard_mode",KeyMapping.name);assertEquals(71,KeyMapping.defaultKey);
        KeyMapping.boundKey=72; // Options can rebind the same mapping; the entry never polls raw GLFW G.
        for(int reason=0;reason<7;reason++){
            Client.INSTANCE=new Client();Sink.requests.clear();var actor=new Actor();seat(actor,new Capable());Client.INSTANCE.player=actor;
            if(reason==1)Client.INSTANCE.screen=new Screen();
            if(reason==2)Client.INSTANCE.paused=true;
            if(reason==3)Client.INSTANCE.focused=false;
            if(reason==4)Client.INSTANCE.player=null;
            if(reason==5)seat(actor,new Entity());
            if(reason==6)actor.vehicle=null;
            KeyMapping.clicks=3;tick.invoke(null,new Tick());assertEquals(0,KeyMapping.clicks);
            assertEquals(reason==0?1:0,Sink.requests.size());tick.invoke(null,new Tick());assertEquals(reason==0?1:0,Sink.requests.size());
        }
    }
    private static void seat(Actor actor,Entity vehicle){actor.vehicle=vehicle;vehicle.driver=actor;vehicle.world=actor.world;}
    private java.lang.reflect.Method network()throws Exception{
        return load("com/liucodex/doggiesnewvehicle/network/DnvVehicleModeNetwork",Set.of("accept"),Map.of())
                .getMethod("accept",Actor.class,UUID.class);
    }
    private java.lang.reflect.Method key()throws Exception{
        return load("com/liucodex/doggiesnewvehicle/client/DnvVehicleModeKey",Set.of("tick","<clinit>"),Map.of(
                "com/liucodex/doggiesnewvehicle/network/DnvVehicleModeNetwork",Type.getInternalName(Sink.class)))
                .getMethod("tick",Tick.class);
    }
    private Class<?> load(String source,Set<String> methods,Map<String,String> extra)throws Exception{
        var node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(in.readAllBytes()).accept(node,0);}
        node.methods.removeIf(m->!methods.contains(m.name));node.fields.removeIf(f->!f.name.equals("KEY"));node.innerClasses.clear();
        node.methods.forEach(m->m.access=(m.access&~Opcodes.ACC_PRIVATE)|Opcodes.ACC_PUBLIC);
        String target=source+"Execution";var names=new HashMap<String,String>();names.put(source,target);names.putAll(extra);
        for(var e:Map.ofEntries(Map.entry("net/minecraft/server/level/ServerPlayer",Actor.class),Map.entry("net/minecraft/world/entity/player/Player",Actor.class),
                Map.entry("net/minecraft/client/player/LocalPlayer",Actor.class),Map.entry("net/minecraft/world/entity/Entity",Entity.class),
                Map.entry("net/minecraft/world/level/Level",World.class),Map.entry("net/minecraft/client/Minecraft",Client.class),
                Map.entry("net/minecraft/client/KeyMapping",KeyMapping.class),Map.entry("net/minecraft/client/gui/screens/Screen",Screen.class),
                Map.entry("net/minecraftforge/event/TickEvent$ClientTickEvent",Tick.class),Map.entry("net/minecraftforge/event/TickEvent$Phase",Phase.class),
                Map.entry("net/neoforged/neoforge/client/event/ClientTickEvent$Post",Tick.class),
                Map.entry("com/liucodex/doggiesnewvehicle/api/DnvVehicleModeSwitch",Modes.class)).entrySet())names.put(e.getKey(),Type.getInternalName(e.getValue()));
        var out=new ClassWriter(0);node.accept(new ClassRemapper(out,new SimpleRemapper(names)));
        var loader=new ExactQueuedShotFixture.Loader(Map.of(target,out.toByteArray()));
        return Class.forName(target.replace('/','.'),true,loader);
    }
    public interface Modes {boolean requestMode(Actor actor);}
    public static class World {public boolean isClientSide;}
    public static class Entity {
        public UUID id=UUID.randomUUID();public Actor driver;public World world=new World();public boolean alive=true,removed;
        public UUID getUUID(){return id;}public Entity getFirstPassenger(){return driver;}public World level(){return world;}
        public boolean isAlive(){return alive;}public boolean isRemoved(){return removed;}
    }
    public static class Actor extends Entity {public Entity vehicle;public boolean spectator;public Entity getVehicle(){return vehicle;}public boolean isSpectator(){return spectator;}}
    public static class Capable extends Entity implements Modes {public int requests;public boolean ready=true;public boolean requestMode(Actor actor){assertSame(driver,actor);requests++;return ready;}}
    public static class Screen { }
    public enum Phase {START,END}
    public static class Tick {public Phase phase=Phase.END;}
    public static class Client {
        public static Client INSTANCE=new Client();public Actor player;public Screen screen;public boolean paused,focused=true;
        public static Client getInstance(){return INSTANCE;}public boolean isPaused(){return paused;}public boolean isWindowActive(){return focused;}
    }
    public static class KeyMapping {
        public static String name;public static int defaultKey,boundKey,clicks;
        public KeyMapping(String label,int key,String category){name=label;defaultKey=boundKey=key;assertEquals("key.categories.doggiesnewvehicle",category);}
        public boolean consumeClick(){if(clicks==0)return false;clicks--;return true;}
    }
    public static class Sink {public static final List<UUID> requests=new ArrayList<>();public static void request(UUID id){requests.add(id);}}
}
