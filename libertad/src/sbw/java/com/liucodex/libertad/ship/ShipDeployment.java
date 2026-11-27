package com.liucodex.libertad.ship;

import com.atsuishio.superbwarfare.block.ContainerBlock;
import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import com.liucodex.libertad.Libertad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Ship-specific admission inside the native container lifecycle. */
public final class ShipDeployment {
    // The lower and upper hull paints meet near local Y=0.22 in the exported 05 mesh.
    static final double WATERLINE = .22;

    private ShipDeployment() {}

    public static boolean canOpen(ServerLevel level, BlockPos boxPos) {
        if (ShipPresence.get(level).occupied()) return false;
        LibertadEntity preview = Libertad.SHIP.get().create(level);
        BlockState state = level.getBlockState(boxPos);
        return preview != null && state.hasProperty(ContainerBlock.FACING)
                && prepare(level, boxPos, preview, state.getValue(ContainerBlock.FACING).toYRot());
    }

    public static boolean launch(ServerLevel level, BlockPos boxPos, LibertadEntity ship, boolean fresh) {
        ShipPresence presence = ShipPresence.get(level);
        if ((presence.occupied() && !presence.matches(ship.getUUID()))
                || !prepare(level, boxPos, ship, ship.getYRot())) return false;
        if (fresh) {
            ship.setHealth(ship.getMaxHealth());
            ship.setEnergy(ship.getMaxEnergy());
            // One complete AP salvo and enough HE to load both batteries once.
            starterAmmo(ship, 0, "large_shell_ap", 12);
            starterAmmo(ship, 1, "large_shell_he", 40);
        }
        return level.addFreshEntity(ship);
    }

    private static void starterAmmo(LibertadEntity ship, int slot, String id, int amount) {
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation("superbwarfare", id));
        ship.getInventory().setStackInSlot(slot, new ItemStack(item, amount));
    }

    private static boolean prepare(ServerLevel level, BlockPos boxPos, LibertadEntity ship, float yaw) {
        if (ship.computed().getEngineType() != EngineType.SHIP || ship.computed().seats().isEmpty()) return false;
        double surface = surfaceNearBox(level, boxPos);
        if (!Double.isFinite(surface)) return false;
        ship.moveTo(boxPos.getX() + .5, surface - WATERLINE, boxPos.getZ() + .5, yaw, 0);
        ship.setServerYaw(yaw);
        ship.setZRot(0);
        ship.setPrevRoll(0);
        ship.setDeltaMovement(Vec3.ZERO);
        ship.updateOBB();
        ship.refreshBoundingBoxFromOBBs();
        if (ship.getOBBs().isEmpty()) return false;
        for (OBB hull : ship.getOBBs()) {
            if (!ShipHull.clearAtBox(level, ship, hull, boxPos)) return false;
            if (hull.part == OBB.Part.COLLISION) {
                BlockPos water = BlockPos.containing(hull.center.x, surface - .75, hull.center.z);
                if (!water.equals(boxPos) && !level.getFluidState(water).is(FluidTags.WATER)) return false;
            }
        }
        return level.getEntities(ship, ship.getBoundingBox(), entity -> entity instanceof VehicleEntity).isEmpty();
    }

    private static double surfaceNearBox(ServerLevel level, BlockPos boxPos) {
        // Read the adjacent surface because the container can replace the central water cell.
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos row = boxPos.above(dy);
            double surface = Double.NEGATIVE_INFINITY;
            for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos sample = row.relative(side);
                if (!level.hasChunkAt(sample)) return Double.NaN;
                var fluid = level.getFluidState(sample);
                if (fluid.is(FluidTags.WATER)) {
                    surface = Math.max(surface, sample.getY() + fluid.getHeight(level, sample));
                }
            }
            if (Double.isFinite(surface)) return surface;
        }
        return Double.NaN;
    }
}
