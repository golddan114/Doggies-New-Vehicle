package com.liucodex.doggiesnewvehicle.armored;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BlizzardRulesTest {
    @Test void nativeColdBeamMetadataMatchesTheStandardModePulsePrice()throws Exception{
        try(var stream=getClass().getResourceAsStream("/data/doggievehicle/sbw/vehicles/blizzard.json")){
            assertNotNull(stream);
            var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(BlizzardRules.pulseCost(false),json.getAsJsonObject("Weapons").getAsJsonObject("ColdBeam").get("AmmoCostPerShoot").getAsInt());
        }
    }
    @Test void eightyBlockShotCrossesTheOldBoundaryButKeepsTheLoadedChunkGate() {
        assertEquals(256,BlizzardRules.RANGE);
        assertEquals(80,BlizzardRules.MODE2_RANGE);
        var from=new net.minecraft.world.phys.Vec3(0,64,0);
        var to=from.add(BlizzardRules.MODE2_RANGE,0,0);
        var seen=new java.util.HashSet<Integer>();
        assertTrue(BlizzardLoadedPath.chunks(from,to,(x,z)->{seen.add(x);return true;}));
        assertTrue(seen.contains(5));
        assertFalse(BlizzardLoadedPath.chunks(from,to,(x,z)->x<5));
    }
    @Test void motionChargesHoverAndNormalizedThrottleWithinBudget() {
        assertEquals(16, BlizzardRules.motionCost(40, false));
        assertEquals(144, BlizzardRules.motionCost(40, true));
        for (int ticks = 1; ticks <= 40; ticks++) {
            int cost = BlizzardRules.motionCost(ticks, true);
            assertTrue(cost >= 16 && cost <= 144);
            assertTrue(cost >= BlizzardRules.motionCost(ticks - 1, true));
        }
        assertEquals(100_000, BlizzardRules.JUMP_FE);
    }
    @Test void hoverConvergesToHalfBlockFromEitherSideAndFallsWithoutSupportOrPower() {
        for (double initial : new double[] {0, .2, 1.5, 8}) {
            double y = initial, velocity = 0;
            for (int tick = 0; tick < 240; tick++) {
                velocity = BlizzardRules.vertical(velocity, y, 0, true); y += velocity;
                assertTrue(Double.isFinite(y)); assertTrue(Math.abs(velocity) <= .3);
            }
            assertEquals(.5, y, 1e-8);
        }
        assertTrue(BlizzardRules.vertical(0, 8, Double.NaN, true) < 0);
        assertTrue(BlizzardRules.vertical(0, 1, 0, false) < 0);
        assertEquals(-.8, BlizzardRules.vertical(-.8, 200, Double.NaN, true));
    }
    @Test void forwardsReverseAndReleaseHaveBoundedAccelerationAndSeparateCaps() {
        double velocity = 0;
        for (int i = 1; i <= 120; i++) velocity = BlizzardRules.horizontal(velocity, 1, true, i, true);
        assertEquals(10.142857142857, velocity * 20, 1e-9);
        for (int i = 1; i <= 120; i++) velocity = BlizzardRules.horizontal(velocity, -1, true, i, true);
        assertEquals(-8.142857142857, velocity * 20, 1e-9);
        for (int i = 0; i < 40; i++) velocity = BlizzardRules.horizontal(velocity, 0, true, 0, true);
        assertEquals(0, velocity);
        assertEquals(0, BlizzardRules.horizontal(0, 1, false, 40, true));
    }
    @Test void startIsExponentialRatherThanInstantOrLinearAndGroundGripIsStrongerThanAirDrag() {
        double early = BlizzardRules.throttle(10), middle = BlizzardRules.throttle(20), later = BlizzardRules.throttle(30);
        assertTrue(early < .1 && middle < .2 && later < .5);
        assertTrue(later - middle > middle - early);
        assertEquals(1, BlizzardRules.throttle(40));
        double ground = BlizzardRules.FORWARD_SPEED, air = ground, distance = 0;
        for (int tick = 0; tick < 10; tick++) {
            ground = BlizzardRules.horizontal(ground, 0, true, 0, true); distance += ground;
            air = BlizzardRules.horizontal(air, 0, true, 0, false);
        }
        assertEquals(0, ground); assertTrue(distance < .85); assertTrue(air > .1);
        assertTrue(BlizzardRules.lateralRetention(true, true) < .5);
    }
}
