package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicy;
import com.liucodex.doggiesnewvehicle.compat.DnvCombatPolicyRuntime;
import com.liucodex.doggiesnewvehicle.compat.DnvBossTeamMembership;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetCandidateCache;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetExclusions;
import com.liucodex.doggiesnewvehicle.core.DnvHoverFlightPolicy;
import com.liucodex.doggiesnewvehicle.core.DnvHoverFlightPolicy.Point;
import com.liucodex.doggiesnewvehicle.core.DnvHoverSummonPolicy;
import com.liucodex.noise.entity.NoiseVehicleEntity;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** DNV-owned flying boss prototype. The visible cube is renderer-only. */
public final class DnvHoverCubeBossEntity extends Mob
        implements DnvCombatPolicy.BossAutonomy, com.liucodex.doggiesnewvehicle.api.lifecycle.DnvRemovalLifecycle {
    private static final double TARGET_RANGE = 64.0D;
    private static final String NOISE_PARENT_KEY = "DnvHoverCubeParent";
    private static final int[][] DROP_OFFSETS = {
            {12, 0}, {0, 12}, {-12, 0}, {0, -12},
            {10, 10}, {-10, 10}, {-10, -10}, {10, -10}
    };
    private final DnvTargetCandidateCache targetCandidates = new DnvTargetCandidateCache(20);
    private final com.liucodex.doggiesnewvehicle.compat.DnvTargetSight targetSight =
            new com.liucodex.doggiesnewvehicle.compat.DnvTargetSight(0);
    private boolean targetVisible;
    private boolean anchorReady;
    private double anchorX;
    private double anchorZ;
    private Point flightVelocity = new Point(0.0D, 0.0D, 0.0D);
    @Nullable private UUID lockedTargetId;
    @Nullable private BlockPos markedLandingSite;
    private int warningTicks;
    private int spawnedNoiseCount;
    private long nextSummonGameTime;

    public DnvHoverCubeBossEntity(
            EntityType<? extends DnvHoverCubeBossEntity> type,
            Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        // The bounded flight controller is the only movement writer.
    }

    @Override
    public void travel(Vec3 input) {
        // Suppress LivingEntity's ground motion; tick() applies server flight once.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        setNoGravity(true);
        if (!anchorReady) {
            DnvBossTeamMembership.assignIfUnassigned(this);
            anchorX = getX();
            anchorZ = getZ();
            anchorReady = true;
        }
        if (!isAlive() || isRemoved()) {
            clearWarning();
            stopFlight();
            return;
        }
        tickNoiseSummon(serverLevel);

        Entity flightTarget = visibleFlightTarget(serverLevel);
        boolean followTarget = flightTarget != null;
        double centerX = followTarget ? flightTarget.getX() : anchorX;
        double centerZ = followTarget ? flightTarget.getZ() : anchorZ;

        Point goalOnOrbit = DnvHoverFlightPolicy.orbitGoal(
                centerX, centerZ, tickCount, 0,
                serverLevel.getMinBuildHeight(), serverLevel.getMaxBuildHeight());
        int terrainX = (int) Math.floor(goalOnOrbit.x());
        int terrainZ = (int) Math.floor(goalOnOrbit.z());
        AABB goalBounds = getBoundingBox().move(
                goalOnOrbit.x() - getX(), 0.0D, goalOnOrbit.z() - getZ());
        if (!serverLevel.hasChunk(terrainX >> 4, terrainZ >> 4)
                || !serverLevel.getWorldBorder().isWithinBounds(goalBounds)) {
            stopFlight();
            return;
        }

        int terrainY = serverLevel.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, terrainX, terrainZ);
        Point goal = DnvHoverFlightPolicy.orbitGoal(
                centerX, centerZ, tickCount, terrainY,
                serverLevel.getMinBuildHeight(), serverLevel.getMaxBuildHeight());
        Point next = DnvHoverFlightPolicy.nextMotion(
                new Point(getX(), getY(), getZ()), flightVelocity, goal);
        AABB plannedBounds = getBoundingBox().move(next.x(), next.y(), next.z());
        if (!safeEnvelope(serverLevel, plannedBounds)) {
            stopFlight();
            return;
        }
        Point accepted = DnvHoverFlightPolicy.chooseUnobstructed(next,
                candidate -> canTraverse(serverLevel, candidate));
        Vec3 motion = new Vec3(accepted.x(), accepted.y(), accepted.z());
        if (motion.lengthSqr() < 1.0E-9D) {
            stopFlight();
            return;
        }
        move(MoverType.SELF, motion);
        flightVelocity = accepted;
        setDeltaMovement(motion);
        if (motion.horizontalDistanceSqr() > 1.0E-6D) {
            setYRot((float) Math.toDegrees(Math.atan2(-motion.x, motion.z)));
        }
    }

    private Entity visibleFlightTarget(ServerLevel level) {
        Entity target = lockedTargetId == null ? null : level.getEntity(lockedTargetId);
        return targetVisible && isLegalTarget(target) ? target : null;
    }

    private static boolean hasLoadedChunks(ServerLevel level, AABB bounds) {
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

    private boolean canTraverse(ServerLevel level, Point candidate) {
        AABB bounds = getBoundingBox().move(
                candidate.x(), candidate.y(), candidate.z());
        return safeEnvelope(level, bounds) && level.noCollision(this, bounds);
    }

    private static boolean safeEnvelope(ServerLevel level, AABB bounds) {
        return hasLoadedChunks(level, bounds)
                && bounds.minY >= level.getMinBuildHeight()
                && bounds.maxY <= level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(bounds);
    }

    private void tickNoiseSummon(ServerLevel level) {
        targetVisible = false;
        long now = level.getGameTime();
        // An explicit vanilla team is required until the no-team boss relation is defined.
        if (!(getTeam() instanceof PlayerTeam)
                || !DnvHoverSummonPolicy.mayLock(spawnedNoiseCount, nextSummonGameTime, now)) {
            clearWarning();
            targetSight.clear();
            return;
        }
        Entity target = lockedTargetId == null ? null : level.getEntity(lockedTargetId);
        if (isLegalTarget(target)) {
            Boolean visible = targetSight.visible(this, getEyePosition(), target);
            if (visible == null) return;
            if (!visible) { clearWarning(); target = null; }
        }
        if (!isLegalTarget(target)) {
            clearWarning();
            targetCandidates.refreshIfDue(this, TARGET_RANGE, this::isLegalTarget);
            target = targetSight.select(this, getEyePosition(), targetCandidates.candidates(this),
                    null, this::isLegalTarget);
            if (target == null) {
                return;
            }
            markedLandingSite = findLandingSite(level, target);
            if (markedLandingSite == null) {
                nextSummonGameTime = DnvHoverSummonPolicy.nextAllowedTime(now, false);
                targetCandidates.clear();
                targetSight.clear();
                return;
            }
            lockedTargetId = target.getUUID();
        }
        targetVisible = true;
        if (markedLandingSite == null) {
            clearWarning();
            return;
        }
        if (!DnvHoverSummonPolicy.targetNearMarkedSite(
                target.getX(), target.getZ(),
                markedLandingSite.getX() + 0.5D,
                markedLandingSite.getZ() + 0.5D)) {
            nextSummonGameTime = DnvHoverSummonPolicy.nextAllowedTime(now, false);
            clearWarning();
            return;
        }

        warningTicks = DnvHoverSummonPolicy.advanceWarning(warningTicks, true);
        if (warningTicks % 10 == 0) {
            if (prepareNoiseAt(level, markedLandingSite) == null) {
                nextSummonGameTime = DnvHoverSummonPolicy.nextAllowedTime(now, false);
                clearWarning();
                return;
            }
            level.sendParticles(ParticleTypes.END_ROD,
                    markedLandingSite.getX() + 0.5D,
                    markedLandingSite.getY() + 1.5D,
                    markedLandingSite.getZ() + 0.5D,
                    10, 0.8D, 0.8D, 0.8D, 0.01D);
        }
        if (warningTicks % 20 == 1) {
            target.getSelfAndPassengers()
                    .filter(ServerPlayer.class::isInstance)
                    .map(ServerPlayer.class::cast)
                    .forEach(player -> player.displayClientMessage(Component.translatable(
                            "message.doggiesnewvehicle.hover_cube_boss.warning"), true));
        }
        if (warningTicks < DnvHoverSummonPolicy.WARNING_TICKS) {
            return;
        }
        boolean spawned = isLegalTarget(target)
                && visibleLoadedTarget(level, target)
                && spawnNoiseAt(level, markedLandingSite);
        if (spawned) {
            spawnedNoiseCount++;
        }
        nextSummonGameTime = DnvHoverSummonPolicy.nextAllowedTime(now, spawned);
        clearWarning();
        targetCandidates.clear();
        targetSight.clear();
    }

    private boolean isLegalTarget(@Nullable Entity target) {
        return target != null && target != this && target.level() == level()
                && target.isAlive()
                && distanceToSqr(target) <= TARGET_RANGE * TARGET_RANGE
                && (target instanceof LivingEntity || target instanceof VehicleEntity)
                && !DnvTargetExclusions.isUnavailableCombatTarget(target)
                && !(target.getPersistentData().hasUUID(NOISE_PARENT_KEY)
                    && getUUID().equals(target.getPersistentData().getUUID(NOISE_PARENT_KEY)))
                && DnvCombatPolicyRuntime.allowsBossNativeTarget(this, target, true);
    }

    private boolean visibleLoadedTarget(ServerLevel level, Entity target) {
        AABB sightArea = new AABB(getEyePosition(), target.getEyePosition()).inflate(1.0D);
        return hasLoadedChunks(level, sightArea) && hasLineOfSight(target);
    }

    @Nullable
    private BlockPos findLandingSite(ServerLevel level, Entity target) {
        for (int[] offset : DROP_OFFSETS) {
            int x = (int) Math.floor(target.getX()) + offset[0];
            int z = (int) Math.floor(target.getZ()) + offset[1];
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (prepareNoiseAt(level, candidate) != null) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private NoiseVehicleEntity prepareNoiseAt(ServerLevel level, BlockPos site) {
        if (!level.hasChunk(site.getX() >> 4, site.getZ() >> 4)
                || site.getY() <= level.getMinBuildHeight() + 1
                || site.getY() + 6 > level.getMaxBuildHeight()
                || !level.getBlockState(site.below()).isSolid()) {
            return null;
        }
        NoiseVehicleEntity noise = com.liucodex.noise.init.ModEntities.NOISE.get().create(level);
        if (noise == null) {
            return null;
        }
        noise.setPos(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D);
        AABB bounds = noise.getBoundingBox();
        return hasLoadedChunks(level, bounds)
                && level.getWorldBorder().isWithinBounds(bounds)
                && level.noCollision(noise, bounds) ? noise : null;
    }

    private boolean spawnNoiseAt(ServerLevel level, BlockPos site) {
        NoiseVehicleEntity noise = prepareNoiseAt(level, site);
        if (noise == null) {
            return false;
        }
        noise.alignCoreToHull();
        noise.setHealth(NoiseVehicleEntity.MAX_HEALTH);
        noise.getPersistentData().putUUID(NOISE_PARENT_KEY, getUUID());
        if (!level.addFreshEntity(noise)) {
            return false;
        }
        if (getTeam() instanceof PlayerTeam team) {
            DnvBossTeamMembership.inheritIfUnassigned(noise, team);
        }
        return true;
    }

    private void clearWarning() {
        targetVisible = false;
        lockedTargetId = null;
        markedLandingSite = null;
        warningTicks = 0;
    }

    @Override
    public void remove(RemovalReason reason) {
        releaseExternalState(reason);
        super.remove(reason);
    }

    @Override public void releaseExternalState(RemovalReason reason) {
        DnvBossTeamMembership.releaseOnTerminalRemoval(this, reason);
        clearWarning();
        targetCandidates.clear();
        targetSight.clear();
    }

    private void stopFlight() {
        flightVelocity = new Point(0.0D, 0.0D, 0.0D);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity other) {
        // A transient collision cannot change the server flight state.
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (anchorReady) {
            tag.putDouble("DnvHoverAnchorX", anchorX);
            tag.putDouble("DnvHoverAnchorZ", anchorZ);
        }
        tag.putInt("DnvHoverNoiseSpawned", spawnedNoiseCount);
        tag.putLong("DnvHoverNextSummon", nextSummonGameTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        anchorReady = tag.contains("DnvHoverAnchorX", 6)
                && tag.contains("DnvHoverAnchorZ", 6);
        if (anchorReady) {
            anchorX = tag.getDouble("DnvHoverAnchorX");
            anchorZ = tag.getDouble("DnvHoverAnchorZ");
        }
        flightVelocity = new Point(0.0D, 0.0D, 0.0D);
        spawnedNoiseCount = Math.max(0, Math.min(
                DnvHoverSummonPolicy.MAX_LIFETIME_SUMMONS,
                tag.getInt("DnvHoverNoiseSpawned")));
        nextSummonGameTime = Math.max(0L, tag.getLong("DnvHoverNextSummon"));
        clearWarning();
        targetCandidates.clear();
        targetSight.clear();
        setNoAi(true);
        setNoGravity(true);
    }
}
