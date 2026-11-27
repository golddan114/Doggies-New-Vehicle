package com.liucodex.doggiesnewvehicle.core;

import com.atsuishio.superbwarfare.init.ModDamageTypes;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;

/** Shared receipt for repeated SBW star contacts; each boss owns damage admission. */
public final class DnvStarProjectileHits {
    private static final String STAR_HITS = "DnvBossStarHits";
    private static final int MAX_STAR_TARGETS = 128;
    private DnvStarProjectileHits() { }

    /** Boss-owned receipt on the projectile survives its delayed callback and target unload/reload. */
    public static boolean starAlreadyHit(DamageSource source, UUID target) {
        int stage = starStage(source);
        var direct = source.getDirectEntity();
        if (stage == 0 || direct == null) return false;
        var hits = direct.getPersistentData().getCompound(STAR_HITS);
        String key = target.toString();
        return (hits.getInt(key) & stage) != 0 || (!hits.contains(key) && hits.size() >= MAX_STAR_TARGETS);
    }

    public static void recordStarHit(DamageSource source, UUID target) {
        int stage = starStage(source);
        var direct = source.getDirectEntity();
        if (stage == 0 || direct == null) return;
        var data = direct.getPersistentData();
        var hits = data.getCompound(STAR_HITS);
        String key = target.toString();
        if (!hits.contains(key) && hits.size() >= MAX_STAR_TARGETS) return;
        hits.putInt(key, hits.getInt(key) | stage);
        data.put(STAR_HITS, hits);
    }

    private static int starStage(DamageSource source) {
        if (source.is(ModDamageTypes.SUPER_STAR_HIT)) return 1;
        if (source.is(ModDamageTypes.SUPER_STAR_SLASH)) return 2;
        return 0;
    }
}
