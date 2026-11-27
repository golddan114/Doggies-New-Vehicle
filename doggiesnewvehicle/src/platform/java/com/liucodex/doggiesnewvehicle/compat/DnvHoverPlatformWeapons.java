package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.entity.DnvHoverRoutePlatformEntity;
import com.liucodex.doggiesnewvehicle.platform.DnvHoverPlatformStations;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-only placeholder light guns with fixed AI/manual station ownership. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID)
public final class DnvHoverPlatformWeapons {
    private static final double MAX_RANGE = 48.0D;
    private static final int AI_INTERVAL = 10;
    private static final int AI_COOLDOWN = 16;
    private static final int PLAYER_COOLDOWN = 8;
    private static final float DAMAGE = 5.0F;
    private static final Map<ServerPlayer, DnvHoverRoutePlatformEntity> SESSIONS =
            new IdentityHashMap<>();
    private static final Map<DnvHoverRoutePlatformEntity, AiTargeting> AI_TARGETING =
            new WeakHashMap<>();

    private DnvHoverPlatformWeapons() {
    }

    public static SessionIssue claim(ServerPlayer player, DnvHoverRoutePlatformEntity platform) {
        if (!authorized(player, platform)) {
            return SessionIssue.NOT_AUTHORIZED;
        }
        DnvHoverRoutePlatformEntity previous = SESSIONS.get(player);
        var result = platform.stations().claimPlayer(player.getUUID(),
                platform.ownerId().orElseThrow(), platform.level().getGameTime(),
                platform.stations().revision());
        if (!result.accepted()) {
            return SessionIssue.OCCUPIED;
        }
        if (previous != null && previous != platform) {
            previous.stations().clearPlayer(player.getUUID());
        }
        SESSIONS.put(player, platform);
        return SessionIssue.NONE;
    }

    public static SessionIssue release(ServerPlayer player) {
        DnvHoverRoutePlatformEntity platform = SESSIONS.remove(player);
        if (platform == null) {
            return SessionIssue.NO_SESSION;
        }
        platform.stations().clearPlayer(player.getUUID());
        return SessionIssue.NONE;
    }

    static boolean activeSession(ServerPlayer player,
            DnvHoverRoutePlatformEntity platform) {
        return SESSIONS.get(player) == platform && authorized(player, platform)
                && platform.stations().playerMayFire(player.getUUID(),
                        platform.ownerId().orElseThrow(), platform.level().getGameTime());
    }

    public static ShotIssue fire(ServerPlayer player, DnvHoverRoutePlatformEntity platform,
            Vec3 targetPoint) {
        if (!authorized(player, platform)) {
            clearSession(player, platform);
            return ShotIssue.NOT_AUTHORIZED;
        }
        if (SESSIONS.get(player) != platform
                || !platform.stations().playerMayFire(player.getUUID(),
                        platform.ownerId().orElseThrow(), platform.level().getGameTime())) {
            clearSession(player, platform);
            return ShotIssue.NO_SESSION;
        }
        return shoot(player.serverLevel(), platform,
                DnvHoverPlatformStations.PLAYER_STATION, targetPoint, player);
    }

