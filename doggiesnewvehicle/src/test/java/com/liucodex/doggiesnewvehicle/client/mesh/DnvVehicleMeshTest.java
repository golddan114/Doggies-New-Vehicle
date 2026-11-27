package com.liucodex.doggiesnewvehicle.client.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.client.mesh.DnvVehicleMesh.MeshData;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DnvVehicleMeshTest {
    @Test
    void weaponsWheelsAndOtherProtectedStreamsRemainByteIdenticalToThePreUpgradeModels() throws Exception {
        for (String namespace : new String[] {"noise", "silent_heaven"}) {
            String fixture = namespace.equals("noise") ? "noise" : "silent-heaven";
            var audit = JsonParser.parseString(Files.readString(Path.of(
                    "src/test/resources/audited-exports", fixture,
                    namespace + "_export_report.json")))
                    .getAsJsonObject().getAsJsonObject("track_upgrade")
                    .getAsJsonObject("protected_stream_sha256");
            MeshData mesh = (namespace.equals("noise") ? DnvVehicleMesh.NOISE : DnvVehicleMesh.SILENT_HEAVEN)
                    .parse(productionMesh(namespace, namespace));
            assertTrue(audit.size() >= 25);
            for (var entry : audit.entrySet()) {
                float[] stream = mesh.part(entry.getKey()).stream();
                ByteBuffer raw = ByteBuffer.allocate(stream.length * 4).order(ByteOrder.LITTLE_ENDIAN);
                for (float value : stream) raw.putFloat(value);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.array()));
                assertEquals(entry.getValue().getAsString(), hash, namespace + "/" + entry.getKey());
            }
        }
    }

    @Test
    void newTrackStreamsHaveValidNormalsUvsAndMirroredWheelCenters() throws IOException {
        for (String namespace : new String[] {"noise", "silent_heaven"}) {
            MeshData mesh = (namespace.equals("noise") ? DnvVehicleMesh.NOISE : DnvVehicleMesh.SILENT_HEAVEN)
                    .parse(productionMesh(namespace, namespace));
            for (int index = 0; index < 58; index++) {
                float[] left = mesh.part("track_shoe_l_" + index + "/track").stream();
                float[] right = mesh.part("track_shoe_r_" + index + "/track").stream();
                assertEquals(36 * 8, left.length);
                for (int v = 0; v < left.length; v += 8) {
                    assertEquals(3.585, left[v] - right[v], 1e-6);
                    assertEquals(left[v + 1], right[v + 1]);
                    assertEquals(left[v + 2], right[v + 2]);
                    assertEquals(1, Math.sqrt(left[v+3]*left[v+3] + left[v+4]*left[v+4]
                            + left[v+5]*left[v+5]), 1e-6);
                    for (int k = 0; k < 8; k++) assertTrue(Float.isFinite(left[v+k]));
                    assertTrue(left[v+6] >= 0 && left[v+6] <= 1);
                    assertTrue(left[v+7] >= 0 && left[v+7] <= 1);
                }
            }
        }
    }

    @Test
    void productionMeshesParseWithTheirCompleteRuntimePartSets() throws IOException {
        MeshData noise = assertVehicleMesh(
                DnvVehicleMesh.NOISE, "noise", "noise", 5_402);
        MeshData silentHeaven = assertVehicleMesh(
                DnvVehicleMesh.SILENT_HEAVEN,
                "silent_heaven",
                "silent_heaven",
                5_370);

        assertFalse(noise.part("halo_ring/light").stream().length == 0);
        assertFalse(silentHeaven.part("halo_ring/light").stream().length == 0);
        assertTrue(minimumY(silentHeaven.part("chassis/body").stream()) >= -0.04F);
        assertEquals(0, silentHeaven.part("chassis/track_inner").stream().length);
    }

    @Test
    void additionalRequiredPartsRemainScopedToTheirVehicle() throws IOException {
        byte[] noise = withRenamedHalo(productionMesh("noise", "noise"));
        assertFalse(DnvVehicleMesh.NOISE.parse(noise).isEmpty());

        byte[] silentHeaven = withRenamedHalo(
                productionMesh("silent_heaven", "silent_heaven"));
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> DnvVehicleMesh.SILENT_HEAVEN.parse(silentHeaven));
        assertTrue(failure.getMessage().contains("Silent Heaven"));
        assertTrue(failure.getMessage().contains("halo_ring/light"));
    }

    @Test
    void parserRejectsBadMagicAndTruncatedStreamsWithVehicleIdentity() throws IOException {
        byte[] noise = productionMesh("noise", "noise");
        byte[] badMagic = noise.clone();
        badMagic[0] = 'X';
        IllegalArgumentException badMagicFailure = assertThrows(
                IllegalArgumentException.class,
                () -> DnvVehicleMesh.NOISE.parse(badMagic));
        assertTrue(badMagicFailure.getMessage().contains("Noise"));

        byte[] silentHeaven = productionMesh("silent_heaven", "silent_heaven");
        byte[] truncated = Arrays.copyOf(silentHeaven, silentHeaven.length - 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> DnvVehicleMesh.SILENT_HEAVEN.parse(truncated));
    }

    private static MeshData assertVehicleMesh(
            DnvVehicleMesh owner,
            String namespace,
            String fileName,
            int expectedTriangles) throws IOException {
        MeshData mesh = owner.parse(productionMesh(namespace, fileName));
        assertFalse(mesh.isEmpty());
        assertEquals(147, mesh.partCount());
        assertEquals(expectedTriangles, mesh.triangleCount());
        for (String side : new String[] {"l", "r"}) {
            for (int index = 0; index < 4; index++) {
                assertEquals(
                        28 * 3 * DnvVehicleMesh.FLOATS_PER_VERTEX,
                        mesh.part("road_wheel_" + side + "_" + index + "/body")
                                .stream().length);
            }
            for (int index = 0; index < 58; index++) {
                assertFalse(mesh.part("track_shoe_" + side + "_" + index + "/track")
                        .stream().length == 0);
            }
        }
        return mesh;
    }

    private static byte[] productionMesh(String namespace, String fileName) throws IOException {
        try (InputStream input = DnvVehicleMeshTest.class.getResourceAsStream(
                "/assets/" + namespace + "/meshes/" + fileName + ".shm")) {
            if (input == null) {
                throw new IOException("Production SHM resource is missing: " + namespace);
            }
            return input.readAllBytes();
        }
    }

    private static float minimumY(float[] stream) {
        float minimum = Float.POSITIVE_INFINITY;
        for (int offset = 1; offset < stream.length; offset += DnvVehicleMesh.FLOATS_PER_VERTEX) {
            minimum = Math.min(minimum, stream[offset]);
        }
        return minimum;
    }

    private static byte[] withRenamedHalo(byte[] mesh) {
        byte[] result = mesh.clone();
        byte[] partName = "halo_ring/light".getBytes(StandardCharsets.US_ASCII);
        for (int offset = 0; offset <= result.length - partName.length; offset++) {
            boolean match = true;
            for (int index = 0; index < partName.length; index++) {
                if (result[offset + index] != partName[index]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                result[offset + partName.length - 1] = 'x';
                return result;
            }
        }
        throw new IllegalArgumentException("Production mesh has no halo part");
    }
}
