package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.inventory.handler.VehicleContainerHandler;
import com.liucodex.doggiesnewvehicle.compat.DnvVehicleCrewServices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Immediate hook for every exact-SBW vehicle inventory mutation. */
@Mixin(value = VehicleContainerHandler.class, remap = false)
public abstract class VehicleContainerHandlerMixin {
  @Shadow(remap = false)
  public abstract VehicleEntity getVehicle();

  @Inject(method = "onContentsChanged", at = @At("TAIL"), remap = false)
  private void dnv$vehicleCrewCoreChanged(int slot, CallbackInfo callback) {
    DnvVehicleCrewServices.vehicleInventoryChanged(getVehicle());
  }
}
