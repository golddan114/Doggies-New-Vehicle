package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.projectile.JavelinMissileEntity;
import com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetPoint;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Native incoming Javelins use the same point as the lock display, including player-fired SBW missiles. */
@Mixin(value = JavelinMissileEntity.class, remap = false)
public abstract class HullTargetMissileMixin {
    @Redirect(method = "m_8119_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20185_()D"))
    private double dnv$targetX(Entity target) {
        return target instanceof DnvHullTarget ? DnvTargetPoint.center(target, 1).x : target.getX();
    }
    @Redirect(method = "m_8119_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20186_()D"))
    private double dnv$targetY(Entity target) {
        return target instanceof DnvHullTarget ? DnvTargetPoint.center(target, 1).y : target.getY();
    }
    @Redirect(method = "m_8119_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20189_()D"))
    private double dnv$targetZ(Entity target) {
        return target instanceof DnvHullTarget ? DnvTargetPoint.center(target, 1).z : target.getZ();
    }
    @Redirect(method = "m_8119_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20206_()F"))
    private float dnv$alreadyCentered(Entity target) {
        return target instanceof DnvHullTarget ? 0 : target.getBbHeight();
    }
}
