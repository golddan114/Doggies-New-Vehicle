package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DnvFiringRangeTest {
    @Test void highLaserAndBallisticTargetsCauseRetreatUntilTheBarrelCanReach() {
        for (double gravity : new double[]{0, .06}) {
            double speed = gravity == 0 ? 24 : 5;
            double distance = DnvFiringRange.minimum(60, speed, gravity, -10, 46, 30, 160);
            assertTrue(distance > 55 && distance < 90);
            double angle = Math.toRadians(46);
            assertEquals(60, distance * Math.tan(angle)
                - gravity * distance * distance / (2 * speed * speed * Math.pow(Math.cos(angle), 2)), 1E-8);
            var core = new DnvAutonomyCore(30, 40);
            assertEquals(DnvAutonomyCore.TacticalState.RETREATING, core.decideMovement(
                sensor(35, false, false), distance).state());
            assertEquals(DnvAutonomyCore.TacticalState.FIRE_HOLD, core.decideMovement(
                sensor(distance + 2, false, false), distance).state());
            assertEquals(DnvAutonomyCore.TacticalState.APPROACHING, core.decideMovement(
                sensor(distance + 15, false, false), distance).state());
            assertTrue(core.decideMovement(sensor(35, true, false), distance).hardStop());
            assertEquals(DnvAutonomyCore.TacticalState.LOCKED, core.decideMovement(sensor(35, false, true), distance).state());
        }
    }
    @Test void levelTargetsKeepOrdinaryBandAndUnreachableOrInvalidTargetsStayBounded() {
        assertEquals(30, DnvFiringRange.minimum(0, 5, .06, -10, 46, 30, 160));
        assertEquals(160, DnvFiringRange.minimum(1000, 5, .06, -10, 46, 30, 160));
        assertEquals(30, DnvFiringRange.minimum(Double.NaN, 5, .06, -10, 46, 30, 160));
        assertTrue(DnvFiringRange.minimum(-20, 24, 0, -10, 46, 30, 160) > 100);
    }
    private static DnvAutonomyCore.SensorSnapshot sensor(double distance, boolean hold, boolean locked) {
        return new DnvAutonomyCore.SensorSnapshot(hold, locked, true, true, false, distance, 0, 0);
    }
}
