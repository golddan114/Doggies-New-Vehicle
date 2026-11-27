package com.liucodex.carriercube;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class CubeGeometry {
    static final float SHIP_SCALE = .85F;
    static final double DECK = 19.90 * SHIP_SCALE;
    static final double HALF_WIDTH = 20.81 * SHIP_SCALE;
    static final double HALF_LENGTH = 66.58 * SHIP_SCALE;
    // Maximum measured horizontal radius across the airframe, gun and nozzle is 6.506 blocks.
    static final double HALF_AIRCRAFT = 6.51;
    static final int LAUNCH_TICKS = 72;
    static final int TAKEOFF_BLEND_TICKS = 16;
    // Blocks per tick at 20 TPS; the rail briefly exceeds the free-flight limit.
    static final double FLIGHT_SPEED = 150.0 / 72;
    private static final double RAIL_EXPONENT = 1.95;
    static final double MIN_JET_CAPTURE_SPEED = .12;
    static final double PARKED = -21.79 * SHIP_SCALE;
    private static final double TAKEOFF_LIFT = .48;
    static final double LAST_RAIL_STEP = (HALF_LENGTH - PARKED) * (1 - railFraction(LAUNCH_TICKS - 1));
    static final double TAKEOFF_SPEED = Math.hypot(LAST_RAIL_STEP, TAKEOFF_LIFT);

    private static double railFraction(int tick) {
        // Integrate velocity proportional to exp(k*t)-1, then normalize to the authored rail length.
        double x = RAIL_EXPONENT * tick / LAUNCH_TICKS;
        return (Math.expm1(x) - x) / (Math.expm1(RAIL_EXPONENT) - RAIL_EXPONENT);
    }

    static float cardinal(float yaw) {
        return Math.floorMod(Math.round(yaw / 90), 4) * 90;
    }

    static double alignedHullY(double hullY) {
        return Math.floor(hullY + DECK + .5) - DECK;
    }

    static Vec3 forward(float yaw) {
        if (yaw % 90 != 0) return Vec3.directionFromRotation(0, yaw);
        return switch (Math.floorMod(Math.round(yaw / 90), 4)) {
            case 0 -> new Vec3(0, 0, 1);
            case 1 -> new Vec3(-1, 0, 0);
            case 2 -> new Vec3(0, 0, -1);
            default -> new Vec3(1, 0, 0);
        };
    }

    static Vec3 rail(Vec3 hull, Vec3 forward, int tick) {
        return hull.add(forward.scale(PARKED + (HALF_LENGTH - PARKED) * railFraction(tick)))
                .add(0, DECK + .03, 0);
    }

    static Vec3 departureVelocity(Vec3 forward) {
        return forward.scale(LAST_RAIL_STEP).add(0, TAKEOFF_LIFT, 0);
    }

    static double flightLimit(int takeoffTicks) {
        return FLIGHT_SPEED + (TAKEOFF_SPEED - FLIGHT_SPEED) * takeoffTicks / TAKEOFF_BLEND_TICKS;
    }

    static final int CAPTURE_RADIUS = 256;

    static double bowDistance(Vec3 hull, Vec3 forward, Vec3 aircraft) {
        return aircraft.distanceTo(rail(hull, forward, LAUNCH_TICKS));
    }

    static boolean canCapture(Vec3 hull, Vec3 forward, Vec3 aircraft) {
        return bowDistance(hull, forward, aircraft) <= CAPTURE_RADIUS;
    }

    static AABB aircraftEnvelope(Vec3 bottom) {
        // Includes the seated player and the aircraft when rolling or pitching.
        double extent = HALF_AIRCRAFT + .3;
        return new AABB(bottom.x - extent, bottom.y - .25, bottom.z - extent,
                        bottom.x + extent, bottom.y + 4.25, bottom.z + extent);
    }

    static AABB aircraftSweep(Vec3 from, Vec3 to) {
        return aircraftEnvelope(from).minmax(aircraftEnvelope(to));
    }

    static AABB headingEnvelope(Vec3 bottom,float yaw) {
        double angle=Math.toRadians(yaw);
        double x=Math.abs(Math.cos(angle))*HALF_WIDTH + Math.abs(Math.sin(angle))*HALF_LENGTH;
        double z=Math.abs(Math.sin(angle))*HALF_WIDTH + Math.abs(Math.cos(angle))*HALF_LENGTH;
        return new AABB(bottom.x-x,bottom.y,bottom.z-z,bottom.x+x,bottom.y+28.1*SHIP_SCALE,bottom.z+z);
    }

    static AABB hullEnvelope(Vec3 bottom) {
        // Outer bound of the rotating hull at intermediate headings, including crew.
        double extent = Math.hypot(HALF_WIDTH, HALF_LENGTH);
        return new AABB(bottom.x - extent, bottom.y, bottom.z - extent,
                        bottom.x + extent, bottom.y + 28.1 * SHIP_SCALE, bottom.z + extent);
    }
}
