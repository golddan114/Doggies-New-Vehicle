package com.liucodex.doggiesnewvehicle.naval;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-owned wreck motion. The entity remains tracked and saves its descent between ticks. */
public final class Shipwreck {
    private static final String AGE = "DnvShipwreckAge";
    private static final String DETONATED = "DnvShipwreckDetonated";

    private Shipwreck() {}

    public static void playBreakup(ServerLevel level, Vec3 center) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2F, .85F);
    }

    public static void tick(Entity ship, ServerLevel level, boolean sinking) {
        var data = ship.getPersistentData();
        if (data.getBoolean(DETONATED)) {
            ship.discard();
            return;
        }
        int age = Math.min(10000, data.getInt(AGE) + 1);
        data.putInt(AGE, age);
        if (!sinking && age == 1) playBreakup(level, ship.position().add(0, 16, 0));
        double speed = sinking ? Math.min(.34, .035 + age * .0035) : Math.min(1.85, .06 + age * .032);
        double keel = sinking ? -5.19 : 3.845934 * .85;
        Vec3 bottom = ship.position().add(0, keel, 0);
        var hit = level.clip(new ClipContext(bottom.add(0, .05, 0), bottom.add(0, -speed, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ship));
        boolean contact = hit.getType() == HitResult.Type.BLOCK;
        double nextY = contact ? Math.min(ship.getY(), hit.getLocation().y - keel) : ship.getY() - speed;
        ship.setDeltaMovement(Vec3.ZERO);
        ship.setPos(ship.getX(), nextY, ship.getZ());
        if (age % 4 == 0) {
            level.sendParticles(sinking && ship.isUnderWater() ? ParticleTypes.BUBBLE : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    ship.getX(), nextY + 4, ship.getZ(), 8, 5, 2, 9, .04);
        }
        if (contact && age >= 12 || nextY + keel < level.getMinBuildHeight() - 8) {
            data.putBoolean(DETONATED, true);
            Vec3 center = ship.position().add(0, keel + 3, 0);
            Vec3 axis = Vec3.directionFromRotation(0, ship.getYRot());
            for (int i = -3; i <= 3; i++) {
                Vec3 plume = center.add(axis.scale(i * (sinking ? 11 : 8)));
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, plume.x, plume.y, plume.z, 1, 1, 1, 1, 0);
            }
            level.explode(ship, center.x, center.y, center.z, 12F, Level.ExplosionInteraction.NONE);
            ship.discard();
        }
    }
}
