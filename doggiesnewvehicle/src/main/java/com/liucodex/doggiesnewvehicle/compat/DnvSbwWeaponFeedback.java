package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore;
import com.liucodex.doggiesnewvehicle.core.DnvWeaponFireGate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded, change-driven projection of exact weapon gate failures into shared-core feedback. */
final class DnvSbwWeaponFeedback {
  private static final int HEARTBEAT_TICKS = 20;
  private static final int MAX_STATIONS = 32;
  private final Map<String, Stamp> lastByStation = new LinkedHashMap<>();

  void report(
      DnvAutonomyCore core,
      long gameTime,
      String stationId,
      List<String> weapons,
      List<DnvWeaponFireGate.Decision> gates,
      UUID targetId) {
    for (int index = 0; index < gates.size(); index++) {
      DnvWeaponFireGate.Decision gate = gates.get(index);
      if (gate.allowed()) {
        continue;
      }
      String weapon = index < weapons.size() ? weapons.get(index) : "unknown";
      String reason = gate.reason().name().toLowerCase(Locale.ROOT);
      Stamp previous = lastByStation.get(stationId);
      if (previous != null
          && previous.reason().equals(reason)
          && gameTime - previous.gameTime() < HEARTBEAT_TICKS) {
        return;
      }
      while (lastByStation.size() >= MAX_STATIONS
          && !lastByStation.containsKey(stationId)) {
        lastByStation.remove(lastByStation.keySet().iterator().next());
      }
      lastByStation.put(stationId, new Stamp(reason, gameTime));
      core.acceptFeedback(new DnvAutonomyCore.ExecutionFeedback(
          gameTime,
          boundedDomain(stationId, weapon),
          Optional.of(targetId),
          gate.outcome(),
          reason));
      return;
    }
  }

  void fired(String stationId) {
    lastByStation.remove(stationId);
  }

  void clear() {
    lastByStation.clear();
  }

  private static String boundedDomain(String stationId, String weapon) {
    String domain = stationId + "/" + weapon;
    return domain.length() <= DnvAutonomyCore.MAX_TEXT_LENGTH
        ? domain
        : domain.substring(0, DnvAutonomyCore.MAX_TEXT_LENGTH);
  }

  private record Stamp(String reason, long gameTime) {}
}
