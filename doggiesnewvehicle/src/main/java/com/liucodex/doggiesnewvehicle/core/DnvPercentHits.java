package com.liucodex.doggiesnewvehicle.core;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.entity.PartEntity;

/** Native hit adapter. State lives on the projectile/source, never in a world or target tick ledger. */
public final class DnvPercentHits {
    private static final String FRACTION = "DnvDirectPercent";
    private static final String TARGETS = "DnvDirectPercentTargets";
    private DnvPercentHits() { }
    public static void mark(Entity projectile, float fraction) {
        if (projectile instanceof Projectile && Float.isFinite(fraction) && fraction > 0 && fraction <= 1)
            projectile.getPersistentData().putFloat(FRACTION, fraction);
    }
    public static float apply(Entity target, DamageSource source, float incoming, float armored) {
        if (target.level().isClientSide) return armored;
        float fraction;
        DnvDirectHitSource hit = source instanceof DnvDirectHitSource direct ? direct : null;
        Entity projectile = source.getDirectEntity();
        if (hit != null) fraction = hit.percent();
        else {
            // SBW fires its absolute split first. The bonus belongs to the ordinary direct leg,
            // so it cannot inherit that leg's armor bypass or consume the real hit's claim.
            if (!(projectile instanceof Projectile) || !DnvTaggedProjectileArmor.isDirectProjectile(source)
                    || source.is(com.atsuishio.superbwarfare.init.ModDamageTypes.GUN_FIRE_ABSOLUTE)
                    || source.is(com.atsuishio.superbwarfare.init.ModDamageTypes.GUN_FIRE_HEADSHOT_ABSOLUTE)) return armored;
            fraction = projectile.getPersistentData().getFloat(FRACTION);
        }
        if (!Float.isFinite(fraction) || fraction <= 0 || fraction > 1) return armored;
        Entity parent = target instanceof PartEntity<?> part ? part.getParent() : target;
        float max = parent instanceof LivingEntity living ? living.getMaxHealth()
                : parent instanceof VehicleEntity vehicle ? vehicle.getMaxHealth() : 0;
        float merged = DnvPercentDamage.merge(incoming, armored, max, fraction);
        if (merged == armored) return armored;
        if (hit != null) return hit.claim(parent.getUUID()) ? merged : armored;
        var data = projectile.getPersistentData();
        var targets = data.getCompound(TARGETS);
        String key = parent.getUUID().toString();
        if (targets.getBoolean(key)) return armored;
        targets.putBoolean(key, true);
        data.put(TARGETS, targets);
        return merged;
    }
}
