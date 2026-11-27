package com.liucodex.doggiesnewvehicle.artillery;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import com.liucodex.doggiesnewvehicle.combat.BossPercentDamage;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Approved packaged inputs and the compiled consumers; execution gates cover hit math and emission. */
class ApprovedBalanceTest {
    private static final Path RES = Path.of("src/artillery/resources");
    private static JsonObject json(String relative) throws Exception {
        return JsonParser.parseString(Files.readString(RES.resolve(relative))).getAsJsonObject();
    }
    @Test void towerHealthAndM1DamageThresholdStayConsistent() throws Exception {
        var expected = Map.of("storm_turret", 150, "50_changfeng_cannon", 300, "jingzhe_turret", 350,
                "m1_yunlong", 450, "blizzard", 600, "apocalypse", 1800, "project612", 650, "nuclear_silo", 800);
        for (var item : expected.entrySet()) {
            assertEquals(item.getValue(), json("data/doggievehicle/sbw/vehicles/" + item.getKey() + ".json").get("MaxHealth").getAsInt());
        }
        var node = compiled("com/liucodex/doggiesnewvehicle/entity/DnvM1YunlongEntity");
        assertEquals(450F, constant(node, "MAX_HEALTH"));
        assertEquals(270F, constant(node, "DAMAGED_HEALTH"));
    }
    @Test void blizzardSurvivalRecipeHasObtainableCopyableBlueprintAndExactApprovedComponents() throws Exception {
        String id = "doggiesnewvehicle:blizzard_blueprint";
        var recipe = json("data/doggiesnewvehicle/recipes/blizzard_vehicle_assembling.json");
        var expected = Set.of("12 #forge:storage_blocks/steel", id, "2 superbwarfare:cannon_core",
                "3 superbwarfare:intelligent_chip", "3 superbwarfare:large_battery_pack",
                "3 superbwarfare:large_motor", "8 minecraft:blue_ice");
        var actual = new HashSet<String>();
        recipe.getAsJsonArray("inputs").forEach(value -> assertTrue(actual.add(value.getAsString())));
        assertEquals(expected, actual);
        assertEquals("superbwarfare:vehicle_assembling", recipe.get("type").getAsString());
        assertEquals("doggievehicle:blizzard", recipe.getAsJsonObject("result").get("entity").getAsString());
        for (String suffix : List.of("_copy", "_copy_researching")) {
            var copy = json("data/doggiesnewvehicle/recipes/blizzard_blueprint" + suffix + ".json");
            assertEquals(id, copy.getAsJsonObject("result").get("item").getAsString());
            assertEquals(2, copy.getAsJsonObject("result").get("count").getAsInt());
            var input = suffix.equals("_copy") ? copy.getAsJsonObject("key").getAsJsonObject("C") : copy.getAsJsonObject("input");
            assertEquals(id, input.get("item").getAsString());
        }
        for (String tag : List.of("cannon", "legendary"))
            assertTrue(json("data/superbwarfare/tags/items/blueprint/" + tag + ".json").getAsJsonArray("values").contains(new JsonPrimitive(id)));
        for (String owner : List.of("DnvArtilleryContent", "DnvArtilleryBlueprintLoot")) {
            var node = compiled("com/liucodex/doggiesnewvehicle/artillery/" + owner);
            assertTrue(node.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray()))
                    .anyMatch(instruction -> instruction instanceof FieldInsnNode field && field.name.equals("BLIZZARD_BLUEPRINT")), owner);
        }
        for (String locale : List.of("zh_cn", "en_us")) {
            var lang = JsonParser.parseString(Files.readString(Path.of("src/artillery/lang/" + locale + ".json"))).getAsJsonObject();
            assertTrue(lang.has("item.doggiesnewvehicle.blizzard_blueprint"));
        }
    }
    @Test void bossFractionsAreReadByTheirActualCompiledHitEntrances() throws Exception {
        String[] profiles = {"noise_main", "noise_autocannon", "noise_rocket", "noise_vls", "silent_heaven_he",
                "silent_heaven_autocannon", "silent_heaven_rocket", "silent_heaven_vls", "silent_heaven_halo"};
        float[] expected = {.015F, .001F, .024F, .05F, .0125F, .00125F, .025F, .06F, .03F};
        String[] methods = {"fireMainGun", "createAutocannonDamageSource", "fireUnguidedSideRocket", "fireTrackingMissile",
                "fireMainGun", "createAutocannonDamageSource", "fireUnguidedSideRocket", "fireTrackingMissile", "createHaloDamageSource"};
        for (int index = 0; index < profiles.length; index++) {
            assertEquals(expected[index], BossPercentDamage.fraction(profiles[index]));
            String owner = index < 4 ? "noise/entity/AbstractNoiseVehicleWeapons" : index == 8
                    ? "silentheaven/entity/AbstractSilentHeavenVehicleCombat" : "silentheaven/entity/AbstractSilentHeavenVehicleWeapons";
            String methodName = methods[index];
            var method = compiled("com/liucodex/" + owner).methods.stream().filter(value -> value.name.equals(methodName)).findFirst().orElseThrow();
            var instructions = Arrays.asList(method.instructions.toArray());
            String key = profiles[index];
            assertTrue(instructions.stream().anyMatch(value -> value instanceof LdcInsnNode ldc && key.equals(ldc.cst)), key);
            assertTrue(instructions.stream().anyMatch(value -> value instanceof MethodInsnNode call
                    && call.owner.equals("com/liucodex/doggiesnewvehicle/combat/BossPercentDamage") && call.name.equals("fraction")), key);
        }
        assertEquals(240, constant(compiled("com/liucodex/noise/entity/AbstractNoiseVehicleState"), "MISSILE_RELOAD"));
        assertEquals(200, constant(compiled("com/liucodex/noise/entity/AbstractNoiseVehicleState"), "VLS_RELOAD"));
        assertEquals(400, constant(compiled("com/liucodex/silentheaven/entity/AbstractSilentHeavenVehicleState"), "VLS_RELOAD"));
    }
    private static Object constant(ClassNode node, String name) {
        return node.fields.stream().filter(field -> field.name.equals(name)).findFirst().orElseThrow().value;
    }
    private static ClassNode compiled(String name) throws Exception {
        var node = new ClassNode();
        try (var stream = ApprovedBalanceTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream, name);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }
}
