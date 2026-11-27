package com.liucodex.libertad.compat.sbw;

import com.liucodex.libertad.naval.ShellRange;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.function.BiPredicate;

/** Return the first inactive chunk boundary along a world segment without fetching chunks. */
public final class LoadedPath {
    // A diagonal can cross a boundary on each axis. Include both end columns.
    private static final int MAX_COLUMNS = 3 + (int) Math.ceil(Math.sqrt(2) * ShellRange.MAIN / 16);
    private LoadedPath() {}

    public static double limit(ServerLevel level, Vec3 start, Vec3 end) {
        return limit(start, end, (x, z) -> level.isPositionEntityTicking(new BlockPos(x << 4, Mth.floor(start.y), z << 4)));
    }

    public static double limit(Vec3 start, Vec3 end, BiPredicate<Integer, Integer> activeChunk) {
        Vec3 delta = end.subtract(start);
        int x = Mth.floor(start.x) >> 4;
        int z = Mth.floor(start.z) >> 4;
        int sx = delta.x > 0 ? 1 : delta.x < 0 ? -1 : 0;
        int sz = delta.z > 0 ? 1 : delta.z < 0 ? -1 : 0;
        double tx = sx == 0 ? Double.POSITIVE_INFINITY : ((x + (sx > 0 ? 1 : 0)) * 16. - start.x) / delta.x;
        double tz = sz == 0 ? Double.POSITIVE_INFINITY : ((z + (sz > 0 ? 1 : 0)) * 16. - start.z) / delta.z;
        double dx = sx == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(delta.x);
        double dz = sz == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(delta.z);
        double t = 0;
        for (int i = 0; i < MAX_COLUMNS; i++) {
            if (!activeChunk.test(x, z)) {
                return Math.max(0, t - 1e-6);
            }
            if (Math.min(tx, tz) > 1) return 1;
            t = Math.min(tx, tz);
            // The vanilla block ray can enter a side column when crossing a corner.
            if (Math.abs(tx - tz) <= 1e-7
                    && (!activeChunk.test(x + sx, z) || !activeChunk.test(x, z + sz))) {
                return Math.max(0, t - 1e-6);
            }
            if (tx <= t) { x += sx; tx += dx; }
            if (tz <= t) { z += sz; tz += dz; }
        }
        return Math.max(0, t - 1e-6);
    }
}
