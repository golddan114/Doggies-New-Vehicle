package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.mission.DnvCombatUi;
import com.liucodex.doggiesnewvehicle.mission.DnvManagedVehicleDirectory;
import java.util.Objects;
import java.util.UUID;

/** Pure request gate preventing duplicate or late combat snapshots from changing a newer draft. */
final class DnvCombatUiCorrelation {
  static final int TIMEOUT_TICKS = 100;

  private DnvCombatUiCorrelation() {}

  static Decision evaluate(Cursor cursor, DnvCombatUi.View view) {
    Objects.requireNonNull(cursor, "cursor");
    Objects.requireNonNull(view, "view");
    if (!cursor.requestId().equals(view.requestId())) return Decision.REQUEST_MISMATCH;
    if (cursor.sequence() != view.sequence()) return Decision.SEQUENCE_MISMATCH;
    if (!cursor.selection().equals(view.selection())) return Decision.IDENTITY_MISMATCH;
    return Decision.ACCEPT;
  }

  static boolean timedOut(int ticks) {
    return ticks >= TIMEOUT_TICKS;
  }

  record Cursor(
      UUID requestId, long sequence, DnvManagedVehicleDirectory.Selection selection) {
    Cursor {
      Objects.requireNonNull(requestId, "requestId");
      Objects.requireNonNull(selection, "selection");
      if (sequence < 0L) throw new IllegalArgumentException("negative sequence");
    }
  }

  enum Decision { ACCEPT, REQUEST_MISMATCH, SEQUENCE_MISMATCH, IDENTITY_MISMATCH }
}
