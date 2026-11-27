package com.liucodex.doggiesnewvehicle.client.mesh;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Samples the native source surfaces and guards the unchanged original distance geometry. */
class BlizzardSurfacePaintTest {
    @Test void pairedShellCoatsAndStripesReachTheActualOriginalSurface() throws Exception {
        var fixture = json("/dnv-blizzard-color-blocks.json");
        var base = image("base");
        var glow = image("emission");
        assertEquals(66, fixture.getAsJsonArray("surfaces").size());
        for (String file : new String[] {"blizzard"}) {
            var mesh = mesh(file);
            for (var element : fixture.getAsJsonArray("surfaces")) {
                var row = element.getAsJsonObject();
                for (var triangle : row.getAsJsonArray("triangles")) {
                var vertices = triangle.getAsJsonArray();
                for (double[] weights : new double[][] {{.2, .3, .5}, {.4, .4, .2}}) {
                    double[] point = new double[3];
                    for (int i = 0; i < 3; i++) for (int axis = 0; axis < 3; axis++)
                        point[axis] += weights[i] * vertices.get(i).getAsJsonArray().get(axis).getAsDouble();
                    var expected = row.getAsJsonArray("expectedRgb");
                    if (row.has("stripeMinecraftZ")) {
                        double distance = Math.abs(point[2] - row.get("stripeMinecraftZ").getAsDouble());
                        double width = row.get("stripeHalfWidth").getAsDouble();
                        if (Math.abs(distance - width) < row.get("stripeBoundaryMargin").getAsDouble()) continue;
                        if (distance < width) expected = row.getAsJsonArray("stripeExpectedRgb");
                    }
                    String label = file + " " + row.get("object") + " face " + row.get("face");
                    double[] uv = surfaceUv(mesh.part(row.get("group").getAsString()).main(), point, file, label);
                    int color = pixel(base, uv), emission = pixel(glow, uv);
                    for (int channel = 0; channel < 3; channel++) {
                        assertEquals(expected.get(channel).getAsInt(), (color >> (16 - 8 * channel)) & 255, 5, label);
                        assertTrue(((emission >> (16 - 8 * channel)) & 255) < 5, label + " must not glow");
                    }
                }
                }
            }
        }
    }

    @Test void wholeOriginalCapAndBasePlateStayInTheExistingGreyWhiteCoat() throws Exception {
        var fixture = json("/dnv-blizzard-front-cover.json");
        var base = image("base");
        var glow = image("emission");
        assertEquals(24, fixture.getAsJsonArray("surfaces").size());
        for (String file : new String[] {"blizzard"}) {
            var stream = mesh(file).part("Body").main();
            for (var element : fixture.getAsJsonArray("surfaces")) {
                var row = element.getAsJsonObject();
                for (var triangle : row.getAsJsonArray("triangles")) {
                    double[] point = new double[3];
                    for (var vertex : triangle.getAsJsonArray()) for (int axis = 0; axis < 3; axis++)
                        point[axis] += vertex.getAsJsonArray().get(axis).getAsDouble() / 3;
                    double[] uv = surfaceUv(stream, point, file, "cover face " + row.get("face"));
                    int color = pixel(base, uv);
                    var expected = fixture.getAsJsonArray("expectedRgb");
                    for (int channel = 0; channel < 3; channel++)
                        assertEquals(expected.get(channel).getAsInt(), (color >> (16 - 8 * channel)) & 255, 2, file + " cover face " + row.get("face"));
                    assertEquals(0, pixel(glow, uv) & 0xffffff, "Grey-white coat stays world-lit");
                }
            }
        }
    }

