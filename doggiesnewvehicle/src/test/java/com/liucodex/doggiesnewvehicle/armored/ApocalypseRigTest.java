package com.liucodex.doggiesnewvehicle.armored;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.compat.ExactSbwTransformFixture;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.junit.jupiter.api.Test;

class ApocalypseRigTest {
    private static final ApocalypseRig RIG = ApocalypseRig.INSTANCE;

    @Test void authoredMainMuzzlesMatchExactNativeSbwAxesAtCardinalAndReverseHeadings() throws Exception {
        var fixture = new ExactSbwTransformFixture();
        fixture.vehicle.turretPos = RIG.yaw;
        fixture.vehicle.barrelPos = RIG.main.subtract(RIG.yaw);
        for (int body : new int[] {0, 90, 180, 270}) {
            fixture.vehicle.body.identity().translate(67, 24, -130).rotateY(Math.toRadians(-body));
            for (int turret : new int[] {-180, -90, 0, 90, 180}) {
                fixture.vehicle.turretYaw = turret;
                for (int elevation : new int[] {-5, 0, 25}) {
                    fixture.vehicle.turretPitch = -elevation;
                    var transform = new Matrix4d(fixture.vehicle.body).mul(RIG.local("Main", turret, elevation, 0, 0));
                    for (String name : new String[] {"muzzle_main_l", "muzzle_main_r"}) {
                        Vec3 point = RIG.muzzles.get(name).position();
                        near(fixture.nativeBarrelPoint(point.subtract(RIG.main)), ApocalypseRig.transformPoint(transform, point));
                    }
                }
            }
        }
    }

    @Test void positiveElevationRaisesBoresightAndEachBarrelRecoilStaysOnItsAxis() {
        for (int yaw : new int[] {-180, -90, 0, 90, 180}) {
            for (String name : new String[] {"muzzle_main_l", "muzzle_main_r"}) {
                Vec3 point = RIG.muzzles.get(name).position();
                var neutral = RIG.local("MainL", yaw, 25, 0, 0);
                Vec3 direction = ApocalypseRig.transformDirection(neutral, new Vec3(0, 0, 1));
                assertTrue(direction.y > .42);
                var recoiled = RIG.local("MainL", yaw, 25, 0, 1);
                Vec3 delta = ApocalypseRig.transformPoint(recoiled, point).subtract(ApocalypseRig.transformPoint(neutral, point));
                near(direction.scale(-.18), delta);
            }
        }
    }

    @Test void missileSlotsStayFixedAsBothMovableWeaponsElevate() {
        for (var entry : RIG.muzzles.entrySet()) {
            if (!entry.getKey().startsWith("muzzle_missile")) continue;
            var muzzle = entry.getValue();
            var first = RIG.local("Turret", 135, -5, -3, 0);
            var second = RIG.local("Turret", 135, 25, 10, 1);
            near(ApocalypseRig.transformPoint(first, muzzle.position()), ApocalypseRig.transformPoint(second, muzzle.position()));
            assertTrue(ApocalypseRig.transformDirection(second, muzzle.direction()).y > .6);
        }
    }

    @Test void trackLoopClosesAndDoesNotTeleportAtAnySegmentBoundary() {
        for (var track : RIG.tracks) {
            near(ApocalypseRig.link(track, 0).position(), ApocalypseRig.link(track, track.length()).position());
            double straight = track.front() - track.rear(), arc = Math.PI * track.radius();
            for (double seam : new double[] {0, straight, straight + arc, 2 * straight + arc, track.length()}) {
                Vec3 before = ApocalypseRig.link(track, seam - 1E-5).position();
                Vec3 after = ApocalypseRig.link(track, seam + 1E-5).position();
                assertTrue(before.distanceTo(after) < 3E-5);
            }
            assertEquals(40, track.count());
        }
        assertEquals(16, RIG.wheels.size());
    }

    @Test void phaseInterpolationCrossesTheWrapByTheShortPathInBothDirections() {
        assertEquals(1, ApocalypseMotion.interpolateCycle(.99F, .01F, .5F, 1), 1E-6);
        assertEquals(0, ApocalypseMotion.interpolateCycle(.01F, .99F, .5F, 1), 1E-6);
        assertEquals(360, ApocalypseMotion.interpolateCycle(359, 1, .5F, 360), 1E-6);
        assertEquals(.3F, ApocalypseMotion.interpolateCycle(.3F, .3F, .9F, 1), 1E-6);
    }

    @Test void rearClearanceRejectsUnsafeRotationAndClampsCorruptSavedAngles() {
        assertTrue(ApocalypseMotion.cleared(0, -5, -3));
        assertFalse(ApocalypseMotion.cleared(180, -5, -3));
        assertTrue(ApocalypseMotion.cleared(180, 0, 2));
        assertFalse(ApocalypseMotion.cleared(Float.NaN, 0, 2));
        for (int yaw = -1080; yaw <= 1080; yaw++) {
            float main = ApocalypseMotion.elevation(-1000, yaw, false);
            float rocket = ApocalypseMotion.elevation(-1000, yaw, true);
            assertTrue(ApocalypseMotion.cleared(yaw, main, rocket));
            assertEquals(main, ApocalypseMotion.elevation(main, yaw + 360, false), 1E-6);
        }
        assertEquals(25, ApocalypseMotion.elevation(1000, 0, false));
        assertEquals(10, ApocalypseMotion.elevation(1000, 0, true));
        assertEquals(2, ApocalypseMotion.elevation(Float.NaN, 0, true));
    }

    @Test void physicsBoxIsIndependentOfEveryTurretPoseAndExcludedFromDamageDefinitions() throws Exception {
        var data = JsonParser.parseString(Files.readString(Path.of(
                "src/artillery/resources/data/doggievehicle/sbw/vehicles/apocalypse.json"))).getAsJsonObject();
        var physics = data.getAsJsonArray("OBB").get(0).getAsJsonObject();
        assertEquals("Collision", physics.get("Part").getAsString());
        assertFalse(physics.has("Transform")); // Native default Vehicle transform, never a turret transform.
        assertEquals(13, RIG.boxes.size());
        assertTrue(RIG.boxes.stream().noneMatch(box -> box.part().equals("Collision")));
        Vec3 anchor = new Vec3(0, 1.06980395, .32962608);
        for (int yaw = -180; yaw <= 180; yaw += 5) {
            near(anchor, ApocalypseRig.transformPoint(RIG.local("Body", yaw, 25, 10, 1), anchor));
        }
    }

    private static void near(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) < .001, expected + " != " + actual);
    }
}
