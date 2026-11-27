package com.liucodex.doggiesnewvehicle.boss;

import java.util.UUID;

/** Bounded pursuit policy; callers resolve the actual damage owner, never the remote missile position. */
public final class MothershipTactics {
    public static final double TARGET_RANGE = 160;
    public static final double DISCOVERY_RANGE = 256;
    public static final double DROP_RANGE = 128;
    public static final double DOOMSDAY_RANGE = 128;
    public static final double HOVER_DISTANCE = 60;
    public static final double RETALIATION_RANGE = 224;
    public static final double PURSUIT_DISTANCE = 64;
    public static final double MAX_TARGET_HEIGHT_DIFFERENCE = 64;
    public static final double GROUND_CLEARANCE = 35;
    public static final double MIN_IDLE_CLEARANCE = 30;
    public static final double MAX_IDLE_CLEARANCE = 40;
    public static final double MAX_GROUND_CLEARANCE = 128;
    public static final int PURSUIT_TICKS = 400;
    private UUID pursuerTarget;
    private long deadline;

    public UUID target() { return pursuerTarget; }
    public long remaining(long now) { return pursuerTarget == null ? 0 : Math.max(0, Math.min(PURSUIT_TICKS, deadline - now)); }
    public void restore(UUID target, long now, long remaining) {
        pursuerTarget = remaining > 0 && remaining <= PURSUIT_TICKS ? target : null;
        deadline = now + Math.max(0, Math.min(PURSUIT_TICKS, remaining));
    }

    public boolean onAttacked(UUID attacker, long now, double distance, boolean validHostileOwner) {
        if (attacker == null || !validHostileOwner || now < 0 || now > Long.MAX_VALUE - PURSUIT_TICKS
                || !within(distance, RETALIATION_RANGE)) return false;
        if (pursuerTarget != null && now < deadline) return pursuerTarget.equals(attacker);
        pursuerTarget = attacker;
        deadline = now + PURSUIT_TICKS;
        return true;
    }

    /** Stop on timeout, lost owner, range exit or departure from the chase origin; repeated hits cannot extend it. */
    public UUID pursuitTarget(long now, double targetDistance, double distanceFromOrigin, boolean validHostileOwner) {
        if (pursuerTarget != null && (now < 0 || now >= deadline || !validHostileOwner
                || !within(targetDistance, RETALIATION_RANGE) || !within(distanceFromOrigin, PURSUIT_DISTANCE))) pursuerTarget = null;
        return pursuerTarget;
    }

    /** Hysteresis keeps a huge ship from constantly restarting around its preferred combat distance. */
    public static boolean shouldApproach(double distance, boolean alreadyApproaching) {
        return Double.isFinite(distance) && distance > (alreadyApproaching ? HOVER_DISTANCE : HOVER_DISTANCE + 2);
    }

    public static boolean shouldRetreat(double distance, boolean alreadyRetreating) {
        return Double.isFinite(distance) && distance>=0 && distance<(alreadyRetreating?HOVER_DISTANCE:HOVER_DISTANCE-2);
    }

    public static boolean within(double distance, double range) {
        return Double.isFinite(distance) && distance >= 0 && distance <= range;
    }

    /** Distant observations only fill slots left by current combat candidates. */
    public static double distantDiscoveryScore(double distanceSquared, double horizontalDistance, double height) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0 || !within(horizontalDistance, DISCOVERY_RANGE)
                || !within(Math.abs(height), MAX_TARGET_HEIGHT_DIFFERENCE)) return Double.POSITIVE_INFINITY;
        return distanceSquared + 2 * DISCOVERY_RANGE * DISCOVERY_RANGE;
    }

    /** Horizontal strike footprint; the beam still travels the full three-dimensional slant distance. */
    public static boolean inWeaponRange(double dx, double dy, double dz, double horizontalRange) {
        return within(Math.hypot(dx, dz), horizontalRange) && Double.isFinite(dy) && Math.abs(dy) <= MAX_TARGET_HEIGHT_DIFFERENCE;
    }

    /** Fixed encounter ground datum; tower roofs must not silently move the climb ceiling upwards. */
    public static double maximumBottomY(double encounterGroundY, double worldCeiling, double shipHeight) {
        return Math.min(encounterGroundY + MAX_GROUND_CLEARANCE, worldCeiling - shipHeight - 4);
    }
}
