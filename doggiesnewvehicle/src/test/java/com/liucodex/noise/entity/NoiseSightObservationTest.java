package com.liucodex.noise.entity;

import com.liucodex.doggiesnewvehicle.compat.DnvSightObservation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class NoiseSightObservationTest {
  @Test
  void movingOriginOrTargetAndChangedPoseCannotReuseAVisibleShot() {
    UUID id = new UUID(1L, 2L);
    AABB body = new AABB(4, 0, 4, 5, 2, 5);
    var visible = new DnvSightObservation(id, Vec3.ZERO, body, 1.8D,
        42L, 20, 26, true);

    assertTrue(visible.reusable(id, new Vec3(.1D, 0, 0), body.move(.1D, 0, 0),
        1.8D, 42L, 23));
    assertFalse(visible.reusable(id, new Vec3(.3D, 0, 0), body, 1.8D, 42L, 23));
    assertFalse(visible.reusable(id, Vec3.ZERO, body.move(.3D, 0, 0), 1.8D, 42L, 23));
    assertFalse(visible.reusable(id, Vec3.ZERO, new AABB(3.7D, 0, 4, 5.3D, 2, 5),
        1.8D, 42L, 23));
    assertFalse(visible.reusable(id, Vec3.ZERO, body, 2.1D, 42L, 23));
    assertFalse(visible.reusable(id, Vec3.ZERO, body, 1.8D, 43L, 23));
    assertFalse(visible.reusable(new UUID(1L, 3L), Vec3.ZERO, body, 1.8D, 42L, 23));
    assertFalse(visible.reusable(id, Vec3.ZERO, body, 1.8D, 42L, 19));
    assertFalse(visible.reusable(id, Vec3.ZERO, body, 1.8D, 42L, 26));
  }
}
