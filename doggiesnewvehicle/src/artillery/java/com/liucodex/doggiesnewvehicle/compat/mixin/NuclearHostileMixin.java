package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.atsuishio.superbwarfare.entity.vehicle.ai.TowerAI;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNuclearInterceptionTarget;
import com.liucodex.doggiesnewvehicle.compat.NuclearAntiMissile;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=TowerAI.TeamResolver.class,remap=false)
public abstract class NuclearHostileMixin {
    @Inject(method="isHostile(Lcom/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity;Lnet/minecraft/world/entity/Entity;)Z",
            at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private static void dnv$nuclearHostile(AutoAimableEntity tower,Entity target,CallbackInfoReturnable<Boolean> result){
        if(target instanceof DnvNuclearInterceptionTarget)result.setReturnValue(NuclearAntiMissile.hostile(tower,target));
    }
}
