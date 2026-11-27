package com.liucodex.doggiesnewvehicle.artillery;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.compat.DnvArtillerySettings;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DnvArtillerySettingsTest {
    @TempDir Path directory;
    @Test void generatedChineseFileRoundTripsAndHasAnActualVehicleConsumer() throws Exception {
        var defaults = DnvArtillerySettings.defaults();
        var settings = DnvArtillerySettings.load(directory);
        String original = Files.readString(directory.resolve(DnvArtillerySettings.FILE));
        assertTrue(original.contains("完全重启")); assertTrue(original.contains("单位度/秒"));
        var parsed = DnvArtillerySettings.parse(original, defaults);
        for (String model : new String[]{"dragon_bofors_twin", "dragon_bofors_quad", "dragon_flak38", "dragon_flak88", "m1_yunlong", "dragon_flak40"}) {
            var input = vehicle(model);
            var result = parsed.apply("doggievehicle:" + model, input);
            assertEquals(settings.apply("doggievehicle:" + model, input), result);
            if (model.equals("dragon_flak88") || model.equals("m1_yunlong") || model.equals("dragon_flak40"))
                assertEquals(input.get("Weapons"), result.get("Weapons"), "heavy HE radius must stay untouched");
        }
        String edited = original.replace("dragon_bofors_twin.yaw-degrees-per-second=43.2", "dragon_bofors_twin.yaw-degrees-per-second=120.0")
                .replace("dragon_bofors_twin.he-radius-blocks=1.0", "dragon_bofors_twin.he-radius-blocks=2.5");
        Files.writeString(directory.resolve(DnvArtillerySettings.FILE), edited);
        var changed = DnvArtillerySettings.load(directory).apply("doggievehicle:dragon_bofors_twin", vehicle("dragon_bofors_twin"));
        assertEquals(6, changed.getAsJsonArray("TurretTurnSpeed").get(1).getAsDouble());
        assertEquals(2.5, changed.getAsJsonObject("Weapons").getAsJsonObject("Main").getAsJsonArray("AmmoType")
                .get(1).getAsJsonObject().getAsJsonObject("Override").get("ExplosionRadius").getAsDouble());
        Files.delete(directory.resolve(DnvArtillerySettings.FILE));
        DnvArtillerySettings.load(directory);
        assertEquals(original, Files.readString(directory.resolve(DnvArtillerySettings.FILE)));
    }

    @Test void malformedPartialDuplicateAndUnknownFilesNeverBecomeSnapshots() {
        var defaults = DnvArtillerySettings.defaults();
        String text = DnvArtillerySettings.template(defaults);
        String key = "dragon_flak38.he-radius-blocks";
        for (String bad : new String[]{"NaN", "Infinity", "-1", "17", "oops"})
            assertThrows(IllegalArgumentException.class, () -> DnvArtillerySettings.parse(text.replace(key + "=1.0", key + "=" + bad), defaults));
        assertThrows(IllegalArgumentException.class, () -> DnvArtillerySettings.parse("schema=1\n", defaults));
        assertThrows(IllegalArgumentException.class, () -> DnvArtillerySettings.parse(text + "unknown=2\n", defaults));
        assertThrows(IllegalArgumentException.class, () -> DnvArtillerySettings.parse(text + key + "=1\n", defaults));
    }

    @Test void lightHeUsesNativeAlternateAmmoWithoutDirectDamageOrBurning() throws Exception {
        for (String model : new String[]{"dragon_bofors_twin", "dragon_bofors_quad", "dragon_flak38"}) {
            var gun = vehicle(model).getAsJsonObject("Weapons").getAsJsonObject("Main");
            var ammo = gun.getAsJsonArray("AmmoType"); assertEquals(2, ammo.size());
            assertEquals("superbwarfare:small_shell_ap", ammo.get(0).getAsString());
            var he = ammo.get(1).getAsJsonObject(); assertEquals("superbwarfare:small_shell_he", he.get("Ammo").getAsString());
            var override = he.getAsJsonObject("Override"); assertEquals("HE", override.get("ShellType").getAsString());
            assertEquals(0, override.get("Damage").getAsDouble()); assertEquals(1, override.get("ExplosionRadius").getAsDouble());
            assertTrue(override.get("ExplosionDamage").getAsDouble() > 0);
            assertFalse(override.toString().toLowerCase().contains("fire"));
        }
    }
    @Test void validNativeSingleAmmoAndMetadataOverridesDoNotLoseTheirVehicleDefinition() throws Exception {
        var settings = DnvArtillerySettings.packaged();
        var input = vehicle("dragon_flak38");
        var gun = input.getAsJsonObject("Weapons").getAsJsonObject("Main");
        gun.addProperty("AmmoType", "superbwarfare:small_shell_ap");
        var output = settings.apply("doggievehicle:dragon_flak38", input);
        assertEquals(gun, output.getAsJsonObject("Weapons").getAsJsonObject("Main"));
        gun.add("AmmoType", JsonParser.parseString("[{\"Ammo\":\"superbwarfare:small_shell_ap\",\"Override\":{\"Name\":\"custom.ap\"}}]"));
        output = settings.apply("doggievehicle:dragon_flak38", input);
        assertEquals(gun, output.getAsJsonObject("Weapons").getAsJsonObject("Main"));
    }
    private static com.google.gson.JsonObject vehicle(String model) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles/" + model + ".json"))).getAsJsonObject();
    }
}
