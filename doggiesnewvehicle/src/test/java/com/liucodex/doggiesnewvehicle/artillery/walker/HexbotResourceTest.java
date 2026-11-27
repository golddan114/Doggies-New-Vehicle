package com.liucodex.doggiesnewvehicle.artillery.walker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HexbotResourceTest {
    @Test void pinnedUpstreamMatricesAndAllFourLegModelsArePresent()throws Exception{
        JsonObject json=JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/assets/doggiesnewvehicle/models/hexbot.json"))).getAsJsonObject();
        assertEquals("56a3f2116c18c70fbddb7afbc9cd0eeb6c95e871",json.get("commit").getAsString());
        assertEquals(34,json.getAsJsonArray("torso").size());
        int total=0;int[] expected={2,4,7,3};int index=0;
        for(String name:new String[]{"base","femur","tibia","tip"}){
            var array=json.getAsJsonObject("segments").getAsJsonArray(name);assertEquals(expected[index++],array.size());total+=array.size();
            for(var part:array){assertEquals(16,part.getAsJsonObject().getAsJsonArray("matrix").size());}
        }
        assertEquals(130,34+6*total);
        assertEquals(1,json.getAsJsonArray("torso").asList().stream().filter(p->p.getAsJsonObject().get("emissive").getAsBoolean()).count());
    }
    @Test void nativeVehicleHasOneDriverAndNoAutomaticWeaponsOrFuelRequirement()throws Exception{
        var json=JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles/hexbot.json"))).getAsJsonObject();
        assertEquals("Empty",json.get("EngineType").getAsString());assertEquals(0,json.get("MaxEnergy").getAsInt());
        assertEquals("@Empty",json.get("HudType").getAsString(),"An unarmed seat must not enter the native armed Land HUD");
        assertEquals(1,json.getAsJsonArray("Seats").size());assertEquals(0,json.getAsJsonObject("Weapons").size());
        var seat=json.getAsJsonArray("Seats").get(0).getAsJsonObject();
        assertEquals(0,seat.getAsJsonArray("Position").get(0).getAsDouble());
        assertEquals(0,seat.getAsJsonArray("Position").get(2).getAsDouble());
        assertFalse(seat.get("HidePassenger").getAsBoolean());assertEquals("Default",seat.get("Pose").getAsString());
        // The chassis is longer than the square entity dimensions: the native OBB must cover its actual model.
        var box=json.getAsJsonArray("OBB").get(0).getAsJsonObject();
        var model=JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/assets/doggiesnewvehicle/models/hexbot.json"))).getAsJsonObject();
        for(var part:model.getAsJsonArray("torso"))for(int corner=0;corner<8;corner++)for(int axis=0;axis<3;axis++){
            var a=part.getAsJsonObject().getAsJsonArray("matrix");double coordinate=a.get(axis*4+3).getAsDouble();
            for(int component=0;component<3;component++)coordinate+=a.get(axis*4+component).getAsDouble()*((corner>>component)&1);
            if(axis==1)coordinate+=.4;
            assertTrue(Math.abs(coordinate-box.getAsJsonArray("Position").get(axis).getAsDouble())
                    <=box.getAsJsonArray("Size").get(axis).getAsDouble()+1e-6,"Chassis model outside native collision bounds");
        }
    }
}
