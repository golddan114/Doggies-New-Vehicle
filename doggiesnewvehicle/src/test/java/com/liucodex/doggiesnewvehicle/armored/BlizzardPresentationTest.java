package com.liucodex.doggiesnewvehicle.armored;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BlizzardPresentationTest {
    @Test void paidMotionSharesThePoseClockAndCodecInsteadOfReadingInstantaneousFe() {
        var presentation=new BlizzardPresentation();
        for(int tick=0;tick<20;tick++){
            var pose=new BlizzardPresentation.Pose(tick,Vec3.ZERO,0,0,0,0,0,Vec3.ZERO,tick<10);
            presentation.accept(BlizzardPresentation.encode(pose,null));presentation.advance();
            double at=presentation.time(1);
            assertEquals(at<10,presentation.sample(1F).motionPowered());
        }
        assertFalse(presentation.sample(1F).motionPowered());
    }
    @Test void acceptedThreePerSecondLaserCuesPlayOncePerShotWithoutReplayingAfterTrackingGap() {
        var p=new BlizzardPresentation();var heard=new java.util.ArrayList<Long>();
        long shot=0;int phase=0;
        for(int tick=0;tick<70;tick++){
            int gap=phase==2?6:7;
            if(tick==shot+gap){shot=tick;phase=(phase+1)%3;}
            p.accept(pose(tick),new BlizzardPresentation.Pulse(shot,Vec3.ZERO,Vec3.ZERO,0));p.advance();
            var sound=p.pollSound();if(sound!=null)heard.add(sound.tick());
        }
        assertEquals(java.util.List.of(7L,14L,20L,27L,34L,40L,47L,54L,60L),heard);
        p.accept(pose(100),new BlizzardPresentation.Pulse(99,Vec3.ZERO,Vec3.ZERO,0));p.advance();assertNull(p.pollSound());
    }
    @Test void acceptedBeamsStayOnTheirWorldRaysFadeAfterReleaseAndNeverReplayOnTrackingGap() {
        var p = new BlizzardPresentation(); p.accept(pose(0), null);
        var first = new BlizzardPresentation.Laser(7, new Vec3(1,2,3), new Vec3(1,2,83));
        var second = new BlizzardPresentation.Laser(14, new Vec3(9,2,3), new Vec3(49,2,3));
        var tag = BlizzardPresentation.encode(pose(15), null);
        BlizzardPresentation.appendLasers(tag, java.util.List.of(first, second)); p.accept(tag);
        var shots = new java.util.ArrayList<BlizzardPresentation.Laser>(); p.lasers().forEach(shots::add);
        assertEquals(java.util.List.of(first, second), shots);
        p.acceptLaser(new net.minecraft.nbt.CompoundTag());
        p.accept(tag); // Duplicate / release / turning the hull cannot rewrite a paid ray.
        shots.clear();p.lasers().forEach(shots::add); assertEquals(java.util.List.of(first,second),shots);
        assertEquals(0, first.alpha(6)); assertEquals(1, first.alpha(7));
        assertEquals(.5F, first.alpha(19)); assertEquals(0, first.alpha(31));
        assertTrue(second.alpha(31)>0);
        p.accept(pose(40), null); assertFalse(p.lasers().iterator().hasNext());
        p.accept(pose(100), null); p.acceptLaser(tag.getList("Beams",10).getCompound(1));
        assertFalse(p.lasers().iterator().hasNext());
    }
    @Test void laserHistoryIsBoundedRejectsFutureInvalidAndOldShotsAndDoesNotBridgeDistinctRays() {
        var p = new BlizzardPresentation(); p.accept(pose(0), null);
        p.accept(pose(20), null);
        var shots = new java.util.ArrayList<BlizzardPresentation.Laser>();
        for(int i=1;i<=20;i++)shots.add(new BlizzardPresentation.Laser(i,Vec3.ZERO,new Vec3(i,0,0)));
        var tag=BlizzardPresentation.encode(pose(21),null);BlizzardPresentation.appendLasers(tag,shots);
        assertEquals(8,tag.getList("Beams",10).size());p.accept(tag);
        var accepted=new java.util.ArrayList<BlizzardPresentation.Laser>();p.lasers().forEach(accepted::add);
        assertEquals(8,accepted.size());assertEquals(new Vec3(1,0,0),accepted.get(0).end());
        var invalid=new net.minecraft.nbt.CompoundTag();invalid.putLong("Tick",22);invalid.putDouble("Ex",40);
        p.acceptLaser(invalid);invalid.putLong("Tick",20);invalid.putDouble("Ex",81);p.acceptLaser(invalid);
        invalid.putDouble("Ex",Double.NaN);p.acceptLaser(invalid);
        accepted.clear();p.lasers().forEach(accepted::add);assertEquals(8,accepted.size());
    }
    @Test void extendedPulseStaysVisibleForItsFull32TickFlightAndThenExpiresWithoutReplay(){
        var playback=new BlizzardPresentation();
        var pulse=new BlizzardPresentation.Pulse(10,Vec3.ZERO,new Vec3(0,0,8),BlizzardRules.RANGE);
        for(int tick=0;tick<=45;tick++){
            playback.accept(pose(tick),tick>=10?pulse:null);playback.advance();
            if(tick==40){assertTrue(playback.pulses().iterator().hasNext());assertEquals(new Vec3(0,0,248),pulse.point(41));}
        }
        assertNull(pulse.point(42));assertNull(pulse.point(43));
    }
    @Test void overlappingRoundsReceiveTheirOwnOlderImpactAndStaleSnapshotsCannotExtendThem(){
        var p=new BlizzardPresentation();p.accept(pose(0),null);
        var first=new BlizzardPresentation.Pulse(1,Vec3.ZERO,new Vec3(0,0,8),256);
        var second=new BlizzardPresentation.Pulse(11,new Vec3(2,0,0),new Vec3(0,0,8),256);
        var initial=BlizzardPresentation.encode(pose(12),second);
        BlizzardPresentation.appendPulses(initial,java.util.List.of(first,second));p.accept(initial);
        var update=BlizzardPresentation.encode(pose(25),second);
        BlizzardPresentation.appendPulses(update,java.util.List.of(new BlizzardPresentation.Pulse(1,first.origin(),first.velocity(),192),second));
        p.accept(update);
        var shots=new java.util.ArrayList<BlizzardPresentation.Pulse>();p.pulses().forEach(shots::add);
        assertEquals(2,shots.size());assertEquals(192,shots.get(0).distance());assertEquals(256,shots.get(1).distance());
        assertNull(shots.get(0).point(25));assertEquals(new Vec3(2,0,112),shots.get(1).point(25));
        p.accept(initial);shots.clear();p.pulses().forEach(shots::add);assertEquals(192,shots.get(0).distance());
        p.accept(BlizzardPresentation.encode(pose(26),first));shots.clear();p.pulses().forEach(shots::add);
        assertEquals(192,shots.get(0).distance(),"Even a newer packet must not undo a known impact");
        var many=new java.util.ArrayList<BlizzardPresentation.Pulse>();for(int tick=20;tick<50;tick++)many.add(new BlizzardPresentation.Pulse(tick,Vec3.ZERO,new Vec3(0,0,8),256));
        var bounded=BlizzardPresentation.encode(pose(51),null);BlizzardPresentation.appendPulses(bounded,many);
        assertEquals(8,bounded.getList("Pulses",10).size());
    }
    @Test void soundFollowsVisibleLaunchOnceWithoutLeadingTheInterpolatedClock() {
        for (int packetStride : new int[] {1, 2, 3}) {
            var p = new BlizzardPresentation();
            var shot = new BlizzardPresentation.Pulse(10, new Vec3(2, 3, 4), new Vec3(0, 0, 8), 48);
            int sounds = 0;
            for (int tick = 0; tick < 24; tick++) {
                if (tick % packetStride == 0) p.accept(pose(tick), tick >= 10 ? shot : null);
                p.advance();
                var sound = p.pollSound();
                if (sound != null) {
                    sounds++;
                    assertTrue(p.time(0) >= 10, "Sound cannot use the undelayed server clock");
                    assertTrue(p.time(0) - 10 <= 1.25, "At most one bounded playback step after launch");
                    assertEquals(shot.origin(), sound.origin());
                }
                assertNull(p.pollSound(), "Repeated ticks/duplicate packets never repeat the sound");
            }
            assertEquals(1, sounds);
        }
    }
    @Test void initialTrackingAndLongGapsDoNotReplayShotsAndImpactDoesNotSuppressAcceptedFire() {
        var p = new BlizzardPresentation();
        var shot = new BlizzardPresentation.Pulse(10, Vec3.ZERO, new Vec3(0, 0, 8), 0);
        p.accept(pose(11), shot); p.advance(); assertNull(p.pollSound());
        p.accept(pose(40), new BlizzardPresentation.Pulse(39, Vec3.ZERO, new Vec3(0, 0, 8), 48));
        p.advance(); assertNull(p.pollSound());
        int sounds = 0;
        for (int tick = 41; tick < 49; tick++) {
            p.accept(pose(tick), new BlizzardPresentation.Pulse(42, Vec3.ZERO, new Vec3(0, 0, 8), 0));
            p.advance();
            if (p.pollSound() != null) sounds++;
        }
        assertEquals(1, sounds, "An immediate wall impact still has one accepted fire sound");
    }
    private BlizzardPresentation.Pose pose(long tick) {
        return new BlizzardPresentation.Pose(tick, new Vec3(tick * .6, 1 + tick * .02, tick * .2),
                (float) (170 + tick * 3), 3, -2, (float) (-170 - tick * 4), -12, new Vec3(.6, .02, .2));
    }
    @Test void movingTurningMuzzleAndAcceptedShotShareTimeAcrossDelayedBatchDelivery() {
        var playback = new BlizzardPresentation();
        var fired = pose(10);
        var rig = BlizzardRig.INSTANCE;
        var anchor = rig.anchors.get("main_gun_muzzle");
        var matrix = fired.transform().mul(rig.local(anchor.group(), fired.turretYaw(), -fired.turretPitch()));
        Vec3 muzzle = BlizzardRig.point(matrix, anchor.position());
        Vec3 velocity = BlizzardRig.direction(matrix, anchor.direction()).scale(8);
        var shot = new BlizzardPresentation.Pulse(10, muzzle, velocity, 48);
        for (int tick = 0; tick <= 14; tick++) {
            playback.accept(BlizzardPresentation.encode(pose(tick), tick >= 10 ? shot : null));
            if (tick % 3 == 2) { playback.advance(); playback.advance(); playback.advance(); }
        }
        var atLaunch = playback.sample(10.0);
        var displayMatrix = atLaunch.transform().mul(rig.local(anchor.group(), atLaunch.turretYaw(), -atLaunch.turretPitch()));
        assertEquals(0, shot.point(10).distanceTo(BlizzardRig.point(displayMatrix, anchor.position())), 1e-9);
        assertEquals(0, velocity.normalize().distanceTo(BlizzardRig.direction(displayMatrix, anchor.direction())), 1e-9);
        assertEquals(muzzle.add(velocity.scale(2.5)), shot.point(12.5));
        assertNotEquals(shot.point(12.5), BlizzardRig.point(playback.sample(12.5).transform(), anchor.position()));
        assertNull(shot.point(9.9)); assertNull(shot.point(16));
        assertEquals(1, java.util.stream.StreamSupport.stream(playback.pulses().spliterator(), false).count());
    }
    @Test void reorderedPacketsFreezeRatherThanPredictAndLateImpactCapsTheExistingPulse() {
        var p = new BlizzardPresentation();
        for (int i = 10; i < 16; i++) { p.accept(pose(i), null); p.advance(); }
        p.accept(pose(11), null);
        for (int i = 0; i < 50; i++) p.advance();
        assertTrue(p.time(1) <= 15); assertTrue(p.time(1) >= 13);
        p.accept(pose(16), new BlizzardPresentation.Pulse(15, Vec3.ZERO, new Vec3(0, 0, 8), 48));
        p.accept(pose(17), new BlizzardPresentation.Pulse(15, Vec3.ZERO, new Vec3(0, 0, 8), 3));
        var pulse = p.pulses().iterator().next();
        assertNotNull(pulse.point(15.25)); assertNull(pulse.point(15.4));
        p.accept(pose(100), null); assertFalse(p.pulses().iterator().hasNext());
        assertEquals(100, p.time(1));
    }
    @Test void angleInterpolationCrossesTheShortArcAndAllSamplesRemainBounded() {
        var p = new BlizzardPresentation();
        p.accept(new BlizzardPresentation.Pose(1, Vec3.ZERO, 179, 0, 0, -179, 0, Vec3.ZERO), null);
        p.accept(new BlizzardPresentation.Pose(2, new Vec3(1,0,0), -179, 2, 4, 179, -10, new Vec3(1,0,0)), null);
        var half = p.sample(1.5);
        assertEquals(180, half.yaw()); assertEquals(-180, half.turretYaw());
        assertEquals(.5, half.position().x); assertEquals(2, half.roll());
        assertEquals(-5, half.turretPitch());
    }
}
