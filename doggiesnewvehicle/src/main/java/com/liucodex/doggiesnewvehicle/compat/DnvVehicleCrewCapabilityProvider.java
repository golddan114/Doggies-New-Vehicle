package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

/** Non-persistent provider; the vehicle inventory item remains the persistence owner. */
public final class DnvVehicleCrewCapabilityProvider implements ICapabilityProvider {
  private final DnvVehicleCrewAttachment attachment;
  private final LazyOptional<DnvVehicleCrewAttachment> optional;

  public DnvVehicleCrewCapabilityProvider(VehicleEntity vehicle) {
    attachment = new DnvVehicleCrewAttachment(vehicle);
    optional = LazyOptional.of(() -> attachment);
  }

  @Override
  public <T> LazyOptional<T> getCapability(
      @Nonnull Capability<T> capability, @Nullable Direction side) {
    return capability == DnvVehicleCrewCapability.CAPABILITY
        ? optional.cast()
        : LazyOptional.empty();
  }

  public void invalidate() {
    attachment.clearOnUnload();
    optional.invalidate();
  }
}
