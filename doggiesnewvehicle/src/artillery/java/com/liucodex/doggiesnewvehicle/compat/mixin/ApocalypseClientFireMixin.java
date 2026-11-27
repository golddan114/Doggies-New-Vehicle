package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotAdmission;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Query the vehicle owner before native packet/predicted sound; native held-fire and lock clocks are preserved. */
@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class ApocalypseClientFireMixin {
    @Inject(method = "clientShootVehicle", at = @At("HEAD"), cancellable = true, remap = false)
    private void dnv$missileLock(Player player, VehicleEntity vehicle, GunData gun, CallbackInfo callback) {
        if (vehicle instanceof DnvNativeShotAdmission admission
                && !admission.permitsNativeClientFire(player, ClientEventHandler.lockingEntityVehicle,
                        ClientEventHandler.lockOnVehicle, gun)) {
            callback.cancel();
        }
    }
}
