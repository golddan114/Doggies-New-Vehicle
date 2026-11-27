package com.liucodex.doggiesnewvehicle.mission;

import java.util.Objects;
import java.util.function.Consumer;

/** Client callbacks for the independent vehicle operations channel. */
public final class DnvManagedVehicleClientProjection {
  private static volatile Handlers handlers = new Handlers(() -> {}, view -> {});

  private DnvManagedVehicleClientProjection() {}

  public static void install(Runnable open, Consumer<DnvManagedVehicleDirectory.View> directory) {
    handlers = new Handlers(open, directory);
  }

  public static void open() {
    handlers.open().run();
  }

  public static void directory(DnvManagedVehicleDirectory.View view) {
    handlers.directory().accept(view);
  }

  private record Handlers(Runnable open, Consumer<DnvManagedVehicleDirectory.View> directory) {
    private Handlers {
      Objects.requireNonNull(open, "open");
      Objects.requireNonNull(directory, "directory");
    }
  }
}
