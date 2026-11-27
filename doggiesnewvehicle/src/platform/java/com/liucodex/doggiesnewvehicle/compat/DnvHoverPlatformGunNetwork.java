package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvHoverRoutePlatformEntity;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

/** Client input reaches the same server-owned weapon actions as commands. */
public final class DnvHoverPlatformGunNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL =
            net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
                    ResourceLocation.fromNamespaceAndPath(
                            DoggiesNewVehicle.MOD_ID, "hover_platform_gun"),
                    () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean initialized;

    private DnvHoverPlatformGunNetwork() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        CHANNEL.registerMessage(0, ActionRequest.class,
                DnvHoverPlatformGunNetwork::encode,
                DnvHoverPlatformGunNetwork::decode,
                DnvHoverPlatformGunNetwork::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        initialized = true;
    }

    @OnlyIn(Dist.CLIENT)
    public static void toggle(UUID platformId) {
        CHANNEL.sendToServer(new ActionRequest(Action.TOGGLE, platformId, Vec3.ZERO));
    }

    @OnlyIn(Dist.CLIENT)
    public static void fire(UUID platformId, Vec3 target) {
        CHANNEL.sendToServer(new ActionRequest(Action.FIRE, platformId, target));
    }

    private static void encode(ActionRequest message, FriendlyByteBuf buffer) {
        buffer.writeEnum(message.action());
        buffer.writeUUID(message.platformId());
        buffer.writeDouble(message.target().x);
        buffer.writeDouble(message.target().y);
        buffer.writeDouble(message.target().z);
    }

    private static ActionRequest decode(FriendlyByteBuf buffer) {
        return new ActionRequest(buffer.readEnum(Action.class), buffer.readUUID(),
                new Vec3(buffer.readDouble(), buffer.readDouble(),
                        buffer.readDouble()));
    }

    private static void handle(ActionRequest message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> dispatch(player, message));
        }
        context.setPacketHandled(true);
    }

    private static void dispatch(ServerPlayer player, ActionRequest message) {
        if (!(player.serverLevel().getEntity(message.platformId())
                instanceof DnvHoverRoutePlatformEntity platform)) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.unavailable"));
            return;
        }
        if (message.action() == Action.TOGGLE) {
            boolean release = DnvHoverPlatformWeapons.activeSession(player, platform);
            DnvHoverPlatformWeapons.SessionIssue issue = release
                    ? DnvHoverPlatformWeapons.release(player)
                    : DnvHoverPlatformWeapons.claim(player, platform);
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.gun."
                            + (release ? "release." : "claim.") + issue.name()));
            return;
        }
        DnvHoverPlatformWeapons.ShotIssue issue =
                DnvHoverPlatformWeapons.fire(player, platform, message.target());
        player.sendSystemMessage(Component.translatable(
                "message.doggiesnewvehicle.hover_route_platform.gun.fire."
                        + issue.name()));
    }

    private enum Action {
        TOGGLE, FIRE
    }

    private record ActionRequest(Action action, UUID platformId, Vec3 target) {
        private ActionRequest {
            if (action == null || platformId == null || target == null
                    || !Double.isFinite(target.x) || !Double.isFinite(target.y)
                    || !Double.isFinite(target.z)) {
                throw new IllegalArgumentException("invalid platform gun request");
            }
        }
    }
}
