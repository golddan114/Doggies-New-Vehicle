package com.liucodex.doggiesnewvehicle.mission;

import java.util.Objects;
import java.util.function.Consumer;

/** Client response port; screen lookup remains client-owned. */
public final class DnvCombatUiClientProjection {
  private static Consumer<DnvCombatUi.View> sink;

  private DnvCombatUiClientProjection() {}

  public static synchronized void install(Consumer<DnvCombatUi.View> installed) {
    sink = Objects.requireNonNull(installed, "installed");
  }

  public static void accept(DnvCombatUi.View view) {
    Consumer<DnvCombatUi.View> current = sink;
    if (current != null) {
      current.accept(Objects.requireNonNull(view, "view"));
    }
  }
}
