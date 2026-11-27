package com.liucodex.doggiesnewvehicle.mission;

import java.util.Objects;
import java.util.function.Consumer;

/** Client sender port; the Forge transport installs its implementation. */
public final class DnvCombatUiClientGateway {
  private static Consumer<DnvCombatUi.Request> sender;

  private DnvCombatUiClientGateway() {}

  public static synchronized void install(Consumer<DnvCombatUi.Request> installed) {
    sender = Objects.requireNonNull(installed, "installed");
  }

  public static void send(DnvCombatUi.Request request) {
    Consumer<DnvCombatUi.Request> current = sender;
    if (current == null) {
      throw new IllegalStateException("combat UI transport is unavailable");
    }
    current.accept(Objects.requireNonNull(request, "request"));
  }
}
