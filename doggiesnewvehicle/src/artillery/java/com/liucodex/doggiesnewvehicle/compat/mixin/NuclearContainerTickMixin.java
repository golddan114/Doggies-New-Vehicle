package com.liucodex.doggiesnewvehicle.compat.mixin;

import com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity;
import com.liucodex.doggiesnewvehicle.api.combat.DnvContainerTickBoundary;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native box animation remains shared; the nuclear facility owns protected excavation and spawn commit. */
@Mixin(targets="com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity$Companion",remap=false)
public abstract class NuclearContainerTickMixin {
    @Inject(method="serverTick",at=@At("HEAD"),cancellable=true,remap=false)
    private void dnv$nuclearUnfold(Level level,BlockPos pos,BlockState state,ContainerBlockEntity box,CallbackInfo callback){
        if(DnvContainerTickBoundary.tick(level,pos,state,box))callback.cancel();
    }
}
