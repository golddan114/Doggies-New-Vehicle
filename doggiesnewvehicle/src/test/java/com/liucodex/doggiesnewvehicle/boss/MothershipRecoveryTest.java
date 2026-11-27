package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MothershipRecoveryTest {
    private static final MothershipDoomsday.Pose POSE=new MothershipDoomsday.Pose(0,84,0,0,0);
    @Test void reloadAtEachDamageBoundaryCancelsEventsAndKeepsRecoveryWithoutReplay() {
        for(int age:new int[]{0,99,100,259,260,261,290,320,360,361,1000,3599,3600}) {
            var before=new MothershipDoomsday();assertTrue(before.begin(100));
            if(age>=260)before.advance(360,POSE);
            before.advance(100+age,POSE);
            long remaining=before.cooldownToSave(100+age);
            var after=new MothershipDoomsday();after.restoreCooldown(2000,remaining);
            assertEquals(0,after.advance(2000,POSE).hitMask());assertFalse(after.advance(2001,POSE).movementLocked());
            if(age<260 || age>=3600){assertEquals(0,remaining);assertTrue(after.begin(2001));}
            else if(remaining==1){assertFalse(after.begin(2000));assertTrue(after.begin(2001));}
            else {assertFalse(after.begin(2001));assertEquals(0,after.advance(2000+remaining,POSE).hitMask());assertTrue(after.begin(2000+remaining));}
        }
    }
    @Test void secondSaveDuringRecoveredCooldownDoesNotRefundTheShot() {
        var original=new MothershipDoomsday();original.begin(0);original.advance(260,POSE);
        var restored=new MothershipDoomsday();restored.restoreCooldown(1000,original.cooldownToSave(260));
        restored.cancel();restored.cancel(); // Repeated unload/clear callbacks must not refund a restored shot.
        assertEquals(3330,restored.cooldownToSave(1010));
        var twice=new MothershipDoomsday();twice.restoreCooldown(2000,restored.cooldownToSave(1010));assertFalse(twice.begin(5329));assertTrue(twice.begin(5330));
    }
    @Test void secondaryRecoveryNeverReplaysPartialTwinSalvosOrOverdueBursts() {
        var armament=new MothershipArmament();int[] actual={0};
        assertThrows(IllegalStateException.class,()->armament.tryFire(MothershipArmament.Station.REAR_AFT,10,(station,barrel)->{
            actual[0]++;if(barrel==1)throw new IllegalStateException();return true;
        }));
        var restored=new MothershipArmament();restored.restore(1000,armament.remaining(10));
        assertEquals(0,restored.tryFire(MothershipArmament.Station.REAR_AFT,1099,(s,b)->true));
        assertEquals(2,restored.tryFire(MothershipArmament.Station.REAR_AFT,1100,(s,b)->true));
        assertEquals(0,restored.tryFire(MothershipArmament.Station.REAR_AFT,1100,(s,b)->true));
        assertEquals(2,restored.tryFire(MothershipArmament.Station.REAR_AFT,9000,(s,b)->true));
    }
    @Test void twoBaySaveRestoresReservationAndFailedPlacementCanRetry() {
        var original=new MothershipDrops();assertFalse(original.deploy(0,()->false));assertEquals(2,original.remaining());
        assertThrows(IllegalStateException.class,()->original.deploy(1,()->{throw new IllegalStateException("ambiguous insert");}));
        var restored=new MothershipDrops();restored.restore(original.spentMask());
        assertFalse(restored.deploy(1,()->fail("must not replay reserved insertion")));
        assertTrue(restored.deploy(0,()->true));assertEquals(0,restored.remaining());
        assertFalse(restored.deploy(0,()->fail("third Noise")));
    }
    @Test void malformedSavedConsumptionAndCooldownFailClosed() {
        var drops=new MothershipDrops();drops.restore(-1);assertEquals(0,drops.remaining());
        var weapon=new MothershipDoomsday();weapon.restoreCooldown(10,Long.MAX_VALUE);assertFalse(weapon.begin(20));
        var armament=new MothershipArmament();armament.restore(10,new long[]{-1});
        for(var station:MothershipArmament.Station.values())assertFalse(armament.ready(station,11));
    }
    @Test void pursuitDeadlineDoesNotRenewAcrossHitsOrReload() {
        UUID target=UUID.randomUUID();var before=new MothershipTactics();assertTrue(before.onAttacked(target,10,90,true));
        assertTrue(before.onAttacked(target,300,90,true));assertEquals(110,before.remaining(300));
        var after=new MothershipTactics();after.restore(target,1000,before.remaining(300));
        assertEquals(target,after.pursuitTarget(1109,90,31,true));assertNull(after.pursuitTarget(1110,90,31,true));
    }
}
