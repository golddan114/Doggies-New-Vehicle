package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.compat.mixin.DnvPersistentEntitySectionsAccess;
import com.liucodex.doggiesnewvehicle.compat.mixin.DnvServerLevelEntityManagerAccess;
import com.liucodex.doggiesnewvehicle.compat.mixin.DnvEntitySectionEntitiesAccess;
import com.liucodex.doggiesnewvehicle.compat.mixin.DnvEntitySectionKeysAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One staggered broad-phase scan shared by every weapon station on a DNV boss.
 * Only UUID values cross ticks. Live entities are resolved from the already-loaded server level
 * for the current control pass and are followed by cheap alive/range checks at the point of use.
 */
public final class DnvTargetCandidateCache {
    static final int MAX_CANDIDATES = 32;
    private final int requestedInterval;
    private int lastScanTick = DnvTargetingBudget.NEVER_SCANNED;
    private List<UUID> candidateIds = List.of();
    private Set<UUID> authorizedIds = Set.of();
    private UUID pinnedExternalId;
    private boolean pinnedExternalWasNativeCandidate;
    private ScanState scan;
    private boolean refreshAfterMovement;
    @Nullable private ServerLevel budgetLevel;
    @Nullable private UUID budgetOwnerId;

    public DnvTargetCandidateCache(int requestedInterval) {
        this.requestedInterval = requestedInterval;
    }

    public boolean refreshIfDue(
            Entity source,
            double range,
            Predicate<Entity> acceptedTarget) {
        return refreshIfDue(source, range, acceptedTarget, requestedInterval);
    }

    public boolean refreshIfDue(
            Entity source,
            double range,
            Predicate<Entity> acceptedTarget,
            int interval) {
        return refreshScoredIfDue(source, range, range,
                candidate -> acceptedTarget.test(candidate) ? source.distanceToSqr(candidate) : Double.POSITIVE_INFINITY,
                interval, false);
    }

    /** A non-finite score rejects the candidate. Lower scores retain priority in the existing bounded queue. */
    public boolean refreshScoredIfDue(Entity source, double range, double verticalRange,
            ToDoubleFunction<Entity> score) {
        return refreshScoredIfDue(source, range, verticalRange, score, requestedInterval, false);
    }

    /** Keep the initial stagger, then time each batch from the start of the previous batch. */
    public boolean refreshScoredOnStartCadence(Entity source, double range, double verticalRange,
            ToDoubleFunction<Entity> score) {
        return refreshScoredIfDue(source, range, verticalRange, score, requestedInterval, true);
    }

    private boolean refreshScoredIfDue(Entity source, double range, double verticalRange,
            ToDoubleFunction<Entity> score, int interval, boolean startCadence) {
        if (!(source.level() instanceof ServerLevel level) || !Double.isFinite(range)
                || !Double.isFinite(verticalRange) || range <= 0.0D || verticalRange <= 0.0D) return false;
        UUID ownerId = source.getUUID();
        if (budgetLevel != null && (budgetLevel != level || !ownerId.equals(budgetOwnerId))) {
            // A UUID from the old level must not authorize a different entity in this level.
            clear();
        }
        budgetLevel = level;
        budgetOwnerId = ownerId;
        if (scan != null && (scan.level != level || source.tickCount < scan.startedAtTick
                || level.getGameTime() < scan.startedAtGameTime
                || Double.compare(range, scan.range) != 0
                || Double.compare(verticalRange, scan.verticalRange) != 0
                || shouldRestartMovingScan(scan.origin, source.position(), range,
                        scan.startedSteps))) {
            scan = null;
        }
        if (source.tickCount < lastScanTick) lastScanTick = DnvTargetingBudget.NEVER_SCANNED;
        if (scan == null && !refreshAfterMovement && !(startCadence
                ? DnvTargetingBudget.isScanDueFromStart(source.tickCount, source.getId(), lastScanTick, interval)
                : DnvTargetingBudget.isScanDue(source.tickCount, source.getId(), lastScanTick, interval))) {
            return false;
        }
        if (scan == null) {
            scan = new ScanState(level, source.position(),
                    source.getBoundingBox(), range, verticalRange, source.tickCount);
            refreshAfterMovement = false;
        }
        if (!DnvTargetingBudget.admitScanStep(level, ownerId)) return false;
        // The public getter can be wrapped by sub-level/physics mods. The server entity
        // manager still owns the loaded sections used by this storage-coordinate pager.
        var manager = ((DnvServerLevelEntityManagerAccess) level).dnv$getEntityManager();
        EntitySectionStorage<Entity> sections =
                ((DnvPersistentEntitySectionsAccess) manager).dnv$getSectionStorage();
        var sectionKeys = ((DnvEntitySectionKeysAccess) sections).dnv$getSectionIds();
        ScanState activeScan = scan;
        activeScan.startedSteps = true;
        var step = activeScan.pager.step(sectionKeys, key -> {
            EntitySection<Entity> section = sections.getSection(key);
            return section == null || !section.getStatus().isAccessible()
                    ? null : ((DnvEntitySectionEntitiesAccess) section).dnv$getStorage().getAllInstances();
        }, candidate -> {
            if (candidate == source || !candidate.getBoundingBox().intersects(activeScan.bounds)) return;
            double priority = score.applyAsDouble(candidate);
            if (Double.isFinite(priority)) activeScan.accept(candidate.getUUID(), priority);
        });
        if (!step.complete()) return false;
        lastScanTick = startCadence ? activeScan.startedAtTick : source.tickCount;
        refreshAfterMovement = movedOutsideScanOrigin(
                activeScan.origin, source.position(), range);
        List<UUID> refreshed = scan.ids();
        scan = null;
        pinnedExternalId = null;
        pinnedExternalWasNativeCandidate = false;
        replaceIds(refreshed);
        return true;
    }

