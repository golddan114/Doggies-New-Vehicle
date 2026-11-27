package com.liucodex.carriercube;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One server-owned movement program. Destination tickets are short-lived and never pin a route. */
final class CubeNavigation {
    private static final float WARP_DEGREES_PER_TICK = 5F / 20;
    private static final int ENGINE_CALIBRATION_TICKS = 5 * 20;
    private enum Stage { PREPARE, ASCEND, TRANSIT, LOAD, WARP_CALIBRATE, WARP_CHARGE, DESCEND, STOP }
    private static final TicketType<UUID> AREA=TicketType.create("shanyue_destination",UUID::compareTo);
    private final int x,z;
    private final boolean warp;
    private Stage stage=Stage.PREPARE;
    private int ticks=100;
    private double arrival;
    private boolean surveyed;
    private float lockedYaw=Float.NaN;
    long turnStarted=-1;
    float turnOrigin;
    private long lastTurnTick=-1;
    private float bearing(CubeVehicle ship) {
        double dx=x+.5-ship.getX(),dz=z+.5-ship.getZ();
        return Math.hypot(dx,dz)<.001 ? ship.getYRot() : (float)Math.toDegrees(Math.atan2(-dx,dz));
    }
    float warpBearing(CubeVehicle ship) {
        return Float.isFinite(lockedYaw) ? lockedYaw : bearing(ship);
    }
    static float nextWarpYaw(float yaw, float target) {
        return Mth.approachDegrees(yaw,target,WARP_DEGREES_PER_TICK);
    }
    static float turnYaw(float from,float bearing,long elapsed) {
        float angle=Mth.wrapDegrees(bearing-from);
        return from+Math.copySign(Math.min(Math.abs(angle),WARP_DEGREES_PER_TICK*Math.max(0,elapsed)),angle);
    }
    int ticksLeft(){return ticks;}
    private final LinkedHashSet<ChunkPos> held=new LinkedHashSet<>();
    private List<ChunkPos> footprint;
    CubeNavigation(int x,int z,boolean warp){this.x=x;this.z=z;this.warp=warp;}
    void stop(){if(stage!=Stage.STOP){stage=Stage.STOP;ticks=100;}}
    String stage(){return stage.name();}
    boolean warp(){return warp;}
    static int alignmentTicks(float yaw, float targetYaw) {
        return Mth.ceil(Math.abs(Mth.wrapDegrees(targetYaw - yaw)) / WARP_DEGREES_PER_TICK);
    }
    int seconds(CubeVehicle ship) {
        int remaining = ticks;
        if (warp && stage == Stage.PREPARE) {
            float target = warpBearing(ship);
            remaining = alignmentTicks(ship.getYRot(), target);
        }
        return (remaining + 19) / 20;
    }
    int x(){return x;} int z(){return z;}

