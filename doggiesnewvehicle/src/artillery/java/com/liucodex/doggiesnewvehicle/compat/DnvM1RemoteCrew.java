package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks;
import com.liucodex.doggiesnewvehicle.api.combat.DnvNativeCrewTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side station adapter; SBW owns the actual occupied seat and all vehicle controls. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle")
public final class DnvM1RemoteCrew {
  static final String BINDING = "DnvM1Binding";
  private DnvM1RemoteCrew() {}

  /** The station item itself is the only pairing tool. Vanilla entity interaction covers M1 too. */
  @SubscribeEvent public static void entityInteract(PlayerInteractEvent.EntityInteract event) {
    ItemStack stack = event.getItemStack();
    if (!stack.is(DnvRemoteWeaponStationContent.ITEM)) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    if (event.getEntity() instanceof ServerPlayer player) bindHeld(player, stack, event.getTarget());
  }

  static void bindHeld(ServerPlayer player, ItemStack held, Entity target) {
    if (target instanceof com.liucodex.doggiesnewvehicle.core.DnvRemoteManualTarget capable
        && capable.remoteManualDefinition()!=null && !capable.remoteManualDefinition().remoteManualOptIn()) {
      message(player,"mounted"); return;
    }
    if (!(target instanceof DnvNativeCrewTarget m1) || m1.isDestroyed()
        || !(target instanceof com.liucodex.doggiesnewvehicle.core.DnvRemoteManualTarget capable)
        || capable.remoteManualDefinition()==null || !capable.remoteManualDefinition().remoteManualOptIn()
        || target.level() != player.level() || target.distanceToSqr(player) > 64
        || !player.getUUID().equals(m1.remoteManualOwner())) {
      message(player, "bind_denied"); return;
    }
    var binding = new net.minecraft.nbt.CompoundTag();
    binding.putInt("Schema", 1);
    binding.putUUID("Target", target.getUUID());
    binding.putString("Dimension", player.level().dimension().location().toString());
    binding.putString("TargetType",net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
    Component custom=target.getCustomName();
    if(custom!=null&&!custom.equals(target.getType().getDescription())&&custom.getString().length()<=128)
      binding.putString("TargetName",custom.getString());
    held.getOrCreateTag().put(BINDING, binding);
    message(player, "bound",target.getDisplayName());
    player.displayClientMessage(ammoGuide(m1), false);
  }

  static void placed(ServerPlayer player, BlockPos pos, ItemStack stack) {
    if (stack.getTag() == null || !stack.getTag().contains(BINDING)) { message(player, "unbound"); return; }
    var tag = stack.getTag().getCompound(BINDING);
    if (tag.getInt("Schema") != 1 || !tag.hasUUID("Target")
        || !tag.getString("Dimension").equals(player.level().dimension().location().toString())) {
      message(player, "bind_denied"); return;
    }
    var preparation = DnvRemoteWeaponServices.prepare(player, pos, DnvRemoteWeaponLinks.Action.LINK, tag.getUUID("Target"));
    if (preparation.draft().isEmpty()) { failure(player, preparation.reason().name()); return; }
    var result = DnvRemoteWeaponServices.confirm(player, preparation.draft().orElseThrow().request().operation());
    if (result.accepted()) {
      message(player, "ready",bindingDisplayName(tag));
      if (player.serverLevel().getEntity(tag.getUUID("Target")) instanceof DnvNativeCrewTarget target)
        player.displayClientMessage(ammoGuide(target), false);
    } else failure(player, result.reason().name());
  }

  static void enter(ServerPlayer player, BlockPos pos) {
    var station = DnvRemoteWeaponServices.station(player, pos).orElse(null);
    if (station == null) { message(player, "bind_denied"); return; }
    var status = DnvRemoteWeaponServices.status(player.serverLevel(), station);
    if (status == DnvRemoteWeaponLinks.Reason.STATION_UNLINKED) { message(player, "unbound"); return; }
    if (status != DnvRemoteWeaponLinks.Reason.NONE) { failure(player, status.name()); return; }
    if (!(player.serverLevel().getEntity(station.target().orElseThrow().id()) instanceof DnvNativeCrewTarget m1)) {
      message(player, "bind_denied"); return;
    }
    String readiness = m1.remoteCrewReadiness();
    if (!readiness.equals("ready")) {
      if (readiness.equals("no_ammo")) {
        message(player, "no_ammo", ammoNames(m1));
        player.displayClientMessage(ammoGuide(m1), false);
      } else message(player, readiness);
      return;
    }
    var anchor = DnvRemoteWeaponServices.anchor(player.serverLevel(), pos);
    if (anchor == null) { failure(player, "STATION_MISSING"); return; }
    // No force-loaded chunks: the native passenger packet needs an entity already tracked by this client.
    if (!player.serverLevel().getChunkSource().chunkMap.getPlayers(m1.remoteCrewEntity().chunkPosition(), false).contains(player)) {
      failure(player, "TARGET_UNLOADED"); return;
    }
    String rejection = DnvM1CrewSafety.rejection(player, anchor);
    if (!rejection.equals("NONE")) { failure(player, rejection); return; }
    BlockPos body = pos.relative(anchor.getBlockState().getValue(DnvRemoteWeaponStationContent.FACING));
    if (!m1.beginRemoteCrew(player, station.id(), pos, station.revision(), body)) {
      message(player, "occupied"); return;
    }
    // The native passenger packet is the takeover acknowledgment. No management Screen is opened.
    message(player, "entered",m1.remoteCrewEntity().getDisplayName());
  }

  public static boolean valid(DnvNativeCrewTarget m1) {
    Entity host = m1.remoteCrewEntity();
    if (!(host.level() instanceof ServerLevel level) || m1.isDestroyed()
        || !(host.getFirstPassenger() instanceof ServerPlayer player) || !m1.isRemoteOperator(player)
        || player.getVehicle() != host || !player.isAlive() || player.isRemoved() || player.isSpectator()
        || player.level() != level || player.server.getPlayerList().getPlayer(player.getUUID()) != player
        || m1.remoteStation() == null || m1.remoteStationPosition() == null) return false;
    var store = DnvRemoteWeaponLinkStore.get(player.server);
    if (!store.writable()) return false;
    var station = store.station(m1.remoteStation()).orElse(null);
    if (station == null || station.revision() != m1.remoteStationRevision()
        || !station.owner().equals(player.getUUID()) || station.position() != m1.remoteStationPosition().asLong()
        || station.target().isEmpty() || !station.target().get().id().equals(host.getUUID())
        || DnvRemoteWeaponServices.status(level, station) != DnvRemoteWeaponLinks.Reason.NONE) return false;
    var anchor = DnvRemoteWeaponServices.anchor(level, m1.remoteStationPosition());
    if (anchor == null || !anchor.getBlockState().hasProperty(DnvRemoteWeaponStationContent.FACING)) return false;
    BlockPos body = anchor.getBlockPos().relative(anchor.getBlockState().getValue(DnvRemoteWeaponStationContent.FACING));
    return body.equals(m1.remoteBodyPosition())
        && player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(body)) < .0625
        && DnvM1CrewSafety.safe(player, net.minecraft.world.phys.Vec3.atBottomCenterOf(body), false);
  }

