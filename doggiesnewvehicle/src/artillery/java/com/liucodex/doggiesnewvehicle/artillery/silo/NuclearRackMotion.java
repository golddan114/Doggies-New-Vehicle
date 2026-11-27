package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Packaged motion determines physical rack position; client resource packs cannot rewrite gameplay. */
public final class NuclearRackMotion {
    private static final NuclearMotion MOTION;
    private static final int GROUP;
    private static final Matrix4f READY_INVERSE;
    static {
        try(var binary=NuclearRackMotion.class.getResourceAsStream("/assets/doggiesnewvehicle/meshes/nuclear_motion.nsm");
                var json=NuclearRackMotion.class.getResourceAsStream("/assets/doggiesnewvehicle/meshes/nuclear_parts.json")) {
            if(binary==null||json==null)throw new IllegalStateException("Missing rack motion");
            MOTION=NuclearMotion.parse(binary.readAllBytes());
            var groups=JsonParser.parseReader(new InputStreamReader(json,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("groups");
            int rocket=-1;
            for(int i=0;i<groups.size();i++)if(groups.get(i).getAsJsonObject().get("name").getAsString().equals("CTRL_展示弹_独立飞出"))rocket=i;
            if(rocket<0)throw new IllegalStateException("Rocket motion owner missing");
            GROUP=rocket;READY_INVERSE=MOTION.sample(GROUP,NuclearMechanicalTimeline.LAUNCH_FRAME).invert();
        }catch(java.io.IOException ex){throw new ExceptionInInitializerError(ex);}
    }
    private NuclearRackMotion() { }
    public static Vector3f tail(float frame, float yaw) {
        var local=MOTION.sample(GROUP,frame).mul(READY_INVERSE)
                .transformPosition(new Vector3f(0,(float)NuclearModelData.DEFAULT.readyTailHeight(),0));
        return local.rotateY((float)Math.toRadians(-yaw));
    }
}
