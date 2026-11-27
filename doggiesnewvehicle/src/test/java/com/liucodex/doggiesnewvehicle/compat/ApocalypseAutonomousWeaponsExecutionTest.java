package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApocalypseAutonomousWeaponsExecutionTest {
    @TempDir Path temp;
    @Test void blockedOrUnknownFriendlyLaneRejectsDirectShotsWithoutSpendingAndRecovers() throws Exception {
        var f=new ExactQueuedShotFixture(temp,true,true);var tank=(AlliedApocalypseShotHost)f.vehicle;
        tank.laneClear=false;assertFalse(tank.shoot(0));assertFalse(tank.shoot(1));
        tank.laneClear=null;assertFalse(tank.shoot(0));assertEquals(0,f.level.admissions);
        assertEquals(0,tank.guns.get("MainL").ammoWrites);assertEquals(0,tank.sound);assertEquals(0,tank.main);
        tank.laneClear=true;assertTrue(tank.shoot(0));assertEquals(1,f.level.admissions);
        assertTrue(tank.immediateLaneChecks>=5,"both the request and native admission inspect current geometry");
    }
    @Test void blockedMainLaneDoesNotDisableVlsOrFreezeApproachOutsideMainRange() throws Exception {
        var f=new ExactQueuedShotFixture(temp,true,true);var tank=(AlliedApocalypseShotHost)f.vehicle;
        tank.laneClear=false;for(int i=0;i<20;i++)tank.lock();assertTrue(tank.shoot(2));
        tank.enemy.position=tank.muzzle.add(210,0,0);
        assertEquals(Boolean.TRUE,tank.callAi("mainLaneClear",new Class<?>[]{ExactQueuedShotFixture.Entity.class},tank.enemy));
    }
    @Test void unreachableMainAndRocketsDoNotStarveLockedVerticalMissiles() throws Exception {
        var f=new ExactQueuedShotFixture(temp,true,true);var tank=(AlliedApocalypseShotHost)f.vehicle;
        tank.enemy.position=tank.muzzle.add(30,45,0);
        assertFalse((boolean)tank.callAi("reachable",new Class<?>[]{int.class,ExactQueuedShotFixture.Entity.class},0,tank.enemy));
        assertFalse((boolean)tank.callAi("reachable",new Class<?>[]{int.class,ExactQueuedShotFixture.Entity.class},1,tank.enemy));
        assertEquals(2,tank.callAi("select",new Class<?>[]{ExactQueuedShotFixture.Entity.class},tank.enemy));
        assertTrue((double)tank.callAi("combatDistance",new Class<?>[]{ExactQueuedShotFixture.Entity.class},tank.enemy)>100);
        for(int i=0;i<20;i++)tank.lock();
        assertFalse(tank.shoot(0));assertFalse(tank.shoot(1));assertTrue(tank.shoot(2));
        assertEquals(1,f.level.admissions);assertEquals(4,tank.guns.get("MainL").ammo.get());
    }

    @Test void mainBecomesReachableAfterPullingBackAndRocketsKeepTheirOwnLowerElevationLimit() throws Exception {
        var f=new ExactQueuedShotFixture(temp,true,true);var tank=(AlliedApocalypseShotHost)f.vehicle;
        tank.enemy.position=tank.muzzle.add(100,35,0);
        assertTrue((boolean)tank.callAi("reachable",new Class<?>[]{int.class,ExactQueuedShotFixture.Entity.class},0,tank.enemy));
        assertFalse((boolean)tank.callAi("reachable",new Class<?>[]{int.class,ExactQueuedShotFixture.Entity.class},1,tank.enemy));
        tank.enemy.position=tank.muzzle.add(80,5,0);
        assertTrue((boolean)tank.callAi("reachable",new Class<?>[]{int.class,ExactQueuedShotFixture.Entity.class},1,tank.enemy));
        assertEquals(24,(double)tank.callAi("combatDistance",new Class<?>[]{ExactQueuedShotFixture.Entity.class},tank.enemy),.001);
    }
    @Test void aiVirtualReserveSupportsBothAmmoChoicesWithoutRewritingLoadedRounds() throws Exception {
        var f=new ExactQueuedShotFixture(temp,true,true);var tank=(AlliedApocalypseShotHost)f.vehicle;
        tank.guns.values().forEach(g->g.ammo.set(0));var main=tank.guns.get("MainL");main.virtualAmmo.set(8);
        assertTrue((boolean)tank.callAi("hasSupply",new Class<?>[0]));
        tank.callAi("preferAmmo",new Class<?>[]{boolean.class},true);assertEquals(1,tank.preference);
        assertEquals(0,main.selectedAmmoType.get());assertEquals(8,main.virtualAmmo.get());
        tank.callAi("preferAmmo",new Class<?>[]{boolean.class},false);assertEquals(0,tank.preference);
        assertEquals(0,main.ammo.get());assertEquals(0,main.consumers.get(0).queries);
    }
    @Test void apOnlyInventoryAndMissingPreferredAmmoFallBackWithoutSwitchingLoadedRounds() throws Exception {
        var f=new ExactQueuedShotFixture(temp,true,true);var tank=(AlliedApocalypseShotHost)f.vehicle;
        tank.guns.values().forEach(g->g.ammo.set(0));var main=tank.guns.get("MainL");
        main.consumers.get(1).stock=3;
        assertTrue((boolean)tank.callAi("hasSupply",new Class<?>[0]));assertEquals(1,tank.preference);
        assertEquals(0,main.selectedAmmoType.get());assertEquals(3,main.consumers.get(1).stock);
        int checks=main.consumers.get(1).queries;
        for(int i=0;i<5;i++)tank.callAi("hasSupply",new Class<?>[0]);
        assertEquals(checks,main.consumers.get(1).queries);
        main.consumers.get(0).stock=5;main.consumers.get(1).stock=0;
        tank.callAi("preferAmmo",new Class<?>[]{boolean.class},true);assertEquals(0,tank.preference);
        f.level.now+=20;main.consumers.get(0).stock=0;
        assertFalse((boolean)tank.callAi("hasSupply",new Class<?>[0]));
        main.ammo.set(1);assertTrue((boolean)tank.callAi("hasSupply",new Class<?>[0]));assertEquals(1,main.ammo.get());
    }
    @Test void fiveNativeGunHandlesFireWithOneAttemptPerTickAndAcceptedOnlyEffects() throws Exception {
        var f=new ExactQueuedShotFixture(temp,true,true);var tank=(AlliedApocalypseShotHost)f.vehicle;
        for(int i=0;i<20;i++)tank.lock();
        assertTrue(tank.shoot(0));assertFalse(tank.shoot(1));
        f.level.now++;assertTrue(tank.shoot(1));
        f.level.now++;assertTrue(tank.shoot(2));
        f.level.now+=10;assertTrue(tank.shoot(0));
        f.level.now++;assertTrue(tank.shoot(1));
        assertEquals(5,f.level.admissions);assertEquals(5,tank.sound);assertEquals(5,tank.recoil);
        assertTrue(tank.guns.values().stream().allMatch(g->g.ammoWrites==1));
        assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
    }
    @Test void crossingTheAimWindowDoesNotFireUntilThePriorPoseIsAlsoAligned() throws Exception {
        var f = new ExactQueuedShotFixture(temp, true, true);
        var tank = (AlliedApocalypseShotHost) f.vehicle;
        tank.priorDirection = new Vec3(-1, 0, 0);
        for (int i = 0; i < 20; i++) tank.lock();
        assertFalse(tank.shoot(0));
        assertFalse(tank.shoot(2));
        assertEquals(0, f.level.admissions);
        assertEquals(4, tank.guns.get("MainL").ammo.get());

        tank.priorDirection = null;
        assertTrue(tank.shoot(0));
        f.level.now++;
        for (int i = 0; i < 19; i++) tank.lock();
        assertFalse(tank.shoot(2));
        tank.lock();
        assertTrue(tank.shoot(2));
        assertEquals(2, f.level.admissions);
    }

    @Test void worldRefusalAndReentrantCallDoNotAdvanceAmmoBarrelOrSound() throws Exception {
        var f=new ExactQueuedShotFixture(temp,true,true);var tank=(AlliedApocalypseShotHost)f.vehicle;
        f.level.accept=false;assertFalse(tank.shoot(0));assertEquals(0,tank.main);assertEquals(0,tank.sound);
        assertEquals(0,tank.guns.get("MainL").ammoWrites);
        f.level.now++;f.level.accept=true;f.level.duringAdmission=()->assertFalse(tank.shoot(0));
        assertTrue(tank.shoot(0));assertEquals(1,tank.guns.get("MainL").ammoWrites);assertEquals(1,tank.main);
    }
    @Test void nativeBeforeShotCallbackCannotFireAfterAuthorityPoseWeaponOrMuzzleChanges() throws Exception {
        for(int failure=0;failure<7;failure++){
            var f=new ExactQueuedShotFixture(temp,true,true);var tank=(AlliedApocalypseShotHost)f.vehicle;
            final int choice=failure;
            tank.guns.get("MainL").beforeShot=()->{
                switch(choice){
                    case 0->tank.active=false; case 1->tank.main=1; case 2->tank.muzzleClear=false;
                    case 3->tank.enemy.removed=true; case 4->tank.clearPose=false; case 5->tank.direction=new Vec3(0,0,1);
                    case 6->tank.laneClear=false;
                }
            };
            assertFalse(tank.shoot(0));assertEquals(0,f.level.admissions);assertEquals(0,tank.sound);
            assertEquals(0,tank.guns.get("MainL").ammoWrites);
        }
    }
    @Test void missileNeedsLockAndCooldownSurvivesSaveRestoreWithoutLockAuthority() throws Exception {
        var f=new ExactQueuedShotFixture(temp,true,true);var tank=(AlliedApocalypseShotHost)f.vehicle;
        for(int i=0;i<19;i++)tank.lock();assertFalse(tank.shoot(2));tank.lock();assertTrue(tank.shoot(2));
        var tag=new CompoundTag();tank.callAi("save",new Class<?>[]{CompoundTag.class},tag);
        tank.callAi("load",new Class<?>[]{CompoundTag.class},tag);
        assertFalse(tank.shoot(2));f.level.now+=40;assertFalse(tank.shoot(2));
        for(int i=0;i<20;i++)tank.lock();assertTrue(tank.shoot(2));
        tank.lineClear=false;f.level.now+=40;assertFalse(tank.shoot(0));
    }
}
