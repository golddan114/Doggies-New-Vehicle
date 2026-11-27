package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DnvHoverSummonPolicyTest {
    @Test
    void warningRequiresContinuousLegalTarget() {
        int warning = 0;
        for (int tick = 0; tick < DnvHoverSummonPolicy.WARNING_TICKS; tick++) {
            warning = DnvHoverSummonPolicy.advanceWarning(warning, true);
        }
        assertEquals(DnvHoverSummonPolicy.WARNING_TICKS, warning);
        assertEquals(DnvHoverSummonPolicy.WARNING_TICKS,
                DnvHoverSummonPolicy.advanceWarning(warning, true));
        assertEquals(0, DnvHoverSummonPolicy.advanceWarning(warning, false));
    }

    @Test
    void successfulSummonsHaveCooldownAndHardLifetimeCap() {
        assertTrue(DnvHoverSummonPolicy.mayLock(0, 0, 100));
        long next = DnvHoverSummonPolicy.nextAllowedTime(100, true);
        assertEquals(700, next);
        assertFalse(DnvHoverSummonPolicy.mayLock(1, next, 699));
        assertTrue(DnvHoverSummonPolicy.mayLock(1, next, 700));
        assertFalse(DnvHoverSummonPolicy.mayLock(2, next, 100_000));
        assertEquals(200, DnvHoverSummonPolicy.nextAllowedTime(100, false));
    }

    @Test
    void movingOutOfMarkedSiteCancelsTheWarning() {
        assertTrue(DnvHoverSummonPolicy.targetNearMarkedSite(
                12.0D, 0.0D, 0.0D, 0.0D));
        assertFalse(DnvHoverSummonPolicy.targetNearMarkedSite(
                24.1D, 0.0D, 0.0D, 0.0D));
        assertFalse(DnvHoverSummonPolicy.targetNearMarkedSite(
                Double.NaN, 0.0D, 0.0D, 0.0D));
    }
}
