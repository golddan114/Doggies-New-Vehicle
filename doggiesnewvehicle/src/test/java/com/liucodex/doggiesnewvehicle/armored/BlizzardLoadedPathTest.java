package com.liucodex.doggiesnewvehicle.armored;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BlizzardLoadedPathTest {
    @Test void shortDiagonalCannotSkipAnUnloadedSliverAtAChunkCorner() {
        Vec3 start = new Vec3(15.99, 50, 15.98), end = new Vec3(16.11, 50, 16.10);
        assertFalse(BlizzardLoadedPath.chunks(start, end, (x,z) -> !(x == 1 && z == 0)));
        assertFalse(BlizzardLoadedPath.chunks(end, start, (x,z) -> !(x == 1 && z == 0)));
        assertTrue(BlizzardLoadedPath.chunks(start, end, (x,z) -> true));
    }
    @Test void cardinalNegativeAndVerticalSegmentsAreBoundedAndVisitEachNecessaryChunk() {
        var seen = new HashSet<String>();
        assertTrue(BlizzardLoadedPath.chunks(new Vec3(-.1,50,1), new Vec3(-48.1,50,1), (x,z)-> {seen.add(x+","+z); return true;}));
        assertEquals(Set.of("-1,0","-2,0","-3,0","-4,0"), seen);
        seen.clear();
        assertTrue(BlizzardLoadedPath.chunks(new Vec3(0,0,0), new Vec3(0,-14,0), (x,z)-> {seen.add(x+","+z);return true;}));
        assertEquals(Set.of("0,0"),seen);
        assertFalse(BlizzardLoadedPath.chunks(Vec3.ZERO,new Vec3(513,0,0),(x,z)->fail("oversized path cannot query chunks")));
        assertFalse(BlizzardLoadedPath.chunks(Vec3.ZERO,new Vec3(Double.NaN,0,0),(x,z)->fail("invalid path cannot query chunks")));
    }
}
