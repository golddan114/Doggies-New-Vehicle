package com.liucodex.libertad.compat.sbw.mixin;

import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.liucodex.libertad.compat.sbw.NativeShells;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/** Bounds the exact SBW 0.8.9.2 callbacks used after the ordinary shell trace has already hit. */
@Mixin(value = CannonShellEntity.class, remap = false)
abstract class CannonPenetrationMixin {
    @Inject(method = "forceLoadChunk", at = @At("HEAD"), cancellable = true)
    private void libertad$noChunkTickets(CallbackInfoReturnable<Boolean> callback) {
        if (NativeShells.managed((CannonShellEntity) (Object) this)) callback.setReturnValue(false);
    }

    @Inject(method = "afterHitBlock", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/CannonShellEntity;getDurability()I", ordinal = 0),
            locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void libertad$beforeBlockProbe(BlockHitResult hit, CallbackInfo callback, Level level, Vec3 direction, Vec3 sample) {
        libertad$stopAtBoundary(hit, callback, sample);
    }

    @Inject(method = "afterHitBlock", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/CannonShellEntity;m_6034_(DDD)V"),
            locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void libertad$beforePenetrationMove(BlockHitResult hit, CallbackInfo callback, Level level, Vec3 direction, Vec3 end) {
        libertad$stopAtBoundary(hit, callback, end);
    }

    @Unique
    private void libertad$stopAtBoundary(BlockHitResult hit, CallbackInfo callback, Vec3 end) {
        var shell = (CannonShellEntity) (Object) this;
        if (NativeShells.managed(shell) && !NativeShells.containsSegment(shell, hit.getLocation(), end)) {
            shell.discard();
            callback.cancel();
        }
    }

    @Unique private Vec3 libertad$penetrationHit;

    @Inject(method = "afterHitEntity", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/entity/projectile/CannonShellEntity;m_20184_()Lnet/minecraft/world/phys/Vec3;", ordinal = 0))
    private void libertad$capturePenetrationHit(EntityHitResult hit, CallbackInfo callback) {
        var shell = (CannonShellEntity) (Object) this;
        libertad$penetrationHit = NativeShells.managed(shell) ? hit.getLocation() : null;
    }

    @ModifyArg(method = "afterHitEntity", index = 3, at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/tools/TraceTool;getEntitiesAlongVector(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private Predicate<Entity> libertad$markEntityTrace(Predicate<Entity> filter) {
        Vec3 hit = libertad$penetrationHit;
        libertad$penetrationHit = null;
        // DNV can redirect the call and still forwards this argument to TraceTool.
        return hit == null ? filter : new NativeShells.PenetrationFilter((CannonShellEntity) (Object) this, hit, filter);
    }
}
