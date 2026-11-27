package com.liucodex.doggiesnewvehicle.client.weapons;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded immutable corner mesh; no game state or graphics dependency. */
public final class Dsx1Mesh {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final Dsx1Mesh EMPTY = new Dsx1Mesh(Map.of());
    private final Map<String, Part> parts;

    private Dsx1Mesh(Map<String, Part> parts) { this.parts = Map.copyOf(parts); }
    public Part part(String name) { return parts.get(name); }
    public int triangleCount() { return parts.values().stream().mapToInt(Part::triangles).sum(); }

    public static Dsx1Mesh read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("DSX1 mesh exceeds limit");
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        try {
            if (data.getInt() != 0x314d5344 || data.getInt() != 1) throw new IOException("Unsupported DSX1 mesh header");
            int count = data.getInt();
            if (count < 1 || count > 32) throw new IOException("Invalid DSX1 part count");
            Map<String, Part> parts = new LinkedHashMap<>();
            int total = 0;
            for (int i = 0; i < count; i++) {
                int length = Short.toUnsignedInt(data.getShort());
                if (length < 1 || length > 64 || length > data.remaining()) throw new IOException("Invalid DSX1 part name");
                byte[] nameBytes = new byte[length]; data.get(nameBytes);
                String name = new String(nameBytes, StandardCharsets.US_ASCII);
                if (!name.matches("dsx_[a-z_]+") || parts.containsKey(name)) throw new IOException("Invalid/duplicate DSX1 part");
                int triangles = data.getInt();
                if (triangles < 1 || triangles > 40000 || triangles > data.remaining() / 96 || (total += triangles) > 40000) {
                    throw new IOException("Invalid DSX1 triangle count");
                }
                float[] corners = new float[triangles * 24];
                for (int c = 0; c < corners.length; c++) {
                    float v = data.getFloat();
                    int attribute = c % 8;
                    if (!Float.isFinite(v) || (attribute < 3 && Math.abs(v) > 16)
                            || ((attribute == 3 || attribute == 4) && (v < -0.000001f || v > 1.000001f))
                            || (attribute >= 5 && Math.abs(v) > 1.0001f)) throw new IOException("Invalid DSX1 corner");
                    corners[c] = v;
                }
                parts.put(name, new Part(corners));
            }
            if (data.hasRemaining()) throw new IOException("Trailing DSX1 mesh bytes");
            return new Dsx1Mesh(parts);
        } catch (java.nio.BufferUnderflowException e) {
            throw new IOException("Truncated DSX1 mesh", e);
        }
    }

    public static final class Part {
        private final float[] corners;
        private Part(float[] corners) { this.corners = corners; }
        public int triangles() { return corners.length / 24; }
        public float value(int corner, int attribute) { return corners[corner * 8 + attribute]; }
    }
}