    public static void dropPlatform(DnvHoverRoutePlatformEntity platform) {
        SESSIONS.entrySet().removeIf(entry -> entry.getValue() == platform);
        AiTargeting targeting = AI_TARGETING.remove(platform);
        if (targeting != null) targeting.candidates.clear();
        if (platform.level() instanceof ServerLevel level) {
            DnvTargetingBudget.releaseGeometryOwner(level, platform.getUUID());
        }
        platform.stations().clearAll();
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof DnvHoverRoutePlatformEntity platform)
                || !(platform.level() instanceof ServerLevel level)
                || !platform.isAlive() || platform.isRemoved()) {
            return;
        }
        long now = level.getGameTime();
        ServerPlayer owner = platform.ownerId()
                .map(level.getServer().getPlayerList()::getPlayer).orElse(null);
        if (owner == null || owner.level() != level) {
            AiTargeting stopped = AI_TARGETING.remove(platform);
            if (stopped != null) stopped.candidates.clear();
            DnvTargetingBudget.releaseGeometryOwner(level, platform.getUUID());
            return;
        }
        AiTargeting targeting = AI_TARGETING.computeIfAbsent(platform,
                ignored -> new AiTargeting());
        targeting.candidates.refreshIfDue(platform, MAX_RANGE + 4.0D,
                candidate -> candidate instanceof LivingEntity living
                        && (living instanceof Monster || living instanceof Player)
                        && platform.distanceToSqr(living) <= MAX_RANGE * MAX_RANGE
                        && legalTarget(platform, living, owner));
        if (now % AI_INTERVAL != Math.floorMod(platform.getUUID().hashCode(), AI_INTERVAL)
                || !platform.stations().aiMayFire(now) || !platform.gunReady(
                        DnvHoverPlatformStations.AI_STATION, now)) {
            return;
        }
        LivingEntity target = nearestHostile(level, platform, owner, targeting);
        if (target != null && legalTarget(platform, target, owner)
                && loadedSegment(level, aiMuzzle(platform), target.getEyePosition())
                && level.clip(new ClipContext(aiMuzzle(platform), target.getEyePosition(),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, platform))
                        .getType() == HitResult.Type.MISS) {
            shoot(level, platform, DnvHoverPlatformStations.AI_STATION,
                    target.getEyePosition(), owner);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player);
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SESSIONS.values().forEach(platform -> platform.stations().clearAll());
        SESSIONS.clear();
        AI_TARGETING.forEach((platform, targeting) -> {
            targeting.candidates.clear();
            if (platform.level() instanceof ServerLevel level) {
                DnvTargetingBudget.releaseGeometryOwner(level, platform.getUUID());
            }
        });
        AI_TARGETING.clear();
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            pruneSessions();
        }
    }

    private static void pruneSessions() {
        var entries = SESSIONS.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (!activeSession(entry.getKey(), entry.getValue())) {
                entry.getValue().stations().clearPlayer(entry.getKey().getUUID());
                entries.remove();
            }
        }
    }

    private static void clearSession(ServerPlayer player,
            DnvHoverRoutePlatformEntity platform) {
        if (SESSIONS.remove(player, platform)) {
            platform.stations().clearPlayer(player.getUUID());
        }
    }

    private static boolean authorized(ServerPlayer player,
            DnvHoverRoutePlatformEntity platform) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.getServer() != null
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && platform.isAlive() && !platform.isRemoved()
                && platform.level() instanceof ServerLevel level && level.getEntity(platform.getUUID()) == platform
                && player.level() == platform.level()
                && platform.ownerId().filter(player.getUUID()::equals).isPresent()
                && player.distanceToSqr(platform) <= 128.0D * 128.0D;
    }

    private static LivingEntity nearestHostile(ServerLevel level,
            DnvHoverRoutePlatformEntity platform, ServerPlayer owner, AiTargeting targeting) {
        List<net.minecraft.world.entity.Entity> candidates =
                new ArrayList<>(targeting.candidates.candidates(platform));
        if (targeting.targetId != null) {
            net.minecraft.world.entity.Entity retained = level.getEntity(targeting.targetId);
            if (retained instanceof LivingEntity living
                    && (retained instanceof Monster || retained instanceof Player)
                    && platform.distanceToSqr(retained) <= MAX_RANGE * MAX_RANGE
                    && legalTarget(platform, living, owner)) {
                candidates.remove(retained);
                candidates.add(0, retained);
            } else targeting.targetId = null;
        }
        if (candidates.isEmpty()) return null;
        int start = targeting.targetId == null ? targeting.cursor : 0;
        for (int offset = 0; offset < candidates.size(); offset++) {
            int index = (start + offset) % candidates.size();
            if (!(candidates.get(index) instanceof LivingEntity candidate)
                    || !(candidate instanceof Monster || candidate instanceof Player)
                    || platform.distanceToSqr(candidate) > MAX_RANGE * MAX_RANGE
                    || !legalTarget(platform, candidate, owner)) continue;
            Vec3 muzzle = aiMuzzle(platform);
            AABB bounds = candidate.getBoundingBox();
            DnvSightObservation sight = targeting.sight;
            boolean clear;
            if (sight != null && sight.reusable(candidate.getUUID(), muzzle, bounds,
                    candidate.getEyeY(), 0L, platform.tickCount)) {
                clear = sight.clear();
            } else {
                if (!DnvTargetingBudget.admitGeometryCheck(level, platform.getUUID())) {
                    targeting.cursor = index;
                    return null;
                }
                Vec3 aim = candidate.getEyePosition();
                clear = loadedSegment(level, muzzle, aim)
                        && level.clip(new ClipContext(muzzle, aim,
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, platform))
                                .getType() == HitResult.Type.MISS;
                targeting.sight = new DnvSightObservation(candidate.getUUID(), muzzle,
                        bounds, candidate.getEyeY(), 0L, platform.tickCount,
                        DnvTargetingBudget.nextCheckTick(platform.tickCount, platform.getId(),
                                DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL, 0), clear);
            }
            if (!clear && candidate.getUUID().equals(targeting.targetId)) targeting.targetId = null;
            targeting.cursor = (index + 1) % candidates.size();
            if (clear) {
                targeting.targetId = candidate.getUUID();
                return candidate;
            }
        }
        targeting.targetId = null;
        return null;
    }

    private static Vec3 aiMuzzle(DnvHoverRoutePlatformEntity platform) {
        return platform.position().add(-1.2D, platform.getBbHeight() + 0.55D, 0.0D);
    }

    private static final class AiTargeting {
        private final DnvTargetCandidateCache candidates =
                new DnvTargetCandidateCache(DnvTargetingBudget.MIN_FULL_SCAN_INTERVAL);
        private DnvSightObservation sight;
        private int cursor;
        private UUID targetId;
    }

    private static ShotIssue shoot(ServerLevel level,
            DnvHoverRoutePlatformEntity platform, String station, Vec3 targetPoint,
            ServerPlayer owner) {
        Vec3 muzzle = platform.position().add(
                DnvHoverPlatformStations.AI_STATION.equals(station) ? -1.2D : 1.2D,
                platform.getBbHeight() + 0.55D, 0.0D);
        if (targetPoint == null || !Double.isFinite(targetPoint.x)
                || !Double.isFinite(targetPoint.y) || !Double.isFinite(targetPoint.z)
                || muzzle.distanceToSqr(targetPoint) < 4.0D
                || muzzle.distanceToSqr(targetPoint) > MAX_RANGE * MAX_RANGE) {
            return ShotIssue.OUT_OF_RANGE;
        }
        if (!loadedSegment(level, muzzle, targetPoint)) {
            return ShotIssue.UNLOADED;
        }
        Vec3 direction = targetPoint.subtract(muzzle).normalize();
        platform.aimGun(station,
                (float) Math.toDegrees(Math.atan2(-direction.x, direction.z)),
                (float) Math.toDegrees(Math.asin(-direction.y)));
        long now = level.getGameTime();
        if (!platform.gunReady(station, now)) {
            return ShotIssue.COOLDOWN;
        }
        HitResult block = level.clip(new ClipContext(muzzle, targetPoint,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, platform));
        Vec3 end = block.getType() == HitResult.Type.BLOCK
                ? block.getLocation() : targetPoint;
        LivingEntity first = firstEntity(level, platform, muzzle, end);
        if (first != null && !legalTarget(platform, first, owner)) {
            return ShotIssue.PROTECTED_TARGET;
        }
        if (first != null && !first.hurt(level.damageSources().mobAttack(platform), DAMAGE)) {
            return ShotIssue.BLOCKED;
        }
        emitShot(level, muzzle, first == null ? end : first.getEyePosition());
        platform.recordGunShot(station, now,
                DnvHoverPlatformStations.AI_STATION.equals(station)
                        ? AI_COOLDOWN : PLAYER_COOLDOWN);
        return ShotIssue.FIRED;
    }

    private static boolean legalTarget(DnvHoverRoutePlatformEntity platform,
            LivingEntity candidate, ServerPlayer owner) {
        return candidate.isAlive() && !candidate.isRemoved()
                && candidate != platform && candidate != owner
                && !candidate.isAlliedTo(platform) && !candidate.isAlliedTo(owner)
                && !platform.isAlliedTo(candidate) && !owner.isAlliedTo(candidate)
                && (candidate instanceof Player player
                    ? !player.isCreative() && !player.isSpectator()
                        && owner.getServer() != null && owner.getServer().isPvpAllowed()
                        && owner.canHarmPlayer(player)
                    : !DnvCombatPolicyRuntime.isPvpProtected(candidate))
                && !DnvCombatCoordinator.isExcluded(candidate);
    }

    private static LivingEntity firstEntity(ServerLevel level,
            DnvHoverRoutePlatformEntity platform, Vec3 start, Vec3 end) {
        LivingEntity first = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(start, end).inflate(0.5D),
                entity -> entity != platform && entity.isAlive())) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.25D).clip(start, end);
            if (hit.isPresent()) {
                double distance = start.distanceToSqr(hit.orElseThrow());
                if (distance < nearest) {
                    nearest = distance;
                    first = candidate;
                }
            }
        }
        return first;
    }

    private static boolean loadedSegment(ServerLevel level, Vec3 start, Vec3 end) {
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) / 8.0D));
        for (int step = 0; step <= steps; step++) {
            Vec3 point = start.lerp(end, step / (double) steps);
            BlockPos block = BlockPos.containing(point);
            if (!level.hasChunk(block.getX() >> 4, block.getZ() >> 4)
                    || !level.getWorldBorder().isWithinBounds(block)) {
                return false;
            }
        }
        return true;
    }

    private static void emitShot(ServerLevel level, Vec3 start, Vec3 end) {
        level.playSound(null, start.x, start.y, start.z, SoundEvents.CROSSBOW_SHOOT,
                SoundSource.HOSTILE, 0.8F, 1.15F);
        for (int step = 0; step < 12; step++) {
            Vec3 point = start.lerp(end, step / 12.0D);
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public enum SessionIssue {
        NONE, NOT_AUTHORIZED, OCCUPIED, NO_SESSION
    }

    public enum ShotIssue {
        FIRED, NOT_AUTHORIZED, NO_SESSION, COOLDOWN, OUT_OF_RANGE,
        UNLOADED, PROTECTED_TARGET, BLOCKED
    }
}
