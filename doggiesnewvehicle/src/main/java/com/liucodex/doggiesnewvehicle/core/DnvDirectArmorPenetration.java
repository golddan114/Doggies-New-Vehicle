package com.liucodex.doggiesnewvehicle.core;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Carries one synchronous, non-projectile direct hit's armor penetration. */
public final class DnvDirectArmorPenetration extends DnvDirectHitSource {
    private final float fraction;

    public DnvDirectArmorPenetration(Holder<DamageType> type, Entity direct, Entity attacker, float fraction) {
        this(type, direct, attacker, fraction, 0);
    }

    public DnvDirectArmorPenetration(Holder<DamageType> type, Entity direct, Entity attacker, float fraction, float percent) {
        super(type, direct, attacker, percent);
        if (!Float.isFinite(fraction) || fraction <= 0 || fraction > 1)
            throw new IllegalArgumentException("fraction");
        this.fraction = fraction;
    }

    public float fraction() { return fraction; }
}
