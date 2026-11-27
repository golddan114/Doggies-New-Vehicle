package com.liucodex.doggiesnewvehicle.boss;

/** Server-owned destruction progress; no health revival and no replayed impact after reload. */
public final class MothershipCrash {
    public static final int ALIVE = 0, FALLING = 1, LANDED = 2, FINISHED = 3;
    public static final int MAX_FALL_TICKS = 6000;
    public static final double BLOCKS_PER_SECOND = 2;
    public static final double BLOCKS_PER_TICK = BLOCKS_PER_SECOND / 20;
    public static final int LANDED_TICKS = 20;
    public static final int LOCAL_EXPLOSION_INTERVAL = 20;
    private int state, age;
    private int landedAge;
    private int planAge;
    private double startY=Double.NaN,endY=Double.NaN;
    public boolean begin() { if (state != ALIVE) return false; state = FALLING; return true; }
    public int state() { return state; }
    public int age() { return age; }
    public void advance() {
        if (state == FALLING) age = Math.min(MAX_FALL_TICKS, age + 1);
        else if (state == LANDED) landedAge = Math.min(LANDED_TICKS, landedAge + 1);
    }
    public int landedAge() { return landedAge; }
    public boolean localExplosionDue() { return state == FALLING && age > 0 && age % LOCAL_EXPLOSION_INTERVAL == 0; }
    /** Consume before side effects, so saving/reloading the final phase cannot repeat damage or effects. */
    public boolean finish() {
        if (state != LANDED || landedAge < LANDED_TICKS) return false;
        state = FINISHED;
        return true;
    }
    /** Keep the safety timeout ticking, but never catch up unobserved motion when surrounding chunks return. */
    public void hold() { if(planned())planAge=Math.min(age,planAge+1); }
    public boolean timedOut() { return state == FALLING && age >= MAX_FALL_TICKS; }
    public boolean planned() { return Double.isFinite(startY)&&Double.isFinite(endY); }
    public void plan(double start,double end) { if(!planned()&&Double.isFinite(start)&&Double.isFinite(end)){startY=start;endY=Math.min(start-.02,end);planAge=age;} }
    public double nextY() {
        // Terrain decides contact; altitude must never increase the speed of a high wreck.
        return startY-Math.max(0,age-planAge)*BLOCKS_PER_TICK;
    }
    public double pitch() { return Math.min(12, Math.max(0,age-planAge)*.12); }
    public double roll() { return Math.min(8, Math.max(0,age-planAge)*.08); }
    public double startY() { return startY; } public double endY() { return endY; } public int planAge() { return planAge; }
    public boolean impact() { if (state != FALLING) return false; state = LANDED; landedAge = 0; return true; }
    public void restore(int savedState, int savedAge) {
        restore(savedState, savedAge, 0);
    }
    public void restore(int savedState, int savedAge, int savedLandedAge) {
        state = savedState >= ALIVE && savedState <= FINISHED ? savedState : FINISHED;
        age = Math.max(0, Math.min(MAX_FALL_TICKS, savedAge));
        landedAge = Math.max(0, Math.min(LANDED_TICKS, savedLandedAge));
    }
    public void restorePlan(double start,double end,int atAge) {
        if(Double.isFinite(start)&&Double.isFinite(end)&&end<=start&&start-end<=256&&Math.abs(start)<4096&&Math.abs(end)<4096) {
            startY=start;endY=end;planAge=Math.max(0,Math.min(age,atAge));
        }
    }
}
