package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DnvVehicleOperationsLayoutTest {
  @Test
  void smallestCompactConsoleKeepsVehicleAndTargetSelectionReachable() {
    for (int[] size : new int[][] {{240, 160}, {320, 180}, {480, 270}}) {
      var geometry = DnvVehicleOperationsLayout.calculate(size[0], size[1], 2.0D);
      assertFalse(geometry.wide());
      assertTrue(geometry.visibleVehicleRows() >= 1);
      assertTrue((geometry.vehicleDetailBounds().height() - 6) / 20 >= 1);
      assertTrue(geometry.vehicleViewportBounds().contains(geometry.vehicleListBounds()));
      assertTrue(geometry.vehicleViewportBounds().contains(geometry.vehicleDetailBounds()));
      assertFalse(geometry.vehicleListBounds().intersects(geometry.vehicleDetailBounds()));
      assertFalse(geometry.closeButton().intersects(geometry.headerActionButton(3)));
      for (int row = 0; row < geometry.visibleVehicleRows(); row++) {
        assertTrue(geometry.vehicleListBounds().contains(geometry.vehicleRow(row)));
      }
      for (int index = 0; index < 4; index++) {
        assertTrue(geometry.panelBounds().contains(geometry.combatActionButton(index)));
        assertFalse(geometry.combatActionButton(index).intersects(geometry.contentBounds()));
      }
    }
  }

  @Test
  void longWeaponDetailsCanReachTheirLastLineWithinNarrowAndWideViewports() {
    for (int[] size : new int[][] {{240, 160}, {480, 270}, {640, 220}, {960, 540}}) {
      var geometry = DnvVehicleOperationsLayout.calculate(size[0], size[1], 2.0D);
      assertTrue(geometry.vehicleViewportBounds().contains(geometry.vehicleListBounds()));
      assertTrue(geometry.vehicleViewportBounds().contains(geometry.vehicleDetailBounds()));
      for (int row = 0; row < geometry.visibleVehicleRows(); row++) {
        assertTrue(geometry.vehicleListBounds().contains(geometry.vehicleRow(row)));
      }
      int visible = geometry.visibleVehicleDetailLines(9);
      if (visible > 0) {
        int totalWrappedLines = 300;
        int scroll = DnvVehicleOperationsLayout.clampDetailScroll(Integer.MAX_VALUE, totalWrappedLines - visible);
        assertEquals(totalWrappedLines, scroll + visible);
        var detail = geometry.vehicleDetailBounds();
        var lastLine = new DnvVehicleOperationsLayout.Rect(
            detail.x() + 4, detail.y() + 4 + (visible - 1) * 9, detail.width() - 8, 9);
        assertTrue(detail.contains(lastLine));
      }
    }
  }

  @Test
  void guiScaleTwoPreservesVehicleAndCombatGeometry() {
    DnvVehicleOperationsLayout.Geometry geometry = DnvVehicleOperationsLayout.calculate(960, 540, 2.0D);
    assertTrue(geometry.wide());
    assertEquals(1920L, geometry.sourcePixelWidth());
    assertTrue(geometry.contentBounds().contains(geometry.statusCardBounds()));
    for (int index = 0; index < 4; index++) {
      assertTrue(geometry.panelBounds().contains(geometry.combatActionButton(index)));
      for (int other = index + 1; other < 4; other++) {
        assertFalse(geometry.combatActionButton(index).intersects(
            geometry.combatActionButton(other)));
      }
    }
    for (int index = 0; index < 4; index++) {
      assertTrue(geometry.panelBounds().contains(geometry.headerActionButton(index)));
      for (int other = index + 1; other < 4; other++) {
        assertFalse(geometry.headerActionButton(index).intersects(
            geometry.headerActionButton(other)));
      }
    }
    assertTrue(geometry.panelBounds().contains(geometry.closeButton()));
    assertTrue(geometry.vehicleViewportBounds().contains(
        geometry.vehicleListBounds()));
    assertTrue(geometry.vehicleViewportBounds().contains(
        geometry.vehicleDetailBounds()));
    assertFalse(geometry.vehicleListBounds().intersects(
        geometry.vehicleDetailBounds()));
  }

  @Test
  void guiScaleFourKeepsCompactVehicleAndCombatControlsReachable() {
    DnvVehicleOperationsLayout.Geometry geometry = DnvVehicleOperationsLayout.calculate(480, 270, 4.0D);
    assertFalse(geometry.wide());
    assertEquals(1920L, geometry.sourcePixelWidth());
    for (int index = 0; index < 4; index++) {
      assertTrue(geometry.panelBounds().contains(geometry.combatActionButton(index)));
      for (int other = index + 1; other < 4; other++) {
        assertFalse(geometry.combatActionButton(index).intersects(
            geometry.combatActionButton(other)));
      }
    }
    for (int index = 0; index < 4; index++) {
      assertTrue(geometry.panelBounds().contains(geometry.headerActionButton(index)));
      for (int other = index + 1; other < 4; other++) {
        assertFalse(geometry.headerActionButton(index).intersects(
            geometry.headerActionButton(other)));
      }
    }
    assertTrue(geometry.vehicleViewportBounds().contains(
        geometry.vehicleListBounds()));
    assertTrue(geometry.vehicleViewportBounds().contains(
        geometry.vehicleDetailBounds()));
    assertFalse(geometry.vehicleListBounds().intersects(
        geometry.vehicleDetailBounds()));
  }

}
