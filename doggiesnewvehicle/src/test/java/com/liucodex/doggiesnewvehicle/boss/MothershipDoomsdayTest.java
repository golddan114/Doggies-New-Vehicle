package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MothershipDoomsdayTest {
    private static final MothershipDoomsday.Pose POSE = new MothershipDoomsday.Pose(10, 70, -3, 65, -30);

    @Test void failureDissipatesForThirtyTicksWithNoPulseAndNoRestartReplay() {
        for(int failureAt:new int[]{20,180,259,260}) {
            var weapon=new MothershipDoomsday();weapon.begin(0);weapon.advance(failureAt-1,POSE);
            assertTrue(weapon.abort(failureAt,false));
            assertEquals(failureAt,weapon.abortStarted());
            for(int tick=failureAt;tick<failureAt+30;tick++) {
                var update=weapon.advance(tick,POSE);
                assertEquals(MothershipDoomsday.Phase.ABORTING,update.phase());
                assertEquals(0,update.hitMask()|update.soundMask());
                assertFalse(update.movementLocked());assertFalse(update.targetGlowing());
                assertFalse(weapon.begin(tick));
            }
            assertEquals(0,weapon.advance(failureAt,POSE).hitMask());
            assertEquals(0,weapon.consumedHits());
            var restored=new MothershipDoomsday();restored.restoreCooldown(1000,weapon.cooldownToSave(failureAt+20));
            assertEquals(-1,restored.abortStarted());assertEquals(0,restored.advance(1000,POSE).hitMask());
            assertTrue(weapon.begin(failureAt+30));
        }
    }

    @Test void failedLockDeathKeepsRetryDelayAndFiredBeamCannotEnterFailureState() {
        var weapon=new MothershipDoomsday();weapon.begin(0);weapon.advance(19,POSE);
        assertTrue(weapon.abort(20,true));assertFalse(weapon.ready(219));assertTrue(weapon.ready(220));
        weapon.begin(220);assertEquals(1,weapon.advance(480,POSE).hitMask());
        assertFalse(weapon.abort(481,false));weapon.cancel();
        assertEquals(-1,weapon.abortStarted());assertEquals(3339,weapon.cooldownToSave(481));
    }

    @Test
    void fourExactTicksFitTheFiveSecondFireSampleAndEscalateDirectAndAreaDamage() {
        var weapon = new MothershipDoomsday();
        assertTrue(weapon.begin(500));
        var ticks = new ArrayList<Long>();
        int direct = 0;
        int blast = 0;
        int ignitions = 0;
        for (long tick = 500; tick < 4500; tick++) {
            var update = weapon.advance(tick, POSE);
            for (int hit = 1; hit <= 4; hit++) {
                if ((update.hitMask() & (1 << (hit - 1))) == 0) continue;
                ticks.add(tick - 500);
                var event = weapon.hit(hit);
                assertEquals(hit * 250, event.directDamage());
                assertEquals(hit * 125, event.blastDamage());
                direct += event.directDamage();
                blast += event.blastDamage();
                if (event.igniteGround()) { ignitions++; assertEquals(4, hit); }
                assertEquals(hit * 4, event.blastRadius());
            }
            assertEquals(0, weapon.advance(tick, POSE).hitMask());
        }
        assertEquals(List.of(260L, 290L, 320L, 360L), ticks);
        assertEquals(2500, direct);
        assertEquals(1250, blast);
        assertEquals(3750, direct + blast, "An exposed direct target also receives each additional blast");
        assertEquals(1, ignitions);
    }

    @Test
    void firingFreezesPositionYawAndPitchUntilFinalDamageTick() {
        var weapon = new MothershipDoomsday();
        weapon.begin(0);
        assertFalse(weapon.advance(259, POSE).movementLocked());
        var start = weapon.advance(260, POSE);
        assertTrue(start.movementLocked());
        var moved = new MothershipDoomsday.Pose(100, 90, 300, -150, 40);
        for (int tick = 261; tick <= 360; tick++) {
            var state = weapon.advance(tick, moved);
            assertTrue(state.movementLocked());
            assertEquals(POSE, state.firingPose());
        }
        assertFalse(weapon.advance(361, moved).movementLocked());
    }

    @Test
    void successiveFireStartsAreThreeMinutesApartIncludingBothPreparations() {
        var weapon = new MothershipDoomsday();
        assertTrue(weapon.begin(0));
        assertFalse(weapon.begin(1));
        assertEquals(1, weapon.advance(260, POSE).hitMask());
        assertEquals(3240, weapon.advance(360, POSE).cooldownRemaining());
        assertFalse(weapon.begin(360));
        assertFalse(weapon.begin(3599));
        var last = weapon.advance(3599, POSE);
        assertEquals(MothershipDoomsday.Phase.COOLDOWN, last.phase());
        assertEquals(1, last.cooldownRemaining());
        assertEquals(MothershipDoomsday.Phase.READY, weapon.advance(3600, POSE).phase());
        assertTrue(weapon.begin(3600));
        assertEquals(0, weapon.advance(3859, POSE).hitMask());
        assertEquals(1, weapon.advance(3860, POSE).hitMask());
        assertEquals(3600, 3860 - 260);
    }

    @Test
    void skippedTicksAndRollbackDoNotReplayAndInstancesAreIndependent() {
        var weapon = new MothershipDoomsday();
        weapon.begin(0);
        assertEquals(3, weapon.advance(300, POSE).hitMask());
        var rolledBack = weapon.advance(250, POSE);
        assertEquals(0, rolledBack.hitMask());
        assertTrue(rolledBack.movementLocked());
        assertEquals(POSE, rolledBack.firingPose());
        assertEquals(12, weapon.advance(365, POSE).hitMask());
        assertEquals(0, weapon.advance(500, POSE).hitMask());
        var other = new MothershipDoomsday();
        other.begin(0);
        assertEquals(15, other.advance(360, POSE).hitMask());
        assertEquals(4, weapon.consumedHits());
    }

    @Test
    void cancellationReleasesPoseDropsEventsAndPreservesSpentCooldown() {
        var weapon = new MothershipDoomsday();
        weapon.begin(0);
        weapon.advance(260, POSE);
        weapon.cancel();
        var cancelled = weapon.advance(320, POSE);
        assertFalse(cancelled.movementLocked());
        assertNull(cancelled.firingPose());
        assertEquals(0, cancelled.hitMask());
        assertFalse(weapon.begin(360));
        assertEquals(0, weapon.advance(359, POSE).hitMask());
        assertTrue(weapon.begin(3600));
        weapon.cancel();
        assertTrue(weapon.begin(3601), "Cancelling before any shot spends no cooldown");
    }

    @Test
    void groundIgnitionHasBoundedReadOnlyCandidatesInsideConfigurableBlastRadius() {
        for (double radius : new double[]{1, 16, 24, 32, 64}) {
            var weapon = new MothershipDoomsday(radius);
            assertEquals(radius, weapon.hit(4).blastRadius());
            assertEquals(32, weapon.fireSites().size());
            for (var site : weapon.fireSites()) assertTrue(Math.hypot(site.x(), site.z()) <= radius);
            assertThrows(UnsupportedOperationException.class, () -> weapon.fireSites().clear());
            for (int hit = 1; hit <= 3; hit++) {
                assertFalse(weapon.hit(hit).igniteGround());
                assertEquals(hit * 125, weapon.hit(hit).blastDamage());
                assertEquals(radius * hit / 4, weapon.hit(hit).blastRadius());
            }
        }
    }

    @Test
    void lockChargeFireCoolSegmentsMatchAudioAndGlowEndsAfterFiveSeconds() {
        var weapon = new MothershipDoomsday();
        weapon.begin(0);
        List<Long> sounds = new ArrayList<>();
        for (long tick = 0; tick <= 460; tick++) {
            var state = weapon.advance(tick, POSE);
            if (state.soundMask() != 0) sounds.add(tick);
            assertEquals(tick < 100, state.targetGlowing());
            assertEquals(tick >= 260 && tick <= 360, state.movementLocked());
            assertEquals(0, weapon.advance(tick, POSE).soundMask());
        }
        assertEquals(List.of(0L, 100L, 260L, 360L), sounds);
        var late = new MothershipDoomsday();
        late.begin(0);
        assertEquals(4, late.advance(260, POSE).soundMask(), "Late update plays only the current segment");
        late.cancel();
        assertFalse(late.advance(280, POSE).targetGlowing());
    }

    @Test void deadTargetDuringLockWaitsTenSecondsButLaterCancellationDoesNot() {
        var weapon = new MothershipDoomsday();
        assertTrue(weapon.begin(0));
        weapon.advance(40, POSE);
        assertTrue(weapon.abort(40,true));
        assertFalse(weapon.begin(239));
        assertTrue(weapon.begin(240));
        weapon.advance(340, POSE);
        weapon.cancel();
        assertTrue(weapon.begin(341));
    }

    @Test
    void invalidInputsCannotCreateDamageOrOverflowCooldown() {
        var weapon = new MothershipDoomsday();
        assertFalse(weapon.begin(-1));
        assertFalse(weapon.begin(Long.MAX_VALUE));
        assertEquals(0, weapon.advance(0, POSE).hitMask());
        assertThrows(IllegalArgumentException.class, () -> weapon.advance(0, null));
        assertThrows(IllegalArgumentException.class, () -> new MothershipDoomsday.Pose(0, Double.NaN, 0, 0, 0));
        for (double radius : new double[]{0, 65, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new MothershipDoomsday(radius));
        }
        assertThrows(IllegalArgumentException.class, () -> weapon.hit(0));
        assertThrows(IllegalArgumentException.class, () -> weapon.hit(5));
        assertEquals(0, MothershipDoomsday.hitsDue(Double.NaN));
        assertEquals(0, MothershipDoomsday.hitsDue(Double.POSITIVE_INFINITY));
    }

    @Test void directHitsAddEightyPercentOfMaximumHealthOnceAcrossFourPulses() {
        var weapon = new MothershipDoomsday();
        for (double health : new double[]{0, 20, 4000, 18750, 20000}) {
            double total = 0;
            for (int pulse = 1; pulse <= 4; pulse++) total += weapon.hit(pulse).directDamageAgainst(health);
            assertEquals(2500 + health * .8, total, .01);
        }
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(1000, weapon.hit(4).directDamageAgainst(invalid));
        assertTrue(Float.isFinite(weapon.hit(4).directDamageAgainst(Double.MAX_VALUE)));
    }

    @Test void delayedFirstDamageCannotShortenTheNextFireInterval() {
        var weapon = new MothershipDoomsday();
        assertTrue(weapon.begin(0));
        assertEquals(15, weapon.advance(1000, POSE).hitMask());
        assertFalse(weapon.begin(4339));
        assertTrue(weapon.begin(4340));
        assertEquals(0, weapon.advance(4599, POSE).hitMask());
        assertEquals(1, weapon.advance(4600, POSE).hitMask());
    }
}
