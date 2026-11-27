package com.liucodex.doggiesnewvehicle.boss.mesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** SHM1 byte contract shared by the three boss mesh consumers; no client or world dependencies. */
public final class DnvMeshCodec {
    public static final int STRIDE = 8;
    public record Part(int material, float[] stream) { }
    private DnvMeshCodec() { }

    public static Map<String, Part> parse(byte[] bytes) {
        if (bytes == null || bytes.length < 12 || bytes.length > 128 * 1024 * 1024) throw invalid("size");
        try {
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (b.getInt() != 0x314d4853 || b.getInt() != 1) throw invalid("header");
            int count = b.getInt();
            if (count < 1 || count > 256) throw invalid("part count");
            Map<String, Part> parts = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int length = Short.toUnsignedInt(b.getShort());
                if (length < 1 || length > 128 || length > b.remaining()) throw invalid("name");
                byte[] nameBytes = new byte[length];
                b.get(nameBytes);
                String name = new String(nameBytes, StandardCharsets.UTF_8);
                int material = b.getInt(), vertices = b.getInt();
                if (material < 0 || material > 3 || vertices < 0 || vertices > 2_000_000 || vertices % 3 != 0
                        || (long) vertices * STRIDE * 4 > b.remaining()) throw invalid("part stream");
                float[] stream = new float[vertices * STRIDE];
                for (int j = 0; j < stream.length; j++) {
                    stream[j] = b.getFloat();
                    if (!Float.isFinite(stream[j])) throw invalid("non-finite vertex");
                }
                if (parts.put(name, new Part(material, stream)) != null) throw invalid("duplicate part");
            }
            if (b.hasRemaining()) throw invalid("trailing bytes");
            return java.util.Collections.unmodifiableMap(parts);
        } catch (java.nio.BufferUnderflowException exception) {
            throw invalid("truncated stream");
        }
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("Invalid SHM1 " + detail);
    }
}
