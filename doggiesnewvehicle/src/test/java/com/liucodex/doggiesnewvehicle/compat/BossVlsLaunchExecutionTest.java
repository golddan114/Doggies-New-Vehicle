package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.ClassNode;

/** Executes the production preflight, with only world queries replaced by controlled ports. */
class BossVlsLaunchExecutionTest {
    @Test void summoningShipAboveTheLauncherBlocksUntilItsHullLeavesTheColumn() throws Exception {
        var f = new Fixture();
        var ship = new Body(f.level, new AABB(-10, 15, -10, 10, 25, 10));
        f.level.entities.add(ship);
        assertFalse(f.clear());
        assertEquals(0, f.level.rays);
        ship.bounds = ship.bounds.move(40, 0, 0);
        assertTrue(f.clear());
        assertEquals(5, f.level.rays);
        assertTrue(f.level.lastColumn.maxY >= 35);
    }
    @Test void roofAndUnloadedOrDeferredSpaceRejectWithoutCreatingLaunchPermission() throws Exception {
        var f = new Fixture();
        f.level.blocked = true;
        assertFalse(f.clear()); assertEquals(1, f.level.rays);
        f.level.blocked = false; f.level.loaded = false; f.level.rays = 0;
        assertFalse(f.clear()); assertEquals(0, f.level.rays);
        f.level.loaded = true; Budget.allowed = false;
        assertFalse(f.clear()); assertEquals(0, f.level.rays);
        Budget.allowed = true;
        assertTrue(f.clear());
    }
    @Test void phantomAboveAHigherShipStillExtendsTheCheckedAscentColumn() throws Exception {
        var f = new Fixture();
        f.target.bounds = new AABB(0,60,8,1,62,9);
        f.level.entities.add(new Body(f.level,new AABB(-10,45,-10,10,55,10)));
        assertFalse(f.clear());
        assertEquals(64,f.level.lastColumn.maxY);
    }
    @Test void intendedEnemyRemainsAnImpactDestinationAndSpectatorsDoNotBlock() throws Exception {
        var f = new Fixture();
        f.target.bounds = new AABB(-1, 15, -1, 1, 20, 1);
        f.level.entities.add(f.target);
        var spectator = new Body(f.level, f.target.bounds); spectator.spectator = true;
        f.level.entities.add(spectator);
        assertTrue(f.clear());
        spectator.spectator = false;
        assertFalse(f.clear());
    }
    static class Fixture {
        final Server level = new Server();
        final Body source = new Body(level, new AABB(-3, 0, -3, 3, 6, 3));
        final Body target = new Body(level, new AABB(0, 0, 50, 2, 3, 52));
        final Class<?> type;
        Fixture() throws Exception {
            Budget.allowed = true;
            String sourceName = "com/liucodex/doggiesnewvehicle/boss/DnvBossVlsGuidance";
            String name = "com/liucodex/doggiesnewvehicle/compat/executionprobe/VlsLaunch";
            Map<String,String> names = new HashMap<>(); names.put(sourceName, name);
            Map.ofEntries(Map.entry("net/minecraft/world/entity/Entity", Body.class),
                Map.entry("net/minecraft/world/level/Level", World.class),
                Map.entry("net/minecraft/server/level/ServerLevel", Server.class),
                Map.entry("net/minecraft/core/BlockPos", BlockPos.class),
                Map.entry("net/minecraft/world/level/ClipContext", Clip.class),
                Map.entry("net/minecraft/world/level/ClipContext$Block", Clip.Block.class),
                Map.entry("net/minecraft/world/level/ClipContext$Fluid", Clip.Fluid.class),
                Map.entry("net/minecraft/world/phys/BlockHitResult", Hit.class),
                Map.entry("net/minecraft/world/phys/HitResult$Type", Hit.Kind.class),
                Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvTargetingBudget", Budget.class))
                .forEach((key,value)->names.put(key,Type.getInternalName(value)));
            ClassNode node = new ClassNode();
            try (var in = getClass().getResourceAsStream("/"+sourceName+".class")) { new ClassReader(in.readAllBytes()).accept(node,0); }
            node.methods.removeIf(m -> !m.name.equals("clearLaunch") && !m.name.startsWith("lambda$clearLaunch"));
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}));
            type = new ExactQueuedShotFixture.Loader(Map.of(name,writer.toByteArray())).loadClass(name.replace('/','.'));
        }
        boolean clear() throws Exception { return (boolean)type.getMethod("clearLaunch",Body.class,Body.class,Vec3.class,UUID.class).invoke(null,source,target,new Vec3(0,4,0),source.getUUID()); }
    }
    public static class World { }
    public static class Server extends World {
        public boolean loaded=true,blocked; public int rays; public AABB lastColumn;
        public final List<Body> entities=new ArrayList<>();
        public boolean hasChunksAt(BlockPos a,BlockPos b){return loaded;}
        public List<Body> getEntities(Body source,AABB area,Predicate<? super Body> filter){lastColumn=area;return entities.stream().filter(e->e!=source&&area.intersects(e.bounds)&&filter.test(e)).toList();}
        public Hit clip(Clip clip){rays++;return new Hit(blocked?Hit.Kind.BLOCK:Hit.Kind.MISS);}
    }
    public static class Body {
        public final Server world; public AABB bounds; public boolean spectator;
        public Body(Server world,AABB bounds){this.world=world;this.bounds=bounds;}
        public World level(){return world;} public UUID getUUID(){return new UUID(0,1);}
        public AABB getBoundingBox(){return bounds;} public boolean isAlive(){return true;}
        public boolean isSpectator(){return spectator;} public boolean isPickable(){return true;}
    }
    public static class BlockPos { public static BlockPos containing(double x,double y,double z){return new BlockPos();} }
    public static class Clip {
        public enum Block { COLLIDER } public enum Fluid { NONE }
        public Clip(Vec3 a,Vec3 b,Block block,Fluid fluid,Body source) { }
    }
    public static class Hit { public enum Kind { MISS,BLOCK } final Kind kind; public Hit(Kind kind){this.kind=kind;} public Kind getType(){return kind;} }
    public static class Budget { public static boolean allowed=true; public static boolean admitGeometryCheck(Server level,UUID id){return allowed;} }
}
