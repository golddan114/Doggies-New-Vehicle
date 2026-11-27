package com.liucodex.doggiesnewvehicle.boss;

import java.util.List;
import java.util.ArrayList;

/** Per-mothership weapon state. World effects belong to the server entity, never the renderer. */
public final class MothershipDoomsday {
    public static final int LOCK_TICKS = 100;
    public static final int CHARGE_TICKS = 160;
    public static final int FIRST_HIT_TICK = LOCK_TICKS + CHARGE_TICKS;
    public static final int FIRE_TICKS = 100;
    public static final int COOLDOWN_TICKS = 100;
    /** Minimum spacing between fire starts; the same preparation precedes each shot. */
    public static final int REFIRE_INTERVAL_TICKS = 3 * 60 * 20;
    public static final int LAST_HIT_TICK = FIRST_HIT_TICK + FIRE_TICKS;
    public static final int FIRE_END_TICK = LAST_HIT_TICK;
    public static final int LOST_LOCK_RETRY_TICKS = 200;
    public static final int ABORT_TICKS = 30;
    public static final int DIRECT_DAMAGE = 1000;
    public static final int BLAST_DAMAGE = 500;
    public static final double DEFAULT_BLAST_RADIUS = 16;
    public static final double FINAL_VISUAL_RADIUS = 42 * .7;
    public static final int MAX_FIRE_SITES = 32;

    public enum Phase { READY, LOCKING, CHARGING, FIRING, COOLDOWN, ABORTING }

    /** Capture ship position and full aim at firing start; the movement owner must enforce this pose. */
    public record Pose(double x, double y, double z, float yaw, float pitch) {
        public Pose {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Non-finite pose");
        }
    }

    public record FireSite(double x, double z) { }

    /** Each blast is additional damage, including for a directly struck target within its area. */
    public record Hit(int ordinal, int directDamage, int blastDamage, double blastRadius, boolean igniteGround) {
        /** Four escalating pulses add 8% + 16% + 24% + 32% of maximum health. */
        public float directDamageAgainst(double maximumHealth) {
            double health = Double.isFinite(maximumHealth) ? Math.max(0, maximumHealth) : 0;
            return (float) Math.min(Float.MAX_VALUE, directDamage + health * .08 * ordinal);
        }
    }

    public record Update(Phase phase, long elapsed, int hitMask, Pose firingPose, long cooldownRemaining, int soundMask) {
        public boolean movementLocked() { return phase == Phase.FIRING; }
        public boolean targetGlowing() { return phase == Phase.LOCKING; }
    }

    private final double blastRadius;
    private final List<FireSite> fireSites;
    private long started = -1;
    private long lastTick = -1;
    private long readyAt;
    private int consumed;
    private int soundsConsumed;
    private Pose firingPose;
    private boolean active;
    private long abortStarted = -1;
    private long abortElapsed;

    public MothershipDoomsday() { this(DEFAULT_BLAST_RADIUS); }

    public boolean ready(long now) {return !active && now>=readyAt && now>=lastTick;}

    public MothershipDoomsday(double blastRadius) {
        if (!Double.isFinite(blastRadius) || blastRadius < 1 || blastRadius > 64) {
            throw new IllegalArgumentException("Blast radius must be 1..64 blocks");
        }
        this.blastRadius = blastRadius;
        fireSites = ignitionSites(blastRadius);
    }

    public static List<FireSite> ignitionSites(double blastRadius) {
        if (!Double.isFinite(blastRadius) || blastRadius < 1 || blastRadius > 64) {
            throw new IllegalArgumentException("Blast radius must be 1..64 blocks");
        }
        List<FireSite> sites = new ArrayList<>(MAX_FIRE_SITES);
        for (int ring = 1; ring <= 4; ring++) {
            double radius = blastRadius * ring / 4 * 0.9;
            for (int side = 0; side < 8; side++) {
                double angle = (side + (ring % 2) * 0.5) * Math.PI / 4;
                sites.add(new FireSite(Math.cos(angle) * radius, Math.sin(angle) * radius));
            }
        }
        return List.copyOf(sites);
    }

    /** Use monotonic server game ticks. A cancelled fired shot retains its original cooldown. */
    public boolean begin(long now) {
        if (now < 0 || now < lastTick || now < readyAt || now > Long.MAX_VALUE - REFIRE_INTERVAL_TICKS
                || active && now <= started + LAST_HIT_TICK) return false;
        started = now;
        lastTick = now;
        readyAt = now + REFIRE_INTERVAL_TICKS;
        consumed = 0;
        soundsConsumed = 0;
        firingPose = null;
        active = true;
        abortStarted = -1;
        return true;
    }

