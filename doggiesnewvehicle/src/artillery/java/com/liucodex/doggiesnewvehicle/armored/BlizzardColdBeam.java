package com.liucodex.doggiesnewvehicle.armored;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-owned charge cycle. SBW owns input and the one synced ChargeProgress value. */
final class BlizzardColdBeam {
    static final int IDLE = 0, CHARGING = 1, COOLING = 2, DECAYING = 3;
    private final DnvBlizzardEntity tank;
    private int phase;
    private Entity operator;
    BlizzardColdBeam(DnvBlizzardEntity tank) { this.tank = tank; if (!tank.level().isClientSide) tank.setChargeProgress(1); }
    int phase() { return phase; }
    int cooldown() { return (int)Math.ceil((phase == COOLING ? progress() : 1 - progress()) * reloadTicks() - .00001); }
    int reloadTicks() { return phase == COOLING ? BlizzardRules.MODE2_COOLDOWN_TICKS : BlizzardRules.DAMAGE_INTERVAL; }
    int cadencePhase() { return 0; }
    void restoreCooldown(int value) { tank.setChargeProgress(1 - Math.max(0, Math.min(10, value)) / 10F); }
    void restoreReloadTicks(int value) { /* Old rapid-fire cadence is no longer a mode-two state. */ }
    void restoreCadencePhase(int value) { }
    void restorePhase(int saved) {
        operator = null;
        phase = saved == COOLING && progress() > 0 ? COOLING : IDLE;
        if (phase != COOLING && tank.mode2()) tank.setChargeProgress(0);
    }
    void modeChanged() {
        operator = null;
        if (phase != COOLING) { phase = IDLE; tank.setChargeProgress(0); }
        stop();
    }
    void release() {
        if (phase == CHARGING) phase = DECAYING;
        if (phase != DECAYING) operator = null;
        stop();
    }
    private float progress() {
        float value = tank.getChargeProgress();
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }
    private boolean ready() {
        return tank.driverReady() && tank.getWeaponIndex(0) == 0
                && (tank.mode2() ? tank.mode2Ready() : tank.hovering())
                && tank.getEnergy() >= BlizzardRules.pulseCost(tank.mode2());
    }
    private boolean held() {
        return operator != null && operator == tank.getFirstPassenger() && tank.fireInputDown() && ready();
    }
    void tick() {
        if (tank.level().isClientSide) return;
        if (operator != null && (operator != tank.getFirstPassenger() || !tank.driverReady()
                || tank.getWeaponIndex(0) != 0 || !tank.mode2Ready())) {
            operator = null;
            if (phase == CHARGING || phase == DECAYING) { phase = IDLE; tank.setChargeProgress(0); }
            stop();
        }
        if (operator != null && !held()) release();
        if (phase == COOLING) {
            stop();
            tank.setChargeProgress(Math.max(0, progress() - 1F / BlizzardRules.MODE2_COOLDOWN_TICKS));
            if (progress() <= .00001F) {
                phase = IDLE;
                tank.setChargeProgress(tank.mode2() ? 0 : 1);
                if (tank.mode2() && held()) phase = CHARGING;
            }
            return;
        }
        if (!tank.mode2()) {
            if (tank.powered() && tank.getEnergy() >= BlizzardRules.PULSE_FE)
                tank.setChargeProgress(Math.min(1, progress() + 1F / BlizzardRules.DAMAGE_INTERVAL));
            if (!tank.driverReady() || !tank.fireInputDown() || tank.getWeaponIndex(0) != 0) stop();
            return;
        }
        if (phase == CHARGING) {
            tank.setChargeProgress(Math.min(1, progress() + 1F / BlizzardRules.MODE2_CHARGE_TICKS));
            if (progress() >= .99999F) {
                // Only this transition can damage: one immutable world-space ray, one payment.
                if (held() && shoot()) { phase = COOLING; tank.setChargeProgress(1); }
                else { release(); operator = null; tank.setChargeProgress(0); phase = IDLE; }
            }
        } else if (phase == DECAYING) {
            tank.setChargeProgress(Math.max(0, progress() - 1F / BlizzardRules.MODE2_DECAY_TICKS));
            if (progress() <= .00001F) { phase = IDLE; operator = null; tank.setChargeProgress(0); }
        }
    }
    /** Authenticated native shot request arms a held charge; movement bits cannot arm it. */
    void fire() {
        if (tank.level().isClientSide || !ready()) return;
        if (tank.mode2()) {
            if (!tank.fireInputDown()) return;
            operator = tank.getFirstPassenger();
            if (phase == IDLE || phase == DECAYING) phase = CHARGING;
        } else if (phase != COOLING && progress() >= .99999F && shoot()) tank.setChargeProgress(0);
    }
    private boolean shoot() {
        Vec3 from = tank.getShootPos("ColdBeam", 1);
        Vec3 pivot = BlizzardRig.point(tank.groupTransform("Barrel", 1), BlizzardRig.INSTANCE.barrel);
        if (!BlizzardLoadedPath.loaded(tank, pivot, from) || tank.level().clip(new ClipContext(pivot, from,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tank)).getType() != HitResult.Type.MISS) return false;
        if (!(tank.mode2() ? tank.traceLaser(true) : tank.emitColdBolt())) return false;
        tank.consumeEnergy(BlizzardRules.pulseCost(tank.mode2()));
        tank.setFiring(true);
        return true;
    }
    private void stop() { tank.setFiring(false); tank.clearLaser(); }
}
