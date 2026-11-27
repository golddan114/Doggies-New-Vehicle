package com.liucodex.libertad.compat.sbw.mixin;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.liucodex.libertad.compat.sbw.NativeShells;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GunItem.class, remap = false)
abstract class GunAdmissionMixin {
    @Inject(method = "shootBullet(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)Z", at = @At("RETURN"), cancellable = true)
    private void libertad$confirmedAdmission(ShootParameters shot, CallbackInfoReturnable<Boolean> callback) {
        boolean vanilla = callback.getReturnValueZ();
        boolean accepted = NativeShells.actualResult(shot, vanilla);
        if (vanilla != accepted) callback.setReturnValue(accepted);
    }
}
