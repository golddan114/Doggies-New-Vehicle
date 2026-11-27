package com.liucodex.doggiesnewvehicle.client.mesh;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class DogEggAssetTest {
    final Path resources=Path.of("src/artillery/resources");
    final Path assets=resources.resolve("assets/doggiesnewvehicle");
    @Test void runtimeMeshPreserves192ShellTrianglesAndTwoImageTrianglesWithFiniteUvNormals() throws Exception {
        var mesh=DnvTurretMesh.DOGEGG.parse(Files.readAllBytes(assets.resolve("meshes/dogegg.etm")));
        assertEquals(2,mesh.partCount());assertEquals(194,mesh.triangleCount());
        double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxRadius=0;
        for(String name:new String[]{"shell","dog"}){
            var stream=mesh.part(name).main();assertEquals((name.equals("shell")?192:2)*24,stream.length);
            for(int i=0;i<stream.length;i+=8){
                for(int j=0;j<8;j++)assertTrue(Float.isFinite(stream[i+j]));
                assertEquals(1,Math.sqrt(stream[i+3]*stream[i+3]+stream[i+4]*stream[i+4]+stream[i+5]*stream[i+5]),1e-4);
                assertTrue(stream[i+6]>=-1e-5&&stream[i+6]<=1.00001);assertTrue(stream[i+7]>=-1e-5&&stream[i+7]<=1.00001);
                if(name.equals("shell")){minY=Math.min(minY,stream[i+1]);maxY=Math.max(maxY,stream[i+1]);
                    maxRadius=Math.max(maxRadius,Math.hypot(stream[i],stream[i+2]));}
            }
        }
        assertEquals(0,minY,1e-5);assertEquals(5.2,maxY,1e-5);assertEquals(1.49,maxRadius,1e-4);
        var boxes=JsonParser.parseString(Files.readString(resources.resolve("data/doggiesnewvehicle/nuclear/dogegg_collision.json")))
                .getAsJsonObject().getAsJsonArray("missile");assertEquals(9,boxes.size());
        double last=0;for(var value:boxes){var b=value.getAsJsonObject().getAsJsonArray("bounds");
            assertEquals(last,b.get(1).getAsDouble(),1e-5);last=b.get(4).getAsDouble();assertTrue(last>0&&last<=5.20001);
            assertTrue(b.get(3).getAsDouble()<=1.49001&&b.get(0).getAsDouble()>=-1.49001);}
        assertEquals(5.2,last,1e-5);
    }
    @Test void dogImagesHaveRealTransparencyAndWhiteOutlineAndIconsCanDecode() throws Exception {
        for(String name:new String[]{"egg_dog","burst_dog"}){
            var image=ImageIO.read(assets.resolve("textures/entity/dogegg/"+name+".png").toFile());assertNotNull(image);
            assertTrue(image.getColorModel().hasAlpha());int transparent=0,white=0,opaque=0;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                int pixel=image.getRGB(x,y),alpha=pixel>>>24;
                if(alpha<16)transparent++;if(alpha>240){opaque++;if((pixel&0xffffff)>0xf0f0f0)white++;}
            }
            assertTrue(transparent>image.getWidth()*image.getHeight()/10);assertTrue(opaque>10000);assertTrue(white>1000);
        }
        for(String name:new String[]{"dogegg_missile","dogegg_silo_deployer"}){
            var icon=ImageIO.read(assets.resolve("textures/item/"+name+".png").toFile());assertEquals(512,icon.getWidth());
            String recipeName=name.equals("dogegg_silo_deployer")?"dogegg_silo":name;
            var recipe=JsonParser.parseString(Files.readString(resources.resolve("data/doggiesnewvehicle/recipes/"+recipeName+".json"))).getAsJsonObject();
            assertEquals("doggiesnewvehicle:"+name,recipe.getAsJsonObject("result").get("item").getAsString());
        }
    }
    @Test void fourNativeSoundsAreMono48kVorbisAndImpactRetainsElevenSeconds() throws Exception {
        var events=JsonParser.parseString(Files.readString(assets.resolve("sounds.json"))).getAsJsonObject();
        for(String name:new String[]{"extend","ready","launch","impact"}){
            String event="dogegg_"+name;assertTrue(events.has(event));
            byte[] bytes=Files.readAllBytes(assets.resolve("sounds/"+event+".ogg"));
            assertEquals("OggS",new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII));
            int header=-1;for(int i=0;i<bytes.length-16;i++)if(bytes[i]==1&&bytes[i+1]=='v'&&bytes[i+2]=='o'
                    &&bytes[i+3]=='r'&&bytes[i+4]=='b'&&bytes[i+5]=='i'&&bytes[i+6]=='s'){header=i;break;}
            assertTrue(header>=0);assertEquals(1,bytes[header+11]);
            assertEquals(48000,ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(header+12));
            if(name.equals("impact")){
                long samples=0;for(int i=0;i<bytes.length-27;i++)if(bytes[i]=='O'&&bytes[i+1]=='g'&&bytes[i+2]=='g'&&bytes[i+3]=='S')
                    samples=Math.max(samples,ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong(i+6));
                assertTrue(samples/48000.0>11&&samples/48000.0<11.15);
            }
        }
    }
}
