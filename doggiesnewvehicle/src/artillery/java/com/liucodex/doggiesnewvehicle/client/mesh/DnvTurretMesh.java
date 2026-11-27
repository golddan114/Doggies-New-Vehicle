package com.liucodex.doggiesnewvehicle.client.mesh;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

/** Profile-scoped owner for the Blender-authored ETM1 turret meshes. */
public final class DnvTurretMesh {
    public static final DnvTurretMesh NUCLEAR_SILO = new DnvTurretMesh("Nuclear silo", "nuclear_silo.etm", 512);
    public static final DnvTurretMesh NUCLEAR_MISSILE = new DnvTurretMesh("Nuclear missile", "nuclear_missile.etm", 32);
    public static final DnvTurretMesh DOGEGG = new DnvTurretMesh("Dog egg", "dogegg.etm", 2);
    public static final int FLOATS_PER_VERTEX = 8;
    public static final DnvTurretMesh BLIZZARD_MISSILE = new DnvTurretMesh("Blizzard cryo missile", "blizzard_missile.etm", 1);
    public static final DnvTurretMesh BLIZZARD = new DnvTurretMesh("Blizzard hover tank", "blizzard.etm", 5);
    public static final DnvTurretMesh APOCALYPSE = new DnvTurretMesh("Apocalypse tank", "apocalypse.etm", 32);
    public static final DnvTurretMesh PROJECT612 = new DnvTurretMesh("Project 612 tank destroyer", "project612.etm", 5);
    public static final DnvTurretMesh ENDFIELD = new DnvTurretMesh(
            "Endfield turret",
            "endfield_sniper_turret.etm",
            64);
    public static final DnvTurretMesh JINGZHE = new DnvTurretMesh(
            "Jingzhe turret",
            "jingzhe_turret.etm",
            128);
    public static final DnvTurretMesh STORM = new DnvTurretMesh(
            "Storm turret",
            "storm_turret.etm",
            64);
    public static final DnvTurretMesh M1 = new DnvTurretMesh(
            "M1 Yunlong Remote Heavy Cannon",
            "m1_yunlong.etm",
            32);
    public static final Map<String,DnvTurretMesh> DRAGON = dragonMeshes();
    private static Map<String,DnvTurretMesh> dragonMeshes(){
        var meshes=new java.util.LinkedHashMap<String,DnvTurretMesh>();
        for(var p:com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile.ALL.values())
            meshes.put(p.entity,p.entity.equals("m1_yunlong")?M1:new DnvTurretMesh(p.entity,p.entity+".etm",32));
        return Collections.unmodifiableMap(meshes);
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MeshPart EMPTY = new MeshPart(new float[0], new float[0]);

    private final String displayName;
    private final ResourceLocation resource;
    private final int maximumPartCount;
    private volatile MeshData cached;

    private DnvTurretMesh(
            String displayName,
            String resourceName,
            int maximumPartCount
    ) {
        this.displayName = displayName;
        this.resource = ResourceLocation.fromNamespaceAndPath(
                "doggiesnewvehicle", "meshes/" + resourceName);
        this.maximumPartCount = maximumPartCount;
    }

    public MeshData get() {
        MeshData result = cached;
        if (result != null) {
            return result;
        }
        synchronized (this) {
            if (cached == null) {
                cached = load(Minecraft.getInstance().getResourceManager());
            }
            return cached;
        }
    }
    public MeshData get(double distanceSquared) {
        return get().detail(com.liucodex.doggiesnewvehicle.client.DnvRenderDetail.level(distanceSquared));
    }

    MeshData load(ResourceManager resourceManager) {
        try (InputStream input = resourceManager.open(resource)) {
            byte[] bytes = input.readAllBytes();
            MeshData mesh = parse(bytes);
            for (int level = 1; level <= 2; level++) {
                String path = resource.getPath().replace(".etm", "_lod" + level + ".etm");
                var lodResource = ResourceLocation.fromNamespaceAndPath(resource.getNamespace(), path);
                try (var lodInput = resourceManager.open(lodResource);
                        var proof = resourceManager.open(ResourceLocation.fromNamespaceAndPath(resource.getNamespace(), path + ".sha256"))) {
                    byte[] lodBytes = lodInput.readAllBytes();
                    if (!com.liucodex.doggiesnewvehicle.client.DnvRenderDetail.matches(bytes, lodBytes, proof.readAllBytes())) continue;
                    MeshData lod = parse(lodBytes);
                    if (lod.parts.keySet().equals(mesh.parts.keySet()) && lod.triangleCount() <= mesh.triangleCount()) mesh.lods[level - 1] = lod;
                } catch (IOException | RuntimeException optionalMissing) { /* Full mesh is the safe resource-pack fallback. */ }
            }
            return mesh;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to load {} mesh {}", displayName, resource, exception);
            return new MeshData(Map.of());
        }
    }

    void install(MeshData mesh) {
        cached = mesh;
    }

    MeshData parse(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        buffer.get(magic);
        if (!"ETM1".equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException(
                    "Invalid " + displayName + " mesh magic");
        }
        int version = buffer.getInt();
        if (version != 1) {
            throw new IllegalArgumentException(
                    "Unsupported " + displayName + " mesh version: " + version);
        }

        int partCount = checkedCount(
                buffer.getInt(), maximumPartCount, "part");
        Map<String, MeshPart> parts = new HashMap<>();
        for (int index = 0; index < partCount; index++) {
            int nameLength = Short.toUnsignedInt(buffer.getShort());
            if (nameLength == 0
                    || nameLength > 128
                    || nameLength > buffer.remaining()) {
                throw new IllegalArgumentException(
                        "Invalid mesh part name length: " + nameLength);
            }
            byte[] encodedName = new byte[nameLength];
            buffer.get(encodedName);
            String name = new String(encodedName, StandardCharsets.UTF_8);
            int mainVertexCount = checkedCount(
                    buffer.getInt(), 2_000_000, "main vertex");
            int emissiveVertexCount = checkedCount(
                    buffer.getInt(), 2_000_000, "emissive vertex");
            if (mainVertexCount % 3 != 0 || emissiveVertexCount % 3 != 0) {
                throw new IllegalArgumentException(
                        "Mesh streams must contain complete triangles");
            }
            parts.put(name, new MeshPart(
                    readStream(buffer, mainVertexCount),
                    readStream(buffer, emissiveVertexCount)));
        }
        if (buffer.hasRemaining()) {
            throw new IllegalArgumentException(
                    "Unexpected bytes at the end of the mesh");
        }
        return new MeshData(parts);
    }

    private static int checkedCount(int value, int maximum, String label) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(
                    "Invalid " + label + " count: " + value);
        }
        return value;
    }

    private float[] readStream(ByteBuffer buffer, int vertexCount) {
        int floatCount = Math.multiplyExact(vertexCount, FLOATS_PER_VERTEX);
        int byteCount = Math.multiplyExact(floatCount, Float.BYTES);
        if (byteCount > buffer.remaining()) {
            throw new IllegalArgumentException(
                    "Truncated " + displayName + " mesh stream");
        }
        float[] stream = new float[floatCount];
        for (int index = 0; index < floatCount; index++) {
            stream[index] = buffer.getFloat();
        }
        return stream;
    }

    public static final class MeshData {
        private final Map<String, MeshPart> parts;
        private final MeshData[] lods = new MeshData[2];

        private MeshData(Map<String, MeshPart> parts) {
            this.parts = Collections.unmodifiableMap(parts);
        }

        public MeshPart part(String name) {
            return parts.getOrDefault(name, EMPTY);
        }
        public MeshData detail(int level) {
            return level > 0 && level <= lods.length && lods[level - 1] != null ? lods[level - 1] : this;
        }

        int partCount() {
            return parts.size();
        }

        int triangleCount() {
            int vertexCount = parts.values().stream()
                    .mapToInt(part -> (part.main().length
                            + part.emissive().length) / FLOATS_PER_VERTEX)
                    .sum();
            return vertexCount / 3;
        }
    }

    public record MeshPart(float[] main, float[] emissive) {
    }
}