  /** Vanilla saves a rider's X/Z at the vehicle; remote crew must save the physical body instead. */
  public static void preserveBodySave(Entity entity, net.minecraft.nbt.CompoundTag tag) {
    if (!(entity instanceof ServerPlayer player) || !(player.getVehicle() instanceof DnvNativeCrewTarget m1)
        || !m1.isRemoteOperator(player)) return;
    var position = new net.minecraft.nbt.ListTag();
    position.add(net.minecraft.nbt.DoubleTag.valueOf(player.getX()));
    position.add(net.minecraft.nbt.DoubleTag.valueOf(player.getY()));
    position.add(net.minecraft.nbt.DoubleTag.valueOf(player.getZ()));
    tag.put("Pos", position);
    tag.remove("RootVehicle");
  }

  public static Runnable guardQueuedShot(com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity vehicle, Runnable nativeWork) {
    if (!(vehicle instanceof DnvNativeCrewTarget m1)) return nativeWork;
    long generation = m1.remoteCrewGeneration();
    return () -> {
      if (generation == m1.remoteCrewGeneration() && m1.remoteCrewReadiness().equals("ready") && valid(m1)) nativeWork.run();
    };
  }

  static Component bindingDisplayName(net.minecraft.nbt.CompoundTag tag) {
    if(tag.contains("TargetName")&&!tag.getString("TargetName").isBlank()&&tag.getString("TargetName").length()<=128)
      return Component.literal(tag.getString("TargetName"));
    var id=net.minecraft.resources.ResourceLocation.tryParse(tag.getString("TargetType"));
    if(id!=null&&net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id))
      return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id).getDescription();
    return Component.translatable("tooltip.doggiesnewvehicle.remote_weapon_station.unknown_target");
  }
  /** Only reads the installed native weapon consumers; never selects, loads or consumes ammunition. */
  static Component ammoNames(DnvNativeCrewTarget target) {
    var names = Component.empty();
    if (target.remoteCrewEntity() instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity vehicle) {
      var gun = vehicle.getGunData("Main");
      if (gun != null) for (var consumer : gun.get(com.atsuishio.superbwarfare.data.gun.GunProp.AMMO_CONSUMER)) {
        var stack = consumer.stack();
        if (stack.isEmpty()) continue;
        if (!names.getString().isEmpty()) names.append(Component.literal(" / "));
        names.append(stack.getHoverName());
      }
    }
    return names.getString().isEmpty()
        ? Component.translatable("message.doggiesnewvehicle.m1_crew.ammo_unknown") : names;
  }
  static Component ammoGuide(DnvNativeCrewTarget target) {
    return Component.translatable("message.doggiesnewvehicle.m1_crew.ammo_guide",
        target.remoteCrewEntity().getDisplayName(), ammoNames(target));
  }
  private static void message(ServerPlayer player, String key, Object... args) {
    player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.m1_crew." + key,args), true);
  }
  private static void failure(ServerPlayer player, String reason) {
    player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.remote_station.reason." + reason), true);
  }
}
