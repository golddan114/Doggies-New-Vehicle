package com.liucodex.libertad.ship;

import com.atsuishio.superbwarfare.tools.OBB;
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleMotionUtils;
import java.util.List;
import java.util.TreeMap;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Narrow the block search only; SBW still resolves the complete compound and entity collisions. */
final class ShipCollisionQuery {
    private ShipCollisionQuery() {}

    static Vec3 resolve(LibertadEntity ship, Vec3 motion, List<OBB> hull) {
        if (motion.lengthSqr() < 1e-7 || !ship.isInWater() || ship.blockCollisionCacheTick == ship.tickCount)
            return VehicleMotionUtils.resolveObbWorldCollision(ship, motion, hull);
        var regions = hull.stream().map(box -> OBB.getWorldAABB(box).expandTowards(motion)
                .inflate(.5).expandTowards(0, ship.getStepHeight() + .5, 0)).toList();
        AABB broad = regions.get(0);
        long visits = 0;
        for (AABB region : regions) { broad = broad.minmax(region); visits += cost(region); }
        if (visits >= cost(broad)) return VehicleMotionUtils.resolveObbWorldCollision(ship, motion, hull);
        var boxes = boxes(ship.level(), ship, regions);
        double[] data = new double[boxes.size() * 6];
        int i = 0;
        for (AABB box : boxes) {
            data[i++] = box.minX; data[i++] = box.minY; data[i++] = box.minZ;
            data[i++] = box.maxX; data[i++] = box.maxY; data[i++] = box.maxZ;
        }
        double[] prior = ship.blockCollisionCoords;
        int priorCount = ship.blockCollisionCount, priorTick = ship.blockCollisionCacheTick;
        try {
            ship.blockCollisionCoords = data;
            ship.blockCollisionCount = data.length;
            ship.blockCollisionCacheTick = ship.tickCount;
            return VehicleMotionUtils.resolveObbWorldCollision(ship, motion, hull);
        } finally {
            ship.blockCollisionCoords = prior;
            ship.blockCollisionCount = priorCount;
            ship.blockCollisionCacheTick = priorTick;
        }
    }

    static List<AABB> boxes(CollisionGetter level, Entity entity, List<AABB> regions) {
        // Cursor3D visits x fastest, then y, then z. Preserve that order and each shape's native box order.
        var blocks = new TreeMap<BlockPos, VoxelShape>(Comparator.<BlockPos>comparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX));
        for (AABB region : regions) {
            var query = new BlockCollisions<>(level, entity, region, false,
                    (pos, shape) -> java.util.Map.entry(pos.immutable(), shape));
            while (query.hasNext()) {
                var entry = query.next();
                blocks.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return blocks.values().stream().flatMap(shape -> shape.toAabbs().stream()).toList();
    }

    static long cost(AABB box) {
        return (long) (Math.floor(box.maxX) - Math.floor(box.minX) + 3)
                * (long) (Math.floor(box.maxY) - Math.floor(box.minY) + 3)
                * (long) (Math.floor(box.maxZ) - Math.floor(box.minZ) + 3);
    }
}
