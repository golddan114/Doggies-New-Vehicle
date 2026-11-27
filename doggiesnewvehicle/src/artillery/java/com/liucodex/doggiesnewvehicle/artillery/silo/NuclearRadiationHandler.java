package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applies radiation once to a server-side target already admitted by the payload's protection checks.
 * The selected implementation owns effect admission and native duration; source may be null.
 * Refusal must not trigger a second implementation. A future verified SBW adapter replaces the
 * default wiring in NuclearPayloadWorld, without another scan, timer, or persistence owner.
 */
@FunctionalInterface
public interface NuclearRadiationHandler {
    void apply(LivingEntity target, Entity source, int durationTicks);

    static NuclearRadiationHandler dnv() {
        return (target, source, durationTicks) -> target.addEffect(
                new MobEffectInstance(NuclearEffects.RADIATION.get(), durationTicks, 0), source);
    }
}
