package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MothershipSystemsTest {
    private static final MothershipDoomsday.Pose POSE = new MothershipDoomsday.Pose(0, 80, 0, 30, 5);

    @Test
    void accelerationIsConvexMonotonicAndReachesOneBlockPerSecondAtFiveSeconds() {
        double last = 0;
        double lastDelta = 0;
        for (int tick = 1; tick <= 100; tick++) {
            double speed = MothershipFlight.speedAt(tick);
            assertTrue(speed > last && speed <= .05);
            assertTrue(speed - last > lastDelta);
            lastDelta = speed - last;
            last = speed;
        }
        assertEquals(0, MothershipFlight.speedAt(0));
        assertEquals(.05, last);
        assertEquals(.05, MothershipFlight.speedAt(Long.MAX_VALUE));
        assertTrue(MothershipFlight.speedAt(50) < .025, "Heavy initial motion must be slower than linear acceleration");
    }

    @Test
    void firingOverridesMotionAndRestartsTheFiveSecondRampWithoutCatchupTeleport() {
        var weapon = new MothershipDoomsday();
        var flight = new MothershipFlight();
        weapon.begin(0);
        assertEquals(0, flight.advance(0, true, weapon.advance(0, POSE)).blocksPerTick());
        assertEquals(.05, flight.advance(100, true, weapon.advance(100, POSE)).blocksPerTick());
        var stopped = flight.advance(260, true, weapon.advance(260, POSE));
        assertTrue(stopped.locked());
        assertEquals(POSE, stopped.fixedPose());
        assertEquals(0, stopped.blocksPerTick());
        assertEquals(stopped, flight.advance(259, true, weapon.advance(259, POSE)));
        assertTrue(flight.advance(360, true, weapon.advance(360, POSE)).locked());
        assertEquals(0, flight.advance(361, true, weapon.advance(361, POSE)).blocksPerTick());
        assertEquals(.05, flight.advance(461, true, weapon.advance(461, POSE)).blocksPerTick());
        assertEquals(0, flight.advance(462, false, weapon.advance(462, POSE)).blocksPerTick());
        assertEquals(0, flight.advance(463, true, weapon.advance(463, POSE)).blocksPerTick());
    }

    @Test
    void sixStationsHaveIndependentRatesAndTwinBarrelsEachDealFifty() {
        var guns = new MothershipArmament();
        int[] shots = new int[6];
        for (int tick = 0; tick < 100; tick++) {
            for (var station : MothershipArmament.Station.values()) {
                long now = tick;
                guns.tryFire(station, now, (s, barrel) -> { shots[s.ordinal()]++; return true; });
                assertEquals(0, guns.tryFire(station, now, (s, barrel) -> fail("Duplicate tick")));
            }
        }
        assertArrayEquals(new int[]{10, 10, 1, 1, 2, 2}, shots);
        assertEquals(50, MothershipArmament.Kind.TWIN_LASER.damage);
        assertTrue(MothershipArmament.Kind.TWIN_LASER.piercing);
        assertEquals(160, MothershipArmament.Kind.TWIN_LASER.range);
        assertEquals(96, MothershipArmament.Kind.HE_ROCKET.range);
        assertEquals(20, MothershipArmament.Kind.HE_ROCKET.damage);
        assertEquals(15, MothershipArmament.Kind.HE_ROCKET.blastDamage);
        assertEquals(2, MothershipArmament.Kind.HE_ROCKET.blastRadius);
        assertEquals(2, guns.tryFire(MothershipArmament.Station.REAR_FORWARD, 1000, (s, b) -> true));
    }

    @Test
    void deniedAndPartialSalvosAreSafeAgainstReentryAndExceptions() {
        var guns = new MothershipArmament();
        var station = MothershipArmament.Station.REAR_FORWARD;
        assertEquals(0, guns.tryFire(station, 0, (s, b) -> false));
        assertEquals(1, guns.tryFire(station, 1, (s, b) -> {
            assertEquals(0, guns.tryFire(s, 1, (again, barrel) -> fail("Reentry")));
            return b == 0;
        }));
        assertEquals(0, guns.tryFire(station, 100, (s, b) -> fail("Partial salvo was already spent")));
        assertThrows(IllegalStateException.class, () -> guns.tryFire(station, 101, (s, b) -> { throw new IllegalStateException(); }));
        assertEquals(0, guns.tryFire(station, 102, (s, b) -> fail("Ambiguous effects must not repeat")));
        assertEquals(0, guns.tryFire(station, Long.MAX_VALUE, (s, b) -> fail("Overflow")));
        assertEquals(2, new MothershipArmament().tryFire(station, 1, (s, b) -> true));
    }

    @Test
    void pursuitRequiresAConfirmedOwnerWithin224AndCannotBeExtendedByRepeatedDamage() {
        var tactics = new MothershipTactics();
        UUID attacker = UUID.randomUUID();
        assertFalse(tactics.onAttacked(attacker, 0, 224.01, true));
        assertFalse(tactics.onAttacked(attacker, 0, 80, false));
        assertFalse(tactics.onAttacked(null, 0, 80, true));
        assertTrue(tactics.onAttacked(attacker, 0, 224, true));
        assertTrue(tactics.onAttacked(attacker, 399, 80, true));
        assertEquals(attacker, tactics.pursuitTarget(399, 80, 20, true));
        assertNull(tactics.pursuitTarget(400, 80, 20, true));
        assertTrue(tactics.onAttacked(attacker, 500, 80, true));
        assertNull(tactics.pursuitTarget(501, 225, 0, true));
        assertTrue(tactics.onAttacked(attacker, 600, 80, true));
        assertNull(tactics.pursuitTarget(601, 80, 65, true));
        assertFalse(MothershipTactics.shouldApproach(61, false));
        assertTrue(MothershipTactics.shouldApproach(61, true));
        assertFalse(MothershipTactics.shouldApproach(60, true));
    }

    @Test
    void horizontalCoverageAimArcsAndFixedAltitudeCeilingMatchTheShip() {
        assertTrue(MothershipTactics.inWeaponRange(40, 20, 0, 40));
        assertFalse(MothershipTactics.inWeaponRange(40.01, 20, 0, 40));
        assertFalse(MothershipTactics.inWeaponRange(20, 64.01, 0, 40));
        assertEquals(192, MothershipTactics.maximumBottomY(64, 320, 7));
        assertEquals(309, MothershipTactics.maximumBottomY(290, 320, 7));
        assertTrue(MothershipAim.doomsdayArc(-90));
        assertTrue(MothershipAim.doomsdayArc(90));
        assertFalse(MothershipAim.doomsdayArc(90.01));
        assertFalse(MothershipAim.mayBeginDoomsdayLock(180));
        double yaw = 0;
        for (int tick = 0; tick < 1200; tick++) yaw = MothershipAim.turnHull(yaw, 180);
        assertTrue(Math.abs(Math.abs(yaw) - 180) < .0001);
        assertTrue(MothershipAim.turret(MothershipArmament.Kind.TWIN_LASER, 0, -30).mayFire());
        assertFalse(MothershipAim.turret(MothershipArmament.Kind.TWIN_LASER, 0, 10).mayFire());
        assertFalse(MothershipAim.turret(MothershipArmament.Kind.HE_ROCKET, 0, -80).mayFire());
    }

    @Test
    void twoLifetimeDropBaysNeverRefillAndBlockedSkyUsesShipFallback() {
        var drops = new MothershipDrops();
        assertFalse(drops.deploy(0, () -> false));
        assertEquals(2, drops.remaining());
        assertTrue(drops.deploy(0, () -> {
            assertFalse(drops.deploy(0, () -> fail("Duplicate drop")));
            return true;
        }));
        var restored = new MothershipDrops();
        restored.restore(drops.spentMask());
        assertFalse(restored.deploy(0, () -> fail("Reloaded bay")));
        assertTrue(restored.deploy(1, () -> true));
        assertEquals(0, restored.remaining());
        assertEquals(MothershipDrops.Destination.BELOW_SHIP, MothershipDrops.destination(false));
        assertEquals(MothershipDrops.Destination.AROUND_TARGET, MothershipDrops.destination(true));
        restored.restore(-1);
        assertEquals(0, restored.remaining());
    }
}
