package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real transformed SBW queue/admission; ammunition and the world remain explicit fixture ports. */
class BlizzardNativeShotExecutionTest {
    @TempDir Path temp;
    @Test void rejectedAndDelayedTvShotsStaySilentAndRetryEmitsEffectsExactlyOnce() throws Exception {
        for (int cause = 0; cause < 6; cause++) {
            var f = new ExactQueuedShotFixture(temp, false, false, true); var tank = (BlizzardShotHost) f.vehicle;
            if (cause == 0) ExactQueuedShotFixture.Mod.Companion.accept = false;
            tank.fire(tank.player, null);
            assertEquals(0, tank.sound, "queued sound cause=" + cause); assertEquals(0, tank.recoil);
            assertTrue(ApocalypseShotHost.Network.feedback.isEmpty());
            if (cause == 1) tank.passenger = null;
            if (cause == 2) tank.powered = false;
            if (cause == 3) f.level.accept = false;
            if (cause == 4) tank.lineClear = false;
            if (cause == 5) tank.gun.ready = false;
            if (cause != 0) { Runnable work = ExactQueuedShotFixture.Mod.Companion.work.remove(0); work.run(); work.run(); }
            assertEquals(0, tank.sound); assertEquals(0, tank.recoil); assertEquals(0, tank.gun.ammoWrites); assertEquals(0, tank.tvBegins);
            assertTrue(ApocalypseShotHost.Network.feedback.isEmpty(), "cause=" + cause);
            ExactQueuedShotFixture.Mod.Companion.accept = true; tank.passenger = tank.player; tank.powered = true;
            f.level.accept = true; tank.lineClear = true; tank.gun.ready = true;
            tank.fire(tank.player, null);
            Runnable retry = ExactQueuedShotFixture.Mod.Companion.work.remove(0); retry.run(); retry.run();
            assertEquals(1, tank.sound); assertEquals(1, tank.recoil); assertEquals(1, tank.gun.ammoWrites); assertEquals(1, tank.tvBegins);
            assertEquals(java.util.List.of(new ApocalypseShotHost.Feedback(tank.player.getUUID(),
                    tank.getUUID(), 0, "ClearingMissile")), ApocalypseShotHost.Network.feedback);
        }
    }
    @Test void cryoMissileUsesExactNativeTwentySecondReloadWithoutParallelTimers() throws Exception {
        var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(Path.of(
                "src/artillery/resources/data/doggievehicle/sbw/vehicles/blizzard.json")))
                .getAsJsonObject().getAsJsonObject("Weapons").getAsJsonObject("ClearingMissile");
        assertEquals(400, json.get("EmptyReloadTime").getAsInt());
        assertEquals(400, json.get("NormalReloadTime").getAsInt());
        assertEquals(1, json.get("Magazine").getAsInt());
        assertFalse(json.has("SeekWeaponInfo"));
        assertTrue(json.get("RPM").getAsInt() >= 120, "a rejected click must not start a 20-second client trigger timer");
        var nativeReload = new ExactM1ReloadSoundFixture(temp, false);
        var gun = new ExactM1ReloadSoundFixture.Data();
        gun.duration = json.get("EmptyReloadTime").getAsInt(); gun.magazine = json.get("Magazine").getAsInt();
        gun.reload.reloadStarter.requested = true;
        var vehicle = new ExactM1ReloadSoundFixture.Vehicle();
        nativeReload.tick(vehicle, gun);
        nativeReload.idle(vehicle, gun, gun.duration - 1);
        assertEquals(0, gun.completions); assertEquals(0, gun.ammo);
        nativeReload.tick(vehicle, gun);
        assertEquals(1, gun.completions); assertEquals(1, gun.ammo);
    }
    @Test void acceptedWorldShotAloneConsumesNativeAmmoAndAlternatesPortsAndKeepsPilotSeated() throws Exception {
        var f=new ExactQueuedShotFixture(temp,false,false,true);var tank=(BlizzardShotHost)f.vehicle;
        tank.fire(tank.player,null);assertEquals(1,ExactQueuedShotFixture.Mod.Companion.work.size());
        assertEquals(0,tank.begins);assertEquals(0,tank.gun.ammoWrites);
        f.run();assertEquals(1,tank.gun.ammoWrites);assertEquals(1,tank.begins);assertEquals(1,tank.port);
        assertSame(tank.player,tank.current.owner);assertNotNull(tank.current.target);assertEquals(1,tank.tvBegins);
        tank.targetValid=false;tank.fire(tank.player,null,null);f.run();
        assertEquals(2,tank.gun.ammoWrites);assertEquals(2,tank.begins);assertEquals(0,tank.port);
    }
    @Test void staleSeatNoPowerObstructionWorldRejectionAndDuplicateQueueHaveNoConsumptionOrPortAdvance() throws Exception {
        for(int cause=0;cause<6;cause++) {
            var f=new ExactQueuedShotFixture(temp,false,false,true);var tank=(BlizzardShotHost)f.vehicle;
            tank.fire(tank.player,null);assertEquals(1,ExactQueuedShotFixture.Mod.Companion.work.size());
            if(cause==0)tank.passenger=null;
            if(cause==1)tank.powered=false;
            if(cause==2)tank.lineClear=false;
            if(cause==3)f.level.accept=false;
            if(cause==4)tank.selected=0;
            if(cause==5)f.level.entities.remove(tank.id);
            Runnable queued=ExactQueuedShotFixture.Mod.Companion.work.remove(0);
            queued.run();queued.run();
            assertEquals(0,tank.gun.ammoWrites);assertEquals(0,tank.begins);assertEquals(0,tank.port);
            assertEquals(0,tank.energyWrites);
        }
    }
    @Test void wrongWeaponWrongPilotBlockedMuzzleAndRejectedQueueStayRecoverable() throws Exception {
        var f=new ExactQueuedShotFixture(temp,false,false,true);var tank=(BlizzardShotHost)f.vehicle;
        tank.fire(tank.player,"ColdBeam");tank.fire(new BlizzardShotHost.Missile(),null);
        tank.lineClear=false;tank.fire(tank.player,null);tank.lineClear=true;
        assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
        ExactQueuedShotFixture.Mod.Companion.accept=false;tank.fire(tank.player,null);
        ExactQueuedShotFixture.Mod.Companion.accept=true;tank.fire(tank.player,null);
        assertEquals(1,ExactQueuedShotFixture.Mod.Companion.work.size());f.run();assertEquals(1,tank.begins);
    }
}
