package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.core.BlockPos;

/** Stable center-out sphere order; buried ordinary blocks participate in the same bounded scan. */
public final class VacuumTerrainScan {
    private static final int RADIUS = VacuumPayloadProgress.TERRAIN_RADIUS;
    private static final int[] BLAST = create(VacuumPayloadProgress.BLAST_RADIUS);
    private static final int[] ORDER = create(RADIUS);
    private VacuumTerrainScan() { }
    public static int size() { return ORDER.length; }
    public static BlockPos offset(int cursor) { return NuclearSphereScan.offset(RADIUS, ORDER[cursor]); }

    public static int blastSize() { return BLAST.length; }
    public static BlockPos blastOffset(int cursor) { return NuclearSphereScan.offset(VacuumPayloadProgress.BLAST_RADIUS, BLAST[cursor]); }

    private static int distanceSquared(int radius, int index) {
        int width = radius * 2 + 1;
        int x = index / (width * width) - radius, y = index / width % width - radius, z = index % width - radius;
        return x * x + y * y + z * z;
    }
    private static int[] create(int radius) {
        int cube = NuclearSphereScan.size(radius), limit = radius * radius;
        int[] counts = new int[limit + 1];
        for (int index = 0; index < cube; index++) {
            int distance = distanceSquared(radius, index);
            if (distance <= limit) counts[distance]++;
        }
        int total = 0;
        for (int distance = 0; distance <= limit; distance++) {
            int count = counts[distance]; counts[distance] = total; total += count;
        }
        int[] order = new int[total];
        for (int index = 0; index < cube; index++) {
            int distance = distanceSquared(radius, index);
            if (distance <= limit) order[counts[distance]++] = index;
        }
        return order;
    }
}
