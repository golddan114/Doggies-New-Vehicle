package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DnvHoverCrewExitRulesTest {
    @Test
    void allowsGroundedExitButRejectsHighAltitudeAndInvalidHeights() {
        double limit = DnvHoverCrewExitRules.MAX_GROUND_OFFSET;
        assertTrue(DnvHoverCrewExitRules.groundClose(64.1D, 64.0D, limit));
        assertTrue(DnvHoverCrewExitRules.groundClose(64.0D, 65.6D, limit));
        assertFalse(DnvHoverCrewExitRules.groundClose(128.0D, 64.0D, limit));
        assertFalse(DnvHoverCrewExitRules.groundClose(Double.NaN, 64.0D, limit));
        assertFalse(DnvHoverCrewExitRules.groundClose(64.0D, Double.POSITIVE_INFINITY, limit));
    }
}
