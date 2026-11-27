package com.liucodex.doggiesnewvehicle.client;

/** Pure GUI-scale-aware geometry for the current vehicle and target console. */
public final class DnvVehicleOperationsLayout {
  private static final int WIDE_THRESHOLD = 640;
  private static final int WIDE_HEADER_HEIGHT = 56;
  private static final int COMPACT_HEADER_HEIGHT = 38;
  private static final int WIDE_FOOTER_HEIGHT = 34;
  private static final int COMPACT_FOOTER_HEIGHT = 44;
  private static final int COMBAT_ACTION_COUNT = 4;

  private DnvVehicleOperationsLayout() {}

  public static Geometry calculate(int width, int height, double guiScale) {
    if (width < 240 || height < 160 || !Double.isFinite(guiScale) || guiScale < 1.0D) {
      throw new IllegalArgumentException("vehicle layout source geometry is invalid");
    }
    boolean wide = width >= WIDE_THRESHOLD;
    int margin = wide ? 12 : 6;
    int panelWidth = Math.min(wide ? 920 : width - margin * 2, width - margin * 2);
    int panelHeight = Math.min(wide ? 520 : height - margin * 2, height - margin * 2);
    int panelX = (width - panelWidth) / 2;
    int panelY = (height - panelHeight) / 2;
    int contentX = panelX + 8;
    int contentWidth = panelWidth - 16;
    int headerHeight = wide ? WIDE_HEADER_HEIGHT : COMPACT_HEADER_HEIGHT;
    int footerHeight = wide ? WIDE_FOOTER_HEIGHT : COMPACT_FOOTER_HEIGHT;
    int contentY = panelY + headerHeight;
    int contentHeight = Math.max(1, panelHeight - headerHeight - footerHeight);
    long sourceWidth = Math.round(width * guiScale);
    long sourceHeight = Math.round(height * guiScale);
    return new Geometry(
        wide, panelX, panelY, panelWidth, panelHeight,
        contentX, contentY, contentWidth, contentHeight, footerHeight,
        sourceWidth, sourceHeight);
  }

  public static int clampDetailScroll(int requested, int maximum) {
    return Math.max(0, Math.min(requested, Math.max(0, maximum)));
  }

  public record Geometry(
      boolean wide,
      int panelX,
      int panelY,
      int panelWidth,
      int panelHeight,
      int contentX,
      int contentY,
      int contentWidth,
      int contentHeight,
      int footerHeight,
      long sourcePixelWidth,
      long sourcePixelHeight) {
    public Geometry {
      if (panelX < 0 || panelY < 0 || panelWidth <= 0 || panelHeight <= 0
          || contentX < panelX || contentY < panelY
          || contentWidth <= 0 || contentHeight <= 0
          || footerHeight <= 0
          || sourcePixelWidth <= 0L || sourcePixelHeight <= 0L) {
        throw new IllegalArgumentException("vehicle layout exceeds screen bounds");
      }
      if (contentX + contentWidth > panelX + panelWidth
          || contentY + contentHeight > panelY + panelHeight
          || panelWidth < 96 || panelHeight < 32) {
        throw new IllegalArgumentException("vehicle child layout exceeds panel bounds");
      }
    }

    public Rect panelBounds() {
      return new Rect(panelX, panelY, panelWidth, panelHeight);
    }

    public Rect headerActionButton(int index) {
      if (index < 0 || index >= 4) {
        throw new IllegalArgumentException("vehicle header action index is out of range");
      }
      int gap = 4;
      int width = Math.min(136, Math.max(48, (panelWidth - 28) / 4));
      return new Rect(
          panelX + 8 + index * (width + gap), panelY + (wide ? 29 : 20),
          width, wide ? 20 : 16);
    }

    public Rect closeButton() {
      return new Rect(panelX + panelWidth - 72, panelY + (wide ? 6 : 2),
          64, wide ? 20 : 16);
    }

    public Rect contentBounds() {
      return new Rect(contentX, contentY, contentWidth, contentHeight);
    }

    public Rect statusCardBounds() {
      return new Rect(
          contentX + 4, contentY + 3, Math.max(1, contentWidth - 8), 26);
    }

    public Rect combatActionButton(int index) {
      if (index < 0 || index >= COMBAT_ACTION_COUNT) {
        throw new IllegalArgumentException("combat action index is out of range");
      }
      int left = panelX + 8;
      int usable = panelWidth - 16;
      int gap = 4;
      if (wide) {
        int cell = Math.max(1, (usable - gap * (COMBAT_ACTION_COUNT - 1))
            / COMBAT_ACTION_COUNT);
        return new Rect(left + index * (cell + gap), panelY + panelHeight - 28, cell, 20);
      }
      int column = index % 2;
      int row = index / 2;
      int cell = Math.max(1, (usable - gap) / 2);
      return new Rect(left + column * (cell + gap),
          panelY + panelHeight - footerHeight + 3 + row * 20, cell, 18);
    }

    public Rect vehicleListBounds() {
      Rect page = vehicleViewportBounds();
      if (wide) {
        int listWidth = Math.max(120, page.width() * 45 / 100);
        return new Rect(page.x(), page.y(), listWidth, page.height());
      }
      return new Rect(
          page.x(), page.y(), Math.max(1, page.width() * 42 / 100), page.height());
    }

    public Rect vehicleDetailBounds() {
      Rect page = vehicleViewportBounds();
      Rect list = vehicleListBounds();
      int x = list.right() + 8;
      return new Rect(x, page.y(), Math.max(1, page.right() - x), list.height());
    }

    public Rect vehicleViewportBounds() {
      Rect status = statusCardBounds();
      return new Rect(
          contentX + 4, status.bottom() + 4,
          Math.max(1, contentWidth - 8),
          Math.max(1, contentY + contentHeight - status.bottom() - 8));
    }

    public int visibleVehicleRows() {
      return Math.max(0, (vehicleListBounds().height() - 8) / (wide ? 22 : 20));
    }

    public int visibleVehicleDetailLines(int lineHeight) {
      if (lineHeight <= 0) {
        throw new IllegalArgumentException("line height must be positive");
      }
      return Math.max(0, (vehicleDetailBounds().height() - 8) / lineHeight);
    }

    public Rect vehicleRow(int index) {
      if (index < 0 || index >= visibleVehicleRows()) {
        throw new IllegalArgumentException("managed vehicle row is out of range");
      }
      Rect list = vehicleListBounds();
      return new Rect(
          list.x() + 4, list.y() + 4 + index * (wide ? 22 : 20),
          Math.max(1, list.width() - 8), wide ? 20 : 18);
    }

  }

  public record Rect(int x, int y, int width, int height) {
    public Rect {
      if (width < 0 || height < 0) {
        throw new IllegalArgumentException("vehicle rectangle extent is negative");
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

    public boolean intersects(Rect other) {
      return x < other.right() && right() > other.x
          && y < other.bottom() && bottom() > other.y;
    }
  }
}
