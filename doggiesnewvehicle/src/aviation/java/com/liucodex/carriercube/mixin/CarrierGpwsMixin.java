package com.liucodex.carriercube.mixin;

import com.atsuishio.superbwarfare.client.overlay.GPWSOverlay;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.carriercube.CubePrototype;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GPWSOverlay.class, remap = false)
public abstract class CarrierGpwsMixin {
    @Inject(method = "isAircraft", at = @At("HEAD"), cancellable = true)
    private void carrier$ignoreStationHolding(VehicleEntity vehicle, CallbackInfoReturnable<Boolean> result) {
        if (CubePrototype.stationaryFlight(vehicle)) result.setReturnValue(false);
    }
}
