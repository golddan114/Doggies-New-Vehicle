package com.liucodex.libertad.ship;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/** Client-side smoke anchored to the two outlets measured on the authored ship mesh. */
final class FunnelExhaust {
    // The fixed SBW chase camera sits beyond the normal 32-block particle admission distance.
    static final SimpleParticleType SMOKE = ParticleTypes.CAMPFIRE_COSY_SMOKE;
    private static final Vec3 FORE = new Vec3(0, 12.24, 12.85);
    private static final Vec3 AFT = new Vec3(0, 12.24, -23.37);

    private FunnelExhaust() {}

    static void tick(LibertadEntity ship) {
        if (!ship.level().isClientSide || ship.isRemoved() || ship.isWreck()) return;
        double yaw = Math.toRadians(ship.getYRot());
        double speed = forwardMotion(ship.getX() - ship.xo, ship.getZ() - ship.zo, yaw);
        int interval = interval(speed);
        if (interval == 0 || (ship.tickCount + ship.getId()) % interval != 0) return;

        var pose = ship.getVehicleTransform(1);
        double drift = Math.min(.065, speed * .13);
        double vx = Math.sin(yaw) * drift;
        double vz = -Math.cos(yaw) * drift;
        puff(ship, ShipModel.point(pose, FORE), vx, vz);
        puff(ship, ShipModel.point(pose, AFT), vx, vz);
    }

    static double forwardMotion(double dx, double dz, double yaw) {
        if (!(dx * dx + dz * dz <= 1)) return 0; // Ignore jumps larger than a normal travel tick.
        return -dx * Math.sin(yaw) + dz * Math.cos(yaw);
    }

    static int interval(double speed) {
        if (!(speed >= .025)) return 0;
        if (speed >= .45) return 3;
        return speed >= .20 ? 4 : 6;
    }

    private static void puff(LibertadEntity ship, Vec3 origin, double vx, double vz) {
        var random = ship.getRandom();
        ship.level().addParticle(SMOKE,
                origin.x + (random.nextDouble() - .5) * .32, origin.y, origin.z + (random.nextDouble() - .5) * .32,
                vx, .08 + random.nextDouble() * .04, vz);
    }
}
