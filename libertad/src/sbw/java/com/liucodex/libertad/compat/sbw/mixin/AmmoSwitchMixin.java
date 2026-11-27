package com.liucodex.libertad.compat.sbw.mixin;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.liucodex.libertad.ship.LibertadEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GunData.class, remap = false)
abstract class AmmoSwitchMixin {
    @Inject(method = "changeAmmoConsumer(ILnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void libertad$preserveMainReload(int index, Entity supplier, CallbackInfo callback) {
        if (supplier instanceof LibertadEntity ship && ship.deferAmmoChange((GunData) (Object) this, index)) callback.cancel();
    }
}
