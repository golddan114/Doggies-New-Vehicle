package com.liucodex.doggiesnewvehicle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DnvLazyCandidateFrameTest {
  @Test
  void stableStationsDoNotResolveAnyCandidates() {
    AtomicInteger calls = new AtomicInteger();
    try (var frame = new DnvLazyCandidateFrame<>(() -> {
      calls.incrementAndGet();
      return List.of("target");
    })) {
      assertEquals(0, calls.get());
    }
    assertEquals(0, calls.get());
  }

  @Test
  void severalStationsShareOneImmutableResolutionAndCloseDropsAccess() {
    AtomicInteger calls = new AtomicInteger();
    List<String> source = new ArrayList<>(List.of("one"));
    var frame = new DnvLazyCandidateFrame<>(() -> {
      calls.incrementAndGet();
      return source;
    });
    List<String> first = frame.get();
    source.add("two");
    assertSame(first, frame.get());
    assertEquals(List.of("one"), first);
    assertEquals(1, calls.get());
    frame.close();
    assertThrows(IllegalStateException.class, frame::get);
  }
}
