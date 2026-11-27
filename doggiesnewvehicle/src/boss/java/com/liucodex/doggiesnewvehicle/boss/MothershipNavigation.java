package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.V;
import java.util.function.Predicate;

/** Bounded local steering with side persistence; never a world pathfinder or a chunk loader. */
public final class MothershipNavigation {
    private int side;
    private long detourUntil;

    /** At most five probes. Retain the chosen side for three seconds to avoid alternating at a wall. */
    public V choose(long now, V desired, Predicate<V> clear) {
        double length = desired.length();
        if (length < 1e-7) return new V(0, 0, 0);
        V direct = desired.scale(1 / length);
        if (side == 0 || now >= detourUntil) {
            if (clear.test(direct)) { side = 0; return direct; }
        }
        int preferred = side == 0 ? 1 : side;
        for (double angle : new double[]{45 * preferred, 90 * preferred, -45 * preferred, -90 * preferred}) {
            V candidate = MothershipRig.yaw(direct, angle);
            if (!clear.test(candidate)) continue;
            int chosen = angle > 0 ? 1 : -1;
            if (chosen != side || now >= detourUntil) detourUntil = now + 60;
            side = chosen;
            return candidate;
        }
        return new V(0, 0, 0);
    }

    /** Axis-aligned envelope of the rotated rigid hull, with a small margin for moving guns. */
    public static double[] halfExtents(double width, double length, double yaw) {
        double r = Math.toRadians(yaw), c = Math.abs(Math.cos(r)), s = Math.abs(Math.sin(r));
        return new double[]{(width * c + length * s) / 2 + .25, (width * s + length * c) / 2 + .25};
    }
}
