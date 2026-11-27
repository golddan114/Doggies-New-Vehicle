package com.liucodex.doggiesnewvehicle.boss;

import com.atsuishio.superbwarfare.entity.projectile.JavelinMissileEntity;
import com.liucodex.doggiesnewvehicle.compat.DnvVlsFlight;
import net.minecraft.world.entity.Entity;

/** Boss launch geometry; the projectile owns ejection and guidance after spawn. */
public final class DnvBossVlsGuidance {
    // Covers the native rendered missile's 4.21-block radius plus moving-vehicle clearance.
    public static final double CLEARANCE = 5.5;

    private DnvBossVlsGuidance() {}

    /** Reserve the cold-launch column and initial pitch-over space before consuming a round. */
    public static boolean clearLaunch(Entity source, Entity target, net.minecraft.world.phys.Vec3 muzzle,
            java.util.UUID geometryOwner) {
        if (!(source.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        if (!com.liucodex.doggiesnewvehicle.compat.DnvTargetingBudget.admitGeometryCheck(level, geometryOwner)) return false;
        double ceiling = Math.max(source.getBoundingBox().maxY + CLEARANCE + 24,
                Math.max(muzzle.y + 3, target.getBoundingBox().maxY + 2));
        var top = new net.minecraft.world.phys.Vec3(muzzle.x, ceiling, muzzle.z);
        var corridor = new net.minecraft.world.phys.AABB(muzzle, top).inflate(.65, 0, .65);
        if (!level.hasChunksAt(net.minecraft.core.BlockPos.containing(corridor.minX,corridor.minY,corridor.minZ),
                net.minecraft.core.BlockPos.containing(corridor.maxX,corridor.maxY,corridor.maxZ))) return false;
        // The intended enemy is an impact destination; other hulls (including the summoning SES) are obstructions.
        if (!level.getEntities(source, corridor, entity -> entity != target && entity.isAlive()
                && !entity.isSpectator() && entity.isPickable()).isEmpty()) return false;
        for (double[] offset : new double[][]{{0,0},{-.6,-.6},{-.6,.6},{.6,-.6},{.6,.6}}) {
            var start=muzzle.add(offset[0],0,offset[1]);
            var end=top.add(offset[0],0,offset[1]);
            if (level.clip(new net.minecraft.world.level.ClipContext(start,end,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,source)).getType()
                    != net.minecraft.world.phys.HitResult.Type.MISS) return false;
        }
        return true;
    }

    public static void arm(JavelinMissileEntity missile, Entity target, double hullTop, float launchSpeed) {
        DnvVlsFlight.armEjection(missile, target, hullTop + CLEARANCE, launchSpeed);
    }
}
