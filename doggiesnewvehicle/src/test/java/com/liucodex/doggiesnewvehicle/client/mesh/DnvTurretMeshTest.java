package com.liucodex.doggiesnewvehicle.client.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DnvTurretMeshTest {
    @Test void mountedCameraAnchorsClearTheActualRuntimeMeshAtNeutral() throws Exception {
        for (var profile : com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile.ALL.values()) {
            if (profile.seats == 0) continue;
            var mesh = DnvTurretMesh.DRAGON.get(profile.entity).parse(productionMesh(profile.entity));
            for (String part : profile.groups) {
                assertTrue(profile.clearAdsPosition().z > maximumAxis(mesh.part(part), 2) + .2,
                        profile.entity + "/" + part);
            }
        }
        var rig = com.liucodex.doggiesnewvehicle.armored.ApocalypseRig.INSTANCE;
        for (String meshName : new String[]{"apocalypse", "apocalypse_lod1", "apocalypse_lod2"}) {
            var mesh = DnvTurretMesh.APOCALYPSE.parse(productionMesh(meshName));
            assertTrue(rig.forwardCameraAnchor().z > maximumAxis(mesh.part("yaw_assembly"), 2) + .25);
            assertTrue(rig.forwardCameraAnchor().y > maximumAxis(mesh.part("base_static"), 1) + .5);
        }
    }

    private static double maximumAxis(DnvTurretMesh.MeshPart part, int axis) {
        double result = Double.NEGATIVE_INFINITY;
        for (float[] stream : new float[][] {part.main(), part.emissive()}) {
            for (int i = axis; i < stream.length; i += DnvTurretMesh.FLOATS_PER_VERTEX) {
                result = Math.max(result, stream[i]);
            }
        }
        assertTrue(Double.isFinite(result));
        return result;
    }

    @Test void rearLiquidTankHasOutwardUpperAndLowerFacesAtEveryLod() throws Exception {
        for(String name:new String[]{"blizzard","blizzard_lod1","blizzard_lod2"}) {
            float[] stream=DnvTurretMesh.BLIZZARD.parse(productionMesh(name)).part("Turret").main();
            for(int direction:new int[]{-1,1}) {
                var origin=new org.joml.Vector3d(0,direction<0?2.1:1.3,-2.4);
                var ray=new org.joml.Vector3d(0,direction,0);
                double nearest=Double.POSITIVE_INFINITY;org.joml.Vector3d hitNormal=null;
                for(int i=0;i<stream.length;i+=24) {
                    var a=new org.joml.Vector3d(stream[i],stream[i+1],stream[i+2]);
                    var ab=new org.joml.Vector3d(stream[i+8],stream[i+9],stream[i+10]).sub(a);
                    var ac=new org.joml.Vector3d(stream[i+16],stream[i+17],stream[i+18]).sub(a);
                    var cross=new org.joml.Vector3d(ray).cross(ac);double det=ab.dot(cross);
                    if(Math.abs(det)<1e-9)continue;
                    var delta=new org.joml.Vector3d(origin).sub(a);double u=delta.dot(cross)/det;
                    var q=new org.joml.Vector3d(delta).cross(ab);double v=ray.dot(q)/det,t=ac.dot(q)/det;
                    if(u<0||v<0||u+v>1||t<0||t>=nearest)continue;
                    nearest=t;hitNormal=new org.joml.Vector3d(stream[i+3],stream[i+4],stream[i+5]);
                }
                assertTrue(nearest<.4,name+" rear tank surface missing");
                assertTrue(hitNormal.y*direction<-.2,name+" rear tank normal points into the tank");
            }
        }
    }
    @Test
    void blizzardKeepsThreeRigidGroupsTwoHatchesAndOnlyAuditedLightTriangles() throws Exception {
        var mesh = DnvTurretMesh.BLIZZARD.parse(productionMesh("blizzard"));
        assertEquals(5, mesh.partCount());
        assertEquals(3412 + 200, mesh.triangleCount());
        for (var name : new String[] {"Body", "Turret", "Barrel", "HatchLeft", "HatchRight"}) assertPart(mesh, name);
        assertEquals(172, mesh.part("Body").emissive().length / DnvTurretMesh.FLOATS_PER_VERTEX / 3);
        assertEquals(8, mesh.part("Turret").emissive().length / DnvTurretMesh.FLOATS_PER_VERTEX / 3);
        assertEquals(20, mesh.part("Barrel").emissive().length / DnvTurretMesh.FLOATS_PER_VERTEX / 3);
    }
    @Test void rayIdentifiedUpperFrontCoverActuallySamplesPalePaintAndNeverGlow() throws Exception {
        var mesh = DnvTurretMesh.BLIZZARD.parse(productionMesh("blizzard"));
        com.google.gson.JsonObject fixture;
        try(var input=getClass().getResourceAsStream("/dnv-blizzard-front-cover.json")){
            fixture=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        java.awt.image.BufferedImage base,glow;
        try(var input=getClass().getResourceAsStream("/assets/doggiesnewvehicle/textures/entity/blizzard_base.png")){
            base=javax.imageio.ImageIO.read(input);
        }
        try(var input=getClass().getResourceAsStream("/assets/doggiesnewvehicle/textures/entity/blizzard_emission.png")){
            glow=javax.imageio.ImageIO.read(input);
        }
        float[] stream=mesh.part("Body").main();
        for(var element:fixture.getAsJsonArray("surfaces")){
            var surface=element.getAsJsonObject();var points=surface.getAsJsonArray("positions");int matches=0;
            for(int offset=0;offset<stream.length;offset+=24){
                boolean belongs=true;
                for(int corner=0;corner<3;corner++){
                    int vertex=offset+corner*8;boolean found=false;
                    for(var value:points){var p=value.getAsJsonArray();
                        if(Math.abs(stream[vertex]-p.get(0).getAsDouble())<1e-5
                                &&Math.abs(stream[vertex+1]-p.get(1).getAsDouble())<1e-5
                                &&Math.abs(stream[vertex+2]-p.get(2).getAsDouble())<1e-5){found=true;break;}}
                    belongs&=found;
                }
                if(!belongs)continue;
                matches++;
                double u=(stream[offset+6]+stream[offset+14]+stream[offset+22])/3D;
                double v=(stream[offset+7]+stream[offset+15]+stream[offset+23])/3D;
                int color=base.getRGB((int)(u*base.getWidth()),(int)(v*base.getHeight()));
                for(int shift:new int[]{0,8,16})assertTrue(((color>>shift)&255)>220,"Front cover face "+surface.get("face"));
                int emission=glow.getRGB((int)(u*glow.getWidth()),(int)(v*glow.getHeight()));
                for(int shift:new int[]{0,8,16})assertTrue(((emission>>shift)&255)<5,"Painted cover must remain world-lit");
            }
            assertTrue(matches>0,"Missing ray-identified cover surface "+surface.get("face"));
        }
    }
    @Test void everyBlizzardCornerNormalMatchesItsActualRenderTriangle() throws Exception {
        var mesh = DnvTurretMesh.BLIZZARD.parse(productionMesh("blizzard"));
        for (var name : new String[] {"Body", "Turret", "Barrel", "HatchLeft", "HatchRight"}) {
            var part = mesh.part(name);
            for (float[] vertices : new float[][] {part.main(), part.emissive()}) {
                for (int i = 0; i < vertices.length; i += 24) {
                    double x = vertices[i + 8] - vertices[i], y = vertices[i + 9] - vertices[i + 1], z = vertices[i + 10] - vertices[i + 2];
                    double a = vertices[i + 16] - vertices[i], b = vertices[i + 17] - vertices[i + 1], c = vertices[i + 18] - vertices[i + 2];
                    double nx = y*c-z*b, ny = z*a-x*c, nz = x*b-y*a;
                    double length = Math.sqrt(nx*nx+ny*ny+nz*nz);
                    assertTrue(length > 0);
                    for (int offset : new int[] {0, 8, 16}) {
                        double dot = (nx*vertices[i+offset+3]+ny*vertices[i+offset+4]+nz*vertices[i+offset+5])/length;
                        assertTrue(dot > .999, name + " wrong/smoothed corner at triangle " + i/24);
                    }
                }
            }
        }
    }

    @Test
    void whiteCryoMissileFitsMeasuredCanistersAndUsesFinitePaletteUvs() throws Exception {
        var mesh = DnvTurretMesh.BLIZZARD_MISSILE.parse(productionMesh("blizzard_missile"));
        assertEquals(1, mesh.partCount()); assertPart(mesh, "Missile");
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, radius = 0;
        var bodyCrossSection = new java.util.HashSet<java.util.List<Float>>();
        float[] vertices = mesh.part("Missile").main();
        for (int i = 0; i < vertices.length; i += DnvTurretMesh.FLOATS_PER_VERTEX) {
            min = Math.min(min, vertices[i + 2]); max = Math.max(max, vertices[i + 2]);
            radius = Math.max(radius, Math.hypot(vertices[i], vertices[i + 1]));
            for (int j = 0; j < 8; j++) assertTrue(Float.isFinite(vertices[i + j]));
            assertTrue(vertices[i + 6] >= 0 && vertices[i + 6] <= 1);
            assertTrue(vertices[i + 7] >= 0 && vertices[i + 7] <= 1);
            if (Math.abs(vertices[i + 2] - .57) < .00001) {
                bodyCrossSection.add(java.util.List.of(vertices[i], vertices[i + 1]));
            }
        }
        assertEquals(6, bodyCrossSection.size(), "User-requested hexagonal body cross-section");
        assertEquals(1.822, max - min, .00001);
        assertTrue(max - min < 1.99715, "Measured shortest canister");
        assertTrue(radius < .18, "Folded fin and body clearance");
    }

    @Test
    void m1UsesTheAuditedRenderExportWithSeparateInternalLinkGroups() throws Exception {
        byte[] bytes = productionMesh("m1_yunlong");
        assertEquals("2573f87a318bdef2176eda7fd3b7ab71b6d4991fbbfc50b63ccaea5e9d9f0e9b",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        var mesh = DnvTurretMesh.M1.parse(bytes);
        for(var link:com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile.M1.links)assertPart(mesh,link.group());
        assertEquals(4,com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile.M1.links.size());
    }

    @Test
    void productionMeshesParseWithTheirIndependentProfiles() throws IOException {
        DnvTurretMesh.MeshData endfield = assertMesh(
                DnvTurretMesh.ENDFIELD,
                "endfield_sniper_turret",
                4,
                4_776);
        DnvTurretMesh.MeshData jingzhe = assertMesh(
                DnvTurretMesh.JINGZHE,
                "jingzhe_turret",
                41,
                3_341);
        DnvTurretMesh.MeshData storm = assertMesh(
                DnvTurretMesh.STORM,
                "storm_turret",
                3,
                2_490);
        DnvTurretMesh.MeshData m1 = assertMesh(
                DnvTurretMesh.M1,
                "m1_yunlong",
                8,
                2_475);

        assertPart(endfield, "barrel_recoil");
        assertPart(jingzhe, "key_light");
        assertPart(jingzhe, "scroll_4_light_3");
        assertPart(storm, "pitch_assembly");
        assertPart(m1, "recoil_barrel_01");
        assertPart(m1, "link_outer_1");
        assertEquals(4,
                m1.part("yaw_assembly").emissive().length
                        / DnvTurretMesh.FLOATS_PER_VERTEX / 3);
    }

    @Test
    void sixSeparateGameMeshesRetainTheirOwnCountsGroupsAndFourteenPhysicalMuzzleEnds() throws Exception {
        var counts=java.util.Map.of("m1_yunlong",2475,"dragon_bofors_twin",1930,"dragon_bofors_quad",3003,
                "dragon_flak38",1776,"dragon_flak88",1330,"dragon_flak40",3502);
        for(var p:com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile.ALL.values()) {
            var mesh=assertMesh(DnvTurretMesh.DRAGON.get(p.entity),p.entity,p.groups.size(),counts.get(p.entity));
            for(String group:p.groups)assertPart(mesh,group);
            for(int barrel=0;barrel<p.barrels;barrel++) {
                var stream=mesh.part("recoil_barrel_"+String.format("%02d",barrel+1)).main();
                double front=-Double.MAX_VALUE;for(int i=2;i<stream.length;i+=8)front=Math.max(front,stream[i]);
                var tips=new java.util.HashSet<net.minecraft.world.phys.Vec3>();
                for(int i=0;i<stream.length;i+=8)if(stream[i+2]>front-.002)
                    tips.add(new net.minecraft.world.phys.Vec3(stream[i],stream[i+1],stream[i+2]));
                var center=net.minecraft.world.phys.Vec3.ZERO;for(var tip:tips)center=center.add(tip);center=center.scale(1D/tips.size());
                var expected=p.pitch.add(p.muzzles.get(barrel));
                assertTrue(center.distanceTo(expected)<.003,p.entity+" barrel "+barrel+" "+center+" != "+expected);
            }
        }
    }

    @Test
    void parserFailuresRetainTheOwningTurretIdentity() throws IOException {
        byte[] endfield = productionMesh("endfield_sniper_turret");
        endfield[0] = 'X';
        IllegalArgumentException badMagic = assertThrows(
                IllegalArgumentException.class,
                () -> DnvTurretMesh.ENDFIELD.parse(endfield));
        assertTrue(badMagic.getMessage().contains("Endfield turret"));

        byte[] jingzhe = productionMesh("jingzhe_turret");
        byte[] truncated = Arrays.copyOf(jingzhe, jingzhe.length - 1);
        IllegalArgumentException badStream = assertThrows(
                IllegalArgumentException.class,
                () -> DnvTurretMesh.JINGZHE.parse(truncated));
        assertTrue(badStream.getMessage().contains("Jingzhe turret"));
    }

    private static DnvTurretMesh.MeshData assertMesh(
            DnvTurretMesh owner,
            String resourceName,
            int expectedParts,
            int expectedTriangles
    ) throws IOException {
        DnvTurretMesh.MeshData mesh = owner.parse(productionMesh(resourceName));
        assertEquals(expectedParts, mesh.partCount());
        assertEquals(expectedTriangles, mesh.triangleCount());
        assertPart(mesh, "base_static");
        return mesh;
    }

    private static void assertPart(
            DnvTurretMesh.MeshData mesh,
            String name
    ) {
        DnvTurretMesh.MeshPart part = mesh.part(name);
        assertFalse(part.main().length == 0 && part.emissive().length == 0);
    }

    private static byte[] productionMesh(String resourceName)
            throws IOException {
        try (InputStream input = DnvTurretMeshTest.class.getResourceAsStream(
                "/assets/doggiesnewvehicle/meshes/"
                        + resourceName + ".etm")) {
            if (input == null) {
                throw new IOException(
                        "Production ETM resource is missing: " + resourceName);
            }
            return input.readAllBytes();
        }
    }
}
