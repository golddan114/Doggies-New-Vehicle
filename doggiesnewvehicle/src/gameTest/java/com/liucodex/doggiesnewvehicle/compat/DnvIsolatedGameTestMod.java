package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.init.DnvM1Content;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Route tests need no content; the optional RWS run registers its fixture only. */
@Mod("dnvgametest")
public final class DnvIsolatedGameTestMod {
  public DnvIsolatedGameTestMod(FMLJavaModLoadingContext context) {
    if (System.getProperty("dnv.rwsGameTest.report") == null) {
      return;
    }
    DnvM1Content.TYPES.register(context.getModEventBus());
    context.getModEventBus().addListener(DnvRemoteWeaponStationContent::register);
    MinecraftForge.EVENT_BUS.addListener(DnvM1RemoteCrew::entityInteract);
    MinecraftForge.EVENT_BUS.addListener(DnvRemoteWeaponCommands::logout);
    MinecraftForge.EVENT_BUS.addListener(DnvRemoteWeaponCommands::dimension);
    MinecraftForge.EVENT_BUS.addListener(DnvRemoteWeaponCommands::death);
    MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
      if (event.phase == TickEvent.Phase.END) {
        DnvRemoteWeaponServices.expire(event.getServer());
      }
    });
    MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
        DnvRemoteWeaponCommands.stopping(event));
  }
}
