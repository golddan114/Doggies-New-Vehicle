package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NuclearSoundCuesTest {
    @Test void redAlertAlarmFiresAtCradleArrivalBeforeArmsExtendAndNeverRepeatsAtReady(){
        var cycle=new SiloCycle(NuclearRules.DEFAULT);var ready=new NuclearReadyCue();
        assertFalse(ready.observe(cycle.snapshot()));cycle.tick(true);
        var heard=new ArrayList<Integer>();
        for(int i=0;i<=6000;i++){
            var state=cycle.snapshot();
            if(ready.observe(state)){
                heard.add(state.remaining());
                assertEquals(SiloCycle.Phase.RELOADING,state.phase());
                assertEquals(NuclearMechanicalTimeline.RACK_ARRIVED_FRAME,NuclearMechanicalTimeline.frame(state.phase(),state.remaining(),NuclearRules.DEFAULT));
                assertTrue(NuclearMechanicalTimeline.frame(state.phase(),state.remaining(),NuclearRules.DEFAULT)<1411);
            }
            assertFalse(ready.observe(state));cycle.tick(true);
        }
        assertEquals(List.of(800),heard);
        assertTrue(cycle.request(java.util.UUID.randomUUID(),new SiloCycle.Target(1,64,1),true,20_000_000));
        assertFalse(ready.observe(cycle.snapshot()));
        var loaded=new NuclearReadyCue();
        assertFalse(loaded.observe(new SiloCycle.Snapshot(SiloCycle.Phase.RELOADING,800,null,null)));
        assertFalse(loaded.observe(new SiloCycle.Snapshot(SiloCycle.Phase.READY,0,null,null)));
        cycle.reset();assertFalse(ready.observe(cycle.snapshot()));cycle.tick(true);
        while(cycle.snapshot().remaining()>800){cycle.tick(true);assertEquals(cycle.snapshot().remaining()==800,ready.observe(cycle.snapshot()));}
    }
    @Test void firstLoadedTickCrossingArrivalStillCuesButAlreadyArrivedDoesNot(){
        var before=new SiloCycle.Snapshot(SiloCycle.Phase.RELOADING,801,null,null);
        var after=new SiloCycle.Snapshot(SiloCycle.Phase.RELOADING,800,null,null);
        assertTrue(new NuclearReadyCue().observe(before,after));
        assertFalse(new NuclearReadyCue().observe(after,after));
        var arrived=NuclearRackMotion.tail(NuclearMechanicalTimeline.RACK_ARRIVED_FRAME,0);
        var supported=NuclearRackMotion.tail(NuclearMechanicalTimeline.READY_FRAME,0);
        assertEquals(arrived.x,supported.x,1e-5);assertEquals(arrived.y,supported.y,1e-5);assertEquals(arrived.z,supported.z,1e-5);
    }
    @Test void globalVoiceWaitsForReloadAndEnergyAndFiresOnlyOncePerCycle(){
        var cycle=new SiloCycle(NuclearRules.DEFAULT);var cue=new NuclearReadyCue();
        for(int i=0;i<=6000;i++){
            var before=cycle.snapshot();cycle.tick(true);
            assertFalse(cue.announce(before,cycle.snapshot(),false));
        }
        assertEquals(SiloCycle.Phase.READY,cycle.snapshot().phase());
        assertTrue(cue.announce(cycle.snapshot(),cycle.snapshot(),true));
        assertFalse(cue.announce(cycle.snapshot(),cycle.snapshot(),true));
        assertFalse(cue.announce(cycle.snapshot(),cycle.snapshot(),false));
        assertFalse(cue.announce(cycle.snapshot(),cycle.snapshot(),true),"Power cycling must not spam the map");
        var before=cycle.snapshot();cycle.tick(false);assertFalse(cue.announce(before,cycle.snapshot(),true));
        int heard=0;
        for(int i=0;i<=6000;i++){
            before=cycle.snapshot();cycle.tick(true);
            if(cue.announce(before,cycle.snapshot(),true)){
                heard++;assertEquals(SiloCycle.Phase.RELOADING,before.phase());
                assertEquals(SiloCycle.Phase.READY,cycle.snapshot().phase());
            }
        }
        assertEquals(1,heard);
    }
    @Test void readyVoiceSeedsSilentlyAfterLoadButDoesNotLoseNewCompletionOrLaterPower(){
        var ready=new SiloCycle.Snapshot(SiloCycle.Phase.READY,0,null,null);
        var lastTick=new SiloCycle.Snapshot(SiloCycle.Phase.RELOADING,1,null,null);
        var loaded=new NuclearReadyCue();assertFalse(loaded.announce(ready,ready,true));
        assertFalse(loaded.announce(ready,ready,false));assertFalse(loaded.announce(ready,ready,true));
        var unpowered=new NuclearReadyCue();assertFalse(unpowered.announce(ready,ready,false));
        assertTrue(unpowered.announce(ready,ready,true));assertFalse(unpowered.announce(ready,ready,true));
        assertTrue(new NuclearReadyCue().announce(lastTick,ready,true));
    }
    @Test void thirtySecondEarlyReadyPoseCannotBypassReloadOrConsumeALaunch(){
        var cycle=new SiloCycle(NuclearRules.DEFAULT);cycle.tick(true);
        for(int i=0;i<5400;i++)cycle.tick(true);
        for(int i=600;i>0;i--){
            assertEquals(SiloCycle.Phase.RELOADING,cycle.snapshot().phase());
            assertEquals(1681,NuclearMechanicalTimeline.frame(cycle.snapshot().phase(),cycle.snapshot().remaining(),NuclearRules.DEFAULT));
            assertFalse(cycle.request(java.util.UUID.randomUUID(),new SiloCycle.Target(1,64,1),true,20_000_000));
            assertFalse(cycle.tick(true));
        }
        assertEquals(SiloCycle.Phase.READY,cycle.snapshot().phase());
        assertTrue(cycle.request(java.util.UUID.randomUUID(),new SiloCycle.Target(1,64,1),true,20_000_000));
    }
    @Test void fiveMinuteReloadPlaysThreeCompleteMotionsOnceAndNeverRepeatsTheArrival(){
        var cues=new NuclearSoundCues();var cycle=new SiloCycle(NuclearRules.DEFAULT);cycle.tick(true);
        var heard=new ArrayList<NuclearSoundCues.Cue>();
        for(int i=0;i<=6000;i++){
            var state=cycle.snapshot();float frame=NuclearMechanicalTimeline.frame(state.phase(),state.remaining(),NuclearRules.DEFAULT);
            var cue=cues.observe(state.phase(),frame);if(cue!=null)heard.add(cue);
            assertNull(cues.observe(state.phase(),frame),"Same synchronization must not duplicate playback");
            cycle.tick(true);
        }
        assertEquals(List.of(NuclearSoundCues.Cue.TURNTABLE,NuclearSoundCues.Cue.COVER,NuclearSoundCues.Cue.LIFT),heard);
        for(var cue:heard)assertEquals(cue==NuclearSoundCues.Cue.TURNTABLE?5:cue==NuclearSoundCues.Cue.COVER?15:20,(cue.end-cue.start)/30);
    }
    @Test void joiningOrJumpingPastStartDoesNotPlayLateAndCorrectionsCannotReplay(){
        var cues=new NuclearSoundCues();var reload=SiloCycle.Phase.RELOADING;
        assertNull(cues.observe(reload,220));assertNull(cues.observe(reload,500));
        assertNull(cues.observe(reload,779.5F));assertEquals(NuclearSoundCues.Cue.LIFT,cues.observe(reload,781));
        assertNull(cues.observe(reload,779.5F));assertNull(cues.observe(reload,781));
        assertNull(cues.observe(SiloCycle.Phase.EMPTY,1));
        assertNull(cues.observe(reload,29.5F));assertEquals(NuclearSoundCues.Cue.TURNTABLE,cues.observe(reload,31));
        assertFalse(NuclearSoundCues.Cue.LIFT.active(SiloCycle.Phase.CLOSING,900));
        assertFalse(NuclearSoundCues.Cue.LIFT.active(reload,1384));
    }
    @Test void previousV15SnapshotPreservesLaunchIdentityAndScalesRemainingDurationOnlyOnce(){
        var id=java.util.UUID.randomUUID();var target=new SiloCycle.Target(1,70,3);
        for(var phase:List.of(SiloCycle.Phase.OPENING,SiloCycle.Phase.DEPARTING,SiloCycle.Phase.CLOSING)){
            int old=phase==SiloCycle.Phase.DEPARTING?100:160;
            int duration=new SiloCycle(NuclearRules.DEFAULT).duration(phase);
            var tag=SiloCycleCodec.write(new SiloCycle.Snapshot(phase,old/2,id,target));tag.putInt("Schema",3);
            var restored=SiloCycleCodec.read(tag);
            assertEquals((int)Math.ceil(duration*.5),restored.remaining());assertEquals(id,restored.launch());assertEquals(target,restored.target());
            assertEquals(restored,SiloCycleCodec.read(SiloCycleCodec.write(restored)));
        }
    }
}
