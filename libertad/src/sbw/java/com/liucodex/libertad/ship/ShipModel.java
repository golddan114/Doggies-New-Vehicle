package com.liucodex.libertad.ship;

import com.liucodex.libertad.naval.MountGeometry;
import com.liucodex.libertad.naval.MountGeometry.Angles;
import com.liucodex.libertad.naval.MountGeometry.Point;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Neutral visual and fire-control geometry using the same mount transforms. */
public final class ShipModel {
    public static final int FIRST_PROPELLER = 49;
    public static final int PARTS = 53;
    public static final int VISUAL_PARTS = PARTS + 24;
    private static final int MAX_TRIANGLES = 27438;
    private static final ShipModel INSTANCE = read();
    public final List<MountGeometry> mounts;
    public final List<Batch> batches;
    public final List<AaMount> aaMounts;
    private final List<Shaft> shafts;
    private final List<HullPart> parts;

    private ShipModel(List<MountGeometry> mounts, List<Batch> batches, List<Shaft> shafts, List<HullPart> parts,
                      List<AaMount> aaMounts) {
        this.mounts = mounts;
        this.batches = batches;
        this.shafts = shafts;
        this.parts = parts;
        this.aaMounts = aaMounts;
    }

    public static ShipModel get() { return INSTANCE; }

    private static ShipModel read() {
        try (var in = openModelResource("ship.bin")) {
            if (in.readInt() != 0x4C425432) throw new IOException("Wrong Libertad mesh signature");
            var textures = new ArrayList<ResourceLocation>();
            int textureCount = in.readUnsignedShort();
            if (textureCount < 1 || textureCount > 64) throw new IOException("Invalid texture count");
            for (int i = 0; i < textureCount; i++) textures.add(new ResourceLocation("libertad", in.readUTF()));
            List<Shaft> shafts = new ArrayList<>();
            for (int i = FIRST_PROPELLER; i < PARTS; i++) {
                var pivot = new Point(in.readDouble(), in.readDouble(), in.readDouble());
                var axis = new Point(in.readDouble(), in.readDouble(), in.readDouble());
                if (Math.abs(axis.lengthSquared() - 1) > 1e-5) throw new IOException("Invalid propeller axis");
                shafts.add(new Shaft(pivot, axis.times(1 / Math.sqrt(axis.lengthSquared()))));
            }
            int expected = in.readInt();
            if (expected < 1 || expected > MAX_TRIANGLES) throw new IOException("Invalid Libertad triangle count");
            int count = in.readInt();
            if (count < 1 || count > 256) throw new IOException("Invalid batch count");
            var batches = new ArrayList<Batch>();
            int triangles = 0;
            for (int i = 0; i < count; i++) {
                int part = in.readUnsignedShort(), texture = in.readUnsignedShort(), vertices = in.readInt();
                if (part >= VISUAL_PARTS || texture >= textureCount || vertices <= 0 || vertices % 3 != 0
                        || (triangles += vertices / 3) > expected) throw new IOException("Invalid mesh batch");
                float[] data = new float[vertices * 8];
                for (int j = 0; j < data.length; j++) {
                    data[j] = in.readFloat();
                    if (!Float.isFinite(data[j])) throw new IOException("Nonfinite mesh vertex");
                }
                batches.add(new Batch(part, textures.get(texture), data));
            }
            if (triangles != expected || in.read() != -1) throw new IOException("Incomplete Libertad mesh");
            return new ShipModel(MountGeometry.load(), List.copyOf(batches), List.copyOf(shafts), readObstructions(), readAaMounts());
        } catch (IOException e) {
            throw new IllegalStateException("Invalid Libertad runtime model", e);
        }
    }

    private static DataInputStream openModelResource(String name) throws IOException {
        var stream = ShipModel.class.getResourceAsStream("/assets/libertad/" + name);
        if (stream == null) throw new IOException("Missing Libertad model resource: " + name);
        return new DataInputStream(new BufferedInputStream(stream));
    }

