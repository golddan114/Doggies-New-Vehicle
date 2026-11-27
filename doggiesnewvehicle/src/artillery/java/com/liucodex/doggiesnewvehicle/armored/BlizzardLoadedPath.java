package com.liucodex.doggiesnewvehicle.armored;

import com.liucodex.doggiesnewvehicle.api.combat.DnvProjectilePath;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Enumerates every crossed chunk, including thin diagonal intersections, before block clipping. */
final class BlizzardLoadedPath {
    private BlizzardLoadedPath() { }
    static boolean loaded(Entity source, Vec3 from, Vec3 to) {
        var world = source.level();
        for (Vec3 point : new Vec3[] {from, to}) {
            BlockPos block = BlockPos.containing(point);
            if (world.isOutsideBuildHeight(block) || !world.getWorldBorder().isWithinBounds(block)) return false;
        }
        return chunks(from, to, world::hasChunk);
    }
    /** Returns the original endpoint when clear, a shortened endpoint at an edge, or null if blocked at the start. */
    @Nullable static Vec3 clipStep(Entity source, Vec3 from, Vec3 to) {
        if (!(source.level() instanceof ServerLevel level)) return null;
        BlockPos start = BlockPos.containing(from);
        BlockPos end = BlockPos.containing(to);
        if (level.isOutsideBuildHeight(start) || level.isOutsideBuildHeight(end)
                || !level.getWorldBorder().isWithinBounds(start) || !level.getWorldBorder().isWithinBounds(end)) return null;
        double limit = DnvProjectilePath.limit(level, from, to);
        if (limit == 0) return null;
        return limit == 1 ? to : from.lerp(to, limit);
    }
    static boolean chunks(Vec3 from, Vec3 to, BiPredicate<Integer, Integer> loaded) {
        if (!Double.isFinite(from.x + from.y + from.z + to.x + to.y + to.z) || from.distanceToSqr(to) > 512 * 512) return false;
        int x = (int) Math.floor(from.x / 16), z = (int) Math.floor(from.z / 16);
        int endX = (int) Math.floor(to.x / 16), endZ = (int) Math.floor(to.z / 16);
        double dx = to.x - from.x, dz = to.z - from.z;
        int sx = Double.compare(dx, 0), sz = Double.compare(dz, 0);
        double tx = sx == 0 ? Double.POSITIVE_INFINITY : ((x + (sx > 0 ? 1 : 0)) * 16 - from.x) / dx;
        double tz = sz == 0 ? Double.POSITIVE_INFINITY : ((z + (sz > 0 ? 1 : 0)) * 16 - from.z) / dz;
        double stepX = sx == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dx);
        double stepZ = sz == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dz);
        for (int visits = 0; visits < 68; visits++) {
            if (!loaded.test(x, z)) return false;
            if (x == endX && z == endZ) return true;
            if (tx < tz) { x += sx; tx += stepX; }
            else if (tz < tx) { z += sz; tz += stepZ; }
            else {
                // Conservative corner check protects vanilla clipping's endpoint epsilon.
                if (!loaded.test(x + sx, z) || !loaded.test(x, z + sz)) return false;
                x += sx; z += sz; tx += stepX; tz += stepZ;
            }
        }
        return false;
    }
}
