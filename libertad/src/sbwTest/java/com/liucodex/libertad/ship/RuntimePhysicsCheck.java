package com.liucodex.libertad.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import com.liucodex.libertad.compat.sbw.WaterEntry;

public final class RuntimePhysicsCheck {
    static void bootstrapStandalone() {
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError failure) {
            // Standalone JVM has no Forge event constructor transformation. This last bootstrap
            // operation starts networking, after vanilla registry construction and snapshotting.
            // Only tolerate that exact missing network constructor; all fluid math remains native.
            Throwable root = failure;
            while (root.getCause() != null) root = root.getCause();
            if (!(root instanceof NoSuchMethodException)
                    || !root.getMessage().equals("net.minecraftforge.network.NetworkEvent.<init>()")
                    || !failure.getStackTrace()[0].getClassName().equals("net.minecraftforge.network.NetworkHooks")) throw failure;
            System.out.println("Standalone Minecraft check: Forge networking is not initialized");
        }
    }

    public static void main(String[] args) {
        bootstrapStandalone();
        require(!ParticleTypes.LARGE_SMOKE.getOverrideLimiter() && FunnelExhaust.SMOKE.getOverrideLimiter(),
                "funnel smoke must be admitted beyond the native 32-block cutoff");
        double forwardAtScreenshotSpeed = 13.0 / 3.6 / 20;
        require(FunnelExhaust.interval(FunnelExhaust.forwardMotion(0, forwardAtScreenshotSpeed, 0)) > 0
                && FunnelExhaust.interval(FunnelExhaust.forwardMotion(0, -forwardAtScreenshotSpeed, 0)) == 0
                && FunnelExhaust.interval(0) == 0, "funnel smoke must follow forward motion only");
        System.out.println("PASS: funnel smoke distance flag and forward-only emission");
        var world = new WaterWorld();
        var pos = new BlockPos.MutableBlockPos();
        var neighbor = new BlockPos.MutableBlockPos();
        for (int scenario = 0; scenario < 4; scenario++) {
            world.scenario = scenario;
            for (int x = -6; x <= 6; x++) for (int y = 62; y <= 65; y++) for (int z = -6; z <= 6; z++) {
                pos.set(x, y, z);
                var state = world.getFluidState(pos);
                if (state.isEmpty()) continue;
                var expected = state.getFlow(world, pos);
                var actual = ShipFluidAccess.flow(world, state, pos, neighbor);
                require(expected.equals(actual), "flow changed at " + pos + " scenario " + scenario);
            }
            var box = new AABB(-5.9, 63.4, -5.9, 5.9, 67, 5.9);
            var sample = ShipFluidAccess.sample(world, box, state -> true, true);
            double height = 0; int count = 0; Vec3 direction = Vec3.ZERO;
            for (int x = -6; x < 6; x++) for (int y = 63; y < 67; y++) for (int z = -6; z < 6; z++) {
                pos.set(x, y, z); var state = world.getFluidState(pos);
                if (state.isEmpty()) continue;
                double surface = (float) y + state.getHeight(world, pos);
                if (surface < box.minY) continue;
                height = Math.max(height, surface - box.minY);
                Vec3 flow = state.getFlow(world, pos);
                if (height < .4) flow = flow.scale(height);
                direction = direction.add(flow); count++;
            }
            require(sample != null && sample.height() == height && sample.count() == count
                    && sample.direction().equals(direction), "fluid depth/current aggregation changed " + scenario);
            require(ShipFluidAccess.sample(world, box, state -> false, true).count() == 0, "predicate ignored");
        }
        System.out.println("PASS: native fluid flow and aggregation match for still water, shore, flowing and falling water");
        BuiltInRegistries.FLUID.bindTags(java.util.Map.of(FluidTags.WATER, java.util.List.of(
                BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER), BuiltInRegistries.FLUID.wrapAsHolder(Fluids.FLOWING_WATER))));
        world.scenario = 0;
        Vec3 above = new Vec3(0, 70, 0), below = new Vec3(0, 60, 0);
        double fraction = WaterEntry.limit(world, null, above, below);
        require(fraction > .5 && fraction < .6, "fast shell skipped the water surface");
        require(WaterEntry.limit(world, null, below, above) == 0, "underwater spawn escaped cleanup");
        require(WaterEntry.limit(world, null, above, above.add(50, 0, 0)) == 1, "dry flight was clipped");
        require(WaterEntry.limit(world, null, above, above) == 1, "stationary dry shell was removed");
        System.out.println("PASS: continuous water entry, underwater start, dry and stationary negative paths");
        checkCollisionCandidates();
    }

    private static void checkCollisionCandidates() {
        var world = new CollisionWorld(Map.of(
                new BlockPos(-18, 63, -1), Blocks.STONE.defaultBlockState(),
                new BlockPos(-17, 64, 0), Blocks.STONE_SLAB.defaultBlockState(),
                new BlockPos(-16, 63, 1), Blocks.OAK_FENCE.defaultBlockState(),
                new BlockPos(0, 63, 0), Blocks.STONE.defaultBlockState(),
                new BlockPos(17, 63, -1), Blocks.STONE.defaultBlockState(),
                new BlockPos(18, 64, 1), Blocks.STONE_SLAB.defaultBlockState()));
        var a = new AABB(-19.4, 62.1, -2.4, -15.2, 66.1, 2.4);
        var b = new AABB(16.4, 62.1, -2.4, 20.2, 66.1, 2.4);
        var overlap = new AABB(-18.4, 62.6, -1.4, -15.5, 66.1, 1.9);
        var regions = List.of(b, overlap, a); // Different order and an intentionally repeated block.
        var nativeBoxes = new ArrayList<AABB>();
        for (var shape : world.getBlockCollisions(null, a.minmax(b))) nativeBoxes.addAll(shape.toAabbs());
        int broadReads = world.reads;
        world.reads = 0;
        var narrow = ShipCollisionQuery.boxes(world, null, regions);
        var relevant = nativeBoxes.stream().filter(box -> regions.stream().anyMatch(box::intersects)).toList();
        require(!relevant.isEmpty() && relevant.size() < nativeBoxes.size(), "collision fixture did not exercise the gap");
        require(narrow.stream().filter(box -> regions.stream().anyMatch(box::intersects)).toList().equals(relevant),
                "narrow block search lost, repeated or reordered a relevant native collision box");
        require(world.reads < broadReads, "narrow search did not skip the empty span");
        System.out.printf("PASS: native collision candidates and ordering, %d -> %d block reads%n", broadReads, world.reads);
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class CollisionWorld implements CollisionGetter {
        final Map<BlockPos, BlockState> blocks;
        int reads;
        CollisionWorld(Map<BlockPos, BlockState> blocks) { this.blocks = blocks; }
        @Override public BlockState getBlockState(BlockPos pos) {
            reads++;
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
        @Override public WorldBorder getWorldBorder() { return new WorldBorder(); }
        @Override public BlockGetter getChunkForCollisions(int x, int z) { return this; }
        @Override public List<VoxelShape> getEntityCollisions(Entity entity, AABB box) { return List.of(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }

    private static final class WaterWorld implements BlockGetter {
        int scenario;
        @Override public FluidState getFluidState(BlockPos pos) {
            if (pos.getY() > 64 || (scenario > 0 && pos.getX() > 3)) return Fluids.EMPTY.defaultFluidState();
            if (scenario > 1 && pos.getX() == 3) return Fluids.FLOWING_WATER.getFlowing(5, scenario == 3);
            return Fluids.WATER.defaultFluidState();
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            return pos.getY() < 62 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }
}
