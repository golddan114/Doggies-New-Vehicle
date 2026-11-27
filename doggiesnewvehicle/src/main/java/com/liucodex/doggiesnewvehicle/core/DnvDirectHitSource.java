package com.liucodex.doggiesnewvehicle.core;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** One synchronous hit; its target claim prevents multipart/reentrant bonus duplication. */
public class DnvDirectHitSource extends DamageSource {
    private final float percent;
    private final Set<UUID> claimed = new HashSet<>();
    public DnvDirectHitSource(Holder<DamageType> type, Entity direct, Entity attacker, float percent) {
        super(type, direct, attacker);
        if (!Float.isFinite(percent) || percent < 0 || percent > 1) throw new IllegalArgumentException("percent");
        this.percent = percent;
    }
    public float percent() { return percent; }
    boolean claim(UUID target) { return claimed.add(target); }
}
