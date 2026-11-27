package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the production observer, with chunk/raycast/budget backends supplied by ports. */
class DnvTargetSightExecutionTest {
    @Test void hiddenNearestCannotLockOrStarveLaterVisibleCandidate() throws Exception {
        View sight = create(0); Actor source = new Actor(), hidden = new Actor(), visible = new Actor();
        hidden.clear = false; hidden.point = new Vec3(10, 0, 0); visible.point = new Vec3(20, 0, 0);
        List<Actor> candidates = List.of(hidden, visible);
        assertNull(sight.select(source, Vec3.ZERO, candidates, null, e -> true));
        assertEquals(1, source.world.rays);
        source.tickCount++;
        assertSame(visible, sight.select(source, Vec3.ZERO, candidates, null, e -> true));
        assertEquals(2, source.world.rays);
    }
    @Test void movementInvalidatesPositiveObservationAndDeferredIsNotVisibility() throws Exception {
        View sight = create(1); Actor source = new Actor(), target = new Actor();
        assertEquals(true, sight.visible(source, Vec3.ZERO, target));
        source.world.deferred = true; target.point = target.point.add(1, 0, 0);
        assertNull(sight.visible(source, Vec3.ZERO, target));
        assertNull(sight.select(source, Vec3.ZERO, List.of(target), target, e -> true));
        source.world.deferred = false; source.tickCount++; target.clear = false;
        assertEquals(false, sight.visible(source, Vec3.ZERO, target));
        assertNull(sight.select(source, Vec3.ZERO, List.of(target), target, e -> true));
    }
    @Test void chunkBoundaryAndStationaryNewWallFailClosedWithinBoundedObservationAge() throws Exception {
        View sight = create(2); Actor source = new Actor(), target = new Actor();
        target.point = new Vec3(48, 0, 48); source.world.missingChunk = true;
        assertEquals(false, sight.visible(source, Vec3.ZERO, target)); assertEquals(0, source.world.rays);
        source.world.missingChunk = false; source.tickCount += 10;
        assertEquals(true, sight.visible(source, Vec3.ZERO, target));
        target.clear = false; source.tickCount += 10;
        assertEquals(false, sight.visible(source, Vec3.ZERO, target));
        sight.clear(); assertEquals(1, source.world.released);
    }
    @Test void independentObserversAndWorldChangeDoNotShareAuthorization() throws Exception {
        View first = create(0), second = create(1); Actor source = new Actor(), target = new Actor();
        assertEquals(true, first.visible(source, Vec3.ZERO, target));
        assertEquals(true, second.visible(source, Vec3.ZERO, target)); assertEquals(2, source.world.rays);
        World old = source.world; source.world = new World(); source.world.deferred = true;
        assertNull(first.visible(source, Vec3.ZERO, target)); assertEquals(1, old.released);
        assertNull(second.select(source, Vec3.ZERO, List.of(target), target, e -> false));
    }
    public interface View {
        Boolean visible(Actor source, Vec3 origin, Actor target);
        Actor select(Actor source, Vec3 origin, List<Actor> candidates, Actor current, Predicate<Actor> eligible);
        void clear();
    }
    static View create(int slot) throws Exception {
        String original = "com/liucodex/doggiesnewvehicle/compat/DnvTargetSight", probe = original + "Executed";
        ClassNode node = new ClassNode();
        try (var in = DnvTargetSightExecutionTest.class.getResourceAsStream("/" + original + ".class")) {
            assertNotNull(in); new ClassReader(in).accept(node, 0);
        }
        node.interfaces.add(Type.getInternalName(View.class));
        Map<String,String> names = new HashMap<>(); names.put(original, probe);
        Map.of("net/minecraft/world/entity/Entity", Actor.class, "net/minecraft/world/level/Level", World.class,
                "net/minecraft/server/level/ServerLevel", World.class,
                "com/liucodex/doggiesnewvehicle/compat/DnvTargetingBudget", Budget.class,
                "com/liucodex/doggiesnewvehicle/compat/DnvTargetLineOfSight", Ray.class)
                .forEach((k,v) -> names.put(k, Type.getInternalName(v)));
        ClassWriter out = new ClassWriter(0); node.accept(new ClassRemapper(out, new SimpleRemapper(names)));
        byte[] bytes = out.toByteArray();
        Class<?> type = new ClassLoader(DnvTargetSightExecutionTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(probe.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
        return (View)type.getConstructor(int.class).newInstance(slot);
    }
    public static class Actor {
        static long sequence;
        public final UUID id = new UUID(0, ++sequence);
        public Vec3 point = new Vec3(4, 0, 0); public boolean clear = true;
        public int tickCount = 100; public World world = new World();
        public UUID getUUID() { return id; } public int getId() { return 42; }
        public World level() { world.now = tickCount; return world; }
        public AABB getBoundingBox() { return new AABB(point, point.add(1, 2, 1)); }
        public double getEyeY() { return point.y + 1.7; }
        public double distanceToSqr(Actor other) { return point.distanceToSqr(other.point); }
    }
    public static class World {
        public boolean deferred, missingChunk; public int rays, released, now;
        public Map<UUID,Integer> checked = new HashMap<>();
        public boolean hasChunk(int x, int z) { return !missingChunk || x != 1 || z != 1; }
    }
    public static class Budget {
        public static boolean admitGeometryCheck(World level, UUID id) { if (level.deferred || Objects.equals(level.checked.get(id), level.now)) return false;
            level.checked.put(id, level.now); return true; }
        public static void releaseGeometryOwner(World level, UUID id) { level.released++; }
        public static int nextCheckTick(int tick, int id, int interval, int salt) { return tick + interval; }
    }
    public static class Ray {
        public static Optional<Vec3> clearAimPoint(World world, Actor source, Vec3 from, Actor target) {
            world.rays++;
            return target.clear ? Optional.of(target.point) : Optional.empty();
        }
    }
}
