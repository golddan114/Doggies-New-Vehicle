package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liucodex.doggiesnewvehicle.core.DnvCandidateProbeOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvNoiseProbeBudgetTest {
  @Test
  void rotatingOrderReachesEveryCandidateIncludingTheLastOne() {
    List<Integer> candidates = new ArrayList<>();
    for (int index = 0; index < 32; index++) candidates.add(index);
    for (int cursor = 0; cursor < 32; cursor++) {
      List<Integer> order = DnvCandidateProbeOrder.startingAt(candidates, cursor);
      assertEquals(cursor, order.get(0));
      assertEquals(32, order.stream().distinct().count());
    }
    assertTrue(DnvCandidateProbeOrder.startingAt(List.of(), 100).isEmpty());
  }

  @Test
  void fiftyVehiclesWithSixStationsAndDenseCandidatesReachTheClearTailWithinQuota() {
    final int owners = 50 * 6;
    List<Integer> candidates = new ArrayList<>();
    for (int index = 0; index < 32; index++) candidates.add(index);
    DnvTargetingBudget.ScanLedger ledger = new DnvTargetingBudget.ScanLedger(
        DnvTargetingBudget.MAX_GEOMETRY_CHECKS_PER_LEVEL_TICK);
    int[] cursor = new int[owners];
    boolean[] found = new boolean[owners];
    int completed = 0;
    for (int tick = 0; tick < 340 && completed < owners; tick++) {
      for (int station = 0; station < owners; station++) {
        if (found[station]) continue;
        for (int target : DnvCandidateProbeOrder.startingAt(candidates, cursor[station])) {
          if (!ledger.admit(tick, new UUID(0L, station + 1L))) break;
          cursor[station]++;
          if (target == 31) {
            found[station] = true;
            completed++;
          }
          break; // one exploratory line check per station and tick
        }
      }
      assertTrue(ledger.used() <= DnvTargetingBudget.MAX_GEOMETRY_CHECKS_PER_LEVEL_TICK);
    }
    assertEquals(owners, completed);
  }
}
