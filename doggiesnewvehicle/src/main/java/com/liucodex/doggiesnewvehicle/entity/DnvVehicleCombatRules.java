package com.liucodex.doggiesnewvehicle.entity;

import com.liucodex.doggiesnewvehicle.core.DnvArmorRules;

/** Shared pure combat rules for DNV boss vehicles with the same phase damage model. */
public final class DnvVehicleCombatRules {
    public enum DamageCategory {
        BULLET,
        HEAVY_DIRECT,
        EXPLOSION,
        SPECIAL_EXPLOSION,
        OTHER
    }

    private DnvVehicleCombatRules() {
    }

    public static boolean withinArc(
            double deltaX,
            double deltaY,
            double deltaZ,
            float hullYaw,
            float maxLocalYaw,
            float minPitch,
            float maxPitch) {
        if (!Double.isFinite(deltaX)
                || !Double.isFinite(deltaY)
                || !Double.isFinite(deltaZ)
                || !Float.isFinite(hullYaw)) {
            return false;
        }
        double horizontal = Math.hypot(deltaX, deltaZ);
        if (horizontal <= 1.0E-9D && Math.abs(deltaY) <= 1.0E-9D) {
            return false;
        }
        double worldYaw = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D;
        double localYaw = wrapDegrees(worldYaw - hullYaw);
        double pitch = Math.toDegrees(Math.atan2(deltaY, horizontal));
        return Math.abs(localYaw) <= maxLocalYaw
                && pitch >= minPitch
                && pitch <= maxPitch;
    }

    public static float applyPhaseDamage(
            int phase,
            DamageCategory category,
            float rawDamage) {
        if (!(rawDamage > 0.0F) || !Float.isFinite(rawDamage)) {
            return 0.0F;
        }
        if (category == DamageCategory.OTHER) return rawDamage * (phase <= 0 ? 0.40F : 0.30F);
        if (category == DamageCategory.SPECIAL_EXPLOSION) return rawDamage * (phase <= 0 ? 0.60F : 0.70F);
        return DnvArmorRules.apply(phase <= 0 ? DnvArmorRules.Profile.MEDIUM : DnvArmorRules.Profile.SPECIAL, rawDamage);
    }

    public static float applyBaseDamage(DamageCategory category, float rawDamage) {
        if (!(rawDamage > 0.0F) || !Float.isFinite(rawDamage)) return 0.0F;
        return category == DamageCategory.OTHER ? rawDamage * 0.40F
                : category == DamageCategory.SPECIAL_EXPLOSION ? rawDamage * 0.60F
                : DnvArmorRules.apply(DnvArmorRules.Profile.HEAVY, rawDamage);
    }

    public static double wrapDegrees(double value) {
        double wrapped = value % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        }
        if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }
}
