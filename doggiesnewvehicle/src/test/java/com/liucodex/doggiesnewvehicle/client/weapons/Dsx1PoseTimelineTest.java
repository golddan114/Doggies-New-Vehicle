package com.liucodex.doggiesnewvehicle.client.weapons;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class Dsx1PoseTimelineTest {
    @Test void nativeSkinForearmExtendsDownBackAndLeftWhileThePalmStaysAtTheSupport() throws Exception {
        var geo=JsonParser.parseString(Files.readString(ASSET.resolve("geo/dsx1.geo.json"))).getAsJsonObject();
        var bones=geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        var hand=bones.asList().stream().map(e->e.getAsJsonObject())
                .filter(b->b.get("name").getAsString().equals("Lefthand")).findFirst().orElseThrow();
        var angles=hand.getAsJsonArray("rotation");
        var rotation=new Matrix4f().rotateZ((float)Math.toRadians(angles.get(2).getAsDouble()))
                .rotateY((float)Math.toRadians(-angles.get(1).getAsDouble()))
                .rotateX((float)Math.toRadians(-angles.get(0).getAsDouble()));
        // Native renderArms(false): helper Y offset=2, ModelPart pivot Y=bone+7,
        // ModelPart rotations Y=PI/Z=PI. Skin palm centre Y=8.5, sleeve centre Y=2.5.
        var palm=rotation.transformPosition(new Vector3f(0,(9-8.5f)/16,0));
        var forearm=rotation.transformPosition(new Vector3f(0,(9-2.5f)/16,0)).sub(palm);
        assertTrue(forearm.x<-.08,"support forearm must extend left of the opaque gun body");
        assertTrue(forearm.y<-.12,"support forearm must extend down");
        assertTrue(forearm.z>.2,"support forearm must extend back towards the player");
        assertTrue(palm.length()<.04,"native palm remains at the existing support anchor");
    }
    private static final Path ASSET=Path.of("src/main/resources/assets/doggiesnewvehicle");
    private static Dsx1PoseTimeline timeline() throws Exception {
        try(var in=Files.newInputStream(ASSET.resolve("animations/dsx1.animation.json"))) { return Dsx1PoseTimeline.read(in); }
    }
    private static int bone(String name) { return java.util.Arrays.asList(Dsx1PoseTimeline.BONES).indexOf(name); }
    private static Map<String,Vector3f> anchors() throws Exception {
        var entries=JsonParser.parseString(Files.readString(ASSET.resolve("geo/dsx1.geo.json"))).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        var result=new HashMap<String,Vector3f>();
        for(var entry:entries){var b=entry.getAsJsonObject();var p=b.getAsJsonArray("pivot");
            result.put(b.get("name").getAsString(),new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()).div(16));}
        return result;
    }
    private static Matrix4f transform(Dsx1PoseTimeline.Frame frame,String name,Vector3f pivot) {
        int i=bone(name);
        return new Matrix4f().translation(-frame.value(i,0)/16,frame.value(i,1)/16,frame.value(i,2)/16).translate(pivot)
                .rotateZ((float)Math.toRadians(frame.value(i,5))).rotateY((float)Math.toRadians(-frame.value(i,4)))
                .rotateX((float)Math.toRadians(-frame.value(i,3))).translate(-pivot.x,-pivot.y,-pivot.z);
    }
    private static Vector3f palm(Dsx1PoseTimeline.Frame frame,String name,Map<String,Vector3f> anchors) {
        return transform(frame,name,anchors.get(name)).transformPosition(new Vector3f(anchors.get(name)));
    }

    @Test void originalNativeTimerIsTheOnlyClockIncludingRestartAndStaleOrForeignViews() {
        assertEquals(0,Dsx1PoseTimeline.progress(25,24,0));
        assertEquals(.5,Dsx1PoseTimeline.progress(13,24,0));
        assertEquals(1,Dsx1PoseTimeline.progress(1,24,1));
        assertEquals(0,Dsx1PoseTimeline.progress(0,24,1));
        assertEquals(0,Dsx1PoseTimeline.progress(99,24,Float.NaN));
        assertEquals(Dsx1PoseTimeline.Action.IDLE,Dsx1PoseTimeline.action(false,18,42,true));
        assertEquals(Dsx1PoseTimeline.Action.BOLT,Dsx1PoseTimeline.action(true,18,42,true));
        assertEquals(Dsx1PoseTimeline.Action.RELOAD_EMPTY,Dsx1PoseTimeline.action(true,0,42,true));
        assertEquals(Dsx1PoseTimeline.Action.RELOAD_NORMAL,Dsx1PoseTimeline.action(true,0,42,false));
        assertEquals(Dsx1PoseTimeline.Action.IDLE,Dsx1PoseTimeline.action(true,0,0,true));
    }

    @Test void interruptedOrReenteredActionsCannotLeakTheOldPoseOrRestartACompletedMechanism() throws Exception {
        var timeline=timeline();var reused=new Dsx1PoseTimeline.Frame();var clean=new Dsx1PoseTimeline.Frame();
        timeline.sample(Dsx1PoseTimeline.Action.BOLT,.43,reused);
        assertTrue(reused.value(bone("rot"),5)>0);
        for(var next:Dsx1PoseTimeline.Action.values()) {
            timeline.sample(next,.42,reused);timeline.sample(next,.42,clean);
            for(int b=0;b<Dsx1PoseTimeline.BONES.length;b++)for(int c=0;c<6;c++)assertEquals(clean.value(b,c),reused.value(b,c));
            timeline.sample(next,1,reused);
            for(int b=0;b<Dsx1PoseTimeline.BONES.length;b++)for(int c=0;c<6;c++)assertEquals(0,reused.value(b,c),1e-6);
        }
        timeline.sample(Dsx1PoseTimeline.Action.RELOAD_EMPTY,.5,reused);
        timeline.sample(Dsx1PoseTimeline.Action.IDLE,0,reused);
        for(int b=0;b<Dsx1PoseTimeline.BONES.length;b++)for(int c=0;c<6;c++)assertEquals(0,reused.value(b,c));
    }

    @Test void handPalmsFollowTheActualMovingHandleAndMagazineThroughoutContactIntervals() throws Exception {
        var timeline=timeline();var frame=new Dsx1PoseTimeline.Frame();var a=anchors();
        assertEquals(a.get("grip_contact"),a.get("Righthand"));assertEquals(a.get("support_contact"),a.get("Lefthand"));
        for(var action:new Dsx1PoseTimeline.Action[]{Dsx1PoseTimeline.Action.BOLT,Dsx1PoseTimeline.Action.RELOAD_EMPTY}) {
            double offset=action==Dsx1PoseTimeline.Action.BOLT?0:2.65,duration=action==Dsx1PoseTimeline.Action.BOLT?1.2:4;
            for(int step=0;step<=160;step++) {
                double t=.16+.68*step/160;
                timeline.sample(action,(offset+t)/duration,frame);
                var handle=transform(frame,"bolt",a.get("bolt")).mul(transform(frame,"rot",a.get("rot")))
                        .transformPosition(new Vector3f(a.get("handle_contact")));
                assertTrue(handle.distance(palm(frame,"Righthand",a))<.004,"handle contact at "+t);
            }
        }
        for(var action:new Dsx1PoseTimeline.Action[]{Dsx1PoseTimeline.Action.RELOAD_NORMAL,Dsx1PoseTimeline.Action.RELOAD_EMPTY}) {
            double duration=action==Dsx1PoseTimeline.Action.RELOAD_NORMAL?3.1:4;
            for(int step=0;step<=200;step++) {
                double t=.30+1.88*step/200;timeline.sample(action,t/duration,frame);
                var magazine=transform(frame,"magazine",a.get("magazine")).transformPosition(new Vector3f(a.get("magazine_contact")));
                assertTrue(magazine.distance(palm(frame,"Lefthand",a))<.005,"magazine contact at "+t);
            }
        }
    }

    @Test void allNativeDurationsAndMechanicalReturnEndpointsAgreeWithTheActualResource() throws Exception {
        var data=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/doggiesnewvehicle/sbw/guns/dsx1.json"))).getAsJsonObject();
        var clips=JsonParser.parseString(Files.readString(ASSET.resolve("animations/dsx1.animation.json"))).getAsJsonObject().getAsJsonObject("animations");
        for(String[] row:new String[][]{{"bolt","BoltActionTime"},{"reload_normal","NormalReloadTime"},{"reload_empty","EmptyReloadTime"}})
            assertEquals(data.get(row[1]).getAsDouble()/20,clips.getAsJsonObject("animation.dsx1."+row[0]).get("animation_length").getAsDouble(),1e-8);
        var timeline=timeline();var frame=new Dsx1PoseTimeline.Frame();
        timeline.sample(Dsx1PoseTimeline.Action.BOLT,.28/1.2,frame);
        assertEquals(30,frame.value(bone("rot"),5));assertEquals(0,frame.value(bone("bolt"),2));
        timeline.sample(Dsx1PoseTimeline.Action.BOLT,.46/1.2,frame);assertEquals(4.1,frame.value(bone("bolt"),2),1e-6);
    }

    @Test void malformedAndOversizedResourcesFailAsOneUnitAndFallbackClearsAnOldFrame() throws Exception {
        String original=Files.readString(ASSET.resolve("animations/dsx1.animation.json"));
        for(String bad:new String[]{"{}",original.replace("animation.dsx1.bolt","animation.awm.shift"),
                original.replace("\"animation_length\": 1.2","\"animation_length\": 0"),
                original.replaceFirst("\"vector\": \\[[\\s\\S]*?\\]","\"vector\": [999,0,0]")}) {
            assertThrows(java.io.IOException.class,()->Dsx1PoseTimeline.read(new ByteArrayInputStream(bad.getBytes(StandardCharsets.UTF_8))));
        }
        assertThrows(java.io.IOException.class,()->Dsx1PoseTimeline.read(new ByteArrayInputStream(new byte[131073])));
        var frame=new Dsx1PoseTimeline.Frame();timeline().sample(Dsx1PoseTimeline.Action.BOLT,.5,frame);
        Dsx1PoseTimeline.EMPTY.sample(Dsx1PoseTimeline.Action.BOLT,.5,frame);
        for(int b=0;b<Dsx1PoseTimeline.BONES.length;b++)for(int c=0;c<6;c++)assertEquals(0,frame.value(b,c));
    }
}
