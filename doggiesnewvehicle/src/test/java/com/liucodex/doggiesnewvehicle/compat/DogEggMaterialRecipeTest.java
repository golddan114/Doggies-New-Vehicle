package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class DogEggMaterialRecipeTest {
    final Path root=Path.of("src/artillery/resources");
    JsonObject recipe(String name) throws Exception {return JsonParser.parseString(Files.readString(
            root.resolve("data/doggiesnewvehicle/recipes/"+name+".json"))).getAsJsonObject();}
    @Test void coreGridMatchesTheNineUserSpecifiedPositionsExactly() throws Exception {
        var recipe=recipe("dog_core");var pattern=recipe.getAsJsonArray("pattern");var key=recipe.getAsJsonObject("key");
        var expected=List.of("minecraft:chorus_fruit","minecraft:nether_star","minecraft:chorus_fruit",
                "minecraft:dragon_breath","doggiesnewvehicle:wild_dog_milk","minecraft:dragon_breath",
                "minecraft:fire_charge","minecraft:fire_charge","minecraft:fire_charge");
        assertEquals(3,pattern.size());int index=0;
        for(var row:pattern){assertEquals(3,row.getAsString().length());for(char c:row.getAsString().toCharArray())
            assertEquals(expected.get(index++),key.getAsJsonObject(String.valueOf(c)).get("item").getAsString());}
        assertEquals("doggiesnewvehicle:dog_core",recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(1,recipe.getAsJsonObject("result").get("count").getAsInt());
    }
    @Test void vacuumAmmoKeepsEveryNuclearAssemblyMaterialAndQuantityExceptItsCore() throws Exception {
        var nuclear=recipe("nuclear_missile_assembling");var dog=recipe("dogegg_missile");
        assertEquals(nuclear.get("type"),dog.get("type"));assertEquals("superbwarfare:vehicle_assembling",dog.get("type").getAsString());
        var inputs=nuclear.getAsJsonArray("inputs");var actual=dog.getAsJsonArray("inputs");assertEquals(inputs.size(),actual.size());
        int replaced=0;for(int i=0;i<inputs.size();i++){
            String expected=inputs.get(i).getAsString();if(expected.equals("1 doggiesnewvehicle:warhead_core")){
                expected="1 doggiesnewvehicle:dog_core";replaced++;}
            assertEquals(expected,actual.get(i).getAsString());
        }
        assertEquals(1,replaced);assertEquals("doggiesnewvehicle:dogegg_missile",dog.getAsJsonObject("result").get("item").getAsString());
        assertFalse(dog.has("ingredients"),"The temporary nuclear-missile-plus-egg shortcut is retired");
    }
    @Test void emptyCartonIsCraftableAndFullMilkOnlyComesFromTheInteraction() throws Exception {
        var carton=recipe("dog_milk_carton");var pattern=carton.getAsJsonArray("pattern");
        assertEquals(" P ",pattern.get(0).getAsString());assertEquals("PBP",pattern.get(1).getAsString());
        assertEquals(" P ",pattern.get(2).getAsString());
        assertEquals("minecraft:paper",carton.getAsJsonObject("key").getAsJsonObject("P").get("item").getAsString());
        assertEquals("minecraft:glass_bottle",carton.getAsJsonObject("key").getAsJsonObject("B").get("item").getAsString());
        assertEquals("doggiesnewvehicle:dog_milk_carton",carton.getAsJsonObject("result").get("item").getAsString());
        assertFalse(Files.exists(root.resolve("data/doggiesnewvehicle/recipes/wild_dog_milk.json")));
    }
    @Test void threeApprovedMaterialIconsRemainAvailableAndKeepFullAtlasMipLevels() throws Exception {
        var hashes=new java.util.HashSet<String>();
        for(String name:new String[]{"dog_core","dog_milk_carton","wild_dog_milk"}){
            var imagePath=root.resolve("assets/doggiesnewvehicle/textures/item/"+name+".png");
            var image=ImageIO.read(imagePath.toFile());assertNotNull(image);assertTrue(image.getColorModel().hasAlpha());
            assertEquals(256,image.getWidth());assertEquals(256,image.getHeight(),"Atlas art must retain full native mip levels");
            int transparent=0,opaque=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                int alpha=image.getRGB(x,y)>>>24;if(alpha<16)transparent++;if(alpha>240)opaque++;}
            assertTrue(transparent>image.getWidth()*image.getHeight()/10);assertTrue(opaque>1000);
            assertTrue(hashes.add(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(imagePath)))));
            if(name.equals("dog_core")) {
                var model=JsonParser.parseString(Files.readString(root.resolve("assets/doggiesnewvehicle/models/item/"+name+".json"))).getAsJsonObject();
                assertEquals("doggiesnewvehicle:item/"+name,model.getAsJsonObject("textures").get("layer0").getAsString());
            }
        }
    }
    @Test void emptyAndFilledCartonsHaveSolidDepthFoldedRoofsAndUsableDisplayTransforms() throws Exception {
        var atlas=ImageIO.read(root.resolve("assets/doggiesnewvehicle/textures/item/dog_milk_carton_atlas.png").toFile());
        assertNotNull(atlas);assertEquals(512,atlas.getWidth());assertEquals(512,atlas.getHeight());
        for(String name:List.of("dog_milk_carton","wild_dog_milk")) {
            var model=JsonParser.parseString(Files.readString(root.resolve("assets/doggiesnewvehicle/models/item/"+name+".json"))).getAsJsonObject();
            assertFalse(model.has("parent"),"Generated sprite geometry must not replace the solid carton");
            assertEquals("doggiesnewvehicle:item/dog_milk_carton_atlas",model.getAsJsonObject("textures").get("carton").getAsString());
            boolean body=false,fold=false,spout=false;
            for(var value:model.getAsJsonArray("elements")) {
                var element=value.getAsJsonObject();var from=element.getAsJsonArray("from");var to=element.getAsJsonArray("to");
                for(int i=0;i<3;i++){assertTrue(to.get(i).getAsDouble()>from.get(i).getAsDouble());
                    assertTrue(from.get(i).getAsDouble()>=-16);assertTrue(to.get(i).getAsDouble()<=32);}
                if(element.get("name").getAsString().equals("carton_body")) {
                    body=true;assertTrue(to.get(2).getAsDouble()-from.get(2).getAsDouble()>=4);
                    assertTrue(to.get(0).getAsDouble()-from.get(0).getAsDouble()>=4);
                }
                if(element.has("rotation")) {
                    fold=true;assertTrue(List.of(-45.0,-22.5,0.0,22.5,45.0).contains(element.getAsJsonObject("rotation").get("angle").getAsDouble()));
                }
                spout|=element.get("name").getAsString().contains(name.equals("wild_dog_milk")?"filled_sealed_spout":"empty_spout_recess");
                for(var face:element.getAsJsonObject("faces").entrySet()) {
                    assertEquals("#carton",face.getValue().getAsJsonObject().get("texture").getAsString());
                    for(var uv:face.getValue().getAsJsonObject().getAsJsonArray("uv")) assertTrue(uv.getAsDouble()>=0&&uv.getAsDouble()<=16);
                }
            }
            assertTrue(body&&fold&&spout);
            for(String context:List.of("gui","ground","fixed","firstperson_righthand","firstperson_lefthand","thirdperson_righthand","thirdperson_lefthand")) {
                var display=model.getAsJsonObject("display").getAsJsonObject(context);assertNotNull(display);
                for(var scale:display.getAsJsonArray("scale")) assertTrue(scale.getAsDouble()>0&&scale.getAsDouble()<=4);
            }
        }
    }
}
