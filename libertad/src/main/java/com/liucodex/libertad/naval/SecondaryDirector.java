package com.liucodex.libertad.naval;

import com.liucodex.libertad.naval.MountGeometry.Angles;
import com.liucodex.libertad.naval.SalvoControl.Arc;
import com.liucodex.libertad.naval.SalvoControl.Control;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One ship owns the schedule and assignments. The world adapter supplies loaded, visible observations. */
public final class SecondaryDirector {
    public enum Mode { OFF, ACTIVE, RETURNING }
    public enum Relation { HOSTILE, FRIENDLY, NEUTRAL }

    public record Contact(UUID id, Relation relation, double priority) {}

    /** One server-evaluated clear solution per target for this turret. */
    public record Station(String id, Arc arc, Map<UUID, Angles> clearSolutions) {
        public Station {
            if (clearSolutions.size() > TargetAllocator.MAX_CANDIDATES) {
                throw new IllegalArgumentException("Too many per-station solutions");
            }
            clearSolutions = Map.copyOf(clearSolutions);
        }
    }

    public static final class Scan {
        private Scan() {}
    }

    private Control controller;
    private Mode mode = Mode.OFF;
    private long nextScanAt;
    private Scan inFlight;
    private Map<String, UUID> assignments = Map.of();
    private final Map<String, List<UUID>> reserves = new HashMap<>();

    public Mode mode() { return mode; }
    public Map<String, UUID> assignments() { return assignments; }

    public void updateController(Control current) {
        if (Objects.equals(controller, current)) return;
        controller = current;
        stop();
    }

    public boolean setEnabled(Control requester, boolean enabled) {
        if (controller == null || !controller.equals(requester)) return false;
        if (enabled) mode = Mode.ACTIVE;
        else stop();
        return true;
    }

    private void stop() {
        if (mode == Mode.ACTIVE) mode = Mode.RETURNING;
        inFlight = null;
        assignments = Map.of();
        reserves.clear();
    }

    public void reachedHome() {
        if (mode == Mode.RETURNING) mode = Mode.OFF;
    }

    public void restoreIdle(boolean awayFromHome) {
        controller = null;
        inFlight = null;
        assignments = Map.of();
        reserves.clear();
        nextScanAt = 0;
        mode = awayFromHome ? Mode.RETURNING : Mode.OFF;
    }

    public Scan beginScan(long gameTime) {
        if (mode != Mode.ACTIVE || inFlight != null || gameTime < nextScanAt) return null;
        nextScanAt = Math.addExact(gameTime, 100);
        return inFlight = new Scan();
    }

    /** The scan reserves candidate slots for still-valid tracked contacts before adding discoveries. */
    public boolean completeScan(Scan token, List<Contact> contacts, List<Station> stations) {
        return publishScan(token, contacts, stations, true);
    }

    /** Publish a near-first batch while the same bounded sweep continues outward. */
    public boolean publishScan(Scan token, List<Contact> contacts, List<Station> stations, boolean finished) {
        if (token == null || token != inFlight || mode != Mode.ACTIVE) return false;
        if (finished) inFlight = null;
        if (contacts.size() > TargetAllocator.MAX_CANDIDATES || stations.size() > 18) {
            throw new IllegalArgumentException("Secondary scan exceeded its input budget");
        }
        List<TargetAllocator.Candidate> candidates = new ArrayList<>();
        var hostile = new HashSet<UUID>();
        for (Contact contact : contacts) {
            if (contact.relation() == Relation.HOSTILE) {
                candidates.add(new TargetAllocator.Candidate(contact.id(), contact.priority()));
                hostile.add(contact.id());
            }
        }
        List<TargetAllocator.Station> eligible = new ArrayList<>();
        var observed = contacts.stream().map(Contact::id).collect(java.util.stream.Collectors.toSet());
        candidates.sort(Comparator.comparingDouble(TargetAllocator.Candidate::priority)
                .thenComparing(TargetAllocator.Candidate::targetId));
        for (Station station : stations) {
            var targets = new HashSet<UUID>();
            for (var entry : station.clearSolutions().entrySet()) {
                if (hostile.contains(entry.getKey()) && station.arc().allows(entry.getValue())) targets.add(entry.getKey());
            }
            eligible.add(new TargetAllocator.Station(station.id(), assignments.get(station.id()), targets, null, true, false));
            var previous = reserves.getOrDefault(station.id(), List.of());
            var available = new ArrayList<>(candidates.stream().map(TargetAllocator.Candidate::targetId)
                    .filter(targets::contains).toList());
            for (UUID id : previous) {
                // A different page is not evidence that a previously clear contact disappeared.
                // Observed rejections are removed now; all cached replacements are rechecked by retain.
                if (available.size() >= TargetAllocator.MAX_CANDIDATES) break;
                if (!observed.contains(id) && !available.contains(id)) available.add(id);
            }
            reserves.put(station.id(), available);
        }
        assignments = TargetAllocator.assign(candidates, eligible);
        return true;
    }

    /** Run between broad scans to drop a target immediately when its solution becomes invalid. */
    public boolean retain(String station, UUID target, Relation relation, Arc arc, Angles aim, boolean clear) {
        if (mode != Mode.ACTIVE || target == null || !target.equals(assignments.get(station))) return false;
        if (relation == Relation.HOSTILE && clear && aim != null && arc.allows(aim)) return true;
        var remaining = new HashMap<>(assignments);
        remaining.remove(station);
        var reserve = reserves.get(station);
        if (reserve != null) {
            reserve.remove(target);
            if (!reserve.isEmpty()) {
                var count = new HashMap<UUID, Integer>();
                for (UUID other : remaining.values()) count.merge(other, 1, Integer::sum);
                UUID replacement = reserve.stream().min(Comparator.comparingInt(id -> count.getOrDefault(id, 0))).orElseThrow();
                remaining.put(station, replacement);
            }
        }
        assignments = Map.copyOf(remaining);
        return false;
    }
}
