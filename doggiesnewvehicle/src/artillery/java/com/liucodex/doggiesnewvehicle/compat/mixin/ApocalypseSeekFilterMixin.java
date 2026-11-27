package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.tools.SeekTool;
import com.atsuishio.superbwarfare.tools.VectorTool;
import com.atsuishio.superbwarfare.tools.VectorToolKt;
import com.liucodex.doggiesnewvehicle.armored.ApocalypseTargeting;
import com.liucodex.doggiesnewvehicle.armored.DnvApocalypseEntity;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNativeLockTargetFilter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Add the tank's requested target categories to the native seek query, including its native HUD candidates. */
@Mixin(value = SeekTool.Builder.class, remap = false)
public abstract class ApocalypseSeekFilterMixin {
    @Shadow @Final private Entity entity;

    @Inject(method = "baseFilter", at = @At("RETURN"), remap = false)
    private void dnv$apocalypseTargets(CallbackInfoReturnable<SeekTool.Builder> callback) {
        if (entity instanceof Player player && player.getVehicle() instanceof DnvApocalypseEntity tank && tank.getWeaponIndex(0) == 2) {
            callback.getReturnValue().custom(target -> ApocalypseTargeting.eligible(tank, player, target));
        } else if (entity instanceof Player player && player.getVehicle() instanceof DnvNativeLockTargetFilter vehicle && vehicle.nativeLockFilterActive()) {
            callback.getReturnValue().custom(target -> vehicle.admitsNativeLockTarget(player, target));
        }
    }

    @Inject(method = "withinAngle(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;D)Lcom/atsuishio/superbwarfare/tools/SeekTool$Builder;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void dnv$airframeAim(Vec3 origin, Vec3 direction, double degrees, CallbackInfoReturnable<SeekTool.Builder> callback) {
        if (origin == null || direction == null || !(entity instanceof Player player)
                || !(player.getVehicle() instanceof DnvApocalypseEntity tank) || tank.getWeaponIndex(0) != 2) return;
        var builder = (SeekTool.Builder) (Object) this;
        // SBW retains vehicle locks against the same projected center.
        callback.setReturnValue(builder.custom(target -> VectorToolKt.angleTo(direction,
                origin.vectorTo(VectorTool.lerpGetEntityBoundingBoxCenter(target, 1))) <= degrees));
    }
}
