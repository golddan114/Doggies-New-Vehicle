package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Transient, bounded preflight. Uses the same trajectory and complete body as the live missile. */
public final class NuclearClearance implements AutoCloseable {
    public enum State { CHECKING, CLEAR, BLOCKED, TIMEOUT, CANCELLED }
    public enum BlockKind { NORMAL, FOLIAGE, UNBREAKABLE }
    public enum Rejection { LAUNCH, OUTSIDE_IMPACT, UNBREAKABLE, CEILING }
    public record Description(String translationKey,String id) { }
    public interface World {
        int minY();
        int maxY();
        /** Holds at most one temporary chunk; must never synchronously load a chunk. */
        boolean ready(BlockPos position);
        List<AABB> obstacles(BlockPos position);
        default BlockKind kind(BlockPos position){return BlockKind.NORMAL;}
        default Description describe(BlockPos position){return new Description("message.doggiesnewvehicle.nuclear.block_unknown","unknown");}
        void release();
    }
    public static final int CELLS_PER_TICK=1024, STEPS_PER_TICK=2048, TIMEOUT_TICKS=1200;
    // Nearby early impacts are allowed independently of the eventual crater's footprint.
    private static final int NUCLEAR_CONTACT_TOLERANCE=28;
    // A mostly covered 9x9 area with a gap underneath is a ceiling, rather than solid sloping ground.
    public static final int ROOF_DIAMETER=9, ROOF_COLUMNS=ROOF_DIAMETER*ROOF_DIAMETER;
    public static final int ROOF_REQUIRED_COLUMNS=(ROOF_COLUMNS*3+3)/4;
    private final World world;
    private final Vec3 target;
    private final long started;
    private final boolean launchOnly;
    private final SiloPayloadKind payload;
    private final NuclearRules rules=NuclearRules.DEFAULT;
    private Vec3 position,motion;
    private float yaw,pitch=-90;
    private int age;
    private NuclearFlight.Stage stage=NuclearFlight.Stage.CLIMB;
    private List<AABB> body;
    private BlockPos low,high,cursor,blocked;
    private Hit nearest;
    private Rejection rejection;
    private Description description;
    private BlockPos roofLow,roofHigh,roofCursor;
    private boolean[] roofGap;
    private int[] roofParents;
    private byte[] roofRanks;
    private long[] roofColumnsLow,roofColumnsHigh;
    private State state=State.CHECKING;
    private long observed=Long.MIN_VALUE;

