package com.liucodex.doggiesnewvehicle.armored;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.compat.ExactSbwTransformFixture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.junit.jupiter.api.Test;

class BlizzardRigTest {
    @Test void originalHatchVerticesSlideLaterallyWithoutRotatingAtAllFortyTwoOpenings() throws Exception {
        byte[] bytes;try(var input=getClass().getResourceAsStream("/dnv-blizzard-hinge-source-v15.json")){bytes=input.readAllBytes();}
        var source=com.google.gson.JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        byte[] meshBytes;try(var input=getClass().getResourceAsStream("/assets/doggiesnewvehicle/meshes/blizzard.etm")){meshBytes=input.readAllBytes();}
        var parser=com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh.class.getDeclaredMethod("parse",byte[].class);parser.setAccessible(true);
        var mesh=(com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh.MeshData)
                parser.invoke(com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh.BLIZZARD,meshBytes);
        var samples=source.getAsJsonArray("samples");assertEquals(42,samples.size());
        for(var element:samples){
            var row=element.getAsJsonObject();String group=row.get("group").getAsString();double opening=row.get("opening").getAsDouble();
            // Neutral and open positions were measured from the current recessed author.
            var closed=row.getAsJsonArray("sampleVerticesClosedMinecraft");
            float[] stream=mesh.part(group).main();
            for(int i=0;i<closed.size();i++){
                var p=closed.get(i).getAsJsonArray();
                Vec3 neutral=new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());
                double closest=Double.POSITIVE_INFINITY;
                for(int offset=0;offset<stream.length;offset+=8)closest=Math.min(closest,neutral.distanceTo(new Vec3(stream[offset],stream[offset+1],stream[offset+2])));
                assertEquals(0,closest,1e-5,"Source vertex is missing from the runtime hatch");
                Vec3 actual=BlizzardRig.point(BlizzardRig.INSTANCE.local(group,0,0,opening),neutral);
                double t=opening*opening*(3-2*opening);
                assertEquals(neutral.x+(group.equals("HatchLeft")?-.46:.46)*t,actual.x,1e-6);
                assertEquals(neutral.y,actual.y,1e-6);assertEquals(neutral.z,actual.z,1e-6);
                var measured=row.getAsJsonArray("sampleVerticesMinecraft").get(i).getAsJsonArray();
                assertEquals(0,actual.distanceTo(new Vec3(measured.get(0).getAsDouble(),
                        measured.get(1).getAsDouble(),measured.get(2).getAsDouble())),2e-6);
                assertEquals(new Vec3(0,0,-1),BlizzardRig.direction(BlizzardRig.INSTANCE.local(group,0,0,opening),new Vec3(0,0,-1)));
            }
        }
    }
    @Test void coversStayRecessedBehindTheEnergyFrameAtEveryOpeningAndLod() throws Exception {
        byte[] rigBytes;try(var input=getClass().getResourceAsStream("/assets/doggiesnewvehicle/meshes/blizzard_rig.json")){rigBytes=input.readAllBytes();}
        double frame=com.google.gson.JsonParser.parseString(new String(rigBytes,java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().get("rear_frame_minecraft_z").getAsDouble();
        for(String file:new String[]{"blizzard","blizzard_lod1","blizzard_lod2"}){
        byte[] bytes;try(var input=getClass().getResourceAsStream("/assets/doggiesnewvehicle/meshes/"+file+".etm")){bytes=input.readAllBytes();}
        var parser=com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh.class.getDeclaredMethod("parse",byte[].class);parser.setAccessible(true);
        var mesh=(com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh.MeshData)
                parser.invoke(com.liucodex.doggiesnewvehicle.client.mesh.DnvTurretMesh.BLIZZARD,bytes);
        for(int step=0;step<=100;step++)for(String group:new String[]{"HatchLeft","HatchRight"}){
            double opening=step/100D;
            var hatch=mesh.part(group).main();var transform=BlizzardRig.INSTANCE.local(group,0,0,opening);
            for(int i=0;i<hatch.length;i+=8){
                var point=BlizzardRig.point(transform,new Vec3(hatch[i],hatch[i+1],hatch[i+2]));
                assertTrue(point.z>frame+.01,"Cover must remain inside the rear frame: "+file);
                assertEquals(hatch[i+2],point.z,1e-9,"No outward unseating during mode switch");
            }
        }
        }
    }

    @Test void slidesFollowVehicleLeftAndRightAtEveryCardinalHeadingAndClearTheRearOpening() {
        var rig=BlizzardRig.INSTANCE;
        for(int heading:new int[]{0,90,180,270})for(String group:new String[]{"HatchLeft","HatchRight"}){
            var body=new Matrix4d().rotateY(Math.toRadians(-heading));
            var local=rig.local(group,0,0,1);
            var actual=BlizzardRig.point(new Matrix4d(body).mul(local),Vec3.ZERO);
            var right=BlizzardRig.direction(body,new Vec3(1,0,0));
            assertEquals(group.equals("HatchLeft")?-.46:.46,actual.dot(right),1e-9);
            assertEquals(0,actual.y,1e-9);
        }
        assertTrue(BlizzardRig.point(rig.local("HatchLeft",0,0,1),Vec3.ZERO).x<-.43548483);
        assertTrue(BlizzardRig.point(rig.local("HatchRight",0,0,1),Vec3.ZERO).x>.43548483);
        assertTrue(rig.local("HatchLeft",0,0,-1).equals(rig.local("HatchLeft",0,0,0)));
        assertTrue(rig.local("HatchRight",0,0,2).equals(rig.local("HatchRight",0,0,1)));
        assertTrue(rig.local("HatchLeft",0,0,Double.NaN).isFinite());
    }

    @Test void allCardinalHeadingsAndElevationLimitsMatchPinnedNativeBarrelTransform() throws Exception {
        var rig = BlizzardRig.INSTANCE;
        var fixture = new ExactSbwTransformFixture();
        fixture.vehicle.turretPos = rig.yaw; fixture.vehicle.barrelPos = rig.barrel.subtract(rig.yaw);
        Vec3 muzzle = rig.anchors.get("main_gun_muzzle").position();
        assertEquals(50.8, rig.maxElevation);
        for (int heading : new int[] {0, 90, 180, 270}) for (int yaw : new int[] {-180, -90, 0, 90, 180})
            for (double elevation : new double[] {rig.minElevation(yaw), 0, 25, 50.8}) {
                fixture.vehicle.body.identity().translate(30, 50, -20).rotateY(Math.toRadians(-heading));
                fixture.vehicle.turretYaw = yaw; fixture.vehicle.turretPitch = (float) -elevation;
                var matrix = new Matrix4d(fixture.vehicle.body).mul(rig.local("Barrel", yaw, (float) elevation));
                Vec3 actual = BlizzardRig.point(matrix, muzzle), nativePoint = fixture.nativeBarrelPoint(muzzle.subtract(rig.barrel));
                assertEquals(0, actual.distanceTo(nativePoint), 1e-5);
            }
    }
    @Test void depressionRemainsUsefulAndPeriodicAcrossTheFullTurretSweep() {
        var rig = BlizzardRig.INSTANCE;
        for (int yaw = -360; yaw < 360; yaw++) {
            assertTrue(rig.minElevation(yaw) <= -5, "must retain practical depression at every heading");
            assertTrue(rig.minElevation(yaw) >= -25);
            assertEquals(rig.minElevation(yaw), rig.minElevation(yaw + 360));
        }
        assertEquals(0, rig.minElevation(Double.NaN));
    }
    @Test void launchPortsRemainFixedOnTurretAndEveryNozzlePointsOutward() {
        var rig = BlizzardRig.INSTANCE;
        assertEquals(12, rig.anchors.size());
        for (var entry : rig.anchors.entrySet()) {
            var anchor = entry.getValue(); assertEquals(1, anchor.direction().length(), 1e-7);
            if (anchor.role().equals("lift_exhaust")) assertTrue(anchor.direction().y < -.999);
            if (anchor.role().equals("axial_exhaust")) assertTrue(anchor.direction().z < -.999);
            if (anchor.role().equals("missile_muzzle")) {
                assertEquals("Turret", anchor.group());
                assertTrue(anchor.direction().y > .24);
                assertEquals(BlizzardRig.point(rig.local("Turret", 90, 0), anchor.position()),
                        BlizzardRig.point(rig.local("Turret", 90, 50.8), anchor.position()));
            }
        }
    }
}
