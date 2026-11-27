package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.liucodex.doggiesnewvehicle.core.DnvPercentHits;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class DnvLivingPercentDamageMixin {
    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("RETURN"), cancellable = true)
    private void dnv$percent(DamageSource source, float amount, CallbackInfoReturnable<Float> result) {
        result.setReturnValue(DnvPercentHits.apply((LivingEntity)(Object)this, source, amount, result.getReturnValueF()));
    }
}
