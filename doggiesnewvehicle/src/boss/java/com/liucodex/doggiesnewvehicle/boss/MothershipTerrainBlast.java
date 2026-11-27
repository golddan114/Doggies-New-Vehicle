package com.liucodex.doggiesnewvehicle.boss;

import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/** Per-mothership terrain progress. Entity damage remains exclusively in the doomsday hit owner. */
public final class MothershipTerrainBlast {
    private final int radius;
    private final MothershipBlastFront front;
    private Vec3 center = Vec3.ZERO;
    private long lastTick = Long.MIN_VALUE;
    private boolean ignitePending;

    public MothershipTerrainBlast() { this(MothershipBlastFront.RADIUS); }
    public MothershipTerrainBlast(int radius) {
        this.front = new MothershipBlastFront(radius);
        this.radius = radius;
    }

    public boolean active() { return front.active(); }

    public void begin(Entity ship, Vec3 impact, boolean igniteGround) {
        if (!(ship.level() instanceof ServerLevel level) || !valid(impact)
                || !com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level, ship)) return;
        if (ForgeEventFactory.onExplosionStart(level, explosion(ship, impact))) return;
        center = impact;
        front.begin();
        lastTick = Long.MIN_VALUE;
        ignitePending = igniteGround;
    }

    /** Returns the impact once, after the final terrain batch, when ground fire was requested. */
    public Vec3 tick(Entity ship) {
        if (!front.active() || !(ship.level() instanceof ServerLevel level) || lastTick == level.getGameTime()) return null;
        lastTick = level.getGameTime();
        if (!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level, ship)) {
            front.restore(MothershipBlastFront.TOTAL);
            ignitePending = false;
            return null;
        }
        Explosion explosion = explosion(ship, center);
        BlockPos anchor = BlockPos.containing(center);
        // Both hard count caps and a soft two-millisecond search cap. Block callbacks remain on the server thread.
        long deadline = System.nanoTime() + 2_000_000L;
        int visits = 0;
        while (front.active() && visits < MothershipBlastFront.MAX_VISITS_PER_TICK
                && explosion.getToBlow().size() < MothershipBlastFront.MAX_BLOCKS_PER_TICK) {
            if (visits > 0 && (visits & 31) == 0 && System.nanoTime() >= deadline) break;
            var cell = front.next(); visits++;
            BlockPos pos = anchor.offset(cell.x(), cell.y(), cell.z());
            if (destroyable(level, pos, explosion)) explosion.getToBlow().add(pos);
        }
        if (!explosion.getToBlow().isEmpty()) {
            // Protection mods may remove blocks from this exact batch. Never accept injected out-of-scope positions.
            var allowed = new HashSet<>(explosion.getToBlow());
            ForgeEventFactory.onExplosionDetonate(level, explosion, new ArrayList<>(), radius * 2D);
            if (!com.liucodex.doggiesnewvehicle.compat.DnvBlockDamagePolicy.allowed(level, ship)) explosion.getToBlow().clear();
            explosion.getToBlow().removeIf(pos -> !allowed.contains(pos) || !destroyable(level, pos, explosion));
            explosion.finalizeExplosion(false); // Native block callbacks and explosion-decayed drops; no entity damage pass.
        }
        if (front.active() || !ignitePending) return null;
        ignitePending = false;
        return center;
    }

    private boolean destroyable(ServerLevel level, BlockPos pos, Explosion explosion) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        double distance = pos.getCenter().distanceToSqr(center);
        if (distance > radius * radius) return false;
        var state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) return false;
        // Hard blocks absorb the fringe. Bedrock and explosion-proof blocks remain intact.
        double force = radius * 4 * (1 - Math.sqrt(distance) / radius);
        return state.getExplosionResistance(level, pos, explosion) < force;
    }

    private Explosion explosion(Entity ship, Vec3 point) {
        return new Explosion(ship.level(), ship, point.x, point.y, point.z,
                radius, false, Explosion.BlockInteraction.DESTROY_WITH_DECAY);
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Cursor", front.cursor()); tag.putDouble("X", center.x); tag.putDouble("Y", center.y); tag.putDouble("Z", center.z);
        tag.putBoolean("IgnitePending", ignitePending && front.active());
        return tag;
    }
    public void restore(CompoundTag tag) {
        Vec3 point = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        front.restore(tag.contains("Cursor", 3) && valid(point) ? tag.getInt("Cursor") : Integer.MAX_VALUE);
        center = valid(point) ? point : Vec3.ZERO;
        ignitePending = front.active() && tag.getBoolean("IgnitePending");
        lastTick = Long.MIN_VALUE;
    }
    private static boolean valid(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z)
                && Math.abs(point.x) <= 30_000_000 && Math.abs(point.z) <= 30_000_000 && Math.abs(point.y) < 4096;
    }
}
