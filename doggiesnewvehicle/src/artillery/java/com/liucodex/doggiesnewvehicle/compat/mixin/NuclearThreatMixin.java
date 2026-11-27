package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNuclearInterceptionTarget;
import com.liucodex.doggiesnewvehicle.compat.NuclearAntiMissile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=AutoAimableEntity.class,remap=false)
public abstract class NuclearThreatMixin {
    @Inject(method="isThreateningEntity(Lnet/minecraft/world/entity/Entity;DLnet/minecraft/world/phys/Vec3;)Z",
            at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void dnv$nuclearThreat(Entity target,double size,Vec3 origin,CallbackInfoReturnable<Boolean> result){
        if(target instanceof DnvNuclearInterceptionTarget)
            result.setReturnValue(NuclearAntiMissile.threatening((AutoAimableEntity)(Object)this,target,size,origin));
    }
}
