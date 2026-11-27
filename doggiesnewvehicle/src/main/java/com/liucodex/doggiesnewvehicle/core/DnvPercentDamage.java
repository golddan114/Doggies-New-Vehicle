package com.liucodex.doggiesnewvehicle.core;

/** Shares the base hit's armor pass-through; never turns a refused hit into damage. */
public final class DnvPercentDamage {
    private DnvPercentDamage() { }
    public static float merge(float incoming, float armored, float maxHealth, float fraction) {
        if (!Float.isFinite(incoming) || incoming <= 0 || !Float.isFinite(armored) || armored <= 0
                || !Float.isFinite(maxHealth) || maxHealth <= 0 || !Float.isFinite(fraction)
                || fraction <= 0 || fraction > 1) return armored;
        double result = armored + (double) maxHealth * fraction * armored / incoming;
        return (float) Math.min(Float.MAX_VALUE, result);
    }
}
