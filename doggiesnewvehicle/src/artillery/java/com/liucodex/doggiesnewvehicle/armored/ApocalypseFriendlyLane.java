package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.tools.OBB;
import com.atsuishio.superbwarfare.tools.SeekTool;
import com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer.Continuation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** AI-only shot clearance. A null observation is unknown and must never authorize a shot. */
final class ApocalypseFriendlyLane {
    private final DnvAlliedApocalypseEntity tank;
    private final UUID budgetId;
    private long checkedAt = Long.MIN_VALUE;
    private Vec3 checkedFrom, checkedEnd;
    private Boolean checkedClear;

    ApocalypseFriendlyLane(DnvAlliedApocalypseEntity tank) {
        this.tank = tank;
        budgetId = new UUID(tank.getUUID().getMostSignificantBits() ^ 0x414946495245L,
                tank.getUUID().getLeastSignificantBits());
    }

    Boolean inspect(Vec3 from, Vec3 end, boolean immediate) {
        if (!(tank.level() instanceof ServerLevel level) || !finite(from) || !finite(end)
                || from.distanceToSqr(end) > 256 * 256) return null;
        long now = level.getGameTime();
        if (!immediate) {
            if (checkedAt != Long.MIN_VALUE && now >= checkedAt && now - checkedAt < 5
                    && checkedFrom.distanceToSqr(from) <= .25 && checkedEnd.distanceToSqr(end) <= 1)
                return checkedClear;
            if (!DnvTargetingBudget.admitGeometryCheck(level, budgetId)) return null;
        }
        int[] state = {0, 0}; // visited AI candidates, 0 clear / 1 blocked / 2 truncated
        // Padding includes the long chassis even when its ordinary entity AABB misses the ray.
        level.getEntities().get(EntityTypeTest.<Entity, DnvAlliedApocalypseEntity>forClass(DnvAlliedApocalypseEntity.class),
                new AABB(from, end).inflate(8), ally -> {
                    state[0]++;
                    if (ally != tank && ally.isAlive() && !ally.isRemoved() && !ally.isWreck()
                            && (tank.isAlliedTo(ally) || ally.isAlliedTo(tank) || SeekTool.IS_FRIENDLY.test(tank, ally))) {
                        AABB hull = ally.getBoundingBox();
                        var collision = ally.getCollisionOBB();
                        if (collision != null) hull = hull.minmax(OBB.getWorldAABB(collision));
                        hull = hull.inflate(.5);
                        if (hull.contains(from) || hull.clip(from, end).isPresent()) {
                            state[1] = 1;
                            return Continuation.ABORT;
                        }
                    }
                    if (state[0] >= 64) { state[1] = 2; return Continuation.ABORT; }
                    return Continuation.CONTINUE;
                });
        Boolean clear = state[1] == 2 ? null : state[1] == 0;
        if (!immediate) {
            checkedAt = now; checkedFrom = from; checkedEnd = end; checkedClear = clear;
        }
        return clear;
    }

    void clear() {
        checkedAt = Long.MIN_VALUE; checkedFrom = checkedEnd = null; checkedClear = null;
        if (tank.level() instanceof ServerLevel level) DnvTargetingBudget.releaseGeometryOwner(level, budgetId);
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
