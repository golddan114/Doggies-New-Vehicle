package com.liucodex.doggiesnewvehicle.artillery.energy;

/** Shared by capability receive and native inventory charging; never owns stored FE. */
public final class DnvEnergyInputBudget {
    private final int limit;
    private long tick = Long.MIN_VALUE;
    private int received;
    public DnvEnergyInputBudget(int limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative input budget");
        this.limit = limit;
    }
    public int allowance(long now, int requested) {
        if (tick != now) { tick = now; received = 0; }
        return Math.min(Math.max(0, requested), limit - received);
    }
    public void received(long now, int actual, boolean simulate) {
        if (!simulate) received += allowance(now, actual);
    }
}
