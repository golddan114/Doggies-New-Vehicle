package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import static com.liucodex.doggiesnewvehicle.boss.MothershipEntityExecutionFixture.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MothershipCrashExecutionTest {
    @Test void repeatedForeignSupportWritesCannotStallDescentOrMoveTheLandedImpact() throws Exception {
        Base ship=create();ship.health=0;
        for(int i=0;i<500&&!ship.onGround;i++) {
            crash(ship);Vec3 committed=ship.position;
            ship.setPos(committed.x,committed.y+.4,committed.z);
            assertEquals(committed,ship.position);
        }
        assertEquals(MothershipCrash.LANDED,ship.crashSequence.state());Vec3 impact=ship.position;
        for(int i=0;i<MothershipCrash.LANDED_TICKS;i++) {
            ship.setPos(impact.x+1,impact.y+1,impact.z+1);crash(ship);
        }
        assertEquals(impact,ship.position);assertTrue(ship.removed);assertEquals(1,ship.world.impactEvents.size());
        Base restored=create();restore(restored,save(ship,100),90000);crash(restored);
        assertEquals(MothershipCrash.FINISHED,restored.crashSequence.state());assertTrue(restored.world.impactEvents.isEmpty());
    }
    @Test void nativeHealthMutationStartsBlackoutImmediatelyEvenWhenNativeHurtReportsFalse()throws Exception {
        Base ship=create();ship.health=10;ship.shotStarted=5;ship.entityData.throttle=1;
        assertTrue((boolean)ship.getClass().getMethod("hurt",Damage.class,float.class).invoke(ship,new Damage(new Actor()),20F));
        assertTrue(ship.wreck);assertEquals(0,ship.health);assertEquals(MothershipCrash.FALLING,ship.crashSequence.state());
        assertEquals(-1,ship.shotStarted);assertEquals(0,ship.entityData.throttle);
    }
    @Test void destructionDisarmsDescendsImpactsOnceAndReloadCannotReplayTheExplosion() throws Exception {
        Base ship=create();ship.health=-2000;ship.shotStarted=5;ship.entityData.throttle=1;
        ship.bossEvent.visible=true;ship.bossEvent.players.add(new Actor());
        Vec3 initial=ship.position;
        crash(ship);crash(ship);
        assertEquals(0,ship.health);assertEquals(-1,ship.shotStarted);assertEquals(0,ship.entityData.throttle);
        assertTrue(ship.wreck);
        assertFalse(ship.bossEvent.visible);assertTrue(ship.bossEvent.players.isEmpty());
        assertTrue(ship.position.y<initial.y);assertTrue(ship.pitch>0);assertTrue(ship.roll>0);
        assertTrue(ship.world.impactEvents.isEmpty());
        for(int i=0;i<60;i++)crash(ship);
        int age=ship.crashSequence.age();Base restored=create();restored.position=ship.position;
        restore(restored,save(ship,100),100_000);
        assertEquals(age,restored.crashSequence.age());assertEquals(MothershipCrash.FALLING,restored.crashSequence.state());
        for(int tick=0;tick<600&&!restored.onGround;tick++)crash(restored);
        assertTrue(restored.onGround);assertTrue(restored.world.impactEvents.isEmpty());assertFalse(restored.removed);
        assertTrue(restored.crashSequence.age()>=350&&restored.crashSequence.age()<=352);
        assertFalse(restored.world.fallingExplosions.isEmpty());
        assertTrue(restored.position.y>=restored.world.ground);assertEquals(MothershipCrash.LANDED,restored.crashSequence.state());
        for(int i=0;i<50;i++)crash(restored);
        assertEquals(1,restored.world.impactEvents.size());assertTrue(restored.removed);
        assertEquals(1,restored.getClass().getMethod("dnvWreckLifetimeTicks").invoke(restored));
        Base settled=create();restore(settled,save(restored,200),200_000);crash(settled);
        assertEquals(MothershipCrash.FINISHED,settled.crashSequence.state());assertTrue(settled.world.impactEvents.isEmpty());
        assertEquals(0,settled.health);assertTrue((boolean)settled.getClass().getMethod("dnvWreckReadyForCleanup").invoke(settled));
    }
    @Test void landedWreckRetainsItsRemainingDelayAcrossReloadAndOldLandedSavesCannotReexplode()throws Exception {
        Base ship=create();ship.health=0;
        while(!ship.onGround)crash(ship);
        for(int i=0;i<10;i++)crash(ship);
        assertEquals(10,ship.crashSequence.landedAge());assertFalse(ship.removed);assertTrue(ship.world.impactEvents.isEmpty());
        var saved=save(ship,100);Base loaded=create();restore(loaded,saved,90000);
        for(int i=0;i<9;i++)crash(loaded);
        assertFalse(loaded.removed);assertTrue(loaded.world.impactEvents.isEmpty());
        crash(loaded);assertTrue(loaded.removed);assertEquals(1,loaded.world.impactEvents.size());
        var legacy=saved.copy();legacy.getCompound("DnvMothership").putInt("Schema",3);
        Base old=create();restore(old,legacy,100000);crash(old);
        assertTrue(old.removed);assertTrue(old.world.impactEvents.isEmpty());
    }
    @Test void unavailableTerrainNeverMovesLoadsOrBlastsAndHasABoundedFallback() throws Exception {
        Base ship=create();ship.health=0;ship.world.loaded=false;Vec3 position=ship.position;
        for(int tick=0;tick<MothershipCrash.MAX_FALL_TICKS;tick++)crash(ship);
        assertEquals(position,ship.position);assertTrue(ship.removed);assertTrue(ship.world.impactEvents.isEmpty());
    }
    @Test void temporarilyMissingChunksPauseDescentInsteadOfCausingCatchupTeleport()throws Exception {
        Base ship=create();ship.health=0;
        for(int i=0;i<30;i++)crash(ship);
        Vec3 before=ship.position;int plan=ship.crashSequence.planAge();ship.world.loaded=false;
        for(int i=0;i<80;i++)crash(ship);
        assertEquals(before,ship.position);assertEquals(plan+80,ship.crashSequence.planAge());
        assertTrue(ship.world.impactEvents.isEmpty());ship.world.loaded=true;crash(ship);
        assertTrue(ship.position.y<before.y);assertTrue(before.y-ship.position.y<.3);
        while(!ship.removed)crash(ship);
        assertEquals(1,ship.world.impactEvents.size());assertTrue(ship.crashSequence.age()>=240);
    }
}
