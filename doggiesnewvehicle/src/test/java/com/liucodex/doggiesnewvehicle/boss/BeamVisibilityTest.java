package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BeamVisibilityTest {
    @Test void observersAlongTheFarEndSeeTheWholeRayEvenWithItsMuzzleOutsideTheViewDistance() {
        Vec3 start=new Vec3(0,90,0),end=new Vec3(0,90,512),camera=new Vec3(12,90,500);
        assertTrue(camera.distanceToSqr(start)>256*256);
        assertTrue(BeamVisibility.nearby(camera,start,end));
        assertEquals(144,BeamVisibility.distanceSquared(camera,start,end),1e-9);
        assertTrue(BeamVisibility.bounds(start,end).contains(new Vec3(0,90,400)));
        assertFalse(BeamVisibility.nearby(new Vec3(257,90,300),start,end));
    }
    @Test void sideLaserStaysVisibleAtNormalCombatDistanceAndRayBoundsFollowPitchAndYaw() {
        Vec3 start=new Vec3(30,80,-20),end=start.add(80,-45,50);
        assertTrue(BeamVisibility.nearby(start.add(100,0,0),start,end));
        assertTrue(BeamVisibility.bounds(start,end).contains(start.lerp(end,.95)));
        assertEquals(25,BeamVisibility.distanceSquared(start.add(0,5,0),start,start),1e-9);
        assertFalse(BeamVisibility.nearby(start,start,new Vec3(Double.NaN,0,0)));
    }
}
