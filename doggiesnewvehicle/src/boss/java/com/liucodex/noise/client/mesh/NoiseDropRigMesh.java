package com.liucodex.noise.client.mesh;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

public final class NoiseDropRigMesh {
    public static final int FLOATS_PER_VERTEX = 8;
    private static final int MAX_PART_COUNT = 256;
    private static final int MAX_VERTEX_COUNT_PER_PART = 2_000_000;
    private static final Set<String> REQUIRED_PARTS = Set.of(
            "drop_rig_core/body",
            "drop_rig_thruster_fl/body",
            "drop_rig_thruster_fr/body",
            "drop_rig_thruster_rl/body",
            "drop_rig_thruster_rr/body");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation RESOURCE =
            ResourceLocation.fromNamespaceAndPath("noise", "meshes/noise_drop_rig.shm");
    private static final MeshPart EMPTY_PART = new MeshPart(-1, new float[0]);
    private static volatile NoiseDropRigMesh cached;

    private final Map<String, MeshPart> parts;

    private NoiseDropRigMesh(Map<String, MeshPart> parts) {
        this.parts = Collections.unmodifiableMap(parts);
    }

    public static NoiseDropRigMesh get() {
        NoiseDropRigMesh result = cached;
        if (result != null) {
            return result;
        }
        synchronized (NoiseDropRigMesh.class) {
            if (cached == null) {
                cached = load(Minecraft.getInstance().getResourceManager());
            }
            return cached;
        }
    }

    public MeshPart part(String name) {
        return parts.getOrDefault(name, EMPTY_PART);
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public int partCount() {
        return parts.size();
    }

    public int triangleCount() {
        return parts.values().stream()
                .mapToInt(part -> part.stream().length / (FLOATS_PER_VERTEX * 3))
                .sum();
    }

    static NoiseDropRigMesh load(ResourceManager resourceManager) {
        try (InputStream input = resourceManager.open(RESOURCE)) {
            NoiseDropRigMesh mesh = parse(input.readAllBytes());
            LOGGER.info(
                    "Loaded Silent Heaven mesh: {} material parts, {} triangles",
                    mesh.partCount(),
                    mesh.triangleCount());
            return mesh;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to load Silent Heaven mesh {}", RESOURCE, exception);
            return new NoiseDropRigMesh(Map.of());
        }
    }

    static void install(NoiseDropRigMesh mesh) {
        cached = mesh;
    }

    static NoiseDropRigMesh parse(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        buffer.get(magic);
        if (!"SHM1".equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Invalid Silent Heaven mesh magic");
        }
        int version = buffer.getInt();
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported Silent Heaven mesh version: " + version);
        }
        int partCount = checkedCount(buffer.getInt(), MAX_PART_COUNT, "part");
        Map<String, MeshPart> parts = new HashMap<>();
        for (int index = 0; index < partCount; index++) {
            int nameLength = Short.toUnsignedInt(buffer.getShort());
            if (nameLength == 0 || nameLength > 128 || nameLength > buffer.remaining()) {
                throw new IllegalArgumentException("Invalid mesh part name length: " + nameLength);
            }
            byte[] encodedName = new byte[nameLength];
            buffer.get(encodedName);
            String name = new String(encodedName, StandardCharsets.UTF_8);
            int material = buffer.getInt();
            if (material < 0 || material > 3) {
                throw new IllegalArgumentException("Invalid material id: " + material);
            }
            int vertexCount = checkedCount(buffer.getInt(), MAX_VERTEX_COUNT_PER_PART, "vertex");
            if (vertexCount % 3 != 0) {
                throw new IllegalArgumentException("Mesh stream has incomplete triangles: " + name);
            }
            int floatCount = Math.multiplyExact(vertexCount, FLOATS_PER_VERTEX);
            int byteCount = Math.multiplyExact(floatCount, Float.BYTES);
            if (byteCount > buffer.remaining()) {
                throw new IllegalArgumentException("Truncated mesh stream: " + name);
            }
            float[] stream = new float[floatCount];
            for (int value = 0; value < floatCount; value++) {
                stream[value] = buffer.getFloat();
            }
            if (parts.put(name, new MeshPart(material, stream)) != null) {
                throw new IllegalArgumentException("Duplicate Silent Heaven mesh part: " + name);
            }
        }
        if (buffer.hasRemaining()) {
            throw new IllegalArgumentException("Unexpected bytes at end of Silent Heaven mesh");
        }
        for (String required : REQUIRED_PARTS) {
            if (!parts.containsKey(required)) {
                throw new IllegalArgumentException("Missing required Silent Heaven mesh part: " + required);
            }
        }
        return new NoiseDropRigMesh(parts);
    }

    private static int checkedCount(int value, int maximum, String label) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + value);
        }
        return value;
    }

    public record MeshPart(int material, float[] stream) {
    }
}


