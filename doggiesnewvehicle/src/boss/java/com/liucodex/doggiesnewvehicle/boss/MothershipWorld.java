package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import com.atsuishio.superbwarfare.entity.OBBEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import com.liucodex.doggiesnewvehicle.boss.fx.MothershipFx;
import java.util.List;
import net.minecraft.core.BlockPos;
import com.liucodex.doggiesnewvehicle.entity.DnvMothershipEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Loaded-world queries only. The owning server entity supplies every decision and state value. */
public final class MothershipWorld {
    private MothershipWorld() { }
    public record Trace(Vec3 end, boolean impact) { }

    public static AABB envelope(Vec3 bottom, double yaw) {
        var dimensions = MothershipRig.get().dimensions;
        double[] half = MothershipNavigation.halfExtents(dimensions.x(), dimensions.z(), yaw);
        return new AABB(bottom.x-half[0], bottom.y, bottom.z-half[1], bottom.x+half[0], bottom.y+dimensions.y()+.25, bottom.z+half[1]);
    }

    public static boolean loaded(ServerLevel level, AABB box) {
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)
                || box.getXsize() > 256 || box.getZsize() > 256) return false;
        for (int x = (int)Math.floor(box.minX)>>4; x <= (int)Math.floor(box.maxX)>>4; x++) {
            for (int z = (int)Math.floor(box.minZ)>>4; z <= (int)Math.floor(box.maxZ)>>4; z++) {
                if (!level.hasChunk(x,z)) return false;
            }
        }
        return true;
    }

    public static boolean withinWorld(ServerLevel level, AABB box) {
        return box.minY >= level.getMinBuildHeight() && box.maxY <= level.getMaxBuildHeight()-4
                && level.getWorldBorder().isWithinBounds(box) && loaded(level,box);
    }

    public static boolean safePose(Entity ship, Vec3 bottom, double yaw) {
        if (!(ship.level() instanceof ServerLevel level)) return false;
        var boxes=ship instanceof DnvMothershipEntity mother?mother.collisionPose(bottom,yaw):MothershipHull.pose(bottom,yaw,null);
        AABB box=MothershipHull.bounds(boxes);
        if(!withinWorld(level,box))return false;
        for(AABB query:MothershipHull.scanBounds(boxes))for(var shape:level.getBlockCollisions(ship,query))
            for(AABB part:shape.toAabbs())if(MothershipHull.intersects(boxes,part))return false;
        for(var shape:level.getEntityCollisions(ship,box))
            for(AABB part:shape.toAabbs())if(MothershipHull.intersects(boxes,part))return false;
        return true;
    }

    /** Wreck descent stops at terrain; occupants/aircraft are handled by native OBB support. -1 is unavailable terrain. */
    public static int wreckPose(Entity ship, Vec3 bottom, double pitch, double roll) {
        return wreckPose(ship, bottom, pitch, roll, null);
    }

    public static int wreckPose(Entity ship, Vec3 bottom, double pitch, double roll, List<AABB> candidates) {
        if (!(ship.level() instanceof ServerLevel level) || !(ship instanceof DnvMothershipEntity mother)) return -1;
        var boxes=mother.collisionPose(bottom,ship.getYRot(),pitch,roll);
        AABB bounds=MothershipHull.bounds(boxes);
        if(!loaded(level,bounds)||!level.getWorldBorder().isWithinBounds(bounds))return -1;
        if(bounds.minY<=level.getMinBuildHeight())return -1;
        if(candidates!=null) {
            for(AABB part:candidates)if(MothershipHull.intersects(boxes,part))return 0;
            return 1;
        }
        for(AABB query:MothershipHull.scanBounds(boxes))for(var shape:level.getBlockCollisions(ship,query))
            for(AABB part:shape.toAabbs())if(MothershipHull.intersects(boxes,part))return 0;
        return 1;
    }

    /** One synchronous descent owns the candidates. No terrain writes occur before its last sample. */
    public static List<AABB> wreckCandidates(Entity ship, Vec3 next, double pitch, double roll) {
        if (!(ship.level() instanceof ServerLevel level) || !(ship instanceof DnvMothershipEntity mother)) return null;
        Vec3 from=ship.position(),delta=next.subtract(from);
        var boxes=mother.collisionPose(from,ship.getYRot(),ship.getXRot(),mother.getRoll());
        double angle=Math.toRadians(Math.abs(pitch-ship.getXRot())+Math.abs(roll-mother.getRoll()));
        if(!Double.isFinite(angle)||angle>Math.PI)return null;
        var regions=new java.util.ArrayList<AABB>();
        for(OBB box:boxes) {
            AABB bounds=OBB.getWorldAABB(box);
            double radius=0;
            for(double x:new double[]{bounds.minX,bounds.maxX})for(double y:new double[]{bounds.minY,bounds.maxY})
                for(double z:new double[]{bounds.minZ,bounds.maxZ})radius=Math.max(radius,from.distanceTo(new Vec3(x,y,z)));
            AABB query=bounds.expandTowards(delta).inflate(2*radius*Math.sin(angle/2)+1e-6);
            if(!loaded(level,query))return null;
            regions.add(query);
        }
        var result=new java.util.LinkedHashSet<AABB>();
        for(AABB query:regions)for(var shape:level.getBlockCollisions(ship,query))result.addAll(shape.toAabbs());
        return List.copyOf(result);
    }
    public static double wreckLandingY(Entity ship) {
        if(!(ship.level() instanceof ServerLevel level)||!(ship instanceof DnvMothershipEntity mother))return Double.NaN;
        double ground=groundBelow(level,ship.position(),ship.getYRot());
        return ground-MothershipHull.bounds(mother.collisionPose(Vec3.ZERO,ship.getYRot(),12,8)).minY-.02;
    }

    /** Validate the compound at destination and midpoint, including rotation and newly placed blocks. */
    public static boolean safeStep(Entity ship, Vec3 bottom, double yaw, boolean fullCheck) {
        // The largest permitted step is .05 blocks / .15 degrees. A midpoint catches thin obstacles
        // on moving tips; the complete compound at the destination also catches rotation inside old AABBs.
        if(!safePose(ship,bottom,yaw))return false;
        if(bottom.distanceToSqr(ship.position())<1e-10&&Math.abs(yaw-ship.getYRot())<1e-6)return true;
        return safePose(ship,ship.position().add(bottom.subtract(ship.position()).scale(.5)),
                ship.getYRot()+MothershipRig.wrap(yaw-ship.getYRot())*.5);
    }

    /** Nine footprint samples; a tall roof never updates the encounter datum. NaN means stop. */
    public static double groundBelow(ServerLevel level,Vec3 bottom,double yaw) {
        AABB area=envelope(bottom,yaw);
        if (!loaded(level,area)) return Double.NaN;
        double highest=level.getMinBuildHeight();
        for(double x:new double[]{area.minX,(area.minX+area.maxX)/2,area.maxX}) {
            for(double z:new double[]{area.minZ,(area.minZ+area.maxZ)/2,area.maxZ}) {
                highest=Math.max(highest,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(int)Math.floor(x),(int)Math.floor(z)));
            }
        }
        return highest;
    }

    /** Block-only ray. Sample its corridor before clipping so unloaded chunks never become tickets. */
    public static Trace trace(Entity ship,Vec3 from,Vec3 requested) {
        if (!(ship.level() instanceof ServerLevel level)) return new Trace(from,false);
        Vec3 delta=requested.subtract(from);
        double length=Math.min(MothershipFx.MAX_LENGTH,delta.length());
        if(!Double.isFinite(length)||length<1e-6)return new Trace(from,false);
        Vec3 direction=delta.normalize(), end=from;
        int steps=(int)Math.ceil(length*2);
        for(int i=0;i<=steps;i++) {
            Vec3 point=from.add(direction.scale(length*i/steps));
            BlockPos block=BlockPos.containing(point);
            if(point.y<level.getMinBuildHeight()||point.y>=level.getMaxBuildHeight()
                    ||!level.getWorldBorder().isWithinBounds(block)||!level.hasChunk(block.getX()>>4,block.getZ()>>4))break;
            // A short diagonal step can cross two chunk edges with an unloaded sliver in between.
            BlockPos previous=BlockPos.containing(end);
            int oldX=previous.getX()>>4,oldZ=previous.getZ()>>4;
            if(oldX!=(block.getX()>>4)&&oldZ!=(block.getZ()>>4)) {
                double tx=(((oldX+(point.x>end.x?1:0))*16D)-end.x)/(point.x-end.x);
                double tz=(((oldZ+(point.z>end.z?1:0))*16D)-end.z)/(point.z-end.z);
                if(Math.abs(tx-tz)>1e-9) {
                    Vec3 middle=end.add(point.subtract(end).scale((tx+tz)*.5));
                    BlockPos crossed=BlockPos.containing(middle);
                    if(!level.hasChunk(crossed.getX()>>4,crossed.getZ()>>4))break;
                }
            }
            end=point;
        }
        if(end.distanceToSqr(from)<1e-10)return new Trace(from,false);
        var block=level.clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,ship));
        return new Trace(block.getType()==HitResult.Type.MISS?end:block.getLocation(),block.getType()!=HitResult.Type.MISS);
    }

    /** Broad query once per damage pulse; precise OBB/AABB intersections do not truncate the beam. */
    public static List<Entity> intersected(Entity ship,Vec3 from,Vec3 end) {
        if(!(ship.level() instanceof ServerLevel level)||from.distanceToSqr(end)<1e-8)return List.of();
        return level.getEntities(ship,new AABB(from,end).inflate(.05),
                e->e.isAlive()&&!e.isSpectator()&&rayIntersects(e,from,end));
    }

    public static boolean rayIntersects(Entity entity,Vec3 from,Vec3 end) {
        if(entity instanceof OBBEntity shape && !shape.enableAABB()) {
            var a=OBB.vec3ToVector3d(from);var b=OBB.vec3ToVector3d(end);
            return shape.getOBBs().stream().anyMatch(box->box.contains(from)||box.clip(a,b).isPresent());
        }
        return entity.getBoundingBox().contains(from)||entity.getBoundingBox().clip(from,end).isPresent();
    }

    public static boolean overhead(Entity ship,Vec3 point,double top) {
        if (!(ship.level() instanceof ServerLevel level) || top<=point.y || top>=level.getMaxBuildHeight()-4) return false;
        AABB corridor=new AABB(point.x-3.5,point.y+.1,point.z-4,point.x+3.5,top,point.z+4);
        return loaded(level,corridor) && level.getWorldBorder().isWithinBounds(corridor)
                && !level.getBlockCollisions(ship,corridor).iterator().hasNext();
    }

    public static boolean clearDropColumn(Entity ship,AABB body,double top) {
        if(!(ship.level() instanceof ServerLevel level)||top<=body.maxY||top>=level.getMaxBuildHeight()-4)return false;
        AABB column=new AABB(body.minX,body.maxY,body.minZ,body.maxX,top,body.maxZ);
        return loaded(level,column)&&level.getWorldBorder().isWithinBounds(column)&&!level.getBlockCollisions(ship,column).iterator().hasNext();
    }
}
