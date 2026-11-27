package com.liucodex.doggiesnewvehicle.core;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Local to one weapon update; resolve live candidates only if a station needs a new target. */
public final class DnvLazyCandidateFrame<T> implements AutoCloseable {
  private Supplier<List<T>> resolver;
  private List<T> resolved;

  public DnvLazyCandidateFrame(Supplier<List<T>> resolver) {
    this.resolver = Objects.requireNonNull(resolver, "resolver");
  }

  public List<T> get() {
    if (resolver == null && resolved == null) {
      throw new IllegalStateException("candidate frame has closed");
    }
    if (resolved == null) {
      resolved = List.copyOf(Objects.requireNonNull(resolver.get(), "candidate frame"));
      resolver = null;
    }
    return resolved;
  }

  @Override
  public void close() {
    resolver = null;
    resolved = null;
  }
}
