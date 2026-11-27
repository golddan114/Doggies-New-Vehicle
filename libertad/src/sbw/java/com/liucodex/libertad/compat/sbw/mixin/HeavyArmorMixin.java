package com.liucodex.libertad.compat.sbw.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.damage.DamageModifier;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.atsuishio.superbwarfare.init.ModTags;
import com.liucodex.libertad.ship.LibertadEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNV HEAVY classification, scoped to Libertad and applied once to the raw physical hit. */
@Mixin(value = DamageModifier.class, remap = false)
abstract class HeavyArmorMixin {
    @Inject(method = "compute", at = @At("RETURN"), cancellable = true)
    private void libertad$heavyArmor(Entity target, DamageSource source, float amount, CallbackInfoReturnable<Float> result) {
        if (target instanceof LibertadEntity && !source.is(ModDamageTypes.LASER)
                && (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(ModDamageTypes.CUSTOM_EXPLOSION) || source.is(ModDamageTypes.PROJECTILE_EXPLOSION)
                || source.is(ModDamageTypes.VEHICLE_EXPLOSION) || source.is(ModDamageTypes.PROJECTILE_HIT)
                || source.is(ModDamageTypes.PROJECTILE_HIT_HEADSHOT) || source.is(ModDamageTypes.GRAPESHOT_HIT)
                || source.is(ModTags.DamageTypes.PROJECTILE) || source.is(ModTags.DamageTypes.PROJECTILE_ABSOLUTE))) {
            float heavyLimit = Float.isFinite(amount) ? Math.max(0, amount * .25F) : 0F;
            result.setReturnValue(Math.min(result.getReturnValueF(), heavyLimit));
        }
    }
}
