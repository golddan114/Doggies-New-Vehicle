package com.liucodex.doggiesnewvehicle.client;

/** Pure responsive geometry shared by the crew-core screen and its offline preview. */
public final class DnvVehicleCrewCoreLayout {
  private static final int OUTER_MARGIN = 12;
  private static final int HEADER_HEIGHT = 34;
  private static final int FOOTER_HEIGHT = 34;
  private static final int ROW_HEIGHT = 24;
  private static final int WIDE_BREAKPOINT = 560;
  private static final int NAV_WIDTH = 180;
  private static final int COLUMN_GAP = 12;

  private DnvVehicleCrewCoreLayout() {}

  public static Geometry calculate(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("crew-core viewport is invalid");
    }
    boolean compactHeight = height < 190;
    int margin = compactHeight ? 4 : OUTER_MARGIN;
    int headerHeight = compactHeight ? 26 : HEADER_HEIGHT;
    int footerHeight = compactHeight ? 28 : FOOTER_HEIGHT;
    int panelWidth = Math.max(1, Math.min(760, width - margin * 2));
    int panelHeight = Math.max(1, height - margin * 2);
    Rect panel = new Rect(
        (width - panelWidth) / 2,
        (height - panelHeight) / 2,
        panelWidth,
        panelHeight);
    boolean wide = width >= WIDE_BREAKPOINT && panelWidth >= 520;
    int contentTop = panel.y() + Math.min(headerHeight, panel.height());
    int contentBottom = Math.max(contentTop, panel.bottom() - footerHeight);
    Rect detail;
    Rect coreTab;
    Rect stationsTab;
    if (wide) {
      detail = new Rect(
          panel.x() + NAV_WIDTH + COLUMN_GAP,
          contentTop,
          Math.max(1, panel.width() - NAV_WIDTH - COLUMN_GAP),
          Math.max(1, contentBottom - contentTop));
      coreTab = new Rect(panel.x() + 8, contentTop + 8, NAV_WIDTH - 16, 20);
      stationsTab = new Rect(panel.x() + 8, contentTop + 34, NAV_WIDTH - 16, 20);
    } else {
      int detailY = contentTop + 30;
      detail = new Rect(
          panel.x() + 8,
          detailY,
          Math.max(1, panel.width() - 16),
          Math.max(1, contentBottom - detailY));
      int tabWidth = Math.max(40, (panel.width() - 22) / 2);
      coreTab = new Rect(panel.x() + 8, contentTop + 4, tabWidth, 20);
      stationsTab = new Rect(panel.x() + 14 + tabWidth, contentTop + 4, tabWidth, 20);
    }

    int visibleRows = Math.max(0, (detail.height() - 38) / ROW_HEIGHT);
    int stationButtonWidth = Math.max(1, Math.min(
        Math.max(70, detail.width() / 2 - 12),
        Math.max(1, detail.width() - 16)));
    int stationButtonX = detail.right() - stationButtonWidth - 8;

    int footerY = Math.max(panel.y(), panel.bottom() - 27);
    int footerButtonWidth = Math.max(1, Math.min(
        Math.max(54, Math.min(80, (panel.width() - 24) / 2)),
        Math.max(1, (panel.width() - 20) / 2)));
    Rect save = new Rect(
        panel.right() - footerButtonWidth - 8,
        footerY,
        footerButtonWidth,
        Math.min(20, panel.bottom() - footerY));
    Rect cancel = new Rect(
        panel.right() - footerButtonWidth * 2 - 14,
        footerY,
        footerButtonWidth,
        Math.min(20, panel.bottom() - footerY));
    Rect status = new Rect(
        panel.x() + 8,
        Math.max(panel.y(), panel.bottom() - 22),
        Math.max(1, cancel.x() - panel.x() - 16),
        10);
    return new Geometry(
        width,
        height,
        wide,
        compactHeight,
        panel,
        detail,
        coreTab,
        stationsTab,
        save,
        cancel,
        status,
        visibleRows,
        stationButtonX,
        stationButtonWidth);
  }

  public record Geometry(
      int screenWidth,
      int screenHeight,
      boolean wide,
      boolean compactHeight,
      Rect panel,
      Rect detail,
      Rect coreTab,
      Rect stationsTab,
      Rect saveButton,
      Rect cancelButton,
      Rect statusText,
      int visibleStationRows,
      int stationButtonX,
      int stationButtonWidth) {
    public Geometry {
      Rect screen = new Rect(0, 0, screenWidth, screenHeight);
      if (!screen.contains(panel)
          || !panel.contains(coreTab)
          || !panel.contains(stationsTab)
          || !panel.contains(detail)
          || !panel.contains(saveButton)
          || !panel.contains(cancelButton)
          || !panel.contains(statusText)
          || visibleStationRows < 0) {
        throw new IllegalArgumentException("crew-core layout exceeds its owner");
      }
      for (int row = 0; row < visibleStationRows; row++) {
        Rect station = new Rect(
            stationButtonX,
            detail.y() + 28 + row * ROW_HEIGHT,
            stationButtonWidth,
            20);
        if (!detail.contains(station)) {
          throw new IllegalArgumentException("crew-core station row exceeds detail panel");
        }
      }
    }

    public Rect stationButton(int row) {
      if (row < 0 || row >= visibleStationRows) {
        throw new IllegalArgumentException("crew-core station row is invalid");
      }
      return new Rect(
          stationButtonX,
          detail.y() + 28 + row * ROW_HEIGHT,
          stationButtonWidth,
          20);
    }

    public Rect stationLabel(int row) {
      Rect button = stationButton(row);
      return new Rect(
          detail.x() + 8,
          detail.y() + 34 + row * ROW_HEIGHT,
          Math.max(1, button.x() - detail.x() - 14),
          19);
    }

    public Rect headingText(boolean withScrollSummary) {
      return new Rect(
          detail.x() + 8,
          detail.y() + 10,
          Math.max(1, detail.width() - (withScrollSummary ? 108 : 16)),
          10);
    }

    public Rect scrollText() {
      return new Rect(
          Math.max(detail.x() + 8, detail.right() - 92),
          detail.y() + 10,
          Math.min(84, Math.max(1, detail.width() - 16)),
          10);
    }

    public Rect coreHelpBounds() {
      return new Rect(
          detail.x() + 8,
          detail.y() + 48,
          Math.max(1, detail.width() - 16),
          Math.max(1, detail.height() - 52));
    }
  }

  public record Rect(int x, int y, int width, int height) {
    public Rect {
      if (width < 0 || height < 0) {
        throw new IllegalArgumentException("crew-core rectangle extent is negative");
      }
    }

    public int right() {
      return x + width;
    }

    public int bottom() {
      return y + height;
    }

    public boolean contains(Rect other) {
      return other.x >= x && other.y >= y
          && other.right() <= right() && other.bottom() <= bottom();
    }
  }
}