    /** Pins the one explicit provider assignment without making any ambient candidate eligible. */
    public void pinExternal(Entity assigned) {
        if (assigned == null) {
            clearPinnedExternal();
            return;
        }
        clearPinnedExternal();
        pinnedExternalId = assigned.getUUID();
        pinnedExternalWasNativeCandidate = authorizedIds.contains(pinnedExternalId);
        List<UUID> updated = new ArrayList<>(Math.min(MAX_CANDIDATES, candidateIds.size() + 1));
        updated.add(assigned.getUUID());
        for (UUID candidateId : candidateIds) {
            if (updated.size() >= MAX_CANDIDATES) {
                break;
            }
            if (!assigned.getUUID().equals(candidateId)) {
                updated.add(candidateId);
            }
        }
        replaceIds(updated);
    }

    public void clearPinnedExternal() {
        if (pinnedExternalId == null) {
            return;
        }
        UUID released = pinnedExternalId;
        pinnedExternalId = null;
        if (pinnedExternalWasNativeCandidate) {
            pinnedExternalWasNativeCandidate = false;
            return;
        }
        pinnedExternalWasNativeCandidate = false;
        List<UUID> updated = candidateIds.stream()
                .filter(candidateId -> !released.equals(candidateId))
                .toList();
        replaceIds(updated);
    }

    public List<Entity> candidates(Entity source) {
        if (!(source.level() instanceof ServerLevel level)) {
            return List.of();
        }
        List<Entity> resolved = new ArrayList<>(candidateIds.size());
        for (UUID candidateId : candidateIds) {
            Entity candidate = level.getEntity(candidateId);
            if (candidate != null) {
                resolved.add(candidate);
            }
        }
        return List.copyOf(resolved);
    }

    public boolean isAuthorized(Entity entity) {
        return entity != null && authorizedIds.contains(entity.getUUID());
    }

    @Nullable
    public Entity select(
            Entity source,
            @Nullable Entity current,
            Predicate<Entity> eligible,
            ToDoubleFunction<Entity> distance,
            boolean ignoreForcedTaunt
    ) {
        if (candidateIds.isEmpty()) {
            return null;
        }
        List<Entity> live = candidates(source);
        return selectBest(live, current, eligible, distance, Entity::getUUID);
    }

    @Nullable
    public static <T> T selectBest(
            List<T> live,
            @Nullable T current,
            Predicate<T> eligible,
            ToDoubleFunction<T> distance,
            Function<T, UUID> identity) {
        // A retained target wins by policy. Check it before ray-testing the other 31 candidates
        // independently for each weapon station on every tick.
        if (current != null && live.contains(current) && eligible.test(current)) return current;
        T nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (T candidate : live) {
            if (candidate == current || !eligible.test(candidate)) continue;
            double candidateDistance = distance.applyAsDouble(candidate);
            if (candidateDistance < nearestDistance
                    || candidateDistance == nearestDistance
                            && nearest != null
                            && compareStableIds(identity.apply(candidate), identity.apply(nearest)) < 0) {
                nearest = candidate;
                nearestDistance = candidateDistance;
            }
        }
        return nearest;
    }

