package com.liucodex.doggiesnewvehicle.armored;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.compat.ExactSbwTransformFixture;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.junit.jupiter.api.Test;

class Project612RigTest {
    private static final Project612Rig RIG = Project612Rig.INSTANCE;

    @Test void oneUniformRuntimeScaleMatchesApocalypseHeightAndNativeSeatCameraAndExit() throws Exception {
        var rig = JsonParser.parseString(Files.readString(Path.of(
                "src/artillery/resources/assets/doggiesnewvehicle/meshes/project612_rig.json"))).getAsJsonObject();
        double target = 4.378629207611084;
        assertEquals(target / 5.303124904632568, RIG.scale, 1e-12);
        assertEquals(target, rig.getAsJsonObject("bounds").getAsJsonArray("maximum").get(1).getAsDouble(), 1e-6);
        var data = JsonParser.parseString(Files.readString(Path.of(
                "src/artillery/resources/data/doggievehicle/sbw/vehicles/project612.json"))).getAsJsonObject();
        var seat = data.getAsJsonArray("Seats").get(0).getAsJsonObject();
        assertFalse(seat.get("RotateWithVehicle").getAsBoolean(), "Camera world heading must stay fixed while the hull catches up");
        assertEquals(3.4 * RIG.scale, seat.getAsJsonArray("Position").get(1).getAsDouble(), 1e-9);
        assertEquals(5.55 * RIG.scale, seat.getAsJsonObject("CameraPos").getAsJsonArray("Position").get(1).getAsDouble(), 1e-9);
        assertEquals(3.8 * RIG.scale, seat.getAsJsonObject("DismountInfo").getAsJsonArray("Position").get(0).getAsDouble(), 1e-9);
        assertEquals(80, data.get("Mass").getAsInt());
    }

    @Test void nativeBarrelAxesAndExportedMuzzleAgreeAtEveryApprovedElevationAndHeading() throws Exception {
        var f = new ExactSbwTransformFixture();
        f.vehicle.turretPos = RIG.pivot;
        f.vehicle.barrelPos = Vec3.ZERO;
        for (int heading : new int[] {-180, -90, 0, 90, 180}) {
            f.vehicle.body.identity().translate(21, 64, -83).rotateY(Math.toRadians(-heading));
            f.vehicle.turretYaw = 0;
            for (float elevation = -3; elevation <= 25; elevation += .25F) {
                f.vehicle.turretPitch = -elevation;
                var transform = new Matrix4d(f.vehicle.body).mul(RIG.local(-elevation));
                near(f.nativeBarrelPoint(RIG.muzzle.subtract(RIG.pivot)), Project612Rig.point(transform, RIG.muzzle));
                Vec3 direction = Project612Rig.direction(transform, RIG.direction);
                assertEquals(Math.sin(Math.toRadians(elevation)), direction.y, 1e-6);
                assertEquals(1, direction.length(), 1e-6);
            }
        }
        near(Project612Rig.point(RIG.local(-25), RIG.muzzle), Project612Rig.point(RIG.local(-1000), RIG.muzzle));
        near(Project612Rig.point(RIG.local(3), RIG.muzzle), Project612Rig.point(RIG.local(1000), RIG.muzzle));
    }
    @Test void signedTrackTravelClosesWithNoJumpAtAnyBoundaryAndKeepsSixteenWheels() {
        assertEquals(16, RIG.wheels.size());
        for (var track : RIG.tracks) {
            assertEquals(60, track.count());
            double straight = track.front() - track.rear(), arc = Math.PI * track.radius();
            for (double seam : new double[] {0, straight, straight + arc, 2 * straight + arc, track.length()}) {
                double phase = seam - track.length() / 2;
                assertTrue(Project612Rig.link(track, phase - 1e-5).position().distanceTo(Project612Rig.link(track, phase + 1e-5).position()) < 3e-5);
            }
            for (int index = -600; index < 600; index++) {
                double d = index * .137;
                near(Project612Rig.link(track, d).position(), Project612Rig.link(track, d + track.length()).position());
                assertTrue(Project612Rig.link(track, d).position().y > .08 * RIG.scale);
            }
        }
    }
    @Test void exportedStreamsRetainTheAccepted4188TrianglesAndOnlyFiveCachedParts() throws Exception {
        var path = Path.of("src/artillery/resources/assets/doggiesnewvehicle/meshes/project612.etm");
        var bytes = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x314d5445, bytes.getInt()); assertEquals(1, bytes.getInt()); assertEquals(5, bytes.getInt());
        var triangles = new HashMap<String, Integer>();
        while (bytes.hasRemaining()) {
            byte[] name = new byte[Short.toUnsignedInt(bytes.getShort())]; bytes.get(name);
            int count = bytes.getInt(), emissive = bytes.getInt();
            assertEquals(0, count % 3); assertEquals(0, emissive);
            triangles.put(new String(name, java.nio.charset.StandardCharsets.UTF_8), count / 3);
            for (int vertex = 0; vertex < count; vertex++) {
                for (int i = 0; i < 8; i++) assertTrue(Float.isFinite(bytes.getFloat()));
            }
        }
        assertEquals(4188, triangles.get("Body") + triangles.get("Main") + 8 * triangles.get("Wheel_L")
                + 8 * triangles.get("Wheel_R") + 120 * triangles.get("Track"));
    }
    @Test void collisionStaysOnChassisWhileTheGunAndHudUseTheSameRigidPivot() throws Exception {
        var data = JsonParser.parseString(Files.readString(Path.of(
                "src/artillery/resources/data/doggievehicle/sbw/vehicles/project612.json"))).getAsJsonObject();
        int collision = 0, gun = 0;
        for (var value : data.getAsJsonArray("OBB")) {
            var b = value.getAsJsonObject();
            if (b.get("Part").getAsString().equals("Collision")) { collision++; assertFalse(b.has("Transform")); }
            if (b.has("Transform") && b.get("Transform").getAsString().equals("Project612Main")) gun++;
        }
        assertEquals(1, collision); assertEquals(1, gun);
        assertEquals(16, data.getAsJsonArray("TerrainCompat").size());
        assertEquals(1, data.getAsJsonArray("Seats").size());
        assertEquals("Main", data.getAsJsonArray("Seats").get(0).getAsJsonObject().getAsJsonArray("Weapons").get(0).getAsString());
    }
    private static void near(Vec3 expected, Vec3 actual) { assertTrue(expected.distanceTo(actual) < 1e-5, expected + " != " + actual); }
}
