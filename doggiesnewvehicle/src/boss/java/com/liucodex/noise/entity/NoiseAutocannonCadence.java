package com.liucodex.noise.entity;

/** Exact five-round-per-second cadence on Minecraft's 20-tick clock. */
final class NoiseAutocannonCadence {
    static final int SHOTS_PER_CYCLE = 5;
    static final int CYCLE_TICKS = 20;
    private static final int[] INTERVALS = {4};

    private NoiseAutocannonCadence() {}

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

