package com.liucodex.silentheaven.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.entity.DnvVehicleCombatRules;
import com.liucodex.doggiesnewvehicle.core.DnvArmorRules;
import org.junit.jupiter.api.Test;

class SilentHeavenCombatRulesTest {
    private static final float MAX_YAW = 82.0F;
    private static final float MIN_PITCH = -8.0F;
    private static final float MAX_PITCH = 42.0F;

    @Test
    void autocannonArcAcceptsFrontAndAuthoredLimits() {
        assertTrue(inArc(0.0D, 0.0D, 10.0D, 0.0F));
        assertTrue(inArc(Math.sin(Math.toRadians(82.0D)), 0.0D,
                Math.cos(Math.toRadians(82.0D)), 0.0F));
        double horizontal = 10.0D;
        assertTrue(inArc(0.0D, Math.tan(Math.toRadians(42.0D)) * horizontal,
                horizontal, 0.0F));
        assertTrue(inArc(0.0D, Math.tan(Math.toRadians(-8.0D)) * horizontal,
                horizontal, 0.0F));
    }

    @Test
    void autocannonArcRejectsOutsideLimitsAndInvalidVectors() {
        assertFalse(inArc(Math.sin(Math.toRadians(82.1D)), 0.0D,
                Math.cos(Math.toRadians(82.1D)), 0.0F));
        assertFalse(inArc(0.0D, Math.tan(Math.toRadians(42.1D)) * 10.0D,
                10.0D, 0.0F));
        assertFalse(inArc(0.0D, Math.tan(Math.toRadians(-8.1D)) * 10.0D,
                10.0D, 0.0F));
        assertFalse(inArc(0.0D, 0.0D, 0.0D, 0.0F));
        assertFalse(inArc(Double.NaN, 0.0D, 1.0D, 0.0F));
    }

    @Test
    void autocannonArcIsRelativeToHullYawAcrossWrapBoundary() {
        assertTrue(inArc(0.0D, 0.0D, -10.0D, -180.0F));
        assertEquals(-180.0D, DnvVehicleCombatRules.wrapDegrees(180.0D));
        assertEquals(179.0D, DnvVehicleCombatRules.wrapDegrees(-181.0D));
    }

    @Test
    void armorProfilesApplyToOrdinaryDirectHitsAndExplosions() {
        assertEquals(45.0F, damage(0, DnvVehicleCombatRules.DamageCategory.BULLET, 100.0F), 0.0001F);
        assertEquals(25.0F, DnvVehicleCombatRules.applyBaseDamage(
                DnvVehicleCombatRules.DamageCategory.BULLET, 100.0F), 0.0001F);
        assertEquals(45.0F, damage(0, DnvVehicleCombatRules.DamageCategory.HEAVY_DIRECT, 100.0F), 0.0001F);
        assertEquals(45.0F, damage(0, DnvVehicleCombatRules.DamageCategory.EXPLOSION, 100.0F), 0.0001F);
        assertEquals(20.0F, damage(2, DnvVehicleCombatRules.DamageCategory.BULLET, 100.0F), 0.0001F);
        assertEquals(20.0F, damage(2, DnvVehicleCombatRules.DamageCategory.EXPLOSION, 100.0F), 0.0001F);
        assertEquals(20.0F, DnvArmorRules.apply(DnvArmorRules.Profile.SUPERHEAVY, 100.0F), 0.0001F);
        assertEquals(70.0F, DnvArmorRules.apply(DnvArmorRules.Profile.LIGHT, 100.0F), 0.0001F);
    }

    @Test
    void specialistDamageKeepsExistingHandling() {
        assertEquals(40.0F, damage(0, DnvVehicleCombatRules.DamageCategory.OTHER, 100.0F), 0.0001F);
        assertEquals(30.0F, damage(2, DnvVehicleCombatRules.DamageCategory.OTHER, 100.0F), 0.0001F);
        assertEquals(60.0F, damage(0, DnvVehicleCombatRules.DamageCategory.SPECIAL_EXPLOSION, 100.0F), 0.0001F);
        assertEquals(70.0F, damage(2, DnvVehicleCombatRules.DamageCategory.SPECIAL_EXPLOSION, 100.0F), 0.0001F);
    }

    @Test
    void phaseDamageRejectsInvalidOrNonPositiveInputs() {
        assertEquals(0.0F, damage(0, DnvVehicleCombatRules.DamageCategory.BULLET, 0.0F));
        assertEquals(0.0F, damage(2, DnvVehicleCombatRules.DamageCategory.BULLET, Float.NaN));
    }

    private static boolean inArc(
            double x, double y, double z, float hullYaw) {
        return DnvVehicleCombatRules.withinArc(
                x, y, z, hullYaw, MAX_YAW, MIN_PITCH, MAX_PITCH);
    }

    private static float damage(
            int phase, DnvVehicleCombatRules.DamageCategory category, float raw) {
        return DnvVehicleCombatRules.applyPhaseDamage(phase, category, raw);
    }
}
