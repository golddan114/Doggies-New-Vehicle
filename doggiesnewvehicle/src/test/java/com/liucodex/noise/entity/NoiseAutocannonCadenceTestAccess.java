package com.liucodex.noise.entity;

public final class NoiseAutocannonCadenceTestAccess {
    private NoiseAutocannonCadenceTestAccess() {
    }

    public static int shotsPerCycle() {
        return NoiseAutocannonCadence.SHOTS_PER_CYCLE;
    }

    public static int intervalForIndex(int index) {
        return NoiseAutocannonCadence.intervalForIndex(index);
    }

    public static int nextIndex(int index) {
        return NoiseAutocannonCadence.nextIndex(index);
    }
}
