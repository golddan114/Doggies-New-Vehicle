package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.tools.SeekTool;
import com.atsuishio.superbwarfare.tools.VectorTool;
import com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetPoint;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SeekTool.class, remap = false)
public abstract class HullTargetSeekMixin {
    @Inject(method = "calculateAngle(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)D",
            at = @At("HEAD"), cancellable = true)
    private static void dnv$seekCenter(Vec3 origin, Vec3 direction, Entity target, CallbackInfoReturnable<Double> callback) {
        if (target instanceof DnvHullTarget && origin != null && direction != null)
            callback.setReturnValue(VectorTool.calculateAngle(DnvTargetPoint.center(target, 1).subtract(origin), direction));
    }
    @Inject(method = "calculateAngle(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)D",
            at = @At("HEAD"), cancellable = true)
    private static void dnv$entitySeekCenter(Entity target, Entity seeker, CallbackInfoReturnable<Double> callback) {
        if (target instanceof DnvHullTarget)
            callback.setReturnValue(VectorTool.calculateAngle(DnvTargetPoint.center(target, 1).subtract(seeker.position()), seeker.getLookAngle()));
    }
}
