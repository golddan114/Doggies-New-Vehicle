package com.liucodex.doggiesnewvehicle.core;

import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.atsuishio.superbwarfare.init.ModTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/** Shared armor preset and ordinary-physical classification; optional content supplies the profile. */
public final class DnvArmorRules {
    public enum Profile {
        SUPERHEAVY(0.80F), HEAVY(0.75F), MEDIUM(0.55F), SPECIAL(0.80F), LIGHT(0.30F);

        private final float reduction;
        Profile(float reduction) { this.reduction = reduction; }
        public float reduction() { return reduction; }
    }

    private DnvArmorRules() { }

    public static float apply(Profile profile, float rawDamage) {
        return apply(profile.reduction(), rawDamage);
    }

    public static float apply(float reduction, float rawDamage) {
        if (!(rawDamage > 0.0F) || !Float.isFinite(rawDamage)) return 0.0F;
        if (!Float.isFinite(reduction) || reduction < 0.0F || reduction > 1.0F)
            throw new IllegalArgumentException("reduction");
        return rawDamage * (1.0F - reduction);
    }

    public static boolean ordinaryPhysical(DamageSource source) {
        if (source instanceof DnvDirectArmorPenetration) return true;
        if (source.is(DnvArmorDamageTags.SPECIAL) || source.is(ModDamageTypes.LASER)) return false;
        return source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(ModDamageTypes.CUSTOM_EXPLOSION)
                || source.is(ModDamageTypes.PROJECTILE_EXPLOSION)
                || source.is(ModDamageTypes.VEHICLE_EXPLOSION)
                || source.is(ModDamageTypes.PROJECTILE_HIT)
                || source.is(ModDamageTypes.PROJECTILE_HIT_HEADSHOT)
                || source.is(ModDamageTypes.GRAPESHOT_HIT)
                || source.is(ModTags.DamageTypes.PROJECTILE)
                || source.is(ModTags.DamageTypes.PROJECTILE_ABSOLUTE)
                || source.is(DamageTypeTags.IS_PROJECTILE);
    }
}
