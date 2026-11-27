package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.core.DnvMultiStationTargetAllocator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/** Transient station locks. Only the owning ship's server tick advances this state. */
public final class MothershipStationTracking {
    private static final int MIN_HOLD_TICKS = 40, BALANCE_INTERVAL_TICKS = 20;
    private static final int RANGE_GRACE_TICKS = 40, STALLED_TICKS = 60;
    private static final MothershipArmament.Station[] STATIONS = MothershipArmament.Station.values();
    final UUID[] targets = new UUID[STATIONS.length];
    final long[] nextAttempt = new long[STATIONS.length], acquiredAt = new long[STATIONS.length];
    final long[] nextBalance = new long[STATIONS.length], outOfRangeSince = new long[STATIONS.length];
    final int[] blockedAttempts = new int[STATIONS.length], stalledTicks = new int[STATIONS.length];

    @FunctionalInterface public interface Eligibility<T> {
        boolean allowed(int station, T target, double rangeGrace);
    }

    public <T> List<T> select(long now, Function<UUID,T> resolve, Supplier<List<T>> candidates,
            Function<T,UUID> identity, ToDoubleFunction<T> distance, Eligibility<T> eligibility) {
        List<T> prior = new ArrayList<>(STATIONS.length);
        boolean[] retained = new boolean[STATIONS.length], acquiring = new boolean[STATIONS.length];
        boolean[] redistributing = new boolean[STATIONS.length];
        boolean needsFrame = false;
        for (int i = 0; i < STATIONS.length; i++) {
            T target = targets[i] == null ? null : resolve.apply(targets[i]);
            prior.add(target);
            boolean strict = eligibility.allowed(i, target, 0);
            if (strict) outOfRangeSince[i] = 0;
            else if (outOfRangeSince[i] == 0) outOfRangeSince[i] = now + 1;
            boolean grace = !strict && now >= outOfRangeSince[i] - 1
                    && now - (outOfRangeSince[i] - 1) < RANGE_GRACE_TICKS
                    && eligibility.allowed(i, target, 8);
            retained[i] = !exhausted(i) && (strict || grace);
            acquiring[i] = !retained[i] && now >= nextAttempt[i];
            redistributing[i] = retained[i] && now >= nextBalance[i]
                    && now >= acquiredAt[i] && now - acquiredAt[i] >= MIN_HOLD_TICKS;
            needsFrame |= acquiring[i] || redistributing[i];
        }
        List<T> frame = needsFrame ? candidates.get() : List.of();
        Map<UUID,T> live = new HashMap<>();
        List<DnvMultiStationTargetAllocator.Candidate> ranked = new ArrayList<>(frame.size());
        for (T candidate : frame) {
            double squared = distance.applyAsDouble(candidate);
            if (Double.isFinite(squared) && squared >= 0) {
                UUID id = identity.apply(candidate);
                live.put(id, candidate);
                ranked.add(new DnvMultiStationTargetAllocator.Candidate(id, squared));
            }
        }
        List<DnvMultiStationTargetAllocator.Station> requests = new ArrayList<>(STATIONS.length);
        for (int i = 0; i < STATIONS.length; i++) {
            UUID previous = targets[i];
            if (prior.get(i) != null) live.putIfAbsent(previous, prior.get(i));
            Set<UUID> eligible = new HashSet<>();
            if (retained[i]) eligible.add(previous);
            if (acquiring[i] || redistributing[i]) {
                for (T candidate : frame) {
                    if (eligibility.allowed(i, candidate, 0)) eligible.add(identity.apply(candidate));
                }
            }
            requests.add(new DnvMultiStationTargetAllocator.Station(STATIONS[i].name(),
                    retained[i] ? previous : null, eligible, exhausted(i) ? previous : null,
                    acquiring[i], redistributing[i]));
        }
        Map<String,UUID> allocation = DnvMultiStationTargetAllocator.assign(ranked, requests);
        List<T> selected = new ArrayList<>(STATIONS.length);
        for (int i = 0; i < STATIONS.length; i++) {
            if (!retained[i] && !acquiring[i]) {
                targets[i] = null; outOfRangeSince[i] = 0; stalledTicks[i] = 0;
                selected.add(null);
                continue;
            }
            UUID id = allocation.get(STATIONS[i].name());
            if (!java.util.Objects.equals(id, targets[i])) {
                targets[i] = id; acquiredAt[i] = now; resetFailures(i);
                nextBalance[i] = now + MIN_HOLD_TICKS;
            } else if (acquiring[i]) resetFailures(i);
            else if (redistributing[i]) nextBalance[i] = now + BALANCE_INTERVAL_TICKS;
            T target = id == null ? null : live.get(id);
            if (target == null && id != null) target = resolve.apply(id);
            if (target == null && acquiring[i]) nextAttempt[i] = now + 10;
            selected.add(target);
        }
        return selected;
    }

    public UUID targetId(int index) { return targets[index]; }

    public boolean mayAttempt(int index, long now) { return now >= nextAttempt[index]; }
    public void stalled(int index) { stalledTicks[index]++; }
    public void recordShot(int index, long now, boolean accepted) {
        if (accepted) { blockedAttempts[index] = 0; stalledTicks[index] = 0; }
        else blockedAttempts[index]++;
        nextAttempt[index] = now + (accepted ? 1 : 10);
    }
    public void clear() {
        Arrays.fill(targets, null); Arrays.fill(nextAttempt, 0);
        Arrays.fill(acquiredAt, 0); Arrays.fill(nextBalance, 0);
        Arrays.fill(blockedAttempts, 0); Arrays.fill(stalledTicks, 0); Arrays.fill(outOfRangeSince, 0);
    }
    private boolean exhausted(int index) { return blockedAttempts[index] >= 3 || stalledTicks[index] >= STALLED_TICKS; }
    private void resetFailures(int index) {
        blockedAttempts[index] = 0; stalledTicks[index] = 0; outOfRangeSince[index] = 0;
    }
}
