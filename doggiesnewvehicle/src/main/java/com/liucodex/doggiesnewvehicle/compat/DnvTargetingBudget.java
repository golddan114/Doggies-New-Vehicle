package com.liucodex.doggiesnewvehicle.compat;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/** Shared server-side cadence rules for DNV autonomous combat entities. */
public final class DnvTargetingBudget {
    public static final int NEVER_SCANNED = Integer.MIN_VALUE / 2;
    public static final int MIN_FULL_SCAN_INTERVAL = 20;
    public static final int MIN_DECISION_INTERVAL = 10;
    public static final int RELATION_RECHECK_INTERVAL = 20;
    public static final int GEOMETRY_RECHECK_INTERVAL = 5;
    public static final int MAX_SCAN_STEPS_PER_LEVEL_TICK = 16;
    public static final int MAX_GEOMETRY_CHECKS_PER_LEVEL_TICK = 32;
    private static final Map<ServerLevel, ScanLedger> SCAN_LEDGERS = new WeakHashMap<>();
    private static final Map<ServerLevel, ScanLedger> GEOMETRY_LEDGERS = new WeakHashMap<>();

    private DnvTargetingBudget() {
    }

    public static int fullScanInterval(int requestedTicks) {
        return Math.max(MIN_FULL_SCAN_INTERVAL, requestedTicks);
    }

    public static int decisionInterval(int requestedTicks) {
        return Math.max(MIN_DECISION_INTERVAL, requestedTicks);
    }

    public static boolean isDecisionDue(
            long gameTime,
            long nextDecisionGameTime,
            boolean safetyStateChanged) {
        return safetyStateChanged || gameTime >= nextDecisionGameTime;
    }

    public static long nextDecisionGameTime(
            long gameTime,
            int entityId,
            int requestedInterval,
            int salt) {
        int interval = decisionInterval(requestedInterval);
        int spread = Math.floorMod(entityId * 31 + salt * 17, Math.min(5, interval));
        long delta = (long) interval + spread;
        return gameTime > Long.MAX_VALUE - delta ? Long.MAX_VALUE : gameTime + delta;
    }

    public static boolean isScanDue(
            int tick,
            int entityId,
            int lastScanTick,
            int requestedInterval) {
        int interval = fullScanInterval(requestedInterval);
        if (lastScanTick == NEVER_SCANNED) {
            return Math.floorMod(tick, interval) == Math.floorMod(entityId, interval);
        }
        return tick - lastScanTick >= interval
                && Math.floorMod(tick, interval) == Math.floorMod(entityId, interval);
    }

    public static int nextCheckTick(int tick, int entityId, int interval, int salt) {
        int safeInterval = Math.max(1, interval);
        int spreadWindow = Math.min(5, safeInterval);
        int spread = Math.floorMod(entityId * 31 + salt * 17, spreadWindow);
        return tick + safeInterval + spread;
    }

    static boolean isScanDueFromStart(int tick, int entityId, int lastStartTick, int requestedInterval) {
        return lastStartTick == NEVER_SCANNED
                ? isScanDue(tick, entityId, lastStartTick, requestedInterval)
                : tick - lastStartTick >= fullScanInterval(requestedInterval);
    }

    /** Server-thread-only, per-level admission for one bounded section scan step per owner per tick. */
    static boolean admitScanStep(ServerLevel level, UUID owner) {
        return SCAN_LEDGERS.computeIfAbsent(level, ignored -> new ScanLedger())
                .admit(level.getGameTime(), owner);
    }

    /** Revoke a stopped owner's reservation without refunding work already done this tick. */
    static void releaseScanOwner(ServerLevel level, UUID owner) {
        ScanLedger ledger = SCAN_LEDGERS.get(level);
        if (ledger != null) ledger.release(owner);
    }

