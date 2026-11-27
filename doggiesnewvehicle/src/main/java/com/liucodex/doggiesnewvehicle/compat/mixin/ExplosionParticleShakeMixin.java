package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.tools.ParticleTool;
import com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ParticleTool.class, remap = false)
public abstract class ExplosionParticleShakeMixin {
    @Inject(method = "playExplosionShake", at = @At("HEAD"), cancellable = true, require = 1)
    private void dnv$particleShake(ParticleTool.ParticleType type, ServerLevel level, Vec3 point, CallbackInfo callback) {
        if (DnvProjectileBlastEffects.particleShakeSuppressed()) callback.cancel();
    }
}
