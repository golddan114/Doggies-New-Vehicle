package com.liucodex.libertad.compat.sbw.mixin;

import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.liucodex.libertad.client.ShellTracer;
import com.liucodex.libertad.compat.sbw.NativeShells;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FastThrowableProjectile.class, remap = false)
abstract class ClientShellTrailMixin {
    @Unique private Vec3 libertad$tracerStart;

    @Inject(method = "projectileMove", at = @At("HEAD"))
    private void libertad$beforeTracerMove(Level level, CallbackInfo callback) {
        var shell = (FastThrowableProjectile) (Object) this;
        if (level instanceof ClientLevel && NativeShells.hasClientTracer(shell) && !shell.isInWater())
            libertad$tracerStart = shell.position();
    }

    @Inject(method = "projectileMove", at = @At("RETURN"))
    private void libertad$afterTracerMove(Level level, CallbackInfo callback) {
        Vec3 start = libertad$tracerStart;
        libertad$tracerStart = null;
        if (start == null || !(level instanceof ClientLevel client)) return;
        var shell = (FastThrowableProjectile) (Object) this;
        if (!shell.isRemoved() && !shell.isInWater()) ShellTracer.accept(client, start, shell.position());
    }

    @Inject(method = "shellTrail", at = @At("HEAD"), cancellable = true)
    private void libertad$replaceShellSmoke(CallbackInfo callback) {
        if (NativeShells.hasClientTracer((FastThrowableProjectile) (Object) this)) callback.cancel();
    }
}
