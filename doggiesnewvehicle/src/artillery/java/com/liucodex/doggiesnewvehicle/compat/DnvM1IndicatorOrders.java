package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.Mod;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.item.misc.ArtilleryIndicatorItem;
import com.atsuishio.superbwarfare.item.misc.FiringParametersItemKt;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.api.combat.DnvPlayerArtilleryTarget;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Exact-SBW adapter for player-issued artillery orders; the item and each facility own their state. */
public final class DnvM1IndicatorOrders {
  private static final String CANNONS = "Cannons";
  private static final String TYPE = "Type";
  private static final String UUID_KEY = "UUID";
  private DnvM1IndicatorOrders() {}

  public static boolean isM1Indicator(ItemStack stack) {
    if (!stack.is(ModItems.ARTILLERY_INDICATOR.get()) || !stack.hasTag()) return false;
    String type = stack.getTag().getString(TYPE);
    return DnvPlayerArtilleryTarget.M1_INDICATOR_TYPE.equals(type)
        || DnvPlayerArtilleryTarget.SILO_INDICATOR_TYPE.equals(type)
        || DnvPlayerArtilleryTarget.DOGEGG_INDICATOR_TYPE.equals(type);
  }

  public static InteractionResult bind(ArtilleryIndicatorItem item, VehicleEntity vehicle,
      DnvPlayerArtilleryTarget target,
      ItemStack stack, Player player, InteractionHand hand) {
    if (hand != InteractionHand.MAIN_HAND || player.getVehicle() == vehicle) return InteractionResult.FAIL;
    if (player.level().isClientSide) return InteractionResult.SUCCESS;
    if (!target.canBindIndicator(player)) {
      player.displayClientMessage(target.indicatorAccessProblem(player), true);
      return InteractionResult.FAIL;
    }
    // SBW's own bind/addCannon owns the indicator NBT, duplicate toggle and list capacity.
    InteractionResult result = item.bind(stack, player, vehicle);
    if (player instanceof ServerPlayer server && !contains(stack.getOrCreateTag().getList(CANNONS, 10), vehicle.getUUID()))
      target.clearIndicatorTarget(server);
    return result;
  }

  public static void setTarget(ItemStack stack, Player player) {
    if (!(player instanceof ServerPlayer server) || !isM1Indicator(stack)) return;
    ListTag cannons = stack.getOrCreateTag().getList(CANNONS, 10);
    Set<UUID> seen = new HashSet<>();
    var parameters = FiringParametersItemKt.getFiringParameters(stack);
    int available = 0;
    // A temporarily unloaded or locked cannon stays bound in the player's item; no silent NBT pruning.
    for (int i = 0; i < cannons.size(); i++) {
      CompoundTag entry = cannons.getCompound(i);
      UUID id = uuid(entry.getString(UUID_KEY));
      if (id == null || !seen.add(id)) continue;
      Entity entity = server.serverLevel().getEntity(id); // Never force-load or search another dimension.
      if (!(entity instanceof DnvPlayerArtilleryTarget m1)) continue;
      available++;
      if (!m1.canBindIndicator(server)) {
        server.displayClientMessage(m1.indicatorAccessProblem(server), true);
      } else if (!m1.setIndicatorTarget(server, parameters.pos(), parameters.radius(), parameters.isDepressed())) {
        server.displayClientMessage(m1.indicatorProblem(server, parameters.pos(), parameters.radius(), false), true);
      }
    }
    if (available == 0) unavailable(server, stack, null, false);
  }

  public static void fire(ServerPlayer player, ItemStack stack) {
    if (!isM1Indicator(stack)) return;
    var parameters = FiringParametersItemKt.getFiringParameters(stack);
    ListTag cannons = stack.getOrCreateTag().getList(CANNONS, 10);
    int handled = 0;
    Set<UUID> seen = new HashSet<>();
    for (int i = 0; i < cannons.size(); i++) {
      UUID id = uuid(cannons.getCompound(i).getString(UUID_KEY));
      if (id == null || !seen.add(id)) continue;
      Entity entity = player.serverLevel().getEntity(id);
      if (!(entity instanceof DnvPlayerArtilleryTarget m1)) continue;
      if (!m1.canBindIndicator(player)) {
        player.displayClientMessage(m1.indicatorAccessProblem(player), true);
        handled++;
        continue;
      }
      int delay = i % 5 + 1; // Preserve SBW's artillery-array cadence.
      if (Mod.Companion.queueServerWork(delay, () -> {
        ListTag current = stack.getOrCreateTag().getList(CANNONS, 10);
        if (heldIndicator(player) != stack || !isM1Indicator(stack)
            || !contains(current, id)) return;
        if (!m1.canBindIndicator(player)) {
          player.displayClientMessage(m1.indicatorAccessProblem(player), true);
        } else if (!m1.requestIndicatorFire(player, parameters.pos(), parameters.radius(), parameters.isDepressed())) {
          player.displayClientMessage(m1.indicatorProblem(player, parameters.pos(), parameters.radius(), true), true);
        }
      })) handled++;
    }
    if (handled == 0) unavailable(player, stack, null, true);
  }

  public static ItemStack heldIndicator(ServerPlayer player) {
    ItemStack main = player.getMainHandItem();
    if (main.is(ModItems.MONITOR.get()) && player.getOffhandItem().is(ModItems.ARTILLERY_INDICATOR.get()))
      return player.getOffhandItem();
    return main;
  }

  private static boolean contains(ListTag list, UUID id) {
    for (int i = 0; i < list.size(); i++)
      if (id.equals(uuid(list.getCompound(i).getString(UUID_KEY)))) return true;
    return false;
  }

  private static void unavailable(ServerPlayer player, ItemStack stack, DnvPlayerArtilleryTarget target, boolean firing) {
    player.displayClientMessage(Component.translatable(unavailableKey(stack.getOrCreateTag().getString(TYPE), target, firing)), true);
  }

  static String unavailableKey(String type, DnvPlayerArtilleryTarget target, boolean firing) {
    return target != null ? target.indicatorRejectionKey(firing)
        : (DnvPlayerArtilleryTarget.SILO_INDICATOR_TYPE.equals(type)||DnvPlayerArtilleryTarget.DOGEGG_INDICATOR_TYPE.equals(type))
            ? "message.doggiesnewvehicle.nuclear.indicator_unavailable"
            : "message.doggiesnewvehicle.m1_indicator.unavailable";
  }

  private static UUID uuid(String raw) {
    try { return UUID.fromString(raw); }
    catch (IllegalArgumentException malformed) { return null; }
  }
}
