package com.liucodex.libertad.naval;

import com.liucodex.libertad.naval.MountGeometry.Angles;
import com.liucodex.libertad.naval.MountGeometry.BarrelPose;
import com.liucodex.libertad.naval.MountGeometry.Point;
import com.liucodex.libertad.naval.SalvoControl.Arc;
import com.liucodex.libertad.naval.SalvoControl.Control;
import com.liucodex.libertad.naval.SalvoControl.Result;
import com.liucodex.libertad.naval.SalvoControl.YawSector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FireControlCheck {
    private static final Angles LEVEL = new Angles(0, 0);
    private static final Control MASTER = new Control(UUID.fromString("0b6e1ec0-6139-476d-a585-027556b6ff08"), 2);

    public static void main(String[] args) throws Exception {
        List<MountGeometry> mounts = MountGeometry.load();
        require(mounts.stream().filter(m -> m.id().startsWith("main_")).count() == 6, "six main stations");
        require(mounts.stream().filter(m -> m.id().startsWith("secondary_234_")).count() == 10, "ten 234 mm stations");
        require(mounts.stream().filter(m -> m.id().startsWith("secondary_127_")).count() == 8, "eight 127 mm stations");
        for (MountGeometry mount : mounts) checkMount(mount);
        checkSalvo(mounts.get(0), mounts.get(1));
        checkSecondaries();
        checkAirDefense();
        checkCrew(mounts);
        require(ShellRange.MAIN == 720 && ShellRange.SECONDARY == 600, "configured fire ranges");
        checkRange(ShellRange.SECONDARY);
        checkRange(ShellRange.MAIN);
        System.out.println("PASS: 24 model-derived mounts / 40 barrels, twin admission, crew changes and side arcs, secondary scheduling, 600/720 range");
    }

    private static void checkAirDefense() {
        require(!AirDefenseRules.enabled(false, false, true), "mode one AA must follow the secondary toggle");
        require(AirDefenseRules.enabled(false, true, true), "mode one active secondaries arm AA");
        require(AirDefenseRules.enabled(true, false, true), "mode two arms AA independently");
        require(!AirDefenseRules.enabled(true, true, false), "no power disables AA in either mode");
        require(AirDefenseRules.PULSE_TICKS == 8 && AirDefenseRules.PULSE_DAMAGE == 45,
                "fixed 45-point pulse every eight ticks");
        require(AirDefenseRules.covers(0, 20, 20, 10, 100, false) && AirDefenseRules.covers(1, -20, -20, 10, 100, false),
                "mirror the forward and broadside sectors");
        require(AirDefenseRules.covers(2, 15, 12, 10, -140, false) && AirDefenseRules.covers(3, -15, -12, 10, -140, false),
                "both after sectors cover rear approaches");
        require(!AirDefenseRules.covers(0, -20, -20, 10, 100, false) && !AirDefenseRules.covers(0, 20, 20, 20, 149, false),
                "no across-deck fire or out-of-radius fire");
    }

    private static void checkCrew(List<MountGeometry> mounts) {
        var crew = new CrewControl();
        var helm = MASTER.player();
        var port = UUID.fromString("2ed8302c-5c71-4fc6-8e02-1eb4e4c455d0");
        var starboard = UUID.fromString("5104e668-f36b-4b05-9631-9eb3f48be28b");
        crew.assign(0, helm);
        crew.assign(1, port);
        crew.assign(2, starboard);
        crew.request(1, port, 100);
        require(!crew.consume(1, 100) && !crew.toggle(port, 100), "observer controlled weapons or mode");
        crew.request(0, helm, 100);
        require(crew.toggle(helm, 100) && !crew.consume(0, 100), "mode change admitted an old helm request");
        require(!crew.toggle(helm, 101), "duplicate mode press flipped twice");
        int[] counts = new int[3];
        for (int i = 6; i < mounts.size(); i++) {
            double x = mounts.get(i).yawCenter().x();
            int seat = CrewControl.sideSeat(x);
            counts[seat]++;
            require(CrewControl.controls(true, seat, i, x) && !CrewControl.controls(true, 3 - seat, i, x)
                    && !CrewControl.controls(true, 0, i, x) && CrewControl.controls(false, 0, i, x),
                    "secondary escaped the assigned side: " + mounts.get(i).id());
        }
        require(counts[1] == 9 && counts[2] == 9, "side banks each need nine mounts");
        require(!CrewControl.controls(true, 1, 0, 0) && CrewControl.controls(true, 0, 0, 0), "side seat took a main gun");
        crew.request(2, port, 110);
        require(!crew.consume(2, 110), "cross-seat request was admitted");
        Control old = crew.controller(1);
        crew.request(1, port, 110);
        crew.assign(1, null);
        crew.assign(1, port);
        require(!crew.current(1, old) && !crew.consume(1, 110), "old consent survived a leave and rejoin");
        crew.request(1, port, 110);
        require(crew.consume(1, 111) && !crew.consume(1, 111), "fire intent must be single use");
        crew.request(1, port, 110);
        require(!crew.consume(1, 112), "expired request fired");
        crew.request(2, starboard, 120);
        require(crew.toggle(helm, 120) && !crew.consume(2, 120), "switch to solo left a live gunner request");
        crew.restore(true);
        require(crew.multicrew() && crew.controller(0) == null && crew.controller(1) == null, "restored stale crew");
        for (boolean firstPerson : new boolean[] {true, false}) {
            for (int seat = 1; seat <= 2; seat++) {
                float bow = 173, center = bow + (seat == 1 ? -90 : 90);
                for (int delta = -360; delta <= 360; delta += 5) {
                    float yaw = CrewControl.viewYaw(center + delta, bow, seat, firstPerson);
                    require(Math.abs(Math.IEEEremainder(yaw - center, 360)) <= 90, "camera saw the opposite side");
                    if (Math.abs(delta) <= 90) require(yaw == center + delta, "camera clipped legal bearing");
                }
            }
        }
    }

    private static void checkMount(MountGeometry mount) {
        var level = mount.at(LEVEL);
        var raised = mount.at(new Angles(27, 30));
        var lowered = mount.at(new Angles(-24, -5));
        for (int i = 0; i < level.size(); i++) {
            near(0, level.get(i).muzzle().minus(mount.barrels().get(i).muzzle()).lengthSquared());
            near(0.5, raised.get(i).forward().y());
            near(Math.sin(Math.toRadians(-5)), lowered.get(i).forward().y());
            near(level.get(i).muzzle().minus(mount.pitchCenter()).lengthSquared(),
                    raised.get(i).muzzle().minus(raised.get(i).pitchCenter()).lengthSquared());
        }
        if (level.size() == 2) {
            near(level.get(0).muzzle().minus(level.get(1).muzzle()).lengthSquared(),
                    raised.get(0).muzzle().minus(raised.get(1).muzzle()).lengthSquared());
        }
    }

    private static void checkSalvo(MountGeometry fore, MountGeometry other) {
        Arc arc = new Arc(List.of(new YawSector(-80, 80)), -5, 35);
        Arc aft = new Arc(List.of(new YawSector(165, -165)), -5, 35);
        require(aft.allows(new Angles(181, 0)) && aft.allows(new Angles(-180, 0)) && !aft.allows(LEVEL), "wrap-around arc");
        require(arc.allows(new Angles(0, -5)) && arc.allows(new Angles(0, 35)), "inclusive pitch stops");
        require(!arc.allows(new Angles(0, -5.001)) && !arc.allows(new Angles(0, 35.001)), "reject outside pitch stops");

        var port = new Port();
        port.blocked = fore.barrels().get(1).gunId();
        require(SalvoControl.executeTwin(MASTER, fore, arc, LEVEL, LEVEL, .1, port) == Result.REJECTED, "blocked right barrel");
        require(port.fired.isEmpty(), "blocked twin has no side effect");
        require(SalvoControl.executeTwin(MASTER, other, arc, LEVEL, LEVEL, .1, port) == Result.FIRED, "other turret fires independently");
        require(port.fired.equals(other.barrels().stream().map(MountGeometry.Barrel::gunId).toList()), "two named barrels admitted");
        port.fired.clear();
        port.blocked = null;
        port.unloaded = fore.barrels().get(0).gunId();
        require(SalvoControl.executeTwin(MASTER, fore, arc, LEVEL, LEVEL, .1, port) == Result.REJECTED, "one barrel reloading");
        port.unloaded = null;
        require(SalvoControl.executeTwin(new Control(MASTER.player(), 1), fore, arc, LEVEL, LEVEL, .1, port) == Result.REJECTED,
                "stale control epoch");
        require(SalvoControl.executeTwin(MASTER, fore, arc, LEVEL, new Angles(0, 15), .1, port) == Result.REJECTED,
                "still rotating, not aligned");
        require(port.fired.isEmpty(), "all rejected requests produce zero shots");
        port.worldReject = fore.barrels().get(1).gunId();
        require(SalvoControl.executeTwin(MASTER, fore, arc, LEVEL, LEVEL, .1, port) == Result.PARTIAL, "world rejects only one barrel");
        require(port.fired.equals(List.of(fore.barrels().get(0).gunId())), "partial result accounts for one real shot");
    }

    private static void checkRange(double radius) {
        Point origin = new Point(-12, 25, 9);
        var range = new ShellRange(origin, radius, 200);
        var step = range.next(origin.add(new Point(radius - .001, 0, 0)), new Point(15, 0, 0), 20);
        near(radius, step.endpoint().x() - origin.x());
        require(step.trace() && step.discardIfMiss(), "clip high speed overshoot before tracing");
        var inside = range.next(origin, new Point(radius - .001, 0, 0), 20);
        require(inside.trace() && !inside.discardIfMiss(), "inside range stays live");
        var edge = range.next(origin, new Point(radius, 0, 0), 20);
        require(edge.trace() && edge.discardIfMiss(), "exact edge traces once");
        var outside = range.next(origin.add(new Point(radius + .001, 0, 0)), new Point(-15, 0, 0), 20);
        require(!outside.trace() && outside.discardIfMiss(), "outside shot cannot reenter");
        var diagonal = range.next(origin, new Point(radius, radius, radius), 20);
        near(radius * radius, diagonal.endpoint().minus(origin).lengthSquared());
        require(!range.next(origin, new Point(15, 0, 0), 200).trace(), "absolute expiry before collision");
    }

    private static void checkSecondaries() {
        var director = new SecondaryDirector();
        var hostile = SecondaryDirector.Relation.HOSTILE;
        UUID air = new UUID(0, 11);
        UUID ground = new UUID(0, 12);
        UUID neutral = new UUID(0, 13);
        UUID reserve = new UUID(0, 14);
        var contacts = List.of(new SecondaryDirector.Contact(air, hostile, 1),
                new SecondaryDirector.Contact(ground, hostile, 2),
                new SecondaryDirector.Contact(neutral, SecondaryDirector.Relation.NEUTRAL, 0),
                new SecondaryDirector.Contact(reserve, hostile, 3));
        var arc = new Arc(List.of(new YawSector(-90, 90)), -5, 45);
        var airAim = new Angles(10, 30);
        var stations = List.of(new SecondaryDirector.Station("port", arc,
                        Map.of(air, new Angles(10, 55), ground, LEVEL, neutral, LEVEL)),
                new SecondaryDirector.Station("starboard", arc, Map.of(air, airAim, neutral, LEVEL, reserve, LEVEL)));
        require(director.beginScan(0) == null, "unoccupied ship never scans");
        director.updateController(MASTER);
        require(director.setEnabled(MASTER, true), "master enables the scan");
        var token = director.beginScan(0);
        require(token != null && director.beginScan(120) == null, "never overlaps paged scans");
        require(director.publishScan(token, contacts, stations, false), "near batch publishes before the sweep finishes");
        require(director.beginScan(120) == null, "a partial publication must not start a concurrent scan");
        require(director.assignments().equals(Map.of("port", ground, "starboard", air)), "different per-turret eligibility");
        // A later distant page includes retained live targets but not the earlier nearby reserve.
        require(director.completeScan(token, contacts.subList(0, 3), stations.stream()
                .map(s -> new SecondaryDirector.Station(s.id(), s.arc(), s.clearSolutions().entrySet().stream()
                        .filter(e -> !e.getKey().equals(reserve)).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .toList()), "commits the final batch of the same sweep");
        require(!director.retain("starboard", air, hostile, arc, new Angles(10, 46), true), "air target leaves pitch limit");
        require(director.assignments().equals(Map.of("port", ground, "starboard", reserve)),
                "the turret switches to its reserve without waiting for another sweep");
        require(director.retain("port", ground, hostile, arc, LEVEL, true), "remaining target continues");
        require(!director.retain("port", ground, hostile, arc, LEVEL, false), "obstruction invalidates live track");
        director.setEnabled(MASTER, false);
        director.setEnabled(MASTER, true);
        require(director.beginScan(99) == null, "toggle does not bypass scan cooldown");
        token = director.beginScan(100);
        require(token != null, "100 tick scan interval");
        director.updateController(null);
        require(director.mode() == SecondaryDirector.Mode.RETURNING && director.assignments().isEmpty(), "exit clears target and requests home");
        require(!director.completeScan(token, contacts, stations), "late scan after leaving is ignored");
        director.reachedHome();
        director.updateController(new Control(MASTER.player(), 3));
        require(director.mode() == SecondaryDirector.Mode.OFF && !director.setEnabled(MASTER, true), "reboard stays off and rejects old epoch");
    }

    private static void near(double expected, double actual) {
        require(Math.abs(expected - actual) < 1e-6, expected + " != " + actual);
    }

    private static void require(boolean condition, String scenario) {
        if (!condition) throw new AssertionError(scenario);
    }

    private static final class Port implements SalvoControl.NativeGuns {
        private final List<String> fired = new ArrayList<>();
        private String blocked;
        private String unloaded;
        private String worldReject;

        public boolean isCurrentMaster(Control control) { return MASTER.equals(control); }
        public boolean ready(String gunId) { return !gunId.equals(unloaded); }
        public boolean clear(BarrelPose barrel) { return !barrel.gunId().equals(blocked); }
        public boolean tryFire(BarrelPose barrel) {
            if (barrel.gunId().equals(worldReject)) return false;
            fired.add(barrel.gunId());
            return true;
        }
    }
}
