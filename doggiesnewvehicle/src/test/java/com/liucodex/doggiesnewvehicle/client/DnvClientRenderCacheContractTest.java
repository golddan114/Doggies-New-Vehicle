package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DnvClientRenderCacheContractTest {
  @Test
  void stableCrewLabelsAndVehicleSelectionAreCachedOutsideRender() throws Exception {
    String crew = Files.readString(Path.of(
        "src/main/java/com/liucodex/doggiesnewvehicle/client/"
            + "DnvVehicleCrewCoreScreen.java"));
    String panel = Files.readString(Path.of(
        "src/main/java/com/liucodex/doggiesnewvehicle/client/"
            + "DnvManagedVehiclePanel.java"));

    assertTrue(crew.contains("private final List<Component> stationLabels"));
    assertTrue(crew.contains("private final Component coreHelp"));
    assertTrue(crew.contains("this.coreHelp = Component.translatable("));
    assertTrue(panel.contains("vehiclesById"));
    assertTrue(panel.contains("selectedVehicleId.map(vehiclesById::get)"));
  }
}
