package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.liucodex.doggiesnewvehicle.compat.DnvExplosionExposure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The vanilla sampler is also called by older SBW custom explosions. Only DNV hulls opt in. */
@Mixin(value = Explosion.class, remap = false)
public abstract class HullExplosionExposureMixin {
    @Inject(method = {"getSeenPercent(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)F",
            "m_46064_(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)F"},
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void dnv$hullExposure(Vec3 origin, Entity target, CallbackInfoReturnable<Float> callback) {
        float exposure = DnvExplosionExposure.exposure(origin, target);
        if (exposure >= 0) callback.setReturnValue(exposure);
    }
}
