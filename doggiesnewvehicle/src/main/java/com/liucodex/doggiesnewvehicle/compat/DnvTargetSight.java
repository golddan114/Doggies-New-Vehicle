package com.liucodex.doggiesnewvehicle.compat;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Bounded observations for one autonomous observer; the entity remains the target writer. */
public final class DnvTargetSight {
    private final int slot;
    private final Map<UUID, DnvSightObservation> observations = new LinkedHashMap<>();
    private ServerLevel budgetLevel;
    private UUID budgetOwner;
    private int cursor;

    public DnvTargetSight(int slot) { this.slot = slot; }

    /** Null means deferred, never permission to aim at a live unseen entity. */
    public Boolean visible(Entity source, Vec3 origin, Entity target) {
        if (target == null || !(source.level() instanceof ServerLevel level)) return false;
        if (budgetLevel != level) {
            clear();
            budgetLevel = level;
            budgetOwner = UUID.nameUUIDFromBytes((source.getUUID() + ":sight:" + slot)
                    .getBytes(StandardCharsets.UTF_8));
        }
        UUID id = target.getUUID();
        DnvSightObservation old = observations.get(id);
        if (old != null && old.reusable(id, origin, target.getBoundingBox(), target.getEyeY(), 0, source.tickCount))
            return old.clear();
        if (!DnvTargetingBudget.admitGeometryCheck(level, budgetOwner)) return null;
        boolean clear = loadedLine(level, origin, target.getBoundingBox().getCenter())
                && DnvTargetLineOfSight.clearAimPoint(level, source, origin, target).isPresent();
        if (observations.size() >= 32 && !observations.containsKey(id))
            observations.remove(observations.keySet().iterator().next());
        observations.put(id, new DnvSightObservation(id, origin, target.getBoundingBox(), target.getEyeY(),
                0, source.tickCount, DnvTargetingBudget.nextCheckTick(source.tickCount, source.getId(),
                DnvTargetingBudget.GEOMETRY_RECHECK_INTERVAL, slot), clear));
        return clear;
    }

    public Entity select(Entity source, Vec3 origin, List<Entity> candidates, Entity current,
            Predicate<Entity> eligible) {
        if (current != null && eligible.test(current)) {
            Boolean sight = visible(source, origin, current);
            if (Boolean.TRUE.equals(sight)) return current;
            if (sight == null) return null;
        }
        List<Entity> ordered = candidates.stream().filter(eligible)
                .sorted(Comparator.comparingDouble((Entity e) -> source.distanceToSqr(e))
                        .thenComparing(Entity::getUUID)).toList();
        for (int offset = 0, start = cursor; offset < ordered.size(); offset++) {
            int index = Math.floorMod(start + offset, ordered.size());
            Entity candidate = ordered.get(index);
            if (candidate == current) continue;
            Boolean sight = visible(source, origin, candidate);
            cursor = sight == null ? index : (index + 1) % ordered.size();
            if (sight == null) return null;
            if (sight) return candidate;
        }
        return null;
    }

    private static boolean loadedLine(ServerLevel level, Vec3 from, Vec3 to) {
        for (int x = (int)Math.floor(Math.min(from.x, to.x)) >> 4;
                x <= ((int)Math.floor(Math.max(from.x, to.x)) >> 4); x++) {
            for (int z = (int)Math.floor(Math.min(from.z, to.z)) >> 4;
                    z <= ((int)Math.floor(Math.max(from.z, to.z)) >> 4); z++)
                if (!level.hasChunk(x, z)) return false;
        }
        return true;
    }

    public void clear() {
        if (budgetLevel != null && budgetOwner != null)
            DnvTargetingBudget.releaseGeometryOwner(budgetLevel, budgetOwner);
        observations.clear(); cursor = 0; budgetLevel = null; budgetOwner = null;
    }
}
