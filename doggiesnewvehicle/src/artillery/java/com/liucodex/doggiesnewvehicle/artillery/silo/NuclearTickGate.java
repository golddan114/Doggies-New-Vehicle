package com.liucodex.doggiesnewvehicle.artillery.silo;

/** Native entity ticking and the high-altitude fallback share this one transient clock. */
public final class NuclearTickGate {
    private long last=Long.MIN_VALUE;
    public boolean claim(long gameTime){if(last==gameTime)return false;last=gameTime;return true;}
    public long last(){return last;}
}
