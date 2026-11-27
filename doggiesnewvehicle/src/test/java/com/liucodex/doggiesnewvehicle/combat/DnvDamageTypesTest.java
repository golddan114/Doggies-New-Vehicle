package com.liucodex.doggiesnewvehicle.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DnvDamageTypesTest {
    @Test
    void onlyExplicitDnvSpecialDamageSkipsOrdinaryArmorPresets() throws IOException {
        Path tag = Path.of("src/boss/resources/data/doggiesnewvehicle/tags/damage_type/armor_special.json");
        var values = com.google.gson.JsonParser.parseString(Files.readString(tag))
                .getAsJsonObject().getAsJsonArray("values");
        Set<String> ids = java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .map(value -> value.getAsString()).collect(Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(
                "doggiesnewvehicle:boss_piercing_laser",
                "doggiesnewvehicle:mothership_doomsday_blast",
                "silent_heaven:halo_piercing"), ids);
        assertFalse(ids.contains("superbwarfare:projectile_hit"));
    }

    @Test
    void silentHeavenKeepsItsRealHaloDamageRegistryIdentity()
            throws IOException {
        Path root = Path.of("src/boss/java/com/liucodex");
        String keys = Files.readString(root.resolve(
                "doggiesnewvehicle/combat/DnvDamageTypes.java"));
        String noise = Files.readString(root.resolve(
                "noise/entity/AbstractNoiseVehicleCombat.java"));
        String silentHeaven = Files.readString(root.resolve(
                "silentheaven/entity/AbstractSilentHeavenVehicleCombat.java"));

        assertTrue(keys.contains("\"silent_heaven\", \"halo_piercing\""));
        assertFalse(keys.contains("NOISE_HALO_PIERCING"));
        assertFalse(noise.contains("DnvDamageTypes"));
        assertTrue(silentHeaven.contains(
                "DnvDamageTypes.SILENT_HEAVEN_HALO_PIERCING"));
        assertFalse(Files.exists(Path.of(
                "src/main/resources/data/noise/damage_type/halo_piercing.json")));
        assertFalse(Files.exists(root.resolve("noise/combat/ModDamageTypes.java")));
        assertFalse(Files.exists(root.resolve(
                "silentheaven/combat/ModDamageTypes.java")));
    }
}
