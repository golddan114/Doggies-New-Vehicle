package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.damage.DamageModifier;
import com.liucodex.doggiesnewvehicle.core.DnvTaggedProjectileArmor;
import com.liucodex.doggiesnewvehicle.core.DnvArmorRules;
import com.liucodex.doggiesnewvehicle.api.combat.DnvPhysicalArmor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DamageModifier.class, remap = false)
public abstract class DnvVehicleArmorMixin {
    // Translated gunfire also reaches compute; use its current source/amount and leave hurt redirects available.
    @Inject(method = "compute", at = @At("RETURN"), cancellable = true, require = 1)
    private void dnv$vehicleArmor(Entity target, DamageSource source, float amount,
                                  CallbackInfoReturnable<Float> result) {
        if (!(target instanceof VehicleEntity)) return;
        float damage = result.getReturnValueF();
        if (DnvArmorRules.ordinaryPhysical(source)
                && target instanceof DnvPhysicalArmor profile) {
            damage = DnvArmorRules.apply(profile.ordinaryArmorReduction(), amount);
        }
        float adjusted = DnvTaggedProjectileArmor.apply(source, amount, damage);
        adjusted = com.liucodex.doggiesnewvehicle.core.DnvPercentHits.apply(target, source, amount, adjusted);
        if (adjusted != result.getReturnValueF()) result.setReturnValue(adjusted);
    }
}
