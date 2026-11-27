package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Schema 4: the union of a central sphere and a shallow, wider ellipsoid. Frozen cursor order. */
final class NuclearCraterShape {
    private static final int CORE_RADIUS = 16, DEPTH = 8, HEIGHT = 20;
    private static final Map<Integer, int[]> CACHE = new LinkedHashMap<>();
    private NuclearCraterShape() { }

    static double distance(BlockPos offset, int radius) {
        double x = offset.getX(), y = offset.getY(), z = offset.getZ();
        int core = Math.min(radius, CORE_RADIUS), vertical = Math.min(radius, y < 0 ? DEPTH : HEIGHT);
        return Math.min((x*x + y*y + z*z) / (core*core),
                (x*x + z*z) / (radius*radius) + y*y / (vertical*vertical));
    }
    static int size(int radius) { return cells(radius).length; }
    static BlockPos offset(int radius, int cursor) {
        int packed = cells(radius)[cursor];
        return new BlockPos((packed >>> 13) - 64, (packed & 63) - 32, (packed >>> 6 & 127) - 64);
    }
    static int columnEnd(int radius, int cursor) {
        int[] cells = cells(radius); int column = cells[cursor] >>> 6;
        int end = cursor + 1;
        while (end < cells.length && cells[end] >>> 6 == column) end++;
        return end;
    }
    static double front(int radius, int cursor) {
        if (cursor <= 0) return 0;
        var p = offset(radius, Math.min(size(radius), cursor) - 1);
        return Math.sqrt((double)p.getX()*p.getX() + (double)p.getZ()*p.getZ());
    }
    private static synchronized int[] cells(int radius) {
        NuclearSphereScan.size(radius); // Same bounded radius validation as persisted legacy scans.
        var cached = CACHE.get(radius);
        if (cached != null) return cached;
        var columns = new ArrayList<BlockPos>();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
            if (x*x + z*z <= radius*radius) columns.add(new BlockPos(x, 0, z));
        columns.sort(Comparator.comparingInt((BlockPos p) -> p.getX()*p.getX() + p.getZ()*p.getZ())
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        int[] buffer = new int[columns.size() * (Math.min(radius, CORE_RADIUS) + Math.min(radius, HEIGHT) + 1)];
        int count = 0;
        for (var p : columns) for (int y = -Math.min(radius, CORE_RADIUS); y <= Math.min(radius, HEIGHT); y++) {
            if (distance(new BlockPos(p.getX(), y, p.getZ()), radius) <= 1)
                buffer[count++] = (p.getX() + 64) << 13 | (p.getZ() + 64) << 6 | y + 32;
        }
        var result = java.util.Arrays.copyOf(buffer, count);
        if (CACHE.size() == 4) CACHE.remove(CACHE.keySet().iterator().next());
        CACHE.put(radius, result);
        return result;
    }
}
