package com.liucodex.silentheaven.entity;

/** Exact three-round-per-second cadence on Minecraft's 20-tick clock. */
final class SilentHeavenAutocannonCadence {
    static final int SHOTS_PER_CYCLE = 3;
    static final int CYCLE_TICKS = 20;
    private static final int[] INTERVALS = {7, 7, 6};

    private SilentHeavenAutocannonCadence() {}

    static int intervalForIndex(int index) {
        return INTERVALS[normalizeIndex(index)];
    }

    static int nextIndex(int index) {
        return normalizeIndex(index + 1);
    }

    static int normalizeIndex(int index) {
        return Math.floorMod(index, INTERVALS.length);
    }
}
