package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.liucodex.doggiesnewvehicle.core.DnvPlatformCapabilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DnvTargetCandidateCacheTest {
    @Test
    void emptyCacheReturnsWithoutInvokingSelectionCallbacks() {
        DnvTargetCandidateCache cache = new DnvTargetCandidateCache(20);

        assertNull(cache.select(
                null,
                null,
                ignored -> {
                    fail("empty cache must not evaluate eligibility");
                    return true;
                },
                ignored -> {
                    fail("empty cache must not evaluate distance");
                    return 0.0D;
                },
                false
        ));
    }

    @Test
    void equalDistanceTieBreakUsesStableUuidOrderInsteadOfRuntimeEntityIds() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID high = UUID.fromString("00000000-0000-0000-0000-000000000020");

        assertTrue(DnvTargetCandidateCache.compareStableIds(low, high) < 0);
        assertTrue(DnvTargetCandidateCache.compareStableIds(high, low) > 0);
        assertEquals(0, DnvTargetCandidateCache.compareStableIds(low, low));
    }

    @Test
    void eligibilityFilteringHappensBeforeTheTopKBudget() {
        List<Candidate> input = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            input.add(new Candidate(
                    new UUID(0L, index + 1L), index, false));
        }
        Candidate hostile = new Candidate(new UUID(0L, 100L), 100.0D, true);
        input.add(hostile);

        List<Candidate> bounded = DnvTargetCandidateCache.boundedTopK(
                input,
                Candidate::hostile,
                Candidate::distance,
                Candidate::id,
                DnvTargetCandidateCache.MAX_CANDIDATES);

        assertEquals(List.of(hostile), bounded);
    }

    @Test
    void activeCandidateScanKeepsOnlyTheNearestStableThirtyTwo() {
        List<Candidate> input = new ArrayList<>();
        for (int index = 40; index >= 1; index--) {
            input.add(new Candidate(new UUID(0L, index), 0.0D, true));
        }
        List<Candidate> bounded = DnvTargetCandidateCache.boundedTopK(
                input, Candidate::hostile, Candidate::distance,
                Candidate::id, DnvTargetCandidateCache.MAX_CANDIDATES);
        assertEquals(32, bounded.size());
        assertEquals(new UUID(0L, 1L), bounded.get(0).id());
        assertEquals(new UUID(0L, 32L), bounded.get(31).id());
    }

    @Test
    void retainedTargetCostsOneEligibilityCheckAndBlockedOrStaleTargetsSwitchWithinTheBound() {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 1; i <= DnvTargetCandidateCache.MAX_CANDIDATES; i++)
            candidates.add(new Candidate(new UUID(0, i), i, true));
        Candidate current = candidates.get(20);
        AtomicInteger calls = new AtomicInteger();
        assertEquals(current, DnvTargetCandidateCache.selectBest(candidates, current,
                candidate -> { calls.incrementAndGet(); return true; }, Candidate::distance, Candidate::id));
        assertEquals(1, calls.get());

        calls.set(0);
        Candidate next = DnvTargetCandidateCache.selectBest(candidates, current,
                candidate -> { calls.incrementAndGet(); return candidate != current && candidate != candidates.get(0); },
                Candidate::distance, Candidate::id);
        assertEquals(candidates.get(1), next, "occluded current and nearest candidate must be skipped");
        assertEquals(DnvTargetCandidateCache.MAX_CANDIDATES, calls.get());

        calls.set(0);
        Candidate stale = new Candidate(new UUID(0, 999), 0, true);
        assertEquals(candidates.get(0), DnvTargetCandidateCache.selectBest(candidates, stale,
                candidate -> { calls.incrementAndGet(); return true; }, Candidate::distance, Candidate::id));
        assertEquals(DnvTargetCandidateCache.MAX_CANDIDATES, calls.get());
    }

    @Test
    void platformConfigurationRejectsAnUnboundedScanInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> new DnvPlatformCapabilities(
                        DnvPlatformCapabilities.PlatformFamily.GROUND,
                        Set.of(DnvPlatformCapabilities.MovementMedium.GROUND),
                        Set.of(), Set.of(), true, 1, 1,
                        1_201, 10, 10, 40));
    }

    private record Candidate(UUID id, double distance, boolean hostile) {}
}
