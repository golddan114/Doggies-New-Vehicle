package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.boss.mesh.DnvMeshCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MothershipArrivalAndLodTest {
    @Test void beaconRevealAndActiveAgeRestoreAtExactBoundaries() {
        assertEquals(MothershipArrival.Phase.BEACON,MothershipArrival.phase(0));
        assertEquals(MothershipArrival.Phase.BEACON,MothershipArrival.phase(119));
        assertEquals(MothershipArrival.Phase.REVEAL,MothershipArrival.phase(120));
        assertEquals(MothershipArrival.Phase.REVEAL,MothershipArrival.phase(159));
        assertEquals(MothershipArrival.Phase.ACTIVE,MothershipArrival.phase(160));
        assertEquals(0,MothershipArrival.reveal(120));
        assertEquals(.5,MothershipArrival.reveal(140),1e-9);
        assertEquals(1,MothershipArrival.reveal(160));
        assertEquals(0,MothershipArrival.restore(-10));
        assertEquals(145,MothershipArrival.restore(145));
        assertEquals(160,MothershipArrival.restore(100000));
        assertEquals(160,MothershipArrival.advance(160));
    }

    @Test void remoteMeshPreservesEveryMovingAndEmissivePart() throws Exception {
        Path directory=Path.of("src/boss/resources/assets/doggiesnewvehicle/meshes");
        var full=DnvMeshCodec.parse(Files.readAllBytes(directory.resolve("mothership.shm")));
        byte[] lodBytes=Files.readAllBytes(directory.resolve("mothership_lod.shm"));
        var lod=DnvMeshCodec.parse(lodBytes);
        try(var packaged=getClass().getResourceAsStream("/assets/doggiesnewvehicle/meshes/mothership_lod.shm")) {
            assertNotNull(packaged,"The selected platform must project the LOD as a runtime resource");
            assertArrayEquals(lodBytes,packaged.readAllBytes());
        }
        assertEquals(14,full.size());
        assertEquals(full.keySet(),lod.keySet());
        int fullTriangles=0,lodTriangles=0;
        for(String name:full.keySet()) {
            var source=full.get(name);var distant=lod.get(name);
            assertEquals(source.material(),distant.material(),name);
            fullTriangles+=source.stream().length/24;
            lodTriangles+=distant.stream().length/24;
            if(!name.equals("body_static/body"))assertArrayEquals(source.stream(),distant.stream(),name);
        }
        assertEquals(6595,fullTriangles);
        assertEquals(3550,lodTriangles);
    }
}
