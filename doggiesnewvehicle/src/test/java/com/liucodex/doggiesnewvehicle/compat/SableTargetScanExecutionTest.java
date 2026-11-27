package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.compat.ApocalypseAutonomyExecutionTest.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Real cache, pager, budgets and AI controller with a public getter wrapper at the world port. */
class SableTargetScanExecutionTest {
    @Test void wrappedPublicGetterStillAcquiresFiresAndReacquiresDenseTailTargets() throws Exception {
        var f = wrappedFixture();
        for (int i = 0; i < 128; i++) {
            Body rejected = new Body(); rejected.eligible = false;
            rejected.position = new Vec3(0, 0, 40); f.add(rejected);
        }
        Body enemy = f.enemy(40);
        for (int tick = 0; tick < 80 && !f.active(enemy); tick++) {
            f.step(); assertTrue(f.tank.world.getter.storage.visits <= 64);
        }
        assertTrue(f.active(enemy)); assertTrue(f.weapons.shots > 0);
        int shots = f.weapons.shots;
        f.tank.world.entities.remove(enemy.id); f.step();
        assertFalse(f.active(enemy)); assertEquals(shots, f.weapons.shots);
        Body replacement = f.enemy(32);
        for (int tick = 0; tick < 80 && !f.active(replacement); tick++) f.step();
        assertTrue(f.active(replacement)); assertTrue(f.weapons.shots > shots);
    }

    @Test void wrapperAndVanillaGetterPublishTheSameBoundedOrderedCandidates() throws Exception {
        var vanilla = new Fixture(true);
        var wrapped = wrappedFixture();
        for (int i = 0; i < 100; i++) {
            Body enemy = new Body(); enemy.id = new UUID(0, i + 1);
            enemy.position = new Vec3(0, 0, 5 + i); vanilla.add(enemy); wrapped.add(enemy);
        }
        for (Fixture f : List.of(vanilla, wrapped)) {
            for (int tick = 0; tick < 80; tick++) {
                f.step(); assertTrue(f.tank.world.getter.storage.visits <= 64);
            }
        }
        List<Body> expected = candidates(cache(vanilla), vanilla.tank);
        assertEquals(32, expected.size());
        assertEquals(expected, candidates(cache(wrapped), wrapped.tank));
    }

    @Test void wrapperDoesNotBypassSightAndWorldChangeAndStopReleaseScanningState() throws Exception {
        var f = wrappedFixture(); Body old = f.enemy(40); f.tank.world.blocked = true;
        for (int tick = 0; tick < 40; tick++) f.step();
        assertFalse(f.active(old)); assertEquals(0, f.weapons.shots);
        f.tank.world.blocked = false;
        for (int tick = 0; tick < 20 && !f.active(old); tick++) f.step();
        assertTrue(f.active(old)); int shots = f.weapons.shots;
        f.tank.world = new Server(); wrap(f); f.tank.world.dimension = "nether";
        f.step(); assertFalse(f.active(old)); assertEquals(shots, f.weapons.shots);
        assertTrue(candidates(cache(f), f.tank).isEmpty());
        Body next = f.enemy(32);
        for (int tick = 0; tick < 80 && !f.active(next); tick++) f.step();
        assertTrue(f.active(next));
        f.tank.ready = false; f.step();
        assertNull(field(cache(f), "scan")); assertTrue(candidates(cache(f), f.tank).isEmpty());
    }

    @Test void fiftyOwnersWithAWrappedGetterKeepTheSharedActualVisitBudgetAndMakeProgress() throws Exception {
        var f = wrappedFixture();
        for (int i = 0; i < 128; i++) {
            Body rejected = new Body(); rejected.eligible = false;
            rejected.position = new Vec3(0, 0, 40); f.add(rejected);
        }
        Body enemy = f.enemy(40);
        Object[] caches = new Object[50]; Tank[] owners = new Tank[50]; boolean[] found = new boolean[50];
        Class<?> type = cache(f).getClass();
        for (int i = 0; i < 50; i++) {
            caches[i] = type.getConstructor(int.class).newInstance(20);
            owners[i] = new Tank(); owners[i].world = f.tank.world;
            owners[i].id = new UUID(1, i); owners[i].runtimeId = i;
        }
        for (int tick = 0; tick < 160 && !all(found); tick++) {
            f.tank.world.time = tick; f.tank.world.getter.storage.rebuild(f.tank.world.entities.values());
            for (int i = 0; i < 50; i++) {
                owners[i].tickCount = tick;
                caches[i].getClass().getMethod("refreshIfDue", Body.class, double.class, Predicate.class)
                        .invoke(caches[i], owners[i], 224.0, (Predicate<Body>) body -> body.eligible);
                found[i] |= candidates(caches[i], owners[i]).contains(enemy);
            }
            assertTrue(f.tank.world.getter.storage.visits <= 16 * 64);
        }
        assertTrue(all(found));
    }

    private static Fixture wrappedFixture() throws Exception {
        Fixture f = new Fixture(true); wrap(f); return f;
    }
    private static void wrap(Fixture f) {
        f.tank.world.entityGetter = new TargetScanExecutionFixture.WrappedGetter(f.tank.world.getter);
    }
    private static boolean all(boolean[] values) {
        for (boolean value : values) if (!value) return false; return true;
    }
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static Object cache(Fixture f) throws Exception { return field(f.instance, "candidates"); }
    @SuppressWarnings("unchecked")
    private static List<Body> candidates(Object cache, Tank source) throws Exception {
        return (List<Body>) cache.getClass().getMethod("candidates", Body.class).invoke(cache, source);
    }
}
