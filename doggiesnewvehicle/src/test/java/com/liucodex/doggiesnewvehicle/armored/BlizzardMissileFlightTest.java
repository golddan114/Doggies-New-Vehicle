package com.liucodex.doggiesnewvehicle.armored;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BlizzardMissileFlightTest {
    @Test void RevisedSpeedStaysConstantThroughoutTurnsAndElevation() {
        for (float yaw : new float[] {-180, -90, 0, 90, 179}) for (float pitch : new float[] {-85, -45, 0, 45, 85})
            assertEquals(12, BlizzardMissileFlight.velocity(yaw, pitch).length() * 20, 1e-9);
        assertTrue(BlizzardMissileFlight.velocity(0, -45).y > 0);
        assertTrue(BlizzardMissileFlight.velocity(90, 0).x < 0);
    }
    @Test void eitherDistanceOrLifetimeEndsFlightAndWrappedTurnsTakeTheShortPath() {
        double travelled = 0;
        for (int tick = 1; tick <= 400; tick++) {
            travelled += Math.min(BlizzardMissileFlight.SPEED, BlizzardMissileFlight.DISTANCE - travelled);
            assertEquals(tick == 400, BlizzardMissileFlight.expired(tick, travelled));
        }
        assertEquals(240, travelled, 1e-9);
        assertFalse(BlizzardMissileFlight.expired(399, 30));
        assertTrue(BlizzardMissileFlight.expired(400, 30));
        assertEquals(-179, BlizzardMissileFlight.turn(179, -179));
        assertEquals(179, BlizzardMissileFlight.turn(-179, 179));
        assertEquals(6, BlizzardMissileFlight.turn(0, 160));
        assertFalse(BlizzardMissileFlight.validAngles(Float.NaN, 0));
        assertFalse(BlizzardMissileFlight.validAngles(0, Float.POSITIVE_INFINITY));
    }
}
