package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Executes the production frame queue with render/level ports; GPU compositing remains a field check. */
public class MothershipFxPassExecutionTest {
    @Test void effectsWaitForSceneDepthDeduplicateAndDiscardRemovedCrossWorldOrStaleEntities()throws Exception {
        Class<?> type=load();var enqueue=type.getMethod("enqueue",Ship.class);var render=type.getMethod("render",Event.class);
        Client.INSTANCE.level=new TestWorld(true);Renderer.drawn.clear();Buffer.flushes=0;
        Ship live=new Ship(Client.INSTANCE.level),removed=new Ship(Client.INSTANCE.level),foreign=new Ship(new TestWorld(true));removed.removed=true;
        render.invoke(null,new Event(Stage.AFTER_SKY));
        for(var ship:List.of(live,live,removed,foreign))enqueue.invoke(null,ship);
        render.invoke(null,new Event(Stage.AFTER_ENTITIES));assertTrue(Renderer.drawn.isEmpty());assertEquals(0,Buffer.flushes);
        render.invoke(null,new Event(Stage.AFTER_PARTICLES));assertEquals(List.of(live),Renderer.drawn);assertEquals(1,Buffer.flushes);
        render.invoke(null,new Event(Stage.AFTER_PARTICLES));assertEquals(1,Renderer.drawn.size());assertEquals(1,Buffer.flushes);
        enqueue.invoke(null,live);render.invoke(null,new Event(Stage.AFTER_SKY));render.invoke(null,new Event(Stage.AFTER_PARTICLES));
        assertEquals(1,Renderer.drawn.size());
        enqueue.invoke(null,live);Client.INSTANCE.level=null;render.invoke(null,new Event(Stage.AFTER_PARTICLES));
        Client.INSTANCE.level=live.world;render.invoke(null,new Event(Stage.AFTER_PARTICLES));assertEquals(1,Renderer.drawn.size());
    }

    @Test void onlyClientUnloadClearsTheClientFrameQueue()throws Exception {
        Class<?> type=load();var enqueue=type.getMethod("enqueue",Ship.class);var render=type.getMethod("render",Event.class);
        Client.INSTANCE.level=new TestWorld(true);Renderer.drawn.clear();Ship ship=new Ship(Client.INSTANCE.level);
        enqueue.invoke(null,ship);type.getMethod("unload",Unload.class).invoke(null,new Unload(new TestWorld(false)));
        render.invoke(null,new Event(Stage.AFTER_PARTICLES));assertEquals(List.of(ship),Renderer.drawn);
        enqueue.invoke(null,ship);type.getMethod("unload",Unload.class).invoke(null,new Unload(ship.world));
        render.invoke(null,new Event(Stage.AFTER_PARTICLES));assertEquals(1,Renderer.drawn.size());
    }

    public interface World {boolean isClientSide();}
    public record TestWorld(boolean client) implements World {public boolean isClientSide(){return client;}}
    public static class Ship {
        public final World world;public boolean removed;public Ship(World world){this.world=world;}
        public World level(){return world;}public boolean isRemoved(){return removed;}
    }
    public static class Client {public static final Client INSTANCE=new Client();public World level;public static Client getInstance(){return INSTANCE;}}
    public static class Pose { }
    public static class Camera {public Vec3 getPosition(){return new Vec3(10,64,20);}}
    public enum Stage {AFTER_SKY,AFTER_ENTITIES,AFTER_PARTICLES}
    public record Event(Stage stage) {
        public Stage getStage(){return stage;}public float getPartialTick(){return .5F;}
        public Pose getPoseStack(){return new Pose();}public Camera getCamera(){return new Camera();}
    }
    public record Unload(World world){public World getLevel(){return world;}}
    public record Builder(int size) { }
    public interface Buffers {static Buffer immediate(Builder builder){assertEquals(128*1024,builder.size());return new Buffer();}}
    public static class Buffer implements Buffers {public static int flushes;public void endBatch(){flushes++;}}
    public static class Renderer {
        public static final List<Ship> drawn=new ArrayList<>();
        public static void renderFx(Ship ship,float partial,Pose pose,Buffers buffers,Vec3 camera){
            assertEquals(.5F,partial);assertEquals(new Vec3(10,64,20),camera);drawn.add(ship);
        }
    }
    private static Class<?> load()throws Exception {
        String source="com/liucodex/doggiesnewvehicle/boss/client/MothershipFxPass";
        String target="com/liucodex/doggiesnewvehicle/boss/ExecutedFxPass";
        Map<String,String> names=new HashMap<>();names.put(source,target);
        for(var entry:Map.ofEntries(
                Map.entry("net/minecraft/client/Minecraft",Client.class),Map.entry("net/minecraft/client/multiplayer/ClientLevel",World.class),
                Map.entry("net/minecraft/world/level/Level",World.class),Map.entry("net/minecraft/world/level/LevelAccessor",World.class),
                Map.entry("com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity",Ship.class),
                Map.entry("com/liucodex/doggiesnewvehicle/boss/client/DnvMothershipRenderer",Renderer.class),
                Map.entry("com/mojang/blaze3d/vertex/PoseStack",Pose.class),Map.entry("com/mojang/blaze3d/vertex/BufferBuilder",Builder.class),
                Map.entry("net/minecraft/client/Camera",Camera.class),Map.entry("net/minecraft/client/renderer/MultiBufferSource",Buffers.class),
                Map.entry("net/minecraft/client/renderer/MultiBufferSource$BufferSource",Buffer.class),
                Map.entry("net/minecraftforge/client/event/RenderLevelStageEvent",Event.class),
                Map.entry("net/minecraftforge/client/event/RenderLevelStageEvent$Stage",Stage.class),
                Map.entry("net/minecraftforge/event/level/LevelEvent$Unload",Unload.class)).entrySet())
            names.put(entry.getKey(),Type.getInternalName(entry.getValue()));
        byte[] bytes;
        try(var in=MothershipFxPassExecutionTest.class.getClassLoader().getResourceAsStream(source+".class")) {
            assertNotNull(in);ClassWriter out=new ClassWriter(0);new ClassReader(in).accept(new ClassRemapper(out,new SimpleRemapper(names)),0);bytes=out.toByteArray();
        }
        return new ClassLoader(MothershipFxPassExecutionTest.class.getClassLoader()){
            Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}
        }.define();
    }
}
