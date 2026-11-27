package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.tools.VectorTool;
import com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetPoint;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VectorTool.class, remap = false)
public abstract class HullTargetProjectionMixin {
    @Inject(method = "lerpGetEntityBoundingBoxCenter", at = @At("HEAD"), cancellable = true)
    private static void dnv$hullCenter(Entity entity, float partialTick, CallbackInfoReturnable<Vec3> callback) {
        if (entity instanceof DnvHullTarget) callback.setReturnValue(DnvTargetPoint.center(entity, partialTick));
    }
}
