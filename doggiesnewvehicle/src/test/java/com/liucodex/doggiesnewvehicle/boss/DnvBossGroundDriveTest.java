package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.core.DnvGroundDrive;

import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore.ActionIntent;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore.DecisionReason;
import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore.TacticalState;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DnvBossGroundDriveTest {
    private static final DnvGroundDrive.Terrain CLEAR = (yaw, distance) -> true;

    private static ActionIntent intent(TacticalState state) {
        return new ActionIntent(state, DecisionReason.PATROL_DESTINATION, 0, 0, false, false);
    }

    private static DnvGroundDrive.Input input(TacticalState state, float yaw, float goal, double x, double z) {
        return new DnvGroundDrive.Input(intent(state), yaw, goal, x, z, 40, true, false, false);
    }

    @Test
    void legacyThirtyTwoDegreeCliffIsReplacedWithContinuousSlowdown() {
        // The old production policy is retained as tactical intent; the boss executor smooths it.
        var core = new DnvAutonomyCore(30, 40);
        var before = core.decideMovement(new DnvAutonomyCore.SensorSnapshot(
                false, false, true, true, false, 50, 0, 31.99F));
        var after = core.decideMovement(new DnvAutonomyCore.SensorSnapshot(
                false, false, true, true, false, 50, 0, 32.01F));
        assertEquals(1, before.throttle());
        assertEquals(0, after.throttle());
        var a = new DnvGroundDrive().command(input(TacticalState.APPROACHING, 0, 31.99F, 0, 0), CLEAR);
        var b = new DnvGroundDrive().command(input(TacticalState.APPROACHING, 0, 32.01F, 0, 0), CLEAR);
        assertTrue(a.throttle() > 0.4F);
        assertEquals(a.throttle(), b.throttle(), 0.001F);
    }

    @Test
    void staleTacticalSteeringDoesNotDelayFreshHeadingCorrection() {
        var drive = new DnvGroundDrive();
        assertTrue(drive.command(input(TacticalState.APPROACHING, 0, 25, 0, 0), CLEAR).steering() > 0);
        assertTrue(drive.command(input(TacticalState.APPROACHING, 24, 20, 0, 0.1), CLEAR).steering() < 0);
    }

    @Test
    void startsSmoothlyReachesConfiguredSpeedAndStopsWithoutCreep() {
        float power = 0;
        double zSpeed = 0, previous = 0;
        for (int tick = 0; tick < 100; tick++) {
            power = DnvBossGroundDrive.powerStep(power, 1);
            zSpeed = DnvBossGroundDrive.velocity(0, zSpeed, 0, power, 0.22).z();
            assertTrue(zSpeed >= previous && zSpeed - previous <= 0.008001);
            previous = zSpeed;
        }
        assertEquals(0.22, zSpeed, 1e-6);
        double stoppingDistance = 0;
        for (int tick = 0; tick < 40; tick++) {
            power = DnvBossGroundDrive.powerStep(power, 0);
            zSpeed = DnvBossGroundDrive.velocity(0, zSpeed, 0, power, 0.22).z();
            stoppingDistance += zSpeed;
        }
        assertEquals(0, power);
        assertEquals(0, zSpeed, 1e-9);
        assertTrue(stoppingDistance < 2.0D, "Look-ahead must exceed braking distance");
    }

    @Test
    void reversalPassesNeutralBeforeApplyingReversePower() {
        float power = 1;
        boolean neutral = false;
        for (int tick = 0; tick < 70; tick++) {
            power = DnvBossGroundDrive.powerStep(power, -0.65F);
            if (power == 0) neutral = true;
            if (power < 0) assertTrue(neutral);
        }
        assertEquals(-0.65F, power, 1e-6);
        assertEquals(DnvBossGroundDrive.yawStep(1, 0.65F, 0.72F),
                DnvBossGroundDrive.yawStep(1, -0.65F, 0.72F));
    }

    @Test
    void patrolArrivalReducesThrottleBeforeDestinationIsCleared() {
        var in = new DnvGroundDrive.Input(intent(TacticalState.PATROLLING), 0, 0,
                0, 0, 2.6D, true, false, false);
        assertTrue(new DnvGroundDrive().command(in, CLEAR).throttle() < 0.03F);
    }

    @ParameterizedTest
    @EnumSource(value = TacticalState.class, names = {"HOLDING", "LOCKED", "FIRE_HOLD", "SEARCHING", "IDLE"})
    void stopStatesOverrideAndClearAnyAvoidance(TacticalState state) {
        var drive = new DnvGroundDrive();
        drive.command(input(TacticalState.APPROACHING, 0, 0, 0, 0), (yaw, distance) -> distance < 0);
        var stop = drive.command(input(state, 0, 0, 0, 0), (yaw, distance) -> { fail("Stopped vehicle probed terrain"); return false; });
        assertEquals(0, stop.throttle());
        assertEquals(0, stop.steering());
        assertEquals(1, drive.command(input(TacticalState.APPROACHING, 0, 0, 0, 0), CLEAR).throttle());
    }

    @Test
    void hardHoldAndAirborneNeverCreateAvoidanceMovement() {
        var hold = new ActionIntent(TacticalState.HOLDING, DecisionReason.HOLD_ORDER, 0, 0, true, true);
        assertTrue(new DnvGroundDrive().command(new DnvGroundDrive.Input(
                hold, 0, 0, 0, 0, 0, true, false, false), CLEAR).brake());
        var air = new DnvGroundDrive().command(new DnvGroundDrive.Input(
                intent(TacticalState.APPROACHING), 0, 40, 0, 0, 50, false, false, false),
                (yaw, distance) -> { fail("Airborne terrain scan"); return false; });
        assertEquals(0, air.throttle());
        assertEquals(0, air.steering());
    }

    @Test
    void choosesOpenSideAndDoesNotFlipWhileTurning() {
        var drive = new DnvGroundDrive();
        for (int tick = 0; tick < 80; tick++) {
            var control = drive.command(input(TacticalState.APPROACHING, tick * 0.5F, 0, 0, 0),
                    (yaw, distance) -> yaw > 60);
            assertTrue(control.steering() > 0, "The chosen detour must remain stable");
            assertTrue(control.throttle() >= 0);
        }
    }

    @Test
    void reversesOnlyIntoVerifiedSpaceAndStopsWhenThatSpaceCloses() {
        var drive = new DnvGroundDrive();
        var reverse = drive.command(input(TacticalState.APPROACHING, 0, 0, 0, 0), (yaw, distance) -> distance < 0);
        assertTrue(reverse.throttle() < 0);
        DnvGroundDrive.Control stop = reverse;
        for (int tick = 0; tick < 4; tick++) {
            stop = drive.command(input(TacticalState.APPROACHING, 0, 0, 0, -0.1 * tick), (yaw, distance) -> false);
        }
        assertEquals(0, stop.throttle());
        assertTrue(stop.brake());
    }

    @Test
    void fullyBlockedOrUnsupportedSpaceNeverProducesBlindMotion() {
        var drive = new DnvGroundDrive();
        AtomicInteger calls = new AtomicInteger();
        for (int tick = 0; tick < 40; tick++) {
            var control = drive.command(input(TacticalState.APPROACHING, 0, 0, 0, 0), (yaw, distance) -> { calls.incrementAndGet(); return false; });
            assertEquals(0, control.throttle());
            assertEquals(0, control.steering());
            assertTrue(control.brake());
        }
        assertEquals(40, calls.get(), "At most four local directions once per four ticks");
    }

    @Test
    void stoppedHullDoesNotAdvanceTracksAndReverseTravelHasCorrectSign() {
        assertEquals(0, DnvBossGroundDrive.forwardTravel(0, 0, 45));
        assertEquals(-0.1, DnvBossGroundDrive.forwardTravel(0, -0.1, 0), 1e-8);
        assertEquals(0, DnvBossGroundDrive.forwardTravel(0.1, 0, 0), 1e-8);
        // Old code used requestedSpeed=power*MAX_FORWARD_SPEED even after a wall stopped the hull.
        assertNotEquals(0, 1.0D * 0.22D);
    }

    @Test
    void finiteWallSimulationGoesAroundInsteadOfPushingAgainstIt() {
        var drive = new DnvGroundDrive();
        double x = 0, z = 0, vx = 0, vz = 0;
        float yaw = 0, power = 0, steering = 0;
        for (int tick = 0; tick < 900; tick++) {
            final double px = x, pz = z;
            float goal = (float) Math.toDegrees(Math.atan2(x, 45 - z));
            var control = drive.command(input(TacticalState.APPROACHING, yaw, goal, x, z), (heading, distance) -> {
                for (int i = 1; i <= 3; i++) {
                    double tx = px - Math.sin(Math.toRadians(heading)) * distance * i / 3;
                    double tz = pz + Math.cos(Math.toRadians(heading)) * distance * i / 3;
                    if (Math.abs(tx) < 8 && tz + 3 > 8 && tz - 3 < 9) return false;
                }
                return true;
            });
            power = control.brake() ? 0 : DnvBossGroundDrive.powerStep(power, control.throttle());
            steering = DnvBossGroundDrive.steeringStep(steering, control.steering());
            yaw += DnvBossGroundDrive.yawStep(steering, power, 0.72F);
            var velocity = DnvBossGroundDrive.velocity(vx, vz, yaw, power, 0.22D);
            vx = control.brake() ? 0 : velocity.x(); vz = control.brake() ? 0 : velocity.z();
            x += vx; z += vz;
            assertFalse(Math.abs(x) < 8 && z + 3 > 8 && z - 3 < 9, "Hull intersected wall at tick " + tick);
            if (z > 20) return;
        }
        fail("Vehicle did not get around finite wall: " + x + "," + z);
    }

    @Test
    void blockedFrontCannotCauseUnboundedBackingAway() {
        var drive = new DnvGroundDrive();
        int reversing = 0;
        for (int tick = 0; tick < 200; tick++) {
            var control = drive.command(input(TacticalState.APPROACHING, 0, 0, 0, -0.03 * tick),
                    (yaw, distance) -> distance < 0);
            if (control.throttle() < 0) reversing++;
        }
        assertEquals(40, reversing, "Two short attempts, then wait for a safe route");
    }

    @Test
    void turnConvergesWithoutOscillatingAndPhaseTwoRetainsItsMultiplier() {
        var drive = new DnvGroundDrive();
        float yaw = 0, steering = 0;
        for (int tick = 0; tick < 300; tick++) {
            var command = drive.command(input(TacticalState.APPROACHING, yaw, 90, tick * 0.05, 0), CLEAR);
            steering = DnvBossGroundDrive.steeringStep(steering, command.steering());
            yaw += DnvBossGroundDrive.yawStep(steering, 0, 0.72F);
        }
        assertEquals(90, yaw, 1.01);
        assertEquals(0, steering, 0.01);
        assertEquals(DnvBossGroundDrive.yawStep(1, 0, 0.72F) * 1.5F,
                DnvBossGroundDrive.yawStep(1, 0, 0.72F * 1.5F), 1e-6);
    }

    @Test
    void invalidGeometryStopsWithoutQueryingTheWorld() {
        var invalid = new DnvGroundDrive.Input(intent(TacticalState.APPROACHING),
                0, Float.NaN, 0, 0, 50, true, false, false);
        var control = new DnvGroundDrive().command(invalid, (yaw, distance) -> {
            fail("Invalid geometry reached terrain queries"); return false;
        });
        assertTrue(control.brake());
        assertEquals(0, control.throttle());
        assertEquals(0, control.steering());
    }

    @Test
    void persistentLackOfProgressSurvivesRoutineTerrainScans() {
        var drive = new DnvGroundDrive();
        boolean triedAlternateHeading = false;
        for (int tick = 0; tick < 25; tick++) {
            var control = drive.command(input(TacticalState.APPROACHING, 0, 0, 0, 0), CLEAR);
            if (control.steering() != 0) triedAlternateHeading = true;
        }
        assertTrue(triedAlternateHeading, "A clear sensor cannot erase twenty ticks of actual immobility");
    }
}
