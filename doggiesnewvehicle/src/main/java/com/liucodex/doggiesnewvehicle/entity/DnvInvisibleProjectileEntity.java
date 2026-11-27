package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Server-authoritative projectile with no client-side visual body. */
public final class DnvInvisibleProjectileEntity extends ProjectileEntity {
    public DnvInvisibleProjectileEntity(
            EntityType<? extends DnvInvisibleProjectileEntity> type,
            Level level) {
        super(type, level);
        setInvisible(true);
    }
}
