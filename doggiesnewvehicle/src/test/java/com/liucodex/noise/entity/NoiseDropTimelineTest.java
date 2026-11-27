package com.liucodex.noise.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoiseDropTimelineTest {
    private static final double EPSILON = 1.0E-6D;

    @Test
    void mothershipCarrierAndVehicleRemainBelowTheAuthoredShipBottom() throws Exception {
        try(var input=getClass().getResourceAsStream("/assets/noise/meshes/noise_drop_rig.shm")) {
            var parts=com.liucodex.doggiesnewvehicle.boss.mesh.DnvMeshCodec.parse(input.readAllBytes());
            double actualTop=parts.values().stream().flatMapToDouble(p->java.util.stream.IntStream.range(0,p.stream().length/8)
                    .mapToDouble(i->p.stream()[i*8+1])).max().orElseThrow();
            assertEquals(actualTop,NoiseDropTimeline.RIG_TOP,EPSILON);
            for(var part:parts.values())for(int i=0;i<part.stream().length;i+=8) {
                assertTrue(Math.abs(part.stream()[i])<=NoiseDropTimeline.RIG_HALF_WIDTH+EPSILON);
                assertTrue(Math.abs(part.stream()[i+2])<=NoiseDropTimeline.RIG_HALF_LENGTH+EPSILON);
            }
        }
        for(double bottom:new double[]{14,20,30,50,80}) {
            float start=NoiseDropTimeline.mothershipStartOffset(bottom);
            assertTrue(start>=5.5&&start<=48);
            for(double t=0;t<=139;t+=.25) {
                double rig=NoiseDropTimeline.rigVerticalOffset(t,start);
                assertTrue(rig+NoiseDropTimeline.RIG_TOP<=bottom-.74999);
                assertTrue(NoiseDropTimeline.vehicleVerticalOffset(t,start)>=0);
                if(t<=55)assertEquals(4.277664,rig-NoiseDropTimeline.vehicleVerticalOffset(t,start),EPSILON);
            }
            assertEquals(0,NoiseDropTimeline.vehicleVerticalOffset(66,start));
        }
        assertEquals(-1,NoiseDropTimeline.mothershipStartOffset(12));
        assertEquals(-1,NoiseDropTimeline.mothershipStartOffset(Double.NaN));
        for(float invalid:new float[]{-1,0,Float.NaN,Float.POSITIVE_INFINITY,49}) {
            for(int t=0;t<=139;t++) {
                assertEquals(NoiseDropTimeline.rigVerticalOffset(t),NoiseDropTimeline.rigVerticalOffset(t,invalid));
                assertEquals(NoiseDropTimeline.vehicleVerticalOffset(t),NoiseDropTimeline.vehicleVerticalOffset(t,invalid));
            }
        }
    }

    @Test
    void sevenSecondTimelineKeepsEveryAuthoredAnchor() {
        assertEquals(140, NoiseDropTimeline.TOTAL_TICKS);
        assertEquals(52.277664D, NoiseDropTimeline.rigVerticalOffset(0), EPSILON);
        assertEquals(9.777664D,
                NoiseDropTimeline.rigVerticalOffset(NoiseDropTimeline.SLAM_END_TICK), EPSILON);
        assertEquals(8.277664D,
                NoiseDropTimeline.rigVerticalOffset(NoiseDropTimeline.BRAKE_END_TICK), EPSILON);
        assertEquals(8.277664D,
                NoiseDropTimeline.rigVerticalOffset(NoiseDropTimeline.RELEASE_END_TICK), EPSILON);
        assertEquals(12.277664D,
                NoiseDropTimeline.rigVerticalOffset(NoiseDropTimeline.LIFT_END_TICK), EPSILON);
        assertEquals(70.277664D,
                NoiseDropTimeline.rigVerticalOffset(NoiseDropTimeline.ACTIVATION_TICK), EPSILON);

        assertEquals(48.0D, NoiseDropTimeline.vehicleVerticalOffset(0), EPSILON);
        assertEquals(5.5D,
                NoiseDropTimeline.vehicleVerticalOffset(NoiseDropTimeline.SLAM_END_TICK), EPSILON);
        assertEquals(4.0D,
                NoiseDropTimeline.vehicleVerticalOffset(NoiseDropTimeline.BRAKE_END_TICK), EPSILON);
        assertEquals(0.0D,
                NoiseDropTimeline.vehicleVerticalOffset(NoiseDropTimeline.RELEASE_END_TICK), EPSILON);
    }

    @Test
    void motionIsVerticalMonotonicAndActivatesOnlyAfterTheRigLeaves() {
        for (int tick = 1; tick <= NoiseDropTimeline.BRAKE_END_TICK; tick++) {
            assertTrue(NoiseDropTimeline.rigVerticalOffset(tick)
                    <= NoiseDropTimeline.rigVerticalOffset(tick - 1) + EPSILON);
            assertTrue(NoiseDropTimeline.vehicleVerticalOffset(tick)
                    <= NoiseDropTimeline.vehicleVerticalOffset(tick - 1) + EPSILON);
        }
        for (int tick = NoiseDropTimeline.BRAKE_END_TICK + 1;
                tick <= NoiseDropTimeline.RELEASE_END_TICK;
                tick++) {
            assertTrue(NoiseDropTimeline.vehicleVerticalOffset(tick)
                    <= NoiseDropTimeline.vehicleVerticalOffset(tick - 1) + EPSILON);
        }
        for (int tick = NoiseDropTimeline.RELEASE_END_TICK + 1;
                tick <= NoiseDropTimeline.ACTIVATION_TICK;
                tick++) {
            assertTrue(NoiseDropTimeline.rigVerticalOffset(tick) + EPSILON
                    >= NoiseDropTimeline.rigVerticalOffset(tick - 1));
        }

        assertTrue(NoiseDropTimeline.isRigVisible(NoiseDropTimeline.ACTIVATION_TICK - 1));
        assertFalse(NoiseDropTimeline.isRigVisible(NoiseDropTimeline.ACTIVATION_TICK));
        assertFalse(NoiseDropTimeline.shouldActivate(NoiseDropTimeline.ACTIVATION_TICK - 1));
        assertTrue(NoiseDropTimeline.shouldActivate(NoiseDropTimeline.ACTIVATION_TICK));
    }

    @Test
    void descentPlumeShrinksBeforeLiftThrottleReturns() {
        assertTrue(NoiseDropTimeline.exhaustStrength(0)
                > NoiseDropTimeline.exhaustStrength(NoiseDropTimeline.SLAM_END_TICK));
        assertTrue(NoiseDropTimeline.exhaustStrength(NoiseDropTimeline.SLAM_END_TICK)
                > NoiseDropTimeline.exhaustStrength(NoiseDropTimeline.RELEASE_END_TICK));
        assertTrue(NoiseDropTimeline.exhaustStrength(NoiseDropTimeline.LIFT_END_TICK)
                > NoiseDropTimeline.exhaustStrength(NoiseDropTimeline.RELEASE_END_TICK));
        assertTrue(NoiseDropTimeline.exhaustStrength(NoiseDropTimeline.ACTIVATION_TICK)
                > NoiseDropTimeline.exhaustStrength(NoiseDropTimeline.LIFT_END_TICK));
    }

    @Test
    void renderTimeUsesPartialTicksWithoutChangingTheAuthoredCurve() {
        assertEquals(0.0D,
                NoiseDropTimeline.renderElapsedTicks(NoiseDropTimeline.TOTAL_TICKS, 0.0F),
                EPSILON);
        assertEquals(0.5D,
                NoiseDropTimeline.renderElapsedTicks(NoiseDropTimeline.TOTAL_TICKS, 0.5F),
                EPSILON);
        assertEquals(NoiseDropTimeline.ACTIVATION_TICK,
                NoiseDropTimeline.renderElapsedTicks(1, 1.0F),
                EPSILON);

        for (int tick = 0; tick < NoiseDropTimeline.BRAKE_END_TICK; tick++) {
            double atTick = NoiseDropTimeline.rigVerticalOffset(tick);
            double atHalfTick = NoiseDropTimeline.rigVerticalOffset(tick + 0.5D);
            double atNextTick = NoiseDropTimeline.rigVerticalOffset(tick + 1.0D);
            assertTrue(atTick + EPSILON >= atHalfTick);
            assertTrue(atHalfTick + EPSILON >= atNextTick);
        }
        for (int tick = NoiseDropTimeline.RELEASE_END_TICK;
                tick < NoiseDropTimeline.ACTIVATION_TICK;
                tick++) {
            double atTick = NoiseDropTimeline.rigVerticalOffset(tick);
            double atHalfTick = NoiseDropTimeline.rigVerticalOffset(tick + 0.5D);
            double atNextTick = NoiseDropTimeline.rigVerticalOffset(tick + 1.0D);
            assertTrue(atTick <= atHalfTick + EPSILON);
            assertTrue(atHalfTick <= atNextTick + EPSILON);
        }
    }
}
