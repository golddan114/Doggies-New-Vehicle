package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvWreckLifetime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleWreckLifetimeMixin {
    @Inject(method = "m_6075_", at = @At("HEAD"), cancellable = true)
    private void dnv$expireWreck(CallbackInfo callback) {
        if (DnvWreckLifetime.tick((VehicleEntity) (Object) this)) callback.cancel();
    }
}
