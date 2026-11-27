package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DnvTargetLineOfSightTest {
    @Test
    void samplesCenterThenUpperBodyThenLowerBody() {
        List<Vec3> points = DnvTargetLineOfSight.orderedAimPoints(
                new AABB(2.0D, 10.0D, 4.0D, 4.0D, 12.0D, 6.0D),
                11.7D
        );

        assertEquals(3, points.size());
        assertEquals(new Vec3(3.0D, 11.0D, 5.0D), points.get(0));
        assertTrue(points.get(1).y > points.get(0).y);
        assertTrue(points.get(2).y < points.get(0).y);
    }

    @Test
    void clampsOutOfBoundsEyeHeightIntoTheUpperBody() {
        List<Vec3> points = DnvTargetLineOfSight.orderedAimPoints(
                new AABB(0.0D, 0.0D, 0.0D, 1.0D, 2.0D, 1.0D),
                100.0D
        );

        assertTrue(points.get(1).y < 2.0D);
        assertTrue(points.get(1).y > 1.0D);
    }
}
