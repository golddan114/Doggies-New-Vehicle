package com.liucodex.libertad.compat.sbw.mixin;

import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.liucodex.libertad.compat.sbw.NativeShells;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FastThrowableProjectile.class, remap = false)
abstract class ShellStepMixin {
    @Unique private boolean libertad$terminalStep;

    @Inject(method = "writeSpawnData", at = @At("RETURN"))
    private void libertad$writeTracer(FriendlyByteBuf buffer, CallbackInfo callback) {
        NativeShells.writeTracerSpawn((FastThrowableProjectile) (Object) this, buffer);
    }

    @Inject(method = "readSpawnData", at = @At("RETURN"))
    private void libertad$readTracer(FriendlyByteBuf buffer, CallbackInfo callback) {
        NativeShells.readTracerSpawn((FastThrowableProjectile) (Object) this, buffer);
    }

    @Inject(method = "m_8119_", at = @At("HEAD"), cancellable = true)
    private void libertad$clipBeforeNativeTrace(CallbackInfo callback) {
        libertad$terminalStep = false;
        libertad$clipStep((FastThrowableProjectile) (Object) this, callback);
    }

    @Inject(method = "m_6532_", at = @At("HEAD"), cancellable = true)
    private void libertad$admitNativeHit(HitResult hit, CallbackInfo callback) {
        var shell = (FastThrowableProjectile) (Object) this;
        if (!(shell.level() instanceof ServerLevel level) || !NativeShells.managed(shell)) return;
        if (!NativeShells.containsSegment(shell, shell.position(), hit.getLocation())
                || hit instanceof BlockHitResult block && !level.isPositionEntityTicking(block.getBlockPos())
                || hit instanceof EntityHitResult entity && !level.isPositionEntityTicking(entity.getEntity().blockPosition())) {
            shell.discard();
            callback.cancel();
        }
    }

    @Inject(method = "m_8119_", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/FastThrowableProjectile;m_6532_(Lnet/minecraft/world/phys/HitResult;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void libertad$clipAfterNativeHit(CallbackInfo callback) {
        var shell = (FastThrowableProjectile) (Object) this;
        if (!NativeShells.managed(shell) || shell.level().isClientSide) return;
        if (shell.isRemoved()) callback.cancel();
        else libertad$clipStep(shell, callback);
    }

    @Unique private void libertad$clipStep(FastThrowableProjectile shell, CallbackInfo callback) {
        int step = NativeShells.limitStep(shell);
        libertad$terminalStep |= step == 2;
        if (step == 1) {
            shell.discard();
            callback.cancel();
        }
    }

    @Inject(method = "m_8119_", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/FastThrowableProjectile;projectileMove(Lnet/minecraft/world/level/Level;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void libertad$removeAtEdge(CallbackInfo callback) {
        if (libertad$terminalStep) {
            ((FastThrowableProjectile) (Object) this).discard();
            callback.cancel();
        }
    }

    @Inject(method = "causeExplode", at = @At("HEAD"), cancellable = true)
    private void libertad$blastInsideActiveChunks(Vec3 position, CallbackInfo callback) {
        if (!NativeShells.blastAreaReady((FastThrowableProjectile) (Object) this, position)) callback.cancel();
    }
}
