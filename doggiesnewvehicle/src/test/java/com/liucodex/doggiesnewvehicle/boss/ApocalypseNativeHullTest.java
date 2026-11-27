package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.armored.ApocalypseRig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** Reuses the exact native OBB math fixture; production artillery has no dependency on boss content. */
class ApocalypseNativeHullTest {
    @Test void majorTankRegionsRotateWithTheirOwnerAndDoNotTurnTheGapAboveTheChassisIntoArmor() throws Exception {
        var rig = ApocalypseRig.INSTANCE;
        for (double body : new double[] {0, 90, 180, 270}) {
            Matrix4d bodyMatrix = new Matrix4d().translation(271, 93, -185).rotateY(Math.toRadians(-body));
            for (double turret : new double[] {-180, -90, 0, 90, 180}) {
                List<NativeHullFixture.Box> boxes = new ArrayList<>();
                for (var box : rig.boxes) {
                    Matrix4d matrix = new Matrix4d(bodyMatrix).mul(rig.local(box.transform(), turret, 25, 10, 0));
                    Vec3 center = ApocalypseRig.transformPoint(matrix, box.center());
                    var nativeBox = NativeHullFixture.type("OBB").getConstructor(
                            Vector3d.class, Vector3d.class, Quaterniond.class, NativeHullFixture.Part.class)
                            .newInstance(vector(center), vector(box.half()), matrix.getNormalizedRotation(new Quaterniond()), NativeHullFixture.Part.BODY);
                    var actual = new NativeHullFixture.Box(nativeBox);
                    assertTrue(actual.contains(center));
                    Vec3 before = ApocalypseRig.transformPoint(matrix, box.center().add(-box.half().x - 1, 0, 0));
                    Vec3 after = ApocalypseRig.transformPoint(matrix, box.center().add(box.half().x + 1, 0, 0));
                    assertTrue(actual.clip(before, after));
                    assertFalse(actual.contains(ApocalypseRig.transformPoint(matrix, box.center().add(box.half().x + .2, 0, 0))));
                    boxes.add(actual);
                }
                Vec3 gap = ApocalypseRig.transformPoint(new Matrix4d(bodyMatrix).mul(rig.local("Turret", turret, 0, 0, 0)),
                        new Vec3(0, 4.1, 1.5));
                var broad = boxes.get(0).bounds();
                for (var box : boxes) broad = broad.minmax(box.bounds());
                assertTrue(broad.contains(gap), "fixture must exercise an empty region inside the broad-phase bound");
                for (var box : boxes) assertFalse(box.contains(gap));
                assertEquals(13, boxes.size());
                Matrix4d launcher = new Matrix4d(bodyMatrix).mul(rig.local("Turret", turret, 0, 0, 0));
                int launchChecks = 0;
                for (var entry : rig.muzzles.entrySet()) {
                    if (!entry.getKey().startsWith("muzzle_missile_")) continue;
                    launchChecks++;
                    var muzzle = entry.getValue();
                    // Native velocity=3: the fourth tick moves before starting guidance.
                    Vec3 firstTurn = ApocalypseRig.transformPoint(launcher,
                            muzzle.position().add(muzzle.direction().scale(3 + 2.4 + 1.92 + 1.536)));
                    for (var box : boxes) assertFalse(box.inflate(5.5).contains(firstTurn),
                            "native visual radius plus 1-block motion margin must clear " + entry.getKey());
                }
                assertEquals(4, launchChecks);
            }
        }
    }
    private static Vector3d vector(Vec3 value) { return new Vector3d(value.x, value.y, value.z); }
}
