package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvTurretShells;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ProjectileEntity.class, remap = false)
abstract class DnvBulletStepMixin {
    @Unique private boolean dnv$terminalBulletStep;

    @Inject(method = "m_8119_", at = @At("HEAD"), cancellable = true, require = 1)
    private void dnv$limitBulletStep(CallbackInfo callback) {
        var bullet = (ProjectileEntity) (Object) this;
        dnv$terminalBulletStep = false;
        if (DnvTurretShells.beforeTick(bullet)) {
            callback.cancel();
            return;
        }
        double limit = DnvTurretShells.stepLimit(bullet);
        if (limit == 0) {
            bullet.discard();
            callback.cancel();
        } else if (limit < 1) {
            bullet.setDeltaMovement(bullet.getDeltaMovement().scale(limit));
            dnv$terminalBulletStep = true;
        }
    }

    @Inject(method = "m_8119_", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/ProjectileEntity;onHitWater(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/BlockHitResult;)V"),
            cancellable = true, require = 1)
    private void dnv$noBulletMovementAfterHit(CallbackInfo callback) {
        var bullet = (ProjectileEntity) (Object) this;
        if (bullet.isRemoved() && DnvTurretShells.managed(bullet)) callback.cancel();
    }

    @Inject(method = "m_8119_", at = @At("TAIL"), require = 1)
    private void dnv$finishBulletAtLoadedEdge(CallbackInfo callback) {
        if (dnv$terminalBulletStep) ((ProjectileEntity) (Object) this).discard();
    }
}
