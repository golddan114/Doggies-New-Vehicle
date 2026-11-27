package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvTurretShells;
import com.atsuishio.superbwarfare.tools.TraceTool;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = CannonShellEntity.class, remap = false)
abstract class DnvCannonShellChunkMixin {
    @Inject(method = "forceLoadChunk", at = @At("HEAD"), cancellable = true, require = 1)
    private void dnv$noNativeChunkLoading(CallbackInfoReturnable<Boolean> callback) {
        if (DnvTurretShells.managed((CannonShellEntity) (Object) this)) callback.setReturnValue(false);
    }

    @Redirect(method = "afterHitEntity", at = @At(value = "INVOKE", target =
            "Lcom/atsuishio/superbwarfare/tools/TraceTool;getEntitiesAlongVector("
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1)
    private List<TraceTool.RayTraceResultEntity> dnv$loadedPenetration(Level world, Vec3 from, Vec3 direction,
            Predicate<Entity> filter) {
        var limited = DnvTurretShells.limitSecondaryTrace((CannonShellEntity) (Object) this, from, direction);
        return limited == null ? List.of() : TraceTool.getEntitiesAlongVector(world, from, limited, filter);
    }

    @Inject(method = "afterHitBlock", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_8055_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
            @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;")
    }, cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, require = 1)
    private void dnv$loadedBlockPenetration(BlockHitResult hit, CallbackInfo callback, Level level, Vec3 direction,
            Vec3 rayPosition, Set<BlockPos> visited, int index, BlockPos block) {
        var shell = (CannonShellEntity) (Object) this;
        if (DnvTurretShells.managed(shell) && level instanceof ServerLevel server
                && !server.isPositionEntityTicking(block)) {
            shell.discard();
            callback.cancel();
        }
    }
}
