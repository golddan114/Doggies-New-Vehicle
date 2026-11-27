package com.liucodex.doggiesnewvehicle.core;

import com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore.ActionIntent;

/** Per-entity local driving memory; never owns targets, weapons, position or saved state. */
public final class DnvGroundDrive {
    private int scanDelay;
    private int reverseTicks;
    private int reverseAttempts;
    private int side = 1;
    private int stalledTicks;
    private boolean detouring;
    private boolean blocked;
    private boolean initialized;
    private boolean wasColliding;
    private float detourYaw;
    private float previousThrottle;
    private double lastX;
    private double lastZ;
    private double detourTravel;

    public record Input(ActionIntent intent, float yaw, float goalYaw,
                        double x, double z, double remainingDistance,
                        boolean grounded, boolean collision, boolean reposition) {}

    public record Control(float throttle, float steering, boolean brake) {}

    @FunctionalInterface
    public interface Terrain {
        /** Signed distance is negative for reversing; unknown terrain must return false. */
        boolean clear(float yaw, double distance);
    }

    public Control command(Input in, Terrain terrain) {
        float cruise = switch (in.intent().state()) {
            case APPROACHING -> 1.0F;
            case RETREATING -> -0.65F;
            case REPOSITIONING -> 0.52F;
            case PATROLLING -> 0.58F;
            default -> 0.0F;
        };
        boolean invalid = !Float.isFinite(in.yaw()) || !Float.isFinite(in.goalYaw())
                || !Double.isFinite(in.x()) || !Double.isFinite(in.z())
                || !Double.isFinite(in.remainingDistance());
        if (cruise == 0 || in.intent().hardStop() || invalid) {
            reset();
            return new Control(0, 0, in.intent().hardStop() || invalid);
        }
        if (!in.grounded()) {
            reset();
            return new Control(0, 0, false);
        }
        double progress = initialized ? Math.hypot(in.x() - lastX, in.z() - lastZ) : 0;
        if (progress > 2.0D) reset(); // Teleport/external reposition invalidates local detours.
        if (initialized) {
            detourTravel += Math.min(progress, 0.5D);
            stalledTicks = Math.abs(previousThrottle) > 0.1F && progress < 0.005D
                    ? Math.min(20, stalledTicks + 1) : 0;
        }
        initialized = true;
        lastX = in.x();
        lastZ = in.z();
        float goal = in.goalYaw() + (in.reposition() ? side * 38.0F : 0.0F);
        double lookAhead = cruise < 0 ? -2.0D : 3.0D;
        boolean collisionStarted = in.collision() && !wasColliding;
        wasColliding = in.collision();
        boolean stalled = stalledTicks == 20;
        if (--scanDelay <= 0 || collisionStarted || stalled) {
            scanDelay = 4;
            if (stalled) stalledTicks = 0;
            blocked = false;
            if (reverseTicks > 0) {
                blocked = !terrain.clear(in.yaw(), -2.0D);
                if (blocked) reverseTicks = 0;
            } else {
                if (detouring && detourTravel >= 3.0D && terrain.clear(goal, lookAhead)) {
                    detouring = false;
                }
                float heading = detouring ? detourYaw : goal;
                if (collisionStarted || stalled || !terrain.clear(heading, lookAhead)) {
                    chooseDetour(in.yaw(), goal, lookAhead, terrain);
                } else if (!detouring) {
                    reverseAttempts = 0;
                }
            }
        }
        if (reverseTicks > 0) {
            reverseTicks--;
            if (reverseTicks == 0) scanDelay = 0;
            previousThrottle = -0.35F;
            return new Control(-0.35F, 0, false);
        }
        float heading = detouring ? detourYaw : goal;
        float error = wrap(heading - in.yaw());
        float steering = Math.abs(error) < 1.0F ? 0.0F : clamp(error / 18.0F, -1, 1);
        // Continuous slowdown replaces the old full-power/zero jump at 32 degrees.
        float throttle = cruise * clamp((55.0F - Math.abs(error)) / 45.0F, 0, 1);
        if (in.intent().state() == com.liucodex.doggiesnewvehicle.core.DnvAutonomyCore.TacticalState.PATROLLING) {
            throttle *= clamp((float) ((in.remainingDistance() - 2.5D) / 3.0D), 0, 1);
        }
        if (blocked || in.collision()) throttle = 0;
        previousThrottle = throttle;
        return new Control(throttle, blocked ? 0 : steering, blocked || in.collision());
    }

    private void chooseDetour(float yaw, float goal, double distance, Terrain terrain) {
        // Keep the chosen side throughout a detour instead of alternating every two seconds.
        if (!detouring && Math.abs(wrap(goal - yaw)) > 1) side = wrap(goal - yaw) > 0 ? 1 : -1;
        float candidate = yaw + side * 75.0F;
        if (!terrain.clear(candidate, distance)) {
            candidate = yaw - side * 75.0F;
            if (!terrain.clear(candidate, distance)) {
                if (distance > 0 && reverseAttempts < 2 && terrain.clear(yaw, -2.0D)) {
                    reverseTicks = 20;
                    reverseAttempts++;
                }
                else blocked = true;
                return;
            }
            side = -side;
        }
        detourYaw = wrap(candidate);
        detouring = true;
        detourTravel = 0;
        reverseAttempts = 0;
    }

    public void reset() {
        scanDelay = reverseTicks = reverseAttempts = stalledTicks = 0;
        detouring = blocked = initialized = wasColliding = false;
        previousThrottle = 0;
        detourTravel = 0;
        side = 1;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float wrap(float value) {
        return (float) (value - Math.floor((value + 180.0D) / 360.0D) * 360.0D);
    }
}
