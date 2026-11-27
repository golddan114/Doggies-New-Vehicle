package com.liucodex.doggiesnewvehicle.mission;

/** Client-screen boundary implemented by the exact SBW tactical-map mixin. */
public interface DnvTacticalRouteView {
  void dnv$acceptRouteResponse(DnvTacticalRouteProtocol.RouteResponse response);
}
