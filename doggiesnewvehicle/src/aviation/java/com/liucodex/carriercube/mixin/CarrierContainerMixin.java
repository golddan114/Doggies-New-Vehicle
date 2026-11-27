package com.liucodex.carriercube.mixin;

import com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity;
import com.liucodex.carriercube.CubePrototype;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ContainerBlockEntity.Companion.class,remap=false)
public abstract class CarrierContainerMixin {
    // Only the native crate spawn gets a placement pose after loading its portable NBT.
    @ModifyVariable(method="serverTick", at=@At("STORE"), ordinal=0, require=1, allow=1)
    private Entity carrier$unpack(Entity entity) {
        CubePrototype.beginUnpacking(entity);
        return entity;
    }

    // SBW removes the crate even if its addFreshEntity call returns false.
    @Inject(method="serverTick",at=@At("HEAD"),cancellable=true)
    private void carrier$keepFullFleetCrate(Level level,BlockPos pos,BlockState state,ContainerBlockEntity box,CallbackInfo callback){
        if(CubePrototype.holdCarrierBox(level,pos,state,box))callback.cancel();
    }
}
