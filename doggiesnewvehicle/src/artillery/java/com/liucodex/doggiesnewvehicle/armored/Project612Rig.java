package com.liucodex.doggiesnewvehicle.armored;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Neutral geometry shared by server muzzle/collision and the client rigid mesh. */
public final class Project612Rig {
    public record Wheel(String part, Vec3 position, double radius) { }
    public record Track(double x, double y, double front, double rear, double radius, double length, int count) { }
    public record Link(Vec3 position, double angle) { }
    public static final Project612Rig INSTANCE = load();
    public final Vec3 pivot, muzzle, direction;
    public final double scale;
    public final List<Wheel> wheels;
    public final List<Track> tracks;

    private Project612Rig(JsonObject json) {
        if (json.get("schema").getAsInt() != 1) throw new IllegalStateException("Unsupported 612 rig");
        scale = json.get("runtime_scale").getAsDouble();
        if (!Double.isFinite(scale) || scale <= 0 || scale > 1) throw new IllegalStateException("Invalid 612 scale");
        pivot = vector(json.getAsJsonArray("pivot"));
        muzzle = vector(json.getAsJsonObject("muzzle").getAsJsonArray("position"));
        direction = vector(json.getAsJsonObject("muzzle").getAsJsonArray("direction")).normalize();
        List<Wheel> ws = new ArrayList<>();
        for (var value : json.getAsJsonArray("wheels")) {
            var w = value.getAsJsonObject();
            ws.add(new Wheel(w.get("part").getAsString(), vector(w.getAsJsonArray("position")), w.get("radius").getAsDouble()));
        }
        wheels = List.copyOf(ws);
        List<Track> ts = new ArrayList<>();
        for (var value : json.getAsJsonArray("tracks")) {
            var t = value.getAsJsonObject();
            ts.add(new Track(number(t, "x"), number(t, "y"), number(t, "front"), number(t, "rear"),
                    number(t, "radius"), number(t, "length"), t.get("count").getAsInt()));
        }
        tracks = List.copyOf(ts);
        if (wheels.size() != 16 || tracks.size() != 2 || tracks.stream().anyMatch(t -> t.count != 60 || t.radius <= 0 || t.length <= 0))
            throw new IllegalStateException("Incomplete 612 running gear");
    }

    public Matrix4d local(float nativePitch) {
        return new Matrix4d().translate(pivot.x, pivot.y, pivot.z)
                .rotateX(Math.toRadians(Math.max(-25, Math.min(3, nativePitch))))
                .translate(-pivot.x, -pivot.y, -pivot.z);
    }

    public static Vec3 point(Matrix4d matrix, Vec3 point) {
        Vector3d value = matrix.transformPosition(new Vector3d(point.x, point.y, point.z));
        return new Vec3(value.x, value.y, value.z);
    }
    public static Vec3 direction(Matrix4d matrix, Vec3 direction) {
        Vector3d value = matrix.transformDirection(new Vector3d(direction.x, direction.y, direction.z)).normalize();
        return new Vec3(value.x, value.y, value.z);
    }

    /** Arc-length shoes keep the accepted master's upper-rear origin and turn in either direction. */
    public static Link link(Track track, double distance) {
        double s = ApocalypseMotion.wrap(distance + track.length / 2, track.length), straight = track.front - track.rear;
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

    private static double number(JsonObject json, String key) { return json.get(key).getAsDouble(); }
    private static Vec3 vector(JsonArray array) {
        var v = new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
        if (!Double.isFinite(v.x + v.y + v.z)) throw new IllegalStateException("Nonfinite 612 rig");
        return v;
    }
    private static Project612Rig load() {
        try (var input = Project612Rig.class.getResourceAsStream("/assets/doggiesnewvehicle/meshes/project612_rig.json")) {
            if (input == null) throw new IllegalStateException("Missing 612 rig");
            return new Project612Rig(JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception error) { throw new IllegalStateException("Cannot load 612 rig", error); }
    }
}
