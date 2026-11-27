package com.liucodex.libertad.ship;

import com.liucodex.libertad.naval.MountGeometry.Point;
import com.liucodex.libertad.naval.ShellRange;
import com.liucodex.libertad.ship.MainBatteryView.Endpoint;
import com.liucodex.libertad.ship.MainBatteryView.Marker;
import java.util.function.BiFunction;
import net.minecraft.world.phys.Vec3;

/** Air-path projection of the pinned SBW shell step up to its first contact or range limit. */
final class MainGunPrediction {
    private MainGunPrediction() {}

    static Marker trace(Vec3 muzzle, Vec3 velocity, float gravity, BiFunction<Vec3, Vec3, Marker> segment) {
        return trace(muzzle, velocity, gravity, ShellRange.MAIN, segment);
    }

    static Marker trace(Vec3 muzzle, Vec3 velocity, float gravity, double distance, BiFunction<Vec3, Vec3, Marker> segment) {
        var range = new ShellRange(point(muzzle), distance, ShellRange.LIFETIME);
        Vec3 start = muzzle;
        for (int tick = 0; tick < ShellRange.LIFETIME; tick++) {
            var step = range.next(point(start), point(velocity), tick);
            if (!step.trace()) return new Marker(start, Endpoint.RANGE);
            var endpoint = step.endpoint();
            Vec3 end = new Vec3(endpoint.x(), endpoint.y(), endpoint.z());
            var contact = segment.apply(start, end);
            if (contact != null) return contact;
            if (step.discardIfMiss()) return new Marker(end, Endpoint.RANGE);
            start = end;
            velocity = velocity.add(0, -gravity, 0);
        }
        return new Marker(start, Endpoint.RANGE);
    }

    private static Point point(Vec3 vec) { return new Point(vec.x, vec.y, vec.z); }
}
