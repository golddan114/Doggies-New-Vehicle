package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.Mod;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvSbwShotExecution;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only explicitly scoped DNV submissions change; ordinary native/manual calls pass through. */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleQueuedShotMixin {
  @Redirect(method = "vehicleShoot(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;Ljava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V",
      at = @At(value = "INVOKE", target = "Lcom/atsuishio/superbwarfare/Mod$Companion;queueServerWork(ILjava/lang/Runnable;)Z"), remap = false)
  private boolean dnv$queueShot(Mod.Companion companion, int delay, Runnable work) {
    var vehicle = (VehicleEntity) (Object) this;
    Runnable guarded = vehicle instanceof com.liucodex.doggiesnewvehicle.core.DnvNativeRemoteShotGuard remote
        ? remote.guardNativeShot(work) : work;
    boolean queued = DnvSbwShotExecution.queue(vehicle, delay, guarded, companion::queueServerWork);
    if (vehicle instanceof com.liucodex.doggiesnewvehicle.core.DnvNativeRemoteShotGuard remote)
      remote.onNativeShotQueueResult(queued);
    return queued;
  }

  @Inject(method = "vehicleShoot(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;Ljava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V",
      at = @At(value = "INVOKE", target = "Lcom/atsuishio/superbwarfare/Mod$Companion;queueServerWork(ILjava/lang/Runnable;)Z",
          shift = At.Shift.AFTER), cancellable = true, remap = false)
  private void dnv$deferEffects(LivingEntity shooter, String weapon, UUID target, Vec3 point, CallbackInfo callback) {
    var vehicle = (VehicleEntity) (Object) this;
    if (DnvSbwShotExecution.submitting(vehicle)
        || vehicle instanceof com.liucodex.doggiesnewvehicle.core.DnvNativeRemoteShotGuard host
            && host.deferNativeShotEffects()) callback.cancel();
  }

  @Redirect(method = "vehicleShoot$lambda$28$lambda$27(Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;"
      + "Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;Ljava/util/UUID;"
      + "Lnet/minecraft/world/phys/Vec3;Lcom/atsuishio/superbwarfare/data/gun/GunData;)V",
      at = @At(value = "INVOKE",
          target = "Lcom/atsuishio/superbwarfare/data/gun/GunData;shoot(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)V"), remap = false)
  private static void dnv$validateParameters(GunData data, ShootParameters parameters) {
    DnvSbwShotExecution.shoot(data, parameters);
  }
}
