package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DnvTargetingBudgetTest {
    @Test
    void fullScansNeverRunMoreOftenThanOncePerSecond() {
        assertEquals(20, DnvTargetingBudget.fullScanInterval(1));
        assertEquals(20, DnvTargetingBudget.fullScanInterval(20));
        assertEquals(30, DnvTargetingBudget.fullScanInterval(30));
    }

    @Test
    void highLevelDecisionsHaveAHalfSecondFloorAndSafetyInvalidation() {
        assertEquals(10, DnvTargetingBudget.decisionInterval(1));
        long next = DnvTargetingBudget.nextDecisionGameTime(100L, 5, 1, 9);
        assertTrue(next >= 110L && next < 115L);
        assertFalse(DnvTargetingBudget.isDecisionDue(109L, next, false));
        assertTrue(DnvTargetingBudget.isDecisionDue(109L, next, true));
        assertTrue(DnvTargetingBudget.isDecisionDue(next, next, false));
    }

    @Test
    void firstScanIsStaggeredByEntityId() {
        assertTrue(DnvTargetingBudget.isScanDue(7, 27,
                DnvTargetingBudget.NEVER_SCANNED, 20));
        assertFalse(DnvTargetingBudget.isScanDue(8, 27,
                DnvTargetingBudget.NEVER_SCANNED, 20));
    }

    @Test
    void subsequentScanUsesTheEffectiveInterval() {
        assertFalse(DnvTargetingBudget.isScanDue(40, 1, 21, 4));
        assertTrue(DnvTargetingBudget.isScanDue(41, 1, 21, 4));
    }

    @Test
    void secondaryChecksReceiveStableSpread() {
        int first = DnvTargetingBudget.nextCheckTick(100, 5, 20, 1);
        int second = DnvTargetingBudget.nextCheckTick(100, 6, 20, 1);
        assertTrue(first >= 120 && first < 140);
        assertTrue(second >= 120 && second < 140);
        assertFalse(first == second);
    }

    @Test
    void oneHundredEntitiesDistributeFirstScansAcrossTheWholeInterval() {
        int[] scansPerTick = new int[20];
        for (int entityId = 0; entityId < 100; entityId++) {
            for (int tick = 0; tick < scansPerTick.length; tick++) {
                if (DnvTargetingBudget.isScanDue(
                        tick, entityId, DnvTargetingBudget.NEVER_SCANNED, 20)) {
                    scansPerTick[tick]++;
                }
            }
        }
        for (int scans : scansPerTick) {
            assertEquals(5, scans);
        }
    }

    @Test
    void fiftyOwnersShareActualStepQuotaWithoutStarvation() {
        DnvTargetingBudget.ScanLedger level = new DnvTargetingBudget.ScanLedger();
        final int targetSteps = 32;
        int[] completedCells = new int[50];
        int[] longestWait = new int[50];
        int[] lastGrant = new int[50];
        for (int tick = 0; tick < 110; tick++) {
            if (Arrays.stream(completedCells)
                    .allMatch(count -> count == targetSteps)) break;
            int granted = 0;
            for (int owner = 0; owner < completedCells.length; owner++) {
                if (completedCells[owner] >= targetSteps) continue;
                if (level.admit(tick, new UUID(0L, owner + 1L))) {
                    completedCells[owner]++;
                    granted++;
                    longestWait[owner] = Math.max(longestWait[owner], tick - lastGrant[owner]);
                    lastGrant[owner] = tick;
                }
            }
            assertTrue(granted <= DnvTargetingBudget.MAX_SCAN_STEPS_PER_LEVEL_TICK);
            assertEquals(granted, level.used());
        }
        for (int owner = 0; owner < completedCells.length; owner++) {
            assertEquals(targetSteps, completedCells[owner]);
            assertTrue(longestWait[owner] <= 5);
        }
    }

    @Test
    void admissionChecksGrowLinearlyFromOneToOneThousandOwners() {
        for (int count : new int[]{1, 10, 50, 1000}) {
            DnvTargetingBudget.ScanLedger level = new DnvTargetingBudget.ScanLedger();
            for (int tick = 0; tick < 6; tick++) {
                for (int owner = 0; owner < count; owner++)
                    level.admit(tick, new UUID(0L, owner + 1L));
                assertEquals(Math.min(count, DnvTargetingBudget.MAX_SCAN_STEPS_PER_LEVEL_TICK),
                        level.used());
                assertTrue(level.ownerInspections() <= 2 * count + 16,
                        count + " owners must not inspect the waiting map per admission");
            }
            if (count == 50) assertTrue(level.maxWaitTicks() <= 5);
        }
    }

    @Test
    void oneThousandWaitingOwnersEachReceiveAFirstStepWithinTheQuotaCycle() {
        DnvTargetingBudget.ScanLedger level = new DnvTargetingBudget.ScanLedger();
        boolean[] started = new boolean[1000];
        int seen = 0;
        for (int tick = 0; tick < 70 && seen < started.length; tick++) {
            for (int owner = 0; owner < started.length; owner++) {
                if (started[owner]
                        || !level.admit(tick, new UUID(0L, owner + 1L))) continue;
                started[owner] = true;
                seen++;
            }
            assertTrue(level.used() <= DnvTargetingBudget.MAX_SCAN_STEPS_PER_LEVEL_TICK);
        }
        assertEquals(started.length, seen);
        assertTrue(level.maxWaitTicks() <= 63);
    }

    @Test
    void ledgersForDifferentLevelsDoNotShareQuotaAndClockRollbackClearsWaiters() {
        DnvTargetingBudget.ScanLedger first = new DnvTargetingBudget.ScanLedger();
        DnvTargetingBudget.ScanLedger second = new DnvTargetingBudget.ScanLedger();
        for (int owner = 0; owner < 20; owner++) {
            first.admit(100, new UUID(0L, owner + 1L));
            second.admit(100, new UUID(0L, owner + 1L));
        }
        assertEquals(16, first.used());
        assertEquals(16, second.used());
        assertTrue(first.admit(0, new UUID(0L, 999L)));
        assertEquals(1, first.used());
    }

    @Test
    void stoppingAnOwnerReleasesItsReservationButNeverRefundsThisTicksWork() {
        DnvTargetingBudget.ScanLedger level = new DnvTargetingBudget.ScanLedger();
        for (int owner = 1; owner <= 16; owner++)
            assertTrue(level.admit(100, new UUID(0L, owner)));
        assertEquals(16, level.used());
        assertEquals(16, level.ownerCount());
        level.release(new UUID(0L, 1L));
        assertEquals(15, level.ownerCount());
        assertFalse(level.admit(100, new UUID(0L, 17L)));
        assertEquals(16, level.used());
        assertTrue(level.admit(101, new UUID(0L, 17L)));
        level.release(new UUID(0L, 17L));
        assertEquals(15, level.ownerCount());
    }

    @Test
    void absentOrReleasedWaitersCannotReserveSlotsForever() {
        DnvTargetingBudget.ScanLedger level = new DnvTargetingBudget.ScanLedger();
        for (int owner = 1; owner <= 16; owner++)
            assertTrue(level.admit(0, new UUID(0L, owner)));
        UUID waiting = new UUID(0L, 17L);
        assertFalse(level.admit(0, waiting));
        assertTrue(level.admit(1, new UUID(0L, 18L)));
        level.release(waiting);
        for (int owner = 19; owner <= 33; owner++)
            assertTrue(level.admit(1, new UUID(0L, owner)));
        assertEquals(16, level.used());
        assertFalse(level.admit(1, new UUID(0L, 34L)));

        DnvTargetingBudget.ScanLedger absent = new DnvTargetingBudget.ScanLedger();
        for (int owner = 1; owner <= 16; owner++)
            assertTrue(absent.admit(0, new UUID(0L, owner)));
        assertFalse(absent.admit(0, waiting));
        assertTrue(absent.admit(1, new UUID(0L, 18L)));
        for (int owner = 19; owner <= 34; owner++)
            assertTrue(absent.admit(2, new UUID(0L, owner)));
        assertEquals(16, absent.used());
    }

    @Test
    void geometryQuotaIsSeparateAndFairForManyWeaponOwners() {
        DnvTargetingBudget.ScanLedger geometry = new DnvTargetingBudget.ScanLedger(
                DnvTargetingBudget.MAX_GEOMETRY_CHECKS_PER_LEVEL_TICK);
        DnvTargetingBudget.ScanLedger scans = new DnvTargetingBudget.ScanLedger();
        int[] grants = new int[50];
        for (int tick = 0; tick < 100; tick++) {
            for (int owner = 0; owner < grants.length; owner++) {
                UUID id = new UUID(1L, owner + 1L);
                if (geometry.admit(tick, id)) grants[owner]++;
                scans.admit(tick, id);
            }
            assertEquals(32, geometry.used());
            assertEquals(16, scans.used());
        }
        // Admission order need not give equal throughput, but every requester advances.
        for (int count : grants) assertTrue(count >= 50, "grant count=" + count);
        assertTrue(geometry.maxWaitTicks() <= 2);
    }

    @Test
    void oneThousandGeometryOwnersEventuallyAllReceiveAQuotaSlot() {
        DnvTargetingBudget.ScanLedger geometry = new DnvTargetingBudget.ScanLedger(
                DnvTargetingBudget.MAX_GEOMETRY_CHECKS_PER_LEVEL_TICK);
        boolean[] seen = new boolean[1000];
        int count = 0;
        for (int tick = 0; tick < 35 && count < seen.length; tick++) {
            for (int owner = 0; owner < seen.length; owner++) {
                if (seen[owner] || !geometry.admit(tick, new UUID(1L, owner + 1L))) continue;
                seen[owner] = true;
                count++;
            }
            assertTrue(geometry.used() <= 32);
            assertTrue(geometry.ownerInspections() <= 2 * seen.length + 32);
        }
        assertEquals(seen.length, count);
        assertTrue(geometry.maxWaitTicks() <= 32);
    }

    @Test
    void geometryStopAndClockRollbackNeverRefundAlreadyPerformedChecks() {
        DnvTargetingBudget.ScanLedger geometry = new DnvTargetingBudget.ScanLedger(32);
        for (int owner = 1; owner <= 32; owner++)
            assertTrue(geometry.admit(100, new UUID(1L, owner)));
        geometry.release(new UUID(1L, 1L));
        assertFalse(geometry.admit(100, new UUID(1L, 33L)));
        assertTrue(geometry.admit(101, new UUID(1L, 33L)));
        assertTrue(geometry.admit(0, new UUID(1L, 34L)));
        assertEquals(1, geometry.used());
    }
}
