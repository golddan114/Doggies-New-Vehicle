package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.fx.DnvJetMesh;
import org.junit.jupiter.api.Test;

class BlizzardVisualMotionTest {
    @Test void stationaryPropulsionStopsWhileLiftRemainsAndInvalidVelocityCannotEmit() {
        assertEquals(0,BlizzardVisualMotion.thrust(0,0,false));
        assertEquals(0,BlizzardVisualMotion.thrust(0,1,false));
        assertEquals(.38,BlizzardVisualMotion.thrust(0,0,true),1e-8);
        assertTrue(BlizzardVisualMotion.thrust(.5,0,false)>0);
        assertEquals(1,BlizzardVisualMotion.thrust(10,10,false));
        assertEquals(0,BlizzardVisualMotion.thrust(Double.NaN,0,true));
    }
    @Test void idleSuspensionIsSlowSmallAndHasNoAnimationWhenUnpowered() {
        double previous = BlizzardVisualMotion.bob(0, 0, true);
        for (int tick = 1; tick <= 240; tick++) {
            double current = BlizzardVisualMotion.bob(tick, 0, true);
            assertTrue(Math.abs(current) <= .03000001);
            assertTrue(Math.abs(current - previous) < .0016);
            assertEquals(0, BlizzardVisualMotion.bob(tick, 0, false));
            assertTrue(Math.abs(BlizzardVisualMotion.bob(tick, .65, true)) <= Math.abs(current) + 1e-10);
            previous = current;
        }
        assertEquals(.03, BlizzardVisualMotion.bob(30, 0, true), 1e-10);
        assertEquals(-.03, BlizzardVisualMotion.bob(90, 0, true), 1e-10);
        assertEquals(0, BlizzardVisualMotion.bob(Double.NaN, 0, true));
    }
    @Test void sharedExecutorJetHasBoundedDetailAndStopsOnInvalidOrHiddenInput() {
        for (int detail : new int[] {1, 2}) {
            int[] count = {0}; double[] farthest = {0};
            DnvJetMesh.exhaust((x, y, z, r, g, b, a) -> {
                count[0]++; farthest[0] = Math.max(farthest[0], z);
                assertTrue(Double.isFinite(x + y + z));
                assertTrue(a >= 0 && a <= 1);
            }, 1, .16, 13, detail);
            assertEquals(detail == 2 ? 192 : 48, count[0]);
            assertTrue(farthest[0] > 1 && farthest[0] < 1.22);
        }
        DnvJetMesh.Sink reject = (x, y, z, r, g, b, a) -> fail("Invalid jet emitted vertices");
        DnvJetMesh.exhaust(reject, 0, .16, 0, 2);
        DnvJetMesh.exhaust(reject, 1, .16, 0, 0);
        DnvJetMesh.exhaust(reject, 1, Double.NaN, 0, 2);
        DnvJetMesh.exhaust(reject, 1, .16, Double.NaN, 2);
    }
}
