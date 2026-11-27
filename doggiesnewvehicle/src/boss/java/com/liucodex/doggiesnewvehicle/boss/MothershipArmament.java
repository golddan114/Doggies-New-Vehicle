package com.liucodex.doggiesnewvehicle.boss;

import java.util.Arrays;
import java.util.Objects;

/** Six independent station clocks. Targeting and calibrated model transforms are supplied by the entity. */
public final class MothershipArmament {
    public enum Kind {
        AUTOCANNON(10, 1, 10, 5, 1, false, 96),
        HE_ROCKET(100, 1, 20, 15, 2, false, 96),
        TWIN_LASER(100, 2, 50, 0, 0, true, 160);

        public final int reloadTicks;
        public final int barrels;
        public final int damage;
        public final int blastDamage;
        public final int blastRadius;
        public final boolean piercing;
        public final int range;

        Kind(int reloadTicks, int barrels, int damage, int blastDamage, int blastRadius, boolean piercing, int range) {
            this.reloadTicks = reloadTicks;
            this.barrels = barrels;
            this.damage = damage;
            this.blastDamage = blastDamage;
            this.blastRadius = blastRadius;
            this.piercing = piercing;
            this.range = range;
        }
    }

    public enum Station {
        FRONT_PORT(Kind.AUTOCANNON), FRONT_STARBOARD(Kind.AUTOCANNON),
        MIDDLE_PORT(Kind.HE_ROCKET), MIDDLE_STARBOARD(Kind.HE_ROCKET),
        REAR_FORWARD(Kind.TWIN_LASER), REAR_AFT(Kind.TWIN_LASER);

        public final Kind kind;
        Station(Kind kind) { this.kind = kind; }
    }

    @FunctionalInterface
    public interface Emitter {
        /** Return true when fired, including a laser blocked by an entity's damage immunity. */
        boolean fire(Station station, int barrel);
    }

    private final long[] readyAt = new long[Station.values().length];
    private final long[] lastAttempt = new long[Station.values().length];

    public MothershipArmament() { Arrays.fill(lastAttempt, -1); }

    public long[] remaining(long now) {
        long[] remaining = new long[readyAt.length];
        for (int i = 0; i < remaining.length; i++) remaining[i] = Math.max(0, Math.min(Station.values()[i].kind.reloadTicks, readyAt[i] - now));
        return remaining;
    }

    /** No overdue bursts after reload; malformed clocks spend the normal reload instead of becoming ready. */
    public void restore(long now, long[] remaining) {
        for (int i = 0; i < readyAt.length; i++) {
            int maximum = Station.values()[i].kind.reloadTicks;
            long value = remaining != null && i < remaining.length ? remaining[i] : maximum;
            readyAt[i] = now + (value < 0 || value > maximum ? maximum : value);
            lastAttempt[i] = now;
        }
    }

    public boolean ready(Station station, long now) { return now >= readyAt[station.ordinal()] && now > lastAttempt[station.ordinal()]; }

    /** Reserve before side effects; a partial twin salvo or exception spends the full reload. No burst catch-up. */
    public int tryFire(Station station, long now, Emitter emitter) {
        Objects.requireNonNull(station);
        Objects.requireNonNull(emitter);
        int index = station.ordinal();
        if (now < 0 || now <= lastAttempt[index] || now < readyAt[index]
                || now > Long.MAX_VALUE - station.kind.reloadTicks) return 0;
        lastAttempt[index] = now;
        readyAt[index] = now + station.kind.reloadTicks;
        int fired = 0;
        for (int barrel = 0; barrel < station.kind.barrels; barrel++) {
            if (emitter.fire(station, barrel)) fired++;
        }
        if (fired == 0) readyAt[index] = now;
        return fired;
    }
}
