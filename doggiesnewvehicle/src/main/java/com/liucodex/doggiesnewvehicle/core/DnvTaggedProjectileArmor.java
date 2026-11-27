package com.liucodex.doggiesnewvehicle.core;

import com.atsuishio.superbwarfare.init.ModDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

/** One optional, bounded direct-hit armor adjustment carried by an admitted projectile. */
public final class DnvTaggedProjectileArmor {
    private static final String KEY = "DnvProjectileArmorBoost";
    private DnvTaggedProjectileArmor() { }

    public static void mark(Entity projectile, float fraction) {
        if (projectile != null && Float.isFinite(fraction) && fraction > 0 && fraction <= 1)
            projectile.getPersistentData().putFloat(KEY, fraction);
    }

    public static float apply(DamageSource source, float incoming, float mitigated) {
        return penetrate(incoming, mitigated, fraction(source));
    }

    public static float fraction(DamageSource source) {
        if (source instanceof DnvDirectArmorPenetration directHit) return directHit.fraction();
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof Projectile) || !isDirectProjectile(source)) return 0;
        return direct.getPersistentData().getFloat(KEY);
    }

    public static boolean isDirectProjectile(DamageSource source) {
        return source.is(ModDamageTypes.PROJECTILE_HIT)
                || source.is(ModDamageTypes.PROJECTILE_HIT_HEADSHOT)
                || source.is(ModDamageTypes.GUN_FIRE)
                || source.is(ModDamageTypes.GUN_FIRE_HEADSHOT)
                || source.is(ModDamageTypes.GUN_FIRE_ABSOLUTE)
                || source.is(ModDamageTypes.GUN_FIRE_HEADSHOT_ABSOLUTE);
    }

    public static float penetrate(float incoming, float mitigated, float fraction) {
        // Preserve explicit immunity, invalid damage and target-specific vulnerabilities.
        if (!Float.isFinite(incoming) || !Float.isFinite(mitigated) || !Float.isFinite(fraction)
                || mitigated <= 0 || incoming <= mitigated || fraction <= 0 || fraction > 1) return mitigated;
        return mitigated + (incoming - mitigated) * fraction;
    }
}
