package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DnvBossRunningGearTest {
    @Test
    void closedLoopPreservesPositionAndDirectionInBothDirections() {
        for (double distance : new double[] {-100.3, -0.01, 0, 1.2, 100.3}) {
            var first = DnvBossRunningGear.atDistance(distance);
            for (int cycle = -5; cycle <= 5; cycle++) {
                var other = DnvBossRunningGear.atDistance(
                        distance + cycle * DnvBossRunningGear.PERIMETER);
                assertEquals(first.y(), other.y(), 1e-10);
                assertEquals(first.z(), other.z(), 1e-10);
                assertEquals(Math.sin(first.pitchRadians()), Math.sin(other.pitchRadians()), 1e-10);
                assertEquals(Math.cos(first.pitchRadians()), Math.cos(other.pitchRadians()), 1e-10);
            }
        }
    }

    @Test
    void everyStraightArcJoinHasContinuousPositionAndTangent() {
        double line = DnvBossRunningGear.STRAIGHT;
        double arc = Math.PI * DnvBossRunningGear.PATH_RADIUS;
        for (double join : new double[] {0, line, line + arc, 2 * line + arc}) {
            var before = DnvBossRunningGear.atDistance(join - 1e-7);
            var after = DnvBossRunningGear.atDistance(join + 1e-7);
            assertTrue(Math.hypot(after.y() - before.y(), after.z() - before.z()) < 2.01e-7);
            assertEquals(Math.sin(before.pitchRadians()), Math.sin(after.pitchRadians()), 1e-6);
            assertEquals(Math.cos(before.pitchRadians()), Math.cos(after.pitchRadians()), 1e-6);
        }
    }

    @Test
    void travelIsUniformAndFollowsThePlateTangent() {
        double step = 1e-5;
        for (int i = 0; i < 1000; i++) {
            double distance = i * DnvBossRunningGear.PERIMETER / 1000;
            var start = DnvBossRunningGear.atDistance(distance);
            var end = DnvBossRunningGear.atDistance(distance + step);
            assertEquals(1, Math.hypot(end.y() - start.y(), end.z() - start.z()) / step, 1e-6);
            assertEquals(Math.sin(start.pitchRadians()), (end.y() - start.y()) / step, 1e-5);
            assertEquals(-Math.cos(start.pitchRadians()), (end.z() - start.z()) / step, 1e-5);
        }
    }

    @Test
    void storedPhaseCycleClosesAndOnePhaseAdvancesTwoSmallerShoes() {
        for (int i = 0; i < DnvBossRunningGear.SHOE_COUNT; i++) {
            var reference = DnvBossRunningGear.shoePose(i, 0);
            var cycle = DnvBossRunningGear.shoePose(i, DnvBossRunningGear.PHASE_PERIOD);
            var shifted = DnvBossRunningGear.shoePose(i, 1);
            var next = DnvBossRunningGear.shoePose((i + 2) % DnvBossRunningGear.SHOE_COUNT, 0);
            assertEquals(reference.y(), cycle.y(), 1e-10);
            assertEquals(reference.z(), cycle.z(), 1e-10);
            assertEquals(next.y(), shifted.y(), 1e-10);
            assertEquals(next.z(), shifted.z(), 1e-10);
        }
    }

    @Test
    void forwardReverseAndCounterRotationHaveTheExpectedGroundDirection() {
        int bottomShoe = 4;
        var rest = DnvBossRunningGear.shoePose(bottomShoe, 0);
        var forward = DnvBossRunningGear.shoePose(bottomShoe, .1);
        var reverse = DnvBossRunningGear.shoePose(bottomShoe, -.1);
        assertTrue(forward.z() < rest.z());
        assertTrue(reverse.z() > rest.z());
        assertEquals(rest.z(), (forward.z() + reverse.z()) / 2, 1e-10);
    }

    @Test
    void wheelSurfaceAndTrackHaveClearanceWithoutSinkingBelowGround() {
        assertTrue(DnvBossRunningGear.PATH_RADIUS - DnvBossRunningGear.SHOE_THICKNESS / 2
                - DnvBossRunningGear.WHEEL_RADIUS > .012);
        for (int i = 0; i < 2000; i++) {
            var pose = DnvBossRunningGear.atDistance(i * DnvBossRunningGear.PERIMETER / 2000);
            double yExtent = Math.abs(Math.cos(pose.pitchRadians())) * DnvBossRunningGear.SHOE_THICKNESS / 2
                    + Math.abs(Math.sin(pose.pitchRadians())) * DnvBossRunningGear.SHOE_LENGTH / 2;
            assertTrue(pose.y() - yExtent >= 0.0,
                    "Rigid plate corners must remain above the flat-ground plane");
        }
    }

    @Test
    void wheelSurfaceTravelMatchesTheBeltLinearDistance() {
        assertEquals(DnvBossRunningGear.PHASE_DISTANCE,
                Math.toRadians(DnvBossRunningGear.WHEEL_DEGREES_PER_PHASE)
                        * DnvBossRunningGear.WHEEL_RADIUS, 1e-7);
    }
}
