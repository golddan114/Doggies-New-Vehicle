package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.liucodex.doggiesnewvehicle.compat.DnvTurretShells;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MissileProjectile.class, remap = false)
public abstract class DnvMissileChunkMixin {
    @Inject(method = "forceLoadChunk", at = @At("HEAD"), cancellable = true)
    private void dnv$boundedChunks(CallbackInfoReturnable<Boolean> callback) {
        var missile = (MissileProjectile) (Object) this;
        if (DnvTurretShells.managed(missile)) callback.setReturnValue(false);
    }

    @Inject(method = "m_8119_", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/DestroyableProjectile;m_8119_()V",
            shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void dnv$skipRemovedMissileTick(CallbackInfo callback) {
        var missile = (MissileProjectile) (Object) this;
        if (missile.isRemoved() && DnvTurretShells.managed(missile)) callback.cancel();
    }
}
