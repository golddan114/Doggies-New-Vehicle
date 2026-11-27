package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvHoverRoutePlatformEntity;
import com.liucodex.doggiesnewvehicle.mission.DnvRouteFollower;
import com.liucodex.doggiesnewvehicle.mission.DnvRoutePlan;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Nonpersistent route state and the sole movement writer for the owned platform. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID)
public final class DnvHoverRoutePlatformRuntime {
    private static final double MAX_HORIZONTAL_STEP = 0.28D;
    private static final double MAX_VERTICAL_STEP = 0.16D;
    private static final double MIN_VERTICAL_CLIMB_STEP = 0.04D;
    private static final double HEIGHT_TOLERANCE = 1.0D;
    private static final int MAX_BLOCKED_MOTION_TICKS = 40;
    private static final Map<MinecraftServer, Map<UUID, ActiveRoute>> ACTIVE =
            new IdentityHashMap<>();

    private DnvHoverRoutePlatformRuntime() {
    }

    public static DnvRouteFollower.RouteUpdate start(
            DnvHoverRoutePlatformEntity platform, DnvRoutePlan plan) {
        ServerLevel level = (ServerLevel) platform.level();
        stop(platform);
        ActiveRoute active = new ActiveRoute();
        DnvRouteFollower.RouteUpdate update = active.follower.apply(
                plan, new DnvRouteFollower.RouteCapability(
                        true, Set.of(DnvRoutePlan.MovementMedium.AIR)));
        if (update.accepted()) {
            ACTIVE.computeIfAbsent(level.getServer(), ignored -> new HashMap<>())
                    .put(platform.getUUID(), active);
        }
        return update;
    }

    public static void stop(DnvHoverRoutePlatformEntity platform) {
        if (platform.level() instanceof ServerLevel level) {
            Map<UUID, ActiveRoute> routes = ACTIVE.get(level.getServer());
            if (routes != null) {
                routes.remove(platform.getUUID());
                if (routes.isEmpty()) {
                    ACTIVE.remove(level.getServer());
                }
            }
        }
        platform.setDeltaMovement(Vec3.ZERO);
    }

    /** Replace a route with a bounded descent over loaded, solid ground. */
    public static boolean land(DnvHoverRoutePlatformEntity platform) {
        if (!(platform.level() instanceof ServerLevel level)
                || !platform.isAlive() || platform.isRemoved()) {
            return false;
        }
        if (landingTarget(level, platform).isEmpty()) {
            return false;
        }
        stop(platform);
        ActiveRoute landing = new ActiveRoute();
        landing.landing = true;
        ACTIVE.computeIfAbsent(level.getServer(), ignored -> new HashMap<>())
                .put(platform.getUUID(), landing);
        return true;
    }

