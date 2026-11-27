package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BossVlsNativeMissileTest {
    @Test void expiredMissileStopsBeforeAnyNativeFlightOrProximityFuse() throws Exception {
        var fixture = new ExactApocalypseMissileFixture(true);
        fixture.arm(6);
        var start = fixture.missile.position;
        fixture.missile.data.putLong("DnvTurretShellExpiry", 100);
        fixture.missile.world.now = 101;
        fixture.nativeTick();
        assertTrue(fixture.missile.removed);
        assertEquals(start, fixture.missile.position);
        assertEquals(0, fixture.missile.explosions);
        assertEquals(0, fixture.missile.world.cosmeticExplosions);
    }

    @Test void bothBossesClearTheHullBeforeNativeTurnRampsAndGuidanceTakesOver() throws Exception {
        for (String owner : new String[] {"Noise", "SilentHeaven"}) {
            var f = new ExactApocalypseMissileFixture(true);
            f.missile.position = new Vec3(0, 4, 0);
            f.missile.setXRot(-90);
            f.target.position = new Vec3(0, 0, 40);
            f.arm(6);
            int firstTurn = -1;
            for (int i = 0; i < 100 && firstTurn < 0; i++) {
                int turns = f.missile.turns;
                float pitch = f.missile.pitch;
                double height = f.missile.position.y;
                f.nativeTick();
                if (f.missile.turns > turns) {
                    if (firstTurn < 0) {
                        firstTurn = i;
                        assertTrue(height >= 11.5);
                        assertEquals(2.25F, f.missile.lastTurnSpeed);
                    }
                    assertTrue(Math.abs(f.missile.pitch - pitch) <= 6.0001F);
                } else if (firstTurn < 0) assertEquals(-90, f.missile.pitch);
                assertFalse(f.missile.top);
            }
            assertTrue(firstTurn >= 3 && firstTurn < 10, "short exit replaces the old fixed 24-tick climb");
            assertTrue(f.missile.data.getBoolean(DnvVlsFlight.ACTIVE), "missile must leave its hull-clearance phase");
            assertEquals(f.target.id.toString(), f.missile.targetId);
            assertEquals(0, f.missile.guideType);
        }
    }

    @Test void restoredTurnStageDoesNotEjectAgainAndLostTargetDiscardsWithoutNativeImpact() throws Exception {
        var f = new ExactApocalypseMissileFixture(true);
        f.missile.position = new Vec3(0, 16, 0);
        f.arm(6);
        f.missile.data.putInt("DnvVlsClimb", 5);
        f.missile.setXRot(-70);
        f.nativeTick();
        assertTrue(f.missile.data.getBoolean(DnvVlsFlight.ACTIVE));
        assertNotEquals(-90, f.missile.pitch);
        int before = f.missile.f_19797_;
        f.missile.world.target = null;
        f.nativeTick();
        assertTrue(f.missile.removed);
        assertEquals(1, f.missile.world.cosmeticExplosions);
        assertEquals(before, f.missile.f_19797_, "lost target cancels native movement and collision");
    }

    @Test void lossDuringEjectionDiscardsButForeignNativeJavelinRemainsUntouched() throws Exception {
        var owned = new ExactApocalypseMissileFixture(true);
        owned.arm(6);
        owned.missile.world.target = null;
        owned.nativeTick();
        assertTrue(owned.missile.removed);
        assertEquals(0, owned.missile.f_19797_);
        assertEquals(1, owned.missile.world.cosmeticExplosions);

        var foreign = new ExactApocalypseMissileFixture(true);
        foreign.missile.world.target = null;
        foreign.nativeTick();
        assertFalse(foreign.missile.removed);
        assertEquals(1, foreign.missile.f_19797_);
        assertEquals(0, foreign.missile.world.cosmeticExplosions);
    }
}
