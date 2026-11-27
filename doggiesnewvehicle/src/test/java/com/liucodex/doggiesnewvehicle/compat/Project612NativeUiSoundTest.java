package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Project612NativeUiSoundTest {
    @TempDir Path temp;
    private static JsonObject data() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles/project612.json"))).getAsJsonObject();
    }
    @Test void configuredNativeHudCrosshairIconsAndAllSoundEventsResolveInTheExactDependency() throws Exception {
        var root = data(); var gun = root.getAsJsonObject("Weapons").getAsJsonObject("Main");
        assertEquals("@Land", root.get("HudType").getAsString());
        assertEquals("@VehicleRuApc", gun.get("Crosshair").getAsString());
        try (var zip = new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
            assertNotNull(zip.getEntry("com/atsuishio/superbwarfare/client/overlay/weapon/LandVehicleHud.class"));
            assertNotNull(zip.getEntry("com/atsuishio/superbwarfare/client/sound/VehicleSoundInstance$EngineSound.class"));
            var sounds = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("assets/superbwarfare/sounds.json"))
                    .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            checkSound(zip, sounds, root.get("EngineSound").getAsString());
            var info = gun.getAsJsonObject("SoundInfo");
            for (String key : List.of("Fire1P", "Fire3P", "Fire3PFar", "Fire3PVeryFar", "VehicleReload", "Change"))
                checkSound(zip, sounds, info.get(key).getAsString());
            for (String id : List.of(root.get("ContainerIcon").getAsString(), root.get("VehicleIcon").getAsString(), gun.get("Icon").getAsString())) {
                String[] split = id.split(":", 2); assertNotNull(zip.getEntry("assets/" + split[0] + "/" + split[1]), id);
            }
        }
        assertEquals(650, root.get("MaxHealth").getAsInt());
        assertEquals("AP", gun.get("ShellType").getAsString());
        assertEquals("superbwarfare:large_shell_ap", gun.get("AmmoType").getAsString());
        assertEquals(560, gun.get("Damage").getAsInt()); assertEquals(36, gun.get("Velocity").getAsInt());
        assertEquals(0, gun.get("ExplosionDamage").getAsInt()); assertEquals(0, gun.get("ExplosionRadius").getAsInt());
        assertTrue(gun.get("ExplosionDestroy").getAsBoolean(), "AP uses native impact penetration without area blast damage");
    }
    @Test void nativeReloadCountdownFeedsTheHudAndCompletesOnlyAfterEightSeconds() throws Exception {
        var json = data().getAsJsonObject("Weapons").getAsJsonObject("Main");
        var fixture = new ExactM1ReloadSoundFixture(temp, false);
        var vehicle = new ExactM1ReloadSoundFixture.Vehicle();
        var gun = new ExactM1ReloadSoundFixture.Data();
        gun.duration = json.get("EmptyReloadTime").getAsInt(); gun.magazine = json.get("Magazine").getAsInt();
        assertEquals(160, gun.duration); assertEquals(160, json.get("NormalReloadTime").getAsInt()); assertEquals(1, gun.magazine);
        gun.reload.reloadStarter.requested = true;
        fixture.tick(vehicle, gun);
        assertEquals(161, gun.reload.time());
        fixture.idle(vehicle, gun, 80); assertEquals(81, gun.reload.time()); assertEquals(0, gun.completions);
        fixture.idle(vehicle, gun, 79); assertEquals(0, gun.completions);
        fixture.tick(vehicle, gun); assertEquals(1, gun.completions); assertEquals(1, gun.ammo);
        fixture.tick(vehicle, gun); assertEquals(1, gun.completions);
    }
    private static void checkSound(ZipFile zip, JsonObject sounds, String id) {
        assertTrue(id.startsWith("superbwarfare:"), id);
        var value = sounds.getAsJsonObject(id.substring("superbwarfare:".length())); assertNotNull(value, id);
        for (var sound : value.getAsJsonArray("sounds")) {
            String name = sound.isJsonPrimitive() ? sound.getAsString() : sound.getAsJsonObject().get("name").getAsString();
            String[] split = name.contains(":") ? name.split(":", 2) : new String[] {"superbwarfare", name};
            assertNotNull(zip.getEntry("assets/" + split[0] + "/sounds/" + split[1] + ".ogg"), name);
        }
    }
}
