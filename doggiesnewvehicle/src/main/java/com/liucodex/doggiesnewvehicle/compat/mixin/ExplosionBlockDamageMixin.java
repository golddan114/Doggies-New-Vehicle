package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.tools.CustomExplosion;
import com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers all native constructor/builder paths, including vehicle death and in-flight rule changes. */
@Mixin(value = CustomExplosion.class, remap = false)
public abstract class ExplosionBlockDamageMixin {
    @Shadow @Final private Entity entity;
    @Shadow @Final private Level level;
    @Shadow @Final private double x;
    @Shadow @Final private double y;
    @Shadow @Final private double z;

    @ModifyVariable(method = "processBlockList", at = @At("HEAD"), argsOnly = true)
    private Collection<BlockPos> dnv$blastBlocks(Collection<BlockPos> blocks) {
        return DnvBlockDamagePolicy.nativeBlastBlocks(level, entity, new Vec3(x, y, z), blocks);
    }

    @Inject(method = {"finalizeExplosion(Z)V", "m_46075_(Z)V"}, at = @At("HEAD"), require = 1)
    private void dnv$blockAdmission(boolean particles, CallbackInfo callback) {
        if (DnvBlockDamagePolicy.owned(entity) && !DnvBlockDamagePolicy.allowed(level, entity))
            ((Explosion) (Object) this).getToBlow().clear();
    }
}
