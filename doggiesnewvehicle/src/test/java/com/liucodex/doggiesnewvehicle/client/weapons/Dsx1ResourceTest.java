package com.liucodex.doggiesnewvehicle.client.weapons;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

class Dsx1ResourceTest {
    private static final Path RES = Path.of("src/main/resources");
    private static JsonObject json(String path) throws Exception { return JsonParser.parseString(Files.readString(RES.resolve(path))).getAsJsonObject(); }

    @Test void hudIconMatchesNativeFourToOneConsumerAndKeepsTransparentMargins() throws Exception {
        var image=javax.imageio.ImageIO.read(RES.resolve("assets/doggiesnewvehicle/textures/gun_icon/dsx1.png").toFile());
        assertEquals(4*image.getHeight(),image.getWidth());
        assertTrue(image.getColorModel().hasAlpha());
        int minX=image.getWidth(),minY=image.getHeight(),maxX=0,maxY=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>32){
            minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
        }
        assertTrue(minX>0&&minY>0&&maxX<image.getWidth()-1&&maxY<image.getHeight()-1);
        assertTrue(maxX-minX>image.getWidth()*.8,"recognizable gun silhouette");
    }

    @Test void supportForearmLeavesThePalmBelowAndBehindTheForegripRatherThanAlongTheSight() throws Exception {
        var bones=json("assets/doggiesnewvehicle/geo/dsx1.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        JsonObject hand=null;
        for(var b:bones)if(b.getAsJsonObject().get("name").getAsString().equals("Lefthand"))hand=b.getAsJsonObject();
        assertNotNull(hand);
        var a=hand.getAsJsonArray("rotation");
        var elbow=new org.joml.Vector3f(0,.625f,0);
        new org.joml.Matrix4f().rotateZ((float)Math.toRadians(a.get(2).getAsDouble()))
                .rotateY((float)Math.toRadians(-a.get(1).getAsDouble()))
                .rotateX((float)Math.toRadians(-a.get(0).getAsDouble())).transformDirection(elbow);
        assertTrue(elbow.y<-.25&&elbow.z>.35&&elbow.x<-.10,"left forearm must drop left, below and behind the foregrip: "+elbow);
    }

    @Test void sixOlderContainersResolveDistinctTransparentModelPreviewsAtTheNativeLookupPath() throws Exception {
        var hashes=new java.util.HashSet<String>();
        for(String id:new String[]{"50_changfeng_cannon","storm_turret","jingzhe_turret","project612","apocalypse","blizzard"}){
            var file=Path.of("src/artillery/resources/assets/superbwarfare/textures/vehicle_icon/container",id+".png");
            var image=javax.imageio.ImageIO.read(file.toFile());
            assertEquals(128,image.getWidth());assertEquals(128,image.getHeight());assertTrue(image.getColorModel().hasAlpha());
            int visible=0,transparent=0;
            for(int y=0;y<128;y++)for(int x=0;x<128;x++){
                int a=image.getRGB(x,y)>>>24;if(a>128)visible++;if(a==0)transparent++;
            }
            assertTrue(visible>500&&transparent>4000,id+" needs a visible model and clear surrounding area");
            assertTrue(hashes.add(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))),id);
        }
    }

    @Test void everyBoneOwnsOneMeshGroupAndTheHandleInheritsBoltTranslation() throws Exception {
        var bones = json("assets/doggiesnewvehicle/geo/dsx1.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        Map<String, JsonObject> names = new HashMap<>();
        for (var entry : bones) {
            var bone = entry.getAsJsonObject();
            assertNull(names.put(bone.get("name").getAsString(), bone));
            assertFalse(bone.has("cubes"), "Native AWM geometry must not replace DSX1");
        }
        assertEquals("bolt", names.get("rot").get("parent").getAsString());
        assertEquals("rot", names.get("dsx_handle").get("parent").getAsString());
        assertEquals("bolt", names.get("dsx_bolt").get("parent").getAsString());
        assertEquals("magazine", names.get("dsx_magazine").get("parent").getAsString());
        assertEquals("0", names.get("dsx_body").get("parent").getAsString());
        assertEquals("0", names.get("Lefthand").get("parent").getAsString());
        assertEquals("0", names.get("Righthand").get("parent").getAsString());
        var animations = json("assets/doggiesnewvehicle/animations/dsx1.animation.json").getAsJsonObject("animations");
        for (var animation : animations.entrySet()) {
            var channels = animation.getValue().getAsJsonObject().getAsJsonObject("bones");
            if (channels != null) for (String name : channels.keySet()) assertTrue(names.containsKey(name), name);
        }
        assertTrue(animations.keySet().stream().allMatch(n -> n.startsWith("animation.dsx1.")));
        var shift = animations.getAsJsonObject("animation.dsx1.bolt").getAsJsonObject("bones");
        assertTrue(shift.getAsJsonObject("rot").has("rotation"));
        assertTrue(shift.getAsJsonObject("bolt").has("position"));
        assertFalse(shift.has("dsx_body"));
    }

    @Test void ownResourcesResolveIncludingLodIconAndFixedScopeStats() throws Exception {
        var model = json("assets/doggiesnewvehicle/sbw/guns/dsx1.json").getAsJsonObject("Model");
        for (var entry : model.entrySet()) {
            String path = entry.getValue().getAsString();
            assertTrue(path.startsWith("doggiesnewvehicle:"));
            assertTrue(Files.isRegularFile(RES.resolve("assets/" + path.replace(':','/'))), path);
        }
        var data = json("data/doggiesnewvehicle/sbw/guns/dsx1.json");
        assertEquals(5, data.get("Magazine").getAsInt());
        assertEquals(24, data.get("BoltActionTime").getAsInt());
        assertEquals(4, data.get("MinZoom").getAsInt());
        assertEquals(8, data.get("MaxZoom").getAsInt());
        assertEquals("@SniperAmmo", data.get("AmmoType").getAsString());
        assertTrue(Files.isRegularFile(RES.resolve("assets/" + data.get("Icon").getAsString().replace(':','/'))));
        for (String lang : new String[] {"zh_cn", "en_us"}) assertTrue(json("assets/doggiesnewvehicle/lang/" + lang + ".json").has("item.doggiesnewvehicle.dsx1"));
    }

    @Test void bothExactForgeSbwReleasesExposeTheNativeGunAndRenderHooks() throws Exception {
        for (String jar : new String[] {"superbwarfare-0.8.9.1-hotfix-993063bed.jar", "superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all.jar"}) {
            try (var zip = new ZipFile(Path.of("libs",jar).toFile())) {
                checkMethod(zip,"item/gun/GunItem","init","(Lcom/atsuishio/superbwarfare/data/gun/GunData;)V");
                checkMethod(zip,"item/gun/sniper/AwmItem","getRenderer","()Ljava/util/function/Supplier;");
                checkMethod(zip,"item/gun/sniper/AwmItem","registerControllers","(Lsoftware/bernie/geckolib/core/animation/AnimatableManager$ControllerRegistrar;)V");
                checkMethod(zip,"client/renderer/CustomGunRenderer","<init>","(Lsoftware/bernie/geckolib/model/GeoModel;)V");
                checkMethod(zip,"data/gun/subdata/Attachment","set","(Lcom/atsuishio/superbwarfare/data/gun/value/AttachmentType;I)V");
                checkMethod(zip,"client/model/item/CustomGunModel","handleAnimations","(Lcom/atsuishio/superbwarfare/item/gun/GunGeoItem;JLsoftware/bernie/geckolib/core/animation/AnimationState;)V");
            }
        }
    }

    @Test void independentReticleAndClipsDoNotOverrideAnyNativeSbwScopeResource() throws Exception {
        assertFalse(Files.exists(RES.resolve("assets/superbwarfare/textures/crosshair/sniper.png")));
        assertFalse(Files.exists(RES.resolve("assets/superbwarfare/animations/awm.animation.json")));
        var image=javax.imageio.ImageIO.read(RES.resolve("assets/doggiesnewvehicle/textures/crosshair/dsx1_kh4.png").toFile());
        assertEquals(1024,image.getWidth());assertEquals(1024,image.getHeight());assertTrue(image.getColorModel().hasAlpha());
        int clear=0;for(int y=0;y<1024;y++)for(int x=0;x<1024;x++)if((image.getRGB(x,y)>>>24)==0)clear++;
        assertTrue(clear>1024*1024*.98);
        int lime=0,outline=0;
        for(int y=0;y<1024;y++)for(int x=0;x<1024;x++){
            int rgba=image.getRGB(x,y),a=rgba>>>24,r=(rgba>>>16)&255,g=(rgba>>>8)&255,b=rgba&255;
            if(a>200&&g>220&&r>100&&r<210&&b<110)lime++;
            if(a>180&&r<35&&g<50&&b<55)outline++;
        }
        assertTrue(lime>100&&outline>100,"Accepted lime core and alpha-blended dark outline must both exist");
        var model=json("assets/doggiesnewvehicle/models/item/dsx1_base.json").getAsJsonObject("display");
        for(String side:new String[]{"firstperson_lefthand","thirdperson_lefthand"}) {
            var transform=model.getAsJsonObject(side);
            if(transform.has("scale"))for(var scale:transform.getAsJsonArray("scale"))assertTrue(scale.getAsDouble()>0);
        }
        var animations=json("assets/doggiesnewvehicle/animations/dsx1.animation.json").getAsJsonObject("animations");
        assertEquals(4,animations.size());assertTrue(animations.keySet().stream().allMatch(n->n.startsWith("animation.dsx1.")));
    }

    private static void checkMethod(ZipFile zip, String name, String method, String descriptor) throws Exception {
        var entry = zip.getEntry("com/atsuishio/superbwarfare/" + name + ".class");
        assertNotNull(entry, name);
        var node = new ClassNode();
        new ClassReader(zip.getInputStream(entry)).accept(node, ClassReader.SKIP_CODE);
        assertTrue(node.methods.stream().anyMatch(m -> m.name.equals(method) && m.desc.equals(descriptor)), name + "." + method);
    }
}
