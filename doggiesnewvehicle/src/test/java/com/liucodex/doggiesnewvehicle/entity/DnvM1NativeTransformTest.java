package com.liucodex.doggiesnewvehicle.entity;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.compat.ExactSbwTransformFixture;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DnvM1NativeTransformTest {
  @Test void exactSbwBytecodeAndShippedAnchorsMatchEnlargedRenderedMuzzleAndCamera() throws Exception {
    var data = JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles/m1_yunlong.json"))).getAsJsonObject();
    var fixture = new ExactSbwTransformFixture();
    fixture.vehicle.turretPos = vector(data.getAsJsonArray("TurretPos"));
    fixture.vehicle.barrelPos = vector(data.getAsJsonArray("BarrelPos"));
    var shoot = data.getAsJsonObject("Weapons").getAsJsonObject("Main").getAsJsonObject("ShootPos");
    Vec3 muzzle = vector(shoot.getAsJsonArray("Positions").get(0).getAsJsonArray());
    Vec3 camera = vector(data.getAsJsonObject("Seats").getAsJsonObject("CameraPos").getAsJsonArray("Position"));
    for (float bodyYaw : new float[] {0, 43, 90, 180, 270}) {
      fixture.vehicle.body.identity().rotateY(Math.toRadians(-bodyYaw));
      for (float turretYaw : new float[] {-145, 0, 60, 180}) {
        fixture.vehicle.turretYaw = turretYaw;
        for (float pitch : new float[] {5, 0, -20, -60}) {
          fixture.vehicle.turretPitch = pitch;
          near(DnvM1Pose.pitched(DnvM1Pose.MUZZLE, bodyYaw - turretYaw, -pitch), fixture.nativeBarrelPoint(muzzle));
          near(DnvM1Pose.pitched(DnvM1Pose.CAMERA, bodyYaw - turretYaw, -pitch), fixture.nativeBarrelPoint(camera));
        }
      }
    }
  }
  private static Vec3 vector(JsonArray array) { return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()); }
  // Vec3 uses Minecraft's sine lookup table; JOML uses trigonometric rotations. Stay below 1 mm at this scale.
  private static void near(Vec3 expected, Vec3 actual) { assertTrue(expected.distanceTo(actual) < .001, expected + " != " + actual); }
}
