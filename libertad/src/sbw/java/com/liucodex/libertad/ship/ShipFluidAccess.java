package com.liucodex.libertad.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeMod;
import java.util.function.Predicate;

/** A single fluid query owns these loaded chunk references; nothing survives the call. */
final class ShipFluidAccess implements BlockGetter {
    private final Level level;
    private final int x0, z0, width, depth;
    private final ChunkAccess[] chunks;

    private ShipFluidAccess(Level level, AABB box) {
        this.level = level;
        x0 = Mth.floor(box.minX - 1) >> 4;
        z0 = Mth.floor(box.minZ - 1) >> 4;
        width = (Mth.floor(box.maxX + 1) >> 4) - x0 + 1;
        depth = (Mth.floor(box.maxZ + 1) >> 4) - z0 + 1;
        chunks = new ChunkAccess[width * depth];
        for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++)
            chunks[x * depth + z] = level.getChunk(x + x0, z + z0, ChunkStatus.FULL, false);
    }

    /** Same order, depth arithmetic and push rules as Forge; unfamiliar fluid uses the native path. */
    static boolean update(LibertadEntity ship, Predicate<FluidState> predicate) {
        if (ship.touchingUnloadedChunk()) return true;
        AABB box = ship.getBoundingBox().deflate(.001);
        if (box.getXsize() > 256 || box.getZsize() > 256) return false;
        var access = new ShipFluidAccess(ship.level(), box);
        for (ChunkAccess chunk : access.chunks) if (chunk == null) return false;
        var water = ForgeMod.WATER_TYPE.get();
        Sample sample = sample(access, box, predicate, ship.isPushedByFluid(water));
        if (sample == null) return false;
        if (!sample.found) return true;
        Vec3 direction = sample.direction;
        if (direction.length() > 0) {
            if (sample.count > 0) direction = direction.scale(1.0 / sample.count);
            direction = direction.normalize().scale(ship.getFluidMotionScale(water));
            Vec3 motion = ship.getDeltaMovement();
            if (Math.abs(motion.x) < .003 && Math.abs(motion.z) < .003 && direction.length() < .0045000000000000005)
                direction = direction.normalize().scale(.0045000000000000005);
            ship.setDeltaMovement(motion.add(direction));
        }
        ship.waterHeight(sample.height);
        return true;
    }

    record Sample(boolean found, double height, Vec3 direction, int count) {}

    static Sample sample(BlockGetter access, AABB box, Predicate<FluidState> predicate, boolean push) {
        boolean found = false;
        double height = 0;
        int count = 0;
        Vec3 direction = Vec3.ZERO;
        var pos = new BlockPos.MutableBlockPos();
        var neighbor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x < Mth.ceil(box.maxX); x++)
            for (int y = Mth.floor(box.minY); y < Mth.ceil(box.maxY); y++)
                for (int z = Mth.floor(box.minZ); z < Mth.ceil(box.maxZ); z++) {
                    pos.set(x, y, z);
                    FluidState state = access.getFluidState(pos);
                    if (state.isEmpty()) continue;
                    if (state.getType() != Fluids.WATER && state.getType() != Fluids.FLOWING_WATER) return null;
                    if (!predicate.test(state)) continue;
                    double surface = (float) y + state.getHeight(access, pos);
                    if (surface < box.minY) continue;
                    found = true;
                    height = Math.max(height, surface - box.minY);
                    if (!push) continue;
                    Vec3 flow = flow(access, state, pos, neighbor);
                    if (height < .4) flow = flow.scale(height);
                    direction = direction.add(flow);
                    count++;
                }
        return new Sample(found, height, direction, count);
    }

    static Vec3 flow(BlockGetter access, FluidState state, BlockPos pos, BlockPos.MutableBlockPos neighbor) {
        // Source water with four identical neighbours has exactly zero native flow, including waterlogging.
        if (state == Fluids.WATER.defaultFluidState()
                && access.getFluidState(neighbor.set(pos.getX()-1, pos.getY(), pos.getZ())) == state
                && access.getFluidState(neighbor.set(pos.getX()+1, pos.getY(), pos.getZ())) == state
                && access.getFluidState(neighbor.set(pos.getX(), pos.getY(), pos.getZ()-1)) == state
                && access.getFluidState(neighbor.set(pos.getX(), pos.getY(), pos.getZ()+1)) == state) return Vec3.ZERO;
        return state.getFlow(access, pos);
    }

    private ChunkAccess chunk(BlockPos pos) {
        int x = (pos.getX() >> 4) - x0, z = (pos.getZ() >> 4) - z0;
        return x >= 0 && x < width && z >= 0 && z < depth ? chunks[x * depth + z] : null;
    }
    @Override public FluidState getFluidState(BlockPos pos) {
        var chunk = chunk(pos);
        return chunk == null ? level.getFluidState(pos) : chunk.getFluidState(pos);
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        var chunk = chunk(pos);
        return chunk == null ? level.getBlockState(pos) : chunk.getBlockState(pos);
    }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return level.getBlockEntity(pos); }
    @Override public int getHeight() { return level.getHeight(); }
    @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
}
