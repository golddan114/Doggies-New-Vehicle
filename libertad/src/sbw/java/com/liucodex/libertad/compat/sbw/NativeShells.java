package com.liucodex.libertad.compat.sbw;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.tools.TraceTool;
import com.atsuishio.superbwarfare.tools.TraceTool.RayTraceResultEntity;
import com.liucodex.libertad.naval.MountGeometry.Point;
import com.liucodex.libertad.naval.ShellRange;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Shot admission and bounded cleanup for projectiles emitted by this ship. */
public final class NativeShells {
    private static final String TAG = "LibertadShot";
    private static final String CLIENT_TRACER = "LibertadTracer";
    private static final int TRACER_SPAWN_MARKER = 0x4C425452;
    private static final ThreadLocal<Shot> SHOT = new ThreadLocal<>();
    private static final Map<ServerLevel, Map<UUID, WeakReference<CannonShellEntity>>> WATCH = new WeakHashMap<>();
    private static final int BUDGET = 128;

    private NativeShells() {}

    public static boolean room(ServerLevel level, int count) {
        var shells = WATCH.get(level);
        return shells == null || shells.size() + count <= BUDGET;
    }

    public static boolean fire(GunData gun, ShootParameters parameters, boolean secondary) {
        if (SHOT.get() != null || !room(parameters.level, 1)) return false;
        Shot shot = new Shot(parameters, secondary);
        SHOT.set(shot);
        try {
            gun.shoot(parameters);
            if (!shot.accepted) return false;
            WATCH.computeIfAbsent(parameters.level, ignored -> new LinkedHashMap<>())
                    .put(shot.entity.getUUID(), new WeakReference<>(shot.entity));
            return true;
        } finally {
            SHOT.remove();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof CannonShellEntity shell)) return;
        if (managed(shell)) {
            // In-flight ammunition belongs to the launch session. Replayed chunk NBT is inert.
            event.setCanceled(true);
            return;
        }
        Shot shot = SHOT.get();
        if (shot == null || shot.entity != null || shot.parameters.level != level
                || shell.getOwner() != shot.parameters.shooter
                || shell.position().distanceToSqr(shot.parameters.shootPosition) > .01) return;
        shot.entity = shell;
        var state = new CompoundTag();
        Vec3 muzzle = shot.parameters.shootPosition;
        state.putDouble("X", muzzle.x);
        state.putDouble("Y", muzzle.y);
        state.putDouble("Z", muzzle.z);
        state.putInt("Range", shot.secondary ? ShellRange.SECONDARY : ShellRange.MAIN);
        if (!shot.secondary && shell.getExplosionRadius() > 0) {
            state.putFloat("BlockBlastRadius", shell.getExplosionRadius());
        }
        state.putLong("Until", level.getGameTime() + ShellRange.LIFETIME);
        shell.getPersistentData().put(TAG, state);
        shell.setLife(Integer.MAX_VALUE);
        if (shot.secondary) shell.setExplosionDestroy(false);
    }

    public static boolean actualResult(ShootParameters parameters, boolean vanilla) {
        Shot shot = SHOT.get();
        if (shot == null || shot.parameters != parameters) return vanilla;
        shot.accepted = shot.entity != null && !shot.entity.isRemoved()
                && parameters.level.getEntity(shot.entity.getUUID()) == shot.entity;
        return shot.accepted;
    }

    public static boolean managed(Entity shell) { return shell.getPersistentData().contains(TAG, 10); }

    public static void writeTracerSpawn(FastThrowableProjectile shell, FriendlyByteBuf buffer) {
        if (shell instanceof CannonShellEntity && managed(shell))
            buffer.writeInt(TRACER_SPAWN_MARKER).writeFloat(shell.getCustomGravity());
    }

    public static void readTracerSpawn(FastThrowableProjectile shell, FriendlyByteBuf buffer) {
        if (!(shell instanceof CannonShellEntity) || buffer.readableBytes() < 8
                || buffer.getInt(buffer.readerIndex()) != TRACER_SPAWN_MARKER) return;
        buffer.skipBytes(4);
        float gravity = buffer.readFloat();
        if (!Float.isFinite(gravity)) return;
        shell.setCustomGravity(gravity);
        shell.getPersistentData().putBoolean(CLIENT_TRACER, true);
    }

    public static boolean hasClientTracer(Entity shell) {
        return shell instanceof CannonShellEntity && shell.getPersistentData().getBoolean(CLIENT_TRACER);
    }

    public static boolean blastAreaReady(FastThrowableProjectile shell, Vec3 center) {
        if (!(shell.level() instanceof ServerLevel level) || !managed(shell)) return true;
        double radius = shell.getExplosionRadius();
        if (radius <= 0) return true;
        if (!Double.isFinite(radius) || radius > 16) return false;
        var range = range(shell);
        double remaining = range.radius() - radius;
        if (remaining < 0 || point(center).minus(range.origin()).lengthSquared() > remaining * remaining) return false;
        double scanRadius = radius * 2 + 2;
        int loX = net.minecraft.util.Mth.floor(center.x - scanRadius) >> 4;
        int hiX = net.minecraft.util.Mth.floor(center.x + scanRadius) >> 4;
        int loZ = net.minecraft.util.Mth.floor(center.z - scanRadius) >> 4;
        int hiZ = net.minecraft.util.Mth.floor(center.z + scanRadius) >> 4;
        for (int x = loX; x <= hiX; x++) for (int z = loZ; z <= hiZ; z++) {
            if (!level.isPositionEntityTicking(new BlockPos(x << 4, shell.getBlockY(), z << 4))) return false;
        }
        return true;
    }

    /** 0: normal tick, 1: discard before collision, 2: discard after the clipped step. */
    public static int limitStep(FastThrowableProjectile shell) {
        if (!(shell.level() instanceof ServerLevel) || !managed(shell)) return 0;
        Vec3 start = shell.position();
        var step = flight(shell, start, start.add(shell.getDeltaMovement()));
        if (step == null) return 1;
        if (step.terminal()) {
            shell.setDeltaMovement(step.end().subtract(start));
            return 2;
        }
        return 0;
    }

    public static FlightStep flight(FastThrowableProjectile shell, Vec3 from, Vec3 to) {
        var level = (ServerLevel) shell.level();
        return flight(range(shell), from, to, level.getGameTime(),
                (start, end) -> LoadedPath.limit(level, start, end),
                (start, end) -> WaterEntry.limit(level, shell, start, end));
    }

    public static FlightStep flight(ShellRange range, Vec3 from, Vec3 to, long tick,
            ToDoubleBiFunction<Vec3, Vec3> loaded, ToDoubleBiFunction<Vec3, Vec3> dry) {
        var step = range.next(point(from), point(to.subtract(from)), tick);
        if (!step.trace()) return null;
        var endpoint = step.endpoint();
        Vec3 end = new Vec3(endpoint.x(), endpoint.y(), endpoint.z());
        double active = loaded.applyAsDouble(from, end);
        if (active <= 0) return null;
        Vec3 loadedEnd = from.lerp(end, active);
        double air = dry.applyAsDouble(from, loadedEnd);
        if (air <= 0) return null;
        return new FlightStep(from.lerp(loadedEnd, air), step.discardIfMiss() || active < 1 || air < 1);
    }

    public static boolean containsSegment(FastThrowableProjectile shell, Vec3 from, Vec3 to) {
        var step = flight(shell, from, to);
        return step != null && !step.terminal();
    }

    /** Carries the ship's AP bounds through the native call without a second redirect or shared state. */
    public record PenetrationFilter(CannonShellEntity shell, Vec3 hit, Predicate<Entity> filter) implements Predicate<Entity> {
        @Override
        public boolean test(Entity entity) { return filter.test(entity); }

        public List<RayTraceResultEntity> trace(ServerLevel level, Vec3 from, Vec3 direction) {
            if (!containsSegment(shell, hit, from)) return List.of();
            var step = flight(shell, from, from.add(direction));
            if (step == null) return List.of();
            var block = level.clip(new ClipContext(from, step.end(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shell));
            Vec3 end = block.getType() == HitResult.Type.MISS ? step.end() : block.getLocation();
            if (from.distanceToSqr(end) < 1e-12) return List.of();
            return TraceTool.getEntitiesAlongVector(level, from, end.subtract(from),
                            filter.and(entity -> level.isPositionEntityTicking(entity.blockPosition())))
                    .stream().filter(result -> containsSegment(shell, from, result.getHitVec())).toList();
        }
    }

    public record FlightStep(Vec3 end, boolean terminal) {}

    private static ShellRange range(FastThrowableProjectile shell) {
        var state = shell.getPersistentData().getCompound(TAG);
        return new ShellRange(new Point(state.getDouble("X"), state.getDouble("Y"), state.getDouble("Z")),
                state.getInt("Range"), state.getLong("Until"));
    }

    @SubscribeEvent
    public static void watch(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var shells = WATCH.get(level);
        if (shells == null) return;
        shells.values().removeIf(ref -> {
            CannonShellEntity shell = ref.get();
            if (shell == null || shell.isRemoved()) return true;
            if (level.getGameTime() >= shell.getPersistentData().getCompound(TAG).getLong("Until")
                    || !level.isPositionEntityTicking(shell.blockPosition())) {
                shell.discard();
                return true;
            }
            return false;
        });
        if (shells.isEmpty()) WATCH.remove(level);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) WATCH.remove(level);
    }

    private static Point point(Vec3 vec) { return new Point(vec.x, vec.y, vec.z); }

    private static final class Shot {
        final ShootParameters parameters;
        final boolean secondary;
        CannonShellEntity entity;
        boolean accepted;
        Shot(ShootParameters parameters, boolean secondary) {
            this.parameters = parameters;
            this.secondary = secondary;
        }
    }
}
