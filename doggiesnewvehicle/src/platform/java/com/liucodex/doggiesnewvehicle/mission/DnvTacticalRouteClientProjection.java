package com.liucodex.doggiesnewvehicle.mission;

import java.util.Objects;
import java.util.function.Consumer;

/** Client-installed projection callbacks; owns no world, screen, or queued state. */
public final class DnvTacticalRouteClientProjection {
  private static volatile Consumer<DnvTacticalRouteProtocol.RouteResponse> handler =
      value -> {};

  private DnvTacticalRouteClientProjection() {}

  /** Publishes one complete callback set; the client installs it before connecting. */
  public static void install(
      Consumer<DnvTacticalRouteProtocol.RouteResponse> response) {
    handler = Objects.requireNonNull(response, "response");
  }

  public static void response(DnvTacticalRouteProtocol.RouteResponse response) {
    handler.accept(Objects.requireNonNull(response, "response"));
  }
}
