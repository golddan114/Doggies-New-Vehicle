package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApocalypseNativeShotTest {
    @TempDir Path temp;

    @Test void rocketAndMissileLocalFeedbackExistsOnlyForOneAcceptedWorldShot() throws Exception {
        for(int group : java.util.List.of(1,2)) for(int cause=0;cause<6;cause++) {
            var f=new ExactQueuedShotFixture(temp,true);var tank=(ApocalypseShotHost)f.vehicle;
            tank.selected=group;var gun=tank.getGunData(0);String weapon=tank.getGunName(0);
            int fireIndex=gun.fireIndex.get();
            if(cause==0)ExactQueuedShotFixture.Mod.Companion.accept=false;
            tank.fire(tank.player,null,group==2?f.target.id:null);
            assertTrue(ApocalypseShotHost.Network.feedback.isEmpty());
            if(cause==1)tank.passenger=null;
            if(cause==2)tank.lineClear=false;
            if(cause==3)f.level.accept=false;
            if(cause==4)tank.selected=0;
            if(cause==5)gun.ready=false;
            if(cause!=0){var work=ExactQueuedShotFixture.Mod.Companion.work.remove(0);work.run();work.run();}
            assertTrue(ApocalypseShotHost.Network.feedback.isEmpty(),"rejected feedback group="+group+" cause="+cause);
            assertEquals(0,tank.sound);assertEquals(0,gun.ammoWrites);
            ExactQueuedShotFixture.Mod.Companion.accept=true;tank.passenger=tank.player;tank.lineClear=true;
            f.level.accept=true;tank.selected=group;gun.ready=true;
            tank.fire(tank.player,null,group==2?f.target.id:null);
            var retry=ExactQueuedShotFixture.Mod.Companion.work.remove(0);retry.run();retry.run();
            assertEquals(1,ApocalypseShotHost.Network.feedback.size());
            assertEquals(new ApocalypseShotHost.Feedback(tank.player.id,tank.id,fireIndex,weapon),ApocalypseShotHost.Network.feedback.get(0));
            assertEquals(1,tank.sound);assertEquals(1,gun.ammoWrites);
        }
    }

    @Test void rejectedAndDelayedShotsStaySilentAndAcceptedRetryEmitsEffectsOnce() throws Exception {
        for (int cause = 0; cause < 6; cause++) {
            var f = new ExactQueuedShotFixture(temp, true); var tank = (ApocalypseShotHost) f.vehicle;
            if (cause == 0) ExactQueuedShotFixture.Mod.Companion.accept = false;
            tank.fire(tank.player, null, null);
            assertEquals(0, tank.sound, "queued sound cause=" + cause);
            assertEquals(0, tank.recoil);
            if (cause == 1) tank.passenger = null;
            if (cause == 2) tank.lineClear = false;
            if (cause == 3) f.level.accept = false;
            if (cause == 4) tank.selected = 1;
            if (cause == 5) tank.guns.get("MainL").ready = false;
            if (cause != 0) { Runnable work = ExactQueuedShotFixture.Mod.Companion.work.remove(0); work.run(); work.run(); }
            assertEquals(0, tank.sound); assertEquals(0, tank.recoil);
            assertEquals(0, tank.main); assertEquals(0, tank.guns.get("MainL").ammoWrites);
            ExactQueuedShotFixture.Mod.Companion.accept = true; tank.passenger = tank.player;
            tank.lineClear = true; f.level.accept = true; tank.selected = 0; tank.guns.get("MainL").ready = true;
            tank.fire(tank.player, null, null);
            Runnable retry = ExactQueuedShotFixture.Mod.Companion.work.remove(0); retry.run(); retry.run();
            assertEquals(1, tank.sound); assertEquals(1, tank.recoil); assertEquals(1, tank.guns.get("MainL").ammoWrites);
        }
    }

    @Test void onlyAcceptedHeAndRocketCommandsCarryTheCosmeticExplosionProfile()throws Exception {
        for(int style=0;style<3;style++) {
            var f=new ExactQueuedShotFixture(temp,true);var tank=(ApocalypseShotHost)f.vehicle;
            tank.selected=style==1?1:0;
            tank.getGunData(0).explosionDamage=style==0?0:120;
            tank.fire(tank.player,null,null);f.run();
            var projectile=f.level.entities.values().stream().filter(e->e!=tank&&e!=f.target).findFirst().orElseThrow();
            assertEquals(style,projectile.blastStyle);
            assertEquals(style==1,projectile.armorMarked);
            assertEquals(style==1?.20F:0F,projectile.armorFraction);
            assertEquals(style==1?.01F:.035F,projectile.percentFraction);
        }
    }

    @Test void acceptedApShellAndSlowMissileCarryDirectOnlyPenetration() throws Exception {
        var ap=new ExactQueuedShotFixture(temp,true);var tank=(ApocalypseShotHost)ap.vehicle;
        tank.guns.get("MainL").selectedAmmoType.set(1);
        tank.fire(tank.player,null,null);ap.run();
        var shell=ap.level.entities.values().stream().filter(e->e!=tank&&e!=ap.target).findFirst().orElseThrow();
        assertEquals(.60F,shell.armorFraction);
        assertEquals(.05F,shell.percentFraction);
        var missileFixture=new ExactQueuedShotFixture(temp,true);var missileTank=(ApocalypseShotHost)missileFixture.vehicle;
        missileTank.selected=2;
        missileTank.fire(missileTank.player,null,missileFixture.target.id);missileFixture.run();
        var missile=missileFixture.level.entities.values().stream()
                .filter(e->e!=missileTank&&e!=missileFixture.target).findFirst().orElseThrow();
        assertEquals(.70F,missile.armorFraction);
        assertEquals(.03F,missile.percentFraction);
    }

    @Test void exactNativeQueueFiresAlternatingGunsOnlyAfterActualWorldAcceptance() throws Exception {
        var f = new ExactQueuedShotFixture(temp, true);
        var tank = (ApocalypseShotHost) f.vehicle;
        tank.fire(tank.player, null, null);
        assertEquals(1, ExactQueuedShotFixture.Mod.Companion.work.size());
        tank.fire(tank.player, null, null);
        assertEquals(1, ExactQueuedShotFixture.Mod.Companion.work.size());
        f.run();
        assertEquals(1, f.level.admissions);
        assertEquals(1, tank.guns.get("MainL").ammoWrites);
        assertEquals(0, tank.guns.get("MainR").ammoWrites);
        assertEquals(1, tank.main);
        f.level.now += 10;
        tank.fire(tank.player, null, null);
        f.level.accept = false;
        f.run();
        assertEquals(0, tank.guns.get("MainR").ammoWrites);
        assertEquals(1, tank.main);
        f.level.accept = true;
        tank.fire(tank.player, null, null);
        f.run();
        assertEquals(1, tank.guns.get("MainR").ammoWrites);
        assertEquals(0, tank.main);
    }

    @Test void staleSeatWeaponTargetAndMuzzleRejectWithoutNativeAmmoConsumption() throws Exception {
        var f = new ExactQueuedShotFixture(temp, true);
        var tank = (ApocalypseShotHost) f.vehicle;
        tank.fire(new ApocalypseShotHost.Player(), null, null);
        tank.fire(tank.player, "Missiles", null);
        assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
        tank.fire(tank.player, null, null);
        tank.selected = 1;
        f.run();
        assertEquals(0, f.level.admissions);
        tank.fire(tank.player, null, null);
        tank.passenger = null;
        f.run();
        assertEquals(0, f.level.admissions);
        tank.passenger = tank.player;
        tank.fire(tank.player, null, null);
        tank.lineClear = false;
        f.run();
        assertEquals(0, f.level.admissions);
        tank.lineClear = true;
        tank.selected = 2;
        tank.fire(tank.player, null, null);
        assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
        tank.fire(tank.player, null, f.target.id);
        f.level.entities.remove(f.target.id);
        f.run();
        assertEquals(0, f.level.admissions);
        assertTrue(tank.guns.values().stream().allMatch(g -> g.ammoWrites == 0));
    }

    @Test void nativeQueueRefusalReleasesThePendingTrigger() throws Exception {
        var f = new ExactQueuedShotFixture(temp, true);
        var tank = (ApocalypseShotHost) f.vehicle;
        ExactQueuedShotFixture.Mod.Companion.accept = false;
        tank.fire(tank.player, null, null);
        assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
        ExactQueuedShotFixture.Mod.Companion.accept = true;
        tank.fire(tank.player, null, null);
        assertEquals(1, ExactQueuedShotFixture.Mod.Companion.work.size());
        f.run();
        assertEquals(1, f.level.admissions);
    }
}
