package com.liucodex.doggiesnewvehicle.client.weapons;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Dsx1MeshTest {
    private static final Path MESH = Path.of("src/main/resources/assets/doggiesnewvehicle/models/item/dsx1.dsm");
    private static Dsx1Mesh read(byte[] bytes) throws IOException { return Dsx1Mesh.read(new ByteArrayInputStream(bytes)); }

    @Test void realAuthorMeshHasIndependentMovingPartsAndFiniteUnitNormals() throws Exception {
        var mesh = read(Files.readAllBytes(MESH));
        assertEquals(4149, mesh.triangleCount());
        assertEquals(66, mesh.part("dsx_bolt").triangles());
        assertEquals(56, mesh.part("dsx_handle").triangles());
        assertEquals(118, mesh.part("dsx_magazine").triangles());
        // Rear glass keeps its four corners; two collinear side vertices were removed.
        assertEquals(6, mesh.part("dsx_glass").triangles());
        // V18 R2: six lamp fronts (12), two long lamps (12), original inner back face (2).
        assertEquals(26, mesh.part("dsx_lamps").triangles());
        for (String name : new String[] {"dsx_body","dsx_bolt","dsx_handle","dsx_magazine","dsx_scope","dsx_glass","dsx_lamps"}) {
            var part = mesh.part(name);
            for (int c = 0; c < part.triangles() * 3; c++) {
                double length = 0;
                for (int i = 5; i < 8; i++) length += part.value(c, i) * part.value(c, i);
                assertEquals(1, length, .00001, name + " normal");
            }
        }
        assertNull(mesh.part("missing"));
        assertEquals(0, Dsx1Mesh.EMPTY.triangleCount());
    }

    @Test void truncatedPayloadsCannotProducePartialVisibleGuns() throws Exception {
        byte[] bytes = Files.readAllBytes(MESH);
        for (int length : new int[] {0, 3, 11, 12, 14, 21, bytes.length - 1}) {
            assertThrows(IOException.class, () -> read(Arrays.copyOf(bytes, length)));
        }
    }

    @Test void rejectsUnboundedCountsTrailingBytesAndNonFiniteCorners() throws Exception {
        byte[] bytes = Files.readAllBytes(MESH);
        for (int offset : new int[] {4, 8, 22}) {
            byte[] bad = bytes.clone(); ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, Integer.MAX_VALUE);
            assertThrows(IOException.class, () -> read(bad));
        }
        assertThrows(IOException.class, () -> read(Arrays.copyOf(bytes, bytes.length + 1)));
        assertThrows(IOException.class, () -> read(new byte[4 * 1024 * 1024 + 1]));
        var header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int firstCorner = 12 + 2 + Short.toUnsignedInt(header.getShort(12)) + 4;
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, 1000}) {
            byte[] bad = bytes.clone(); ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putFloat(firstCorner, value);
            assertThrows(IOException.class, () -> read(bad));
        }
    }
}