    private boolean acquire(CubeVehicle ship,ChunkPos pos) {
        var level=(ServerLevel)ship.level();
        if(held.add(pos)) {
            level.getChunkSource().addRegionTicket(AREA,pos,0,ship.getUUID());
            // The distance manager brings the chunk to FULL on subsequent server ticks.
        }
        return level.getChunkSource().getChunkNow(pos.x,pos.z)!=null;
    }
    void close(CubeVehicle ship) {
        var level=(ServerLevel)ship.level();
        for(var p:held)level.getChunkSource().removeRegionTicket(AREA,p,0,ship.getUUID());
        held.clear();
    }
    private void prepareFootprint(CubeVehicle ship) {
        var center=new ChunkPos(new BlockPos(x,0,z));
        var all=new LinkedHashSet<ChunkPos>();
        for(int i=-2;i<=2;i++)for(int j=-2;j<=2;j++)all.add(new ChunkPos(center.x+i,center.z+j));
        var box=CubeGeometry.headingEnvelope(new Vec3(x+.5,0,z+.5),warp?lockedYaw:ship.getYRot()).inflate(1);
        for(int i=Mth.floor(box.minX)>>4;i<=Mth.floor(box.maxX)>>4;i++)
            for(int j=Mth.floor(box.minZ)>>4;j<=Mth.floor(box.maxZ)>>4;j++)all.add(new ChunkPos(i,j));
        if (warp && (stage==Stage.PREPARE || stage==Stage.WARP_CALIBRATE)) {
            // The long hull can rotate into chunks beyond the ship's own persistent center ticket.
            var origin=CubeGeometry.hullEnvelope(ship.position()).inflate(1);
            for(int i=Mth.floor(origin.minX)>>4;i<=Mth.floor(origin.maxX)>>4;i++)
                for(int j=Mth.floor(origin.minZ)>>4;j<=Mth.floor(origin.maxZ)>>4;j++)all.add(new ChunkPos(i,j));
        }
        footprint=List.copyOf(all);
    }
    private boolean loadDestination(CubeVehicle ship) {
        if(footprint==null)prepareFootprint(ship);
        int budget=4;boolean ready=true;
        for(var at:footprint){
            if(!held.contains(at) && budget--<=0){ready=false;break;}
            if(!acquire(ship,at))ready=false;
        }
        return ready;
    }
    private double ground(CubeVehicle ship) {
        var level=(ServerLevel)ship.level();
        var box=CubeGeometry.headingEnvelope(new Vec3(x+.5,0,z+.5),warp?lockedYaw:ship.getYRot());
        int ground=level.getMinBuildHeight();
        for(int bx=Mth.floor(box.minX);bx<=Mth.floor(box.maxX);bx++)
            for(int bz=Mth.floor(box.minZ);bz<=Mth.floor(box.maxZ);bz++)
                ground=Math.max(ground,level.getHeight(Heightmap.Types.WORLD_SURFACE,bx,bz));
        return ground==level.getMinBuildHeight()?Double.NaN:ground+(warp?50:30);
    }
    private boolean checkLanding(CubeVehicle ship) {
        arrival=ground(ship);
        if (!Double.isFinite(arrival)) {ship.missingSurface();return false;}
        if (warp && !ship.clear(CubeGeometry.headingEnvelope(new Vec3(x+.5,arrival,z+.5),lockedYaw).inflate(.01,0,.01),null)) {
            ship.navigationBlocked();
            return false;
        }
        return true;
    }
    /** Returns true on arrival, cancellation or blocked movement. */
    boolean tick(CubeVehicle ship) {
        var level=(ServerLevel)ship.level();
        if(stage==Stage.STOP)return --ticks<=0;
        if (warp && !Float.isFinite(lockedYaw))
            lockedYaw=stage==Stage.PREPARE ? bearing(ship) : ship.getYRot();
        if (stage==Stage.PREPARE) {
            if (!warp) { if (--ticks<=0) stage=Stage.ASCEND; return false; }
            boolean loaded=loadDestination(ship);
            ticks=Math.max(0,ticks-1);
            if (!surveyed && loaded && ticks==0) {
                if (!checkLanding(ship)) return true;
                surveyed=true;
            }
            var origin=CubeGeometry.hullEnvelope(ship.position()).inflate(1);
            if (origin.minY<level.getMaxBuildHeight() && !level.hasChunksAt(BlockPos.containing(origin.minX,origin.minY,origin.minZ),
                    BlockPos.containing(origin.maxX,origin.maxY,origin.maxZ))) {
                turnStarted=-1;
                return false;
            }
            long now=level.getGameTime();
            if (turnStarted<0 || now!=lastTurnTick+1) {turnStarted=now;turnOrigin=ship.getYRot();}
            lastTurnTick=now;
            float yaw=nextWarpYaw(ship.getYRot(),lockedYaw);
            if (!ship.navigationClear(ship.position(),ship.position(),yaw)) {ship.navigationBlocked();return true;}
            ship.positionFixture(ship.position(),yaw);
            if (Math.abs(Mth.wrapDegrees(yaw-lockedYaw))<.001F) {
                stage=Stage.WARP_CALIBRATE;
                ticks=ENGINE_CALIBRATION_TICKS;
            }
            return false;
        }
        if (stage==Stage.WARP_CALIBRATE) {
            ship.positionFixture(ship.position(),lockedYaw);
            boolean ready=loadDestination(ship);
            ticks=Math.max(0,ticks-1);
            if (ready && ticks==0) {
                if (!checkLanding(ship)) return true;
                stage=Stage.WARP_CHARGE;
                ticks=400;
            }
            return false;
        }
        if(stage==Stage.LOAD || stage==Stage.WARP_CHARGE || stage==Stage.DESCEND && footprint==null) {
            if(!loadDestination(ship))return false;
            if(stage==Stage.LOAD){
                if (!checkLanding(ship)) return true;
                stage=warp?Stage.WARP_CHARGE:Stage.DESCEND;
                ticks=warp?400:0;
                return false;
            }
            if(stage==Stage.WARP_CHARGE){
                ship.positionFixture(ship.position(),lockedYaw);
                if(--ticks>0)return false;
                arrival=ground(ship);
                if(Double.isNaN(arrival)){ship.missingSurface();return true;}
                Vec3 landing=new Vec3(x+.5,arrival,z+.5);
                if(ship.navigationClear(landing,landing,ship.getYRot()))ship.jumpTo(landing);
                else ship.navigationBlocked();
                return true;
            }
        }
        double ceiling=level.getMaxBuildHeight()+4.0;
        if(stage==Stage.ASCEND || stage==Stage.DESCEND) {
            double target=stage==Stage.ASCEND?Math.max(ceiling,ship.getY()):arrival;
            var next=ship.position().add(0,Mth.clamp(target-ship.getY(),-.8,.8),0);
            float yaw=stage==Stage.ASCEND ? Mth.approachDegrees(ship.getYRot(),bearing(ship),.65F) : ship.getYRot();
            if(!ship.navigationClear(ship.position(),next,yaw)) {ship.navigationBlocked();return true;}
            ship.positionFixture(next,yaw);
            if(Math.abs(next.y-target)<.001){if(stage==Stage.DESCEND)return true;stage=Stage.TRANSIT;}
            return false;
        }
        Vec3 delta=new Vec3(x+.5-ship.getX(),0,z+.5-ship.getZ());
        double d=delta.length();
        if(d<.001){stage=Stage.LOAD;close(ship);return false;}
        var next=ship.position().add(delta.scale(Math.min(1.25,d)/d));
        var at=new ChunkPos(BlockPos.containing(next));
        if(!acquire(ship,at))return false;
        for(var it=held.iterator();it.hasNext();) {
            var old=it.next();
            if(!old.equals(at)){level.getChunkSource().removeRegionTicket(AREA,old,0,ship.getUUID());it.remove();}
        }
        float yaw=Mth.approachDegrees(ship.getYRot(),(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),1.25F);
        if(!ship.navigationClear(ship.position(),next,yaw)) {ship.navigationBlocked();return true;}
        ship.positionFixture(next,yaw);
        return false;
    }
    CompoundTag save() {
        var tag=new CompoundTag();tag.putInt("x",x);tag.putInt("z",z);tag.putBoolean("warp",warp);
        tag.putString("stage",stage.name());tag.putInt("time",ticks);tag.putDouble("arrival",arrival);
        if(Float.isFinite(lockedYaw))tag.putFloat("bearing",lockedYaw);
        return tag;
    }
    static CubeNavigation restore(CompoundTag tag) {
        var n=new CubeNavigation(tag.getInt("x"),tag.getInt("z"),tag.getBoolean("warp"));
        n.stage=Stage.valueOf(tag.getString("stage"));n.ticks=Math.max(0,tag.getInt("time"));
        if (n.warp && n.stage==Stage.LOAD) {
            n.stage=Stage.WARP_CALIBRATE;
            n.ticks=ENGINE_CALIBRATION_TICKS;
        }
        n.arrival=tag.getDouble("arrival");
        if(tag.contains("bearing"))n.lockedYaw=tag.getFloat("bearing");
        return n;
    }
}
