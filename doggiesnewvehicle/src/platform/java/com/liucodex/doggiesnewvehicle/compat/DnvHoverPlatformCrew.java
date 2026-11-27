package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvHoverRoutePlatformEntity;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned single passenger and safe ground exit for the route platform. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID)
public final class DnvHoverPlatformCrew {
    private static final String RECOVERY_KEY = "DnvHoverCrewRecovery";
    private static final double MAX_ENTRY_DISTANCE_SQR = 8.0D * 8.0D;
    private static final double[][] EXIT_OFFSETS = {
            {3.0D, 0.0D}, {-3.0D, 0.0D}, {0.0D, 3.0D}, {0.0D, -3.0D},
            {3.0D, 3.0D}, {-3.0D, 3.0D}, {3.0D, -3.0D}, {-3.0D, -3.0D}
    };
    private static final Set<ServerPlayer> FORCED_RELEASE =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private DnvHoverPlatformCrew() {
    }

    public static BoardIssue board(ServerPlayer player, DnvHoverRoutePlatformEntity platform) {
        if (platform.ownerId().filter(player.getUUID()::equals).isEmpty()
                || platform.level() != player.level() || !platform.isAlive()
                || platform.isRemoved()
                || recovering(player)
                || player.distanceToSqr(platform) > MAX_ENTRY_DISTANCE_SQR) {
            return BoardIssue.NOT_AUTHORIZED;
        }
        if (player.isPassenger()) {
            return BoardIssue.ALREADY_RIDING;
        }
        if (!platform.getPassengers().isEmpty()) {
            return BoardIssue.OCCUPIED;
        }
        Optional<Vec3> ground = safeExit(platform, player);
        if (DnvHoverRoutePlatformRuntime.active(platform) || ground.isEmpty()) {
            return BoardIssue.NOT_LANDED;
        }
        if (!player.startRiding(platform, false)) {
            return BoardIssue.FAILED;
        }
        platform.setBoardAnchor(ground.orElseThrow());
        rememberRide(player, platform);
        return BoardIssue.NONE;
    }

    public static boolean exit(ServerPlayer player) {
        if (!(player.getVehicle() instanceof DnvHoverRoutePlatformEntity platform)
                || safeExit(platform, player).isEmpty()) {
            return false;
        }
        player.stopRiding();
        if (player.getVehicle() == platform) {
            return false;
        }
        finishRecovery(player);
        platform.clearBoardAnchor();
        return true;
    }

    public static Optional<Vec3> safeExit(
            DnvHoverRoutePlatformEntity platform, LivingEntity passenger) {
        if (!(platform.level() instanceof ServerLevel level)
                || !platform.isAlive() || platform.isRemoved()) {
            return Optional.empty();
        }
        for (double[] offset : EXIT_OFFSETS) {
            Optional<Vec3> candidate = groundPoint(level, passenger,
                    platform.getX() + offset[0], platform.getZ() + offset[1],
                    platform.getY(), DnvHoverCrewExitRules.MAX_GROUND_OFFSET);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void mount(EntityMountEvent event) {
        if (event.getEntityMounting() instanceof ServerPlayer rider) {
            if (event.isDismounting() && FORCED_RELEASE.contains(rider)) {
                // Emergency release must also override another ordinary exit veto.
                event.setCanceled(false);
                return;
            }
            if (!event.isDismounting() && recovering(rider)) {
                event.setCanceled(true);
                return;
            }
        }
        if (!event.isDismounting()
                || !(event.getEntityBeingMounted() instanceof DnvHoverRoutePlatformEntity platform)
                || !(event.getEntityMounting() instanceof ServerPlayer player)
                || FORCED_RELEASE.contains(player)) {
            return;
        }
        if (player.isAlive() && safeExit(platform, player).isEmpty()) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.exit_unsafe"));
            return;
        }
        // A later veto may still retain the rider; clear only after actual detach.
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getVehicle() instanceof DnvHoverRoutePlatformEntity platform) {
            rescue(player, platform);
        }
    }

