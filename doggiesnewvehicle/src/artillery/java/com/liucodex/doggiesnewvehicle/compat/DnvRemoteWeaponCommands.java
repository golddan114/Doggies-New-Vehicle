package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.Action;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Owner-checked link diagnostics and recovery; native crew owns all vehicle controls. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID)
public final class DnvRemoteWeaponCommands {
  private DnvRemoteWeaponCommands() {}

  @SubscribeEvent
  public static void register(RegisterCommandsEvent event) { register(event.getDispatcher()); }

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    var position = Commands.argument("position", BlockPosArgument.blockPos())
        .then(Commands.literal("create").executes(DnvRemoteWeaponCommands::create))
        .then(Commands.literal("status").executes(DnvRemoteWeaponCommands::status));
    var prepare = Commands.literal("prepare");
    for (Action action : Action.values()) {
      var branch = Commands.literal(action.name().toLowerCase(java.util.Locale.ROOT));
      if (action == Action.LINK || action == Action.REPLACE) {
        branch.then(Commands.argument("target", UuidArgument.uuid()).executes(context -> prepare(context, action)));
      } else branch.executes(context -> prepare(context, action));
      prepare.then(branch);
    }
    position.then(prepare);
    dispatcher.register(Commands.literal("dnv").then(Commands.literal("rws")
        .requires(source -> source.getEntity() instanceof ServerPlayer)
        .executes(context -> {
          context.getSource().sendSuccess(() -> Component.translatable("message.doggiesnewvehicle.rws.help"), false);
          return 1;
        }).then(position)
        .then(Commands.literal("confirm").then(Commands.argument("operation", UuidArgument.uuid())
            .executes(DnvRemoteWeaponCommands::confirm)))));
  }

  private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    ServerPlayer player = context.getSource().getPlayerOrException();
    var reason = DnvRemoteWeaponServices.create(player, position(context));
    player.sendSystemMessage(Component.translatable("message.doggiesnewvehicle.rws.created", reason(reason)));
    status(context);
    return reason == com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.Reason.NONE ? 1 : 0;
  }

  private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    ServerPlayer player = context.getSource().getPlayerOrException();
    return status(player, position(context));
  }

  private static int status(ServerPlayer player, BlockPos pos) {
    var station = DnvRemoteWeaponServices.station(player, pos);
    if (station.isEmpty()) {
      player.sendSystemMessage(Component.translatable("message.doggiesnewvehicle.rws.unavailable"));
      return 0;
    }
    var value = station.orElseThrow();
    player.sendSystemMessage(Component.translatable("message.doggiesnewvehicle.rws.status",
        value.id().toString(), value.revision(), reason(DnvRemoteWeaponServices.status(player.serverLevel(), value)),
        value.target().<Component>map(target -> Component.literal(target.type() + " / " + target.id()))
            .orElseGet(DnvRemoteWeaponCommands::none)));
    return 1;
  }

  private static int prepare(CommandContext<CommandSourceStack> context, Action action) throws CommandSyntaxException {
    ServerPlayer player = context.getSource().getPlayerOrException();
    UUID target = action == Action.LINK || action == Action.REPLACE ? UuidArgument.getUuid(context, "target") : null;
    var result = DnvRemoteWeaponServices.prepare(player, position(context), action, target);
    if (result.draft().isEmpty()) {
      player.sendSystemMessage(Component.translatable("message.doggiesnewvehicle.rws.prepare_failed", reason(result.reason())));
      return 0;
    }
    var draft = result.draft().orElseThrow();
    preview(player, draft);
    player.sendSystemMessage(Component.translatable("message.doggiesnewvehicle.rws.confirm_button").withStyle(style -> style.withClickEvent(
        new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/dnv rws confirm " + draft.request().operation()))));
    return 1;
  }

  static void preview(ServerPlayer player, DnvRemoteWeaponServices.Draft draft) {
    player.sendSystemMessage(Component.translatable("message.doggiesnewvehicle.rws.preview",
        Component.translatable("message.doggiesnewvehicle.rws.action." + draft.request().action().name()),
        BlockPos.of(draft.station().position()).toShortString(),
        draft.station().target().<Component>map(value -> Component.literal(value.id().toString())).orElseGet(DnvRemoteWeaponCommands::none),
        draft.request().target().<Component>map(value -> Component.literal(value.id().toString())).orElseGet(DnvRemoteWeaponCommands::none)));
  }

  private static int confirm(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    ServerPlayer player = context.getSource().getPlayerOrException();
    var result = DnvRemoteWeaponServices.confirm(player, UuidArgument.getUuid(context, "operation"));
    return confirmReply(player, result);
  }

  static int confirmReply(ServerPlayer player, com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.Result result) {
    player.sendSystemMessage(Component.translatable("message.doggiesnewvehicle.rws.result",
        Component.translatable("message.doggiesnewvehicle.rws." + (result.accepted() ? "accepted" : "rejected")),
        reason(result.reason()), result.operation().toString(), result.revision(),
        result.replayed() ? Component.translatable("message.doggiesnewvehicle.rws.replayed") : Component.empty()));
    return result.accepted() ? 1 : 0;
  }

  private static Component reason(com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.Reason value) {
    return Component.translatable("message.doggiesnewvehicle.remote_station.reason." + value.name());
  }

  private static Component none() { return Component.translatable("message.doggiesnewvehicle.rws.none"); }

  private static BlockPos position(CommandContext<CommandSourceStack> context) {
    // getLoadedBlockPos can throw and is unnecessary: the service checks hasChunkAt before all reads.
    return BlockPosArgument.getBlockPos(context, "position");
  }

  @SubscribeEvent
  public static void stopping(ServerStoppingEvent event) {
    DnvRemoteWeaponServices.clear(event.getServer());
  }

  @SubscribeEvent
  public static void tick(TickEvent.ServerTickEvent event) {
    if (event.phase == TickEvent.Phase.END) {
      DnvRemoteWeaponServices.expire(event.getServer());
    }
  }

  @SubscribeEvent
  public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) clearPlayer(player);
  }

  @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) clearPlayer(player);
  }
  @SubscribeEvent public static void death(LivingDeathEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) clearPlayer(player);
  }
  private static void clearPlayer(ServerPlayer player) {
    DnvRemoteWeaponServices.clearPlayer(player.server, player.getUUID());
  }
}
