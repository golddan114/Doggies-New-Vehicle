package com.liucodex.carriercube;

import net.minecraft.world.phys.Vec3;

final class CubeHoverFlight {
    private static final double HORIZONTAL_SPEED = 12.0 / 72;
    private static final double VERTICAL_SPEED = 8.0 / 72;
    private static final double ACCELERATION = .055;

    private Vec3 velocity;
    private short keys;
    private float turnRate;

    CubeHoverFlight(Vec3 velocity) {
        this.velocity = velocity;
    }

    void input(short keys) {
        this.keys = keys;
    }

    Vec3 step(float yaw) {
        Vec3 forward = heading(yaw);
        Vec3 horizontal = forward.scale(axis(4, 8));
        Vec3 target = horizontal.normalize().scale(HORIZONTAL_SPEED)
                .add(0, axis(16, 32) * VERTICAL_SPEED, 0);
        Vec3 change = target.subtract(velocity);
        velocity = change.lengthSqr() <= ACCELERATION * ACCELERATION
                ? target : velocity.add(change.normalize().scale(ACCELERATION));
        return velocity;
    }

    float turn() {
        turnRate = net.minecraft.util.Mth.approach(turnRate, 2.2F * axis(2,1), .24F);
        return turnRate;
    }

    Vec3 velocity() {
        return velocity;
    }

    void stop() {
        velocity = Vec3.ZERO;
    }

    static Vec3 heading(float yaw) {
        return Vec3.directionFromRotation(0, yaw);
    }

    private int axis(int positive, int negative) {
        // SBW VehicleMovementMessage uses these bits for the ordered pilot seat.
        return Integer.compare(keys & positive, 0) - Integer.compare(keys & negative, 0);
    }
}
