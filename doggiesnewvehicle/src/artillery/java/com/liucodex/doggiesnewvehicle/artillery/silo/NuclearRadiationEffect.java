package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Native harmful-effect timer; maximum-health true loss bypasses hurt reductions and absorption. */
public final class NuclearRadiationEffect extends MobEffect {
    public NuclearRadiationEffect(){super(MobEffectCategory.HARMFUL,0xDDAA22);}
    @Override public boolean isDurationEffectTick(int duration,int amplifier){
        return duration>0&&(duration-1)%NuclearPayloadRules.DEFAULT.radiationInterval()==0;
    }
    @Override public void applyEffectTick(LivingEntity entity,int amplifier){
        if(!entity.level().isClientSide){
            float health=entity.getHealth();
            float max=entity.getMaxHealth();
            if(Float.isFinite(health)&&health>0&&Float.isFinite(max)&&max>0){
                var rules=NuclearPayloadRules.DEFAULT;
                entity.setHealth(Math.max(0,health-rules.radiationDamage()-max*rules.radiationFraction()));
            }
        }
    }
}
