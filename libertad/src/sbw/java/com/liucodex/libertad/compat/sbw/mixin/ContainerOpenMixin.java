package com.liucodex.libertad.compat.sbw.mixin;

import com.atsuishio.superbwarfare.block.ContainerBlock;
import com.liucodex.libertad.Libertad;
import com.liucodex.libertad.ship.ShipDeployment;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ContainerBlock.Companion.class, remap = false)
abstract class ContainerOpenMixin {
    @Inject(method = "canOpen", at = @At("HEAD"), cancellable = true)
    private void libertad$checkHull(Level level, BlockPos pos, EntityType<?> type, CompoundTag tag,
                                    CallbackInfoReturnable<Boolean> result) {
        if (type == Libertad.SHIP.get() && level instanceof ServerLevel server) {
            result.setReturnValue(ShipDeployment.canOpen(server, pos));
        }
    }
}
