package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.liucodex.doggiesnewvehicle.armored.DnvApocalypseEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve a loaded or reloading shell when the native ammunition-selection input is used. */
@Mixin(value = GunData.class, remap = false)
public abstract class ApocalypseAmmoSelectionMixin {
    @Inject(method = "changeAmmoConsumer(ILnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void dnv$queueApocalypseAmmo(int index, Entity supplier, CallbackInfo callback) {
        if (supplier instanceof DnvApocalypseEntity tank && tank.requestAmmoSelection((GunData) (Object) this, index)) callback.cancel();
    }
}
