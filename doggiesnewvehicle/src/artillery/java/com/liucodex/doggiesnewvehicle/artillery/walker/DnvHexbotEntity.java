package com.liucodex.doggiesnewvehicle.artillery.walker;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import com.liucodex.doggiesnewvehicle.artillery.walker.HexbotGeometry.Point;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** SBW owns authenticated driving/crew and the single collision move; DNV owns foot placement. */
public final class DnvHexbotEntity extends VehicleEntity {
    public static final double BODY_OFFSET=.4;
    public static final double MAX_SPEED=.25;
    public static final int MAX_TERRAIN_QUERIES=69;
    private static final EntityDataAccessor<CompoundTag> POSE=SynchedEntityData.defineId(DnvHexbotEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private HexbotGait gait;
    private HexbotFrame previousFrame,currentFrame;
    private HexbotRigPose previousRig,rig;
    private int receivedAt;
    private int terrainQueries;
    public DnvHexbotEntity(EntityType<? extends DnvHexbotEntity> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(){super.m_8097_();entityData.define(POSE,new CompoundTag());}
    @Override public boolean isControlledByLocalInstance(){return !level().isClientSide;}
    @Override public boolean canAddPassenger(Entity passenger){
        return passenger instanceof Player&&getPassengers().isEmpty()&&super.m_7310_(passenger);
    }
    @Override public void travel(){
        if(level().isClientSide)return;
        terrainQueries=0;
        boolean driven=isAlive()&&!isWreck()&&getFirstPassenger() instanceof Player p&&p.isAlive()&&!p.isSpectator();
        if(!driven)processInput((short)0);
        double forward=driven?(forwardInputDown()?1:0)-(backInputDown()?1:0):0;
        double sideways=driven?(leftInputDown()?1:0)-(rightInputDown()?1:0):0;
        float desiredYaw=driven?getFirstPassenger().getYRot():getYRot();
        if(!Float.isFinite(desiredYaw))desiredYaw=0;
        if(!Float.isFinite(getYRot()))setYRot(desiredYaw);
        setYRot(Mth.approachDegrees(getYRot(),desiredYaw,5F));
        Point intent=new Point(sideways,0,forward);
        if(intent.length()>0)intent=intent.normalized().rotate(-Math.toRadians(desiredYaw)).scale(MAX_SPEED);
        Vec3 current=getDeltaMovement();
        double vx=current.x+Mth.clamp(intent.x()-current.x,-.0375,.0375),vz=current.z+Mth.clamp(intent.z()-current.z,-.0375,.0375);
        Point body=bodyOrigin();
        if(gait==null)gait=new HexbotGait();
        gait.update(body,-Math.toRadians(getYRot()),new Point(vx,0,vz),new HexbotGait.Ground(){
            @Override public Point find(Point nominal){return groundTarget(nominal);}
            @Override public Point contact(Point planted){return ground(planted);}
        });
        double ground=gait.groundHeight();
        Clearance clearance=chassisClearance(body,vx,vz);
        double target=Double.isFinite(ground)?ground+HexbotGeometry.BODY_HEIGHT:Double.NaN;
        if(Double.isFinite(target)&&Double.isFinite(clearance.height))target=Math.max(target,clearance.height);
        // Raise before pushing the body into a riser. Foot support height alone cannot
        // guarantee clearance for the leading edge of a pitched/rolled chassis.
        if(!clearance.passable||!Double.isFinite(target)||target-body.y()>.04){vx=0;vz=0;}
        if(!clearance.headroom&&Double.isFinite(target))target=Math.min(target,body.y());
        double vy=Double.isFinite(target)?Mth.clamp((target-body.y())*.25,-.25,.18):Math.max(-.6,current.y-.08);
        setDeltaMovement(vx,vy,vz);
        setXRot(Mth.approachDegrees(getXRot(),(float)Mth.clamp(-Math.toDegrees(gait.pitch()),-25,25),2));
        setZRot(Mth.approachDegrees(getRoll(),(float)Mth.clamp(-Math.toDegrees(gait.roll()),-25,25),2));
    }
    private record Clearance(double height,boolean passable,boolean headroom){ }
    private Clearance chassisClearance(Point body,double vx,double vz){
        var obb=getCollisionOBB();
        AABB hull=obb==null?getBoundingBox():OBB.getWorldAABB(obb);
        AABB swept=hull.expandTowards(vx,0,vz);
        if(!loaded(swept.minX-.01,swept.minZ-.01)||!loaded(swept.minX-.01,swept.maxZ+.01)
                ||!loaded(swept.maxX+.01,swept.minZ-.01)||!loaded(swept.maxX+.01,swept.maxZ+.01))
            return new Clearance(Double.NaN,false,false);
        double highest=Double.NEGATIVE_INFINITY;boolean passable=true;
        // Cover the next complete chassis footprint, retaining the vehicle centre so an
        // unloaded edge/void cannot be mistaken for a usable floor.
        double[] xs={swept.minX-.01,body.x(),swept.maxX+.01};
        double[] zs={swept.minZ-.01,body.z(),swept.maxZ+.01};
        for(double x:xs)for(double z:zs){
            Point floor=ground(new Point(x,body.y()-HexbotGeometry.BODY_HEIGHT,z));
            if(floor==null)passable=false;else highest=Math.max(highest,floor.y());
        }
        // A finite block can fall between all nine rays. Read the colliding volume's
        // top as well, otherwise the headroom gate stops us without any height to rise to.
        int localShapes=0;
        for(var shape:level().getBlockCollisions(this,swept)){
            if(++localShapes>128)return new Clearance(Double.NaN,false,false);
            AABB obstacle=shape.bounds();
            if(obstacle.minY<=hull.minY+.04&&obstacle.maxY-hull.minY<=1.25)
                highest=Math.max(highest,obstacle.maxY);
        }
        double height=Double.isFinite(highest)?highest+body.y()-hull.minY+.12:Double.NaN;
        // Keep collision authority in the native mover; this is only a read-only block
        // clearance check. Passengers/other entities must not be treated as a ceiling.
        boolean headroom=!Double.isFinite(height)||!level().getBlockCollisions(this,
                swept.move(0,Math.max(0,height-body.y()),0)).iterator().hasNext();
        return new Clearance(height,passable&&headroom,headroom);
    }
    private Point bodyOrigin(){return new Point(getX(),getY()+BODY_OFFSET,getZ());}
    private boolean loaded(double x,double z){
        BlockPos at=BlockPos.containing(x,getY(),z);
        return level().hasChunkAt(at)&&level().getWorldBorder().isWithinBounds(at);
    }
    private Point ground(Point nominal){
        if(!nominal.finite()||!loaded(nominal.x(),nominal.z()))return null;
        double top=Math.max(nominal.y()+1.6,bodyOrigin().y()+.5),bottom=nominal.y()-2.5;
        if(top>level().getMaxBuildHeight()-1||bottom<level().getMinBuildHeight())return null;
        if(++terrainQueries>MAX_TERRAIN_QUERIES)return null;
        // Exact foot contact queries stay at the foot's X/Z. Alternative scan is only for prospective targets.
        Vec3 start=new Vec3(nominal.x(),top,nominal.z()),end=new Vec3(nominal.x(),bottom,nominal.z());
        var hit=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(hit.getType()!=HitResult.Type.BLOCK||hit.isInside()||hit.getDirection()!=Direction.UP)return null;
        Vec3 p=hit.getLocation();return new Point(p.x,p.y,p.z);
    }
    private Point groundTarget(Point nominal){
        Point main=ground(nominal);
        if(main!=null&&main.y()>=nominal.y()-.24&&main.y()<=nominal.y()+1.5)return main;
        Point best=main;
        double[] xs={Math.floor(nominal.x())-.125,nominal.x(),Math.ceil(nominal.x())+.125};
        double[] zs={Math.floor(nominal.z())-.125,nominal.z(),Math.ceil(nominal.z())+.125};
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)if(x!=1||z!=1){
            Point candidate=ground(new Point(xs[x],nominal.y(),zs[z]));
            if(candidate!=null&&(best==null||candidate.distance(nominal)<best.distance(nominal)))best=candidate;
        }
        return best;
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){
            HexbotFrame next=presentation(1);
            previousRig=rig;
            if(rig==null||!rig.matches(next))rig=HexbotRigPose.solve(next);
            if(previousRig==null)previousRig=rig;
        }
        if(!level().isClientSide&&gait!=null&&!isRemoved()){
            Point origin=bodyOrigin();int[] points=new int[18];
            for(int i=0;i<6;i++){
                Point p=gait.foot(i).point().subtract(origin);
                if(!p.finite()||p.length()>8){gait=null;return;}
                points[i*3]=(int)Math.round(p.x()*4096);points[i*3+1]=(int)Math.round(p.y()*4096);points[i*3+2]=(int)Math.round(p.z()*4096);
            }
            CompoundTag tag=new CompoundTag();tag.putIntArray("Feet",points);
            tag.putDouble("X",origin.x());tag.putDouble("Y",origin.y());tag.putDouble("Z",origin.z());
            tag.putFloat("Yaw",getYRot());tag.putFloat("Pitch",getXRot());tag.putFloat("Roll",getRoll());
            entityData.set(POSE,tag);
        }
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        super.onSyncedDataUpdated(key);
        if(key==POSE&&level().isClientSide){
            CompoundTag tag=entityData.get(POSE);int[] data=tag.getIntArray("Feet");
            if(data.length!=18)return;
            Point origin=new Point(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));
            Point[] points=new Point[6];
            for(int i=0;i<6;i++)points[i]=origin.add(new Point(data[i*3]/4096D,data[i*3+1]/4096D,data[i*3+2]/4096D));
            try{
                HexbotFrame next=new HexbotFrame(origin,tag.getFloat("Yaw"),tag.getFloat("Pitch"),tag.getFloat("Roll"),points);
                previousFrame=currentFrame==null?next:currentFrame;currentFrame=next;receivedAt=tickCount;
            }catch(IllegalArgumentException ignored){previousFrame=currentFrame=null;}
        }
    }
    public HexbotFrame presentation(float partial){
        if(currentFrame!=null)return previousFrame.lerp(currentFrame,(tickCount-receivedAt+partial)/2D);
        Point origin=bodyOrigin();Point[] points=new Point[6];
        for(int i=0;i<6;i++)points[i]=origin.add(HexbotGeometry.rest(i).rotate(-Math.toRadians(getYRot())));
        return new HexbotFrame(origin,getYRot(),getXRot(),getRoll(),points);
    }
    public HexbotRigPose rig(float partial){
        if(rig==null){rig=HexbotRigPose.solve(presentation(1));previousRig=rig;}
        return previousRig.lerp(rig,Mth.clamp(partial,0,1));
    }
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(3,2,3);}
}
