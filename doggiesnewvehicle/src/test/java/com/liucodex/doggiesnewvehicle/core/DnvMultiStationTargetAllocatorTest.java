package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DnvMultiStationTargetAllocatorTest {
  private static final UUID A = new UUID(0L, 1L);
  private static final UUID B = new UUID(0L, 2L);
  private static final UUID C = new UUID(0L, 3L);

  @Test
  void sixStationsCanConcentrateOnOneLegalTarget() {
    var assignments = DnvMultiStationTargetAllocator.assign(
        List.of(new DnvMultiStationTargetAllocator.Candidate(A, 4.0D)),
        sixStations(Set.of(A)));
    assertEquals(6, assignments.size());
    assertTrue(assignments.values().stream().allMatch(A::equals));
  }

  @Test
  void sixStationsSpreadAcrossThreeTargetsDespiteOneBeingNearest() {
    var assignments = DnvMultiStationTargetAllocator.assign(
        List.of(candidate(A, 1.0D), candidate(B, 4.0D), candidate(C, 9.0D)),
        sixStations(Set.of(A, B, C)));
    assertEquals(2, assignments.values().stream().filter(A::equals).count());
    assertEquals(2, assignments.values().stream().filter(B::equals).count());
    assertEquals(2, assignments.values().stream().filter(C::equals).count());
  }

  @Test
  void legalRetainedTargetsReserveOccupancyBeforeNewSelections() {
    var stations = List.of(
        station("front", A, Set.of(A), null, true),
        station("middle", B, Set.of(B), null, true),
        station("rear", null, Set.of(A, B, C), null, true));
    var assigned = DnvMultiStationTargetAllocator.assign(
        List.of(candidate(A, 1.0D), candidate(B, 2.0D), candidate(C, 3.0D)), stations);
    assertEquals(Map.of("front", A, "middle", B, "rear", C), assigned);
  }

  @Test
  void lateSecondTargetGetsHalfTheWeaponsAfterHoldButNotBefore() {
    List<DnvMultiStationTargetAllocator.Station> held = new ArrayList<>();
    List<DnvMultiStationTargetAllocator.Station> released = new ArrayList<>();
    for (int index = 0; index < 6; index++) {
      held.add(new DnvMultiStationTargetAllocator.Station(
          "station-" + index, A, Set.of(A, B), null, true, false));
      released.add(new DnvMultiStationTargetAllocator.Station(
          "station-" + index, A, Set.of(A, B), null, true, true));
    }
    var candidates = List.of(candidate(A, 1.0D), candidate(B, 4.0D));
    assertTrue(DnvMultiStationTargetAllocator.assign(candidates, held)
        .values().stream().allMatch(A::equals));
    var balanced = DnvMultiStationTargetAllocator.assign(candidates, released);
    assertEquals(3, balanced.values().stream().filter(A::equals).count());
    assertEquals(3, balanced.values().stream().filter(B::equals).count());
  }

  @Test
  void exactSectorAndBlockedTargetExclusionApplyPerStation() {
    var assigned = DnvMultiStationTargetAllocator.assign(
        List.of(candidate(A, 1.0D), candidate(B, 2.0D), candidate(C, 3.0D)),
        List.of(station("port", null, Set.of(A), null, true),
            station("starboard", null, Set.of(B), null, true),
            station("rocket", null, Set.of(A, B, C), A, true),
            station("cooling", null, Set.of(A, B, C), null, false)));
    assertEquals(A, assigned.get("port"));
    assertEquals(B, assigned.get("starboard"));
    assertEquals(C, assigned.get("rocket"));
    assertFalse(assigned.containsKey("cooling"));
  }

  @Test
  void noLegalCandidateDoesNotCreateALock() {
    assertTrue(DnvMultiStationTargetAllocator.assign(List.of(candidate(A, 1.0D)),
        List.of(station("rear", null, Set.of(B), null, true))).isEmpty());
  }

  @Test
  void equalScoresHaveStableUnsignedUuidOrder() {
    UUID high = new UUID(Long.MIN_VALUE, 0L);
    UUID low = new UUID(0L, 0L);
    var assigned = DnvMultiStationTargetAllocator.assign(
        List.of(candidate(high, 1.0D), candidate(low, 1.0D)),
        List.of(station("first", null, Set.of(high, low), null, true)));
    assertEquals(low, assigned.get("first"));
  }

  @Test
  void malformedOrOversizeFramesFailBeforeAllocation() {
    assertThrows(IllegalArgumentException.class, () ->
        DnvMultiStationTargetAllocator.assign(List.of(candidate(A, 1.0D), candidate(A, 2.0D)),
            List.of()));
    assertThrows(IllegalArgumentException.class, () ->
        DnvMultiStationTargetAllocator.assign(List.of(),
            List.of(station("same", null, Set.of(), null, true),
                station("same", null, Set.of(), null, true))));
    List<DnvMultiStationTargetAllocator.Candidate> tooMany = new ArrayList<>();
    for (int index = 0; index < 33; index++) tooMany.add(candidate(new UUID(0L, index), index));
    assertThrows(IllegalArgumentException.class, () ->
        DnvMultiStationTargetAllocator.assign(tooMany, List.of()));
  }

  private static DnvMultiStationTargetAllocator.Candidate candidate(UUID id, double priority) {
    return new DnvMultiStationTargetAllocator.Candidate(id, priority);
  }

  private static DnvMultiStationTargetAllocator.Station station(
      String id, UUID retained, Set<UUID> eligible, UUID excluded, boolean acquire) {
    return new DnvMultiStationTargetAllocator.Station(id, retained, eligible, excluded, acquire, false);
  }

  private static List<DnvMultiStationTargetAllocator.Station> sixStations(Set<UUID> eligible) {
    List<DnvMultiStationTargetAllocator.Station> stations = new ArrayList<>();
    for (int index = 0; index < 6; index++) {
      stations.add(station("station-" + index, null, eligible, null, true));
    }
    return stations;
  }
}
