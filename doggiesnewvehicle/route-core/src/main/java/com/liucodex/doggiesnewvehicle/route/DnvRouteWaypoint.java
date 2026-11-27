package com.liucodex.doggiesnewvehicle.route;

/** Three-dimensional tactical-map draft value. */
public record DnvRouteWaypoint(double x, double y, double z) {
  public static final int MAX_WAYPOINTS = 32;

  public boolean isFinite() {
    return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
  }
}
