package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvHoverRoutePlatformEntity;
import com.liucodex.doggiesnewvehicle.platform.DnvPlatformContent;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Temporary operator deployment and owner binding for the owned route platform. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID)
public final class DnvHoverRoutePlatformCommands {
    private DnvHoverRoutePlatformCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dnv")
                .then(Commands.literal("hover_platform")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Commands.literal("create")
                                .requires(source -> source.hasPermission(2))
                                .executes(DnvHoverRoutePlatformCommands::create))
                        .then(Commands.literal("bind")
                                .then(Commands.argument("platform", UuidArgument.uuid())
                                        .executes(DnvHoverRoutePlatformCommands::bind)))
                        .then(Commands.literal("board")
                                .then(Commands.argument("platform", UuidArgument.uuid())
                                        .executes(DnvHoverRoutePlatformCommands::board)))
                        .then(Commands.literal("exit")
                                .executes(DnvHoverRoutePlatformCommands::exit))
                        .then(Commands.literal("start")
                                .then(Commands.argument("platform", UuidArgument.uuid())
                                        .executes(context -> start(context, false))))
                        .then(Commands.literal("loop")
                                .then(Commands.argument("platform", UuidArgument.uuid())
                                        .executes(context -> start(context, true))))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("platform", UuidArgument.uuid())
                                        .executes(DnvHoverRoutePlatformCommands::stop)))
                        .then(Commands.literal("land")
                                .then(Commands.argument("platform", UuidArgument.uuid())
                                        .executes(DnvHoverRoutePlatformCommands::land)))
                        .then(Commands.literal("gun")
                                .then(Commands.literal("claim")
                                        .then(Commands.argument("platform", UuidArgument.uuid())
                                                .executes(DnvHoverRoutePlatformCommands::claimGun)))
                                .then(Commands.literal("release")
                                        .executes(DnvHoverRoutePlatformCommands::releaseGun))
                                .then(Commands.literal("fire")
                                        .then(Commands.argument("platform", UuidArgument.uuid())
                                                .then(Commands.argument("target", Vec3Argument.vec3())
                                                        .executes(DnvHoverRoutePlatformCommands::fireGun)))))));
    }

    private static int create(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        DnvHoverRoutePlatformEntity platform = DnvPlatformContent.HOVER_ROUTE_PLATFORM
                .get().create(level);
        if (platform == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.no_space"));
            return 0;
        }
        platform.setPos(player.getX(), player.getY() + 4.0D, player.getZ());
        AABB bounds = platform.getBoundingBox();
        if (!loaded(level, bounds)
                || bounds.minY < level.getMinBuildHeight()
                || bounds.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(bounds)
                || !level.noCollision(platform, bounds)
                || !level.addFreshEntity(platform)) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.no_space"));
            return 0;
        }
        platform.bindOwner(player.getUUID());
        player.sendSystemMessage(Component.translatable(
                "message.doggiesnewvehicle.hover_route_platform.created",
                platform.getUUID()));
        return 1;
    }

    private static int bind(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DnvHoverRoutePlatformEntity platform = platform(context, player);
        if (platform == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.unavailable"));
            return 0;
        }
        var result = DnvHoverRoutePlatformServices.bind(player, platform);
        if (result.isEmpty() || !result.orElseThrow().accepted()) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.bind_rejected"));
            return 0;
        }
        player.sendSystemMessage(Component.translatable(
                "message.doggiesnewvehicle.hover_route_platform.bound"));
        return 1;
    }

    private static int start(
            CommandContext<CommandSourceStack> context, boolean loop)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DnvHoverRoutePlatformEntity platform = platform(context, player);
        if (platform == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.unavailable"));
            return 0;
        }
        DnvHoverRoutePlatformServices.StartResult result =
                DnvHoverRoutePlatformServices.start(player, platform, loop);
        player.sendSystemMessage(result.accepted()
                ? Component.translatable(
                        "message.doggiesnewvehicle.hover_route_platform.started",
                        loop ? "LOOP" : "HOLD")
                : Component.translatable(
                        "message.doggiesnewvehicle.hover_route_platform.start_rejected",
                        Component.translatable(
                                "message.doggiesnewvehicle.hover_route_platform.start_issue."
                                        + result.issue().name())));
        return result.accepted() ? 1 : 0;
    }

    private static int board(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DnvHoverRoutePlatformEntity platform = platform(context, player);
        if (platform == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.unavailable"));
            return 0;
        }
        DnvHoverPlatformCrew.BoardIssue issue = DnvHoverPlatformCrew.board(player, platform);
        player.sendSystemMessage(issue == DnvHoverPlatformCrew.BoardIssue.NONE
                ? Component.translatable(
                        "message.doggiesnewvehicle.hover_route_platform.boarded")
                : Component.translatable(
                        "message.doggiesnewvehicle.hover_route_platform.board_rejected",
                        Component.translatable(
                                "message.doggiesnewvehicle.hover_route_platform.board_issue."
                                        + issue.name())));
        return issue == DnvHoverPlatformCrew.BoardIssue.NONE ? 1 : 0;
    }

    private static int exit(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!(player.getVehicle() instanceof DnvHoverRoutePlatformEntity)) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.not_riding"));
            return 0;
        }
        boolean accepted = DnvHoverPlatformCrew.exit(player);
        player.sendSystemMessage(Component.translatable(accepted
                ? "message.doggiesnewvehicle.hover_route_platform.exited"
                : "message.doggiesnewvehicle.hover_route_platform.exit_unsafe"));
        return accepted ? 1 : 0;
    }

    private static int claimGun(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DnvHoverRoutePlatformEntity platform = platform(context, player);
        if (platform == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.unavailable"));
            return 0;
        }
        DnvHoverPlatformWeapons.SessionIssue issue =
                DnvHoverPlatformWeapons.claim(player, platform);
        player.sendSystemMessage(Component.translatable(
                "message.doggiesnewvehicle.hover_route_platform.gun.claim."
                        + issue.name()));
        return issue == DnvHoverPlatformWeapons.SessionIssue.NONE ? 1 : 0;
    }

    private static int releaseGun(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DnvHoverPlatformWeapons.SessionIssue issue =
                DnvHoverPlatformWeapons.release(player);
        player.sendSystemMessage(Component.translatable(
                "message.doggiesnewvehicle.hover_route_platform.gun.release."
                        + issue.name()));
        return issue == DnvHoverPlatformWeapons.SessionIssue.NONE ? 1 : 0;
    }

    private static int fireGun(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DnvHoverRoutePlatformEntity platform = platform(context, player);
        if (platform == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.unavailable"));
            return 0;
        }
        Vec3 target = Vec3Argument.getVec3(context, "target");
        DnvHoverPlatformWeapons.ShotIssue issue =
                DnvHoverPlatformWeapons.fire(player, platform, target);
        player.sendSystemMessage(Component.translatable(
                "message.doggiesnewvehicle.hover_route_platform.gun.fire."
                        + issue.name()));
        return issue == DnvHoverPlatformWeapons.ShotIssue.FIRED ? 1 : 0;
    }

    private static int stop(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DnvHoverRoutePlatformEntity platform = platform(context, player);
        if (platform == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.unavailable"));
            return 0;
        }
        boolean accepted = DnvHoverRoutePlatformServices.stop(player, platform);
        player.sendSystemMessage(Component.translatable(accepted
                ? "message.doggiesnewvehicle.hover_route_platform.stopped"
                : "message.doggiesnewvehicle.hover_route_platform.stop_rejected"));
        return accepted ? 1 : 0;
    }

    private static int land(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DnvHoverRoutePlatformEntity platform = platform(context, player);
        if (platform == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.unavailable"));
            return 0;
        }
        boolean accepted = DnvHoverRoutePlatformServices.land(player, platform);
        player.sendSystemMessage(Component.translatable(accepted
                ? "message.doggiesnewvehicle.hover_route_platform.landing"
                : "message.doggiesnewvehicle.hover_route_platform.land_rejected"));
        return accepted ? 1 : 0;
    }

    private static DnvHoverRoutePlatformEntity platform(
            CommandContext<CommandSourceStack> context, ServerPlayer player) {
        Entity entity = player.serverLevel().getEntity(
                UuidArgument.getUuid(context, "platform"));
        return entity instanceof DnvHoverRoutePlatformEntity platform
                ? platform : null;
    }

    private static boolean loaded(ServerLevel level, AABB bounds) {
        int minX = (int) Math.floor(bounds.minX) >> 4;
        int maxX = (int) Math.floor(bounds.maxX - 1.0E-6D) >> 4;
        int minZ = (int) Math.floor(bounds.minZ) >> 4;
        int maxZ = (int) Math.floor(bounds.maxZ - 1.0E-6D) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
