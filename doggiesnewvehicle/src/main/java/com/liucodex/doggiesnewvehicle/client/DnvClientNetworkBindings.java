package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewClientProjection;
import com.liucodex.doggiesnewvehicle.mission.DnvCombatUiClientProjection;
import com.liucodex.doggiesnewvehicle.mission.DnvCombatIntentView;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleClientProjection;
import net.minecraft.client.Minecraft;

/** Binds incoming projections during client setup; screen lookup happens on delivery. */
final class DnvClientNetworkBindings {
  private DnvClientNetworkBindings() {}

  static void install() {
    DnvManagedVehicleClientProjection.install(
        () -> Minecraft.getInstance().setScreen(new DnvVehicleOperationsScreen()),
        view -> {
          if (Minecraft.getInstance().screen instanceof DnvCombatIntentView screen) {
            screen.dnv$acceptVehicleDirectory(view);
          }
        });
    DnvVehicleCrewClientProjection.install(
        view -> Minecraft.getInstance().setScreen(new DnvVehicleCrewCoreScreen(view)),
        response -> {
          if (Minecraft.getInstance().screen instanceof DnvVehicleCrewCoreScreen screen) {
            screen.handleResponse(
                response.token(), response.accepted(), response.issue(),
                response.savedFlightModeName(), response.savedStationIds(),
                response.savedStationModeNames());
          }
        });
    DnvCombatUiClientProjection.install(view -> {
      if (Minecraft.getInstance().screen instanceof DnvCombatIntentView screen) {
        screen.dnv$acceptCombat(view);
      }
    });
  }
}
