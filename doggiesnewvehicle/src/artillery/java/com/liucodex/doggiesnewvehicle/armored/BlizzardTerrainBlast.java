package com.liucodex.doggiesnewvehicle.armored;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/** Bounded terrain phase of the TV warhead. Entity damage remains the one native eight-block blast. */
final class BlizzardTerrainBlast {
    static final int RADIUS = 4;
    private BlizzardTerrainBlast() { }
    static boolean apply(ServerLevel level, Entity source, Vec3 point) {
        var explosion = new Explosion(level, source, point.x, point.y, point.z, RADIUS, false, Explosion.BlockInteraction.DESTROY);
        if (ForgeEventFactory.onExplosionStart(level, explosion)) return false;
        if (!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level, source)) return true;
        BlockPos center = BlockPos.containing(point);
        for (int x = -RADIUS; x <= RADIUS; x++) for (int y = -RADIUS; y <= RADIUS; y++) for (int z = -RADIUS; z <= RADIUS; z++) {
            BlockPos pos = center.offset(x, y, z);
            if (!eligible(level, pos, point)) continue;
            var state = level.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0
                    && !(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock))
                explosion.getToBlow().add(pos);
        }
        // Let claim/protection integrations remove blocks; recheck the radius after external hooks.
        ForgeEventFactory.onExplosionDetonate(level, explosion, new ArrayList<>(), RADIUS);
        if (!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level, source)) return true;
        explosion.getToBlow().removeIf(pos -> !eligible(level, pos, point)
                || level.getBlockState(pos).getDestroySpeed(level, pos) < 0);
        // Do not call explode(): it would deal entity damage for a second time.
        explosion.finalizeExplosion(false);
        return true;
    }
    private static boolean eligible(ServerLevel level, BlockPos pos, Vec3 point) {
        return Vec3.atCenterOf(pos).distanceToSqr(point) <= RADIUS * RADIUS
                && level.hasChunkAt(pos) && !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos);
    }

    static void clearLiquids(ServerLevel level, Entity source, Vec3 point) {
        if (!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level, source)) return;
        int radius = BlizzardMissileFlight.LIQUID_RADIUS;
        var blast = new Explosion(level, source, point.x, point.y, point.z, radius, false, Explosion.BlockInteraction.DESTROY);
        BlockPos center = BlockPos.containing(point);
        for (int x=-radius;x<=radius;x++) for(int y=-radius;y<=radius;y++) for(int z=-radius;z<=radius;z++) {
            BlockPos pos=center.offset(x,y,z);
            if(x*x+y*y+z*z<=radius*radius && liquidSite(level,pos)) blast.getToBlow().add(pos);
        }
        var allowed=new java.util.HashSet<>(blast.getToBlow());var seen=new java.util.HashSet<BlockPos>();
        ForgeEventFactory.onExplosionDetonate(level,blast,new ArrayList<>(),radius);
        for(BlockPos pos:blast.getToBlow()) {
            if(!allowed.contains(pos)||!seen.add(pos)||!liquidSite(level,pos)
                    ||!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level,source))continue;
            var state=level.getBlockState(pos);
            if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))
                level.setBlock(pos,state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,false),3);
            else if(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock||state.canBeReplaced()
                    ||state.getFluidState().createLegacyBlock().getBlock()==state.getBlock())
                level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            else if(state.getBlock() instanceof net.minecraft.world.level.block.BucketPickup pickup)pickup.pickupBlock(level,pos,state);
        }
    }
    private static boolean liquidSite(ServerLevel level,BlockPos pos) {
        return level.hasChunkAt(pos)&&!level.isOutsideBuildHeight(pos)&&level.getWorldBorder().isWithinBounds(pos)
                &&!level.getBlockState(pos).getFluidState().isEmpty();
    }
}
