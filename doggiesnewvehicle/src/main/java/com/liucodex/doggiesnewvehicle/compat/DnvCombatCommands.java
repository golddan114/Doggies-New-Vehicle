package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.core.DnvEntityDesignationSession;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Player-owned server commands; all UUID resolution is limited to already-loaded entities. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID)
public final class DnvCombatCommands {
  private DnvCombatCommands() {}

  @SubscribeEvent
  public static void register(RegisterCommandsEvent event) {
    register(event.getDispatcher());
  }

  static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("dnv")
        .then(Commands.literal("combat").requires(source -> source.getEntity() instanceof ServerPlayer)
            .executes(DnvCombatCommands::list)
            .then(Commands.argument("vehicle", UuidArgument.uuid())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    available(context.getSource().getPlayerOrException()).stream()
                        .map(vehicle -> vehicle.getUUID().toString()), builder))
                .then(Commands.literal("status").executes(context -> act(context, "status")))
                .then(Commands.literal("targets").executes(context -> act(context, "targets")))
                .then(Commands.literal("cancel").executes(context -> act(context, "cancel")))
                .then(Commands.literal("designate")
                    .then(Commands.argument("target", UuidArgument.uuid())
                        .executes(context -> act(context, "designate")))))));
  }

  private static List<VehicleEntity> available(ServerPlayer player) {
    return DnvVehicleCrewServices.trackedVehicles(player.server).stream()
        .filter(vehicle -> vehicle.getCapability(DnvVehicleCrewCapability.CAPABILITY)
            .map(attachment -> attachment.managedState()
                .filter(state -> DnvEntityDesignationServices.mayOperate(player, vehicle, state.ownerId()))
                .isPresent()).orElse(false))
        .sorted(Comparator.comparingDouble(player::distanceToSqr))
        .limit(64).toList();
  }

  private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    ServerPlayer player = context.getSource().getPlayerOrException();
    DnvVehicleCrewServices.synchronizeTracked(player.server);
    player.sendSystemMessage(message("help"));
    List<VehicleEntity> vehicles = available(player);
    for (VehicleEntity vehicle : vehicles) {
      player.sendSystemMessage(suggestion(message("vehicle", vehicle.getDisplayName(), vehicle.getUUID()),
          prefix(vehicle) + "status"));
    }
    if (vehicles.isEmpty()) {
      player.sendSystemMessage(message("unavailable"));
    }
    return vehicles.size();
  }

  private static int act(CommandContext<CommandSourceStack> context, String action)
      throws CommandSyntaxException {
    ServerPlayer player = context.getSource().getPlayerOrException();
    Entity resolved = player.serverLevel().getEntity(UuidArgument.getUuid(context, "vehicle"));
    if (!(resolved instanceof VehicleEntity vehicle) || !player.serverLevel().hasChunkAt(vehicle.blockPosition())) {
      player.sendSystemMessage(message("unavailable"));
      return 0;
    }
    DnvVehicleCrewAttachment attachment = vehicle.getCapability(DnvVehicleCrewCapability.CAPABILITY)
        .resolve().orElse(null);
    if (attachment == null) {
      player.sendSystemMessage(message("unavailable"));
      return 0;
    }
    attachment.synchronize(DnvVehicleCrewServices.sbwCatalog());
    Optional<DnvVehicleCrewAttachment.ManagedState> state = attachment.managedState();
    if (state.isEmpty() || !DnvEntityDesignationServices.mayOperate(
        player, vehicle, state.orElseThrow().ownerId())) {
      player.sendSystemMessage(message("unavailable"));
      return 0;
    }
    if ("targets".equals(action)) {
      return targets(player, vehicle, state.orElseThrow().ownerId());
    }
    if ("status".equals(action)) {
      showStatus(player, vehicle, DnvEntityDesignationServices.observe(vehicle, attachment));
      return 1;
    }
    DnvEntityDesignationSession.Result result = DnvEntityDesignationServices.submit(
        player, vehicle, attachment, "designate".equals(action)
            ? Optional.of(UuidArgument.getUuid(context, "target")) : Optional.empty());
    player.sendSystemMessage(message(result.accepted() ? action + "_accepted" : "rejected",
        code(result.reason())));
    player.sendSystemMessage(message("receipt", vehicle.getUUID(), result.operationId(),
        result.view().orders().revision()));
    showStatus(player, vehicle, result.view());
    return result.accepted() ? 1 : 0;
  }

  private static int targets(ServerPlayer player, VehicleEntity vehicle, UUID owner) {
    List<Entity> targets = DnvTargetCandidateCache.boundedTopK(player.serverLevel().getEntities(
        vehicle, vehicle.getBoundingBox().inflate(DnvCombatPolicyRuntime.CREW_TARGET_RANGE),
        candidate -> DnvCombatPolicyRuntime.crewDesignationAllowed(vehicle, candidate, owner)),
        ignored -> true, vehicle::distanceToSqr, Entity::getUUID, 32);
    player.sendSystemMessage(message("targets", targets.size()));
    for (Entity target : targets) {
      player.sendSystemMessage(suggestion(message("target", target.getDisplayName(),
          Math.round(Math.sqrt(vehicle.distanceToSqr(target)))),
          prefix(vehicle) + "designate " + target.getUUID()));
    }
    return targets.size();
  }

  private static void showStatus(
      ServerPlayer player, VehicleEntity vehicle, DnvEntityDesignationSession.View view) {
    player.sendSystemMessage(message("status", vehicle.getDisplayName(), code(view.state().name()),
        view.targetId().map(UUID::toString).orElse("—")));
    player.sendSystemMessage(message("mission_required"));
    view.binding().ifPresent(binding -> player.sendSystemMessage(message("identity",
        binding.coreId(), binding.coreRevision(), view.orders().revision())));
    player.sendSystemMessage(suggestion(message("choose_target"), prefix(vehicle) + "targets"));
    player.sendSystemMessage(suggestion(message("cancel"), prefix(vehicle) + "cancel"));
  }

  static Component suggestion(Component label, String command) {
    return label.copy().withStyle(style -> style
        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, message("suggest"))));
  }

  private static String prefix(VehicleEntity vehicle) {
    return "/dnv combat " + vehicle.getUUID() + " ";
  }

  private static Component code(String code) {
    return Component.translatableWithFallback(
        "message.doggiesnewvehicle.combat.code." + code.toLowerCase(java.util.Locale.ROOT), code);
  }

  private static Component message(String suffix, Object... arguments) {
    return Component.translatable("message.doggiesnewvehicle.combat." + suffix, arguments);
  }
}
