package com.liucodex.doggiesnewvehicle.boss;

/** Per-ship speed owner; the server entity must apply this velocity and the doomsday's fixed pose. */
public final class MothershipFlight {
    public static final double MAX_BLOCKS_PER_TICK = 1.0 / 20;
    public static final int ACCELERATION_TICKS = 100;
    private static final double CURVE = 3;
    private static final double CURVE_SCALE = Math.expm1(CURVE);

    public record Motion(double blocksPerTick, MothershipDoomsday.Pose fixedPose) {
        public boolean locked() { return fixedPose != null; }
    }

    private long started = -1;
    private long lastTick = -1;
    private Motion current = new Motion(0, null);

    /** Slow initial motion with increasing acceleration, reaching exactly 1 block/s at five seconds. */
    public static double speedAt(long elapsedTicks) {
        if (elapsedTicks <= 0) return 0;
        if (elapsedTicks >= ACCELERATION_TICKS) return MAX_BLOCKS_PER_TICK;
        return MAX_BLOCKS_PER_TICK * Math.expm1(CURVE * elapsedTicks / ACCELERATION_TICKS) / CURVE_SCALE;
    }

    /** No catch-up movement on skipped ticks. Stopping or firing resets the next acceleration ramp. */
    public Motion advance(long now, boolean wantsMovement, MothershipDoomsday.Update weapon) {
        if (now < 0 || weapon == null || weapon.movementLocked() && weapon.firingPose() == null) {
            throw new IllegalArgumentException("Missing tick or weapon pose");
        }
        if (now < lastTick) return current;
        lastTick = now;
        if (weapon.movementLocked()) {
            started = -1;
            return current = new Motion(0, weapon.firingPose());
        }
        if (!wantsMovement) {
            started = -1;
            return current = new Motion(0, null);
        }
        if (started < 0) started = now;
        return current = new Motion(speedAt(now - started), null);
    }
}