    public NuclearClearance(World world,Vec3 origin,BlockPos target,float yaw,long now){
        this(world,origin,target,yaw,now,false);
    }
    public NuclearClearance(World world,Vec3 origin,BlockPos target,float yaw,long now,boolean launchOnly){
        this(world,origin,target,yaw,now,launchOnly,SiloPayloadKind.NUCLEAR);
    }
    public NuclearClearance(World world,Vec3 origin,BlockPos target,float yaw,long now,boolean launchOnly,SiloPayloadKind payload){
        this.world=world;position=origin;this.target=target.getCenter();this.yaw=yaw;started=now;this.launchOnly=launchOnly;
        this.payload=java.util.Objects.requireNonNull(payload);
    }
    public State state(){return state;}
    public BlockPos blocked(){return blocked;}
    public Rejection rejection(){return rejection;}
    public Description description(){return description;}
    public boolean launchSide(){return stage==NuclearFlight.Stage.CLIMB;}
    public State tick(long now){
        if(state!=State.CHECKING||observed==now)return state;
        observed=now;
        if(now<started||now-started>=TIMEOUT_TICKS)return finish(State.TIMEOUT);
        int cells=0,steps=0;
        while(cells<CELLS_PER_TICK&&steps<STEPS_PER_TICK){
            if(roofCursor!=null){
                if(!world.ready(roofCursor))return state;
                cells++;
                int layer=roofCursor.getY()-roofLow.getY();
                int column=(roofCursor.getX()-roofLow.getX())*ROOF_DIAMETER+roofCursor.getZ()-roofLow.getZ();
                if(world.obstacles(roofCursor).isEmpty()||world.kind(roofCursor)==BlockKind.FOLIAGE)roofGap[column]=true;
                else if(roofGap[column]){
                    if(connectRoof(layer,column))return reject(roofCursor,world.describe(roofCursor),Rejection.CEILING);
                }
                roofCursor=next(roofCursor,roofLow,roofHigh);
                if(roofCursor==null)return finish(State.CLEAR);
                continue;
            }
            if(cursor==null){
                if(age>=rules.lifeTicks())return finish(State.TIMEOUT);
                stage=NuclearFlight.stage(point(position),point(target),stage,rules);
                if(launchOnly&&stage!=NuclearFlight.Stage.CLIMB)return finish(State.CLEAR);
                var velocity=NuclearFlight.velocity(point(position),point(target),age,stage,rules);
                motion=new Vec3(velocity.x(),velocity.y(),velocity.z());
                if(age>=rules.exitTicks()&&motion.lengthSqr()>1e-8){
                    yaw=NuclearFlight.yaw(velocity,yaw);
                    pitch=(float)-Math.toDegrees(Math.atan2(motion.y,Math.hypot(motion.x,motion.z)));
                }
                // A sphere enclosing the packaged body proves high-altitude segments need no world reads.
                double reach=NuclearModelData.DEFAULT.rocketHeight()+NuclearModelData.DEFAULT.rocketWidth();
                if(Math.min(position.y,position.y+motion.y)-reach>=world.maxY()){
                    if(launchOnly)return finish(State.CLEAR);
                    advance();steps++;continue;
                }
                body=payload==SiloPayloadKind.DOGEGG?NuclearCollision.dogEgg(yaw,pitch,position):NuclearCollision.missile(yaw,pitch,position);
                nearest=null;
                var area=NuclearCollision.envelope(body).expandTowards(motion);
                // The selected target block is the intended impact surface. Only its overhead approach must be clear.
                int floor=stage==NuclearFlight.Stage.DIVE?(int)Math.floor(target.y)+1:world.minY();
                low=BlockPos.containing(area.minX,Math.max(floor,Math.max(world.minY(),area.minY)),area.minZ);
                high=BlockPos.containing(area.maxX,Math.min(world.maxY()-1,Math.floor(area.maxY)),area.maxZ);
                if(low.getY()>high.getY()){
                    if(arrived())return finish(State.CLEAR);
                    advance();steps++;continue;
                }
                cursor=low;
            }
            if(!world.ready(cursor))return state;
            cells++;
            for(var obstacle:world.obstacles(cursor)){
                double fraction=NuclearCollision.sweep(body,motion,obstacle);
                if(Double.isFinite(fraction)&&(nearest==null||fraction<nearest.fraction())){
                    nearest=new Hit(cursor.immutable(),fraction,NuclearCollision.contact(body,motion,obstacle,fraction),
                            world.kind(cursor),world.describe(cursor));
                }
            }
            // Y first keeps a whole column in one chunk; the world port owns the sole temporary ticket.
            cursor=next(cursor,low,high);
            if(cursor==null){
                if(nearest!=null){
                    if(stage==NuclearFlight.Stage.CLIMB)return reject(nearest.block(),nearest.description(),Rejection.LAUNCH);
                    int impactRadius=payload==SiloPayloadKind.DOGEGG?VacuumPayloadProgress.TERRAIN_RADIUS:NUCLEAR_CONTACT_TOLERANCE;
                    if(nearest.contact()==null||nearest.contact().distanceTo(target)>impactRadius)
                        return reject(nearest.block(),nearest.description(),Rejection.OUTSIDE_IMPACT);
                    if(nearest.kind()==BlockKind.UNBREAKABLE)return reject(nearest.block(),nearest.description(),Rejection.UNBREAKABLE);
                    beginRoofCheck(nearest.contact());
                    if(roofCursor==null)return finish(State.CLEAR);
                    continue;
                }
                if(arrived())return finish(State.CLEAR);
                advance();steps++;
            }
        }
        return state;
    }
    private boolean arrived(){return stage==NuclearFlight.Stage.DIVE&&position.distanceTo(target)<=motion.length()+.5;}
    private record Hit(BlockPos block,double fraction,Vec3 contact,BlockKind kind,Description description) { }
    private static BlockPos next(BlockPos current,BlockPos low,BlockPos high){
        if(current.getY()<high.getY())return current.above();
        if(current.getX()<high.getX())return new BlockPos(current.getX()+1,low.getY(),current.getZ());
        if(current.getZ()<high.getZ())return new BlockPos(low.getX(),low.getY(),current.getZ()+1);
        return null;
    }
    private void beginRoofCheck(Vec3 contact){
        int bottom=Math.max(world.minY(),(int)Math.floor(target.y)+1);
        int top=Math.min(world.maxY()-1,(int)Math.floor(contact.y)+ROOF_DIAMETER/2+1);
        if(bottom>top)return;
        var centre=BlockPos.containing(contact);int radius=ROOF_DIAMETER/2;
        roofLow=new BlockPos(centre.getX()-radius,bottom,centre.getZ()-radius);
        roofHigh=new BlockPos(centre.getX()+radius,top,centre.getZ()+radius);
        int nodes=(top-bottom+1)*ROOF_COLUMNS;
        roofParents=new int[nodes];java.util.Arrays.fill(roofParents,-1);roofRanks=new byte[nodes];
        roofColumnsLow=new long[nodes];roofColumnsHigh=new long[nodes];roofGap=new boolean[ROOF_COLUMNS];roofCursor=roofLow;
    }
    /** Connected voxel cover, with unique horizontal columns; slopes join, detached layers do not. */
    private boolean connectRoof(int layer,int column){
        int index=layer*ROOF_COLUMNS+column;roofParents[index]=index;
        if(column<64)roofColumnsLow[index]=1L<<column;else roofColumnsHigh[index]=1L<<(column-64);
        int x=column/ROOF_DIAMETER,z=column%ROOF_DIAMETER,levels=roofParents.length/ROOF_COLUMNS;
        // Edge/corner adjacency retains ordinary one-block stair-step roofs as one connected cover.
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            int nextY=layer+dy,nextX=x+dx,nextZ=z+dz;
            if(nextY<0||nextY>=levels||nextX<0||nextX>=ROOF_DIAMETER||nextZ<0||nextZ>=ROOF_DIAMETER)continue;
            int neighbour=nextY*ROOF_COLUMNS+nextX*ROOF_DIAMETER+nextZ;
            if(roofParents[neighbour]<0)continue;
            int a=roofRoot(index),b=roofRoot(neighbour);if(a==b)continue;
            if(roofRanks[a]<roofRanks[b]){int swap=a;a=b;b=swap;}
            roofParents[b]=a;if(roofRanks[a]==roofRanks[b])roofRanks[a]++;
            roofColumnsLow[a]|=roofColumnsLow[b];roofColumnsHigh[a]|=roofColumnsHigh[b];
        }
        int root=roofRoot(index);
        return Long.bitCount(roofColumnsLow[root])+Long.bitCount(roofColumnsHigh[root])>=ROOF_REQUIRED_COLUMNS;
    }
    private int roofRoot(int index){
        while(roofParents[index]!=index){roofParents[index]=roofParents[roofParents[index]];index=roofParents[index];}
        return index;
    }
    private State reject(BlockPos block,Description detail,Rejection reason){
        blocked=block.immutable();description=detail;rejection=reason;return finish(State.BLOCKED);
    }
    private void advance(){position=position.add(motion);age++;}
    private State finish(State result){state=result;world.release();return state;}
    @Override public void close(){if(state==State.CHECKING)finish(State.CANCELLED);else world.release();}
    private static NuclearFlight.Point point(Vec3 p){return new NuclearFlight.Point(p.x,p.y,p.z);}
}
