package com.liucodex.libertad.ship;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.client.resources.sounds.SoundEventRegistration;
import net.minecraft.client.resources.sounds.SoundEventRegistrationSerializer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

public final class RuntimePackCheck {
    public static void main(String[] args) throws Exception {
        RuntimePhysicsCheck.bootstrapStandalone();
        Path candidate = Path.of(args[0]);
        rejectsMissingPackMetadata();
        Pack assets = pack(candidate, PackType.CLIENT_RESOURCES);
        Pack data = pack(candidate, PackType.SERVER_DATA);
        require(assets != null && data != null && assets.getCompatibility().isCompatible()
                && data.getCompatibility().isCompatible(), "both pack types must load");
        double reportDistance;
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(assets.open()))) {
            try (var sounds = manager.getResourceOrThrow(new ResourceLocation("libertad", "sounds.json")).open();
                 var audio = manager.getResourceOrThrow(new ResourceLocation("libertad", "sounds/cannon_fire_01.ogg")).open()) {
                var fire = JsonParser.parseReader(new InputStreamReader(sounds, StandardCharsets.UTF_8)).getAsJsonObject()
                        .getAsJsonObject("main_cannon_fire");
                var parser = new GsonBuilder().registerTypeAdapter(SoundEventRegistration.class, new SoundEventRegistrationSerializer()).create();
                var sound = parser.fromJson(fire, SoundEventRegistration.class).getSounds();
                require(sound.size() == 1 && sound.get(0).getLocation().toString().equals("libertad:cannon_fire_01")
                        && new String(audio.readNBytes(4), StandardCharsets.US_ASCII).equals("OggS"),
                        "primary report must resolve to one independent OGG asset");
                reportDistance = ShipFireControl.MAIN_CANNON_VOLUME * sound.get(0).getAttenuationDistance();
            }
            var collisionId = new ResourceLocation("libertad", "collision.bin");
            // This conditional eyes layer is not listed in the base-mesh texture table.
            try (var glow = manager.getResourceOrThrow(new ResourceLocation(
                    "libertad", "textures/entity/searchlight-emission.png")).open()) {
                require(glow.read() == 137, "the complete searchlight emission layer must be packaged");
            }
            try (var collision = new DataInputStream(manager.getResourceOrThrow(collisionId).open())) {
                require(collision.readInt() == 0x4C424331, "fire-control obstruction stream must be in the pack");
            }
            try (var mesh = new DataInputStream(manager.getResourceOrThrow(
                    new ResourceLocation("libertad", "ship.bin")).open())) {
                require(mesh.readInt() == 0x4C425432, "candidate visual stream must be in the pack");
                int count = mesh.readUnsignedShort();
                require(count > 0 && count <= 64, "candidate texture table must be bounded");
                for (int i = 0; i < count; i++) {
                    var texture = new ResourceLocation("libertad", mesh.readUTF());
                    try (var image = manager.getResourceOrThrow(texture).open()) {
                        require(image.read() == 137, "bound texture is a readable PNG: " + texture);
                    }
                }
            }
        }
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(data.open()))) {
            var converter = FileToIdConverter.json("sbw/vehicles");
            var id = new ResourceLocation("libertad", "libertad");
            require(converter.listMatchingResources(manager).containsKey(converter.idToFile(id)),
                    "SBW's vehicle data directory must be visible to the reload manager");
            try (var resource = manager.getResourceOrThrow(converter.idToFile(id)).open()) {
                var vehicle = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
                var seats = vehicle.getAsJsonArray("Seats");
                require(seats.size() == 3, "native seat table must expose all three roles");
                for (int seat = 1; seat <= 2; seat++) {
                    var side = seats.get(seat).getAsJsonObject();
                    require(side.get("BanHand").getAsBoolean() && side.getAsJsonArray("Weapons").isEmpty(),
                            "observer seats must block handheld items and native weapon selection");
                }
                var helm = seats.get(0).getAsJsonObject();
                require(!helm.has("MinYaw") && !helm.has("MaxYaw"), "native seat yaw limits would also clamp third-person aim");
                var camera = vehicle.getAsJsonArray("ThirdPersonCameraPos");
                double cameraOffset = Math.hypot(camera.get(1).getAsDouble(), camera.get(2).getAsDouble());
                require(reportDistance > cameraOffset + 64, "primary report must reach the chase camera from all main mounts");
                var weapons = vehicle.getAsJsonObject("Weapons");
                int primary = 0, heavy = 0, light = 0;
                for (var entry : weapons.entrySet()) {
                    var gun = entry.getValue().getAsJsonObject();
                    int ticks = gun.get("EmptyReloadTime").getAsInt();
                    double spread = gun.get("Spread").getAsDouble();
                    if (entry.getKey().startsWith("main_")) {
                        primary++;
                        require(ticks == 400 && gun.getAsJsonObject("SoundInfo").keySet().stream()
                                .noneMatch(key -> key.startsWith("Fire")), "primary reload or duplicate fire sound");
                        var he = gun.getAsJsonArray("AmmoType").get(1).getAsJsonObject().getAsJsonObject("Override");
                        require(spread == .10 && he.get("ExplosionDestroy").getAsBoolean()
                                && he.get("ExplosionRadius").getAsInt() == 6, "main dispersion and six-block HE crater");
                    } else if (entry.getKey().startsWith("secondary_234_")) {
                        heavy++;
                        require(ticks == 110 && spread == .12, "234 mm reload and dispersion");
                    } else if (entry.getKey().startsWith("secondary_127_")) {
                        light++;
                        require(ticks == 80 && spread == .12, "127 mm reload and dispersion");
                    }
                }
                require(primary == 12 && heavy == 20 && light == 8, "native gun groups are incomplete");
            }
        }
        System.out.println("PASS: Minecraft pack metadata, renderer texture IDs, and SBW vehicle resource discovery");
    }

    private static void rejectsMissingPackMetadata() throws Exception {
        Path missingMetadata = Files.createTempFile("libertad-missing-pack-metadata-", ".zip");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(missingMetadata))) {
                zip.putNextEntry(new ZipEntry("assets/libertad/probe.txt"));
                zip.closeEntry();
            }
            require(pack(missingMetadata, PackType.CLIENT_RESOURCES) == null
                    && pack(missingMetadata, PackType.SERVER_DATA) == null,
                    "missing pack.mcmeta must reject both pack types");
        } finally {
            Files.deleteIfExists(missingMetadata);
        }
    }

    private static Pack pack(Path path, PackType type) {
        return Pack.readMetaAndCreate("mod:libertad", Component.literal("Libertad"), true,
                id -> Files.isDirectory(path) ? new PathPackResources(id, path, false)
                        : new FilePackResources(id, path.toFile(), false),
                type, Pack.Position.TOP, PackSource.DEFAULT);
    }

    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
