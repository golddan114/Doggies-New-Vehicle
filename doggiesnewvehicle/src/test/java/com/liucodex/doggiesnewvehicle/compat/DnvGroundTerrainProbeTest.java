package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class DnvGroundTerrainProbeTest {
    private static final AABB HULL = new AABB(-3, 0, -3, 3, 5.6, 3);

    private static final class Space implements DnvGroundTerrainProbe.Space {
        boolean loaded = true;
        boolean border = true;
        Predicate<AABB> collision = b -> false;
        Predicate<double[]> support = p -> true;
        final List<AABB> reads = new ArrayList<>();
        int supportReads;
        public boolean loaded(AABB bounds) { return loaded; }
        public boolean withinBorder(AABB bounds) { return border; }
        public boolean clear(AABB bounds) { reads.add(bounds); return !collision.test(bounds); }
        public boolean supported(double x, double y, double z) { supportReads++; return support.test(new double[]{x,y,z}); }
    }

    @Test
    void flatTerrainChecksTheWholeSixBlockHullAtThreePositions() {
        var space = new Space();
        assertTrue(new DnvGroundTerrainProbe(HULL, 0.6, space).clear(0, 3));
        assertEquals(3, space.reads.size());
        assertEquals(27, space.supportReads);
        assertEquals(6.1, space.reads.get(0).getXsize(), 1e-6);
        assertEquals(6.05, space.reads.get(2).maxZ, 1e-6);
    }

    @Test
    void unloadedTerrainAndWorldBorderFailBeforeCollisionOrBlockQueries() {
        var space = new Space();
        space.loaded = false;
        assertFalse(new DnvGroundTerrainProbe(HULL, 0.6, space).clear(0, 3));
        space.loaded = true; space.border = false;
        assertFalse(new DnvGroundTerrainProbe(HULL, 0.6, space).clear(0, 3));
        assertTrue(space.reads.isEmpty());
        assertEquals(0, space.supportReads);
    }

    @Test
    void passableStepIsAllowedButTallWallIsRejected() {
        var space = new Space();
        space.collision = b -> b.maxZ > 4 && b.minY < 0.5;
        assertTrue(new DnvGroundTerrainProbe(HULL, 0.6, space).clear(0, 3));
        assertTrue(space.reads.stream().anyMatch(b -> b.minY == 0.6));
        space.collision = b -> b.maxZ > 4 && b.minY < 2;
        assertFalse(new DnvGroundTerrainProbe(HULL, 0.6, space).clear(0, 3));
    }

    @Test
    void unsupportedOuterTrackRejectsLedgeEvenWhenHullCenterIsSupported() {
        var space = new Space();
        space.support = p -> !(p[0] > 2 && p[2] > 4);
        assertFalse(new DnvGroundTerrainProbe(HULL, 0.6, space).clear(0, 3));
    }

    @Test
    void rearObstacleOnlyBlocksReverseCorridor() {
        var space = new Space();
        space.collision = b -> b.minZ < -4;
        var probe = new DnvGroundTerrainProbe(HULL, 0.6, space);
        assertTrue(probe.clear(0, 3));
        assertFalse(probe.clear(0, -2));
    }
}
