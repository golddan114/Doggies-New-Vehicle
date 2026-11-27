package com.liucodex.libertad.compat.sbw.mixin;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.network.message.send.VehicleFireMessage;
import com.atsuishio.superbwarfare.tools.MinecraftUtil;
import com.liucodex.libertad.ship.LibertadEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientEventHandler.class, remap = false)
abstract class ClientFireMixin {
    @Inject(method = "clientShootVehicle", at = @At("HEAD"), cancellable = true)
    private void libertad$fireWithoutPredictedSound(Player player, VehicleEntity vehicle, GunData gun, CallbackInfo callback) {
        if (!(vehicle instanceof LibertadEntity)) return;
        MinecraftUtil.sendPacketToServer(new VehicleFireMessage(null, null, null, vehicle.getId()));
        callback.cancel();
    }
}
