package com.liucodex.doggiesnewvehicle.boss;

/** Pure timeline for the one server-owned mothership arrival age. */
public final class MothershipArrival {
    public static final int BEACON_TICKS = 120;
    public static final int REVEAL_TICKS = 40;
    public static final int TOTAL_TICKS = BEACON_TICKS + REVEAL_TICKS;

    public enum Phase { BEACON, REVEAL, ACTIVE }

    private MothershipArrival() { }

    public static int restore(int age) {
        return Math.max(0, Math.min(TOTAL_TICKS, age));
    }

    public static int advance(int age) {
        return Math.min(TOTAL_TICKS, restore(age) + 1);
    }

    public static Phase phase(int age) {
        if (age < BEACON_TICKS) return Phase.BEACON;
        if (age < TOTAL_TICKS) return Phase.REVEAL;
        return Phase.ACTIVE;
    }

    public static double reveal(int age) {
        return reveal((double) age);
    }

    public static double reveal(double age) {
        if (age <= BEACON_TICKS) return 0;
        double t = Double.isFinite(age) ? Math.max(0, Math.min(1, (age - BEACON_TICKS) / REVEAL_TICKS)) : 0;
        return t * t * (3 - 2 * t);
    }
}
