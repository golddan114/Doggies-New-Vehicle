package com.liucodex.doggiesnewvehicle.armored;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** One neutral, +Z-forward coordinate authority for rendering, muzzle positions and local collision. */
public final class ApocalypseRig {
    public record Muzzle(Vec3 position, Vec3 direction) { }
    public record Wheel(String part, Vec3 position, double radius) { }
    public record Track(String code, double x, double y, double front, double rear, double radius, double length, int count) { }
    public record Link(Vec3 position, double angle) { }
    public record Box(String name, String transform, String part, Vec3 center, Vec3 half) { }
    public static final ApocalypseRig INSTANCE = load();
    public final Vec3 yaw;
    public final Vec3 main;
    public final Vec3 rocket;
    public final Map<String, String> parts;
    public final Map<String, Muzzle> muzzles;
    public final List<Muzzle> exhausts;
    public final List<Wheel> wheels;
    public final List<Track> tracks;
    public final List<Box> boxes;

    private ApocalypseRig(JsonObject json) {
        if (json.get("schema").getAsInt() != 1) throw new IllegalStateException("Unsupported Apocalypse rig");
        yaw = vector(json.getAsJsonArray("yaw"));
        main = vector(json.getAsJsonObject("pivots").getAsJsonArray("main_l"));
        rocket = vector(json.getAsJsonObject("pivots").getAsJsonArray("rocket_l"));
        Map<String, String> partMap = new LinkedHashMap<>();
        json.getAsJsonObject("parts").entrySet().forEach(e -> partMap.put(e.getKey(), e.getValue().getAsString()));
        parts = Map.copyOf(partMap);
        Map<String, Muzzle> muzzleMap = new LinkedHashMap<>();
        json.getAsJsonObject("muzzles").entrySet().forEach(e -> {
            JsonObject m = e.getValue().getAsJsonObject();
            muzzleMap.put(e.getKey(), new Muzzle(vector(m.getAsJsonArray("position")), vector(m.getAsJsonArray("direction"))));
        });
        muzzles = Map.copyOf(muzzleMap);
        List<Muzzle> exhaustList = new ArrayList<>();
        for (var value : json.getAsJsonArray("exhausts")) {
            JsonObject e = value.getAsJsonObject();
            exhaustList.add(new Muzzle(vector(e.getAsJsonArray("position")), vector(e.getAsJsonArray("direction"))));
        }
        exhausts = List.copyOf(exhaustList);
        List<Wheel> wheelList = new ArrayList<>();
        for (var value : json.getAsJsonArray("wheels")) {
            JsonObject w = value.getAsJsonObject();
            wheelList.add(new Wheel(w.get("part").getAsString(), vector(w.getAsJsonArray("position")), w.get("radius").getAsDouble()));
        }
        wheels = List.copyOf(wheelList);
        List<Track> trackList = new ArrayList<>();
        for (var value : json.getAsJsonArray("tracks")) {
            JsonObject t = value.getAsJsonObject();
            trackList.add(new Track(t.get("code").getAsString(), number(t, "x"), number(t, "y"), number(t, "front"),
                    number(t, "rear"), number(t, "radius"), number(t, "length"), t.get("count").getAsInt()));
        }
        tracks = List.copyOf(trackList);
        List<Box> boxList = new ArrayList<>();
        for (var value : json.getAsJsonArray("boxes")) {
            JsonObject b = value.getAsJsonObject();
            boxList.add(new Box(b.get("name").getAsString(), b.get("transform").getAsString(), b.get("part").getAsString(),
                    vector(b.getAsJsonArray("center")), vector(b.getAsJsonArray("half"))));
        }
        boxes = List.copyOf(boxList);
        if (muzzles.size() != 14 || exhausts.size() != 2 || wheels.size() != 16 || tracks.size() != 4 || boxes.size() != 13) {
            throw new IllegalStateException("Incomplete Apocalypse rig");
        }
    }

    public Vec3 forwardCameraAnchor() {
        // The rendered turret ends at z=2.246; retain the authored driver eye height.
        return new Vec3(yaw.x, yaw.y + 1.4, 2.55);
    }

    /** Matrices take neutral game-space mesh points; positive elevation points above +Z. */
    public Matrix4d local(String group, double turretRotation, double elevation, double rocketElevation, double recoil) {
        Matrix4d matrix = new Matrix4d();
        if (group.equals("Body")) return matrix;
        around(matrix, yaw, 0, Math.toRadians(turretRotation));
        if (group.startsWith("Main")) {
            around(matrix, main, Math.toRadians(-elevation), 0);
            if (!group.equals("Main")) matrix.translate(0, 0, -.18 * recoil);
        } else if (group.equals("Rocket")) {
            around(matrix, rocket, Math.toRadians(-rocketElevation), 0);
        } else if (!group.equals("Turret")) {
            throw new IllegalArgumentException("Unknown Apocalypse transform " + group);
        }
        return matrix;
    }

    public static Vec3 transformPoint(Matrix4d matrix, Vec3 point) {
        Vector3d v = matrix.transformPosition(new Vector3d(point.x, point.y, point.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public static Vec3 transformDirection(Matrix4d matrix, Vec3 direction) {
        Vector3d v = matrix.transformDirection(new Vector3d(direction.x, direction.y, direction.z)).normalize();
        return new Vec3(v.x, v.y, v.z);
    }

    public static Link link(Track track, double distance) {
        double s = ApocalypseMotion.wrap(distance, track.length), straight = track.front - track.rear;
        if (s < straight) return new Link(new Vec3(track.x, track.y - track.radius, track.front - s), 0);
        s -= straight;
        if (s < Math.PI * track.radius) {
            double angle = s / track.radius;
            return new Link(new Vec3(track.x, track.y - track.radius * Math.cos(angle), track.rear - track.radius * Math.sin(angle)), angle);
        }
        s -= Math.PI * track.radius;
        if (s < straight) return new Link(new Vec3(track.x, track.y + track.radius, track.rear + s), Math.PI);
        double angle = Math.PI + (s - straight) / track.radius;
        return new Link(new Vec3(track.x, track.y - track.radius * Math.cos(angle), track.front - track.radius * Math.sin(angle)), angle);
    }

    private static void around(Matrix4d matrix, Vec3 pivot, double pitch, double yaw) {
        matrix.translate(pivot.x, pivot.y, pivot.z).rotateY(yaw).rotateX(pitch).translate(-pivot.x, -pivot.y, -pivot.z);
    }

    private static double number(JsonObject json, String name) { return json.get(name).getAsDouble(); }
    private static Vec3 vector(JsonArray array) {
        Vec3 v = new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
        if (!Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z)) throw new IllegalStateException("Nonfinite rig point");
        return v;
    }

    private static ApocalypseRig load() {
        try (var input = ApocalypseRig.class.getResourceAsStream("/assets/doggiesnewvehicle/meshes/apocalypse_rig.json")) {
            if (input == null) throw new IllegalStateException("Missing Apocalypse rig");
            return new ApocalypseRig(JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load Apocalypse rig", failure);
        }
    }
}
