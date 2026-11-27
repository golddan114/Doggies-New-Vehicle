package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class DnvM1HydraulicsTest {
    @Test void actualRendererPoseStackMatchesBlenderForBothEndsOfEveryNewInternalLink() throws Exception {
        var oracle=JsonParser.parseString(Files.readString(Path.of("src/test/resources/dragon-turrets/blender-pose-oracle.json"))).getAsJsonObject();
        int tested=0;
        for(var p:DnvDragonTurretProfile.ALL.values())for(var link:p.links){
            var o=oracle.getAsJsonObject(p.model);
            var neutral=o.getAsJsonObject("neutral").getAsJsonArray(link.group());
            for(var item:o.getAsJsonArray("poses")){
                var pose=item.getAsJsonObject();if(pose.get("yaw").getAsFloat()!=0)continue;
                var expected=pose.getAsJsonObject("groups").getAsJsonArray(link.group());
                var stack=new PoseStack();DnvM1Hydraulics.apply(stack,p,link,pose.get("elevation").getAsFloat());
                for(int i=0;i<neutral.size();i++){
                    var v=neutral.get(i).getAsJsonArray();var point=new Vector3f(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat());
                    stack.last().pose().transformPosition(point);var e=expected.get(i).getAsJsonArray();
                    assertEquals(e.get(0).getAsFloat(),point.x,.002,p.model+" "+link.group());
                    assertEquals(e.get(1).getAsFloat(),point.y,.002,p.model+" "+link.group());
                    assertEquals(e.get(2).getAsFloat(),point.z,.002,p.model+" "+link.group());tested++;
                }
            }
        }
        assertTrue(tested>200);
    }
}
