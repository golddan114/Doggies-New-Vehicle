package com.liucodex.doggiesnewvehicle.platform;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteDrafts;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Process-local owner of route drafts; no crew or world persistence state is retained. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID)
public final class DnvRouteDraftServices {
    private static final Map<MinecraftServer, DnvTacticalRouteDrafts> ROUTES =
            new IdentityHashMap<>();

    private DnvRouteDraftServices() {
    }

    public static DnvTacticalRouteDrafts routes(MinecraftServer server) {
        return ROUTES.computeIfAbsent(Objects.requireNonNull(server, "server"),
                ignored -> new DnvTacticalRouteDrafts());
    }

    public static DnvTacticalRouteDrafts.Bounds bounds(ServerLevel level) {
        var border = level.getWorldBorder();
        return new DnvTacticalRouteDrafts.Bounds(
                border.getMinX(), border.getMaxX(),
                level.getMinBuildHeight(), level.getMaxBuildHeight() - 1.0D,
                border.getMinZ(), border.getMaxZ());
    }

    public static String dimension(ServerPlayer player) {
        return player.serverLevel().dimension().location().toString();
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        DnvTacticalRouteDrafts routes = ROUTES.get(event.getServer());
        if (routes != null) {
            routes.expire(event.getServer().getTickCount());
        }
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        DnvTacticalRouteDrafts routes = ROUTES.remove(event.getServer());
        if (routes != null) {
            routes.clear();
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        invalidate(event.getEntity());
    }

    @SubscribeEvent
    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        invalidate(event.getEntity());
    }

    private static void invalidate(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        DnvTacticalRouteDrafts routes = ROUTES.get(serverPlayer.server);
        if (routes != null) {
            routes.invalidate(serverPlayer.getUUID());
        }
    }
}
