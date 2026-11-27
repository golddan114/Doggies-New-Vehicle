package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DNV light AP never removes its impact block; native shells keep their own probability. */
@Mixin(value=SmallCannonShellEntity.class,remap=false)
public abstract class SmallCannonImpactMixin {
    @Inject(method="causeExplode(Lnet/minecraft/world/phys/Vec3;Z)V",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private void dnv$skipEmptyApBlast(net.minecraft.world.phys.Vec3 point, boolean entityHit, CallbackInfo callback) {
        if (com.liucodex.doggiesnewvehicle.compat.DnvTurretShells.emptyApBlast((SmallCannonShellEntity)(Object)this)) callback.cancel();
    }
    @Inject(method="afterHitEntity",at=@At("HEAD"),require=1,remap=false)
    private void dnv$directHe(net.minecraft.world.phys.EntityHitResult hit, CallbackInfo callback) {
        com.liucodex.doggiesnewvehicle.compat.DnvTurretShells.directHe((SmallCannonShellEntity)(Object)this,hit);
    }
    @Redirect(method="afterHitBlock",at=@At(value="INVOKE",target="Ljava/lang/Math;random()D"),require=1,allow=1,remap=false)
    private double dnv$apImpactChance() {
        return DnvBlockDamagePolicy.deterministicApImpact((Entity)(Object)this)?1:Math.random();
    }
}
