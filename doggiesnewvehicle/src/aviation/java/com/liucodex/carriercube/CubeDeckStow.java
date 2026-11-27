package com.liucodex.carriercube;

import net.minecraft.world.phys.Vec3;

/** Reverse of the authored 24 fps deck sequence, sampled on Minecraft's 20 Hz clock. */
final class CubeDeckStow {
    static final double DROP = 7.796 * CubeGeometry.SHIP_SCALE;
    static final double TAXI = 19.67 * CubeGeometry.SHIP_SCALE;
    static final int LOWER = 55;
    static final int HOLD = 3;
    static final int TAXI_END = LOWER + HOLD + 53;
    static final int TOTAL = TAXI_END + 45;

    // Match the animator's two acceleration ramps, central constant speed and two braking ramps.
    static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        if (t > .5) return 1 - ease(1 - t);
        if (t < 1D / 6) return 9 * t * t * t;
        if (t < 1D / 3) {
            double u = 1D / 3 - t;
            return 1.5 * t - .25 + 9 * u * u * u;
        }
        return 1.5 * t - .25;
    }

    static double platformHeight(int tick) {
        return -DROP * (ease((double)tick / LOWER) - ease((double)(tick - TAXI_END) / (TOTAL - TAXI_END)));
    }

    static double planeHeight(int tick) {
        return -DROP * ease((double)tick / LOWER);
    }

    static double forwardOffset(int tick) {
        return TAXI * ease((double)(tick - LOWER - HOLD) / (TAXI_END - LOWER - HOLD));
    }

    static Vec3 planePosition(Vec3 hull, Vec3 forward, int frame) {
        return CubeGeometry.rail(hull,forward,0).add(forward.scale(forwardOffset(frame))).add(0,planeHeight(frame),0);
    }

    private CubeDeckStow() {}
}
