package com.liucodex.doggiesnewvehicle.armored;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Original neutral geometry with corrected v08 normals; rendering and emitters share these pivots. */
public final class BlizzardRig {
    public record Anchor(String group, String role, Vec3 position, Vec3 direction) { }
    public static final BlizzardRig INSTANCE = load();
    public final Vec3 yaw, barrel;
    public final double minElevation, maxElevation;
    private final double[] depression;
    public final Map<String, Anchor> anchors;
    private final Map<String, Vec3> hatchSlides;

    private BlizzardRig(JsonObject json) {
        if (json.get("schema").getAsInt() != 1) throw new IllegalStateException("Unsupported Blizzard rig");
        yaw = vector(json.getAsJsonArray("yaw"));
        barrel = vector(json.getAsJsonArray("barrel"));
        maxElevation = json.get("elevation_max_deg").getAsDouble();
        minElevation = json.get("elevation_min_deg").getAsDouble();
        var limits = json.getAsJsonArray("depression_limits_deg");
        if (limits == null || limits.size() != 72) throw new IllegalStateException("Missing depression envelope");
        depression = new double[72];
        for (int i = 0; i < depression.length; i++) {
            depression[i] = limits.get(i).getAsDouble();
            if (!Double.isFinite(depression[i]) || depression[i] < 0 || depression[i] > 25) throw new IllegalStateException("Invalid depression envelope");
        }
        Map<String, Anchor> result = new LinkedHashMap<>();
        json.getAsJsonObject("anchors").entrySet().forEach(entry -> {
            JsonObject a = entry.getValue().getAsJsonObject();
            result.put(entry.getKey(), new Anchor(a.get("group").getAsString(), a.get("role").getAsString(),
                    vector(a.getAsJsonArray("position")), vector(a.getAsJsonArray("direction")).normalize()));
        });
        anchors = Map.copyOf(result);
        Map<String, Vec3> hatches = new LinkedHashMap<>();
        if (json.has("hatches")) json.getAsJsonObject("hatches").entrySet().forEach(e ->
                hatches.put(e.getKey(), vector(e.getValue().getAsJsonObject().getAsJsonArray("slide"))));
        hatchSlides = Map.copyOf(hatches);
        if (anchors.size() != 12 || !anchors.containsKey("main_gun_muzzle")
                || !anchors.containsKey("missile_launch_L") || !anchors.containsKey("missile_launch_R")
                || !Double.isFinite(maxElevation) || maxElevation <= 0 || maxElevation >= 90) {
            throw new IllegalStateException("Incomplete Blizzard rig");
        }
    }

    /** Includes the next tick's body/turret sweep, so pitch clamps before crossing a tighter heading. */
    public double minElevation(double yawDegrees) {
        if (!Double.isFinite(yawDegrees)) return 0;
        int bin = Math.floorMod((int) Math.floor(yawDegrees / 5), depression.length);
        double limit = 25;
        for (int offset = -3; offset <= 3; offset++) limit = Math.min(limit, depression[Math.floorMod(bin + offset, depression.length)]);
        return -limit;
    }

    public Matrix4d local(String group, double rotation, double elevation) {
        return local(group, rotation, elevation, 0);
    }
    public Matrix4d local(String group, double rotation, double elevation, double opening) {
        Matrix4d matrix = new Matrix4d();
        if (group.equals("Body")) return matrix;
        if (hatchSlides.containsKey(group)) {
            Vec3 slide = hatchSlides.get(group);
            double p = Double.isFinite(opening) ? Math.max(0, Math.min(1, opening)) : 0;
            // The covers remain recessed; their accepted travel is lateral only.
            double t = p*p*(3-2*p);
            return matrix.translate(slide.x*t, slide.y*t, slide.z*t);
        }
        if (!group.equals("Turret") && !group.equals("Barrel")) throw new IllegalArgumentException(group);
        matrix.translate(yaw.x, yaw.y, yaw.z).rotateY(Math.toRadians(rotation)).translate(-yaw.x, -yaw.y, -yaw.z);
        if (group.equals("Barrel")) {
            matrix.translate(barrel.x, barrel.y, barrel.z).rotateX(Math.toRadians(-elevation))
                    .translate(-barrel.x, -barrel.y, -barrel.z);
        }
        return matrix;
    }

    public static Vec3 point(Matrix4d matrix, Vec3 point) {
        Vector3d v = matrix.transformPosition(new Vector3d(point.x, point.y, point.z));
        return new Vec3(v.x, v.y, v.z);
    }
    public static Vec3 direction(Matrix4d matrix, Vec3 direction) {
        Vector3d v = matrix.transformDirection(new Vector3d(direction.x, direction.y, direction.z)).normalize();
        return new Vec3(v.x, v.y, v.z);
    }
    private static Vec3 vector(JsonArray array) {
        if (array == null || array.size() != 3) throw new IllegalStateException("Invalid Blizzard vector");
        Vec3 v = new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
        if (!Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z)) throw new IllegalStateException("Nonfinite rig");
        return v;
    }
    private static BlizzardRig load() {
        try (var in = BlizzardRig.class.getResourceAsStream("/assets/doggiesnewvehicle/meshes/blizzard_rig.json")) {
            if (in == null) throw new IllegalStateException("Missing Blizzard rig");
            return new BlizzardRig(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load Blizzard rig", failure);
        }
    }
}
