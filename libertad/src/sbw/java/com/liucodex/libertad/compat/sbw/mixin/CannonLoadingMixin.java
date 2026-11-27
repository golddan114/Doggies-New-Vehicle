package com.liucodex.libertad.compat.sbw.mixin;

import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.liucodex.libertad.compat.sbw.NativeShells;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CannonShellEntity.class, remap = false)
abstract class CannonLoadingMixin {
    @Inject(method = "forceLoadChunk", at = @At("HEAD"), cancellable = true)
    private void libertad$noShellTickets(CallbackInfoReturnable<Boolean> callback) {
        if (NativeShells.managed((CannonShellEntity) (Object) this)) callback.setReturnValue(false);
    }
}
