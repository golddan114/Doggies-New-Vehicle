package com.liucodex.doggiesnewvehicle.artillery.energy;

/** Aiming admission and once-per-tick debit; native ENERGY remains the only battery. */
public final class M1ServoEnergy {
    public static final int FE_PER_TICK = 8;
    private long paidTick = Long.MIN_VALUE;
    private int cost = FE_PER_TICK;
    public void configureCost(int value) {
        if(value<1)throw new IllegalArgumentException("Invalid servo debit");
        cost=value;
    }

    public boolean canAim(long tick, int energy) { return paidTick == tick || energy >= cost; }
    public int charge(long tick, float oldYaw, float oldPitch, float yaw, float pitch) {
        if (paidTick == tick || oldYaw == yaw && oldPitch == pitch) return 0;
        paidTick = tick;
        return cost;
    }
}
