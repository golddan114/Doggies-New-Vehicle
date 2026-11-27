package com.liucodex.doggiesnewvehicle.armored;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import com.liucodex.doggiesnewvehicle.compat.SbwTestDependency;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class ApocalypseBlueprintTest {
    private static final Path ROOT=Path.of("src/artillery/resources");
    private static JsonObject json(String path)throws Exception {return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject();}
    @Test void survivalAssemblyUsesApprovedSteelModulesBatteriesAndControlChips()throws Exception {
        JsonObject nativeRecipe;
        try(ZipFile zip=new ZipFile(SbwTestDependency.path().toFile())) {
            nativeRecipe=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("data/superbwarfare/recipes/t_90a.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var recipe=json("data/doggiesnewvehicle/recipes/apocalypse_vehicle_assembling.json");
        assertEquals(nativeRecipe.get("type"),recipe.get("type"));assertEquals(nativeRecipe.get("category"),recipe.get("category"));
        Map<String,Integer> wanted=ingredients(nativeRecipe.getAsJsonArray("inputs"));wanted.replaceAll((key,value)->value*4);
        wanted.put("#forge:storage_blocks/steel",24);
        wanted.put("superbwarfare:heavy_armament_module",2);
        wanted.put("superbwarfare:medium_battery_pack",6);
        wanted.put("superbwarfare:intelligent_chip",2);
        wanted.put("doggiesnewvehicle:apocalypse_blueprint",1);
        assertEquals(wanted,ingredients(recipe.getAsJsonArray("inputs")));
        assertEquals("doggievehicle:apocalypse",recipe.getAsJsonObject("result").get("entity").getAsString());
    }
    @Test void copyAndResearchBothRequireTheFirstBlueprintAndKeepTheirTwoCopyOutput()throws Exception {
        String id="doggiesnewvehicle:apocalypse_blueprint";
        var crafting=json("data/doggiesnewvehicle/recipes/apocalypse_blueprint_copy.json");
        var research=json("data/doggiesnewvehicle/recipes/apocalypse_blueprint_copy_researching.json");
        assertEquals(id,crafting.getAsJsonObject("key").getAsJsonObject("C").get("item").getAsString());
        assertEquals(id,research.getAsJsonObject("input").get("item").getAsString());
        for(var recipe:List.of(crafting,research)) {
            assertEquals(id,recipe.getAsJsonObject("result").get("item").getAsString());
            assertEquals(2,recipe.getAsJsonObject("result").get("count").getAsInt());
        }
        assertTrue(json("data/superbwarfare/tags/items/blueprint/legendary.json").getAsJsonArray("values").contains(new JsonPrimitive(id)));
        for(String locale:List.of("en_us","zh_cn"))assertTrue(JsonParser.parseString(Files.readString(Path.of("src/artillery/lang/"+locale+".json"))).getAsJsonObject().has("item.doggiesnewvehicle.apocalypse_blueprint"));
    }
    @Test void explosiveNativeWeaponsDestroyTerrainAndApRemainsNonExplosive()throws Exception {
        var weapons=json("data/doggievehicle/sbw/vehicles/apocalypse.json").getAsJsonObject("Weapons");
        for(String name:List.of("RocketL","RocketR")) {
            var gun=weapons.getAsJsonObject(name);
            assertEquals("superbwarfare:small_rocket",gun.get("Projectile").getAsString());
            assertEquals(6,gun.get("ExplosionRadius").getAsInt());
            assertEquals(40,gun.get("ExplosionDamage").getAsInt());
            assertTrue(gun.get("ExplosionDestroy").getAsBoolean());
        }
        for(String name:List.of("MainL","MainR")) {
            var gun=weapons.getAsJsonObject(name);
            assertEquals("superbwarfare:cannon_shell",gun.get("Projectile").getAsString());
            assertEquals(8,gun.get("ExplosionRadius").getAsInt());
            assertEquals(160,gun.get("ExplosionDamage").getAsInt());
            assertTrue(gun.get("ExplosionDestroy").getAsBoolean());
            var ap=gun.getAsJsonArray("AmmoType").get(1).getAsJsonObject().getAsJsonObject("Override");
            assertEquals(0,ap.get("ExplosionRadius").getAsInt());assertEquals(0,ap.get("ExplosionDamage").getAsInt());assertEquals(360,ap.get("Damage").getAsInt());
            assertEquals(225,gun.get("Damage").getAsInt());
            assertTrue(ap.get("ExplosionDestroy").getAsBoolean(),"AP terrain intent follows the approved 612 policy; blast damage stays zero");
        }
        var guided=weapons.getAsJsonObject("Missiles");
        assertEquals(3,guided.get("ExplosionRadius").getAsInt());
        assertTrue(guided.get("ExplosionDestroy").getAsBoolean());
    }
    private static Map<String,Integer> ingredients(JsonArray inputs) {
        Map<String,Integer> counts=new LinkedHashMap<>();
        for(JsonElement value:inputs) {
            String text=value.getAsString();int split=text.indexOf(' ');
            if(split>0&&Character.isDigit(text.charAt(0)))counts.merge(text.substring(split+1),Integer.parseInt(text.substring(0,split)),Integer::sum);
            else counts.merge(text,1,Integer::sum);
        }
        return counts;
    }
}
