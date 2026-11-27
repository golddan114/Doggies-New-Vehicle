package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import com.liucodex.doggiesnewvehicle.armored.Project612Rig;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.world.phys.*;
import org.joml.Matrix4d;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Exact SBW track physics, engineRunning, barrel vector/transforms and aim math.
 * Only entity/world storage and engine-data getters are ports; no replacement steering/angular formulas. */
class Project612NativeControlExecutionTest {
    @Test void zeroThrottleMouseSteeringConvergesWithExactTrackPhysicsOnBothSides() throws Exception {
        for (boolean client : new boolean[]{false,true}) for (boolean reverse : new boolean[]{false,true})
            for (float target : new float[]{-179,-90,90,179}) {
                var f=new Fixture();var tank=f.tank;tank.world.isClientSide=client;tank.back=reverse;
                tank.yaw=Math.abs(target)>170?-target:0;tank.driver.heading=target;
                assertFalse((boolean)f.call("engineRunning"),"Native idle power is zero, even with fuel and a driver");
                float initial=Math.abs(net.minecraft.util.Mth.wrapDegrees(target-tank.yaw));
                for(int tick=0;tick<300;tick++) {
                    float before=tank.yaw;f.call("travel");
                    assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(tank.yaw-before))<=1.0001);
                    assertFalse(tank.left||tank.right,"Temporary steering must not replace player inputs");
                }
                assertEquals(0,net.minecraft.util.Mth.wrapDegrees(target-tank.yaw),.051,"Initial error "+initial+", reverse="+reverse);
                assertEquals(target,tank.driver.heading);
                assertEquals(300,tank.trackCalls);
            }
    }
    @Test void exactTrackPreservesManualInputAndCannotAutosteerWithoutDriverFuelOrLocalOwnership() throws Exception {
        var f=new Fixture();var tank=f.tank;tank.left=true;tank.driver.heading=-90;f.call("travel");
        assertTrue(tank.yaw<0,"Exact native left input decreases vanilla yaw");assertTrue(tank.left);assertFalse(tank.right);
        for(int veto=0;veto<8;veto++) {
            f=new Fixture();tank=f.tank;tank.driver.heading=90;
            switch(veto) {
                case 0->tank.energy=0;case 1->tank.driver=null;case 2->tank.wreck=true;
                case 3->tank.removed=true;case 4->tank.driver.alive=false;case 5->tank.driver.spectator=true;
                case 6->{tank.local=false;tank.world.isClientSide=true;}case 7->tank.ground=false;
            }
            f.call("travel");assertEquals(0,tank.yaw,"veto="+veto);
        }
    }
    @Test void actualNativeBarrelVectorAndProductionMuzzleTrackTheSamePlayerElevation() throws Exception {
        for(float heading:new float[]{-179,-90,0,90,179})for(float elevation:new float[]{-3,0,10,25}) {
            var f=new Fixture();var tank=f.tank;tank.yaw=heading;tank.driver.heading=heading;tank.driver.elevation=elevation;
            for(int tick=0;tick<150;tick++)f.call("adjustTurretAngle");
            Vec3 muzzle=(Vec3)f.call("getShootVec",new Class<?>[]{String.class,float.class},"Main",1F);
            Vec3 expected=tank.driver.getViewVector(1);
            assertTrue(muzzle.dot(expected)>.99999,"Actual muzzle must point toward player look: "+muzzle+" vs "+expected);
            assertEquals(-elevation,tank.pitch,.15);assertEquals(0,tank.turretYaw);
            Vec3 nativeBarrel=(Vec3)f.call("getBarrelVector",new Class<?>[]{float.class},1F);
            assertEquals(1,nativeBarrel.dot(muzzle),1e-5,"Exact SBW vectorTo and the exported muzzle use the same forward axis");
        }
    }
    @Test void aimStopsAtGunLimitsWithoutChangingPassengerLookAndWreckDoesNotAim() throws Exception {
        for(float elevation:new float[]{-80,80}) {
            var f=new Fixture();f.tank.driver.elevation=elevation;
            for(int tick=0;tick<150;tick++)f.call("adjustTurretAngle");
            assertEquals(elevation>0?-25:3,f.tank.pitch,.001);assertEquals(elevation,f.tank.driver.elevation);
            f.tank.wreck=true;float pitch=f.tank.pitch;f.tank.driver.elevation=-elevation;f.call("adjustTurretAngle");assertEquals(pitch,f.tank.pitch);
        }
    }
    @Test void exactNativeCameraPositionsRetainClearanceAboveTheMovingGun() throws Exception {
        var f=new Fixture();var rig=Project612Rig.INSTANCE;
        for(float elevation=-3;elevation<=25;elevation+=.25F) {
            f.tank.pitch=-elevation;
            for(String method:List.of("getCameraPos","getZoomPos")) {
                Vec3 camera=(Vec3)f.call(method,new Class<?>[]{Body.class,float.class},f.tank.driver,1F);
                Vec3 local=Project612Rig.point(new Matrix4d(rig.local(-elevation)).invert(),camera);
                Vec3 anchor=method.equals("getCameraPos")?f.tank.computed().seat.camera.getPosition():f.tank.computed().seat.camera.getZoomPosition();
                assertTrue(local.distanceTo(anchor)<1e-5,"Camera must remain outside the gun as pitch changes: "+elevation);
                assertTrue(local.y-rig.pivot.y>.55,"At least .55 above the barrel axis (actual mesh surface clearance checked separately)");
            }
        }
    }

    static class Fixture extends ClassLoader {
        static final String SBW="com/atsuishio/superbwarfare/",BASE=SBW+"entity/vehicle/base/VehicleEntity",
                VEC=SBW+"entity/vehicle/utils/VehicleVecUtils",AIM=SBW+"entity/vehicle/utils/VehicleWeaponUtils",
                ENGINE=SBW+"entity/vehicle/utils/VehicleEngineUtils",TANK="com/liucodex/doggiesnewvehicle/armored/DnvProject612Entity",
                PREFIX="com/liucodex/doggiesnewvehicle/compat/project612native/";
        final Map<String,String> types=new HashMap<>(),methods=new HashMap<>(),fields=new HashMap<>();
        final Port tank;final Class<?> nativeType;
        Fixture() throws Exception {
            types.put(BASE,PREFIX+"Native");types.put(TANK,PREFIX+"Tank");types.put(VEC,PREFIX+"Vec");types.put(AIM,PREFIX+"Aim");types.put(ENGINE,PREFIX+"Engine");
            Map.ofEntries(Map.entry("net/minecraft/world/entity/Entity",Body.class),Map.entry("net/minecraft/world/entity/player/Player",Pilot.class),
                    Map.entry("net/minecraft/world/level/Level",World.class),Map.entry("net/minecraft/server/level/ServerLevel",Server.class),
                    Map.entry("net/minecraft/network/syncher/SynchedEntityData",Data.class),Map.entry("net/minecraft/network/syncher/EntityDataAccessor",Key.class),
                    Map.entry(SBW+"data/vehicle/subdata/VehicleType",VehicleType.class),Map.entry(SBW+"data/vehicle/subdata/EngineInfo$Track",Track.class),
                    Map.entry(SBW+"data/vehicle/DefaultVehicleData",Computed.class),Map.entry(SBW+"data/vehicle/subdata/SeatInfo",Seat.class),Map.entry(SBW+"data/vehicle/subdata/CameraPos",Camera.class),
                    Map.entry("net/minecraft/core/particles/ParticleOptions",Particle.class),Map.entry("net/minecraft/core/particles/SimpleParticleType",Particle.class),
                    Map.entry("net/minecraft/core/particles/ParticleTypes",Particles.class)).forEach((k,v)->types.put(k,Type.getInternalName(v)));
            for(String line:Files.readAllLines(Path.of("build/createSrgToMcp/output.srg"))) {
                String[] p=line.split(" ");if(p[0].equals("MD:"))methods.put(shortName(p[1]),shortName(p[3]));
                if(p[0].equals("FD:"))fields.put(shortName(p[1]),shortName(p[2]));
            }
            nativeType=loadClass((PREFIX+"Native").replace('/','.'));
            tank=(Port)loadClass((PREFIX+"Tank").replace('/','.')).getConstructor().newInstance();tank.fixture=this;
        }
        static String shortName(String s){return s.substring(s.lastIndexOf('/')+1);}
        Object call(String name) throws Exception{return call(name,new Class<?>[0]);}
        Object call(String name,Class<?>[] args,Object... values)throws Exception{return tank.getClass().getMethod(name,args).invoke(tank,values);}
        Object utility(String name,String method,Class<?>[] args,Object... values) {
            try{return loadClass((PREFIX+name).replace('/','.')).getMethod(method,args).invoke(null,values);}
            catch(Exception e){throw new AssertionError(e);}
        }
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            String original=types.entrySet().stream().filter(e->e.getValue().equals(name.replace('.','/'))).map(Map.Entry::getKey).findFirst().orElseThrow(()->new ClassNotFoundException(name));
            try {
                ClassNode n=new ClassNode();
                if(original.equals(TANK)){try(var in=getParent().getResourceAsStream(TANK+".class")){new ClassReader(in).accept(n,0);}}
                else try(var zip=new ZipFile(Path.of(System.getProperty("dnv.sbw.jar")).toFile())){new ClassReader(zip.getInputStream(zip.getEntry(original+".class"))).accept(n,0);}
                Set<String> keep=original.equals(TANK)?Set.of("travel","hullAimError","adjustTurretAngle","turretAutoAimFromVector","getTurretTurnXSpeed","getTurretTurnYSpeed","getTurretMinPitch","getTurretMaxPitch","firingPivot","groupTransform","getShootVec","getTransformFromString"):
                        original.equals(BASE)?Set.of("engineRunning","getBarrelVector","getBarrelTransform","getTurretTransform","transformPosition","turretAutoAimFromVector","getCameraPos","getZoomPos"):
                        original.equals(ENGINE)?Set.of("trackEngine"):
                        original.equals(AIM)?Set.of("<init>","<clinit>","turretAutoAimFromVector"):
                        Set.of("<init>","<clinit>","getTurretTransform","getBarrelTransform","getXRotFromVector","getYRotFromVector","transformPosition","getCameraPos","getZoomPos");
                n.methods.removeIf(m->!keep.contains(m.name));n.interfaces.clear();n.innerClasses.clear();n.nestMembers=null;n.visibleAnnotations=null;
                if(original.equals(BASE)||original.equals(TANK)) {
                    n.fields.clear();n.access=Opcodes.ACC_PUBLIC;n.superName=original.equals(BASE)?Type.getInternalName(Port.class):BASE;
                    MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
                    init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,n.superName,"<init>","()V",false));
                    init.instructions.add(new InsnNode(Opcodes.RETURN));init.maxStack=1;init.maxLocals=1;n.methods.add(init);
                }
                ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(new ClassRemapper(out,new Remapper(){
                    @Override public String map(String value){return types.getOrDefault(value,value);}
                    @Override public String mapMethodName(String o,String value,String d){return methods.getOrDefault(value,value);}
                    @Override public String mapFieldName(String o,String value,String d){return fields.getOrDefault(value,value);}
                }));byte[] bytes=out.toByteArray();return defineClass(name,bytes,0,bytes.length);
            }catch(Exception e){throw new ClassNotFoundException(name,e);}
        }
    }
    public static class Body {
        public float yaw;public Vec3 velocity=Vec3.ZERO;
        public boolean isAlive(){return true;}public boolean isSpectator(){return false;}
        public Vec3 getViewVector(float p){return Vec3.directionFromRotation(0,yaw);}
        public Vec3 position(){return Vec3.ZERO;}public Vec3 getDeltaMovement(){return velocity;}public void setDeltaMovement(Vec3 value){velocity=value;}
        public void setDeltaMovement(double x,double y,double z){velocity=new Vec3(x,y,z);}
        public float getYRot(){return yaw;}public void setYRot(float value){yaw=value;}public float getXRot(){return 0;}
    }
    public static class Pilot extends Body {
        public float heading,elevation;public boolean alive=true,spectator;
        public boolean isAlive(){return alive;}public boolean isSpectator(){return spectator;}
        @Override public Vec3 getViewVector(float p){return Vec3.directionFromRotation(-elevation,heading);}
    }
    public static class Port extends Body {
        Fixture fixture;public Pilot driver=new Pilot();public World world=new Server();public final Data entityData=new Data();
        public static final Key TURRET_DAMAGED=new Key();
        public boolean left,right,back,forward,sprint,ground=true,local=true,wreck,removed;
        public float power,deltaRot,pitch,turretYaw;public int energy=5_000_000,hold,trackCalls;public double targetSpeed;
        public void travel(){trackCalls++;fixture.utility("Engine","trackEngine",new Class<?>[]{fixture.nativeType,Track.class},this,new Track());}
        public World level(){return world;}public boolean onGround(){return ground;}public boolean isInFluidType(){return false;}public boolean drift(){return false;}
        public Body getControllingPassenger(){return driver;}public Body getFirstPassenger(){return driver;}public Body getNthEntity(int n){return driver;}
        public int getEnergy(){return energy;}public int getMaxEnergy(){return 5_000_000;}public void consumeEnergy(int n){energy-=n;}
        public float getPower(){return power;}public void setPower(float value){power=value;}public VehicleType getVehicleType(){return VehicleType.TANK;}
        public boolean forwardInputDown(){return forward;}public boolean backInputDown(){return back;}public boolean leftInputDown(){return left;}public boolean rightInputDown(){return right;}
        public boolean sprintInputDown(){return sprint;}public boolean upInputDown(){return false;}
        public void setForwardInputDown(boolean v){forward=v;}public void setBackInputDown(boolean v){back=v;}public void setLeftInputDown(boolean v){left=v;}public void setRightInputDown(boolean v){right=v;}public void setSprintInputDown(boolean v){sprint=v;}
        public int getHoldTick(){return hold;}public void setHoldTick(int n){hold=n;}public float getDeltaRot(){return deltaRot;}public void setDeltaRot(float v){deltaRot=v;}
        public double getTargetSpeed(){return targetSpeed;}public void setTargetSpeed(double v){targetSpeed=v;}
        public boolean getLeftWheelDamaged(){return false;}public boolean getRightWheelDamaged(){return false;}public boolean getMainEngineDamaged(){return false;}
        public float getLeftWheelRot(){return 0;}public float getRightWheelRot(){return 0;}public float getLeftTrack(){return 0;}public float getRightTrack(){return 0;}
        public void setLeftWheelRot(float v){}public void setRightWheelRot(float v){}public void setLeftTrack(float v){}public void setRightTrack(float v){}
        public void addRandomParticle(Particle p,Vec3 v,float a,World w,float b,int n){}
        public boolean isControlledByLocalInstance(){return local;}public boolean isWreck(){return wreck;}public boolean isRemoved(){return removed;}public float getHealth(){return 1000;}
        public Data getEntityData(){return entityData;}public float getTurretXRot(){return pitch;}public void setTurretXRot(float v){pitch=v;}
        public float getTurretTurnXSpeed(){return .9F;}public float getTurretTurnYSpeed(){return 0;}public float getTurretMinPitch(){return -3;}public float getTurretMaxPitch(){return 25;}
        public float getTurretYRot(){return turretYaw;}public void setTurretYRot(float v){turretYaw=v;}
        public float getTurretXRotO(){return pitch;}public float getTurretYRotO(){return turretYaw;}public float getTurretPitch(float p){return pitch;}
        public void turretTurnSound(float x,float y,float p){}public void setTurretYRotLock(float v){}
        public float getCustomTurretMaxPitch(){return 0;}public float getCustomTurretMinPitch(){return 0;}public float getTurretMaxYaw(){return 0;}public float getTurretMinYaw(){return 0;}
        public Vec3 getTurretPos(){return Project612Rig.INSTANCE.pivot;}public Vec3 getBarrelPosition(){return Vec3.ZERO;}
        public AABB getBoundingBox(){return new AABB(-2,0,-3,2,4,3);}
        public Matrix4d getVehicleTransform(float p){return new Matrix4d().rotateY(Math.toRadians(-yaw));}
        public Matrix4d getVehicleTransformWithCustomPitch(float p){return getVehicleTransform(p);}
        public Matrix4d getTransformFromString(String name,float p){return getVehicleTransform(p);}
        public int getSeatIndex(Body p){return p==driver?0:-1;}public Computed computed(){return data;}private final Computed data=new Computed();
    }
    public enum VehicleType {TANK,AIRSHIP}
    public static class Key{}
    public static class Data {public Object get(Key key){return false;}}
    public static class World {public boolean isClientSide;}
    public static class Server extends World{}
    public static class Particle{}
    public static class Particles{public static final Particle SPLASH=new Particle(),BUBBLE_COLUMN_UP=new Particle();}
    public static class Computed {public final Seat seat=new Seat();public List<Seat> seats(){return List.of(seat);}}
    public static class Seat {public final Camera camera=new Camera();public Camera getCameraPos(){return camera;}}
    public static class Camera {
        final JsonObject json;Camera(){try{json=JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles/project612.json"))).getAsJsonObject().getAsJsonArray("Seats").get(0).getAsJsonObject().getAsJsonObject("CameraPos");}catch(Exception e){throw new AssertionError(e);}}
        public String getTransform(){return json.get("Transform").getAsString();}public boolean getUseSimulate3P(){return false;}public boolean getUseFixedCameraPos(){return json.get("UseFixedCameraPos").getAsBoolean();}
        public Vec3 getPosition(){return vector("Position");}public Vec3 getZoomPosition(){return vector("ZoomPosition");}
        Vec3 vector(String key){var a=json.getAsJsonArray(key);return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    }
    public static class Track {
        private static final JsonObject DATA=read();
        static JsonObject read(){try{return JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles/project612.json"))).getAsJsonObject().getAsJsonObject("EngineInfo");}catch(Exception e){throw new AssertionError(e);}}
        double d(String k){return DATA.get(k).getAsDouble();}float f(String k){return DATA.get(k).getAsFloat();}
        public double getBuoyancy(){return d("Buoyancy");}public double getEnergyCostRate(){return d("EnergyCostRate");}
        public double getWheelRotSpeed(){return d("WheelRotSpeed");}public double getWheelDifferential(){return d("WheelDifferential");}
        public double getTrackRotSpeed(){return d("TrackRotSpeed");}public double getTrackDifferential(){return d("TrackDifferential");}
        public float getMaxForwardSpeedRate(){return f("MaxForwardSpeedRate");}public float getMaxBackwardSpeedRate(){return f("MaxBackwardSpeedRate");}
        public float getIncrement(){return f("Increment");}public float getDecrement(){return f("Decrement");}public float getSteeringSpeed(){return f("SteeringSpeed");}
    }
}