    /** Server-only status for route commands and lifecycle diagnostics. */
    public static boolean active(DnvHoverRoutePlatformEntity platform) {
        if (!(platform.level() instanceof ServerLevel level)) {
            return false;
        }
        Map<UUID, ActiveRoute> routes = ACTIVE.get(level.getServer());
        return routes != null && routes.containsKey(platform.getUUID());
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof DnvHoverRoutePlatformEntity platform)
                || !(platform.level() instanceof ServerLevel level)) {
            return;
        }
        Map<UUID, ActiveRoute> routes = ACTIVE.get(level.getServer());
        ActiveRoute active = routes == null ? null : routes.get(platform.getUUID());
        if (active == null) {
            platform.setDeltaMovement(Vec3.ZERO);
            return;
        }
        tickRoute(level, platform, active);
    }

    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof DnvHoverRoutePlatformEntity platform
                && event.getLevel() instanceof ServerLevel) {
            stop(platform);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ACTIVE.remove(event.getServer());
    }

    private static void tickRoute(
            ServerLevel level, DnvHoverRoutePlatformEntity platform,
            ActiveRoute active) {
        ServerPlayer owner = platform.ownerId()
                .map(level.getServer().getPlayerList()::getPlayer).orElse(null);
        if (owner == null || owner.level() != level) {
            stop(platform);
            return;
        }
        int terrainX = (int) Math.floor(platform.getX());
        int terrainZ = (int) Math.floor(platform.getZ());
        if (!level.hasChunk(terrainX >> 4, terrainZ >> 4)) {
            platform.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (active.landing) {
            tickLanding(level, platform, active);
            return;
        }
        double heading = Math.toRadians(platform.getYRot());
        Vec3 probe = new Vec3(-Math.sin(heading), 0.0D, Math.cos(heading));
        AABB forward = platform.getBoundingBox().move(probe);
        boolean forwardClear = safeEnvelope(level, forward)
                && level.noCollision(platform, forward);
        AABB above = platform.getBoundingBox().move(0.0D, MAX_VERTICAL_STEP, 0.0D);
        boolean climbClear = safeEnvelope(level, above)
                && level.noCollision(platform, above);
        boolean localClear = forwardClear || climbClear;
        double terrainHeight = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, terrainX, terrainZ);
        DnvRouteFollower.Intent intent = active.follower.tick(
                new DnvRouteFollower.SensorSnapshot(
                        level.getGameTime(), platform.getX(), platform.getY(),
                        platform.getZ(), platform.getYRot(),
                        platform.getDeltaMovement().length(),
                        safeEnvelope(level, platform.getBoundingBox().inflate(1.0D)),
                        localClear, false, false,
                        OptionalDouble.of(terrainHeight),
                        localClear ? 1.0D : 0.0D, false));
        if (intent.state() == DnvRouteFollower.State.RECOVERY_REQUIRED
                || intent.state() == DnvRouteFollower.State.COMPLETE) {
            stop(platform);
            return;
        }
        if (intent.hardHold() || intent.targetPoint().isEmpty()) {
            platform.setDeltaMovement(Vec3.ZERO);
            return;
        }
        float yaw = platform.getYRot() + (float) (intent.steering() * 4.0D);
        double radians = Math.toRadians(yaw);
        double requiredHeight = Math.max(
                intent.targetPoint().orElseThrow()
                        .serverResolvedHeight().orElse(platform.getY()),
                terrainHeight + 8.0D);
        boolean climbFirst = intent.climbCommand() > 0.0D
                && platform.getY() < requiredHeight - HEIGHT_TOLERANCE;
        double speed = climbFirst ? 0.0D
                : MAX_HORIZONTAL_STEP * intent.throttle();
        double climb = intent.climbCommand() * MAX_VERTICAL_STEP;
        if (climbFirst) {
            climb = Math.max(climb, Math.min(
                    MIN_VERTICAL_CLIMB_STEP, requiredHeight - platform.getY()));
        }
        Vec3 motion = new Vec3(-Math.sin(radians) * speed,
                climb, Math.cos(radians) * speed);
        AABB destination = platform.getBoundingBox().move(motion);
        if (!safeEnvelope(level, destination)
                || !level.noCollision(platform, destination)) {
            platform.setDeltaMovement(Vec3.ZERO);
            if (++active.blockedMotionTicks >= MAX_BLOCKED_MOTION_TICKS) {
                stop(platform);
            }
            return;
        }
        active.blockedMotionTicks = 0;
        platform.setYRot(yaw);
        platform.move(MoverType.SELF, motion);
        platform.setDeltaMovement(motion);
    }

    private static void tickLanding(
            ServerLevel level, DnvHoverRoutePlatformEntity platform,
            ActiveRoute active) {
        OptionalDouble target = landingTarget(level, platform);
        if (target.isEmpty()) {
            stop(platform);
            return;
        }
        double remaining = platform.getY() - target.getAsDouble();
        if (remaining <= 0.1D) {
            stop(platform);
            return;
        }
        Vec3 motion = new Vec3(0.0D,
                -Math.min(MAX_VERTICAL_STEP, remaining), 0.0D);
        AABB destination = platform.getBoundingBox().move(motion);
        if (!safeEnvelope(level, destination)
                || !level.noCollision(platform, destination)) {
            platform.setDeltaMovement(Vec3.ZERO);
            if (++active.blockedMotionTicks >= MAX_BLOCKED_MOTION_TICKS) {
                stop(platform);
            }
            return;
        }
        active.blockedMotionTicks = 0;
        platform.move(MoverType.SELF, motion);
        platform.setDeltaMovement(motion);
    }

    private static OptionalDouble landingTarget(
            ServerLevel level, DnvHoverRoutePlatformEntity platform) {
        AABB bounds = platform.getBoundingBox();
        if (!safeEnvelope(level, bounds)) {
            return OptionalDouble.empty();
        }
        int minX = (int) Math.floor(bounds.minX);
        int maxX = (int) Math.floor(bounds.maxX - 1.0E-6D);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxZ = (int) Math.floor(bounds.maxZ - 1.0E-6D);
        int highest = level.getMinBuildHeight();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int surface = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (surface <= level.getMinBuildHeight() + 1
                        || !level.getBlockState(new BlockPos(x, surface - 1, z))
                                .isSolid()) {
                    return OptionalDouble.empty();
                }
                highest = Math.max(highest, surface);
            }
        }
        double targetY = highest + 0.1D;
        if (targetY > platform.getY() + 0.1D) {
            return OptionalDouble.empty();
        }
        AABB destination = bounds.move(0.0D, targetY - platform.getY(), 0.0D);
        return safeEnvelope(level, destination)
                        && level.noCollision(platform, destination)
                ? OptionalDouble.of(targetY) : OptionalDouble.empty();
    }

    private static boolean safeEnvelope(ServerLevel level, AABB bounds) {
        if (bounds.minY < level.getMinBuildHeight()
                || bounds.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(bounds)) {
            return false;
        }
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

    private static final class ActiveRoute {
        private final DnvRouteFollower follower = new DnvRouteFollower();
        private boolean landing;
        private int blockedMotionTicks;
    }
}
