package com.liucodex.doggiesnewvehicle.entity;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.compat.ExactSbwTransformFixture;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DnvDragonTurretsTest {
    private static final Path RES=Path.of("src/artillery/resources");
    private static Vec3 vec(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static void near(Vec3 a,Vec3 b){assertTrue(a.distanceTo(b)<.002,a+" != "+b);}
    @Test void allRuntimeGroupsIncludingInternalLinksMatchIndependentBlenderOracle() throws Exception {
        var oracle=JsonParser.parseString(Files.readString(Path.of("src/test/resources/dragon-turrets/blender-pose-oracle.json"))).getAsJsonObject();
        for(var p:DnvDragonTurretProfile.ALL.values()) {
            var o=oracle.getAsJsonObject(p.model);var neutral=o.getAsJsonObject("neutral");
            for(var pose:o.getAsJsonArray("poses")) {
                var sample=pose.getAsJsonObject();float yaw=sample.get("yaw").getAsFloat(),elevation=sample.get("elevation").getAsFloat();
                var groups=sample.getAsJsonObject("groups");
                for(String group:p.groups) {
                    var points=neutral.getAsJsonArray(group);var expected=groups.getAsJsonArray(group);
                    for(int i=0;i<points.size();i++) {
                        Vec3 q=p.transformGroup(vec(points.get(i).getAsJsonArray()),group,elevation,0);
                        if(!group.equals("base_static"))q=p.yawed(q,-yaw);
                        near(vec(expected.get(i).getAsJsonArray()),q);
                    }
                }
            }
        }
    }
    @Test void everyNativeBarrelTransformMatchesTheAuthoredMuzzleAtVariedBodyAndTurretAngles() throws Exception {
        for(var p:DnvDragonTurretProfile.ALL.values()) {
            var data=JsonParser.parseString(Files.readString(RES.resolve("data/doggievehicle/sbw/vehicles/"+p.entity+".json"))).getAsJsonObject();
            var f=new ExactSbwTransformFixture();f.vehicle.turretPos=vec(data.getAsJsonArray("TurretPos"));f.vehicle.barrelPos=vec(data.getAsJsonArray("BarrelPos"));
            var positions=data.getAsJsonObject("Weapons").getAsJsonObject("Main").getAsJsonObject("ShootPos").getAsJsonArray("Positions");
            assertEquals(p.barrels,positions.size());
            for(float body:new float[]{0,43,180})for(float yaw:new float[]{-137,0,90})for(float elev:new float[]{-5,0,30,60}) {
                f.vehicle.body.identity().rotateY(Math.toRadians(-body));f.vehicle.turretYaw=yaw;f.vehicle.turretPitch=-elev;
                for(int barrel=0;barrel<p.barrels;barrel++) {
                    Vec3 local=vec(positions.get(barrel).getAsJsonArray());near(local,p.projectiles.get(barrel));
                    near(p.posed(p.pitch.add(local),body-yaw,elev),f.nativeBarrelPoint(local));
                    assertEquals(.03,local.distanceTo(p.muzzles.get(barrel)),1e-5);
                }
            }
        }
    }
    @Test void m1RetainsItsIdentityAndSixProfilesAreCompleteWithoutASeventhM1() throws Exception {
        assertEquals(6,DnvDragonTurretProfile.ALL.size());assertEquals(14,DnvDragonTurretProfile.ALL.values().stream().mapToInt(p->p.barrels).sum());
        assertEquals("m1_yunlong",DnvDragonTurretProfile.M1.entity);
        assertFalse(Files.exists(RES.resolve("data/doggievehicle/sbw/vehicles/dragon_m1_open.json")));
        var inputs=JsonParser.parseString(Files.readString(RES.resolve("data/doggiesnewvehicle/energy/input_limits.json"))).getAsJsonObject();
        for(var p:DnvDragonTurretProfile.ALL.values()) {
            var data=JsonParser.parseString(Files.readString(RES.resolve("data/doggievehicle/sbw/vehicles/"+p.entity+".json"))).getAsJsonObject();
            assertEquals(data.get("MaxEnergy").getAsInt(),inputs.get("doggievehicle:"+p.entity).getAsInt());
            if(!p.entity.equals("m1_yunlong"))for(var shake:data.getAsJsonObject("Weapons").getAsJsonObject("Main").getAsJsonArray("ShootShake"))
                assertEquals(0,shake.getAsFloat(),"Existing DNV rule: new turrets do not shake the camera");
            assertTrue(Files.size(RES.resolve("assets/doggiesnewvehicle/meshes/"+p.entity+".etm"))>1000);
            assertTrue(Files.size(RES.resolve("assets/doggiesnewvehicle/textures/entity/"+p.entity+".png"))>1000);
            assertTrue(Files.exists(RES.resolve("assets/doggiesnewvehicle/textures/entity/"+p.entity+"_emission.png")));
            if(!p.entity.equals("m1_yunlong"))assertTrue(Files.exists(RES.resolve("data/doggiesnewvehicle/recipes/"+p.entity+"_vehicle_assembling.json")));
        }
        assertEquals(2,DnvDragonTurretProfile.ALL.get("dragon_bofors_twin").seats);
        assertEquals(2,DnvDragonTurretProfile.ALL.get("dragon_bofors_quad").seats);
        assertEquals(1,DnvDragonTurretProfile.ALL.get("dragon_flak38").seats);
    }
    @Test void mountedLightTurretCameraIsOnTheMeanBoreAheadOfItsSightAtAimingLimits() throws Exception {
        for(var p:DnvDragonTurretProfile.ALL.values())if(p.seats>0){
            var data = JsonParser.parseString(Files.readString(RES.resolve("data/doggievehicle/sbw/vehicles/"+p.entity+".json")))
                    .getAsJsonObject();
            var elevationBounds = data.getAsJsonArray("TurretPitchRange");
            Vec3 mean=Vec3.ZERO;for(var muzzle:p.projectiles)mean=mean.add(muzzle);
            mean=p.pitch.add(mean.scale(1.0/p.barrels));
            assertTrue(p.ads.subtract(mean).multiply(1,1,0).length()>.4,"Old observer position must remain distinguishable");
            Vec3 eye=p.clearAdsPosition();assertEquals(mean.x,eye.x,1e-8);assertEquals(mean.y,eye.y,1e-8);
            assertTrue(eye.z>p.maximum.z+.2,"Near plane is wholly ahead of the authored geometry");
            for(float yaw:new float[]{-179,-60,0,93,179})for(float elevation:new float[]{
                    elevationBounds.get(0).getAsFloat(),0,35,elevationBounds.get(1).getAsFloat()}){
                var origin=p.posed(mean,yaw,elevation);var camera=p.posed(eye,yaw,elevation);
                var axis=p.posed(mean.add(0,0,1),yaw,elevation).subtract(origin).normalize();
                assertTrue(camera.subtract(origin).dot(axis)>0);
                assertEquals(0,camera.subtract(origin).cross(axis).length(),2e-5);
            }
        }
    }
    @Test void barrelVisualEventsStayIndependentExpireAndResetWithoutReplay(){
        var recoil=new DnvBarrelRecoil();recoil.shot(0,100);
        assertEquals(1,recoil.progress(0,102,0,10),1e-6);assertEquals(0,recoil.progress(1,102,0,10));
        recoil.shot(1,103);assertTrue(recoil.progress(0,105,0,10)>0);assertEquals(1,recoil.progress(1,105,0,10),1e-6);
        assertEquals(0,recoil.progress(0,110,0,10));recoil.reset();assertEquals(0,recoil.progress(1,105,0,10));
        assertEquals(0,recoil.progress(2,Integer.MAX_VALUE,0,42));
    }
    @Test void everyPinnedNativeClientHasExactlyOneSuppressiblePredictedSoundAndAcceptedEventWire() throws Exception {
        var jars=java.util.List.of(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path());
        for(var jar:jars)try(var zip=new java.util.zip.ZipFile(jar.toFile())) {
            var node=new org.objectweb.asm.tree.ClassNode();
            try(var in=zip.getInputStream(zip.getEntry("com/atsuishio/superbwarfare/event/ClientEventHandler.class"))){new org.objectweb.asm.ClassReader(in.readAllBytes()).accept(node,0);}
            var shoot=node.methods.stream().filter(m->m.name.equals("clientShootVehicle")).findFirst().orElseThrow();
            int predicted=0,packets=0;
            for(var ins:shoot.instructions)if(ins instanceof org.objectweb.asm.tree.MethodInsnNode call){
                if(call.name.equals("playVehicleClientSounds"))predicted++;
                if(call.name.equals("sendPacketToServer"))packets++;
            }
            assertEquals(1,predicted,jar.toString());assertEquals(1,packets,jar.toString());
            var event=new org.objectweb.asm.tree.ClassNode();
            try(var in=zip.getInputStream(zip.getEntry("com/atsuishio/superbwarfare/api/event/ClientVehicleFireEvent.class"))){new org.objectweb.asm.ClassReader(in.readAllBytes()).accept(event,0);}
            for(String getter:java.util.List.of("getVehicle","getShooter","getIndex"))assertTrue(event.methods.stream().anyMatch(m->m.name.equals(getter)),jar+" "+getter);
        }
    }
}