    /** Releases every live entity reference when the owning unit leaves combat. */
    public void clear() {
        if (budgetLevel != null && budgetOwnerId != null) {
            DnvTargetingBudget.releaseScanOwner(budgetLevel, budgetOwnerId);
        }
        budgetLevel = null;
        budgetOwnerId = null;
        scan = null;
        refreshAfterMovement = false;
        lastScanTick = DnvTargetingBudget.NEVER_SCANNED;
        candidateIds = List.of();
        authorizedIds = Set.of();
        pinnedExternalId = null;
        pinnedExternalWasNativeCandidate = false;
    }

    public int lastScanTick() {
        return lastScanTick;
    }

    public boolean refreshedAt(int tick) {
        return lastScanTick == tick;
    }

    static boolean movedOutsideScanOrigin(Vec3 origin, Vec3 current, double range) {
        double threshold = Math.max(16.0D, range * 0.25D);
        return current.distanceToSqr(origin) > threshold * threshold;
    }

    static boolean shouldRestartMovingScan(
            Vec3 origin, Vec3 current, double range, boolean startedSteps) {
        return !startedSteps && movedOutsideScanOrigin(origin, current, range);
    }

    public static int compareStableIds(UUID left, UUID right) {
        int most = Long.compareUnsigned(
                left.getMostSignificantBits(), right.getMostSignificantBits());
        return most != 0
                ? most
                : Long.compareUnsigned(
                        left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    static <T> List<T> boundedTopK(
            List<T> candidates,
            Predicate<T> eligible,
            ToDoubleFunction<T> distance,
            Function<T, UUID> identity,
            int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        if (limit == 0) return List.of();
        Comparator<T> ranking = Comparator.comparingDouble(distance)
                .thenComparing(identity, DnvTargetCandidateCache::compareStableIds);
        PriorityQueue<T> best = new PriorityQueue<>(ranking.reversed());
        for (T candidate : candidates) {
            if (!eligible.test(candidate)) continue;
            if (best.size() < limit) best.add(candidate);
            else if (ranking.compare(candidate, best.peek()) < 0) {
                best.remove();
                best.add(candidate);
            }
        }
        List<T> ordered = new ArrayList<>(best);
        ordered.sort(ranking);
        return List.copyOf(ordered);
    }

    private void replaceIds(List<UUID> replacement) {
        Set<UUID> ids = new HashSet<>(replacement.size());
        for (UUID candidateId : replacement) {
            ids.add(candidateId);
        }
        candidateIds = List.copyOf(replacement);
        authorizedIds = Set.copyOf(ids);
    }

    private static final class ScanState {
        private static final Comparator<RankedId> RANKING = Comparator
                .comparingDouble(RankedId::distance)
                .thenComparing(RankedId::id, DnvTargetCandidateCache::compareStableIds);
        private final ServerLevel level;
        private final Vec3 origin;
        private final AABB bounds;
        private final double range;
        private final double verticalRange;
        private final int startedAtTick;
        private final long startedAtGameTime;
        private final DnvTargetSectionPager<Entity> pager;
        private boolean startedSteps;
        private final PriorityQueue<RankedId> best = new PriorityQueue<>(RANKING.reversed());
        private final Set<UUID> seen = new HashSet<>();

        private ScanState(ServerLevel level, Vec3 origin, AABB sourceBox, double range, double verticalRange,
                int startedAtTick) {
            this.level = level;
            this.origin = origin;
            this.bounds = sourceBox.inflate(range, verticalRange, range);
            this.range = range;
            this.verticalRange = verticalRange;
            this.startedAtTick = startedAtTick;
            this.startedAtGameTime = level.getGameTime();
            // SBW rebuilds the world AABB from rotating body/turret OBBs every tick.
            // Finish this bounded snapshot instead of restarting on normal pose changes.
            // The next scan captures the current box; consumers recheck live eligibility.
            this.pager = new DnvTargetSectionPager<>(bounds);
        }

        private void accept(UUID id, double distance) {
            if (!seen.add(id)) return;
            RankedId candidate = new RankedId(id, distance);
            if (best.size() < MAX_CANDIDATES) best.add(candidate);
            else if (RANKING.compare(candidate, best.peek()) < 0) {
                seen.remove(best.remove().id());
                best.add(candidate);
            } else seen.remove(id);
        }

        private List<UUID> ids() {
            List<RankedId> ordered = new ArrayList<>(best);
            ordered.sort(RANKING);
            return ordered.stream().map(RankedId::id).toList();
        }
    }

    private record RankedId(UUID id, double distance) {}
}
