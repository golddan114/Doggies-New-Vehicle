package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.tools.SeekTool;
import com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetPoint;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Exact 0.8.9.1/0.8.9.2 visibility lambda; preserve the seeker's own eye and all ordinary targets. */
@Mixin(value = SeekTool.Builder.class, remap = false)
public abstract class HullTargetSightMixin {
    @Redirect(method = "noClip$lambda$52$lambda$51", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;m_146892_()Lnet/minecraft/world/phys/Vec3;", ordinal = 1))
    private static Vec3 dnv$visibleCenter(Entity target) {
        return target instanceof DnvHullTarget ? DnvTargetPoint.center(target, 1) : target.getEyePosition();
    }
}
