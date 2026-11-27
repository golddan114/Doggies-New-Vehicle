package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.client.DnvDynamicLightField;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.ClassNode;

/** Runs the compiled client tick/reload bodies; native checkBlock and light cache are explicit ports. */
class DnvDynamicLightsLifecycleExecutionTest {
    @Test void missingPlayerWithdrawsOldNativeLightBeforeSameWorldResume()throws Exception{
        var type=production();var field=field(type);Client.current=new Client();
        var world=new World();Client.current.level=world;world.engine.listener.field=field;
        field.update(world.engine.listener,List.of(new DnvDynamicLightField.Source(0,64,0,12)),0,64,0);
        world.engine.listener.cached=12;
        type.getMethod("tick",Event.class).invoke(null,new Event());
        assertEquals(List.of(new DnvDynamicLightField.Block(0,64,0)),world.engine.listener.checked);
        assertEquals(2,world.engine.listener.cached,"Removed source must restore the native cached emission");
        assertEquals(0,field.emission(world.engine.listener,0,64,0));
        Client.current.player=new Player();type.getMethod("tick",Event.class).invoke(null,new Event());
        assertEquals(2,world.engine.listener.cached,"Same-world resume cannot inherit a stale 12-level source");
    }
    @Test void reloadWithdrawsSourcesAndMissingWorldReleasesTheOldEngine()throws Exception{
        var type=production();var field=field(type);Client.current=new Client();
        var world=new World();Client.current.level=world;world.engine.listener.field=field;
        field.update(world.engine.listener,List.of(new DnvDynamicLightField.Source(0,64,0,15)),0,64,0);
        world.engine.listener.cached=15;type.getMethod("reload").invoke(null);
        assertEquals(2,world.engine.listener.cached);
        field.update(world.engine.listener,List.of(new DnvDynamicLightField.Source(0,64,0,15)),0,64,0);
        Client.current.level=null;type.getMethod("tick",Event.class).invoke(null,new Event());
        assertEquals(0,field.emission(world.engine.listener,0,64,0));
        assertEquals(1,world.engine.listener.checked.size(),"An absent world must never check old chunks");
    }
    public enum Phase{END} public enum Layer{BLOCK}
    public static class Event{public Phase phase=Phase.END;}
    public static class Pos{public final int x,y,z;public Pos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}}
    public static class Player{public double getX(){return 0;}public double getY(){return 64;}public double getZ(){return 0;}}
    public interface Listener{void checkBlock(Pos position);}
    public static class NativeCache implements Listener{public DnvDynamicLightField field;public int cached;
        public final List<DnvDynamicLightField.Block> checked=new ArrayList<>();
        public void checkBlock(Pos p){checked.add(new DnvDynamicLightField.Block(p.x,p.y,p.z));cached=Math.max(2,field.emission(this,p.x,p.y,p.z));}}
    public static class LightEngine{public final NativeCache listener=new NativeCache();public Listener getLayerListener(Layer layer){return listener;}}
    public static class World{public final LightEngine engine=new LightEngine();public LightEngine getLightEngine(){return engine;}
        public boolean hasChunkAt(Pos position){return true;}}
    public static class Client{public static Client current;public World level;public Player player;
        public static Client getInstance(){return current;}public boolean isPaused(){return false;}}
    public static class Effects{public static void appendLights(List<DnvDynamicLightField.Source> sources,World world){}}
    private static DnvDynamicLightField field(Class<?> type)throws Exception{
        var member=type.getDeclaredField("FIELD");member.setAccessible(true);return (DnvDynamicLightField)member.get(null);
    }
    private static Class<?> production()throws Exception{
        String name="com/liucodex/doggiesnewvehicle/client/DnvDynamicLights",probe=name+"LifecycleProbe";
        var mapping=new HashMap<String,String>();mapping.put(name,probe);
        Map.ofEntries(Map.entry("net/minecraft/client/Minecraft",Client.class),
                Map.entry("net/minecraft/client/multiplayer/ClientLevel",World.class),Map.entry("net/minecraft/world/level/Level",World.class),
                Map.entry("net/minecraft/client/player/LocalPlayer",Player.class),Map.entry("net/minecraft/world/entity/Entity",Player.class),
                Map.entry("net/minecraft/core/BlockPos",Pos.class),Map.entry("net/minecraft/world/level/LightLayer",Layer.class),
                Map.entry("net/minecraft/world/level/lighting/LevelLightEngine",LightEngine.class),
                Map.entry("net/minecraft/world/level/lighting/LayerLightEventListener",Listener.class),
                Map.entry("net/minecraftforge/event/TickEvent",Event.class),Map.entry("net/minecraftforge/event/TickEvent$ClientTickEvent",Event.class),
                Map.entry("net/minecraftforge/event/TickEvent$Phase",Phase.class),
                Map.entry("net/neoforged/neoforge/client/event/ClientTickEvent$Post",Event.class),
                Map.entry("com/liucodex/doggiesnewvehicle/client/BlizzardClientEffects",Effects.class),
                Map.entry("com/liucodex/doggiesnewvehicle/client/NuclearClientEffects",Effects.class))
                .forEach((source,type)->mapping.put(source,Type.getInternalName(type)));
        try(var stream=DnvDynamicLightsLifecycleExecutionTest.class.getClassLoader().getResourceAsStream(name+".class")){
            var source=new ClassNode();new ClassReader(stream.readAllBytes()).accept(source,0);
            source.visibleAnnotations=null;source.invisibleAnnotations=null;
            source.innerClasses.clear();source.nestMembers=null;source.nestHostClass=null;
            var writer=new ClassWriter(0);source.accept(new ClassRemapper(writer,new SimpleRemapper(mapping)));
            return new ExactQueuedShotFixture.Loader(Map.of(probe,writer.toByteArray())).loadClass(probe.replace('/','.'));
        }
    }
}
