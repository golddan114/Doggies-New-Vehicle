package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.Optional;
import java.util.UUID;

/** Per-screen request identity and expiry; no gameplay or persistent state. */
final class DnvManagedVehicleRequestGate {
  static final int REFRESH_TICKS = 20;
  static final int EXPIRY_TICKS = 60;
  private DnvManagedVehicleDirectory.Query pending;
  private int pendingTicks;
  private int age = EXPIRY_TICKS;

  DnvManagedVehicleDirectory.Query begin(
      Optional<DnvManagedVehicleDirectory.Selection> selection) {
    pending = new DnvManagedVehicleDirectory.Query(UUID.randomUUID(), selection);
    pendingTicks = 0;
    return pending;
  }

  boolean accept(DnvManagedVehicleDirectory.View view) {
    if (pending == null || !pending.requestId().equals(view.requestId())) {
      return false;
    }
    if (view.detail().isPresent()
        && !pending.selected().equals(view.detail().map(DnvManagedVehicleDirectory.Detail::identity))) {
      return false;
    }
    pending = null;
    age = 0;
    return true;
  }

  void tick() {
    age = Math.min(EXPIRY_TICKS, age + 1);
    if (pending != null && ++pendingTicks >= EXPIRY_TICKS) {
      pending = null;
    }
  }

  void invalidate() {
    pending = null;
    age = EXPIRY_TICKS;
  }

  boolean waiting() {
    return pending != null;
  }

  boolean refreshDue() {
    return !waiting() && age >= REFRESH_TICKS;
  }

  boolean expired() {
    return age >= EXPIRY_TICKS;
  }
}
