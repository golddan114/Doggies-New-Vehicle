package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvGroundDrive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.OBB;

/** Short local clearance checks only; never loads chunks or moves the vehicle. */
public final class DnvGroundTerrainProbe implements DnvGroundDrive.Terrain {
    private final AABB source;
    private final double stepHeight;
    private final Space space;

    public DnvGroundTerrainProbe(Entity vehicle) {
        this(footprint(vehicle), vehicle.getStepHeight(), new EntitySpace(vehicle));
    }

    private static AABB footprint(Entity entity) {
        if(entity instanceof VehicleEntity vehicle&&vehicle.getCollisionOBB()!=null)
            return OBB.getWorldAABB(vehicle.getCollisionOBB());
        return entity.getBoundingBox();
    }

    DnvGroundTerrainProbe(AABB source, double stepHeight, Space space) {
        this.source = source.inflate(0.05D, 0, 0.05D);
        this.stepHeight = stepHeight;
        this.space = space;
    }

    /** Read-only world seam; the same footprint algorithm runs against deterministic test terrain. */
    interface Space {
        boolean loaded(AABB bounds);
        boolean withinBorder(AABB bounds);
        boolean clear(AABB bounds);
        boolean supported(double x, double y, double z);
    }

    @Override
    public boolean clear(float yaw, double distance) {
        double radians = Math.toRadians(yaw);
        double dx = -Math.sin(radians) * distance;
        double dz = Math.cos(radians) * distance;
        for (int step = 1; step <= 3; step++) {
            AABB bounds = source.move(dx * step / 3, 0, dz * step / 3);
            if (!space.loaded(bounds) || !space.withinBorder(bounds)) return false;
            if (!space.clear(bounds)) {
                bounds = bounds.move(0, stepHeight, 0);
                if (!space.clear(bounds)) return false;
            }
            for (double x : new double[]{bounds.minX + 0.1D, (bounds.minX + bounds.maxX) / 2, bounds.maxX - 0.1D}) {
                for (double z : new double[]{bounds.minZ + 0.1D, (bounds.minZ + bounds.maxZ) / 2, bounds.maxZ - 0.1D}) {
                    if (!space.supported(x, bounds.minY, z)) return false;
                }
            }
        }
        return true;
    }

    private record EntitySpace(Entity vehicle) implements Space {
        @Override
        public boolean loaded(AABB bounds) {
            int minX = ((int) Math.floor(bounds.minX)) >> 4;
            int maxX = ((int) Math.floor(bounds.maxX)) >> 4;
            int minZ = ((int) Math.floor(bounds.minZ)) >> 4;
            int maxZ = ((int) Math.floor(bounds.maxZ)) >> 4;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!vehicle.level().getChunkSource().hasChunk(x, z)) return false;
                }
            }
            return true;
        }

        @Override
        public boolean withinBorder(AABB bounds) {
            return vehicle.level().getWorldBorder().isWithinBounds(bounds);
        }

        @Override
        public boolean clear(AABB bounds) {
            return vehicle.level().noCollision(vehicle, bounds);
        }

        @Override
        public boolean supported(double x, double y, double z) {
            BlockPos feet = BlockPos.containing(x, y + 0.05D, z);
            if (!vehicle.level().getFluidState(feet).isEmpty()) return false;
            for (int depth = 0; depth <= 2; depth++) {
                BlockPos pos = BlockPos.containing(x, y - 0.05D, z).below(depth);
                if (!vehicle.level().getFluidState(pos).isEmpty()) return false;
                VoxelShape shape = vehicle.level().getBlockState(pos).getCollisionShape(vehicle.level(), pos);
                if (!shape.isEmpty()) {
                    double drop = y - (pos.getY() + shape.max(Direction.Axis.Y));
                    return drop >= -0.1D && drop <= 1.25D;
                }
            }
            return false;
        }
    }
}