    private static List<AaMount> readAaMounts() throws IOException {
        try (var in = openModelResource("air-defense.bin")) {
            if (in.readInt() != 0x4c424132) throw new IOException("Invalid AA mount signature");
            var mounts = new ArrayList<AaMount>();
            for (int index = 0; index < 12; index++) {
                int zone = in.readUnsignedByte();
                Vec3 pivot = readPoint(in), pitchPivot = readPoint(in), muzzle = readPoint(in), forward = readPoint(in);
                if (zone != index / 3 || Math.abs(forward.lengthSqr() - 1) > 1e-5
                        || Math.abs(forward.y) > 1e-5 || pivot.distanceToSqr(muzzle) > 4)
                    throw new IOException("Invalid AA mount geometry at index " + index);
                mounts.add(new AaMount(zone, pivot, pitchPivot, muzzle, Math.atan2(forward.x, forward.z)));
            }
            if (in.read() != -1) throw new IOException("Trailing AA mount data");
            return List.copyOf(mounts);
        }
    }

    public record AaMount(int zone, Vec3 pivot, Vec3 pitchPivot, Vec3 muzzle, double restYaw) {}

    public Matrix4d aaTransform(AaMount mount, Angles angles, boolean barrel) {
        Vec3 pivot = mount.pivot();
        var result = new Matrix4d().translate(pivot.x, pivot.y, pivot.z)
                .rotateY(Math.toRadians(angles.yaw()) - mount.restYaw()).translate(-pivot.x, -pivot.y, -pivot.z);
        if (barrel) {
            Vec3 hinge = mount.pitchPivot();
            result.translate(hinge.x, hinge.y, hinge.z)
                    .rotate(Math.toRadians(angles.pitch()), -Math.cos(mount.restYaw()), 0, Math.sin(mount.restYaw()))
                    .translate(-hinge.x, -hinge.y, -hinge.z);
        }
        return result;
    }