    /** Consume before dispatching effects; duplicate ticks and clock rollback never replay a hit. */
    public Update advance(long now, Pose currentPose) {
        if (currentPose == null) throw new IllegalArgumentException("Missing ship pose");
        boolean forward = now >= 0 && now >= lastTick;
        long tick = Math.max(0, Math.max(now, lastTick));
        lastTick = tick;
        long age = started < 0 ? -1 : tick - started;
        int mask = 0;
        int sounds = 0;
        if (abortStarted >= 0) {
            if (tick - abortStarted < ABORT_TICKS) {
                return new Update(Phase.ABORTING, abortElapsed, 0, null,
                        Math.max(0, readyAt - tick), 0);
            }
            abortStarted = -1;
        }
        if (active && forward) {
            int soundsDue = age >= FIRE_END_TICK ? 4 : age >= FIRST_HIT_TICK ? 3 : age >= LOCK_TICKS ? 2 : 1;
            if (soundsConsumed < soundsDue) {
                // Missed segments are skipped: never replay charge over a live beam or cool tail.
                sounds = 1 << (soundsDue - 1);
                soundsConsumed = soundsDue;
            }
            int due = hitsDue(age);
            if (due > 0 && firingPose == null) {
                firingPose = currentPose;
                // Start recovery at the actual first damage tick, including a delayed update.
                long delay = REFIRE_INTERVAL_TICKS - FIRST_HIT_TICK;
                readyAt = tick > Long.MAX_VALUE - delay ? Long.MAX_VALUE : tick + delay;
            }
            while (consumed < due) mask |= 1 << consumed++;
            if (age > FIRE_END_TICK) active = false;
        }
        Phase phase = active ? age < LOCK_TICKS ? Phase.LOCKING : age < FIRST_HIT_TICK ? Phase.CHARGING : Phase.FIRING
                : tick < readyAt ? Phase.COOLDOWN : Phase.READY;
        boolean preparing = phase == Phase.LOCKING || phase == Phase.CHARGING;
        return new Update(phase, age, mask, firingPose, preparing ? 0 : Math.max(0, readyAt - tick), sounds);
    }

    /** Stop pending events and release movement immediately; pre-fire cancellation does not spend cooldown. */
    public void cancel() {
        boolean preparing = active && consumed == 0;
        active = false;
        firingPose = null;
        abortStarted = -1;
        if (preparing) readyAt = Math.max(0, lastTick);
    }

    /** Pre-fire failure is cosmetic only; a committed beam can never be converted into a refund. */
    public boolean abort(long now, boolean lostTarget) {
        if (!active || consumed != 0 || now < lastTick || now < 0
                || now > Long.MAX_VALUE - LOST_LOCK_RETRY_TICKS) return false;
        long age = Math.max(0, now - started);
        cancel();
        lastTick = now;
        abortStarted = now;
        abortElapsed = Math.min(FIRST_HIT_TICK - 1, age);
        readyAt = now + (lostTarget && age < LOCK_TICKS ? LOST_LOCK_RETRY_TICKS : ABORT_TICKS);
        return true;
    }

    public long abortStarted() { return abortStarted; }
    public long abortElapsed() { return abortElapsed; }

    public int consumedHits() { return consumed; }

    /** Save only the remaining fired-shot recovery or failure retry delay, never an active effect. */
    public long cooldownToSave(long now) {
        return active && consumed == 0 ? 0 : Math.max(0, Math.min(REFIRE_INTERVAL_TICKS, readyAt - now));
    }

    public void restoreCooldown(long now, long remaining) {
        active = false;
        abortStarted = -1;
        started = -1;
        lastTick = now;
        consumed = 0;
        soundsConsumed = 0;
        firingPose = null;
        long delay = remaining < 0 || remaining > REFIRE_INTERVAL_TICKS ? REFIRE_INTERVAL_TICKS : remaining;
        readyAt = now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
    }
    public double blastRadius() { return blastRadius; }

    /** Candidate offsets only: the server must validate loaded ground/support/fire rules and deduplicate block positions. */
    public List<FireSite> fireSites() { return fireSites; }

    public Hit hit(int ordinal) {
        damageTick(ordinal);
        return new Hit(ordinal, DIRECT_DAMAGE * ordinal / 4, BLAST_DAMAGE * ordinal / 4,
                blastRadius * ordinal / 4, ordinal == 4);
    }

    public static int damageTick(int ordinal) {
        if (ordinal < 1 || ordinal > 4) throw new IllegalArgumentException("Hit ordinal must be 1..4");
        return FIRST_HIT_TICK + switch (ordinal) { case 1 -> 0; case 2 -> 30; case 3 -> 60; default -> FIRE_TICKS; };
    }

    public static int hitsDue(double elapsed) {
        if (!Double.isFinite(elapsed) || elapsed < FIRST_HIT_TICK) return 0;
        int due = 0;
        for (int ordinal = 1; ordinal <= 4; ordinal++) if (elapsed >= damageTick(ordinal)) due++;
        return due;
    }
}
