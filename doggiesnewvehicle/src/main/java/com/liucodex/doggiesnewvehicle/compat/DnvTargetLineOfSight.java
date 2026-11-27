package com.liucodex.doggiesnewvehicle.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Small ordered body-point sampler shared by DNV boss weapons. */
public final class DnvTargetLineOfSight {
    private static final double POINT_EPSILON_SQR = 1.0E-8D;

    private DnvTargetLineOfSight() {
    }

    public static Optional<Vec3> clearAimPoint(
            Level level,
            Entity source,
            Vec3 from,
            Entity target
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(target, "target");
        for (Vec3 aimPoint : orderedAimPoints(
                target.getBoundingBox(),
                target.getEyeY()
        )) {
            if (level.clip(new ClipContext(
                    from,
                    aimPoint,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    source
            )).getType() == HitResult.Type.MISS) {
                return Optional.of(aimPoint);
            }
        }
        return Optional.empty();
    }

    public static List<Vec3> orderedAimPoints(AABB bounds, double eyeY) {
        Objects.requireNonNull(bounds, "bounds");
        double height = Math.max(0.01D, bounds.getYsize());
        double x = (bounds.minX + bounds.maxX) * 0.5D;
        double z = (bounds.minZ + bounds.maxZ) * 0.5D;
        double centerY = (bounds.minY + bounds.maxY) * 0.5D;
        double upperFallback = bounds.minY + height * 0.82D;
        double upperY = Double.isFinite(eyeY)
                ? Math.max(
                        bounds.minY + height * 0.62D,
                        Math.min(bounds.maxY - height * 0.05D, eyeY)
                )
                : upperFallback;
        double lowerY = bounds.minY + height * 0.28D;

        List<Vec3> points = new ArrayList<>(3);
        addDistinct(points, new Vec3(x, centerY, z));
        addDistinct(points, new Vec3(x, upperY, z));
        addDistinct(points, new Vec3(x, lowerY, z));
        return List.copyOf(points);
    }

    private static void addDistinct(List<Vec3> points, Vec3 candidate) {
        if (points.stream().noneMatch(existing ->
                existing.distanceToSqr(candidate) <= POINT_EPSILON_SQR)) {
            points.add(candidate);
        }
    }
}