    /** Applies only to exploratory block/entity line checks, never the immediate firing check. */
    public static boolean admitGeometryCheck(ServerLevel level, UUID weaponOwner) {
        return GEOMETRY_LEDGERS.computeIfAbsent(level,
                ignored -> new ScanLedger(MAX_GEOMETRY_CHECKS_PER_LEVEL_TICK))
                .admit(level.getGameTime(), weaponOwner);
    }

    public static void releaseGeometryOwner(ServerLevel level, UUID weaponOwner) {
        ScanLedger ledger = GEOMETRY_LEDGERS.get(level);
        if (ledger != null) ledger.release(weaponOwner);
    }

    static final class ScanLedger {
        private static final long STALE_TICKS = 200L;
        private final int limit;
        private final Map<UUID, Owner> owners = new HashMap<>();
        private final Set<UUID> reserved = new HashSet<>();
        private long tick = Long.MIN_VALUE;
        private int used;
        private int maxWaitTicks;
        private int ownerInspections;

        ScanLedger() {
            this(MAX_SCAN_STEPS_PER_LEVEL_TICK);
        }

        ScanLedger(int limit) {
            if (limit < 1) throw new IllegalArgumentException("limit must be positive");
            this.limit = limit;
        }

        boolean admit(long gameTime, UUID id) {
            if (gameTime != tick) {
                if (gameTime < tick) owners.clear();
                tick = gameTime;
                used = 0;
                reserveRecentWaiters(gameTime);
            }
            Owner owner = owners.computeIfAbsent(id, ignored -> new Owner());
            owner.lastSeen = gameTime;
            if (owner.lastGranted == gameTime) return false;
            if (!owner.pending) owner.firstPending = gameTime;
            owner.pending = true;
            boolean hasReservation = reserved.contains(id);
            // Last tick's oldest live waiters keep their slots even when their entities tick later.
            if (used >= limit
                    || (!hasReservation
                    && used >= limit - reserved.size())) return false;
            if (hasReservation) reserved.remove(id);
            used++;
            owner.pending = false;
            owner.lastGranted = gameTime;
            maxWaitTicks = Math.max(maxWaitTicks, (int) Math.min(Integer.MAX_VALUE,
                    Math.max(0L, gameTime - owner.firstPending)));
            owner.firstPending = gameTime + 1L;
            return true;
        }

        private void reserveRecentWaiters(long gameTime) {
            reserved.clear();
            ownerInspections = 0;
            Comparator<Map.Entry<UUID, Owner>> oldestFirst = (left, right) -> {
                int granted = Long.compare(left.getValue().lastGranted, right.getValue().lastGranted);
                return granted != 0 ? granted
                        : DnvTargetCandidateCache.compareStableIds(left.getKey(), right.getKey());
            };
            PriorityQueue<Map.Entry<UUID, Owner>> oldest = new PriorityQueue<>(
                    limit, oldestFirst.reversed());
            Iterator<Map.Entry<UUID, Owner>> entries = owners.entrySet().iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                ownerInspections++;
                Owner owner = entry.getValue();
                if (gameTime - owner.lastSeen > STALE_TICKS) {
                    entries.remove();
                } else if (owner.pending) {
                    if (gameTime - owner.lastSeen > 1L) owner.pending = false;
                    else if (oldest.size() < limit) oldest.add(entry);
                    else if (oldestFirst.compare(entry, oldest.peek()) < 0) {
                        oldest.remove();
                        oldest.add(entry);
                    }
                }
            }
            for (var entry : oldest) reserved.add(entry.getKey());
        }

        int used() { return used; }
        int maxWaitTicks() { return maxWaitTicks; }
        int ownerInspections() { return ownerInspections; }

        void release(UUID owner) {
            owners.remove(owner);
            reserved.remove(owner);
        }
        int ownerCount() { return owners.size(); }

        private static final class Owner {
            private long lastSeen = Long.MIN_VALUE;
            private long lastGranted = Long.MIN_VALUE;
            private long firstPending = Long.MIN_VALUE;
            private boolean pending;
        }
    }
}
