package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.entity.projectile.MediumRocketEntity;
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Native penetration/impact removal is separate from CustomExplosion. Preserve all other hit bookkeeping. */
@Mixin(value = {CannonShellEntity.class, SmallCannonShellEntity.class, MediumRocketEntity.class, FastThrowableProjectile.class}, remap = false)
public abstract class ProjectileBlockDamageMixin {
    @Group(name = "dnv$nativeBlockWrite", min = 1, max = 1)
    @Redirect(method = {"afterHitBlock", "destroyBlock"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;m_46961_(Lnet/minecraft/core/BlockPos;Z)Z"), require = 0)
    private boolean dnv$levelBlock(Level level, BlockPos pos, boolean drops) {
        Entity source=(Entity)(Object)this;
        return (!DnvBlockDamagePolicy.owned(source)||DnvBlockDamagePolicy.allowed(level,source))&&level.destroyBlock(pos,drops);
    }

    @Group(name = "dnv$nativeBlockWrite", min = 1, max = 1)
    @Redirect(method = {"afterHitBlock", "destroyBlock"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerLevel;m_46961_(Lnet/minecraft/core/BlockPos;Z)Z"), require = 0)
    private boolean dnv$serverBlock(ServerLevel level, BlockPos pos, boolean drops) {
        Entity source=(Entity)(Object)this;
        return (!DnvBlockDamagePolicy.owned(source)||DnvBlockDamagePolicy.allowed(level,source))&&level.destroyBlock(pos,drops);
    }
}
