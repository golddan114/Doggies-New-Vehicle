package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.tools.CustomExplosion;
import com.atsuishio.superbwarfare.tools.ParticleTool;
import com.liucodex.doggiesnewvehicle.compat.DnvProjectileBlastEffects;
import com.liucodex.doggiesnewvehicle.compat.DnvTurretShells;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CustomExplosion.Builder.class, remap = false)
public abstract class ProjectileBlastEffectsMixin {
    @Shadow private Entity directSource;
    @Shadow private float radius;

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true, require = 1)
    private void dnv$loadedBlastArea(CallbackInfo callback) {
        var point = ((CustomExplosion.Builder) (Object) this).getPosition();
        if (!DnvTurretShells.blastAreaReady(directSource, point, radius)) callback.cancel();
    }

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void dnv$blastPresentation(Entity source, CallbackInfo callback) {
        DnvProjectileBlastEffects.apply((CustomExplosion.Builder) (Object) this, source);
    }

    @Redirect(method = "explode", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/tools/ParticleTool;spawnExplosionParticles("
                    + "Lcom/atsuishio/superbwarfare/tools/ParticleTool$ParticleType;"
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;)V"), require = 1)
    private void dnv$particleFeedback(ParticleTool.ParticleType type, Level level, Vec3 point) {
        DnvProjectileBlastEffects.spawnExplosionParticles(directSource, type, level, point);
    }
}
