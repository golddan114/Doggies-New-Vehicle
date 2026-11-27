package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.UUID;

/** Facility mechanical facts only. Physical ammunition and FE are never copied into this state. */
public final class SiloCycle {
    public enum Phase { EMPTY, RELOADING, READY, OPENING, RETURNING, DEPARTING, CLOSING }
    public record Target(int x, int y, int z) { }
    public record Snapshot(Phase phase, int remaining, UUID launch, Target target, boolean preparationRetained) {
        public Snapshot(Phase phase,int remaining,UUID launch,Target target){
            this(phase,remaining,launch,target,phase==Phase.RETURNING);
        }
    }
    private final NuclearRules rules;
    private Snapshot state = new Snapshot(Phase.EMPTY, 0, null, null);
    public SiloCycle(NuclearRules rules) { this.rules = rules; }
    public Snapshot snapshot() { return state; }
    public boolean canPack() {
        return state.phase() == Phase.EMPTY || state.phase() == Phase.RELOADING || state.phase() == Phase.READY;
    }
    /** Packing cancels preparation, including the frozen operation/coordinate. Inventory remains native. */
    public void reset() { state = new Snapshot(Phase.EMPTY, 0, null, null); }
    public void restore(Snapshot saved) {
        if (saved == null || saved.phase() == null || saved.remaining() < 0
                || saved.remaining() > duration(saved.phase())
                || (saved.phase()!=Phase.RETURNING&&saved.preparationRetained())
                || ((saved.phase() == Phase.OPENING || saved.phase() == Phase.RETURNING || saved.phase() == Phase.DEPARTING || saved.phase() == Phase.CLOSING)
                    && (saved.launch() == null || saved.target() == null))
                || ((saved.phase() == Phase.EMPTY || saved.phase() == Phase.RELOADING || saved.phase() == Phase.READY)
                    && (saved.launch() != null || saved.target() != null))
                || ((saved.phase() == Phase.EMPTY || saved.phase() == Phase.READY) && saved.remaining() != 0))
            throw new IllegalArgumentException("Invalid silo cycle");
        state = saved;
    }
    public boolean request(UUID operation, Target target, boolean ammo, int energy) {
        if (state.phase() != Phase.READY || operation == null || target == null
                || !ammo || energy < rules.shotFE()) return false;
        state = new Snapshot(Phase.OPENING, rules.openTicks(), operation, target);
        return true;
    }
    /** Explicit owner-checked creative entry. No ammunition, FE or in-flight operation is changed here. */
    public boolean completePreparation(boolean ammo){
        if(!ammo||(state.phase()!=Phase.EMPTY&&state.phase()!=Phase.RELOADING))return false;
        state=new Snapshot(Phase.READY,0,null,null);return true;
    }
    /** Skip only the idle lead-in; the existing mechanical timeline and save format stay authoritative. */
    public boolean startPreparation(boolean ammo){
        int start=Math.min(rules.reloadTicks(),NuclearMechanicalTimeline.ANIMATION_START_TICKS);
        if(!ammo||(state.phase()!=Phase.EMPTY&&state.phase()!=Phase.RELOADING)
                ||(state.phase()==Phase.RELOADING&&state.remaining()<=start))return false;
        state=new Snapshot(Phase.RELOADING,start,null,null);return true;
    }
    /** Return true when the facility must attempt its one launch transaction. */
    public boolean tick(boolean ammo) {
        if ((state.phase() == Phase.RELOADING || state.phase() == Phase.READY) && !ammo) {
            state = new Snapshot(Phase.EMPTY, 0, null, null);
        }
        if (state.phase() == Phase.EMPTY) {
            if (ammo) state = new Snapshot(Phase.RELOADING, rules.reloadTicks(), null, null);
            return false;
        }
        if (state.phase() == Phase.READY) return false;
        int remaining = Math.max(0, state.remaining() - 1);
        boolean retained=state.preparationRetained()&&(state.phase()!=Phase.RETURNING||ammo);
        state = new Snapshot(state.phase(), remaining, state.launch(), state.target(),retained);
        if (remaining != 0) return false;
        switch (state.phase()) {
            case RELOADING -> state = new Snapshot(Phase.READY, 0, null, null);
            case OPENING -> { return true; }
            case RETURNING -> state = new Snapshot(ammo&&state.preparationRetained() ? Phase.READY : Phase.EMPTY,0,null,null);
            case DEPARTING -> state = new Snapshot(Phase.CLOSING, rules.closeTicks(), state.launch(), state.target());
            case CLOSING -> state = new Snapshot(Phase.EMPTY, 0, null, null);
            default -> { }
        }
        return false;
    }
    public void launched(boolean accepted) {
        launched(accepted,true);
    }
    public void launched(boolean accepted,boolean preparedAmmoPresent) {
        if (state.phase() != Phase.OPENING || state.remaining() != 0) throw new IllegalStateException("Not at launch gate");
        state = new Snapshot(accepted ? Phase.DEPARTING : Phase.RETURNING,
                accepted ? rules.departureTicks() : rules.openTicks(), state.launch(), state.target(),!accepted&&preparedAmmoPresent);
    }
    public int duration(Phase phase) {
        return switch (phase) {
            case RELOADING -> rules.reloadTicks();
            case OPENING, RETURNING -> rules.openTicks();
            case DEPARTING -> rules.departureTicks();
            case CLOSING -> rules.closeTicks();
            default -> 0;
        };
    }
}
