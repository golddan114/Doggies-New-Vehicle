package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Project612NativeShotTest {
    @TempDir Path temp;
    private ExactQueuedShotFixture fixture() throws Exception { return new ExactQueuedShotFixture(temp, false, false, false, true); }

    @Test void oneWorldAcceptedApShotConsumesOnceAndOnlyThenEmitsNativeEffects() throws Exception {
        var f = fixture(); var tank = (Project612ShotHost) f.vehicle;
        tank.fire(tank.player, null);
        tank.fire(tank.player, "Main");
        assertEquals(1, ExactQueuedShotFixture.Mod.Companion.work.size());
        assertEquals(0, tank.sound); assertEquals(0, tank.recoil); assertEquals(0, tank.gun.ammoWrites);
        assertTrue(ApocalypseShotHost.Network.feedback.isEmpty());
        f.run();
        assertEquals(1, f.level.admissions); assertEquals(1, tank.gun.ammoWrites);
        assertEquals(1, tank.sound); assertEquals(1, tank.recoil);
        assertEquals(java.util.List.of(new ApocalypseShotHost.Feedback(tank.player.getUUID(),
                tank.getUUID(), 0, "Main")), ApocalypseShotHost.Network.feedback);
        var shell = f.level.entities.values().stream().filter(e -> e != tank && e != f.target).findFirst().orElseThrow();
        assertEquals(.90F, shell.armorFraction); assertEquals(.06F, shell.percentFraction);
        assertEquals(0, shell.blastStyle);
    }
    @Test void worldRefusalLeavesAmmoRecoilAndSoundUntouchedAndAllowsRetry() throws Exception {
        var f = fixture(); var tank = (Project612ShotHost) f.vehicle;
        f.level.accept = false;
        tank.fire(tank.player, "Main"); f.run();
        assertEquals(0, tank.gun.ammoWrites); assertEquals(0, tank.sound); assertEquals(0, tank.recoil);
        assertTrue(ApocalypseShotHost.Network.feedback.isEmpty());
        f.level.accept = true;
        tank.fire(tank.player, "Main"); f.run();
        assertEquals(1, tank.gun.ammoWrites); assertEquals(1, tank.sound);
        assertEquals(1, ApocalypseShotHost.Network.feedback.size());
    }
    @Test void wrongActorWeaponEmptyReloadingAndInvalidBurstNeverQueue() throws Exception {
        var f = fixture(); var tank = (Project612ShotHost) f.vehicle;
        tank.fire(new ApocalypseShotHost.Player(), null);
        tank.fire(tank.player, "MainL");
        tank.gun.ready = false; tank.fire(tank.player, null);
        tank.gun.ready = true; tank.selected = 1; tank.fire(tank.player, null);
        tank.selected = 0; tank.gun.projectileAmount = 2; tank.fire(tank.player, null);
        assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
        assertEquals(0, tank.gun.ammoWrites); assertEquals(0, tank.sound);
    }
    @Test void queuedSeatGunMuzzleLifecycleAndLatePreEventChangesReject() throws Exception {
        for (int reason = 0; reason < 7; reason++) {
            var f = fixture(); var tank = (Project612ShotHost) f.vehicle;
            tank.fire(tank.player, "Main");
            switch (reason) {
                case 0 -> tank.passenger = null;
                case 1 -> tank.gun = new ExactQueuedShotFixture.Data();
                case 2 -> tank.lineClear = false;
                case 3 -> tank.wreck = true;
                case 4 -> tank.invalidate();
                case 5 -> tank.gun.beforeShot = () -> tank.lineClear = false;
                case 6 -> tank.gun.projectileType = "superbwarfare:small_rocket";
                default -> throw new AssertionError();
            }
            f.run();
            assertEquals(0, f.level.admissions, "reason=" + reason);
            assertEquals(0, tank.gun.ammoWrites); assertEquals(0, tank.sound); assertEquals(0, tank.recoil);
            assertTrue(ApocalypseShotHost.Network.feedback.isEmpty(), "reason=" + reason);
        }
    }
    @Test void rejectedQueueReleasesTriggerAndReentrantTriggerCannotDuplicateShell() throws Exception {
        var f = fixture(); var tank = (Project612ShotHost) f.vehicle;
        ExactQueuedShotFixture.Mod.Companion.accept = false;
        tank.fire(tank.player, null);
        assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
        assertEquals(0, tank.sound);
        ExactQueuedShotFixture.Mod.Companion.accept = true;
        tank.gun.beforeShot = () -> tank.fire(tank.player, null);
        tank.fire(tank.player, null); f.run();
        assertEquals(1, f.level.admissions); assertEquals(1, tank.gun.ammoWrites);
        assertTrue(ExactQueuedShotFixture.Mod.Companion.work.isEmpty());
        assertEquals(1, ApocalypseShotHost.Network.feedback.size());
    }
}
