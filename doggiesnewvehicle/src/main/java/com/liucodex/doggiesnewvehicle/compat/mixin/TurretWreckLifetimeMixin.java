package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.TurretWreckEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvWreckLifetime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TurretWreckEntity.class, remap = false)
public abstract class TurretWreckLifetimeMixin {
    @Inject(method = "m_6075_", at = @At("HEAD"), cancellable = true)
    private void dnv$expireTurretWreck(CallbackInfo callback) {
        if (DnvWreckLifetime.tick((TurretWreckEntity) (Object) this)) callback.cancel();
    }
}
