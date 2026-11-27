package com.liucodex.doggiesnewvehicle.artillery;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** User-approved 1.8–2.1x baseline range; excludes perks, armour, HP-percent and AoE multi-hits. */
class LightAaSustainedDamageTest {
    @Test void sustainedBaseOutputStaysInsideTheUserApprovedMultiplierRange() throws Exception {
        var originals = Map.of("dragon_bofors_twin", 26, "dragon_bofors_quad", 24, "dragon_flak38", 12);
        for (var entry : originals.entrySet()) {
            var gun = main(entry.getKey()); var baseline = gun.deepCopy(); baseline.addProperty("Damage", entry.getValue());
            double multiplier = sustained(gun) / sustained(baseline);
            assertTrue(multiplier >= 1.8 && multiplier <= 2.1, entry.getKey() + ": " + multiplier);
        }
        double twin = sustained(main("dragon_bofors_twin"));
        double quad = sustained(main("dragon_bofors_quad"));
        double flak = sustained(main("dragon_flak38"));
        assertTrue(quad > Math.max(twin, flak) * 1.2, "Quad mount retains a clear sustained-output role");
        assertTrue(main("dragon_bofors_twin").get("Damage").getAsDouble() > main("dragon_flak38").get("Damage").getAsDouble());
    }

    @Test void apArmorThresholdAdjustmentPreservesCadenceAndTheApprovedHeStrength() throws Exception {
        var cycles = Map.of("dragon_bofors_twin", new int[]{240,32,60}, "dragon_bofors_quad", new int[]{400,64,80},
                "dragon_flak38", new int[]{600,80,60});
        var heStrength = Map.of("dragon_bofors_twin", 52.0, "dragon_bofors_quad", 48.0, "dragon_flak38", 24.0);
        for (var entry : cycles.entrySet()) {
            var main = main(entry.getKey()); var values = entry.getValue();
            assertEquals(values[0], main.get("RPM").getAsInt());
            assertEquals(values[1], main.get("Magazine").getAsInt());
            assertEquals(values[2], main.get("EmptyReloadTime").getAsInt());
            assertEquals(1, main.get("AmmoCostPerShoot").getAsInt());
            var he = main.getAsJsonArray("AmmoType").get(1).getAsJsonObject().getAsJsonObject("Override");
            assertEquals("HE", he.get("ShellType").getAsString());
            assertEquals(heStrength.get(entry.getKey()).doubleValue(), he.get("ExplosionDamage").getAsDouble());
            assertEquals(0, he.get("Damage").getAsInt(), "HE direct blast is settled once by the existing impact path");
            assertEquals(0, he.get("BypassesArmor").getAsDouble());
            assertEquals(1, he.get("ExplosionRadius").getAsInt());
            assertFalse(main.get("ExplosionDestroy").getAsBoolean());
        }
    }

    private static double sustained(JsonObject gun) {
        double magazine = gun.get("Magazine").getAsDouble();
        // Conservative full firing interval for every round, plus a full empty reload per magazine.
        return 60 * magazine * gun.get("Damage").getAsDouble()
                / (magazine * 60 / gun.get("RPM").getAsDouble() + gun.get("EmptyReloadTime").getAsDouble() / 20);
    }
    private static JsonObject main(String id) throws Exception {
        try (var in = LightAaSustainedDamageTest.class.getResourceAsStream("/data/doggievehicle/sbw/vehicles/" + id + ".json")) {
            return JsonParser.parseString(new String(java.util.Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("Weapons").getAsJsonObject("Main");
        }
    }
}
