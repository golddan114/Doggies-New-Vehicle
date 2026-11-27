package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.route.DnvRouteWaypoint;
import com.liucodex.doggiesnewvehicle.entity.DnvHoverRoutePlatformEntity;
import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.platform.DnvRouteDraftServices;
import com.liucodex.doggiesnewvehicle.mission.DnvAirRouteValidation;
import com.liucodex.doggiesnewvehicle.mission.DnvRouteFollower;
import com.liucodex.doggiesnewvehicle.mission.DnvRoutePlan;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteDrafts;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-thread bridge from an owned DNV platform to the existing route drafts. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID)
public final class DnvHoverRoutePlatformServices {
    private static final double MAX_ROUTE_ENTRY_DISTANCE = 24.0D;

    private DnvHoverRoutePlatformServices() {
    }

    public static Optional<DnvTacticalRouteDrafts.BindResult> bind(
            ServerPlayer player, DnvHoverRoutePlatformEntity platform) {
        if (!platform.mayBind(player.getUUID())
                || platform.level() != player.level()
                || !platform.isAlive() || platform.isRemoved()
                || player.distanceToSqr(platform) > 64.0D * 64.0D) {
            return Optional.empty();
        }
        DnvTacticalRouteDrafts.BindResult result =
                DnvRouteDraftServices.routes(player.server)
                        .bindOwnedPlatform(
                                player.getUUID(),
                                DnvRouteDraftServices.dimension(player),
                                platform.getUUID(),
                                player.server.getTickCount());
        if (result.accepted()) {
            platform.bindOwner(player.getUUID());
        }
        return Optional.of(result);
    }

    public static void platformRemoved(
            MinecraftServer server, UUID ownerId, UUID platformId) {
        DnvRouteDraftServices.routes(server)
                .invalidateCore(ownerId, platformId);
    }

