package com.liucodex.doggiesnewvehicle.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Stateless admission for the operator's physical body; never creates a seat or loads a chunk. */
final class DnvM1CrewSafety {
  private DnvM1CrewSafety() {}

  static String rejection(ServerPlayer player, BlockEntity anchor) {
    if (player.isSleeping() || player.isSpectator() || !player.isAlive()) return "ACTOR_UNAVAILABLE";
    if (player.isPassenger()) return "ALREADY_RIDING";
    var state = anchor.getBlockState();
    if (!state.hasProperty(DnvRemoteWeaponStationContent.FACING)) return "STATION_CHANGED";
    Vec3 body = Vec3.atBottomCenterOf(anchor.getBlockPos().relative(state.getValue(DnvRemoteWeaponStationContent.FACING)));
    return safe(player, body, true) ? "NONE" : "SEAT_BLOCKED";
  }

  static boolean safe(ServerPlayer player, Vec3 at, boolean occupancy) {
    ServerLevel level = player.serverLevel();
    BlockPos feet = BlockPos.containing(at);
    AABB body = new AABB(at.x - .3, at.y, at.z - .3, at.x + .3, at.y + 1.8, at.z + .3);
    if (level.isOutsideBuildHeight(feet.below()) || level.isOutsideBuildHeight(BlockPos.containing(body.maxX, body.maxY, body.maxZ))
        || !level.getWorldBorder().isWithinBounds(body)) return false;
    for (BlockPos check : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY - 1, body.minZ),
        BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
      if (!level.hasChunkAt(check) || !level.getFluidState(check).isEmpty()) return false;
    }
    if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
        || !level.noCollision(player, body)) return false;
    return !occupancy || level.getEntities(player, body, entity -> entity != player.getVehicle()
        && entity.isAlive() && entity instanceof LivingEntity).isEmpty();
  }
}
