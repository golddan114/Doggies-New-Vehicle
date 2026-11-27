package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleMotionUtils;
import com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native block-crushing phase is distinct from motion and entity collisions. */
@Mixin(value = VehicleMotionUtils.class, remap = false)
public abstract class VehicleBlockDamageMixin {
    @Inject(method = "collideBlocks", at = @At("HEAD"), cancellable = true, require = 1)
    private static void dnv$collisionTerrain(VehicleEntity vehicle, CallbackInfo callback) {
        if (DnvBlockDamagePolicy.owned(vehicle) && !DnvBlockDamagePolicy.terrainAllowed(vehicle.level(), vehicle)) callback.cancel();
    }
}
