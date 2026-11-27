package com.liucodex.doggiesnewvehicle.artillery.silo;

/** One phase-to-authoring-frame contract for the server rack and all visual consumers. */
public final class NuclearMechanicalTimeline {
    public static final int PREPARATION_TICKS = 600;
    public static final int CLEARANCE_TICKS = 520;
    public static final int READY_LEAD_TICKS = 600;
    public static final int ANIMATION_START_TICKS = PREPARATION_TICKS + CLEARANCE_TICKS + READY_LEAD_TICKS;
    public static final int RACK_ARRIVED_FRAME = 1381;
    public static final int READY_FRAME = 1681;
    public static final int LAUNCH_FRAME = 1903;
    public static final int CLOSE_FRAME = 2384;
    public static final int END_FRAME = 2944;
    private NuclearMechanicalTimeline() { }
    public static double remaining(int remaining,long observed,double time,boolean running){
        return Math.max(0,remaining-(running?Math.max(0,time-observed):0));
    }
    public static float radarFrame(long haltedAt,double time){return 1+(float)((haltedAt>=0?haltedAt:time)*1.5%120);}
    public static float frame(SiloCycle.Phase phase, double remaining, NuclearRules rules) {
        return switch (phase) {
            // V20 starts 86 seconds before reload completion and reaches its ready pose 30 seconds early.
            // The 20-second lift is followed by extension and the authored three-second hold.
            case RELOADING -> 1+(READY_FRAME-1)*progress(CLEARANCE_TICKS+PREPARATION_TICKS,remaining-READY_LEAD_TICKS);
            case READY -> READY_FRAME;
            case OPENING -> READY_FRAME+(LAUNCH_FRAME-READY_FRAME)*progress(rules.openTicks(),remaining);
            case RETURNING -> READY_FRAME+(LAUNCH_FRAME-READY_FRAME)*(1-progress(rules.openTicks(),remaining));
            // The physical missile owns departure. Facility tracks remain in the authored open pose.
            case DEPARTING -> LAUNCH_FRAME+(CLOSE_FRAME-LAUNCH_FRAME)*progress(rules.departureTicks(),remaining);
            case CLOSING -> CLOSE_FRAME+(END_FRAME-CLOSE_FRAME)*progress(rules.closeTicks(),remaining);
            default -> 1;
        };
    }
    private static float progress(int duration, double remaining) {
        return (float)Math.max(0, Math.min(1, (duration-remaining)/duration));
    }
}
