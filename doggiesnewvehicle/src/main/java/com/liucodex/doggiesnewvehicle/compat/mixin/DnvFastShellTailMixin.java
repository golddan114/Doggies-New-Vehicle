package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.atsuishio.superbwarfare.entity.projectile.DestroyableProjectile;
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.entity.projectile.RpgRocketStandardEntity;
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity;
import com.atsuishio.superbwarfare.entity.projectile.SmallRocketEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvTurretShells;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {CannonShellEntity.class, SmallCannonShellEntity.class, SmallRocketEntity.class,
        RpgRocketStandardEntity.class, DestroyableProjectile.class}, remap = false)
abstract class DnvFastShellTailMixin {
    @Inject(method = "m_8119_", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/FastThrowableProjectile;m_8119_()V",
            shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void dnv$skipRemovedSubclassTick(CallbackInfo callback) {
        var shell = (FastThrowableProjectile) (Object) this;
        if (shell.isRemoved() && DnvTurretShells.managed(shell)) callback.cancel();
    }
}
