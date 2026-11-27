package com.liucodex.doggiesnewvehicle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One bounded, stateless allocation pass after a vehicle resolves its shared candidates. */
public final class DnvMultiStationTargetAllocator {
  public static final int MAX_CANDIDATES = 32;
  public static final int MAX_STATIONS = 32;

  private DnvMultiStationTargetAllocator() {}

  public static Map<String, UUID> assign(List<Candidate> candidates, List<Station> stations) {
    Objects.requireNonNull(candidates, "candidates");
    Objects.requireNonNull(stations, "stations");
    if (candidates.size() > MAX_CANDIDATES || stations.size() > MAX_STATIONS) {
      throw new IllegalArgumentException("allocation input exceeds its bounds");
    }
    Set<UUID> candidateIds = new HashSet<>();
    List<Candidate> ordered = new ArrayList<>(candidates);
    for (Candidate candidate : ordered) {
      if (candidate == null || !candidateIds.add(candidate.targetId())) {
        throw new IllegalArgumentException("candidate identities must be unique");
      }
    }
    ordered.sort(Comparator.comparingDouble(Candidate::priority)
        .thenComparing(Candidate::targetId, DnvMultiStationTargetAllocator::compareIds));

    Map<String, UUID> assigned = new LinkedHashMap<>();
    Map<UUID, Integer> occupancy = new HashMap<>();
    Set<String> stationIds = new HashSet<>();
    for (Station station : stations) {
      if (station == null || !stationIds.add(station.stationId())) {
        throw new IllegalArgumentException("station identities must be unique");
      }
      UUID retained = station.retainedTargetId();
      if (retained != null && station.eligibleTargetIds().contains(retained)
          && !retained.equals(station.excludedTargetId())) {
        assigned.put(station.stationId(), retained);
        occupancy.merge(retained, 1, Integer::sum);
      }
    }
    for (Station station : stations) {
      if (assigned.containsKey(station.stationId()) || !station.mayAcquire()) continue;
      Candidate best = null;
      int leastAssigned = Integer.MAX_VALUE;
      for (Candidate candidate : ordered) {
        UUID id = candidate.targetId();
        if (!station.eligibleTargetIds().contains(id) || id.equals(station.excludedTargetId())) {
          continue;
        }
        int count = occupancy.getOrDefault(id, 0);
        if (count < leastAssigned) {
          best = candidate;
          leastAssigned = count;
        }
      }
      if (best != null) {
        assigned.put(station.stationId(), best.targetId());
        occupancy.merge(best.targetId(), 1, Integer::sum);
      }
    }
    for (Station station : stations) {
      UUID retained = assigned.get(station.stationId());
      if (retained == null || station.retainedTargetId() == null
          || !station.mayRedistribute()) continue;
      int currentCount = occupancy.getOrDefault(retained, 0);
      Candidate alternative = null;
      int fewest = currentCount;
      for (Candidate candidate : ordered) {
        UUID id = candidate.targetId();
        if (id.equals(retained) || id.equals(station.excludedTargetId())
            || !station.eligibleTargetIds().contains(id)) continue;
        int count = occupancy.getOrDefault(id, 0);
        if (count + 1 < currentCount && count < fewest) {
          alternative = candidate;
          fewest = count;
        }
      }
      if (alternative != null) {
        occupancy.computeIfPresent(retained, (id, count) -> count == 1 ? null : count - 1);
        assigned.put(station.stationId(), alternative.targetId());
        occupancy.merge(alternative.targetId(), 1, Integer::sum);
      }
    }
    return Collections.unmodifiableMap(assigned);
  }

  private static int compareIds(UUID left, UUID right) {
    int most = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
    return most != 0 ? most
        : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
  }

  /** Lower priority wins ties after current occupancy. */
  public record Candidate(UUID targetId, double priority) {
    public Candidate {
      Objects.requireNonNull(targetId, "targetId");
      if (!Double.isFinite(priority) || priority < 0.0D) {
        throw new IllegalArgumentException("candidate priority must be finite and nonnegative");
      }
    }
  }

  /** The owner prevalidates retained targets and supplies its exact station-specific eligibility. */
  public record Station(
      String stationId,
      UUID retainedTargetId,
      Set<UUID> eligibleTargetIds,
      UUID excludedTargetId,
      boolean mayAcquire,
      boolean mayRedistribute) {
    public Station {
      stationId = Objects.requireNonNull(stationId, "stationId").trim();
      if (stationId.isEmpty() || stationId.length() > 96) {
        throw new IllegalArgumentException("station id must be bounded text");
      }
      eligibleTargetIds = Set.copyOf(Objects.requireNonNull(eligibleTargetIds, "eligibleTargetIds"));
      if (eligibleTargetIds.size() > MAX_CANDIDATES + 1) {
        throw new IllegalArgumentException("station eligibility exceeds candidate budget");
      }
    }
  }
}