    private static List<HullPart> readObstructions() throws IOException {
        try (var in = openModelResource("collision.bin")) {
            if (in.readInt() != 0x4C424331) throw new IOException("Wrong Libertad obstruction signature");
            int remaining = in.readInt();
            if (remaining < FIRST_PROPELLER || remaining > MAX_TRIANGLES || in.readUnsignedShort() != FIRST_PROPELLER) {
                throw new IOException("Invalid Libertad obstruction header");
            }
            List<HullPart> hull = new ArrayList<>(FIRST_PROPELLER);
            for (int part = 0; part < FIRST_PROPELLER; part++) {
                int id = in.readUnsignedShort(), count = in.readInt();
                if (id != part || count < 1 || count > remaining) throw new IOException("Invalid obstruction part " + id);
                remaining -= count;
                List<Triangle> faces = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    Vec3 a = readPoint(in), b = readPoint(in), c = readPoint(in);
                    if (b.subtract(a).cross(c.subtract(a)).lengthSqr() == 0) {
                        throw new IOException("Degenerate obstruction triangle in part " + part);
                    }
                    faces.add(new Triangle(a, b, c));
                }
                hull.add(new HullPart(part, Tree.build(faces)));
            }
            if (remaining != 0 || in.read() != -1) throw new IOException("Incomplete Libertad obstruction mesh");
            return List.copyOf(hull);
        }
    }

    private static Vec3 readPoint(DataInputStream in) throws IOException {
        float x = in.readFloat(), y = in.readFloat(), z = in.readFloat();
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IOException("Nonfinite Libertad obstruction vertex");
        }
        return new Vec3(x, y, z);
    }

    public Matrix4d propellerTransform(int part, double degrees) {
        Shaft shaft = shafts.get(part - FIRST_PROPELLER);
        Point pivot = shaft.pivot(), axis = shaft.axis();
        // Port and starboard use the respective handedness of their authored blades.
        double turn = Math.toRadians(pivot.x() < 0 ? degrees : -degrees);
        return new Matrix4d().translate(pivot.x(), pivot.y(), pivot.z())
                .rotate(turn, axis.x(), axis.y(), axis.z()).translate(-pivot.x(), -pivot.y(), -pivot.z());
    }

    public Matrix4d transform(int part, Angles angles) {
        if (part == 0) return new Matrix4d();
        MountGeometry mount = mounts.get((part - 1) / 2);
        Point yaw = mount.yawCenter();
        var matrix = new Matrix4d().translate(yaw.x(), yaw.y(), yaw.z())
                .rotateY(Math.toRadians(angles.yaw())).translate(-yaw.x(), -yaw.y(), -yaw.z());
        if ((part & 1) == 0) {
            Point pitch = mount.pitchCenter(), bore = mount.forward();
            matrix.translate(pitch.x(), pitch.y(), pitch.z())
                    .rotate(Math.toRadians(angles.pitch()), -bore.z(), 0, bore.x())
                    .translate(-pitch.x(), -pitch.y(), -pitch.z());
        }
        return matrix;
    }

    /** Inverses are refreshed once per fire-control tick, independently of the number of candidate rays. */
    public Matrix4d[] inverses(Angles[] poses) {
        Matrix4d[] inverse = new Matrix4d[FIRST_PROPELLER];
        for (int i = 1; i < inverse.length; i++) inverse[i] = transform(i, poses[(i - 1) / 2]).invert();
        return inverse;
    }

    public boolean obstructed(Vec3 from, Vec3 to, int ownMount, Matrix4d[] inverse) {
        for (HullPart part : parts) {
            if (part.id() == 0) {
                if (part.tree().hit(from, to)) return true;
            } else if ((part.id() - 1) / 2 != ownMount
                    && part.tree().hit(point(inverse[part.id()], from), point(inverse[part.id()], to))) return true;
        }
        return false;
    }

    static Vec3 point(Matrix4d transform, Vec3 p) {
        var v = transform.transformPosition(new Vector3d(p.x, p.y, p.z));
        return new Vec3(v.x, v.y, v.z);
    }

    static Vec3 vector(Matrix4d transform, Vec3 p) {
        var v = transform.transformDirection(new Vector3d(p.x, p.y, p.z));
        return new Vec3(v.x, v.y, v.z);
    }

    static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }

    public record Batch(int part, ResourceLocation texture, float[] vertices) {}
    private record Shaft(Point pivot, Point axis) {}
    private record HullPart(int id, Tree tree) {}

    private record Triangle(Vec3 a, Vec3 b, Vec3 c) {
        AABB box() { return new AABB(a, b).minmax(new AABB(c, c)); }
        double centroid(int axis) {
            return switch (axis) { case 0 -> a.x + b.x + c.x; case 1 -> a.y + b.y + c.y; default -> a.z + b.z + c.z; };
        }

        boolean hit(Vec3 from, Vec3 delta) {
            Vec3 e1 = b.subtract(a), e2 = c.subtract(a);
            Vec3 p = delta.cross(e2);
            double det = e1.dot(p);
            if (Math.abs(det) < 1e-10) return false;
            Vec3 t = from.subtract(a);
            double u = t.dot(p) / det;
            if (u < 0 || u > 1) return false;
            Vec3 q = t.cross(e1);
            double v = delta.dot(q) / det;
            double ray = e2.dot(q) / det;
            return v >= 0 && u + v <= 1 && ray > .00001 && ray <= 1;
        }
    }

    private record Tree(AABB bounds, Tree left, Tree right, List<Triangle> leaf) {
        static Tree build(List<Triangle> faces) {
            AABB bounds = faces.get(0).box();
            for (Triangle face : faces) bounds = bounds.minmax(face.box());
            if (faces.size() <= 12) return new Tree(bounds.inflate(.00001), null, null, List.copyOf(faces));
            int axis = bounds.getXsize() >= bounds.getZsize() ? 0 : 2;
            if (bounds.getYsize() > Math.max(bounds.getXsize(), bounds.getZsize())) axis = 1;
            final int direction = axis;
            faces.sort(Comparator.comparingDouble(face -> face.centroid(direction)));
            int mid = faces.size() / 2;
            return new Tree(bounds.inflate(.00001), build(faces.subList(0, mid)), build(faces.subList(mid, faces.size())), List.of());
        }

        boolean hit(Vec3 from, Vec3 to) {
            if (!bounds.contains(from) && !bounds.contains(to) && bounds.clip(from, to).isEmpty()) return false;
            if (left != null) return left.hit(from, to) || right.hit(from, to);
            Vec3 delta = to.subtract(from);
            for (Triangle face : leaf) if (face.hit(from, delta)) return true;
            return false;
        }
    }
}
