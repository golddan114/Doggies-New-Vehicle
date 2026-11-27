package com.liucodex.doggiesnewvehicle.client.mesh;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/** Exercise each real optional-module resource composition, including BOSS without artillery. */
class DnvSoundClosureTest {
    @Test void mode2FireAndChargeReferenceExactNativeEvents()throws Exception{
        var events=JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/assets/doggiesnewvehicle/sounds.json"))).getAsJsonObject();
        var sample=events.getAsJsonObject("blizzard_laser").getAsJsonArray("sounds").get(0).getAsJsonObject();
        assertEquals("event",sample.get("type").getAsString());assertEquals("superbwarfare:waveforce_tower_fire",sample.get("name").getAsString());
        try(var zip=new java.util.zip.ZipFile(Path.of(System.getProperty("dnv.sbw.jar")).toFile())){
            var nativeEvents=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("assets/superbwarfare/sounds.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(nativeEvents.has("waveforce_tower_fire"));
            var charge=events.getAsJsonObject("blizzard_charge").getAsJsonArray("sounds").get(0).getAsJsonObject();
            assertEquals("superbwarfare:annihilator_reload",charge.get("name").getAsString());
            assertEquals("event",charge.get("type").getAsString());assertTrue(nativeEvents.has("annihilator_reload"));
        }
    }
    @Test void everyOptionalResourceCombinationOwnsItsLocalSamples() throws Exception {
        String[] optional={"artillery","boss","platform"};
        for(int mask=0;mask<8;mask++){
            var roots=new ArrayList<Path>();roots.add(Path.of("src/main/resources/assets"));
            for(int i=0;i<3;i++)if((mask&(1<<i))!=0)roots.add(Path.of("src",optional[i],"resources/assets"));
            for(var root:roots.stream().filter(Files::isDirectory).toList())try(var namespaces=Files.list(root)){
                for(var namespace:namespaces.filter(Files::isDirectory).toList()){
                    var file=namespace.resolve("sounds.json");if(!Files.isRegularFile(file))continue;
                    var events=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    for(var event:events.entrySet())for(var sample:event.getValue().getAsJsonObject().getAsJsonArray("sounds")){
                        if(sample.isJsonObject()&&sample.getAsJsonObject().has("type")
                                &&sample.getAsJsonObject().get("type").getAsString().equals("event"))continue;
                        String name=sample.isJsonPrimitive()?sample.getAsString():sample.getAsJsonObject().get("name").getAsString();
                        String[] split=name.split(":",2);String ns=split.length==2?split[0]:namespace.getFileName().toString();
                        String path=split.length==2?split[1]:split[0];
                        if(ns.equals("minecraft")||ns.equals("superbwarfare"))continue;
                        assertTrue(roots.stream().anyMatch(r->Files.isRegularFile(r.resolve(ns+"/sounds/"+path+".ogg"))),
                                "Missing local sample in module mask "+mask+": "+event.getKey()+" -> "+name);
                    }
                }
            }
        }
    }
    @Test void harshNuclearSamplesAreRetiredWhileAllFourOriginalCuesRemain() throws Exception {
        Path assets=Path.of("src/artillery/resources/assets/doggiesnewvehicle");
        var events=JsonParser.parseString(Files.readString(assets.resolve("sounds.json"))).getAsJsonObject();
        for(String name:new String[]{"engine","mechanical","open","close","complete"}){
            assertFalse(events.has("nuclear_"+name));assertFalse(Files.exists(assets.resolve("sounds/nuclear/"+name+".ogg")));
        }
        for(String name:new String[]{"ready","siren","launch","explosion"}){
            assertTrue(events.has("nuclear_"+name));
            for(var sample:events.getAsJsonObject("nuclear_"+name).getAsJsonArray("sounds")){
                String path=sample.getAsJsonObject().get("name").getAsString().split(":",2)[1];
                assertTrue(Files.isRegularFile(assets.resolve("sounds/"+path+".ogg")));
            }
        }
    }
}
