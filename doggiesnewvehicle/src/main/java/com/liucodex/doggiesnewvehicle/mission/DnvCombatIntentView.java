package com.liucodex.doggiesnewvehicle.mission;

/** Current-screen projection target; delivery never opens or revives a screen. */
public interface DnvCombatIntentView {
  void dnv$acceptVehicleDirectory(DnvManagedVehicleDirectory.View view);

  void dnv$acceptCombat(DnvCombatUi.View view);

}
