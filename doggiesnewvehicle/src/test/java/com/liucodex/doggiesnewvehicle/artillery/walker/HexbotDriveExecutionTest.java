package com.liucodex.doggiesnewvehicle.artillery.walker;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the compiled runtime travel/terrain methods against explicit native vehicle/world ports. */
class HexbotDriveExecutionTest {
    @Test void authenticatedDriverProducesVelocityRelativeToViewAndNeverMovesTheWorldDirectly()throws Exception{
        VehiclePort vehicle=subject();vehicle.forward=true;vehicle.driver.yaw=90;
        travel(vehicle);
        assertEquals(-.0375,vehicle.velocity.x,1e-6);assertEquals(0,vehicle.velocity.z,1e-6);
        assertEquals(0,vehicle.velocity.y,1e-6);assertEquals(0,vehicle.z,1e-6);
        assertEquals(1,vehicle.velocityWrites);assertTrue(vehicle.world.rays<=69);
    }
    @Test void missingDriverClearsLatchedKeysAndClientDoesNotSimulate()throws Exception{
        VehiclePort vehicle=subject();vehicle.driver=null;vehicle.forward=true;vehicle.velocity=new Vec3(.1,0,0);
        travel(vehicle);assertEquals(1,vehicle.inputClears);assertEquals(.0625,vehicle.velocity.x,1e-6);
        assertEquals(0,vehicle.velocity.z,1e-6);
        vehicle=subject();vehicle.world.isClientSide=true;vehicle.forward=true;
        travel(vehicle);assertEquals(0,vehicle.velocityWrites);assertEquals(0,vehicle.world.rays);assertEquals(0,vehicle.inputClears);
    }
    @Test void unloadedAndBorderQueriesFailClosedWithoutReadingOrMovingForward()throws Exception{
        for(boolean border:new boolean[]{false,true}){
            VehiclePort vehicle=subject();vehicle.forward=true;
            if(border)vehicle.world.border.allowed=false;else vehicle.world.loaded=false;
            travel(vehicle);assertEquals(0,vehicle.world.rays);assertEquals(0,vehicle.world.blockQueries);assertEquals(0,vehicle.velocity.z,1e-6);
            assertEquals(-.08,vehicle.velocity.y,1e-6);
        }
    }
    @Test void raisedTerrainRaisesBodyButVoidDoesNotCreateHoverSupport()throws Exception{
        VehiclePort vehicle=subject();vehicle.world.height=.5;travel(vehicle);
        assertTrue(vehicle.velocity.y>0);assertTrue(vehicle.velocity.y<=.18);
        vehicle=subject();vehicle.world.floor=false;vehicle.forward=true;travel(vehicle);
        assertEquals(0,vehicle.velocity.z,1e-6);assertEquals(-.08,vehicle.velocity.y,1e-6);
        assertTrue(vehicle.world.rays<=69);
    }
    @Test void onlyOnePlayerSeatIsAdmitted()throws Exception{
        VehiclePort vehicle=subject();var method=vehicle.getClass().getMethod("canAddPassenger",ActorPort.class);
        vehicle.passengers=0;assertEquals(true,method.invoke(vehicle,new DriverPort()));
        assertEquals(false,method.invoke(vehicle,new ActorPort()));
        vehicle.passengers=1;assertEquals(false,method.invoke(vehicle,new DriverPort()));
    }
    @Test void sustainedDrivingAndDirectionChangesKeepEveryFootNearTheChassis()throws Exception{
        VehiclePort vehicle=subject();int[] lastStep=new int[6];
        var field=vehicle.getClass().getDeclaredField("gait");field.setAccessible(true);
        for(int tick=0;tick<840;tick++){
            int phase=tick/120;
            vehicle.forward=phase==0||phase==4||phase==5;
            vehicle.back=phase==1;
            vehicle.left=phase==2;
            vehicle.right=phase==3;
            vehicle.driver.yaw=phase==4?(tick-480)*5:phase==5?600-(tick-600)*5:0;
            vehicle.world.rays=0;travel(vehicle);
            var gait=(HexbotGait)field.get(vehicle);
            var body=new HexbotGeometry.Point(vehicle.x,vehicle.y+.4,vehicle.z);
            for(int i=0;i<6;i++){
                if(gait.foot(i).moving())lastStep[i]=tick;
                if(phase<6)assertTrue(tick-lastStep[i]<40,"Leg waits while driving: "+i+" tick "+tick);
                var rest=body.add(HexbotGeometry.rest(i).rotate(-Math.toRadians(vehicle.yaw)));
                assertTrue(gait.foot(i).point().horizontalDistance(rest)<1.6,"Foot trails driver: "+i+" tick "+tick);
            }
            assertTrue(gait.supportedCount()>=3,"Lost flat-ground support at "+tick);
            assertTrue(vehicle.world.rays<=69,"Terrain query budget");
            // The native vehicle owns collision movement; this fixture applies the returned
            // displacement once to exercise successive travel calls, without claiming native physics coverage.
            vehicle.x+=vehicle.velocity.x;vehicle.y+=vehicle.velocity.y;vehicle.z+=vehicle.velocity.z;
        }
        var gait=(HexbotGait)field.get(vehicle);
        assertEquals(6,gait.supportedCount(),"All feet settle after stopping");
        assertEquals(0,vehicle.velocity.horizontalDistance(),1e-9);
    }
    @Test void snowAndBlockStepsRaiseTheWholeChassisBeforeAdvancing()throws Exception{
        for(double rise:new double[]{.5,1,1.125})for(int direction=0;direction<4;direction++){
            VehiclePort vehicle=subject();vehicle.forward=true;vehicle.yaw=vehicle.driver.yaw=direction*90;
            double dx=-Math.sin(Math.toRadians(vehicle.yaw)),dz=Math.cos(Math.toRadians(vehicle.yaw));
            vehicle.world.heights=(x,z)->Math.min(3,Math.max(0,Math.floor((x*dx+z*dz-2)/3)+1))*rise;
            for(int tick=0;tick<220;tick++){
                vehicle.world.rays=0;travel(vehicle);
                AABB requested=new HullPort(vehicle).bounds().move(vehicle.velocity);
                assertFalse(vehicle.world.getBlockCollisions(vehicle,requested).iterator().hasNext(),
                        "Chassis requests collision: rise "+rise+" direction "+direction+" tick "+tick);
                assertTrue(vehicle.world.rays<=69);
                vehicle.x+=vehicle.velocity.x;vehicle.y+=vehicle.velocity.y;vehicle.z+=vehicle.velocity.z;
            }
            assertTrue(vehicle.x*dx+vehicle.z*dz>12,"Did not cross steps: "+rise+" direction "+direction+" z="+vehicle.z+" y="+vehicle.y);
        }
    }
    @Test void wallsLowCeilingsAndMissingFootprintFloorStopTheAdvance()throws Exception{
        for(int obstruction=0;obstruction<3;obstruction++){
            VehiclePort vehicle=subject();vehicle.forward=true;final int mode=obstruction;
            vehicle.world.heights=(x,z)->z<2?0:mode==0?3:mode==1?1.125:Double.NaN;
            if(mode==1)vehicle.world.ceiling=2.4;
            for(int tick=0;tick<100;tick++){
                vehicle.world.rays=0;travel(vehicle);
                assertTrue(vehicle.world.rays<=69);
                vehicle.x+=vehicle.velocity.x;vehicle.y+=vehicle.velocity.y;vehicle.z+=vehicle.velocity.z;
            }
            assertTrue(vehicle.z<1,"Chassis must stop before its front edge enters the obstacle/void");
            assertTrue(vehicle.y<1.4,"No wall climbing or rising into the ceiling");
        }
    }
    @Test void finiteSingleBlockBetweenProbeRowsDoesNotDeadlockTheChassis()throws Exception{
        for(double startX:new double[]{-.1,.1,.5,.9}){
            VehiclePort vehicle=subject();vehicle.x=startX;vehicle.forward=true;
            vehicle.world.blocks=java.util.List.of(new AABB(0,0,2,1,1,3));
            vehicle.world.heights=(x,z)->x>=0&&x<1&&z>=2&&z<3?1:0;
            int stopped=0,longestStop=0;
            for(int tick=0;tick<180;tick++){
                vehicle.world.rays=0;travel(vehicle);
                if(vehicle.velocity.horizontalDistance()<1e-7){stopped++;longestStop=Math.max(longestStop,stopped);}else stopped=0;
                vehicle.x+=vehicle.velocity.x;vehicle.y+=vehicle.velocity.y;vehicle.z+=vehicle.velocity.z;
                assertTrue(vehicle.world.rays<=69);
            }
            assertTrue(vehicle.z>8,"Stuck on a finite block at x="+startX+", z="+vehicle.z);
            assertTrue(longestStop<50,"No indefinite clearance stop: "+longestStop);
        }
    }
    @Test void flatGroundReachesFiveBlocksPerSecondWithoutDiagonalBoost()throws Exception{
        for(boolean diagonal:new boolean[]{false,true}){
            VehiclePort vehicle=subject();vehicle.forward=true;vehicle.left=diagonal;
            for(int tick=0;tick<30;tick++){
                travel(vehicle);vehicle.x+=vehicle.velocity.x;vehicle.z+=vehicle.velocity.z;
            }
            assertEquals(.25,vehicle.velocity.horizontalDistance(),1e-9);
        }
    }
    private static void travel(VehiclePort p)throws Exception{p.getClass().getMethod("travel").invoke(p);}
    private VehiclePort subject()throws Exception{
        String source="com/liucodex/doggiesnewvehicle/artillery/walker/DnvHexbotEntity";
        String target="com/liucodex/doggiesnewvehicle/artillery/walker/probe/HexbotDrive";
        Map<String,String> map=new HashMap<>();
        Map.ofEntries(Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",VehiclePort.class),
                Map.entry("net/minecraft/world/entity/Entity",ActorPort.class),
                Map.entry("net/minecraft/world/entity/player/Player",DriverPort.class),
                Map.entry("net/minecraft/world/level/Level",WorldPort.class),
                Map.entry("net/minecraft/world/level/border/WorldBorder",BorderPort.class),
                Map.entry("net/minecraft/world/level/ClipContext",TracePort.class),
                Map.entry("net/minecraft/world/level/ClipContext$Block",TracePort.Block.class),
                Map.entry("net/minecraft/world/level/ClipContext$Fluid",TracePort.Fluid.class),
                Map.entry("com/atsuishio/superbwarfare/tools/OBB",HullPort.class),
                Map.entry("net/minecraft/world/phys/HitResult",HitPort.class),
                Map.entry("net/minecraft/world/phys/BlockHitResult",HitPort.class),
                Map.entry("net/minecraft/world/phys/HitResult$Type",HitPort.Kind.class))
                .forEach((name,port)->map.put(name,Type.getInternalName(port)));
        Remapper remapper=new Remapper(){@Override public String map(String key){
            if(key.equals(source)||key.startsWith(source+"$"))return target+key.substring(source.length());
            return map.getOrDefault(key,key);
        }};
        Map<String,byte[]> bytecode=new HashMap<>();
        for(String suffix:new String[]{"","$1","$Clearance"}){
            ClassNode node=new ClassNode();
            try(var in=getClass().getResourceAsStream("/"+source+suffix+".class")){assertNotNull(in);new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
            if(suffix.isEmpty()){
                node.methods.removeIf(m->!Set.of("travel","bodyOrigin","loaded","ground","groundTarget","chassisClearance","canAddPassenger","isControlledByLocalInstance").contains(m.name));
                MethodNode ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
                ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false);
                ctor.visitInsn(Opcodes.RETURN);node.methods.add(ctor);
            }
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(writer,remapper));bytecode.put(target+suffix,writer.toByteArray());
        }
        ClassLoader loader=new ClassLoader(getClass().getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                byte[] bytes=bytecode.get(name.replace('.','/'));
                return bytes==null?super.findClass(name):defineClass(name,bytes,0,bytes.length);
            }
        };
        return (VehiclePort)loader.loadClass(target.replace('/','.')).getConstructor().newInstance();
    }
    public static class ActorPort {
        float yaw;boolean alive=true;
        public float getYRot(){return yaw;}public boolean isAlive(){return alive;}
    }
    public static class DriverPort extends ActorPort {public boolean isSpectator(){return false;}}
    public static class VehiclePort extends ActorPort {
        final WorldPort world=new WorldPort();DriverPort driver=new DriverPort();
        Vec3 velocity=Vec3.ZERO;double x,y=.7,z;float pitch,roll;boolean forward,back,left,right;int inputClears,velocityWrites,passengers;
        public VehiclePort(){ }
        public WorldPort level(){return world;}public boolean isWreck(){return false;}
        public ActorPort getFirstPassenger(){return driver;}
        public java.util.List<ActorPort> getPassengers(){return java.util.Collections.nCopies(passengers,new ActorPort());}
        public void processInput(short v){assertEquals(0,v);inputClears++;forward=back=left=right=false;}
        public boolean forwardInputDown(){return forward;}public boolean backInputDown(){return back;}
        public boolean leftInputDown(){return left;}public boolean rightInputDown(){return right;}
        public Vec3 getDeltaMovement(){return velocity;}
        public void setDeltaMovement(double x,double y,double z){velocity=new Vec3(x,y,z);velocityWrites++;}
        public void setYRot(float v){yaw=v;}public float getXRot(){return pitch;}public float getRoll(){return roll;}
        public void setXRot(float v){pitch=v;}public void setZRot(float v){roll=v;}
        public double getX(){return x;}public double getY(){return y;}public double getZ(){return z;}
        public HullPort getCollisionOBB(){return new HullPort(this);}
        public AABB getBoundingBox(){return new HullPort(this).bounds();}
        public boolean m_7310_(ActorPort p){return true;}
    }
    public static class BorderPort {boolean allowed=true;public boolean isWithinBounds(BlockPos p){return allowed;}}
    public static class WorldPort {
        public boolean isClientSide;boolean loaded=true,floor=true;double height,ceiling=Double.POSITIVE_INFINITY;int rays,blockQueries;final BorderPort border=new BorderPort();
        java.util.function.DoubleBinaryOperator heights=(x,z)->height;
        java.util.List<AABB> blocks;
        public boolean hasChunkAt(BlockPos p){return loaded;}public BorderPort getWorldBorder(){return border;}
        public int getMaxBuildHeight(){return 320;}public int getMinBuildHeight(){return -64;}
        public HitPort clip(TracePort trace){
            rays++;double y=heights.applyAsDouble(trace.from.x,trace.from.z);
            boolean inside=Double.isFinite(y)&&y>=trace.from.y;
            return new HitPort(floor&&Double.isFinite(y)&&(inside||y>=trace.to.y),
                    new Vec3(trace.from.x,inside?trace.from.y:y,trace.from.z),inside);
        }
        public Iterable<VoxelShape> getBlockCollisions(ActorPort owner,AABB box){
            blockQueries++;
            if(box.maxY>ceiling)return java.util.List.of(Shapes.create(new AABB(box.minX,ceiling,box.minZ,box.maxX,ceiling+1,box.maxZ)));
            if(blocks!=null)return blocks.stream().filter(b->b.intersects(box)).map(Shapes::create).toList();
            for(double x:new double[]{box.minX+.001,(box.minX+box.maxX)/2,box.maxX-.001})
                for(double z:new double[]{box.minZ+.001,(box.minZ+box.maxZ)/2,box.maxZ-.001})
                    if(floor&&heights.applyAsDouble(x,z)>box.minY+1e-8)return java.util.List.of(Shapes.create(new AABB(box.minX,-64,box.minZ,box.maxX,heights.applyAsDouble(x,z),box.maxZ)));
            return java.util.List.of();
        }
    }
    /** Explicit rotated hull seam from this model's native OBB dimensions, not native collision physics. */
    public static class HullPort {
        private final VehiclePort owner;public HullPort(VehiclePort owner){this.owner=owner;}
        public static AABB getWorldAABB(HullPort hull){return hull.bounds();}
        AABB bounds(){
            var q=new org.joml.Quaterniond().rotationYXZ(-Math.toRadians(owner.yaw),Math.toRadians(owner.pitch),Math.toRadians(owner.roll));
            AABB box=null;
            for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1}){
                var p=q.transform(new org.joml.Vector3d(x*.5518,.44776+y*.49476,-.0986+z*1.1824));
                var corner=new AABB(p.x+owner.x,p.y+owner.y,p.z+owner.z,p.x+owner.x,p.y+owner.y,p.z+owner.z);
                box=box==null?corner:box.minmax(corner);
            }
            return box;
        }
    }
    public static class TracePort {
        public enum Block{COLLIDER}public enum Fluid{NONE}final Vec3 from,to;
        public TracePort(Vec3 from,Vec3 to,Block block,Fluid fluid,ActorPort owner){this.from=from;this.to=to;}
    }
    public record HitPort(boolean floor,Vec3 point,boolean isInside){
        public enum Kind{BLOCK,MISS}public Kind getType(){return floor?Kind.BLOCK:Kind.MISS;}
        public Vec3 getLocation(){return point;}
        public Direction getDirection(){return Direction.UP;}
    }
}
