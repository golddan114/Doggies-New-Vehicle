package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.api.combat.DnvUpperAirspaceProjectile;
import java.util.HashMap;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ApocalypseNativeMissileTest {
    @Test void upperAirCollisionUsesTheNativeTraceOnlyForPlayerApocalypseMissiles() throws Exception {
        var foreign = collisionProbe(CollisionBody.class);
        var apocalypse = collisionProbe(ApocalypseBody.class);
        var world = new Ceiling();
        for (var type : new Class<?>[] {foreign, apocalypse}) {
            var shell = (CollisionBody) type.getConstructor().newInstance();
            var height = type.getMethod("dnv$airspaceHitHeight", Ceiling.class);
            shell.owner = new PlayerOwner();
            shell.y = 318;
            assertEquals(320, height.invoke(shell, world));
            shell.y = 346.5;
            assertEquals(type == apocalypse ? 346 : 320, height.invoke(shell, world));
            shell.owner = new Object();
            assertEquals(320, height.invoke(shell, world), "AI and non-player projectiles keep native limits");
        }
    }

    private static Class<?> collisionProbe(Class<? extends CollisionBody> parent) throws Exception {
        var names = new HashMap<String, String>();
        NuclearRecoveryExecutionTest.map(names, "com/atsuishio/superbwarfare/entity/projectile/FastThrowableProjectile", CollisionBody.class);
        NuclearRecoveryExecutionTest.map(names, "net/minecraft/world/level/Level", Ceiling.class);
        return NuclearRecoveryExecutionTest.extract(
                "com/liucodex/doggiesnewvehicle/compat/mixin/DnvTurretShellLifecycleMixin",
                Set.of("dnv$airspaceHitHeight"), names, parent, false);
    }

    public static class CollisionBody {
        double y;
        Object owner;
        public double getY() { return y; }
    }
    public static class ApocalypseBody extends CollisionBody implements DnvUpperAirspaceProjectile {
        @Override public boolean admitsUpperAirspaceCollision() { return owner instanceof PlayerOwner; }
    }
    public static class PlayerOwner {}
    public static class Ceiling {
        public int getMaxBuildHeight() { return 320; }
    }

    @Test void alliedMissileLosesLockWhenRelationsChangeOrSourceUnloads() throws Exception {
        for (int failure = 0; failure < 3; failure++) {
            var f = new ExactApocalypseMissileFixture(); var source = new ExactApocalypseMissileFixture.AlliedTank();
            f.allied(source); for (int i=0;i<4;i++) f.tick(); assertFalse(f.missile.removed);
            if (failure == 0) f.target.eligible = false;
            else if (failure == 1) f.missile.world.source = null;
            else source.ready = false;
            f.tick(); assertTrue(f.missile.removed); assertEquals("none", f.missile.targetId);
            assertEquals(1, f.missile.world.cosmeticExplosions);
        }
    }
    @Test void nativeJavelinStartsAlongSlotAxisThenGraduallyUsesItsTurn() throws Exception {
        var f = new ExactApocalypseMissileFixture();
        Vec3 axis = f.missile.look;
        for (int i = 1; i <= 3; i++) {
            f.tick();
            assertEquals(0, f.missile.turns, "early guidance at " + i);
            assertTrue(f.missile.position.normalize().distanceTo(axis) < 1E-10);
            assertTrue(f.missile.velocity.normalize().distanceTo(axis) < 1E-10);
        }
        assertEquals(7.32, f.missile.position.length(), 1E-9);
        f.tick();
        assertEquals(1, f.missile.turns);
        assertEquals(2.25F, f.missile.lastTurnSpeed);
        assertFalse(f.missile.top);
        assertFalse(f.forceLoad());
    }

    @Test void closeOffAxisTargetCanTurnAfterClearanceWithoutRestartingTheNativeFlight() throws Exception {
        var f = new ExactApocalypseMissileFixture();
        f.target.position = new Vec3(0, 0, 3);
        for (int i = 0; i < 3; i++) f.tick();
        assertEquals(0, f.missile.turns);
        f.tick();
        assertEquals(1, f.missile.turns);
        assertEquals(2.25F, f.missile.lastTurnSpeed);
        assertEquals(-42.75F, f.missile.pitch, 1E-4F, "native turn must not snap downward on the first turn");
    }

    @Test void lostTargetDiscardsBeforeNativeCollisionWithOnlyCosmeticExplosion() throws Exception {
        var f = new ExactApocalypseMissileFixture();
        for (int i = 0; i < 4; i++) f.tick();
        assertEquals(1, f.missile.turns);
        f.target.eligible = false;
        int before = f.missile.f_19797_;
        f.tick();
        assertEquals("none", f.missile.targetId);
        assertEquals(1, f.missile.turns);
        assertTrue(f.missile.removed);
        assertEquals(1, f.missile.world.cosmeticExplosions);
        assertEquals(before, f.missile.f_19797_);
        assertFalse(f.forceLoad());
    }
}
