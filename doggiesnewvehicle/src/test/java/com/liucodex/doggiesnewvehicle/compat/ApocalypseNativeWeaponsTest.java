package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApocalypseNativeWeaponsTest {
    @TempDir Path temp;

    @Test void nativeWeaponSwitchPreservesBothLoadedAndReloadingBarrelsAndRounds() throws Exception {
        var f = new ExactApocalypseSelectionFixture();
        var left = f.tank.guns.get("MainL");
        var right = f.tank.guns.get("MainR");
        left.ammo.set(1);
        right.remaining = 101;
        f.tank.guns.get("RocketL").ammo.set(3);
        f.tank.guns.get("RocketR").remaining = 227;
        f.tank.guns.get("Missiles").remaining = 1090;
        for (int n = 0; n < 30; n++) {
            f.change(1, true);
            assertEquals((n + 1) % 3, f.tank.getWeaponIndex(0));
        }
        f.change(-1, true);
        assertEquals(2, f.tank.getWeaponIndex(0));
        f.change(1, false);
        assertEquals(1, f.tank.getWeaponIndex(0));
        assertEquals(1, left.ammo.get());
        assertEquals(101, right.remaining);
        assertEquals(3, f.tank.guns.get("RocketL").ammo.get());
        assertEquals(227, f.tank.guns.get("RocketR").remaining);
        assertEquals(1090, f.tank.guns.get("Missiles").remaining);
        assertTrue(f.tank.guns.values().stream().allMatch(g -> g.withdrawn == 0));
    }

    @Test void nativeAmmoInputQueuesTypeWithoutChangingLoadedShellOrActiveReload() throws Exception {
        var f = new ExactApocalypseSelectionFixture();
        var left = f.tank.guns.get("MainL");
        var right = f.tank.guns.get("MainR");
        left.ammo.set(1);
        right.remaining = 81;
        assertTrue(f.request("MainL", 1));
        assertEquals(1, f.tank.preferred);
        assertEquals(0, left.selectedAmmoType.get());
        assertEquals(0, right.selectedAmmoType.get());
        assertEquals(1, left.ammo.get());
        assertEquals(81, right.remaining);
        left.ammo.set(0); // SBW has fired this chamber; it has not begun the next reload yet.
        f.prepare();
        assertEquals(1, left.selectedAmmoType.get());
        assertEquals(0, right.selectedAmmoType.get());
        left.remaining = 160;
        assertTrue(f.request("MainR", 1)); // Another native next/previous press with two choices.
        assertEquals(0, f.tank.preferred);
        assertEquals(160, left.remaining);
        assertEquals(1, left.selectedAmmoType.get());
        assertEquals(81, right.remaining);
        assertFalse(f.request("RocketL", 1));
        assertTrue(f.request("MainR", 99));
        assertEquals(0, f.tank.preferred);
    }

    @Test void emptyIdleGunsAdoptChoiceWithoutReceivingAnyFreeAmmunition() throws Exception {
        var f = new ExactApocalypseSelectionFixture();
        f.request("MainL", 1);
        for (String name : List.of("MainL", "MainR")) {
            assertEquals(1, f.tank.guns.get(name).selectedAmmoType.get());
            assertEquals(0, f.tank.guns.get(name).ammo.get());
        }
        f.tank.world.isClientSide = true;
        f.request("MainL", 0);
        assertEquals(1, f.tank.preferred);
    }

    @Test void exactNativeCountdownFinishesEachIndependentMagazineAtItsConfiguredDuration() throws Exception {
        var fixture = new ExactM1ReloadSoundFixture(temp, false);
        var vehicle = new ExactM1ReloadSoundFixture.Vehicle();
        var data = JsonParser.parseString(Files.readString(Path.of(
                "src/artillery/resources/data/doggievehicle/sbw/vehicles/apocalypse.json"))).getAsJsonObject().getAsJsonObject("Weapons");
        for (String name : List.of("MainL", "MainR", "RocketL", "RocketR", "Missiles")) {
            var json = data.getAsJsonObject(name);
            if (name.equals("Missiles")) {
                assertEquals(400, json.get("EmptyReloadTime").getAsInt());
                assertEquals(400, json.get("NormalReloadTime").getAsInt());
                assertEquals(4, json.get("Magazine").getAsInt());
            }
            var gun = new ExactM1ReloadSoundFixture.Data();
            gun.duration = json.get("EmptyReloadTime").getAsInt();
            gun.magazine = json.get("Magazine").getAsInt();
            gun.reload.reloadStarter.requested = true;
            fixture.tick(vehicle, gun);
            assertEquals(gun.duration + 1, gun.reload.time());
            fixture.idle(vehicle, gun, gun.duration - 1);
            assertEquals(0, gun.completions, name);
            fixture.tick(vehicle, gun);
            assertEquals(1, gun.completions, name);
            assertEquals(gun.magazine, gun.ammo, name);
        }
    }

    @Test void vehicleHudAndMissileSeekingUseNativeVehicleDataInsteadOfHandheldProperties() throws Exception {
        var root = JsonParser.parseString(Files.readString(Path.of(
                "src/artillery/resources/data/doggievehicle/sbw/vehicles/apocalypse.json"))).getAsJsonObject();
        assertEquals("@Land", root.get("HudType").getAsString());
        var guns = root.getAsJsonObject("Weapons");
        assertEquals(5, guns.size());
        for (var value : guns.entrySet()) assertFalse(value.getValue().getAsJsonObject().get("WithdrawAmmoWhenChangeSlot").getAsBoolean());
        var missile = guns.getAsJsonObject("Missiles");
        assertEquals(3, missile.get("Velocity").getAsDouble());
        assertEquals("@VehicleCommonSeekMissile", missile.get("Crosshair").getAsString());
        assertFalse(missile.has("SeekTime"));
        var seek = missile.getAsJsonObject("SeekWeaponInfo");
        assertEquals(20, seek.get("SeekTime").getAsInt());
        assertTrue(seek.get("OnlyLockEntity").getAsBoolean());
        assertEquals("Passenger", seek.get("SeekDirection").getAsString());
        try (var zip = new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
            var nativeTank = JsonParser.parseString(new String(zip.getInputStream(
                    zip.getEntry("data/superbwarfare/sbw/vehicles/yx_100.json")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals(nativeTank.get("DamageModifiers"), root.get("DamageModifiers"));
            assertEquals(nativeTank.get("EngineSound"), root.get("EngineSound"));
            assertEquals(1800, root.get("MaxHealth").getAsInt());
        }
    }
}
