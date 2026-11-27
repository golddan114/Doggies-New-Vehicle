package com.liucodex.doggiesnewvehicle.api.combat;

/** Content exposes its current armor tier; the firing weapon owns cold damage coefficients. */
public interface DnvColdArmor {
    enum Tier { MEDIUM, HEAVY }
    Tier coldArmorTier();
}
