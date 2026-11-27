package com.liucodex.carriercube;

import com.atsuishio.superbwarfare.entity.vehicle.AnnihilatorEntity;
import com.atsuishio.superbwarfare.entity.vehicle.damage.DamageModifier;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.liucodex.doggiesnewvehicle.api.combat.DnvColdArmor;
import com.liucodex.doggiesnewvehicle.core.DnvArmorDamageTags;
import com.liucodex.doggiesnewvehicle.core.DnvPercentHits;
import com.liucodex.doggiesnewvehicle.core.DnvStarProjectileHits;
import com.liucodex.doggiesnewvehicle.core.MothershipAnnihilatorDefense;
import com.liucodex.doggiesnewvehicle.core.MothershipDefense;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class YueCarrier extends CubeVehicle implements DnvColdArmor {
    private static final DamageModifier IDENTITY = new DamageModifier();
    private final Map<UUID, Float> pulseDamage = new HashMap<>();
    private long pulseTick = Long.MIN_VALUE;

    YueCarrier(EntityType<? extends CubeVehicle> type, Level level) {
        super(type, level);
    }

    // SBW clamps even a light vehicle's collision impulse to at least 1/4 strength.
    // Like SES, this hull moves under propulsion and navigation, independently of contact pushes.
    @Override public void push(double x, double y, double z) {}
    @Override public void pushNew(double x, double y, double z) {}
    @Override public boolean isPushable() { return false; }

    @Override public Tier coldArmorTier() { return Tier.HEAVY; }
    @Override public DamageModifier getDamageModifier() { return IDENTITY; }

    @Override public boolean hurt(DamageSource source, float incoming) {
        if (level().isClientSide || isRemoved() || getHealth() <= 0 || source.getEntity() == this
                || !Float.isFinite(incoming) || incoming <= 0 || DnvStarProjectileHits.starAlreadyHit(source, getUUID())) return false;
        boolean laser = source.is(ModDamageTypes.LASER) || source.is(DnvArmorDamageTags.BOSS_PIERCING_LASER);
        float amount = MothershipDefense.mitigate(source, incoming, laser);
        var direct = source.getDirectEntity();
        boolean pulse = direct instanceof AnnihilatorEntity
                && (laser || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(ModDamageTypes.CUSTOM_EXPLOSION));
        if (pulse) {
            long tick = level().getGameTime();
            if (tick != pulseTick) {
                pulseDamage.clear();
                pulseTick = tick;
            }
            amount = MothershipAnnihilatorDefense.withinTick(pulseDamage.getOrDefault(direct.getUUID(), 0F), amount);
        }
        if (amount <= 0) return false;
        float before = getHealth();
        boolean accepted = super.hurt(source, DnvPercentHits.apply(this, source, incoming, amount));
        float lost = before - getHealth();
        if (lost > 0) {
            DnvStarProjectileHits.recordStarHit(source, getUUID());
            if (pulse) pulseDamage.merge(direct.getUUID(), lost, Float::sum);
        }
        return accepted || lost > 0;
    }
}