    @Test void distanceMeshesKeepTheOriginalGeometryAndOnlyDropTwoNonLampDraws() throws Exception {
        var fixture = json("/dnv-blizzard-lod-baseline.json");
        var normals = json("/dnv-blizzard-rear-tank-normals.json");
        for (var element : fixture.getAsJsonArray("variants")) {
            var row = element.getAsJsonObject();
            String file = "blizzard_lod" + row.get("level").getAsInt();
            var mesh = mesh(file);
            assertEquals(row.get("triangles").getAsInt() - 2, mesh.triangleCount());
            for (var value : row.getAsJsonArray("groups")) {
                var group = value.getAsJsonObject();
                float[] stream = mesh.part(group.get("name").getAsString()).main().clone();
                assertEquals(group.get("opaqueTriangles").getAsInt(), stream.length / 24);
                if(group.get("name").getAsString().equals("Turret")) {
                    // Compare against the unchanged historical geometry after undoing only the
                    // independently measured rear-tank orientation correction. Positions stay exact.
                    var indices=normals.getAsJsonObject("lodTriangles").getAsJsonArray(row.get("level").getAsString());
                    for(var index:indices) {
                        int offset=index.getAsInt()*24;
                        for(int field=0;field<8;field++) {
                            float cornerField=stream[offset+8+field];stream[offset+8+field]=stream[offset+16+field];stream[offset+16+field]=cornerField;
                        }
                        for(int corner=0;corner<3;corner++)for(int axis=3;axis<6;axis++)stream[offset+corner*8+axis]*=-1;
                    }
                }
                var buffer = java.nio.ByteBuffer.allocate(stream.length / 8 * 6 * Float.BYTES)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                for (int offset = 0; offset < stream.length; offset += 8)
                    for (int field = 0; field < 6; field++) buffer.putFloat(stream[offset + field]);
                assertEquals(group.get("opaqueGeometrySha256").getAsString(),
                        java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(buffer.array())));
            }
        }
    }

    private static double[] surfaceUv(float[] stream, double[] point, String file, String label) {
        double nearest = Double.POSITIVE_INFINITY;
        double[] result = null;
        for (int offset = 0; offset < stream.length; offset += 24) {
            double aa = 0, ab = 0, bb = 0, da = 0, db = 0;
            double[] a = new double[3], b = new double[3], delta = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                a[axis] = stream[offset + 8 + axis] - stream[offset + axis];
                b[axis] = stream[offset + 16 + axis] - stream[offset + axis];
                delta[axis] = point[axis] - stream[offset + axis];
                aa += a[axis] * a[axis]; ab += a[axis] * b[axis]; bb += b[axis] * b[axis];
                da += delta[axis] * a[axis]; db += delta[axis] * b[axis];
            }
            double denominator = aa * bb - ab * ab;
            if (denominator < 1e-20) continue;
            double wb = (bb * da - ab * db) / denominator, wc = (aa * db - ab * da) / denominator;
            if (wb < -.002 || wc < -.002 || wb + wc > 1.002) continue;
            double distance = 0;
            for (int axis = 0; axis < 3; axis++) distance += Math.pow(delta[axis] - wb * a[axis] - wc * b[axis], 2);
            if (distance >= nearest) continue;
            nearest = distance;
            result = new double[] {(1 - wb - wc) * stream[offset + 6] + wb * stream[offset + 14] + wc * stream[offset + 22],
                    (1 - wb - wc) * stream[offset + 7] + wb * stream[offset + 15] + wc * stream[offset + 23]};
        }
        assertNotNull(result, file + " source-measured surface is missing: " + label);
        // Full native source geometry is exact; distance geometry has its own baseline contract.
        double tolerance = .00002;
        assertTrue(nearest < tolerance * tolerance, file + " surface deviation " + Math.sqrt(nearest) + ": " + label);
        return result;
    }

    private static int pixel(BufferedImage image, double[] uv) {
        int x = Math.min(image.getWidth() - 1, Math.max(0, (int) (uv[0] * image.getWidth())));
        int y = Math.min(image.getHeight() - 1, Math.max(0, (int) (uv[1] * image.getHeight())));
        return image.getRGB(x, y);
    }
    private static JsonObject json(String path) throws Exception {
        try (var input = BlizzardSurfacePaintTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static BufferedImage image(String name) throws Exception {
        try (var input = BlizzardSurfacePaintTest.class.getResourceAsStream("/assets/doggiesnewvehicle/textures/entity/blizzard_" + name + ".png")) {
            return ImageIO.read(input);
        }
    }
    private static DnvTurretMesh.MeshData mesh(String name) throws Exception {
        try (var input = BlizzardSurfacePaintTest.class.getResourceAsStream("/assets/doggiesnewvehicle/meshes/" + name + ".etm")) {
            return DnvTurretMesh.BLIZZARD.parse(input.readAllBytes());
        }
    }
}
