package com.liucodex.doggiesnewvehicle.client.mesh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.boss.mesh.DnvMeshCodec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class DnvQuadStreamTest {
    private static final HexFormat HEX = HexFormat.of();
    @Test
    void joinsOnlyTheOriginalSharedDiagonalWithIdenticalAttributesAndWinding() {
        float[] a = vertex(0, 0, 0, 0);
        float[] b = vertex(1, 0, 1, 0);
        float[] c = vertex(1, 1, 1, 1);
        float[] d = vertex(0, 1, 0, 1);
        float[] source = join(a, b, c, a, c, d);
        assertArrayEquals(join(a, b, c, d), DnvQuadStream.convert(source));
        assertSame(DnvQuadStream.quads(source), DnvQuadStream.quads(source));

        float[] uvSeam = vertex(0, 0, 0.25F, 0);
        assertEquals(64, DnvQuadStream.convert(join(a, b, c, uvSeam, c, d)).length);
        assertEquals(64, DnvQuadStream.convert(join(a, b, c, a, d, c)).length);
        assertThrows(IllegalArgumentException.class, () -> DnvQuadStream.convert(new float[25]));
    }

    @Test
    void productionAssetsPreserveAllTriangleAttributesWhileReducingSubmittedVertices() throws IOException {
        int oldVertices = 0;
        int newVertices = 0;
        for (String name : List.of("apocalypse", "jingzhe_turret",
                "endfield_sniper_turret", "storm_turret")) {
            byte[] bytes = resource("doggiesnewvehicle", name + ".etm");
            DnvTurretMesh.MeshData mesh = switch (name) {
                case "apocalypse" -> DnvTurretMesh.APOCALYPSE.parse(bytes);
                case "jingzhe_turret" -> DnvTurretMesh.JINGZHE.parse(bytes);
                case "endfield_sniper_turret" -> DnvTurretMesh.ENDFIELD.parse(bytes);
                default -> DnvTurretMesh.STORM.parse(bytes);
            };
            for (String partName : etmPartNames(bytes)) {
                DnvTurretMesh.MeshPart part = mesh.part(partName);
                for (float[] stream : List.of(part.main(), part.emissive())) {
                    assertEquivalent(stream);
                    oldVertices += stream.length / 24 * 4;
                    newVertices += DnvQuadStream.quads(stream).length / 8;
                }
            }
        }
        for (String path : List.of("noise/noise.shm", "noise/noise_drop_rig.shm",
                "silent_heaven/silent_heaven.shm", "doggiesnewvehicle/mothership.shm",
                "doggiesnewvehicle/mothership_lod.shm")) {
            String namespace = path.substring(0, path.indexOf('/'));
            String meshPath = path.substring(path.indexOf('/') + 1);
            for (DnvMeshCodec.Part part : DnvMeshCodec.parse(resource(namespace, meshPath)).values()) {
                assertEquivalent(part.stream());
                oldVertices += part.stream().length / 24 * 4;
                newVertices += DnvQuadStream.quads(part.stream()).length / 8;
            }
        }
        assertTrue(oldVertices - newVertices > 1_000,
                "Production meshes should eliminate a meaningful amount of vertex submission");
    }

    private static void assertEquivalent(float[] triangles) {
        float[] quads = DnvQuadStream.convert(triangles);
        List<String> before = new ArrayList<>();
        List<String> after = new ArrayList<>();
        for (int start = 0; start < triangles.length; start += 24) {
            before.add(signature(triangles, start, start + 8, start + 16));
        }
        for (int start = 0; start < quads.length; start += 32) {
            after.add(signature(quads, start, start + 8, start + 16));
            if (!same(quads, start + 16, start + 24)) {
                after.add(signature(quads, start + 16, start + 24, start));
            }
        }
        Collections.sort(before);
        Collections.sort(after);
        assertEquals(before, after);
    }

    private static String signature(float[] stream, int a, int b, int c) {
        String[] rotations = {key(stream, a) + key(stream, b) + key(stream, c),
                key(stream, b) + key(stream, c) + key(stream, a),
                key(stream, c) + key(stream, a) + key(stream, b)};
        Arrays.sort(rotations);
        return rotations[0];
    }

    private static String key(float[] stream, int start) {
        StringBuilder value = new StringBuilder(64);
        for (int index = 0; index < 8; index++) {
            value.append(HEX.toHexDigits(Float.floatToRawIntBits(stream[start + index])));
        }
        return value.toString();
    }

    private static boolean same(float[] stream, int a, int b) {
        for (int index = 0; index < 8; index++) {
            if (Float.floatToRawIntBits(stream[a + index])
                    != Float.floatToRawIntBits(stream[b + index])) return false;
        }
        return true;
    }

    private static float[] vertex(float x, float y, float u, float v) {
        return new float[] {x, y, 0, 0, 0, 1, u, v};
    }

    private static float[] join(float[]... vertices) {
        float[] result = new float[vertices.length * 8];
        for (int index = 0; index < vertices.length; index++) {
            System.arraycopy(vertices[index], 0, result, index * 8, 8);
        }
        return result;
    }

    private static byte[] resource(String namespace, String name) throws IOException {
        try (InputStream input = DnvQuadStreamTest.class.getResourceAsStream(
                "/assets/" + namespace + "/meshes/" + name)) {
            if (input == null) throw new IOException("Missing production mesh " + namespace + "/" + name);
            return input.readAllBytes();
        }
    }

    private static List<String> etmPartNames(byte[] bytes) {
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        input.position(8);
        int count = input.getInt();
        List<String> names = new ArrayList<>(count);
        for (int part = 0; part < count; part++) {
            int nameLength = Short.toUnsignedInt(input.getShort());
            byte[] encoded = new byte[nameLength];
            input.get(encoded);
            names.add(new String(encoded, StandardCharsets.UTF_8));
            int main = input.getInt();
            int emissive = input.getInt();
            input.position(input.position() + (main + emissive) * 8 * Float.BYTES);
        }
        return names;
    }
}
