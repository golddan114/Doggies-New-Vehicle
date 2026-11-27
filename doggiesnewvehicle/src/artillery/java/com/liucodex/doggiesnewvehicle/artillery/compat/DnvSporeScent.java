package com.liucodex.doggiesnewvehicle.artillery.compat;

import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/** Exact optional Spore identity; no Spore class loading or persistent cleanup state. */
public final class DnvSporeScent {
    private DnvSporeScent() { }

    public static boolean matches(Entity entity) {
        return com.liucodex.doggiesnewvehicle.core.DnvSporeScentIdentity.matches(
                entity.getClass().getName(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    public static void clear(ServerLevel level, Entity source, Vec3 point, int radius) {
        if (radius <= 0 || radius > 52) return;
        var explosion = new Explosion(level, source, point.x, point.y, point.z, radius, false, Explosion.BlockInteraction.KEEP);
        if (ForgeEventFactory.onExplosionStart(level, explosion)) return;
        var targets = new ArrayList<>(level.getEntities((Entity) null, new AABB(point, point).inflate(radius),
                entity -> matches(entity) && !entity.isRemoved() && entity.position().distanceToSqr(point) <= radius * radius));
        var allowed = new HashSet<>(targets);
        ForgeEventFactory.onExplosionDetonate(level, explosion, targets, radius);
        var seen = new HashSet<Entity>();
        for (Entity entity : targets) {
            if (allowed.contains(entity) && seen.add(entity) && matches(entity) && !entity.isRemoved()
                    && entity.level() == level && entity.position().distanceToSqr(point) <= radius * radius) entity.discard();
        }
    }
}
