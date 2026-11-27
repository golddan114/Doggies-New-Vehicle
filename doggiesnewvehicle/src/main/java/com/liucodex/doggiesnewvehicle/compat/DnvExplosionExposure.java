package com.liucodex.doggiesnewvehicle.compat;

import com.atsuishio.superbwarfare.entity.OBBEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import com.liucodex.doggiesnewvehicle.api.combat.DnvHullTarget;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Bounded explosion exposure on the authored hull, independent of the enclosing AABB's volume. */
public final class DnvExplosionExposure {
    public static final int MAX_BOXES = 32;
    public static final int MAX_RAYS = MAX_BOXES * 7;
    private DnvExplosionExposure() { }

    /** Negative means retain vanilla behavior, including every ordinary entity and third-party vehicle. */
    public static float exposure(Vec3 origin, Entity target) {
        if (!(target instanceof DnvHullTarget) || !(target instanceof OBBEntity hull) || hull.enableAABB()) return -1;
        if (!finite(origin)) return 0;
        Level level = target.level();
        return sample(hull.getOBBs(), point -> loadedRay(level, origin, point)
                && level.clip(new ClipContext(point, origin, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, target)).getType() == HitResult.Type.MISS);
    }

    /** Centre and six face centres per posed OBB; no block-density-dependent sample count. */
    public static float sample(List<OBB> boxes, Predicate<Vec3> visible) {
        int count = Math.min(MAX_BOXES, boxes.size()), hits = 0, total = 0;
        Vector3d offset = new Vector3d();
        for (int i = 0; i < count; i++) {
            OBB box = boxes.get((int)((long)i * boxes.size() / count));
            Vector3d centre = box.center, half = box.extents();
            var axes = box.getAxes();
            if (!centre.isFinite() || !half.isFinite()) continue;
            Vec3 point = new Vec3(centre.x, centre.y, centre.z);
            if (visible.test(point)) hits++;
            total++;
            for (int axis = 0; axis < 3; axis++) for (int sign = -1; sign <= 1; sign += 2) {
                axes[axis].mul(sign * half.get(axis), offset);
                point = new Vec3(centre.x + offset.x, centre.y + offset.y, centre.z + offset.z);
                if (finite(point) && visible.test(point)) hits++;
                total++;
            }
        }
        return total == 0 ? 0 : hits / (float)total;
    }

    /** Chunk DDA before clip: an explosion never acquires chunks along a long hull ray. */
    static boolean loadedRay(Level level, Vec3 from, Vec3 to) {
        if (!finite(from) || !finite(to) || from.distanceToSqr(to) > 1024D * 1024D) return false;
        int x = (int)Math.floor(from.x) >> 4, z = (int)Math.floor(from.z) >> 4;
        int endX = (int)Math.floor(to.x) >> 4, endZ = (int)Math.floor(to.z) >> 4;
        double dx = to.x - from.x, dz = to.z - from.z;
        int sx = Double.compare(dx, 0), sz = Double.compare(dz, 0);
        double tx = sx == 0 ? Double.POSITIVE_INFINITY : (((x + (sx > 0 ? 1 : 0)) * 16D) - from.x) / dx;
        double tz = sz == 0 ? Double.POSITIVE_INFINITY : (((z + (sz > 0 ? 1 : 0)) * 16D) - from.z) / dz;
        double stepX = sx == 0 ? Double.POSITIVE_INFINITY : 16D / Math.abs(dx);
        double stepZ = sz == 0 ? Double.POSITIVE_INFINITY : 16D / Math.abs(dz);
        for (int n = 0; n < 132; n++) {
            if (!level.hasChunk(x, z)) return false;
            if (x == endX && z == endZ) return true;
            if (Math.abs(tx - tz) < 1e-10) {
                if (!level.hasChunk(x + sx, z) || !level.hasChunk(x, z + sz)) return false;
                x += sx; z += sz; tx += stepX; tz += stepZ;
            } else if (tx < tz) { x += sx; tx += stepX; }
            else { z += sz; tz += stepZ; }
        }
        return false;
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
