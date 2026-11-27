package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.compat.DnvTacticalMapLayout;
import com.liucodex.dchsuite.uipreview.UiPreviewReport;
import com.liucodex.dchsuite.uipreview.UiPreviewReport.Node;
import com.liucodex.dchsuite.uipreview.UiPreviewReport.Rect;
import com.liucodex.dchsuite.uipreview.UiPreviewReport.Scene;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class DnvAdditionalUiPreviewTest {
  @Test
  @Tag("ui-preview")
  void rendersCrewCoreAndTacticalMapFromProductionGeometry()
      throws Exception {
    List<Scene> scenes = new ArrayList<>();
    scenes.add(crewScene(1140, 621, 3.0D, false));
    scenes.add(crewScene(1140, 621, 3.0D, true));
    scenes.add(crewScene(480, 270, 4.0D, false));
    scenes.add(crewScene(480, 270, 4.0D, true));
    scenes.add(crewScene(320, 180, 4.0D, false));
    scenes.add(crewScene(320, 180, 4.0D, true));
    scenes.add(crewScene(240, 160, 4.0D, false));
    scenes.add(tacticalScene(
        1140, 621, 3.0D, 190, 42, 920, 520, 760, 16));
    scenes.add(tacticalScene(
        480, 270, 4.0D, 12, 34, 456, 220, 250, 8));
    scenes.add(tacticalScene(
        320, 180, 4.0D, 8, 30, 304, 142, 270, 6));
    scenes.add(tacticalRouteScene(
        1140, 621, 3.0D, 190, 42, 920, 520, 760, 16));
    scenes.add(tacticalRouteScene(
        480, 270, 4.0D, 12, 34, 456, 220, 250, 8));

    var palette = new UiPreviewReport.Palette("DnvUiColors", DnvUiColors.PANEL,
        DnvUiColors.SECTION, DnvUiColors.BORDER, DnvUiColors.SECTION,
        DnvUiColors.INPUT, DnvUiColors.TEXT, DnvUiColors.DISABLED, DnvUiColors.ACCENT);
    var crewResult = UiPreviewReport.write(
        Path.of("build", "reports", "ui-preview", "dnv-crew-core"), "DNV Crew Core",
        scenes.stream().filter(scene -> scene.id().startsWith("dnv-crew-")).toList(), palette);
    assertTrue(crewResult.violations().isEmpty(), crewResult.summary());
    UiPreviewReport.Result result = UiPreviewReport.write(
        Path.of("build", "reports", "ui-preview", "dnv-additional-ui"),
        "DNV Tactical Map Geometry Preview",
        scenes.stream().filter(scene -> !scene.id().startsWith("dnv-crew-")).toList());
    assertTrue(result.violations().isEmpty(), result.summary());
  }

  @Test
  void tacticalControlsFailClosedInsteadOfInventingMissingWidth() {
    DnvTacticalMapLayout.Geometry layout = DnvTacticalMapLayout.calculate(
        320, 180, 8, 30, 304, 142, 270, 6);
    assertFalse(layout.controlsVisible());
    assertTrue(layout.routeButton().width() == 0);
    assertTrue(layout.applyButton().width() == 0);
    assertTrue(layout.undoButton().width() == 0);
    assertTrue(layout.cycleDraftButton().width() == 0);
    assertTrue(layout.newDraftButton().width() == 0);
  }

  @Test
  void tacticalRouteControlsStayWithinMap() {
    DnvTacticalMapLayout.Geometry layout = DnvTacticalMapLayout.calculate(
        480, 270, 12, 34, 456, 220, 250, 8);
    assertTrue(layout.controlsVisible());
    assertTrue(layout.routeButton().width() > 0);
    assertTrue(layout.routeButton().x() == 272);
    assertTrue(layout.routeButton().right() <= layout.map().right());
    assertTrue(layout.undoButton().x() >= layout.applyButton().right() + 2);
    assertTrue(layout.newDraftButton().x() >= layout.cycleDraftButton().right() + 2);
    assertTrue(layout.undoButton().right() <= layout.map().right());
    assertTrue(layout.newDraftButton().right() <= layout.map().right());
  }

  @Test
  void compactCrewCoreKeepsBothPagesAndActionsInsideViewport() {
    for (int[] size : new int[][] {{240, 160}, {320, 180}}) {
      DnvVehicleCrewCoreLayout.Geometry layout = DnvVehicleCrewCoreLayout.calculate(size[0], size[1]);
      assertTrue(layout.panel().contains(layout.coreTab()));
      assertTrue(layout.panel().contains(layout.stationsTab()));
      assertTrue(layout.panel().contains(layout.detail()));
      assertTrue(layout.detail().contains(layout.coreHelpBounds()));
      assertTrue(layout.panel().contains(layout.saveButton()));
      assertTrue(layout.panel().contains(layout.cancelButton()));
    }
  }

  private static Scene crewScene(
      int width,
      int height,
      double guiScale,
      boolean stations) {
    DnvVehicleCrewCoreLayout.Geometry layout =
        DnvVehicleCrewCoreLayout.calculate(width, height);
    List<Node> nodes = new ArrayList<>();
    nodes.add(Node.panel("panel", rect(layout.panel())));
    nodes.add(Node.region("detail", rect(layout.detail()), "panel"));
    nodes.add(Node.button(
        "core-tab", rect(layout.coreTab()), "载具核心", "panel"));
    nodes.add(Node.button(
        "stations-tab", rect(layout.stationsTab()), "武器站", "panel"));
    nodes.add(Node.button(
        "save", rect(layout.saveButton()), "保存", "panel"));
    nodes.add(Node.button(
        "cancel", rect(layout.cancelButton()), "取消", "panel"));
    nodes.add(Node.text(
        "title",
        new Rect(layout.panel().x() + 8, layout.panel().y() + 12,
            Math.max(1, layout.panel().width() - 16), 10),
        "DNV 载具乘员核心",
        "panel",
        true));
    nodes.add(Node.text(
        "status",
        rect(layout.statusText()),
        "保存被拒绝：STALE_REVISION；草稿已保留，可刷新后恢复。",
        "panel",
        true));
    if (stations) {
      boolean scroll = layout.visibleStationRows() < 8;
      nodes.add(Node.text(
          "station-heading", rect(layout.headingText(scroll)),
          "武器站（8）", "detail", true));
      if (scroll) {
        nodes.add(Node.text(
            "station-scroll", rect(layout.scrollText()),
            "1 / 8", "detail", true));
      }
      for (int row = 0; row < layout.visibleStationRows(); row++) {
        DnvVehicleCrewCoreLayout.Rect label = layout.stationLabel(row);
        nodes.add(Node.text(
            "station-label-" + row,
            new Rect(label.x(), label.y(), label.width(), 9),
            "武器站 " + (row + 1),
            "detail",
            true));
        nodes.add(Node.text(
            "station-role-" + row,
            new Rect(label.x(), label.y() + 10, label.width(), 9),
            "AI 可控；真人站位拥有者始终优先",
            "detail",
            true));
        nodes.add(Node.button(
            "station-button-" + row,
            rect(layout.stationButton(row)),
            "仅在确认目标后开火",
            "detail"));
      }
    } else {
      nodes.add(Node.text(
          "vehicle", rect(layout.headingText(false)),
          "已绑定载具：superbwarfare:bradley · exact binding",
          "detail",
          true));
      nodes.add(Node.text(
          "core-heading",
          new Rect(layout.detail().x() + 8, layout.detail().y() + 30,
              Math.max(1, layout.detail().width() - 16), 10),
          "控制范围与装配",
          "detail",
          true));
      nodes.add(Node.text(
          "core-help",
          rect(layout.coreHelpBounds()),
          "此核心不接管平台驾驶或移动；航线编辑独立保留。这里仅编辑武器站，保存后将同一核心装入载具。",
          "detail",
          true));
    }
    String id = String.format(
        Locale.ROOT,
        "dnv-crew-%s-%dx%d-scale%d",
        stations ? "stations" : "core",
        Math.round(width * guiScale),
        Math.round(height * guiScale),
        Math.round(guiScale));
    return new Scene(
        id,
        "DNV 载具核心 · " + (stations ? "武器站" : "核心"),
        width,
        height,
        guiScale,
        nodes);
  }

  private static Scene tacticalScene(
      int width,
      int height,
      double guiScale,
      int mapLeft,
      int mapTop,
      int mapWidth,
      int mapHeight,
      int followX,
      int followY) {
    DnvTacticalMapLayout.Geometry layout = DnvTacticalMapLayout.calculate(
        width, height, mapLeft, mapTop, mapWidth, mapHeight, followX, followY);
    List<Node> nodes = new ArrayList<>();
    nodes.add(Node.panel("screen", new Rect(0, 0, width, height)));
    nodes.add(Node.region("map", rect(layout.map()), "screen"));
    if (layout.controlsVisible()) {
      nodes.add(Node.button(
          "route-unbound", rect(layout.routeButton()), "DNV 航线", "screen"));
    }
    String id = String.format(
        Locale.ROOT,
        "dnv-tactical-%dx%d-scale%d-%s",
        Math.round(width * guiScale),
        Math.round(height * guiScale),
        Math.round(guiScale),
        layout.controlsVisible() ? "controls" : "fail-closed");
    return new Scene(
        id,
        "DNV 战术地图 · "
            + (layout.controlsVisible() ? "航线待绑定" : "空间不足时关闭控件"),
        width,
        height,
        guiScale,
        nodes);
  }

  private static Scene tacticalRouteScene(
      int width,
      int height,
      double guiScale,
      int mapLeft,
      int mapTop,
      int mapWidth,
      int mapHeight,
      int followX,
      int followY) {
    DnvTacticalMapLayout.Geometry layout = DnvTacticalMapLayout.calculate(
        width, height, mapLeft, mapTop, mapWidth, mapHeight, followX, followY);
    List<Node> nodes = new ArrayList<>();
    nodes.add(Node.panel("screen", new Rect(0, 0, width, height)));
    nodes.add(Node.region("map", rect(layout.map()), "screen"));
    nodes.add(Node.button("route", rect(layout.routeButton()), "关闭航线", "screen"));
    nodes.add(Node.button("apply", rect(layout.applyButton()), "保存", "screen"));
    nodes.add(Node.button("undo", rect(layout.undoButton()), "撤销", "screen"));
    nodes.add(Node.button("cycle", rect(layout.cycleDraftButton()), "切换草稿", "screen"));
    nodes.add(Node.button("new", rect(layout.newDraftButton()), "新建草稿", "screen"));
    return new Scene(
        String.format(Locale.ROOT, "dnv-tactical-route-%dx%d-scale%d",
            Math.round(width * guiScale), Math.round(height * guiScale), Math.round(guiScale)),
        "DNV 战术地图航线 · 仅已绑定草稿可编辑",
        width, height, guiScale, nodes);
  }

  private static Rect rect(DnvVehicleCrewCoreLayout.Rect value) {
    return new Rect(value.x(), value.y(), value.width(), value.height());
  }

  private static Rect rect(DnvTacticalMapLayout.Rect value) {
    return new Rect(value.x(), value.y(), value.width(), value.height());
  }

}
