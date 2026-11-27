package com.liucodex.doggiesnewvehicle.client.mesh;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.client.DnvRenderDetail;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

/** Real parsers/loaders consume full meshes, optional LODs and proof; missing/stale packs fail closed. */
class DnvFleetLodTest {
    @Test void recessedBlizzardEnergyWallsHaveNoMissingEmissiveFaces() throws Exception {
        var full=DnvTurretMesh.BLIZZARD.load(manager(resources("doggiesnewvehicle","blizzard",".etm")));
        var armor=full.part("Body").main();
        var glow=full.part("Body").emissive();
        java.awt.image.BufferedImage emission;
        try(var input=getClass().getResourceAsStream("/assets/doggiesnewvehicle/textures/entity/blizzard_emission.png")){
            emission=javax.imageio.ImageIO.read(input);
        }
        int energy=0;
        for(int i=0;i<armor.length;i+=24){
            double u=(armor[i+6]+armor[i+14]+armor[i+22])/3D;
            double v=(armor[i+7]+armor[i+15]+armor[i+23])/3D;
            int pixel=emission.getRGB(Math.min(emission.getWidth()-1,(int)(u*emission.getWidth())),
                    Math.min(emission.getHeight()-1,(int)(v*emission.getHeight())));
            // UV islands may move or reuse flat swatches; the authored emission channel owns lamp identity.
            if(((pixel>>16)&255)<5&&((pixel>>8)&255)<5&&(pixel&255)<5)continue;
            energy++;
            boolean found=false;
            for(int k=0;k<glow.length;k+=24){
                boolean same=true;
                for(int attribute=0;attribute<24;attribute++)same&=armor[i+attribute]==glow[k+attribute];
                if(same){found=true;break;}
            }
            assertTrue(found,"Energy channel wall has base color but no emissive triangle: "+i/24);
        }
        assertTrue(energy>50,"Exercise the recessed rings, center and connecting walls");
        assertArrayEquals(glow,full.detail(1).part("Body").emissive());
        assertArrayEquals(glow,full.detail(2).part("Body").emissive());
    }
    @Test void blizzardTurretFacesStayInsideOneAtlasChartAtEveryDistance() throws Exception {
        var full=DnvTurretMesh.BLIZZARD.load(manager(resources("doggiesnewvehicle","blizzard",".etm")));
        for(int level=0;level<=2;level++){
            var stream=full.detail(level).part("Turret").main();
            for(int i=0;i<stream.length;i+=24){
                float minU=1,maxU=0,minV=1,maxV=0;
                for(int j=0;j<3;j++){
                    float u=stream[i+j*8+6],v=stream[i+j*8+7];
                    minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,v);maxV=Math.max(maxV,v);
                }
                if(maxU<.875)assertTrue(Math.max(maxU-minU,maxV-minV)<=.23,
                        "Turret UV crosses unrelated paint islands at LOD "+level+", triangle "+i/24);
            }
        }
    }
    @Test void shippedTurretLodsRetainGroupsAndReduceActualSubmittedStreams() throws Exception {
        DnvTurretMesh[] owners = {DnvTurretMesh.ENDFIELD, DnvTurretMesh.JINGZHE, DnvTurretMesh.STORM,
                DnvTurretMesh.M1, DnvTurretMesh.APOCALYPSE, DnvTurretMesh.BLIZZARD};
        String[] names = {"endfield_sniper_turret", "jingzhe_turret", "storm_turret", "m1_yunlong", "apocalypse", "blizzard"};
        for (int i = 0; i < owners.length; i++) {
            var resources = resources("doggiesnewvehicle", names[i], ".etm");
            var full = owners[i].load(manager(resources));
            assertSame(full, full.detail(0));
            assertTrue(full.detail(1).triangleCount() < full.triangleCount() * .90, names[i]);
            assertTrue(full.detail(2).triangleCount() < full.detail(1).triangleCount());
            assertEquals(full.partCount(), full.detail(2).partCount());
            if (owners[i] == DnvTurretMesh.M1) assertArrayEquals(full.part("pitch_assembly").main(), full.detail(2).part("pitch_assembly").main());
            owners[i].install(full);
            assertSame(full, owners[i].get(31 * 31));
            assertSame(full.detail(1), owners[i].get(32 * 32));
            assertSame(full.detail(2), owners[i].get(64 * 64));
            resources.remove("meshes/" + names[i] + "_lod1.etm.sha256");
            var reloaded = owners[i].load(manager(resources)); owners[i].install(reloaded);
            assertSame(reloaded, owners[i].get(40 * 40), "resource reload cannot retain an obsolete distance mesh");
            owners[i].install(null);
        }
    }
    @Test void staleOrMissingDerivedResourcesFallBackToCurrentFullMesh() throws Exception {
        var owner = DnvTurretMesh.BLIZZARD;
        var resources = resources("doggiesnewvehicle", "blizzard", ".etm");
        resources.put("meshes/blizzard_lod2.etm.sha256", "invalid".getBytes());
        var loaded = owner.load(manager(resources)); assertSame(loaded, loaded.detail(2));
        assertNotSame(loaded, loaded.detail(1));
        assertEquals(0, DnvRenderDetail.level(Double.NaN));
        assertEquals(0, DnvRenderDetail.level(-1));
        byte[] full = resources.get("meshes/blizzard.etm"), lod = resources.get("meshes/blizzard_lod1.etm");
        assertTrue(DnvRenderDetail.matches(full, lod, resources.get("meshes/blizzard_lod1.etm.sha256")));
        byte[] changed = full.clone(); changed[changed.length - 1] ^= 1;
        assertFalse(DnvRenderDetail.matches(changed, lod, resources.get("meshes/blizzard_lod1.etm.sha256")));
    }
    @Test void bossLodsKeepCompleteRigAndReloadWithTheOwningCache() throws Exception {
        for (String namespace : new String[] {"noise", "silent_heaven"}) {
            var owner = namespace.equals("noise") ? DnvVehicleMesh.NOISE : DnvVehicleMesh.SILENT_HEAVEN;
            var resources = resources(namespace, namespace, ".shm");
            var load = DnvVehicleMesh.class.getDeclaredMethod("load", ResourceManager.class); load.setAccessible(true);
            var mesh = (DnvVehicleMesh.MeshData) load.invoke(owner, manager(resources));
            assertEquals(mesh.partCount(), mesh.detail(2).partCount());
            assertTrue(mesh.detail(2).triangleCount() < mesh.triangleCount() * .75);
            assertArrayEquals(mesh.part("track_shoe_l_0/track").stream(), mesh.detail(2).part("track_shoe_l_0/track").stream());
            if (namespace.equals("silent_heaven")) {
                var halo = mesh.part("halo_ring/light").stream();
                assertEquals(48 * 24, halo.length);
                assertArrayEquals(halo, mesh.detail(1).part("halo_ring/light").stream());
                assertArrayEquals(halo, mesh.detail(2).part("halo_ring/light").stream());
            }
        }
    }
    static Map<String, byte[]> resources(String namespace, String name, String extension) throws Exception {
        var result = new HashMap<String, byte[]>();
        for (String suffix : new String[] {extension, "_lod1" + extension, "_lod2" + extension,
                "_lod1" + extension + ".sha256", "_lod2" + extension + ".sha256"}) {
            String path = "meshes/" + name + suffix;
            try (var input = DnvFleetLodTest.class.getResourceAsStream("/assets/" + namespace + "/" + path)) {
                assertNotNull(input, path); result.put(path, input.readAllBytes());
            }
        }
        return result;
    }
    static ResourceManager manager(Map<String, byte[]> resources) {
        return (ResourceManager) Proxy.newProxyInstance(DnvFleetLodTest.class.getClassLoader(), new Class<?>[] {ResourceManager.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("open")) {
                        String path = ((ResourceLocation) arguments[0]).getPath(); byte[] bytes = resources.get(path);
                        if (bytes == null) throw new FileNotFoundException(path);
                        return new ByteArrayInputStream(bytes);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
