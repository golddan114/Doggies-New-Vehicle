package com.liucodex.doggiesnewvehicle.client.mesh;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class RuntimeTexturePolicyTest {
    @Test void runtimeImagesRespectTheSharedSizePolicyAndRemainDecodable() throws Exception {
        var policy=JsonParser.parseString(Files.readString(Path.of(
                "gradle/runtime-textures.json"))).getAsJsonObject();
        int defaultLimit=policy.get("defaultMaximumEdge").getAsInt();
        assertEquals(1024,defaultLimit);
        var overrides=policy.getAsJsonObject("maximumEdgeOverrides");
        assertEquals(2,overrides.size(),"Only the two large facility atlases retain 2048");
        for(var entry:overrides.entrySet()){
            assertEquals(2048,entry.getValue().getAsInt());
            assertTrue(Files.isRegularFile(Path.of(entry.getKey())),entry.getKey());
        }
        int examined=0;
        for(var value:policy.getAsJsonArray("resourceRoots")){
            var root=Path.of(value.getAsString());
            if(!Files.isDirectory(root))continue;
            try(var paths=Files.walk(root)){
                for(var path:paths.filter(Files::isRegularFile)
                        .filter(p->p.toString().endsWith(".png")).toList()){
                    String key=path.toString().replace('\\','/');
                    int maximum=overrides.has(key)?overrides.get(key).getAsInt():defaultLimit;
                    var image=ImageIO.read(path.toFile());assertNotNull(image,key);
                    assertTrue(image.getWidth()>0&&image.getHeight()>0,key);
                    assertTrue(Math.max(image.getWidth(),image.getHeight())<=maximum,
                            key+" exceeds runtime texture limit "+maximum);
                    examined++;
                }
            }
        }
        assertTrue(examined>0,"A missing resource root must not turn the size check into a no-op");
    }
}
