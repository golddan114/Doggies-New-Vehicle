package com.liucodex.libertad.ship;

import com.liucodex.libertad.Libertad;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;

/** Bounded footprint and one chunk of water around it, active only during a helm lease. */
final class ShipTickets {
    private final Set<Long> held = new LinkedHashSet<>();

    void update(ServerLevel level, LibertadEntity ship, boolean active) {
        Set<Long> desired = active ? footprint(ship, 1) : Set.of();
        for (var it = held.iterator(); it.hasNext();) {
            long id = it.next();
            if (!desired.contains(id)) {
                force(level, ship, id, false);
                it.remove();
            }
        }
        int added = 0;
        for (long id : desired) {
            if (held.size() >= 128 || added >= 8) break;
            if (!held.contains(id) && force(level, ship, id, true)) {
                held.add(id);
                added++;
            }
        }
    }

    static Set<Long> footprint(LibertadEntity ship, int margin) {
        double yaw = Math.toRadians(ship.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        Set<Long> result = new LinkedHashSet<>();
        for (int i = 0; i <= 12; i++) {
            int along = i == 0 ? 0 : ((i + 1) / 2) * 13 * (i % 2 == 0 ? -1 : 1);
            for (int side = -9; side <= 9; side += 9) {
                int x = Mth.floor(ship.getX() - along * sin + side * cos) >> 4;
                int z = Mth.floor(ship.getZ() + along * cos + side * sin) >> 4;
                for (int dx = -margin; dx <= margin; dx++) for (int dz = -margin; dz <= margin; dz++) {
                    result.add(ChunkPos.asLong(x + dx, z + dz));
                }
            }
        }
        return result;
    }

    private static boolean force(ServerLevel level, LibertadEntity ship, long id, boolean add) {
        return ForgeChunkManager.forceChunk(level, Libertad.ID, ship.getUUID(), ChunkPos.getX(id), ChunkPos.getZ(id), add, true);
    }
}
