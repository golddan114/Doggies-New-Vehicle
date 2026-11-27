package com.liucodex.doggiesnewvehicle.client.mixin;

import com.liucodex.doggiesnewvehicle.client.DnvDynamicLights;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockLightEngine.class)
public abstract class BlizzardDynamicLightMixin {
    @Redirect(method="getEmission(JLnet/minecraft/world/level/block/state/BlockState;)I",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;"
                    +"getLightEmission(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I",remap=false),require=1,allow=1)
    private int dnv$sourceEmission(BlockState state,BlockGetter world,BlockPos position){
        // Keep native enabled-section, propagation and downstream RETURN hooks (including lightcore).
        return Math.max(state.getLightEmission(world,position),DnvDynamicLights.emission(this,position));
    }
}
