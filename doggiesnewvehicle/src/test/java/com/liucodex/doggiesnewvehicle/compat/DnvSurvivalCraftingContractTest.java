package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class DnvSurvivalCraftingContractTest {
  private static final List<String> BLUEPRINTS = List.of(
      "changfeng_blueprint", "storm_turret_blueprint", "jingzhe_turret_blueprint", "m1_yunlong_blueprint", "blizzard_blueprint");

  @Test void exactSbwReferenceOwnsFirstBlueprintCopyAndVehicleAssemblyFormats() throws Exception {
    try (var zip = new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
      JsonObject assembly = jarJson(zip, "data/superbwarfare/recipes/mk_42.json");
      assertEquals("superbwarfare:vehicle_assembling", assembly.get("type").getAsString());
      assertEquals("superbwarfare:mk_42", assembly.getAsJsonObject("result").get("entity").getAsString());

      JsonObject copy = jarJson(zip, "data/superbwarfare/recipes/mk_42_blueprint_copy_researching.json");
      assertEquals("superbwarfare:researching", copy.get("type").getAsString());
      assertEquals(600, copy.get("time").getAsInt());
      assertEquals(2, copy.getAsJsonObject("result").get("count").getAsInt());

      JsonObject first = jarJson(zip, "data/superbwarfare/recipes/legendary_blueprint_data_chip_researching.json");
      assertEquals("superbwarfare:blueprint/legendary", first.getAsJsonObject("result").get("tag").getAsString());
      assertEquals(3000, first.get("time").getAsInt());
      assertEquals("superbwarfare:legendary_blueprint_data_chip", first.getAsJsonObject("input").get("item").getAsString());
      assertEquals("minecraft:paper", first.getAsJsonObject("base").get("item").getAsString());
      assertEquals("minecraft:lapis_lazuli", first.getAsJsonObject("addition").get("item").getAsString());
      JsonObject directional = jarJson(zip, "data/superbwarfare/recipes/legendary_blueprint_data_chip_researching_directional.json");
      assertTrue(directional.get("selectable").getAsBoolean());
      assertEquals("superbwarfare:directional_research_module", directional.getAsJsonObject("special").get("item").getAsString());
      assertEquals("superbwarfare:blueprint/legendary", directional.getAsJsonObject("result").get("tag").getAsString());
      for (String item : List.of("cannon_core", "motor", "battery", "intelligent_chip", "large_battery_pack")) {
        assertTrue(zip.getEntry("assets/superbwarfare/models/item/" + item + ".json") != null,
            "Exact SBW ingredient is missing: " + item);
      }
    }
  }

  @Test void everyDnvTurretHasFirstBlueprintCopyAndAssemblyClosure() throws Exception {
    JsonObject legendary = artilleryJson("data/superbwarfare/tags/items/blueprint/legendary.json");
    JsonObject cannon = artilleryJson("data/superbwarfare/tags/items/blueprint/cannon.json");
    assertEquals(false, legendary.get("replace").getAsBoolean());
    assertEquals(false, cannon.get("replace").getAsBoolean());
    var values = legendary.getAsJsonArray("values");
    var cannonValues = cannon.getAsJsonArray("values");
    for (String blueprint : BLUEPRINTS) {
      assertTrue(values.asList().stream().anyMatch(value ->
          value.getAsString().equals("doggiesnewvehicle:" + blueprint)));
      assertTrue(cannonValues.asList().stream().anyMatch(value ->
          value.getAsString().equals("doggiesnewvehicle:" + blueprint)));

      JsonObject shaped = resourceJson("data/doggiesnewvehicle/recipes/" + blueprint + "_copy.json");
      assertEquals("minecraft:crafting_shaped", shaped.get("type").getAsString());
      assertEquals("doggiesnewvehicle:" + blueprint,
          shaped.getAsJsonObject("result").get("item").getAsString());
      assertEquals(2, shaped.getAsJsonObject("result").get("count").getAsInt());
      assertEquals("doggiesnewvehicle:" + blueprint + "_copy",
          resourceJson("data/doggiesnewvehicle/advancements/recipes/misc/" + blueprint + "_copy.json")
              .getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());

      JsonObject researching = resourceJson(
          "data/doggiesnewvehicle/recipes/" + blueprint + "_copy_researching.json");
      assertEquals("superbwarfare:researching", researching.get("type").getAsString());
      assertEquals(600, researching.get("time").getAsInt());
      assertEquals("doggiesnewvehicle:" + blueprint, researching.getAsJsonObject("result").get("item").getAsString());
      assertEquals(2, researching.getAsJsonObject("result").get("count").getAsInt());
    }

    assertAssembly("changfeng_vehicle_assembling", "entity", "doggievehicle:50_changfeng_cannon",
        List.of("8 #forge:storage_blocks/steel", "doggiesnewvehicle:changfeng_blueprint",
            "superbwarfare:cannon_core", "2 superbwarfare:motor", "superbwarfare:intelligent_chip", "superbwarfare:medium_battery_pack"));
    assertAssembly("storm_turret_vehicle_assembling", "entity", "doggievehicle:storm_turret",
        List.of("8 #forge:storage_blocks/steel", "doggiesnewvehicle:storm_turret_blueprint",
            "superbwarfare:cannon_core", "4 superbwarfare:motor", "2 superbwarfare:empty_perk"));
    assertAssembly("jingzhe_turret_vehicle_assembling", "entity", "doggievehicle:jingzhe_turret",
        List.of("10 #forge:storage_blocks/steel", "doggiesnewvehicle:jingzhe_turret_blueprint",
            "2 superbwarfare:cannon_core", "3 superbwarfare:intelligent_chip",
            "3 superbwarfare:large_battery_pack"));
    assertAssembly("m1_yunlong_vehicle_assembling", "entity", "doggievehicle:m1_yunlong",
        List.of("12 #forge:storage_blocks/steel", "#doggiesnewvehicle:blueprints/m1_yunlong",
            "superbwarfare:cannon_core", "2 superbwarfare:motor",
            "2 superbwarfare:intelligent_chip", "2 superbwarfare:large_battery_pack"));
    var m1Aliases = resourceJson("data/doggiesnewvehicle/tags/items/blueprints/m1_yunlong.json")
        .getAsJsonArray("values").asList().stream().map(value -> value.getAsString()).toList();
    assertEquals(List.of("doggiesnewvehicle:m1_yunlong_blueprint",
        "doggiesnewvehicle:m1_howitzer_blueprint"), m1Aliases);
  }

  @Test void artilleryBlueprintsReachExistingSbwBoxesWithoutReplacingTheirLootTables() throws Exception {
    try (var zip = new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
      for (String table : List.of("containers/blueprints", "chests/blue_print_epic")) {
        assertTrue(zip.getEntry("data/superbwarfare/loot_tables/" + table + ".json") != null,
            "Exact SBW box table missing: " + table);
      }
    }
    JsonObject list = artilleryJson("data/forge/loot_modifiers/global_loot_modifiers.json");
    assertEquals(false, list.get("replace").getAsBoolean());
    assertEquals(List.of("doggiesnewvehicle:artillery_blueprints_box",
        "doggiesnewvehicle:artillery_blueprints_epic_chest"),
        list.getAsJsonArray("entries").asList().stream().map(value -> value.getAsString()).toList());
    assertBoxModifier("artillery_blueprints_box", "superbwarfare:containers/blueprints");
    assertBoxModifier("artillery_blueprints_epic_chest", "superbwarfare:chests/blue_print_epic");
  }

  private static void assertBoxModifier(String name, String table) throws Exception {
    JsonObject modifier = resourceJson("data/doggiesnewvehicle/loot_modifiers/" + name + ".json");
    assertEquals("doggiesnewvehicle:artillery_blueprint_box", modifier.get("type").getAsString());
    JsonObject condition = modifier.getAsJsonArray("conditions").get(0).getAsJsonObject();
    assertEquals("forge:loot_table_id", condition.get("condition").getAsString());
    assertEquals(table, condition.get("loot_table_id").getAsString());
  }

  @Test void nativeRemoteStationIsCraftableWithoutRetiredConnector() throws Exception {
    JsonObject station = resourceJson("data/doggiesnewvehicle/recipes/remote_weapon_station.json");
    assertEquals("doggiesnewvehicle:remote_weapon_station",
        station.getAsJsonObject("result").get("item").getAsString());
    assertEquals(List.of("ICI", "RGR", "III"), station.getAsJsonArray("pattern").asList().stream()
        .map(value -> value.getAsString()).toList());
    assertEquals("forge:ingots/iron", station.getAsJsonObject("key").getAsJsonObject("I").get("tag").getAsString());
    assertEquals("minecraft:comparator", station.getAsJsonObject("key").getAsJsonObject("C").get("item").getAsString());
    assertEquals("minecraft:redstone", station.getAsJsonObject("key").getAsJsonObject("R").get("item").getAsString());
    assertEquals("minecraft:glass_pane", station.getAsJsonObject("key").getAsJsonObject("G").get("item").getAsString());

    assertEquals("doggiesnewvehicle:remote_weapon_station",
        resourceJson("data/doggiesnewvehicle/advancements/recipes/redstone/remote_weapon_station.json")
            .getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
    for (String retired : List.of(
        "data/doggiesnewvehicle/recipes/remote_weapon_connector.json",
        "data/doggiesnewvehicle/advancements/recipes/tools/remote_weapon_connector.json",
        "assets/doggiesnewvehicle/models/item/remote_weapon_connector.json")) {
      org.junit.jupiter.api.Assertions.assertNull(getClass().getClassLoader().getResource(retired), retired);
    }
  }

  private static void assertAssembly(String name, String resultKind, String resultId, List<String> expectedInputs)
      throws Exception {
    JsonObject recipe = resourceJson("data/doggiesnewvehicle/recipes/" + name + ".json");
    assertEquals("superbwarfare:vehicle_assembling", recipe.get("type").getAsString());
    assertEquals(resultId, recipe.getAsJsonObject("result").get(resultKind).getAsString());
    assertEquals(expectedInputs, recipe.getAsJsonArray("inputs").asList().stream()
        .map(value -> value.getAsString()).toList());
    assertEquals("doggiesnewvehicle:" + name,
        resourceJson("data/doggiesnewvehicle/advancements/recipes/misc/" + name + ".json")
            .getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
  }

  private static JsonObject resourceJson(String path) throws Exception {
    try (var input = DnvSurvivalCraftingContractTest.class.getClassLoader().getResourceAsStream(path)) {
      if (input == null) throw new IllegalStateException("Missing resource " + path);
      return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }

  private static JsonObject artilleryJson(String path) throws Exception {
    try (var input = Files.newBufferedReader(Path.of("src/artillery/resources", path), StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(input).getAsJsonObject();
    }
  }

  private static JsonObject jarJson(ZipFile zip, String path) throws Exception {
    var entry = zip.getEntry(path);
    if (entry == null) throw new IllegalStateException("Missing exact SBW entry " + path);
    try (var input = zip.getInputStream(entry)) {
      return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }
}