    public static RescueResult releaseOnPlatformRemoval(DnvHoverRoutePlatformEntity platform) {
        RescueResult result = RescueResult.RELEASED;
        for (Entity passenger : List.copyOf(platform.getPassengers())) {
            if (passenger instanceof ServerPlayer player) {
                if (rescue(player, platform) == RescueResult.RECOVERY_PENDING) {
                    result = RescueResult.RECOVERY_PENDING;
                }
            }
        }
        return result;
    }

    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof DnvHoverRoutePlatformEntity platform
                && event.getLevel() instanceof ServerLevel) {
            // Chunk unloading may call final Entity.setRemoved directly.
            releaseOnPlatformRemoval(platform);
            DnvHoverPlatformWeapons.dropPlatform(platform);
        }
    }

    /** Transfer safety to the player ticket before the platform can disappear. */
    private static RescueResult rescue(
            ServerPlayer player, DnvHoverRoutePlatformEntity platform) {
        rememberRide(player, platform);
        if (!player.isAlive()) {
            detach(player);
            finishRecovery(player);
            platform.clearBoardAnchor();
            return RescueResult.RELEASED;
        }
        RescueResult result = recover(player);
        if (player.getVehicle() != platform) {
            platform.clearBoardAnchor();
        }
        return result;
    }

    private static void rememberRide(ServerPlayer player, DnvHoverRoutePlatformEntity platform) {
        CompoundTag ticket = new CompoundTag();
        ticket.putUUID("Platform", platform.getUUID());
        ticket.putString("Dimension", platform.level().dimension().location().toString());
        ticket.putString("HoldDimension", player.level().dimension().location().toString());
        platform.boardAnchor().filter(DnvHoverPlatformCrew::finite)
                .ifPresent(anchor -> writePoint(ticket, "Anchor", anchor));
        writePoint(ticket, "Hold", player.position());
        player.getPersistentData().put(RECOVERY_KEY, ticket);
    }

    private static boolean recovering(ServerPlayer player) {
        return player.getPersistentData().getCompound(RECOVERY_KEY).getBoolean("Pending");
    }

    private static void finishRecovery(ServerPlayer player) {
        player.getPersistentData().remove(RECOVERY_KEY);
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        DnvHoverPlatformWeapons.release(player);
    }

    private static boolean detach(ServerPlayer player) {
        FORCED_RELEASE.add(player);
        try {
            player.stopRiding();
            return !player.isPassenger();
        } finally {
            FORCED_RELEASE.remove(player);
        }
    }

    private static RescueResult recover(ServerPlayer player) {
        CompoundTag ticket = player.getPersistentData().getCompound(RECOVERY_KEY);
        boolean alreadyPending = ticket.getBoolean("Pending");
        ticket.putBoolean("Pending", true);
        DnvHoverPlatformWeapons.release(player);
        Optional<Vec3> destination = Optional.empty();
        ServerLevel level = player.serverLevel();
        if (ticket.getString("Dimension").equals(level.dimension().location().toString())) {
            Optional<Vec3> anchor = readPoint(ticket, "Anchor");
            if (anchor.isPresent() && level.getWorldBorder().isWithinBounds(
                    BlockPos.containing(anchor.orElseThrow()))) {
                // One bounded emergency chunk request; no persistent chunk ticket.
                level.getChunkAt(BlockPos.containing(anchor.orElseThrow()));
                destination = nearbyGround(level, player, anchor.orElseThrow(), 4.0D);
            }
        }
        if (destination.isEmpty()) {
            destination = nearbyGround(level, player, player.position(), Double.MAX_VALUE);
        }
        if (destination.isEmpty()) {
            ServerLevel spawnLevel = level.getServer().overworld();
            BlockPos spawn = spawnLevel.getSharedSpawnPos();
            if (spawnLevel.getWorldBorder().isWithinBounds(spawn)) {
                spawnLevel.getChunkAt(spawn);
                destination = nearbyGround(spawnLevel, player,
                        Vec3.atBottomCenterOf(spawn), Double.MAX_VALUE);
                if (destination.isPresent()) {
                    level = spawnLevel;
                }
            }
        }
        if (detach(player) && destination.isPresent()) {
            Vec3 point = destination.orElseThrow();
            player.teleportTo(level, point.x, point.y, point.z,
                    player.getYRot(), player.getXRot());
            if (player.serverLevel() == level && player.position().distanceToSqr(point) < 0.01D) {
                finishRecovery(player);
                return RescueResult.RELEASED;
            }
        }
        hold(player, ticket);
        if (!alreadyPending) {
            player.sendSystemMessage(Component.translatable(
                    "message.doggiesnewvehicle.hover_route_platform.recovery_pending"));
        }
        return RescueResult.RECOVERY_PENDING;
    }

    private static void hold(ServerPlayer player, CompoundTag ticket) {
        ServerLevel level = player.serverLevel();
        String dimension = level.dimension().location().toString();
        Vec3 point = ticket.getString("HoldDimension").equals(dimension)
                ? readPoint(ticket, "Hold").orElse(player.position()) : player.position();
        if (!finite(point) || !level.getWorldBorder().isWithinBounds(BlockPos.containing(point))
                || point.y < level.getMinBuildHeight() + 2
                || point.y > level.getMaxBuildHeight() - player.getBbHeight()) {
            point = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
            point = new Vec3(point.x, Math.max(level.getMinBuildHeight() + 2,
                    Math.min(level.getMaxBuildHeight() - player.getBbHeight(), point.y)), point.z);
            if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(point))) {
                point = new Vec3(level.getWorldBorder().getCenterX(), point.y,
                        level.getWorldBorder().getCenterZ());
            }
        }
        writePoint(ticket, "Hold", point);
        ticket.putString("HoldDimension", dimension);
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(level, point.x, point.y, point.z, player.getYRot(), player.getXRot());
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getVehicle() instanceof DnvHoverRoutePlatformEntity platform) {
                rescue(player, platform);
            } else if (player.getPersistentData().contains(RECOVERY_KEY, Tag.TAG_COMPOUND)) {
                recover(player);
            }
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (ServerPlayer player : List.copyOf(event.getServer().getPlayerList().getPlayers())) {
            if (player.getVehicle() instanceof DnvHoverRoutePlatformEntity platform) {
                rescue(player, platform);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        FORCED_RELEASE.removeIf(player -> player.getServer() == event.getServer());
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !player.isAlive()) {
            return;
        }
        if (!player.getPersistentData().contains(RECOVERY_KEY, Tag.TAG_COMPOUND)) {
            if (player.getVehicle() instanceof DnvHoverRoutePlatformEntity platform) {
                rememberRide(player, platform);
            } else {
                return;
            }
        }
        CompoundTag ticket = player.getPersistentData().getCompound(RECOVERY_KEY);
        if (!ticket.getBoolean("Pending") && player.isPassenger()
                && !(player.getVehicle() instanceof DnvHoverRoutePlatformEntity)) {
            // A successful ordinary exit followed by another vehicle's boarding
            // transfers ownership; do not rescue or alter the other vehicle's rider.
            player.getPersistentData().remove(RECOVERY_KEY);
            return;
        }
        if (!ticket.getBoolean("Pending")
                && player.getVehicle() instanceof DnvHoverRoutePlatformEntity platform
                && !platform.isRemoved() && platform.isAlive()
                && player.serverLevel().getEntity(platform.getUUID()) == platform
                && ticket.hasUUID("Platform") && ticket.getUUID("Platform").equals(platform.getUUID())) {
            return;
        }
        if (!player.isPassenger()
                && groundPoint(player.serverLevel(), player, player.getX(), player.getZ(),
                        player.getY(), DnvHoverCrewExitRules.MAX_GROUND_OFFSET).isPresent()) {
            finishRecovery(player);
            return;
        }
        if (!ticket.getBoolean("Pending") || player.tickCount % 20 == 0) {
            recover(player);
        } else {
            hold(player, ticket);
        }
    }

    @SubscribeEvent
    public static void attack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && recovering(player)
                && !event.getSource().is(DamageTypes.GENERIC_KILL)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && (player.getVehicle() instanceof DnvHoverRoutePlatformEntity
                        || player.getPersistentData().contains(RECOVERY_KEY, Tag.TAG_COMPOUND))) {
            if (player.getVehicle() instanceof DnvHoverRoutePlatformEntity platform) {
                detach(player);
                platform.clearBoardAnchor();
            }
            finishRecovery(player);
        }
    }

    private static void writePoint(CompoundTag ticket, String key, Vec3 point) {
        CompoundTag value = new CompoundTag();
        value.putDouble("X", point.x);
        value.putDouble("Y", point.y);
        value.putDouble("Z", point.z);
        ticket.put(key, value);
    }

    private static Optional<Vec3> readPoint(CompoundTag ticket, String key) {
        CompoundTag value = ticket.getCompound(key);
        if (!value.contains("X", Tag.TAG_ANY_NUMERIC) || !value.contains("Y", Tag.TAG_ANY_NUMERIC)
                || !value.contains("Z", Tag.TAG_ANY_NUMERIC)) {
            return Optional.empty();
        }
        Vec3 point = new Vec3(value.getDouble("X"), value.getDouble("Y"), value.getDouble("Z"));
        return finite(point) ? Optional.of(point) : Optional.empty();
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static Optional<Vec3> nearbyGround(
            ServerLevel level, LivingEntity passenger, Vec3 origin, double maxOffset) {
        if (!finite(origin)) {
            return Optional.empty();
        }
        Optional<Vec3> point = groundPoint(level, passenger,
                origin.x, origin.z, origin.y, maxOffset);
        for (double[] offset : EXIT_OFFSETS) {
            if (point.isPresent()) {
                break;
            }
            point = groundPoint(level, passenger, origin.x + offset[0], origin.z + offset[1],
                    origin.y, maxOffset);
        }
        return point;
    }

    private static Optional<Vec3> groundPoint(
            ServerLevel level, LivingEntity passenger, double x, double z,
            double referenceY, double maxOffset) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            return Optional.empty();
        }
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        if (!level.hasChunk(blockX >> 4, blockZ >> 4)) {
            return Optional.empty();
        }
        int surface = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        if (!DnvHoverCrewExitRules.groundClose(referenceY, surface, maxOffset)
                || surface <= level.getMinBuildHeight() + 1
                || !level.getBlockState(new BlockPos(blockX, surface - 1, blockZ)).isSolid()) {
            return Optional.empty();
        }
        Vec3 point = new Vec3(blockX + 0.5D, surface, blockZ + 0.5D);
        AABB bounds = passenger.getBoundingBox().move(point.subtract(passenger.position()));
        return level.getWorldBorder().isWithinBounds(bounds)
                && bounds.minY >= level.getMinBuildHeight()
                && bounds.maxY <= level.getMaxBuildHeight()
                && level.hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                        BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))
                && level.noCollision(passenger, bounds)
                && level.getBlockStates(bounds.expandTowards(0.0D, -0.1D, 0.0D))
                        .noneMatch(state -> !state.getFluidState().isEmpty()
                                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                                || state.is(Blocks.SWEET_BERRY_BUSH)
                                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                                || state.is(Blocks.WITHER_ROSE))
                ? Optional.of(point) : Optional.empty();
    }

    public enum BoardIssue {
        NONE, NOT_AUTHORIZED, ALREADY_RIDING, OCCUPIED, NOT_LANDED, FAILED
    }

    public enum RescueResult {
        RELEASED, RECOVERY_PENDING
    }
}
