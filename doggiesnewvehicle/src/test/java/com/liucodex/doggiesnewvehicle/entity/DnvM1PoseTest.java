package com.liucodex.doggiesnewvehicle.entity;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class DnvM1PoseTest {
  @Test void muzzleCameraAndDirectionMatchRendererAtAllSelectedElevationsAndYaws() {
    for (float elevation : new float[] {-5, 0, 20, 40, 60}) {
      for (float yaw : new float[] {0, 45, 90, 180, 270, 315}) {
        var pivot = DnvM1Pose.PITCH;
        var matrix = new Matrix4f().rotateY((float) Math.toRadians(-yaw))
            .translate((float) pivot.x, (float) pivot.y, (float) pivot.z)
            .rotateX((float) Math.toRadians(-elevation))
            .translate((float) -pivot.x, (float) -pivot.y, (float) -pivot.z);
        for (Vec3 point : new Vec3[] {DnvM1Pose.MUZZLE, DnvM1Pose.CAMERA}) {
          Vector3f rendered = matrix.transformPosition(new Vector3f((float) point.x, (float) point.y, (float) point.z));
          assertNear(new Vec3(rendered.x, rendered.y, rendered.z), DnvM1Pose.pitched(point, yaw, elevation));
        }
        var renderedDirection = matrix.transformDirection(new Vector3f(0, 0, 1)).normalize();
        assertNear(new Vec3(renderedDirection.x, renderedDirection.y, renderedDirection.z),
            DnvM1Pose.direction(yaw, elevation));
      }
    }
  }

  @Test void sixtyDegreesPointsUpAndClearanceCoversTheAuthoredOverhangs() {
    assertEquals(Math.sin(Math.toRadians(60)), DnvM1Pose.direction(0, 60).y, 0.0001);
    assertTrue(DnvM1Pose.pitched(DnvM1Pose.MUZZLE, 0, 60).y > 5.2);
    var clearance = DnvM1Pose.deploymentClearance(0);
    assertTrue(clearance.stream().anyMatch(box -> box.contains(new Vec3(0, 0.1, -3.98))), "new author rear fixed foot");
    assertTrue(clearance.stream().anyMatch(box -> box.contains(DnvM1Pose.MUZZLE)), "new barrel and spawn margin outside central footprint");
    var sideways = DnvM1Pose.deploymentClearance(90);
    assertTrue(sideways.stream().anyMatch(box -> box.contains(DnvM1Pose.pitched(DnvM1Pose.MUZZLE,90,0))), "yaw-rotated new barrel");
  }

  @Test void newAuthorUsesItsActualSevenByElevenAndThreeHighClearanceAtWorldCoordinates() {
    for (int x : new int[] {0, -1, -120, 100, 29999990, -29999990}) {
      var bounds = DnvM1Pose.deploymentClearance(0).get(0).move(x + .5, 64, x + .5);
      BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
      BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
      assertEquals(7, max.getX() - min.getX() + 1);
      assertEquals(11, max.getZ() - min.getZ() + 1);
      assertEquals(3, max.getY() - min.getY() + 1);
    }
  }

  private static void assertNear(Vec3 expected, Vec3 actual) {
    assertEquals(expected.x, actual.x, 0.0005);
    assertEquals(expected.y, actual.y, 0.0005);
    assertEquals(expected.z, actual.z, 0.0005);
  }
}
