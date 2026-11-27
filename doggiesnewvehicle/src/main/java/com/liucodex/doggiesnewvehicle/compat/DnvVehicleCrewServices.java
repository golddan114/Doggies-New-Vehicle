package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.ai.DnvVehicleCrewProfile;
import com.liucodex.doggiesnewvehicle.init.ModItems;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewConfigSessions;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewCoreCodec;
import com.liucodex.doggiesnewvehicle.item.DnvVehicleCrewCoreItem;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Process-local crew services; tracked vehicle references are released on departure or stop. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID)
public final class DnvVehicleCrewServices {
  private static final int DRIFT_CORRECTION_INTERVAL_TICKS = 20;
  private static final ResourceLocation CREW_CAPABILITY_ID =
      ResourceLocation.fromNamespaceAndPath(
          DoggiesNewVehicle.MOD_ID, "vehicle_crew_runtime");
  private static final DnvSbwVehicleCrewCatalog SBW_CATALOG =
      new DnvSbwVehicleCrewCatalog();
  private static final Map<MinecraftServer, Map<UUID, VehicleEntity>> TRACKED =
      new IdentityHashMap<>();
  private static final Map<MinecraftServer, DnvVehicleCrewConfigSessions> CONFIG_SESSIONS =
      new IdentityHashMap<>();
  private DnvVehicleCrewServices() {}
  public static DnvSbwVehicleCrewCatalog sbwCatalog() {
    return SBW_CATALOG;
  }
  @SubscribeEvent
  public static void addReloadListener(AddReloadListenerEvent event) {
    event.addListener(new DnvSbwVehicleCrewReloadListener(
        SBW_CATALOG, DnvVehicleCrewServices::catalogReloaded));
  }
  @SubscribeEvent
  public static void attachVehicleCapability(AttachCapabilitiesEvent<Entity> event) {
    if (!(event.getObject() instanceof VehicleEntity vehicle)) {
      return;
    }
    DnvVehicleCrewCapabilityProvider provider =
        new DnvVehicleCrewCapabilityProvider(vehicle);
    event.addCapability(CREW_CAPABILITY_ID, provider);
    event.addListener(provider::invalidate);
  }
  @SubscribeEvent
  public static void vehicleJoined(EntityJoinLevelEvent event) {
    if (event.getEntity() instanceof VehicleEntity vehicle
        && event.getLevel() instanceof ServerLevel) {
      synchronizeVehicle(vehicle);
    }
  }
  @SubscribeEvent
  public static void vehicleLeft(EntityLeaveLevelEvent event) {
    if (!(event.getEntity() instanceof VehicleEntity vehicle)
        || !(event.getLevel() instanceof ServerLevel level)) {
      return;
    }
    untrack(level.getServer(), vehicle);
  }
  @SubscribeEvent
  public static void passengerMountChanged(EntityMountEvent event) {
    if (!(event.getEntityBeingMounted() instanceof VehicleEntity vehicle)
        || !(vehicle.level() instanceof ServerLevel level)) {
      return;
    }
    level.getServer().execute(() -> synchronizeSeats(vehicle));
  }
  @SubscribeEvent
  public static void trackedVehicleTick(TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.END) {
      return;
    }
    if (event.getServer().getTickCount() % DRIFT_CORRECTION_INTERVAL_TICKS == 0) {
      synchronizeTracked(event.getServer());
      configSessions(event.getServer()).expire(event.getServer().getTickCount());
    }
    tickTrackedAutonomy(event.getServer());
  }
  @SubscribeEvent
  public static void bindCrewCore(PlayerInteractEvent.EntityInteract event) {
    if (!event.getEntity().isShiftKeyDown()
        || !(event.getTarget() instanceof VehicleEntity vehicle)) {
      return;
    }
    ItemStack stack = event.getEntity().getItemInHand(event.getHand());
    if (!stack.is(ModItems.VEHICLE_CREW_CORE.get())) {
      return;
    }
    event.setCancellationResult(InteractionResult.sidedSuccess(
        event.getLevel().isClientSide()));
    event.setCanceled(true);
    if (!(event.getEntity() instanceof ServerPlayer player)
        || !(stack.getItem() instanceof DnvVehicleCrewCoreItem coreItem)) {
      return;
    }
    DnvVehicleCrewCoreItem.BindResult result =
        coreItem.bind(stack, vehicle, player, SBW_CATALOG);
    Component bindMessage = result.accepted()
        ? Component.translatable("message.doggiesnewvehicle.vehicle_crew_core.bound")
        : Component.translatable(
            "message.doggiesnewvehicle.vehicle_crew_core.bind_failed",
            result.issue().name());
    player.displayClientMessage(
        bindMessage,
        true);
  }
  @SubscribeEvent
  public static void clearServerState(ServerStoppedEvent event) {
    Map<UUID, VehicleEntity> vehicles = TRACKED.remove(event.getServer());
    if (vehicles != null) {
      new ArrayList<>(vehicles.values()).forEach(vehicle ->
          vehicle.getCapability(DnvVehicleCrewCapability.CAPABILITY)
              .ifPresent(DnvVehicleCrewAttachment::clearOnUnload));
    }
    long revision = SBW_CATALOG.snapshot().revision();
    SBW_CATALOG.clear(revision);
    DnvVehicleCrewConfigSessions sessions = CONFIG_SESSIONS.remove(event.getServer());
    if (sessions != null) {
      sessions.clear();
    }
  }
  @SubscribeEvent
  public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
    clearPlayerPortableState(event.getEntity());
  }
  @SubscribeEvent
  public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    clearPlayerPortableState(event.getEntity());
  }
  public static void openCoreConfiguration(
      ServerPlayer player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    DnvVehicleCrewCoreCodec.DecodeResult decoded =
        DnvVehicleCrewCoreCodec.decode(stack.getTag());
    if (!stack.is(ModItems.VEHICLE_CREW_CORE.get()) || !decoded.accepted()) {
      displayConfigOpenFailure(player, DnvVehicleCrewConfigSessions.Issue.PROFILE_REJECTED);
      return;
    }
    DnvVehicleCrewProfile.Draft draft = decoded.draft().orElseThrow();
    var allowlist = SBW_CATALOG.find(draft.vehicleTypeId());
    if (allowlist.isEmpty()) {
      displayConfigOpenFailure(player, DnvVehicleCrewConfigSessions.Issue.CATALOG_MISMATCH);
      return;
    }
    DnvVehicleCrewConfigSessions.OpenResult result = configSessions(player.server).open(
        draft,
        allowlist.orElseThrow(),
        player.getUUID(),
        player.hasPermissions(2),
        SBW_CATALOG.snapshot().revision(),
        player.server.getTickCount(),
        handSlot(hand));
    if (!result.accepted()) {
      displayConfigOpenFailure(player, result.issue());
      return;
    }
    DnvVehicleCrewConfigNetwork.open(player, result.view().orElseThrow());
  }
  public static void commitCoreConfiguration(
      ServerPlayer player, DnvVehicleCrewConfigNetwork.CommitRequest request) {
    InteractionHand hand = interactionHand(request.hand());
    ItemStack stack = player.getItemInHand(hand);
    DnvVehicleCrewCoreCodec.DecodeResult decoded =
        DnvVehicleCrewCoreCodec.decode(stack.getTag());
    if (!stack.is(ModItems.VEHICLE_CREW_CORE.get()) || !decoded.accepted()) {
      configSessions(player.server).close(request.token(), player.getUUID());
      DnvVehicleCrewConfigNetwork.respond(
          player,
          request.token(),
          false,
          DnvVehicleCrewConfigSessions.Issue.CORE_CHANGED,
          Optional.empty(), List.of(), List.of());
      return;
    }
    DnvVehicleCrewConfigSessions.CommitResult result =
        configSessions(player.server).commitWireValues(
            request.token(),
            player.getUUID(),
            player.hasPermissions(2),
            SBW_CATALOG.snapshot().revision(),
            player.server.getTickCount(),
            request.hand(),
            decoded.draft().orElseThrow(),
            request.flightModeName(),
            request.stationIds(),
            request.stationModeNames());
    if (result.accepted()) {
      stack.setTag(DnvVehicleCrewCoreCodec.encode(result));
      player.getInventory().setChanged();
    }
    DnvVehicleCrewConfigNetwork.respond(
        player, request.token(), result.accepted(), result.issue(),
        result.flightModeName(), result.stationIds(), result.stationModeNames());
  }
  public static void cancelCoreConfiguration(ServerPlayer player, UUID token) {
    configSessions(player.server).close(token, player.getUUID());
  }
  /** Exact SBW inventory mixin callback; no-op on clients or unrelated vehicles. */
  public static void vehicleInventoryChanged(VehicleEntity vehicle) {
    synchronizeVehicle(vehicle);
  }

  /** Exact SBW change-seat mixin callback after a successful seat move. */
  public static void vehicleSeatChanged(VehicleEntity vehicle) {
    synchronizeSeats(vehicle);
  }

  private static void synchronizeVehicle(VehicleEntity vehicle) {
    if (!(vehicle.level() instanceof ServerLevel level) || vehicle.isRemoved()) {
      return;
    }
    vehicle.getCapability(DnvVehicleCrewCapability.CAPABILITY).ifPresent(attachment -> {
      DnvVehicleCrewAttachment.SyncResult result = attachment.synchronize(SBW_CATALOG);
      if (result.corePresent()) {
        track(level.getServer(), vehicle);
      } else {
        untrack(level.getServer(), vehicle);
      }
    });
  }

  private static void synchronizeSeats(VehicleEntity vehicle) {
    if (!(vehicle.level() instanceof ServerLevel) || vehicle.isRemoved()) {
      return;
    }
    vehicle.getCapability(DnvVehicleCrewCapability.CAPABILITY)
        .ifPresent(attachment -> attachment.synchronizeSeats(SBW_CATALOG));
  }

  static void synchronizeTracked(MinecraftServer server) {
    Map<UUID, VehicleEntity> vehicles = TRACKED.get(server);
    if (vehicles == null || vehicles.isEmpty()) {
      return;
    }
    for (VehicleEntity vehicle : new ArrayList<>(vehicles.values())) {
      if (!registered(server, vehicle)) {
        untrack(server, vehicle);
      } else {
        synchronizeVehicle(vehicle);
      }
    }
  }

  private static void catalogReloaded() {
    CONFIG_SESSIONS.values().forEach(DnvVehicleCrewConfigSessions::clear);
    revalidateTrackedVehicles();
  }

  private static void revalidateTrackedVehicles() {
    for (MinecraftServer server : new ArrayList<>(TRACKED.keySet())) {
      synchronizeTracked(server);
    }
  }

  private static void tickTrackedAutonomy(MinecraftServer server) {
    Map<UUID, VehicleEntity> vehicles = TRACKED.get(server);
    if (vehicles == null || vehicles.isEmpty()) {
      return;
    }
    for (VehicleEntity vehicle : new ArrayList<>(vehicles.values())) {
      if (!registered(server, vehicle)) {
        untrack(server, vehicle);
      } else {
        vehicle.getCapability(DnvVehicleCrewCapability.CAPABILITY)
            .ifPresent(DnvVehicleCrewAttachment::tickAutonomy);
      }
    }
  }

  private static void track(MinecraftServer server, VehicleEntity vehicle) {
    Map<UUID, VehicleEntity> vehicles =
        TRACKED.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
    vehicles.entrySet().removeIf(entry ->
        entry.getValue() == vehicle && !entry.getKey().equals(vehicle.getUUID()));
    vehicles.put(vehicle.getUUID(), vehicle);
  }

  private static void untrack(MinecraftServer server, VehicleEntity vehicle) {
    vehicle.getCapability(DnvVehicleCrewCapability.CAPABILITY)
        .ifPresent(DnvVehicleCrewAttachment::clearOnUnload);
    Map<UUID, VehicleEntity> vehicles = TRACKED.get(server);
    if (vehicles == null) {
      return;
    }
    vehicles.entrySet().removeIf(entry -> entry.getValue() == vehicle);
    if (vehicles.isEmpty()) {
      TRACKED.remove(server);
    }
  }

  private static DnvVehicleCrewConfigSessions configSessions(MinecraftServer server) {
    return CONFIG_SESSIONS.computeIfAbsent(
        Objects.requireNonNull(server, "server"),
        ignored -> new DnvVehicleCrewConfigSessions());
  }

  private static void clearPlayerPortableState(Entity entity) {
    if (entity instanceof ServerPlayer player) {
      for (VehicleEntity vehicle : trackedVehicles(player.server)) {
        vehicle.getCapability(DnvVehicleCrewCapability.CAPABILITY).ifPresent(attachment -> {
          var session = attachment.combatOrders();
          var snapshot = session.view(vehicle.level().getGameTime());
          if (snapshot.binding().filter(binding -> binding.ownerId().equals(player.getUUID())).isPresent()
              || snapshot.orders().activeOrder()
                  .filter(order -> order.issuerId().equals(player.getUUID())).isPresent()) {
            session.invalidate(com.liucodex.doggiesnewvehicle.core.DnvEntityDesignationSession.State.ISSUER_UNAVAILABLE,
                vehicle.level().getGameTime());
          }
        });
      }
      DnvVehicleCrewConfigSessions sessions = CONFIG_SESSIONS.get(player.server);
      if (sessions != null) {
        sessions.closePlayer(player.getUUID());
      }
    }
  }

  static Optional<VehicleEntity> trackedVehicle(
      MinecraftServer server, UUID vehicleId) {
    Map<UUID, VehicleEntity> vehicles = TRACKED.get(server);
    if (vehicles == null) {
      return Optional.empty();
    }
    VehicleEntity vehicle = vehicles.get(vehicleId);
    return vehicle == null || !registered(server, vehicle)
        ? Optional.empty()
        : Optional.of(vehicle);
  }

  static List<VehicleEntity> trackedVehicles(MinecraftServer server) {
    Map<UUID, VehicleEntity> vehicles = TRACKED.get(server);
    return vehicles == null ? List.of() : List.copyOf(vehicles.values());
  }

  private static boolean registered(MinecraftServer server, VehicleEntity vehicle) {
    return !vehicle.isRemoved() && vehicle.level() instanceof ServerLevel level
        && level.getServer() == server && level.getEntity(vehicle.getUUID()) == vehicle;
  }

  private static DnvVehicleCrewConfigSessions.HandSlot handSlot(InteractionHand hand) {
    return hand == InteractionHand.MAIN_HAND
        ? DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND
        : DnvVehicleCrewConfigSessions.HandSlot.OFF_HAND;
  }

  private static InteractionHand interactionHand(
      DnvVehicleCrewConfigSessions.HandSlot hand) {
    return hand == DnvVehicleCrewConfigSessions.HandSlot.MAIN_HAND
        ? InteractionHand.MAIN_HAND
        : InteractionHand.OFF_HAND;
  }

  private static void displayConfigOpenFailure(
      ServerPlayer player, DnvVehicleCrewConfigSessions.Issue issue) {
    String message = issue == DnvVehicleCrewConfigSessions.Issue.PROFILE_REJECTED
        || issue == DnvVehicleCrewConfigSessions.Issue.CATALOG_MISMATCH
        ? "message.doggiesnewvehicle.vehicle_crew_core.platform_changed" : "message.doggiesnewvehicle.vehicle_crew_core.config_failed";
    player.displayClientMessage(
        Component.translatable(
            message, issue.name()),
        true);
  }
}
