package com.liucodex.doggiesnewvehicle.api.combat;

import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Sweep through the chunk grid without requesting chunks. */
public final class DnvProjectilePath {
    private static final double EDGE_MARGIN = 1.0e-6;

    private DnvProjectilePath() {}

    public static double limit(ServerLevel level, Vec3 from, Vec3 to) {
        return limit(from, to, (x, z) -> level.isPositionEntityTicking(new BlockPos(x << 4, 0, z << 4)));
    }

    public static boolean blastLoaded(ServerLevel level, Vec3 center, float radius) {
        return blastLoaded(center, radius, (x, z) -> level.getChunkSource().getChunkNow(x, z) != null);
    }

    public static boolean blastLoaded(Vec3 center, float radius, BiPredicate<Integer, Integer> loaded) {
        if (!Float.isFinite(radius) || radius < 0 || radius > 128
                || !Double.isFinite(center.x + center.y + center.z)) return false;
        // SBW damageEntities queries 2 * radius + 1. Include the adjacent block for updates.
        double reach = 2.0 * radius + 2;
        int minX = Mth.floor(center.x - reach) >> 4;
        int maxX = Mth.floor(center.x + reach) >> 4;
        int minZ = Mth.floor(center.z - reach) >> 4;
        int maxZ = Mth.floor(center.z + reach) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!loaded.test(x, z)) return false;
            }
        }
        return true;
    }

    public static double limit(Vec3 from, Vec3 to, BiPredicate<Integer, Integer> active) {
        Vec3 delta = to.subtract(from);
        if (!Double.isFinite(from.x + from.y + from.z + to.x + to.y + to.z)
                || delta.lengthSqr() > 512 * 512) return 0;

        // Native block tracing extends the segment slightly beyond both ends.
        Vec3 traceStart = from.subtract(delta.scale(EDGE_MARGIN));
        int x = Mth.floor(traceStart.x) >> 4;
        int z = Mth.floor(traceStart.z) >> 4;
        int dx = delta.x > 0 ? 1 : delta.x < 0 ? -1 : 0;
        int dz = delta.z > 0 ? 1 : delta.z < 0 ? -1 : 0;
        double crossX = dx == 0 ? Double.POSITIVE_INFINITY : ((x + (dx > 0 ? 1 : 0)) * 16.0 - from.x) / delta.x;
        double crossZ = dz == 0 ? Double.POSITIVE_INFINITY : ((z + (dz > 0 ? 1 : 0)) * 16.0 - from.z) / delta.z;
        double strideX = dx == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(delta.x);
        double strideZ = dz == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(delta.z);
        double enteredAt = 0;
        for (int visited = 0; visited < 68; visited++) {
            if (!active.test(x, z)) return Math.max(0, enteredAt - EDGE_MARGIN);
            double crossing = Math.min(crossX, crossZ);
            if (crossing > 1 + EDGE_MARGIN) return 1;
            if (crossX == crossZ && (!active.test(x + dx, z) || !active.test(x, z + dz))) {
                return Math.max(0, crossing - EDGE_MARGIN);
            }
            enteredAt = crossing;
            if (crossX <= crossing) {
                x += dx;
                crossX += strideX;
            }
            if (crossZ <= crossing) {
                z += dz;
                crossZ += strideZ;
            }
        }
        return 0;
    }
}
