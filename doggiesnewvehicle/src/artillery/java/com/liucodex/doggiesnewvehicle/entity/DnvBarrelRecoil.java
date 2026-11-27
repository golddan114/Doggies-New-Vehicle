package com.liucodex.doggiesnewvehicle.entity;

/** Client-only visual event history; never ammunition, cooldown or persisted firing state. */
public final class DnvBarrelRecoil {
    private final int[] fired={Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};
    public void shot(int barrel,int tick){if(barrel<0||barrel>=fired.length)throw new IllegalArgumentException("barrel");fired[barrel]=tick;}
    public float progress(int barrel,int tick,float partial,int duration){
        if(fired[barrel]==Integer.MIN_VALUE)return 0;
        float t=((long)tick-fired[barrel]+partial)/duration;
        if(t<0||t>=1)return 0;
        if(t<.2F)return t/.2F;
        float r=(t-.2F)/.8F;return 1-r*r*(3-2*r);
    }
    public void reset(){java.util.Arrays.fill(fired,Integer.MIN_VALUE);}
}
