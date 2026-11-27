package com.liucodex.doggiesnewvehicle.boss;

import java.util.function.BooleanSupplier;

/** Two one-use bays; the entity independently selects a target for each bay and persists spentMask. */
public final class MothershipDrops {
    public static final int FIRST_CHARGE_TICKS=60*20, SECOND_CHARGE_TICKS=90*20;
    public enum Destination { AROUND_TARGET, BELOW_SHIP }
    private int spentMask;

    public int spentMask() { return spentMask; }
    public int remaining() { return 2 - Integer.bitCount(spentMask); }
    public double charge(int bay,int elapsed) {
        if(bay<0||bay>=2)throw new IllegalArgumentException("Invalid drop bay");
        return (spentMask&(1<<bay))!=0?0:Math.max(0,Math.min(1,elapsed/(double)(bay==0?FIRST_CHARGE_TICKS:SECOND_CHARGE_TICKS)));
    }
    public boolean ready(int bay,int elapsed) {return charge(bay,elapsed)>=1;}

    /** Unknown saved bits fail closed instead of reloading the bays. */
    public void restore(int mask) { spentMask = mask < 0 || mask > 3 ? 3 : mask; }

    public static Destination destination(boolean clearOverheadCorridor) {
        return clearOverheadCorridor ? Destination.AROUND_TARGET : Destination.BELOW_SHIP;
    }

    /** Reserve before spawning; rejection refunds the bay, an ambiguous exception never duplicates a Noise. */
    public boolean deploy(int bay, BooleanSupplier spawnAccepted) {
        if (bay < 0 || bay >= 2 || spawnAccepted == null) throw new IllegalArgumentException("Invalid drop bay");
        int bit = 1 << bay;
        if ((spentMask & bit) != 0) return false;
        spentMask |= bit;
        if (spawnAccepted.getAsBoolean()) return true;
        spentMask &= ~bit;
        return false;
    }
}
