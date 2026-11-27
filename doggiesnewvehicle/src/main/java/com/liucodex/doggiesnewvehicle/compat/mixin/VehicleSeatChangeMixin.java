package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvVehicleCrewServices;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact custom-method hook for SBW seat changes that do not emit inventory dirty state. */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleSeatChangeMixin {
  @Inject(method = "changeSeat", at = @At("RETURN"), remap = false)
  private void dnv$vehicleSeatChanged(
      Entity passenger, int seatIndex, CallbackInfoReturnable<Boolean> callback) {
    if (callback.getReturnValueZ()) {
      DnvVehicleCrewServices.vehicleSeatChanged((VehicleEntity) (Object) this);
    }
  }
}
