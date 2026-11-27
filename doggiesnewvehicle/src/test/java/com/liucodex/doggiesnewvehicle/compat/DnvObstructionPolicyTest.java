package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DnvObstructionPolicyTest {
    @Test
    void advancesThroughAimMoveRetargetAndTeleportStages() {
        assertEquals(DnvObstructionPolicy.Stage.CLEAR,
                DnvObstructionPolicy.stage(0));
        assertEquals(DnvObstructionPolicy.Stage.AIM_ADJUST,
                DnvObstructionPolicy.stage(1));
        assertEquals(DnvObstructionPolicy.Stage.REPOSITION,
                DnvObstructionPolicy.stage(10));
        assertEquals(DnvObstructionPolicy.Stage.RETARGET,
                DnvObstructionPolicy.stage(60));
        assertEquals(DnvObstructionPolicy.Stage.TELEPORT,
                DnvObstructionPolicy.stage(200));
        assertFalse(DnvObstructionPolicy.shouldTeleport(199));
        assertTrue(DnvObstructionPolicy.shouldTeleport(200));
    }

    @Test
    void maneuverDirectionAlternatesWithoutPerTickRandomness() {
        float first = DnvObstructionPolicy.maneuverYawOffset(12, 10);
        assertEquals(first,
                DnvObstructionPolicy.maneuverYawOffset(12, 39));
        assertNotEquals(first,
                DnvObstructionPolicy.maneuverYawOffset(12, 40));
    }
}
