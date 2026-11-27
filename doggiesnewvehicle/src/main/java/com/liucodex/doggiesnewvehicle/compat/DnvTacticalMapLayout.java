package com.liucodex.doggiesnewvehicle.compat;

/** Bounded route controls for the exact Superb Warfare tactical-map UI injection. */
public final class DnvTacticalMapLayout {
  private static final int MINIMUM_CONTROL_WIDTH = 100;

  private DnvTacticalMapLayout() {}

  public static Geometry calculate(
      int screenWidth,
      int screenHeight,
      int mapLeft,
      int mapTop,
      int mapWidth,
      int mapHeight,
      int followButtonX,
      int followButtonY) {
    if (screenWidth <= 0 || screenHeight <= 0 || mapWidth < 0 || mapHeight < 0) {
      throw new IllegalArgumentException("tactical-map source geometry is invalid");
    }
    Rect screen = new Rect(0, 0, screenWidth, screenHeight);
    Rect map = new Rect(
        clamp(mapLeft, 0, screenWidth),
        clamp(mapTop, 0, screenHeight),
        Math.max(0, Math.min(mapWidth, screenWidth - clamp(mapLeft, 0, screenWidth))),
        Math.max(0, Math.min(mapHeight, screenHeight - clamp(mapTop, 0, screenHeight))));
    int firstX = followButtonX + 22;
    int available = map.right() - firstX;
    boolean controlsVisible = firstX >= 0
        && followButtonY >= 0
        && followButtonY + 64 <= screenHeight
        && available >= MINIMUM_CONTROL_WIDTH;
    Rect route;
    Rect apply;
    Rect undo;
    Rect cycleDraft;
    Rect newDraft;
    if (controlsVisible) {
      route = new Rect(firstX, followButtonY, Math.min(available, 146), 20);
      int firstWidth = (available - 2) / 2;
      int secondWidth = available - firstWidth - 2;
      apply = new Rect(firstX, followButtonY + 22, firstWidth, 20);
      undo = new Rect(apply.right() + 2, followButtonY + 22, secondWidth, 20);
      cycleDraft = new Rect(firstX, followButtonY + 44, firstWidth, 20);
      newDraft = new Rect(cycleDraft.right() + 2, followButtonY + 44, secondWidth, 20);
    } else {
      Rect hidden = new Rect(
          clamp(firstX, 0, screenWidth), clamp(followButtonY, 0, screenHeight), 0, 0);
      route = hidden;
      apply = hidden;
      undo = hidden;
      cycleDraft = hidden;
      newDraft = hidden;
    }
    if (!screen.contains(map)
        || !screen.contains(route)
        || !screen.contains(apply)
        || !screen.contains(undo)
        || !screen.contains(cycleDraft)
        || !screen.contains(newDraft)) {
      throw new IllegalArgumentException("tactical-map layout exceeds the screen");
    }
    return new Geometry(
        screen, map, route, apply, undo, cycleDraft, newDraft, controlsVisible);
  }

  private static int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  public record Geometry(
      Rect screen,
      Rect map,
      Rect routeButton,
      Rect applyButton,
      Rect undoButton,
      Rect cycleDraftButton,
      Rect newDraftButton,
      boolean controlsVisible) {}

  public record Rect(int x, int y, int width, int height) {
    public Rect {
      if (width < 0 || height < 0) {
        throw new IllegalArgumentException("tactical-map rectangle extent is negative");
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
