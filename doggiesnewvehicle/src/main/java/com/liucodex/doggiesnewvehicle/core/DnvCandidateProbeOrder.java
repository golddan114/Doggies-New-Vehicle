package com.liucodex.doggiesnewvehicle.core;

import java.util.AbstractList;
import java.util.List;

/** A bounded, allocation-light rotating view for one station's candidate probes. */
public final class DnvCandidateProbeOrder {
  private DnvCandidateProbeOrder() {}

  public static <T> List<T> startingAt(List<T> candidates, int cursor) {
    if (candidates.isEmpty()) return List.of();
    int size = candidates.size();
    int start = Math.floorMod(cursor, size);
    return new AbstractList<>() {
      @Override
      public T get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        return candidates.get((start + index) % size);
      }

      @Override
      public int size() {
        return size;
      }
    };
  }
}
