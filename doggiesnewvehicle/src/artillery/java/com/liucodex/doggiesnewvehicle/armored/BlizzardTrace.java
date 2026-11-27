package com.liucodex.doggiesnewvehicle.armored;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Broad-phase AABB followed by native damage OBB clipping, bounded by the nearest wall. */
final class BlizzardTrace {
    record Hit(Entity entity, Vec3 position) { }
    private BlizzardTrace() { }
    /** One nearest damage box per entity; wall clipping is supplied by the caller. */
    static java.util.List<Hit> piercing(Level level, Vec3 from, Vec3 end, Predicate<Entity> filter) {
        var hits = new java.util.ArrayList<Hit>();
        var seen = new java.util.HashSet<Entity>();
        for (Entity entity : level.getEntities((Entity) null, new AABB(from, end).inflate(.25), filter)) {
            if (!seen.add(entity)) continue;
            Hit hit = clip(entity, from, end);
            if (hit != null) hits.add(hit);
        }
        hits.sort(java.util.Comparator.comparingDouble(hit -> from.distanceToSqr(hit.position())));
        return hits;
    }
    static Hit nearest(Level level, Vec3 from, Vec3 end, Predicate<Entity> filter) {
        Hit nearest = null;
        double distance = from.distanceToSqr(end);
        for (Entity entity : level.getEntities((Entity) null, new AABB(from, end).inflate(.25), filter)) {
            Hit hit = clip(entity, from, end);
            if (hit != null && from.distanceToSqr(hit.position()) < distance) {
                distance = from.distanceToSqr(hit.position()); nearest = hit;
            }
        }
        return nearest;
    }
    private static Hit clip(Entity entity, Vec3 from, Vec3 end) {
        Hit nearest = null;
        double distance = from.distanceToSqr(end);
        if (entity instanceof VehicleEntity vehicle) {
            for (var box : vehicle.getOBBs()) {
                if (box.part == com.atsuishio.superbwarfare.tools.OBB.Part.COLLISION) continue;
                var clipped = box.clip(new Vector3d(from.x, from.y, from.z), new Vector3d(end.x, end.y, end.z));
                if (box.contains(from)) clipped = java.util.Optional.of(new Vector3d(from.x, from.y, from.z));
                if (clipped.isEmpty()) continue;
                var p = clipped.get(); Vec3 hit = new Vec3(p.x, p.y, p.z);
                double candidate = from.distanceToSqr(hit);
                if (candidate < distance) { distance = candidate; nearest = new Hit(entity, hit); }
            }
        } else {
            var box = entity.getBoundingBox().inflate(entity.getPickRadius());
            var clipped = box.contains(from) ? java.util.Optional.of(from) : box.clip(from, end);
            if (clipped.isPresent() && from.distanceToSqr(clipped.get()) < distance) {
                distance = from.distanceToSqr(clipped.get()); nearest = new Hit(entity, clipped.get());
            }
        }
        return nearest;
    }
}
