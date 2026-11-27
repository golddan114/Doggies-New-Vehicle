package com.liucodex.doggiesnewvehicle.client.mixin;

import com.liucodex.doggiesnewvehicle.client.BlizzardTvCamera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Route vanilla sensitivity-adjusted mouse deltas to the TV session without rotating the seated body. */
@Mixin(value = Entity.class, priority = 2000)
public abstract class BlizzardTvMouseMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void dnv$tvTurn(double yaw, double pitch, CallbackInfo callback) {
        if (BlizzardTvCamera.turn((Entity) (Object) this, yaw, pitch)) callback.cancel();
    }
}
