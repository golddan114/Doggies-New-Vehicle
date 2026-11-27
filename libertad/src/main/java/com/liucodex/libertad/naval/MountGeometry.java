package com.liucodex.libertad.naval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Ship-local blocks. Positive elevation raises the bore. Angles are relative to the authored rest pose. */
public record MountGeometry(String id, Point yawCenter, Point pitchCenter, Point forward, List<Barrel> barrels) {
    private static final Point UP = new Point(0, 1, 0);

    public MountGeometry {
        Objects.requireNonNull(id);
        Objects.requireNonNull(yawCenter);
        Objects.requireNonNull(pitchCenter);
        Objects.requireNonNull(forward);
        if (Math.abs(forward.y()) > 1e-6 || Math.abs(forward.lengthSquared() - 1) > 1e-6) {
            throw new IllegalArgumentException("Expected a horizontal unit bore: " + id);
        }
        barrels = List.copyOf(barrels);
        int expected = id.startsWith("secondary_127_") ? 1 : 2;
        if (barrels.size() != expected) throw new IllegalArgumentException("Wrong barrel count: " + id);
    }

    public List<BarrelPose> at(Angles angles) {
        Point pitchAxis = forward.cross(UP);
        List<BarrelPose> result = new ArrayList<>(barrels.size());
        Point movedPitchCenter = yawCenter.add(pitchCenter.minus(yawCenter).rotate(UP, angles.yaw()));
        for (Barrel barrel : barrels) {
            Point tilted = barrel.muzzle().minus(pitchCenter).rotate(pitchAxis, angles.pitch());
            Point muzzle = movedPitchCenter.add(tilted.rotate(UP, angles.yaw()));
            Point direction = barrel.forward().rotate(pitchAxis, angles.pitch()).rotate(UP, angles.yaw());
            result.add(new BarrelPose(barrel.gunId(), movedPitchCenter, muzzle, direction));
        }
        return List.copyOf(result);
    }

    public static List<MountGeometry> load() throws IOException {
        var input = MountGeometry.class.getResourceAsStream("/data/libertad/mounts.tsv");
        if (input == null) throw new IOException("Missing model-derived mounts.tsv");
        var mounts = new LinkedHashMap<String, List<Row>>();
        var gunIds = new HashSet<String>();
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 6 || !gunIds.add(fields[1])) throw new IOException("Invalid mount row: " + line);
                Point bore = Point.parse(fields[5]);
                mounts.computeIfAbsent(fields[0], key -> new ArrayList<>()).add(new Row(
                        Point.parse(fields[2]), Point.parse(fields[3]), new Barrel(fields[1], Point.parse(fields[4]), bore)));
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid model-derived mount data", e);
        }
        List<MountGeometry> result = new ArrayList<>(24);
        for (var entry : mounts.entrySet()) {
            List<Row> rows = entry.getValue();
            Row first = rows.get(0);
            if (rows.stream().anyMatch(r -> !r.yaw().equals(first.yaw()) || !r.pitch().equals(first.pitch()))) {
                throw new IOException("Barrels disagree on turret pivots: " + entry.getKey());
            }
            result.add(new MountGeometry(entry.getKey(), first.yaw(), first.pitch(), first.barrel().forward(),
                    rows.stream().map(Row::barrel).toList()));
        }
        if (result.size() != 24 || gunIds.size() != 40) throw new IOException("Incomplete Libertad mount table");
        return List.copyOf(result);
    }

    private record Row(Point yaw, Point pitch, Barrel barrel) {}
    public record Barrel(String gunId, Point muzzle, Point forward) {}
    public record BarrelPose(String gunId, Point pitchCenter, Point muzzle, Point forward) {}

    public record Angles(double yaw, double pitch) {
        public Angles {
            if (!Double.isFinite(yaw) || !Double.isFinite(pitch)) throw new IllegalArgumentException("Nonfinite aim");
            yaw = Math.IEEEremainder(yaw, 360);
        }
    }

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Nonfinite position");
            }
        }

        static Point parse(String value) {
            String[] parts = value.split(",", -1);
            if (parts.length != 3) throw new IllegalArgumentException("Expected x,y,z");
            return new Point(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        }

        public Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        public Point minus(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        public Point times(double k) { return new Point(x * k, y * k, z * k); }
        public double dot(Point p) { return x * p.x + y * p.y + z * p.z; }
        public double lengthSquared() { return dot(this); }
        Point cross(Point p) { return new Point(y * p.z - z * p.y, z * p.x - x * p.z, x * p.y - y * p.x); }

        Point rotate(Point unitAxis, double degrees) {
            double radians = Math.toRadians(degrees);
            double cosine = Math.cos(radians);
            return times(cosine).add(unitAxis.cross(this).times(Math.sin(radians)))
                    .add(unitAxis.times(unitAxis.dot(this) * (1 - cosine)));
        }
    }
}
