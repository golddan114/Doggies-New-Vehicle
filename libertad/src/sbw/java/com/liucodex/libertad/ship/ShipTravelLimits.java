package com.liucodex.libertad.ship;

import net.minecraft.world.phys.Vec3;

/** Libertad horizontal limits applied to the result of SBW's native ship engine. */
final class ShipTravelLimits {
    static final double WATER_FORWARD = 32 * 1852.0 / 3600 / 20;

    private ShipTravelLimits() {}

    static Vec3 limit(Vec3 movement, float yaw, boolean afloat) {
        double direction = Math.toRadians(yaw);
        double forward = movement.x * -Math.sin(direction) + movement.z * Math.cos(direction);
        double maximum = forward < 0 ? .1 : afloat ? WATER_FORWARD : .4;
        double speed = movement.horizontalDistance();
        if (speed <= maximum) return movement;
        return new Vec3(movement.x * maximum / speed, movement.y, movement.z * maximum / speed);
    }
}
