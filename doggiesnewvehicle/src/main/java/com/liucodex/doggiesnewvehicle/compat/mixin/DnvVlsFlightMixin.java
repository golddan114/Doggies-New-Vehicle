package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.projectile.JavelinMissileEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvTurretShells;
import com.liucodex.doggiesnewvehicle.compat.DnvVlsFlight;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JavelinMissileEntity.class, remap = false)
public abstract class DnvVlsFlightMixin {
    @Inject(method = "m_8119_", at = @At("HEAD"), cancellable = true)
    private void dnv$discardLostTarget(CallbackInfo callback) {
        var missile = (JavelinMissileEntity) (Object) this;
        if (DnvTurretShells.beforeTick(missile) || DnvVlsFlight.beforeNativeTick(missile)) callback.cancel();
    }

    @Inject(method = "m_8119_", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/MissileProjectile;m_8119_()V",
            shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void dnv$skipRemovedGuidance(CallbackInfo callback) {
        var missile = (JavelinMissileEntity) (Object) this;
        if (missile.isRemoved() && DnvTurretShells.managed(missile)) callback.cancel();
    }

    @Redirect(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lcom/atsuishio/superbwarfare/entity/projectile/JavelinMissileEntity;turn(Lnet/minecraft/world/phys/Vec3;F)V"))
    private void dnv$singleSteering(JavelinMissileEntity missile, Vec3 direction, float speed) {
        if (!DnvVlsFlight.active(missile)) missile.turn(direction, speed);
    }
    @Inject(method = "m_8119_", at = @At("TAIL"))
    private void dnv$intercept(CallbackInfo callback) {
        DnvVlsFlight.tick((JavelinMissileEntity) (Object) this);
    }
}
