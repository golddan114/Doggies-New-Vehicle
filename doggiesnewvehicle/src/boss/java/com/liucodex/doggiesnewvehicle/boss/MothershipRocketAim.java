package com.liucodex.doggiesnewvehicle.boss;

import net.minecraft.world.phys.Vec3;

/** Static gravity compensation for the exact SBW RPG; moving-target interception is deferred. */
public final class MothershipRocketAim {
    private static final int LIFE = 80;
    private static final double[] TRAVEL = new double[LIFE + 1];
    private static final double[] DROP = new double[LIFE + 1];

    static {
        double speed = 2.5, fall = 0;
        for (int tick = 1; tick <= LIFE; tick++) {
            TRAVEL[tick] = TRAVEL[tick - 1] + speed;
            DROP[tick] = DROP[tick - 1] + fall;
            fall += (double) .015F;
            if (tick > 2) { speed *= 1.03; fall *= 1.03; }
        }
    }

    private MothershipRocketAim() { }

    public static Vec3 direction(Vec3 muzzle, Vec3 target) {
        if (!finite(muzzle) || !finite(target)) return Vec3.ZERO;
        Vec3 relative = target.subtract(muzzle);
        if (relative.lengthSqr() < 1E-8) return Vec3.ZERO;
        double drop = dropAt(relative.length());
        Vec3 corrected = target.add(0, drop, 0);
        // A raised aim point travels slightly farther. One refinement avoids overshooting steep shots.
        drop = dropAt(corrected.distanceTo(muzzle));
        return target.add(0, drop, 0).subtract(muzzle).normalize();
    }

    private static double dropAt(double distance) {
        if (distance >= TRAVEL[LIFE]) return DROP[LIFE];
        int low = 0, high = LIFE;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (TRAVEL[middle] < distance) low = middle; else high = middle;
        }
        double fraction = (distance - TRAVEL[low]) / (TRAVEL[high] - TRAVEL[low]);
        return DROP[low] + fraction * (DROP[high] - DROP[low]);
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x)
                && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
