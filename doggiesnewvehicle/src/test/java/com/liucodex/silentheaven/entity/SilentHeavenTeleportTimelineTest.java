package com.liucodex.silentheaven.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.entity.DnvVehicleTeleportTimeline;
import org.junit.jupiter.api.Test;

class SilentHeavenTeleportTimelineTest {
    @Test
    void teleportUsesChargeHiddenAndRecoveryStages() {
        assertEquals(160, DnvVehicleTeleportTimeline.TOTAL_TICKS);
        assertEquals(60, DnvVehicleTeleportTimeline.CHARGE_TICKS);
        assertEquals(40, DnvVehicleTeleportTimeline.PRE_TELEPORT_HIDDEN_TICKS);
        assertEquals(60, DnvVehicleTeleportTimeline.RECOVERY_TICKS);
        assertEquals(5, DnvVehicleTeleportTimeline.ARRIVAL_HIDDEN_TICKS);
        assertFalse(DnvVehicleTeleportTimeline.isHidden(101));
        assertTrue(DnvVehicleTeleportTimeline.isHidden(100));
        assertTrue(DnvVehicleTeleportTimeline.isHidden(61));
        assertTrue(DnvVehicleTeleportTimeline.isHidden(60));
        assertTrue(DnvVehicleTeleportTimeline.isHidden(56));
        assertFalse(DnvVehicleTeleportTimeline.isHidden(55));
        assertFalse(DnvVehicleTeleportTimeline.isHidden(1));
        assertFalse(DnvVehicleTeleportTimeline.isHidden(0));
    }

    @Test
    void whiteOverlayChargesThenFadesAfterArrival() {
        assertEquals(0.0F, DnvVehicleTeleportTimeline.whiteOverlayProgress(160));
        assertEquals(0.5F,
                DnvVehicleTeleportTimeline.whiteOverlayProgress(130), 0.0001F);
        assertEquals(59.0F / 60.0F,
                DnvVehicleTeleportTimeline.whiteOverlayProgress(101), 0.0001F);
        assertEquals(1.0F, DnvVehicleTeleportTimeline.whiteOverlayProgress(55));
        assertEquals(28.0F / 55.0F,
                DnvVehicleTeleportTimeline.whiteOverlayProgress(28), 0.0001F);
        assertEquals(1.0F / 55.0F,
                DnvVehicleTeleportTimeline.whiteOverlayProgress(1), 0.0001F);
        assertEquals(0.0F, DnvVehicleTeleportTimeline.whiteOverlayProgress(0));
    }

    @Test
    void remainingTicksAreClampedAndZeroMeansInactive() {
        assertEquals(160, DnvVehicleTeleportTimeline.clampRemaining(200));
        assertEquals(0, DnvVehicleTeleportTimeline.clampRemaining(-1));
        assertTrue(DnvVehicleTeleportTimeline.isActive(160));
        assertFalse(DnvVehicleTeleportTimeline.isActive(0));
    }
}