    @SubscribeEvent
    public static void platformLeft(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof DnvHoverRoutePlatformEntity platform)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity.RemovalReason reason = platform.getRemovalReason();
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            return;
        }
        platform.ownerId().ifPresent(ownerId -> platformRemoved(
                level.getServer(), ownerId, platform.getUUID()));
    }

    public static DnvAirRouteValidation.Result validation(
            ServerPlayer player,
            DnvTacticalRouteDrafts.DraftProjection projection) {
        ServerLevel level = player.serverLevel();
        var bounds = DnvRouteDraftServices.bounds(level);
        Entity entity = level.getEntity(projection.binding().vehicleId());
        if (!(entity instanceof DnvHoverRoutePlatformEntity platform)
                || !projection.binding().coreId().equals(platform.getUUID())
                || !projection.binding().dimension().equals(
                        DnvRouteDraftServices.dimension(player))
                || platform.ownerId().filter(player.getUUID()::equals).isEmpty()
                || !platform.isAlive() || platform.isRemoved()) {
            return DnvAirRouteValidation.configurationUnavailable(
                    projection.snapshot().waypoints(), bounds);
        }
        return DnvAirRouteWorldValidation.validate(
                level, projection.snapshot().waypoints(), bounds,
                envelope(level));
    }

    public static StartResult start(
            ServerPlayer player,
            DnvHoverRoutePlatformEntity platform,
            boolean loop) {
        if (platform.ownerId().filter(player.getUUID()::equals).isEmpty()
                || platform.level() != player.level()
                || !platform.isAlive() || platform.isRemoved()
                || player.distanceToSqr(platform) > 64.0D * 64.0D) {
            return new StartResult(false, StartIssue.NOT_AUTHORIZED);
        }
        Optional<DnvTacticalRouteDrafts.DraftProjection> selected =
                DnvRouteDraftServices.routes(player.server).active(
                        player.getUUID(),
                        DnvRouteDraftServices.dimension(player),
                        player.server.getTickCount());
        if (selected.isEmpty()
                || !selected.orElseThrow().binding().vehicleId()
                        .equals(platform.getUUID())) {
            return new StartResult(false, StartIssue.NO_BOUND_DRAFT);
        }
        DnvTacticalRouteDrafts.DraftProjection projection = selected.orElseThrow();
        List<DnvRouteWaypoint> waypoints =
                projection.snapshot().waypoints();
        if (waypoints.isEmpty()
                || Math.hypot(platform.getX() - waypoints.get(0).x(),
                        platform.getZ() - waypoints.get(0).z())
                        > MAX_ROUTE_ENTRY_DISTANCE) {
            return new StartResult(false, StartIssue.START_OUTSIDE_ROUTE);
        }
        DnvAirRouteValidation.Result validation = validation(player, projection);
        if (validation.status() != DnvAirRouteValidation.Status.EXECUTABLE) {
            return new StartResult(false, StartIssue.ROUTE_NOT_EXECUTABLE);
        }
        if (loop && !loopClosureExecutable(player.serverLevel(), waypoints)) {
            return new StartResult(false, StartIssue.ROUTE_NOT_EXECUTABLE);
        }
        Optional<DnvRoutePlan> plan = plan(player.serverLevel(), projection, loop);
        if (plan.isEmpty()) {
            return new StartResult(false, StartIssue.ROUTE_NOT_EXECUTABLE);
        }
        DnvRouteFollower.RouteUpdate update = DnvHoverRoutePlatformRuntime.start(
                platform, plan.orElseThrow());
        return update.accepted()
                ? new StartResult(true, StartIssue.NONE)
                : new StartResult(false, StartIssue.REVISION_REJECTED);
    }

    public static boolean stop(
            ServerPlayer player, DnvHoverRoutePlatformEntity platform) {
        if (platform.ownerId().filter(player.getUUID()::equals).isEmpty()
                || platform.level() != player.level()
                || !platform.isAlive() || platform.isRemoved()) {
            return false;
        }
        DnvHoverRoutePlatformRuntime.stop(platform);
        return true;
    }

    public static boolean land(
            ServerPlayer player, DnvHoverRoutePlatformEntity platform) {
        if (platform.ownerId().filter(player.getUUID()::equals).isEmpty()
                || platform.level() != player.level()
                || !platform.isAlive() || platform.isRemoved()) {
            return false;
        }
        return DnvHoverRoutePlatformRuntime.land(platform);
    }

    private static Optional<DnvRoutePlan> plan(
            ServerLevel level,
            DnvTacticalRouteDrafts.DraftProjection projection,
            boolean loop) {
        DnvAirRouteValidation.FlightEnvelope envelope = envelope(level);
        List<DnvRouteWaypoint> waypoints =
                projection.snapshot().waypoints();
        List<DnvRoutePlan.RoutePoint> points = new java.util.ArrayList<>();
        for (int index = 0; index < waypoints.size(); index++) {
            var waypoint = waypoints.get(index);
            int x = (int) Math.floor(waypoint.x());
            int z = (int) Math.floor(waypoint.z());
            if (!level.hasChunk(x >> 4, z >> 4)) {
                return Optional.empty();
            }
            double terrain = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            double height = Math.max(waypoint.y(), Math.max(
                    envelope.preferredAltitude(),
                    terrain + envelope.minimumTerrainClearance()));
            if (height > envelope.maximumAltitude()) {
                return Optional.empty();
            }
            points.add(new DnvRoutePlan.RoutePoint(
                    waypoint.x(), waypoint.z(), OptionalDouble.of(height),
                    !loop && index == waypoints.size() - 1
                            ? DnvRoutePlan.WaypointAction.HOLD
                            : DnvRoutePlan.WaypointAction.PASS_THROUGH));
        }
        return Optional.of(new DnvRoutePlan(
                DnvRoutePlan.CURRENT_SCHEMA,
                projection.snapshot().dimension(),
                projection.snapshot().revision(),
                DnvRoutePlan.MovementMedium.AIR,
                points,
                loop ? DnvRoutePlan.CompletionPolicy.LOOP
                        : DnvRoutePlan.CompletionPolicy.HOLD,
                DnvRoutePlan.BlockedPolicy.RECOVERY_REQUIRED,
                new DnvRoutePlan.AltitudePolicy(
                        DnvRoutePlan.AltitudeReference.ABSOLUTE,
                        envelope.preferredAltitude(),
                        envelope.minimumTerrainClearance(),
                        envelope.maximumAltitude())));
    }

    private static boolean loopClosureExecutable(
            ServerLevel level, List<DnvRouteWaypoint> waypoints) {
        if (waypoints.size() < 2) {
            return false;
        }
        List<DnvRouteWaypoint> closure =
                List.of(waypoints.get(waypoints.size() - 1), waypoints.get(0));
        return DnvAirRouteWorldValidation.validate(
                level, closure, DnvRouteDraftServices.bounds(level),
                envelope(level)).status() == DnvAirRouteValidation.Status.EXECUTABLE;
    }

    private static DnvAirRouteValidation.FlightEnvelope envelope(ServerLevel level) {
        double minimum = level.getMinBuildHeight() + 4.0D;
        double maximum = level.getMaxBuildHeight() - 4.0D;
        double preferred = Math.min(maximum, Math.max(minimum, 96.0D));
        return new DnvAirRouteValidation.FlightEnvelope(
                minimum, preferred, maximum, 8.0D, 2.0D);
    }

    public record StartResult(boolean accepted, StartIssue issue) {
        public StartResult {
            if (accepted != (issue == StartIssue.NONE)) {
                throw new IllegalArgumentException("Inconsistent hover route start");
            }
        }
    }

    public enum StartIssue {
        NONE,
        NOT_AUTHORIZED,
        NO_BOUND_DRAFT,
        START_OUTSIDE_ROUTE,
        ROUTE_NOT_EXECUTABLE,
        REVISION_REJECTED
    }
}
