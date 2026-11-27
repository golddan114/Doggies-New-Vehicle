package com.liucodex.doggiesnewvehicle.boss;

import net.minecraft.world.phys.Vec3;

/** Exact dry-air ProjectileEntity stepping: move at v, then subtract gravity, without drag. */
public final class MothershipAutocannonAim {
    public static final float SPEED = 6F, GRAVITY = .05F;
    public static final int LIFE = 40;
    private MothershipAutocannonAim() { }

    public static Vec3 point(Vec3 muzzle, Vec3 center, Vec3 motion) {
        // Horizontal lead is bounded; a jumping mob's instantaneous Y velocity is not sustained flight.
        Vec3 velocity = new Vec3(motion.x, 0, motion.z);
        if (velocity.lengthSqr() > 1) velocity = velocity.normalize();
        double time = Math.min(LIFE, center.distanceTo(muzzle) / SPEED);
        Vec3 aim = center;
        for (int i = 0; i < 8; i++) {
            int ticks = (int) Math.floor(time);
            double drop = GRAVITY * (ticks * (ticks - 1) * .5 + ticks * (time - ticks));
            aim = center.add(velocity.scale(time)).add(0, drop, 0);
            time = Math.min(LIFE, aim.distanceTo(muzzle) / SPEED);
        }
        return aim;
    }
}
