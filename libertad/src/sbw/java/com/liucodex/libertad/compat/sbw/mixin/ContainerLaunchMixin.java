package com.liucodex.libertad.compat.sbw.mixin;

import com.atsuishio.superbwarfare.block.ContainerBlock;
import com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity;
import com.liucodex.libertad.Libertad;
import com.liucodex.libertad.ship.LibertadEntity;
import com.liucodex.libertad.ship.ShipDeployment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContainerBlockEntity.Companion.class, remap = false)
abstract class ContainerLaunchMixin {
    @Redirect(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean libertad$admitShip(Level world, Entity entity,
                                       Level level, BlockPos pos, BlockState state, ContainerBlockEntity box) {
        if (!(entity instanceof LibertadEntity ship) || !(level instanceof ServerLevel server)) {
            return world.addFreshEntity(entity);
        }
        boolean accepted = ShipDeployment.launch(server, pos, ship, box.entityTag == null);
        if (!accepted) {
            box.setOpened(false);
            box.setTick(0);
            box.setChanged();
            level.setBlockAndUpdate(pos, state.setValue(ContainerBlock.OPENED, false));
        }
        return accepted;
    }

    @Inject(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z",
            shift = At.Shift.AFTER), cancellable = true)
    private void libertad$retainRejectedBox(Level level, BlockPos pos, BlockState state,
                                           ContainerBlockEntity box, CallbackInfo callback) {
        if (box.entityType == Libertad.SHIP.get() && !box.getOpened()) callback.cancel();
    }
}
