package com.liucodex.silentheaven.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.boss.DnvVehicleTransitionTimeline;
import org.junit.jupiter.api.Test;

class SilentHeavenTransitionTimelineTest {
    @Test
    void hatchOpensHoldsAndClosesOnAuthoredTimeline() {
        assertEquals(0.0F, hatchAt(19.99F));
        assertEquals(0.5F, hatchAt(40.0F), 0.0001F);
        assertEquals(1.0F, hatchAt(60.0F));
        assertEquals(1.0F, hatchAt(249.99F));
        assertEquals(0.5F, hatchAt(270.0F), 0.0001F);
        assertEquals(0.0F, hatchAt(290.0F));
        assertEquals(0.0F, DnvVehicleTransitionTimeline.hatchOpenProgress(
                true, false, 370.0F));
    }

    @Test
    void needlesAppearInThreeStaggeredPairs() {
        assertEquals(70.0F, DnvVehicleTransitionTimeline.needleStartTick(0));
        assertEquals(100.0F, DnvVehicleTransitionTimeline.needleStartTick(1));
        assertEquals(130.0F, DnvVehicleTransitionTimeline.needleStartTick(2));
        assertEquals(70.0F, DnvVehicleTransitionTimeline.needleStartTick(3));
        assertFalse(DnvVehicleTransitionTimeline.haloNeedleVisible(false, true, 69, 0));
        assertTrue(DnvVehicleTransitionTimeline.haloNeedleVisible(false, true, 70, 0));
        assertTrue(DnvVehicleTransitionTimeline.haloNeedleVisible(true, false, 0, 5));
        assertThrows(IllegalArgumentException.class,
                () -> DnvVehicleTransitionTimeline.needleStartTick(6));
    }

    @Test
    void everyNeedleIsDockedBeforeHatchesCloseAndHaloStarts() {
        assertEquals(190.0F, DnvVehicleTransitionTimeline.needleDockTick(0));
        assertEquals(220.0F, DnvVehicleTransitionTimeline.needleDockTick(1));
        assertEquals(250.0F, DnvVehicleTransitionTimeline.needleDockTick(2));
        assertEquals(250.0F, DnvVehicleTransitionTimeline.needleDockTick(5));
        for (int index = 0; index < 6; index++) {
            assertEquals(1.0F,
                    DnvVehicleTransitionTimeline.needleEjectProgress(250.0F, index));
            assertEquals(1.0F,
                    DnvVehicleTransitionTimeline.needleSettleProgress(250.0F, index));
            assertEquals(1.0F,
                    DnvVehicleTransitionTimeline.needleUprightProgress(250.0F, index));
        }
    }

    @Test
    void haloRingAppearsAndFadesInDuringLateTransition() {
        assertFalse(DnvVehicleTransitionTimeline.haloRingVisible(false, true, 329));
        assertTrue(DnvVehicleTransitionTimeline.haloRingVisible(false, true, 330));
        assertFalse(DnvVehicleTransitionTimeline.haloRotationActive(false, true, 329.99F));
        assertTrue(DnvVehicleTransitionTimeline.haloRotationActive(false, true, 330.0F));
        assertEquals(0.0F, opacityAt(330.0F));
        assertEquals(0.5F, opacityAt(345.0F), 0.0001F);
        assertEquals(1.0F, opacityAt(360.0F));
        assertEquals(1.0F, opacityAt(369.0F));
        assertEquals(1.0F, DnvVehicleTransitionTimeline.haloRingOpacity(
                true, false, 0.0F));
    }

    private static float hatchAt(float tick) {
        return DnvVehicleTransitionTimeline.hatchOpenProgress(false, true, tick);
    }

    private static float opacityAt(float tick) {
        return DnvVehicleTransitionTimeline.haloRingOpacity(false, true, tick);
    }
}
