package com.liucodex.libertad.ship;

import com.atsuishio.superbwarfare.tools.OBB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

final class ShipHull {
    private static final double EPSILON = 1e-6;

    private ShipHull() {}

    static boolean areaLoaded(Level level, AABB bounds) {
        return bounds.minY >= level.getMinBuildHeight() && bounds.maxY <= level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(bounds)
                && level.hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                        BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ));
    }

    static boolean clearAtBox(Level level, LibertadEntity ship, OBB hull, BlockPos boxPos) {
        AABB bounds = OBB.getWorldAABB(hull);
        if (!areaLoaded(level, bounds)) return false;
        AABB box = new AABB(boxPos);
        for (var shape : level.getBlockCollisions(ship, bounds)) {
            for (AABB block : shape.toAabbs()) {
                boolean deployingBox = block.minX >= box.minX && block.maxX <= box.maxX
                        && block.minY >= box.minY && block.maxY <= box.maxY
                        && block.minZ >= box.minZ && block.maxZ <= box.maxZ;
                if (!deployingBox && penetration(hull, block) > EPSILON) return false;
            }
        }
        return true;
    }

    static boolean canTurn(Level level, LibertadEntity ship, OBB start, double angle) {
        Vec3 pivot = ship.position();
        OBB end = rotate(start, pivot, angle);
        OBB swept = rotationEnvelope(start, pivot, angle);
        AABB bounds = OBB.getWorldAABB(swept);
        if (!areaLoaded(level, bounds)) return false;
        for (var shape : level.getBlockCollisions(ship, bounds)) {
            for (AABB block : shape.toAabbs()) {
                if (blocksTurn(start, end, swept, block)) return false;
            }
        }
        return true;
    }

    static boolean blocksTurn(OBB start, OBB end, OBB swept, AABB block) {
        double oldDepth = penetration(start, block);
        // Existing contact may separate or slide. Do not sweep into a new obstacle.
        if (oldDepth > EPSILON) return penetration(end, block) > oldDepth + EPSILON;
        AABB initialBounds = OBB.getWorldAABB(start);
        if (block.maxY <= initialBounds.minY + EPSILON || block.minY >= initialBounds.maxY - EPSILON) return false;
        return penetration(swept, block) > EPSILON;
    }

    static OBB rotate(OBB start, Vec3 pivot, double angle) {
        Vector3d center = new Vector3d(start.center).sub(pivot.x, pivot.y, pivot.z);
        Quaterniond rotation = new Quaterniond().rotationY(angle);
        rotation.transform(center).add(pivot.x, pivot.y, pivot.z);
        return new OBB(center, new Vector3d(start.extents()), rotation.mul(start.rotation()), start.part);
    }

    static OBB rotationEnvelope(OBB start, Vec3 pivot, double angle) {
        OBB midpoint = rotate(start, pivot, angle / 2);
        double radius = Math.hypot(start.center.x - pivot.x, start.center.z - pivot.z) + start.extents().length();
        // Bound the distance any corner can travel from the middle of this tick's yaw interval.
        double padding = 2 * radius * Math.sin(Math.abs(angle) / 4);
        return midpoint.inflate(padding);
    }

    private static double penetration(OBB box, AABB block) {
        Vector3d overlap = OBB.computeObbAabbMtv(box, block);
        return overlap == null ? 0 : overlap.length();
    }
}
