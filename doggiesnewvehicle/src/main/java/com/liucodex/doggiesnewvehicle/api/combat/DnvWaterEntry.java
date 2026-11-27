package com.liucodex.doggiesnewvehicle.api.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Called only after the owner clips the complete segment to loaded chunks. */
public final class DnvWaterEntry {
    private DnvWaterEntry() {}
    public static double limit(BlockGetter world, Entity projectile, Vec3 from, Vec3 to) {
        BlockPos pos = BlockPos.containing(from);
        FluidState fluid = world.getFluidState(pos);
        if (fluid.is(FluidTags.WATER) && from.y < pos.getY() + fluid.getHeight(world, pos)) return 0;
        double distance = from.distanceToSqr(to);
        if (distance < 1e-18) return 1;
        var hit = world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, projectile) {
            @Override public VoxelShape getBlockShape(BlockState state, BlockGetter getter, BlockPos block) {
                return Shapes.empty();
            }
            @Override public VoxelShape getFluidShape(FluidState state, BlockGetter getter, BlockPos block) {
                return state.is(FluidTags.WATER) ? state.getShape(getter, block) : Shapes.empty();
            }
        });
        return hit.getType() == HitResult.Type.MISS ? 1
                : Math.max(0, Math.min(1, Math.sqrt(from.distanceToSqr(hit.getLocation()) / distance)) - 1e-7);
    }
}
