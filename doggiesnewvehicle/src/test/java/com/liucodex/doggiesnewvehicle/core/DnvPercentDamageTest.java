package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DnvPercentDamageTest {
    @Test void blockedStormCannotCreateDamageAgainstAnUnpenetratedTank() {
        assertEquals(0, DnvPercentDamage.merge(10, 0, 3000, .001F));
        assertEquals(5.72F, DnvPercentDamage.merge(10, 4.4F, 3000, .001F), .00001);
    }
    @Test void percentageUsesMaximumHealthAndTheSameArmorPassThrough() {
        assertEquals(400, DnvPercentDamage.merge(220, 220, 3000, .06F), .0001);
        assertEquals(200, DnvPercentDamage.merge(220, 110, 3000, .06F), .0001);
        assertEquals(425, DnvPercentDamage.merge(600, 300, 2000, .125F), .0001);
        assertEquals(52.8F, DnvPercentDamage.merge(22, 6.6F, 7700, .02F), .0001);
    }
    @Test void noBonusOnMissingInvalidOrZeroInputsAndNoOverflow() {
        for (float value : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertEquals(10, DnvPercentDamage.merge(value, 10, 1000, .04F));
            assertEquals(10, DnvPercentDamage.merge(10, 10, value, .04F));
            assertEquals(10, DnvPercentDamage.merge(10, 10, 1000, value));
        }
        assertEquals(10, DnvPercentDamage.merge(10, 10, 1000, 1.01F));
        assertEquals(Float.MAX_VALUE, DnvPercentDamage.merge(Float.MIN_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, 1));
    }
    @Test void packagedFractionsAndNativeCadencesMatchConfiguredMinuteTotals() {
        String[] ids = {"storm", "changfeng", "jingzhe", "yunlong", "blizzard_mode1", "blizzard_mode2"};
        float[] expected = {.001F, .045F, .035F, .08F, .003F, .114F};
        int[] rates = {480, 10, 30, 5, 120, 12};
        float[] minute = {.48F, .45F, 1.05F, .40F, .36F, 1.368F};
        for (int i = 0; i < ids.length; i++) {
            float fraction = com.liucodex.doggiesnewvehicle.artillery.ArtilleryPercentDamage.fraction(ids[i]);
            assertEquals(expected[i], fraction);
            assertEquals(minute[i], fraction * rates[i], .000001);
        }
    }
}
