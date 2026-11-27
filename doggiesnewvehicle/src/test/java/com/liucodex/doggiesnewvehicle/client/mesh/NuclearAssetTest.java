package com.liucodex.doggiesnewvehicle.client.mesh;

import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMotion;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NuclearAssetTest {
    @Test void poseCacheMatchesAuthoredMotionAcrossStatesInstancesAndReload() throws Exception {
        byte[] bytes=Files.readAllBytes(assets.resolve("meshes/nuclear_motion.nsm"));
        var motion=NuclearMotion.parse(bytes);var cache=new NuclearPoseCache();cache.prepare(motion);
        for(float frame:new float[]{1,1681,1681,1903.5F,1903.6F,1903.7F,2944,1681,1})for(int group=0;group<motion.groupCount();group++){
            var previous=cache.position(group,frame);
            var actual=cache.position(group,frame);assertTrue(actual.equals(motion.sample(group,frame),1e-6F));
            assertSame(previous,actual);
            assertTrue(cache.normal(group).equals(new org.joml.Matrix3f(actual).invert().transpose(),1e-6F));
            assertSame(actual,cache.position(group,frame));
        }
        var before=cache.position(0,1);cache.prepare(NuclearMotion.parse(bytes));
        assertNotSame(before,cache.position(0,1));
        before=cache.position(0,1);cache.prepare(null);cache.prepare(motion);
        assertNotSame(before,cache.position(0,1),"Malformed reload must release the old authored pose");
    }
    @Test void independentSamplerScratchNeverLeaksBetweenDestinationsOrIntoAuthoredData() throws Exception {
        var motion=NuclearMotion.parse(Files.readAllBytes(assets.resolve("meshes/nuclear_motion.nsm")));
        var first=motion.sampler();var second=motion.sampler();
        var a=new org.joml.Matrix4f();var b=new org.joml.Matrix4f();
        for(float frame:new float[]{1,1081.25F,2680.75F,2944})for(int group=0;group<motion.groupCount();group++){
            var expected=motion.sample(group,frame);
            assertSame(a,first.sample(group,frame,a));second.sample(group,frame+1,b);
            assertTrue(a.equals(expected,1e-6F));a.zero();
            assertTrue(motion.sample(group,frame).equals(expected,1e-6F));
        }
        assertThrows(IllegalArgumentException.class,()->first.sample(-1,1,a));
        assertThrows(IllegalArgumentException.class,()->first.sample(0,Float.NaN,a));
    }
    @Test void completeRuntimePaintRetainsAuthoredSourceUvAndExplicitRuntimeTextureDerivation() throws Exception {
        var meta=JsonParser.parseString(Files.readString(assets.resolve("meshes/nuclear_parts.json"))).getAsJsonObject();
        var paint=meta.getAsJsonObject("paint");
        assertTrue(paint.get("texturePixelsChanged").getAsBoolean());
        assertEquals("5f4614dfa9576d7cac4f33193eb92780449c07f708fb97a379ee1010269931b7",meta.get("sourceSha256").getAsString());
        var resize=paint.getAsJsonObject("runtimeResize");
        assertEquals("41234f8019773349345d54ea3285f00fee6aa775494c0da4c25ccea29d444bb0",
                resize.getAsJsonObject("v20_body.png").get("authoredSha256").getAsString());
        for(String name:new String[]{"v20_body.png","v20_rocket.png"}){
            var expected=resize.getAsJsonObject(name);
            var file=assets.resolve("textures/entity/nuclear/"+name);
            String actual=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file)));
            assertEquals(expected.get("runtimeSha256").getAsString(),actual);
            var image=javax.imageio.ImageIO.read(file.toFile());assertNotNull(image);
            assertEquals(expected.get("width").getAsInt(),image.getWidth());
            assertEquals(expected.get("height").getAsInt(),image.getHeight());
        }
        for(String key:new String[]{"siloParts","missileParts"})for(var p:meta.getAsJsonArray(key)){
            var part=p.getAsJsonObject();assertTrue(part.get("tint").getAsString().matches("[0-9A-F]{6}"));
            String texture=part.get("texture").getAsString();
            assertTrue(texture.endsWith("v20_body.png")||texture.endsWith("v20_rocket.png")||texture.endsWith("v20_white.png"));
        }
    }
    @Test void materialBatchingRetainsAllPartsAndDoesNotMergeDifferentTextures() {
        var a=net.minecraft.resources.ResourceLocation.parse("test:a");
        var b=net.minecraft.resources.ResourceLocation.parse("test:b");
        var parts=java.util.List.of(new NuclearVisualData.Part("left",0,false,a,null,false),
                new NuclearVisualData.Part("middle",1,true,b,a,false),new NuclearVisualData.Part("right",2,false,a,b,true));
        var base=NuclearVisualData.batches(parts,false);assertEquals(2,base.size());
        assertEquals(java.util.List.of(parts.get(0),parts.get(2)),base.get(0).parts());
        var glow=NuclearVisualData.batches(parts,true);assertEquals(2,glow.size());
        assertEquals(2,glow.stream().mapToInt(p->p.parts().size()).sum());
        assertThrows(UnsupportedOperationException.class,()->base.get(0).parts().clear());
    }
    @Test void fieldAssetsRepairLampShellsPaintRealWheelGeometryAndFinishExtensionEarlier() throws Exception {
        var meta=JsonParser.parseString(Files.readString(assets.resolve("meshes/nuclear_parts.json"))).getAsJsonObject();
        var repairs=meta.getAsJsonObject("fieldRepairs");assertEquals(6,repairs.getAsJsonArray("inwardLampShells").size());
        assertEquals(34,repairs.getAsJsonArray("paintedWheelObjects").size());
        int wheels=0;for(var value:meta.getAsJsonArray("siloParts")){
            var p=value.getAsJsonObject();var probe=p.getAsJsonArray("lightProbe");assertEquals(3,probe.size());
            for(var coordinate:probe)assertTrue(Float.isFinite(coordinate.getAsFloat()));
            if(p.has("mechanismPaint")&&!p.get("mechanismPaint").isJsonNull()){
                wheels++;assertNotEquals("FFFFFF",p.get("tint").getAsString());assertTrue(p.get("texture").getAsString().endsWith("v20_white.png"));
            }
        }
        assertTrue(wheels>=12);
        var motion=NuclearMotion.parse(Files.readAllBytes(assets.resolve("meshes/nuclear_motion.nsm")));
        for(int group=0;group<motion.groupCount();group++){
            String name=meta.getAsJsonArray("groups").get(group).getAsJsonObject().get("name").getAsString();
            if(!name.contains("机械手"))continue;
            assertTrue(motion.sample(group,1501).equals(motion.sample(group,1591),1e-4F),name+" should already be extended");
            assertTrue(motion.sample(group,1861).equals(motion.sample(group,1903),1e-4F),name+" should retract before ignition");
        }
    }
    final Path assets=Path.of("src/artillery/resources/assets/doggiesnewvehicle");
    @Test void fractionalMotionPreservesShearedArmsAtBothSidesOfEveryKeyframe() throws Exception {
        var motion=NuclearMotion.parse(Files.readAllBytes(assets.resolve("meshes/nuclear_motion.nsm")));
        for(int group=0;group<motion.groupCount();group++)for(int frame=2;frame<2944;frame++){
            var exact=motion.sample(group,frame);
            for(float offset:new float[]{-.0001F,.0001F}){
                float[] expected=exact.get(new float[16]),actual=motion.sample(group,frame+offset).get(new float[16]);
                for(int i=0;i<16;i++)assertEquals(expected[i],actual[i],.003,
                        "Motion discontinuity group="+group+" frame="+frame+" element="+i);
            }
            assertTrue(motion.sample(group,frame+.5F).determinant3x3()>.1F,"Affine interpolation collapsed geometry");
        }
    }
    @Test void authoredStatusLampsKeepTheirNativeGroupsAndSeparateTintableGlowMaterial() throws Exception {
        var meta=JsonParser.parseString(Files.readString(assets.resolve("meshes/nuclear_parts.json"))).getAsJsonObject();
        var mesh=DnvTurretMesh.NUCLEAR_SILO.parse(Files.readAllBytes(assets.resolve("meshes/nuclear_silo.etm")));
        int lamps=0;
        for(var value:meta.getAsJsonArray("siloParts")){
            var p=value.getAsJsonObject();if(!p.has("statusLamp")||!p.get("statusLamp").getAsBoolean())continue;
            lamps++;assertFalse(p.get("rocket").getAsBoolean());
            assertTrue(p.get("texture").getAsString().endsWith("/v20_white.png"));
            assertTrue(p.get("emission").getAsString().endsWith("/v20_white.png"));
            var stream=mesh.part(p.get("name").getAsString()).main();assertTrue(stream.length>0);
            for(int i=0;i<stream.length;i+=8)assertTrue(Float.isFinite(stream[i+1]));
        }
        assertEquals(2,lamps,"V20 fixed status lights and authored moving light surfaces keep distinct groups");
    }
    @Test void exportedMeshesHaveFiniteUnitNormalsUvAndExactPartMapping() throws Exception {
        var metadata=JsonParser.parseString(Files.readString(assets.resolve("meshes/nuclear_parts.json"))).getAsJsonObject();
        for(String name:new String[]{"nuclear_silo","nuclear_missile"}){
            byte[] bytes=Files.readAllBytes(assets.resolve("meshes/"+name+".etm"));
            var mesh=(name.equals("nuclear_silo")?DnvTurretMesh.NUCLEAR_SILO:DnvTurretMesh.NUCLEAR_MISSILE).parse(bytes);
            var parts=metadata.getAsJsonArray(name.equals("nuclear_silo")?"siloParts":"missileParts");assertEquals(parts.size(),mesh.partCount());assertTrue(mesh.triangleCount()>1000);
            for(var value:parts){var part=value.getAsJsonObject();float[] stream=mesh.part(part.get("name").getAsString()).main();assertTrue(stream.length>0);
                for(int i=0;i<stream.length;i+=8){for(int j=0;j<8;j++)assertTrue(Float.isFinite(stream[i+j]));double n=Math.sqrt(stream[i+3]*stream[i+3]+stream[i+4]*stream[i+4]+stream[i+5]*stream[i+5]);assertEquals(1,n,1e-4);}
                assertTrue(Files.isRegularFile(assets.resolve(part.get("texture").getAsString().split(":",2)[1])));
                if(!part.get("emission").isJsonNull())assertTrue(Files.isRegularFile(assets.resolve(part.get("emission").getAsString().split(":",2)[1])));
            }
        }
        assertEquals(5,metadata.getAsJsonArray("nozzles").size());assertTrue(metadata.get("rocketHeight").getAsDouble()>7);
    }
    @Test void independentMotionReturnsMechanismsButNeverPullsFlyingRocketBack() throws Exception {
        var motion=NuclearMotion.parse(Files.readAllBytes(assets.resolve("meshes/nuclear_motion.nsm")));
        var meta=JsonParser.parseString(Files.readString(assets.resolve("meshes/nuclear_parts.json"))).getAsJsonObject();assertEquals(meta.getAsJsonArray("groups").size(),motion.groupCount());
        int flight=-1,radar=-1;for(int i=0;i<motion.groupCount();i++){String n=meta.getAsJsonArray("groups").get(i).getAsJsonObject().get("name").getAsString();if(n.equals("CTRL_展示弹_独立飞出"))flight=i;if(n.equals("CTRL_前置雷达_连续旋转"))radar=i;}
        assertTrue(flight>0);assertTrue(radar>0);assertTrue(motion.sample(flight,2264).m31()>15);
        assertTrue(motion.sample(flight,2264).equals(motion.sample(flight,2944),1e-4F));assertFalse(motion.sample(radar,1).equals(motion.sample(radar,61),1e-3F));
        for(int i=0;i<motion.groupCount();i++)for(float frame:new float[]{1,211,781,1381,1681,1903,2264,2664.5F,2944})assertTrue(motion.sample(i,frame).isFinite());
    }
    @Test void corruptOrTruncatedMotionIsRejectedBeforeLargeAllocation() throws Exception {
        var bytes=Files.readAllBytes(assets.resolve("meshes/nuclear_motion.nsm"));assertThrows(RuntimeException.class,()->NuclearMotion.parse(java.util.Arrays.copyOf(bytes,100)));
        bytes[0]=0;assertThrows(IllegalArgumentException.class,()->NuclearMotion.parse(bytes));
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(0,0x314d534e).putInt(8,1000000);assertThrows(IllegalArgumentException.class,()->NuclearMotion.parse(bytes));
    }
    @Test void wreckClockFreezesActualRadarAndMechanicalPosesAcrossTime() throws Exception {
        var motion=NuclearMotion.parse(Files.readAllBytes(assets.resolve("meshes/nuclear_motion.nsm")));
        var meta=JsonParser.parseString(Files.readString(assets.resolve("meshes/nuclear_parts.json"))).getAsJsonObject();
        int radar=-1;for(int i=0;i<motion.groupCount();i++)if(meta.getAsJsonArray("groups").get(i).getAsJsonObject()
                .get("name").getAsString().equals("CTRL_前置雷达_连续旋转"))radar=i;
        assertTrue(radar>=0);
        var frozen=motion.sample(radar,com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMechanicalTimeline.radarFrame(101,101));
        for(double time:new double[]{101,101.5,120,200,10000}){
            assertTrue(frozen.equals(motion.sample(radar,com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMechanicalTimeline.radarFrame(101,time)),1e-6F));
            double remaining=com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMechanicalTimeline.remaining(44,101,time,false);
            assertEquals(44,remaining);
            float frame=com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMechanicalTimeline.frame(
                    com.liucodex.doggiesnewvehicle.artillery.silo.SiloCycle.Phase.OPENING,remaining,
                    com.liucodex.doggiesnewvehicle.artillery.silo.NuclearRules.DEFAULT);
            for(int group=0;group<motion.groupCount();group++)assertTrue(motion.sample(group,frame).equals(motion.sample(group,
                    com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMechanicalTimeline.frame(
                            com.liucodex.doggiesnewvehicle.artillery.silo.SiloCycle.Phase.OPENING,44,
                            com.liucodex.doggiesnewvehicle.artillery.silo.NuclearRules.DEFAULT)),1e-6F));
        }
        assertFalse(motion.sample(radar,com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMechanicalTimeline.radarFrame(-1,101))
                .equals(motion.sample(radar,com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMechanicalTimeline.radarFrame(-1,121)),1e-4F));
        assertEquals(24,com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMechanicalTimeline.remaining(44,101,121,true));
    }
}
