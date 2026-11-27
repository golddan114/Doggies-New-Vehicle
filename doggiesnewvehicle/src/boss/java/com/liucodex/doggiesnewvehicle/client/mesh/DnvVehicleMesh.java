package com.liucodex.doggiesnewvehicle.client.mesh;

import com.liucodex.doggiesnewvehicle.boss.DnvBossRunningGear;
import com.liucodex.doggiesnewvehicle.boss.mesh.DnvMeshCodec;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/** Shared SHM parser and resource-reload owner for the two DNV vehicles. */
public final class DnvVehicleMesh {
    public static final int FLOATS_PER_VERTEX = 8;
    private static final Set<String> BASE_REQUIRED_PARTS = Set.of(
            "chassis/body",
            "chassis/track_inner",
            "core/body",
            "main_gun_l/body",
            "main_gun_r/body",
            "autocannon_l_yaw/body",
            "autocannon_l_pitch/body",
            "autocannon_r_yaw/body",
            "autocannon_r_pitch/body",
            "missile_pod_l_yaw/body",
            "missile_pod_l_pitch/body",
            "missile_pod_r_yaw/body",
            "missile_pod_r_pitch/body");
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DnvVehicleMesh NOISE = new DnvVehicleMesh(
            "Noise",
            ResourceLocation.fromNamespaceAndPath("noise", "meshes/noise.shm"),
            Set.of());
    public static final DnvVehicleMesh SILENT_HEAVEN = new DnvVehicleMesh(
            "Silent Heaven",
            ResourceLocation.fromNamespaceAndPath(
                    "silent_heaven", "meshes/silent_heaven.shm"),
            Set.of("halo_ring/light"));

    private final String displayName;
    private final ResourceLocation resource;
    private final Set<String> additionalRequiredParts;
    private volatile MeshData cached;

    private DnvVehicleMesh(
            String displayName,
            ResourceLocation resource,
            Set<String> additionalRequiredParts) {
        this.displayName = displayName;
        this.resource = resource;
        this.additionalRequiredParts = Set.copyOf(additionalRequiredParts);
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

    public ReloadListener reloadListener() {
        return new ReloadListener(this);
    }
    public MeshData get(double distanceSquared) {
        return get().detail(com.liucodex.doggiesnewvehicle.client.DnvRenderDetail.level(distanceSquared));
    }

    MeshData parse(byte[] bytes) {
        Map<String, MeshPart> parts = new HashMap<>();
        try {
            DnvMeshCodec.parse(bytes).forEach((name, part) ->
                    parts.put(name, new MeshPart(part.material(), part.stream())));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Invalid " + displayName + " mesh: " + failure.getMessage(), failure);
        }
        for (String required : BASE_REQUIRED_PARTS) {
            requirePart(parts, required);
        }
        for (String required : additionalRequiredParts) {
            requirePart(parts, required);
        }
        for (String side : new String[] {"l", "r"}) {
            for (int index = 0; index < 4; index++) {
                requirePart(parts, "road_wheel_" + side + "_" + index + "/body");
            }
            for (int index = 0; index < DnvBossRunningGear.SHOE_COUNT; index++) {
                requirePart(parts, "track_shoe_" + side + "_" + index + "/track");
            }
        }
        return new MeshData(parts);
    }

    private MeshData load(ResourceManager resourceManager) {
        try (InputStream input = resourceManager.open(resource)) {
            byte[] bytes = input.readAllBytes();
            MeshData mesh = parse(bytes);
            for (int level = 1; level <= 2; level++) {
                var variant = ResourceLocation.fromNamespaceAndPath(resource.getNamespace(),
                        resource.getPath().replace(".shm", "_lod" + level + ".shm"));
                try (var lodInput = resourceManager.open(variant);
                        var proof = resourceManager.open(ResourceLocation.fromNamespaceAndPath(resource.getNamespace(), variant.getPath() + ".sha256"))) {
                    byte[] lodBytes = lodInput.readAllBytes();
                    if (!com.liucodex.doggiesnewvehicle.client.DnvRenderDetail.matches(bytes, lodBytes, proof.readAllBytes())) continue;
                    MeshData lod = parse(lodBytes);
                    if (lod.parts.keySet().equals(mesh.parts.keySet()) && lod.triangleCount() <= mesh.triangleCount()) mesh.lods[level - 1] = lod;
                } catch (IOException | RuntimeException optionalMissing) { /* Full mesh remains available. */ }
            }
            LOGGER.info(
                    "Loaded {} mesh: {} material parts, {} triangles",
                    displayName,
                    mesh.partCount(),
                    mesh.triangleCount());
            return mesh;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to load {} mesh {}", displayName, resource, exception);
            return MeshData.EMPTY;
        }
    }

    private void install(MeshData mesh) {
        cached = mesh;
    }

    private void requirePart(Map<String, MeshPart> parts, String name) {
        if (!parts.containsKey(name)) {
            throw invalid("missing required mesh part: " + name);
        }
    }

    private IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("Invalid " + displayName + " " + detail);
    }

    public static final class MeshData {
        private static final MeshPart EMPTY_PART = new MeshPart(-1, new float[0]);
        private static final MeshData EMPTY = new MeshData(Map.of());

        private final Map<String, MeshPart> parts;
        private final MeshData[] lods = new MeshData[2];

        private MeshData(Map<String, MeshPart> parts) {
            this.parts = Collections.unmodifiableMap(parts);
        }

        public MeshPart part(String name) {
            return parts.getOrDefault(name, EMPTY_PART);
        }

        public boolean isEmpty() {
            return parts.isEmpty();
        }
        public MeshData detail(int level) {
            return level > 0 && level <= lods.length && lods[level - 1] != null ? lods[level - 1] : this;
        }

        public int partCount() {
            return parts.size();
        }

        public int triangleCount() {
            return parts.values().stream()
                    .mapToInt(part -> part.stream().length / (FLOATS_PER_VERTEX * 3))
                    .sum();
        }
    }

    public record MeshPart(int material, float[] stream) {
    }

    public static final class ReloadListener
            extends SimplePreparableReloadListener<MeshData> {
        private final DnvVehicleMesh owner;

        private ReloadListener(DnvVehicleMesh owner) {
            this.owner = owner;
        }

        @Override
        protected MeshData prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            return owner.load(resourceManager);
        }

        @Override
        protected void apply(
                MeshData mesh,
                ResourceManager resourceManager,
                ProfilerFiller profiler) {
            owner.install(mesh);
        }
    }
}
