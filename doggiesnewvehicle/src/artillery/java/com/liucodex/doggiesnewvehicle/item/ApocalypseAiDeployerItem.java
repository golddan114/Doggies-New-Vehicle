package com.liucodex.doggiesnewvehicle.item;

import com.atsuishio.superbwarfare.tools.OBB;
import com.atsuishio.superbwarfare.entity.OBBEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.armored.ApocalypseContent;
import com.liucodex.doggiesnewvehicle.armored.DnvAlliedApocalypseEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** An explicit deployment transaction; scoreboard and item consumption follow successful world insertion. */
public final class ApocalypseAiDeployerItem extends Item {
    public ApocalypseAiDeployerItem(Properties properties) { super(properties); }

    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        var player = context.getPlayer();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (player == null || !player.getAbilities().mayBuild || !level.hasChunkAt(pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())
                || !level.mayInteract(player, pos) || level.isOutsideBuildHeight(pos)) return reject(player, "permission_or_boundary");
        var tank = ApocalypseContent.ALLIED_TANK.get().create(level);
        if (tank == null) return reject(player, "unavailable");
        tank.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, player.getYRot(), 0);
        tank.updateOBB();
        tank.refreshBoundingBoxFromOBBs();
        var boundsList = deploymentBounds(tank);
        if (boundsList.isEmpty()) return reject(player, "unavailable");
        // Use the same standing envelope for both gestures. A large hull must not require crouching underneath it.
        AABB standing = player.getDimensions(Pose.STANDING).makeBoundingBox(player.position());
        if (boundsList.stream().anyMatch(standing::intersects)) {
            AABB whole = boundsList.get(0);
            for (AABB bounds : boundsList) whole = whole.minmax(bounds);
            double yaw = Math.toRadians(player.getYRot()), dx = -Math.sin(yaw), dz = Math.cos(yaw);
            double offset = clearanceOffset(whole, standing, dx, dz);
            if (!Double.isFinite(offset) || offset > 12) return reject(player, "space");
            tank.moveTo(tank.getX() + dx * offset, tank.getY(), tank.getZ() + dz * offset, player.getYRot(), 0);
            tank.updateOBB(); tank.refreshBoundingBoxFromOBBs();
            boundsList = deploymentBounds(tank);
        }
        for (var bounds : boundsList) {
            BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
            BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
            if (!level.hasChunksAt(min, max) || !level.getWorldBorder().isWithinBounds(bounds)
                    || level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)) return reject(player, "permission_or_boundary");
            for (BlockPos occupied : BlockPos.betweenClosed(min, max))
                if (!level.mayInteract(player, occupied)) return reject(player, "permission_or_boundary");
            if (bounds.intersects(standing) || !level.noCollision(tank, bounds)) return reject(player, "space");
        }
        if (!nativeVehicleSpaceClear(level, tank, boundsList)) return reject(player, "space");
        tank.deployedBy(player);
        if (!level.addFreshEntity(tank)) return reject(player, "unavailable");
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    private static boolean nativeVehicleSpaceClear(ServerLevel level, DnvAlliedApocalypseEntity tank, List<AABB> boundsList) {
        AABB whole = boundsList.get(0);
        for (var bounds : boundsList) whole = whole.minmax(bounds);
        // Vanilla noCollision omits OBB vehicles whose canBeCollidedWith returns false.
        // Native support tests the receiving entity's whole AABB, including gaps between its parts.
        for (var other : level.getEntities(tank, whole, entity -> !entity.isRemoved() && !entity.isSpectator()
                && !entity.noPhysics && entity instanceof OBBEntity shape && !shape.enableAABB())) {
            for (var box : ((OBBEntity) other).getOBBs()) if (OBB.isColliding(box, whole)) return false;
            if (other instanceof VehicleEntity vehicle && vehicle.getCollisionOBB() != null
                    && OBB.isColliding(vehicle.getCollisionOBB(), whole)) return false;
        }
        return true;
    }

    private static List<AABB> deploymentBounds(DnvAlliedApocalypseEntity tank) {
        var boxes = new java.util.ArrayList<>(tank.getOBBs());
        if (tank.getCollisionOBB() != null) boxes.add(tank.getCollisionOBB());
        return boxes.stream().map(OBB::getWorldAABB).toList();
    }

    static double clearanceOffset(AABB hull, AABB player, double dx, double dz) {
        double x = dx > 1E-6 ? (player.maxX - hull.minX) / dx
                : dx < -1E-6 ? (player.minX - hull.maxX) / dx : Double.POSITIVE_INFINITY;
        double z = dz > 1E-6 ? (player.maxZ - hull.minZ) / dz
                : dz < -1E-6 ? (player.minZ - hull.maxZ) / dz : Double.POSITIVE_INFINITY;
        return Math.max(0, Math.min(x, z)) + .25;
    }

    private static InteractionResult reject(Player player, String reason) {
        if (player != null) player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.apocalypse_deploy." + reason), true);
        return InteractionResult.FAIL;
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.doggiesnewvehicle.apocalypse_ai_deployer"));
    }
}
