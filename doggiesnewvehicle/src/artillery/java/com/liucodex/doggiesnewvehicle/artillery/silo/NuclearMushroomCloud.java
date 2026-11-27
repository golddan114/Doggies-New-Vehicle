package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Native smoke follows committed terrain progress; no independent damage clock or effect entity. */
final class NuclearMushroomCloud {
    private static final int VIEW_DISTANCE = 160;

    private NuclearMushroomCloud() { }

    static void spawn(ServerLevel level, Vec3 center) {
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(center) > VIEW_DISTANCE * VIEW_DISTANCE) continue;
            smoke(level, viewer, center.add(0, 3, 0), 24, 2.1, 2.6);
            smoke(level, viewer, center.add(0, 8, 0), 18, 2.1, 2.6);
        }
    }

    static void advance(ServerLevel level, Vec3 center, double front, int radius) {
        double fraction = Math.min(1, front / radius), height = 8 + 19 * fraction;
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(center) > VIEW_DISTANCE * VIEW_DISTANCE) continue;
            // At most one ring per two committed horizontal blocks; the shared block budget also bounds emitters.
            for (int index = 0; index < 24; index++) {
                double angle = 2 * Math.PI * index / 24;
                smoke(level, viewer, center.add(front * Math.cos(angle), 1, front * Math.sin(angle)), 2, .8, .8);
            }
            smoke(level, viewer, center.add(0, height / 2, 0), 8, 1.8, 3);
            for (int index = 0; index < 6; index++) {
                double angle = 2 * Math.PI * index / 6, width = 2 + 6 * fraction;
                smoke(level, viewer, center.add(width * Math.cos(angle), height, width * Math.sin(angle)), 6, 2.5, 1.8);
            }
        }
    }

    private static void smoke(ServerLevel level, ServerPlayer viewer, Vec3 point, int count, double width, double height) {
        level.sendParticles(viewer, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, point.x, point.y, point.z,
                count, width, height, width, .012);
    }
}
