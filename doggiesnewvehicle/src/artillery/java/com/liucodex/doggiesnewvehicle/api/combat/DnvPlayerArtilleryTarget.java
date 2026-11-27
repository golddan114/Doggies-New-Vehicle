package com.liucodex.doggiesnewvehicle.api.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** The exact SBW indicator may issue player orders without knowing a DNV entity implementation. */
public interface DnvPlayerArtilleryTarget {
  String M1_INDICATOR_TYPE = "entity.doggievehicle.m1_yunlong";
  String SILO_INDICATOR_TYPE = "entity.doggievehicle.nuclear_silo";
  String DOGEGG_INDICATOR_TYPE = "entity.doggievehicle.dogegg_silo";

  boolean canBindIndicator(Player player);
  boolean setIndicatorTarget(ServerPlayer player, BlockPos target, int radius, boolean depressed);
  boolean requestIndicatorFire(ServerPlayer player, BlockPos target, int radius, boolean depressed);
  void clearIndicatorTarget(ServerPlayer player);
  default Component indicatorAccessProblem(Player player) {
    return Component.translatable(indicatorRejectionKey(false));
  }

  default Component indicatorProblem(ServerPlayer player, BlockPos target, int radius, boolean firing) {
    return Component.translatable(indicatorRejectionKey(firing));
  }

  /** Read-only rejection projection; each facility keeps its own validation and state. */
  default String indicatorRejectionKey(boolean firing) {
    return "message.doggiesnewvehicle.m1_indicator.unavailable";
  }
}
