package com.liucodex.doggiesnewvehicle.api.combat;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Optional native-shot decoration. Ammunition and world admission remain native owners. */
public interface DnvNativeShotVisual {
    void prepareShotVisual(Entity projectile, Vec3 origin);

    static void prepare(Entity projectile, ShootParameters shot) {
        if (shot.data.item instanceof DnvNativeShotVisual visual)
            visual.prepareShotVisual(projectile, shot.shootPosition);
    }

    interface Trail {
        void dnv$startDsx1Trail(Vec3 origin, TrailSink sink);
    }

    @FunctionalInterface interface TrailSink {
        void send(ServerLevel level, UUID projectile, int sequence, Vec3 from, Vec3 to);
    }

    static boolean validSegment(Vec3 from, Vec3 to) {
        return finite(from) && finite(to) && from.distanceToSqr(to) <= 512 * 512;
    }

    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
                && Math.abs(v.x) <= 3e7 && Math.abs(v.y) <= 3e7 && Math.abs(v.z) <= 3e7;
    }
}
