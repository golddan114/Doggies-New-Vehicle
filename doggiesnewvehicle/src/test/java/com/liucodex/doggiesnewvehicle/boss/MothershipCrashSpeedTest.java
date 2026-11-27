package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MothershipCrashSpeedTest {
    @Test void everyAltitudeFallsExactlyTwoBlocksPerSecondWithoutAcceleration() {
        for (double altitude : new double[]{80, 160, 320}) {
            var crash = new MothershipCrash(); crash.begin(); crash.plan(altitude, 64);
            for (int second = 1; second <= 5; second++) {
                for (int tick = 0; tick < 20; tick++) crash.advance();
                assertEquals(altitude - second * 2, crash.nextY(), 1e-8);
            }
        }
    }
    @Test void holdingAndRestoringNeverAccumulateUnobservedDescent() {
        var crash = new MothershipCrash(); crash.begin(); crash.plan(200, 64);
        for (int n = 0; n < 60; n++) crash.advance();
        double before = crash.nextY();
        for (int n = 0; n < 400; n++) { crash.advance(); crash.hold(); }
        assertEquals(before, crash.nextY());
        var loaded = new MothershipCrash(); loaded.restore(crash.state(), crash.age(), crash.landedAge());
        loaded.restorePlan(crash.startY(), crash.endY(), crash.planAge()); loaded.advance();
        assertEquals(before - .1, loaded.nextY(), 1e-8);
    }
}
