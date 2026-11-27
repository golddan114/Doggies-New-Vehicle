package com.liucodex.doggiesnewvehicle.armored;

import com.liucodex.doggiesnewvehicle.api.combat.DnvColdArmor;
import com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor;

/** Only the user-approved cold coefficients; native hurt still owns immunity and health. */
final class BlizzardColdDamage {
    private BlizzardColdDamage() { }
    static float amount(Object target, boolean mode2) {
        // Multipart proxies own no maximum health; use the same parent's armor and maximum.
        if (target instanceof net.minecraftforge.entity.PartEntity<?> part) target = part.getParent();
        float scale = mode2 ? BlizzardRules.MODE2_DAMAGE_SCALE : 1;
        float max = target instanceof net.minecraft.world.entity.LivingEntity living ? living.getMaxHealth()
                : target instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity vehicle ? vehicle.getMaxHealth() : 0;
        return com.liucodex.doggiesnewvehicle.core.DnvPercentDamage.merge(10 * scale, amount(target) * scale, max,
                com.liucodex.doggiesnewvehicle.artillery.ArtilleryPercentDamage.fraction(mode2 ? "blizzard_mode2" : "blizzard_mode1"));
    }
    static float amount(Object target) {
        if (target instanceof net.minecraftforge.entity.PartEntity<?> part) target = part.getParent();
        if (target instanceof DnvColdArmor armor) return armor.coldArmorTier() == DnvColdArmor.Tier.MEDIUM ? 6 : 3;
        if (target instanceof DnvPhysicalArmor armor) {
            float reduction = armor.ordinaryArmorReduction();
            if (reduction >= .75F) return 3;
            if (reduction >= .55F) return 6;
        }
        return 10;
    }
}
