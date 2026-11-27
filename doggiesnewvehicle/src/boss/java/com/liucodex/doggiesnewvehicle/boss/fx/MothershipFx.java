package com.liucodex.doggiesnewvehicle.boss.fx;

import com.liucodex.doggiesnewvehicle.boss.MothershipDoomsday;

/** Stateless visual projection. The eventual weapon owner supplies elapsed ticks and a clipped ray. */
public final class MothershipFx {
    public static final int CHARGE_TICKS = MothershipDoomsday.FIRST_HIT_TICK;
    public static final int FIRE_TICKS = MothershipDoomsday.FIRE_TICKS;
    // Five-second visual/audio tail only; firing readiness is owned by REFIRE_INTERVAL_TICKS.
    public static final int COOL_TICKS = 100;
    public static final int TOTAL_TICKS = CHARGE_TICKS + FIRE_TICKS + COOL_TICKS;
    public static final int MAX_NOZZLES = 10;
    public static final double MAX_LENGTH = 512;
    public static final int MAX_VERTICES = 8192;
    public static final double ORB_RADIUS = 6;
    public static final int TERMINAL_TICKS = 80;

    public enum Phase { IDLE, CHARGE, LOCK, FIRE, COOL, DONE, ABORT }
    public enum Detail { NEAR, FAR, HIDDEN }
    public record Sample(Phase phase, double charge, double beam, double heat, double thrust) { }

    private MothershipFx() { }

    /** Frozen pre-fire energy dissipates without ever advancing into beam or terminal effects. */
    public static Sample sampleAbort(double interruptedTick,double abortAge,double throttle) {
        if(!Double.isFinite(interruptedTick) || interruptedTick<0 || !Double.isFinite(abortAge)
                || abortAge<0 || abortAge>=MothershipDoomsday.ABORT_TICKS)
            return new Sample(Phase.DONE,0,0,0,clamp(throttle));
        var before=sample(Math.min(interruptedTick,CHARGE_TICKS-1),throttle);
        double fade=1-smooth(abortAge/MothershipDoomsday.ABORT_TICKS);
        double glow=interruptedTick<MothershipDoomsday.LOCK_TICKS
                ?.06+.12*smooth(interruptedTick/MothershipDoomsday.LOCK_TICKS):.18+.82*before.charge();
        return new Sample(Phase.ABORT,before.charge()*fade,0,glow*fade,clamp(throttle));
    }

    public static Sample sample(double tick, double throttle) {
        throttle = clamp(throttle);
        if (!Double.isFinite(tick) || tick < 0) return new Sample(Phase.IDLE, 0, 0, 0, throttle);
        if (tick < CHARGE_TICKS) {
            if (tick < MothershipDoomsday.LOCK_TICKS) return new Sample(Phase.LOCK, 0, 0, 0, throttle);
            double charge = smooth((tick - MothershipDoomsday.LOCK_TICKS) / MothershipDoomsday.CHARGE_TICKS);
            return new Sample(Phase.CHARGE, charge, 0, 0,
                    throttle * (1 - charge * 0.55));
        }
        double fireAge = tick - CHARGE_TICKS;
        if (fireAge <= FIRE_TICKS) {
            double beam = 0.25 + 0.75 * smooth(fireAge / 3);
            return new Sample(Phase.FIRE, 1, beam, beam, throttle * 0.45);
        }
        double coolAge = fireAge - FIRE_TICKS;
        if (coolAge < COOL_TICKS) {
            return new Sample(Phase.COOL, 0, 0, 1 - smooth(coolAge / COOL_TICKS),
                    throttle);
        }
        return new Sample(Phase.DONE, 0, 0, 0, throttle);
    }

    /** Distance to the rendered segment, not just its muzzle, keeps a nearby long beam visible. */
    public static Detail detail(double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0 || distanceSquared >= 256 * 256) return Detail.HIDDEN;
        return distanceSquared < 96 * 96 ? Detail.NEAR : Detail.FAR;
    }

    public static double length(double length) {
        return Double.isFinite(length) ? Math.max(0, Math.min(MAX_LENGTH, length)) : 0;
    }

    public static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    public static double smooth(double value) {
        double x = clamp(value);
        return x * x * (3 - 2 * x);
    }

    public static int damageTick(int ordinal) {
        return MothershipDoomsday.damageTick(ordinal);
    }

    public static int hitsDue(double elapsed) {
        return MothershipDoomsday.hitsDue(elapsed);
    }
}
