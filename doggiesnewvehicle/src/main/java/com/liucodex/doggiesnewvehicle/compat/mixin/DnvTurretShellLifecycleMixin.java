package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.liucodex.doggiesnewvehicle.api.combat.DnvUpperAirspaceProjectile;
import com.liucodex.doggiesnewvehicle.compat.DnvTurretShells;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=FastThrowableProjectile.class,remap=false)
public abstract class DnvTurretShellLifecycleMixin {
    @Unique private boolean dnv$terminalStep;

    @Redirect(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_151558_()I"), require = 1)
    private int dnv$airspaceHitHeight(Level level) {
        var shell = (FastThrowableProjectile) (Object) this;
        int ceiling = level.getMaxBuildHeight();
        // The later ticket guard queries ServerLevel separately.
        return shell instanceof DnvUpperAirspaceProjectile guided && guided.admitsUpperAirspaceCollision()
                ? Math.max(ceiling, (int) shell.getY()) : ceiling;
    }

    @Inject(method="m_8119_",at=@At("HEAD"),cancellable=true,require=1)
    private void dnv$boundedStep(CallbackInfo callback) {
        var shell = (FastThrowableProjectile) (Object) this;
        dnv$terminalStep = false;
        if (DnvTurretShells.beforeTick(shell)) {
            callback.cancel();
            return;
        }
        dnv$clipStep(shell, callback);
    }

    @Inject(method = "m_8119_", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/FastThrowableProjectile;m_6532_(Lnet/minecraft/world/phys/HitResult;)V",
            shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void dnv$clipAfterNativePenetration(CallbackInfo callback) {
        var shell = (FastThrowableProjectile) (Object) this;
        if (!shell.isRemoved()) dnv$clipStep(shell, callback);
    }

    @Unique private void dnv$clipStep(FastThrowableProjectile shell, CallbackInfo callback) {
        double limit = DnvTurretShells.stepLimit(shell);
        if (limit == 0) {
            shell.discard();
            callback.cancel();
        } else if (limit < 1) {
            shell.setDeltaMovement(shell.getDeltaMovement().scale(limit));
            dnv$terminalStep = true;
        }
    }

    @Inject(method = "m_8119_", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/FastThrowableProjectile;projectileMove(Lnet/minecraft/world/level/Level;)V"),
            cancellable = true, require = 1)
    private void dnv$noMovementAfterHit(CallbackInfo callback) {
        var shell = (FastThrowableProjectile) (Object) this;
        if (shell.isRemoved() && DnvTurretShells.managed(shell)) callback.cancel();
    }

    @Inject(method = "m_8119_", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/FastThrowableProjectile;projectileMove(Lnet/minecraft/world/level/Level;)V",
            shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void dnv$finishAtLoadedEdge(CallbackInfo callback) {
        if (dnv$terminalStep) {
            ((FastThrowableProjectile) (Object) this).discard();
            callback.cancel();
        }
    }

    @Inject(method = "m_8119_", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/FastThrowableProjectile;updateManualTickRegistration()V"),
            cancellable = true, require = 1)
    private void dnv$noRemovedTickets(CallbackInfo callback) {
        var shell = (FastThrowableProjectile) (Object) this;
        if (shell.isRemoved() && DnvTurretShells.managed(shell)) callback.cancel();
    }
}
