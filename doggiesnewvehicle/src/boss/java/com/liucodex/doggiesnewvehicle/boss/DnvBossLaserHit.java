package com.liucodex.doggiesnewvehicle.boss;

import com.atsuishio.superbwarfare.tools.DamageHandler;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetExclusions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;

/** The weapon must first authorize its target and verify its loaded, unobstructed beam. */
public final class DnvBossLaserHit {
    private DnvBossLaserHit() { }

    public static boolean apply(Entity source, Entity target, DamageSource damage, float amount) {
        if (source == null || target == null || !(source.level() instanceof ServerLevel)
                || source.level() != target.level() || target.isRemoved() || !target.isAlive()
                || !Float.isFinite(amount) || amount <= 0) return false;
        if (DnvTargetExclusions.isSporeScent(target)) {
            // ScentEntity.hurt always returns false before Forge's ordinary attack hook.
            // Give protection mods that hook before the explicitly authorized special removal.
            if (!(target instanceof LivingEntity living) || !ForgeHooks.onLivingAttack(living, damage, amount)) return false;
            if (target.isRemoved() || target.level() != source.level()) return false;
            target.discard();
        } else {
            DamageHandler.doDamage(target, damage, amount);
        }
        return true;
    }
}
