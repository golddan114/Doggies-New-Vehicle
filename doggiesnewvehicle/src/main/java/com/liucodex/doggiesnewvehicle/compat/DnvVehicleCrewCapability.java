package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Capability identity and mod-bus registration for entity-owned crew runtimes. */
@Mod.EventBusSubscriber(
    modid = DoggiesNewVehicle.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DnvVehicleCrewCapability {
  public static final Capability<DnvVehicleCrewAttachment> CAPABILITY =
      CapabilityManager.get(new CapabilityToken<>() {});

  private DnvVehicleCrewCapability() {}

  @SubscribeEvent
  public static void register(RegisterCapabilitiesEvent event) {
    event.register(DnvVehicleCrewAttachment.class);
  }
}
