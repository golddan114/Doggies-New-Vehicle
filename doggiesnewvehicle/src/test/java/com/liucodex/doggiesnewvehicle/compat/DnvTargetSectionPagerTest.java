package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DnvTargetSectionPagerTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    void mothershipBatchesStayFourSecondsApartWithStaggerAndDeferredPages(int occupiedTicks) {
        int id = 87;
        int priorStart = DnvTargetingBudget.NEVER_SCANNED;
        int start = -1;
        var starts = new ArrayList<Integer>();
        var completions = new ArrayList<Integer>();
        var ledger = new DnvTargetingBudget.ScanLedger(1);
        var owner = new UUID(0, 2);
        var other = new UUID(0, 1);
        var keys = new LongAVLTreeSet(new long[]{SectionPos.asLong(0, 0, 0)});
        var entities = java.util.Collections.nCopies(2 * DnvTargetSectionPager.MAX_ENTITY_VISITS_PER_STEP, 1);
        DnvTargetSectionPager<Integer> pager = null;
        for (int tick = 0; tick < 176; tick++) {
            if (pager == null && DnvTargetingBudget.isScanDueFromStart(tick, id, priorStart, 80)) {
                start = tick;
                starts.add(start);
                pager = new DnvTargetSectionPager<>(new AABB(0, 0, 0, 1, 1, 1));
            }
            if (pager == null) continue;
            if (tick >= 87 && tick < 87 + occupiedTicks) ledger.admit(tick, other);
            if (!ledger.admit(tick, owner)) continue;
            if (pager.step(keys, ignored -> entities, value -> {}).complete()) {
                completions.add(tick);
                priorStart = start;
                pager = null;
            }
        }
        assertEquals(List.of(7, 87, 167), starts);
        assertTrue(completions.get(0) > starts.get(0), "fixture must span multiple pages");
        assertTrue(completions.get(1) - completions.get(0) >= 80 + (occupiedTicks == 0 ? 0 : 1));
        assertEquals(completions.get(0) + 160, completions.get(2));
        System.out.println("Mothership 80-tick scan starts " + starts + ", completed " + completions);
    }

    @Test
    void movingDenseOwnerEventuallyCompletesInsteadOfRestartingForever() {
        long section = SectionPos.asLong(0, 0, 0);
        LongAVLTreeSet keys = new LongAVLTreeSet(new long[]{section});
        List<Integer> entities = new ArrayList<>();
        for (int index = 0; index < 4096; index++) entities.add(index);
        DnvTargetingBudget.ScanLedger ledger = new DnvTargetingBudget.ScanLedger();
        UUID owner = new UUID(0L, 1L);
        DnvTargetSectionPager<Integer> pager = null;
        Vec3 origin = null;
        boolean startedSteps = false;
        boolean complete = false;
        Vec3 completedPosition = null;
        AtomicInteger tailVisits = new AtomicInteger();
        for (int tick = 0; tick < 100 && !complete; tick++) {
            Vec3 position = new Vec3(tick, 0.0D, 0.0D);
            if (pager == null || DnvTargetCandidateCache.shouldRestartMovingScan(
                    origin, position, 128.0D, startedSteps)) {
                origin = position;
                startedSteps = false;
                pager = new DnvTargetSectionPager<>(
                        new AABB(tick, 0, 0, tick + 1, 1, 1).inflate(128.0D));
            }
            if (!ledger.admit(tick, owner)) continue;
            startedSteps = true;
            var step = pager.step(keys, ignored -> entities,
                    value -> { if (value == 4095) tailVisits.incrementAndGet(); });
            assertTrue(step.sectionLookups() <= DnvTargetSectionPager.MAX_SECTION_LOOKUPS_PER_STEP);
            assertTrue(step.entityVisits() <= DnvTargetSectionPager.MAX_ENTITY_VISITS_PER_STEP);
            complete = step.complete();
            if (complete) completedPosition = position;
        }
        assertTrue(complete, "continuous movement must not restart a dense scan indefinitely");
        assertEquals(1, tailVisits.get());
        assertTrue(DnvTargetCandidateCache.movedOutsideScanOrigin(origin,
                completedPosition, 128.0D),
                "completion after movement must request a scan around the new location");
    }

    @ParameterizedTest
    @ValueSource(doubles = {128, 256})
    void fiftyMovingOwnersShareTheStepBudgetAndReachDenseTail(double range) {
        long section = SectionPos.asLong(0, 0, 0);
        LongAVLTreeSet keys = new LongAVLTreeSet(new long[]{section});
        List<Integer> entities = new ArrayList<>();
        for (int index = 0; index < 2048; index++) entities.add(index);
        DnvTargetingBudget.ScanLedger ledger = new DnvTargetingBudget.ScanLedger();
        @SuppressWarnings("unchecked")
        DnvTargetSectionPager<Integer>[] pagers = new DnvTargetSectionPager[50];
        Vec3[] origins = new Vec3[50];
        boolean[] started = new boolean[50];
        boolean[] complete = new boolean[50];
        boolean[] tailReached = new boolean[50];
        for (int tick = 0; tick < 200 && !all(complete); tick++) {
            int grants = 0;
            Vec3 position = new Vec3(tick, 0.0D, 0.0D);
            for (int owner = 0; owner < 50; owner++) {
                if (complete[owner]) continue;
                if (pagers[owner] == null || DnvTargetCandidateCache.shouldRestartMovingScan(
                        origins[owner], position, range, started[owner])) {
                    origins[owner] = position;
                    started[owner] = false;
                    pagers[owner] = new DnvTargetSectionPager<>(
                            new AABB(tick, 0, 0, tick + 1, 1, 1).inflate(range, 80, range));
                }
                if (!ledger.admit(tick, new UUID(0L, owner + 1L))) continue;
                grants++;
                started[owner] = true;
                int current = owner;
                var step = pagers[owner].step(keys, ignored -> entities,
                        value -> { if (value == 2047) tailReached[current] = true; });
                assertTrue(step.sectionLookups() <= DnvTargetSectionPager.MAX_SECTION_LOOKUPS_PER_STEP);
                assertTrue(step.entityVisits() <= DnvTargetSectionPager.MAX_ENTITY_VISITS_PER_STEP);
                complete[owner] = step.complete();
            }
            assertTrue(grants <= DnvTargetingBudget.MAX_SCAN_STEPS_PER_LEVEL_TICK);
        }
        assertTrue(all(complete), "moving owners must finish under shared contention");
        assertTrue(all(tailReached), "each owner must reach the dense section tail");
    }

    @Test
    void ownerAndDensityMatrixKeepsEveryTickBoundedAndEventuallyVisitsTheTail() {
        long section = SectionPos.asLong(0, 0, 0);
        LongAVLTreeSet keys = new LongAVLTreeSet(new long[]{section});
        for (int owners : new int[]{1, 10, 50}) for (int density : new int[]{32, 256, 2048}) {
            List<Integer> entities = new ArrayList<>();
            for (int index = 0; index < density; index++) entities.add(index);
            DnvTargetingBudget.ScanLedger ledger = new DnvTargetingBudget.ScanLedger();
            List<DnvTargetSectionPager<Integer>> pagers = new ArrayList<>();
            for (int owner = 0; owner < owners; owner++)
                pagers.add(new DnvTargetSectionPager<>(new AABB(0, 0, 0, 1, 1, 1)));
            int[] visits = new int[owners];
            boolean[] foundTail = new boolean[owners];
            boolean[] completed = new boolean[owners];
            for (int tick = 0; tick < 250; tick++) {
                int grants = 0;
                for (int owner = 0; owner < owners; owner++) {
                    if (completed[owner] || !ledger.admit(tick, new UUID(0L, owner + 1L))) continue;
                    grants++;
                    int current = owner;
                    var result = pagers.get(owner).step(keys, ignored -> entities, value -> {
                        visits[current]++;
                        if (value == density - 1) foundTail[current] = true;
                    });
                    assertTrue(result.sectionLookups() <= DnvTargetSectionPager.MAX_SECTION_LOOKUPS_PER_STEP);
                    assertTrue(result.entityVisits() <= DnvTargetSectionPager.MAX_ENTITY_VISITS_PER_STEP);
                    completed[owner] = result.complete();
                }
                assertTrue(grants <= DnvTargetingBudget.MAX_SCAN_STEPS_PER_LEVEL_TICK);
                if (all(completed)) break;
            }
            assertTrue(all(completed), owners + " owners, " + density + " entities");
            assertTrue(all(foundTail), owners + " owners, " + density + " entities");
            for (int count : visits) assertEquals(density, count);
        }
    }

    @Test
    void denseSectionEventuallyVisitsTargetAfterTheFirstSixtyFourEntries() {
        long section = SectionPos.asLong(0, 0, 0);
        LongAVLTreeSet keys = new LongAVLTreeSet(new long[]{section});
        List<Integer> entities = new ArrayList<>();
        for (int index = 0; index < 2048; index++) entities.add(index);
        DnvTargetSectionPager<Integer> pager = new DnvTargetSectionPager<>(
                new AABB(0, 0, 0, 1, 1, 1));
        AtomicInteger targetVisits = new AtomicInteger();
        int totalVisits = 0;
        boolean complete = false;
        for (int step = 0; step < 34 && !complete; step++) {
            var result = pager.step(keys, ignored -> entities,
                    value -> { if (value == 2047) targetVisits.incrementAndGet(); });
            assertTrue(result.entityVisits() <= DnvTargetSectionPager.MAX_ENTITY_VISITS_PER_STEP);
            assertTrue(result.sectionLookups() <= DnvTargetSectionPager.MAX_SECTION_LOOKUPS_PER_STEP);
            totalVisits += result.entityVisits();
            if (step < 31) assertEquals(0, targetVisits.get());
            complete = result.complete();
        }
        assertTrue(complete);
        assertEquals(2048, totalVisits);
        assertEquals(1, targetVisits.get());
    }

    @Test
    void sectionMutationCanDelayOneTargetButTheNextScanRecoversIt() {
        long section = SectionPos.asLong(0, 0, 0);
        LongAVLTreeSet keys = new LongAVLTreeSet(new long[]{section});
        List<Integer> entities = new ArrayList<>();
        for (int index = 0; index < 128; index++) entities.add(index);
        AABB bounds = new AABB(0, 0, 0, 1, 1, 1);
        DnvTargetSectionPager<Integer> first = new DnvTargetSectionPager<>(bounds);
        List<Integer> visited = new ArrayList<>();
        var firstStep = first.step(keys, ignored -> entities, visited::add);
        assertEquals(64, firstStep.entityVisits());
        entities.subList(0, 32).clear();
        boolean firstComplete = false;
        for (int step = 0; step < 5 && !firstComplete; step++)
            firstComplete = first.step(keys, ignored -> entities, visited::add).complete();
        assertTrue(firstComplete);
        assertFalse(visited.contains(80), "removal before the cursor can skip an entity in this cycle");

        DnvTargetSectionPager<Integer> next = new DnvTargetSectionPager<>(bounds);
        visited.clear();
        boolean nextComplete = false;
        for (int step = 0; step < 5 && !nextComplete; step++)
            nextComplete = next.step(keys, ignored -> entities, visited::add).complete();
        assertTrue(nextComplete);
        assertTrue(visited.contains(80), "a stable following cycle revisits the whole section");
    }

    @Test
    void sparseWorldBoundsSectionKeyWalkAndRejectsSectionsOutsideRange() {
        LongAVLTreeSet keys = new LongAVLTreeSet();
        long target = SectionPos.asLong(0, 0, 0);
        keys.add(target);
        for (int index = 1; index <= 256; index++) keys.add(SectionPos.asLong(index, 0, 0));
        DnvTargetSectionPager<Integer> pager = new DnvTargetSectionPager<>(
                new AABB(0, 0, 0, 1, 1, 1));
        AtomicInteger visited = new AtomicInteger();
        AtomicInteger sectionReads = new AtomicInteger();
        boolean complete = false;
        int steps = 0;
        while (!complete && steps++ < 10) {
            var result = pager.step(keys, key -> {
                sectionReads.incrementAndGet();
                assertEquals(target, key);
                return List.of(1);
            }, ignored -> visited.incrementAndGet());
            assertTrue(result.sectionLookups() <= DnvTargetSectionPager.MAX_SECTION_LOOKUPS_PER_STEP);
            assertTrue(result.entityVisits() <= DnvTargetSectionPager.MAX_ENTITY_VISITS_PER_STEP);
            complete = result.complete();
        }
        assertTrue(complete);
        assertEquals(1, visited.get());
        assertEquals(1, sectionReads.get());
        assertFalse(pager.step(keys, ignored -> List.of(1), ignored -> visited.incrementAndGet()).entityVisits() > 0);
    }

    @Test
    void signedXKeyWindowIncludesBothSidesOfZeroAndSkipsDistantColumns() {
        LongAVLTreeSet keys = new LongAVLTreeSet();
        for (int x : new int[]{-100, -1, 0, 100}) keys.add(SectionPos.asLong(x, 0, 0));
        DnvTargetSectionPager<Integer> pager = new DnvTargetSectionPager<>(
                new AABB(0, 0, 0, 1, 1, 1));
        List<Integer> visited = new ArrayList<>();
        var result = pager.step(keys,
                key -> List.of(SectionPos.x(key)), visited::add);
        assertTrue(result.complete());
        assertEquals(List.of(-1, 0), visited);
        assertTrue(result.sectionLookups() <= DnvTargetSectionPager.MAX_SECTION_LOOKUPS_PER_STEP);
    }

    @Test
    void zWindowsSkipThousandsOfLoadedSectionsOutsideTheSearchBox() {
        LongAVLTreeSet keys = new LongAVLTreeSet();
        for (int z = -2048; z <= 2048; z++) keys.add(SectionPos.asLong(0, 0, z));
        DnvTargetSectionPager<Integer> pager = new DnvTargetSectionPager<>(
                new AABB(0, 0, 0, 1, 1, 1));
        List<Integer> visited = new ArrayList<>();
        var result = pager.step(keys,
                key -> List.of(SectionPos.z(key)), visited::add);
        assertTrue(result.complete());
        assertEquals(Set.of(-1, 0), Set.copyOf(visited));
        assertEquals(2, visited.size());
        assertTrue(result.sectionLookups() <= DnvTargetSectionPager.MAX_SECTION_LOOKUPS_PER_STEP);
    }

    @Test
    void bothMappedMinecraftVersionsExposeTheSectionPagerFields() throws NoSuchFieldException {
        assertTrue(ServerLevel.class.getDeclaredField("entityManager") != null);
        assertTrue(PersistentEntitySectionManager.class.getDeclaredField("sectionStorage") != null);
        assertTrue(EntitySectionStorage.class.getDeclaredField("sectionIds") != null);
        assertTrue(EntitySection.class.getDeclaredField("storage") != null);
    }

    private static boolean all(boolean[] values) {
        for (boolean value : values) if (!value) return false;
        return true;
    }
}
