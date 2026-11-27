package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.armored.ApocalypseRig;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Compiled DNV joint control + exact SBW aim/camera/Passenger-vector methods.
 * Data and entity storage are ports; neither the manual input dispatch nor angular math is reimplemented. */
public class ApocalypseAimCameraExecutionTest {
    @Test void blizzardNativeAimCanCompleteMultipleTurnsInBothDirectionsWithoutHittingTheYawClamp() throws Exception {
        for (int sign : new int[] {-1, 1}) {
            Loader loader = new Loader("com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity");
            TankBase tank = loader.tank(); tank.minYaw = -360; tank.maxYaw = 360;
            for (int tick = 1; tick <= 2400; tick++) {
                double requested = sign * tick * .4;
                tank.oldYaw = tank.yaw;
                loader.nativeType.getMethod("turretAutoAimFromVector", Vec3.class).invoke(tank, view(-requested, 0));
                float error = net.minecraft.util.Mth.wrapDegrees((float)requested - tank.yaw);
                assertEquals(0, error, .01, "native aim must track across full revolutions");
                float halfway = (float)tank.getClass().getMethod("getTurretYaw", float.class).invoke(tank, .5F);
                assertEquals(0, net.minecraft.util.Mth.wrapDegrees(halfway - (tank.oldYaw + sign * .2F)), .01);
            }
        }
    }
    @Test void manualInputActuallyMovesBothRocketPodsWithNativeTurnSpeedAndIndependentLimits()throws Exception {
        Loader loader=new Loader();TankBase tank=loader.tank();tank.driver.view=view(80,70);
        loader.aim(tank);
        assertEquals(-.9,tank.yaw,1e-5);assertEquals(.9,-tank.pitch,1e-5);assertEquals(.9,tank.entityData.get(TankBase.ROCKET_ELEVATION),1e-5);
        for(int i=0;i<150;i++)loader.aim(tank);
        assertEquals(25,-tank.pitch,1e-4);assertEquals(10,tank.entityData.get(TankBase.ROCKET_ELEVATION),1e-4);
        assertEquals(-80,tank.yaw,.3);assertEquals(view(80,70),tank.driver.view,"Turret limits never overwrite the player's look vector");
        tank.wreck=true;float yaw=tank.yaw,pitch=tank.pitch; tank.driver.view=view(-90,-70);loader.aim(tank);
        assertEquals(yaw,tank.yaw);assertEquals(pitch,tank.pitch);
        // Negative control is the exact old static dispatch: main gun turns, DNV rocket joint is never called.
        TankBase old=loader.tank();old.driver.view=view(80,70);
        loader.utility("Aim").getMethod("adjustTurretAngle",loader.nativeType).invoke(null,old);
        assertTrue(old.pitch<0);assertEquals(0F,old.entityData.get(TankBase.ROCKET_ELEVATION));
    }
    @Test void firstPersonZoomAndSeekAllFollowThePassengerAboveAndAheadOfTheLimitedBarrel()throws Exception {
        Loader loader=new Loader();TankBase tank=loader.tank();
        var json=JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles/apocalypse.json"))).getAsJsonObject();
        var seat=json.getAsJsonArray("Seats").get(0).getAsJsonObject();var camera=seat.getAsJsonObject("CameraPos");
        tank.data.seat.camera.direction=new Direction(camera.get("Direction").getAsString());
        tank.data.seat.camera.zoomDirection=new Direction(camera.get("ZoomDirection").getAsString());
        assertTrue(seat.get("CanRotateHead").getAsBoolean());assertEquals(-85,seat.get("MinPitch").getAsInt());assertEquals(85,seat.get("MaxPitch").getAsInt());
        String seek=json.getAsJsonObject("Weapons").getAsJsonObject("Missiles").getAsJsonObject("SeekWeaponInfo").get("SeekDirection").getAsString();
        for(double yaw:new double[]{-175,0,100})for(double elevation:new double[]{-80,0,80}) {
            tank.driver.view=view(yaw,elevation);tank.yaw=0;tank.pitch=0;
            for(String method:List.of("getCameraDirection","getZoomDirection")) {
                Vec3 result=(Vec3)loader.utility("Vec").getMethod(method,loader.nativeType,Body.class,float.class).invoke(null,tank,tank.driver,.5F);
                assertEquals(tank.driver.view,result,method);
            }
            Vec3 locked=(Vec3)loader.nativeType.getMethod("getVectorFromString",String.class,float.class,int.class).invoke(tank,seek,1F,0);
            assertEquals(tank.driver.view,locked);
        }
    }
    @Test void exhaustUsesBothAuthoredOutletsOnlyForLoadedClientMotionWithinItsBudget()throws Exception {
        Loader loader=new Loader();TankBase tank=loader.tank();tank.world.isClientSide=true;
        for(int i=0;i<60;i++){tank.tickCount=i;loader.exhaust(tank,new Vec3(.1,0,0));}
        assertEquals(40,tank.world.particles.size());
        for(int i=0;i<2;i++)assertEquals(ApocalypseRig.INSTANCE.exhausts.get(i).position(),tank.world.particles.get(i));
        int count=tank.world.particles.size();tank.tickCount=60;
        for(Vec3 motion:List.of(Vec3.ZERO,new Vec3(0,.2,0),new Vec3(5,0,0)))loader.exhaust(tank,motion);
        tank.wreck=true;loader.exhaust(tank,new Vec3(.1,0,0));tank.wreck=false;
        tank.engine=false;loader.exhaust(tank,new Vec3(.1,0,0));tank.engine=true;
        tank.world.isClientSide=false;loader.exhaust(tank,new Vec3(.1,0,0));
        assertEquals(count,tank.world.particles.size());
    }
    private static Vec3 view(double yaw,double elevation){double y=Math.toRadians(yaw),e=Math.toRadians(elevation);return new Vec3(-Math.sin(y)*Math.cos(e),Math.sin(e),Math.cos(y)*Math.cos(e));}
    public record Key<T>(T initial) { }
    public static class Data {
        final Map<Key<?>,Object> values=new HashMap<>();
        @SuppressWarnings("unchecked") public <T>T get(Key<T> key){return (T)values.getOrDefault(key,key.initial);}
        public <T>void set(Key<T> key,T value){values.put(key,value);}
    }
    public static class Body {
        public Vec3 view=new Vec3(0,0,1);public Vec3 getViewVector(float partial){return view;}
        public World level(){return new World();}
    }
    public static class TankBase extends Body {
        public static final Key<Float> ROCKET_ELEVATION=new Key<>(0F);public static final Key<Boolean> TURRET_DAMAGED=new Key<>(false);
        public final Data entityData=new Data();public Body driver=new Body();public boolean wreck,engine=true;
        public final World world=new World();public int tickCount;public float yaw,pitch,oldYaw,minYaw=-180,maxYaw=180;public final Computed data=new Computed();
        public Data getEntityData(){return entityData;}
        public float getTurretTurnYSpeed(){return .6F;}public float getTurretTurnXSpeed(){return .6F;}
        public float getTurretMinPitch(){return -5;}public float getTurretMaxPitch(){return 25;}
        public boolean isWreck(){return wreck;}public boolean engineRunning(){return engine;}@Override public World level(){return world;}
        public int getTurretControllerIndex(){return 0;}public Body getNthEntity(int index){return index==0?driver:null;}
        public Vec3 getBarrelPosition(){return Vec3.ZERO;}public Matrix4d getTurretTransform(float partial){return new Matrix4d();}
        public Matrix4d getVehicleTransform(float partial){return new Matrix4d();}
        public AABB getBoundingBox(){return new AABB(-2,-1,-2,2,1,2);}
        public float getTurretYRot(){return yaw;}public void setTurretYRot(float value){yaw=value;}
        public float getTurretXRot(){return pitch;}public void setTurretXRot(float value){pitch=value;}
        public float getTurretYRotO(){return oldYaw;}public void setTurretYRotO(float value){oldYaw=value;}public float getTurretXRotO(){return pitch;}
        public float getTurretYaw(float partial){return net.minecraft.util.Mth.lerp(partial, oldYaw, yaw);}
        public Vec3 getBarrelVector(float partial){return view(-yaw,-pitch);}
        public float getCustomTurretMaxPitch(){return 0;}public float getCustomTurretMinPitch(){return 0;}
        public float getTurretMaxYaw(){return maxYaw;}public float getTurretMinYaw(){return minYaw;}
        public void setTurretYRotLock(float value){}public void turretTurnSound(float x,float y,float pitch){}
        public int getSeatIndex(Body entity){return entity==driver?0:-1;}public Computed computed(){return data;}
    }
    public static class Computed {public final Seat seat=new Seat();public List<Seat> seats(){return List.of(seat);}}
    public static class Seat {public final Camera camera=new Camera();public Camera getCameraPos(){return camera;}}
    public static class Camera {
        public String transform="Turret";public Direction direction=new Direction("Passenger"),zoomDirection=new Direction("Passenger");
        public boolean getUseSimulate3P(){return false;}public Direction getDirection(){return direction;}public Direction getZoomDirection(){return zoomDirection;}
        public Vec3 getPosition(){return Vec3.ZERO;}public Vec3 getZoomPosition(){return Vec3.ZERO;}
    }
    public record Direction(String value) {public String getString(){return value;}public boolean isString(){return true;}public Vec3 getVec3(){return null;}}
    public static class Client {public static boolean zoomVehicle;}
    public static class Particle { }
    public static class Particles {public static final Particle SMOKE=new Particle();}
    public static class World {
        public boolean isClientSide;public final List<Vec3> particles=new ArrayList<>();
        public void addParticle(Particle p,double x,double y,double z,double dx,double dy,double dz){particles.add(new Vec3(x,y,z));}
    }
    private static class Loader extends ClassLoader {
        static final String SBW="com/atsuishio/superbwarfare/",BASE=SBW+"entity/vehicle/base/VehicleEntity",VEC=SBW+"entity/vehicle/utils/VehicleVecUtils",AIM=SBW+"entity/vehicle/utils/VehicleWeaponUtils";
        static final String PREFIX="com/liucodex/doggiesnewvehicle/compat/aimprobe/";
        final String TANK;
        private final Map<String,String> types=new HashMap<>(),methods=new HashMap<>(),fields=new HashMap<>();
        final Class<?> nativeType;
        Loader()throws Exception { this("com/liucodex/doggiesnewvehicle/armored/DnvApocalypseEntity"); }
        Loader(String source)throws Exception {
            TANK = source;
            types.put(BASE,PREFIX+"Native");types.put(TANK,PREFIX+"Tank");types.put(VEC,PREFIX+"Vec");types.put(AIM,PREFIX+"Aim");
            for(var entry:Map.ofEntries(Map.entry("net/minecraft/world/entity/Entity",Body.class),Map.entry("net/minecraft/world/level/Level",World.class),
                    Map.entry("net/minecraft/network/syncher/SynchedEntityData",Data.class),Map.entry("net/minecraft/network/syncher/EntityDataAccessor",Key.class),
                    Map.entry(SBW+"data/vehicle/DefaultVehicleData",Computed.class),Map.entry(SBW+"data/vehicle/subdata/SeatInfo",Seat.class),
                    Map.entry(SBW+"data/vehicle/subdata/CameraPos",Camera.class),Map.entry(SBW+"data/StringOrVec3",Direction.class),
                    Map.entry(SBW+"event/ClientEventHandler",Client.class),Map.entry("net/minecraft/core/particles/ParticleOptions",Particle.class),
                    Map.entry("net/minecraft/core/particles/SimpleParticleType",Particle.class),Map.entry("net/minecraft/core/particles/ParticleTypes",Particles.class)).entrySet())
                types.put(entry.getKey(),Type.getInternalName(entry.getValue()));
            for(String line:Files.readAllLines(Path.of("build/createSrgToMcp/output.srg"))) {
                String[] p=line.split(" ");if(p[0].equals("MD:"))methods.put(shortName(p[1]),shortName(p[3]));
                if(p[0].equals("FD:"))fields.put(shortName(p[1]),shortName(p[2]));
            }
            nativeType=loadClass((PREFIX+"Native").replace('/','.'));
        }
        private static String shortName(String name){return name.substring(name.lastIndexOf('/')+1);}
        TankBase tank()throws Exception{return (TankBase)utility("Tank").getConstructor().newInstance();}
        Class<?> utility(String name)throws Exception{return loadClass((PREFIX+name).replace('/','.'));}
        void aim(TankBase tank)throws Exception{tank.getClass().getMethod("adjustTurretAngle").invoke(tank);}
        void exhaust(TankBase tank,Vec3 motion)throws Exception{var m=tank.getClass().getDeclaredMethod("emitExhaust",Vec3.class);m.setAccessible(true);m.invoke(tank,motion);}
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            String original=types.entrySet().stream().filter(e->e.getValue().equals(name.replace('.','/'))).map(Map.Entry::getKey).findFirst().orElseThrow(()->new ClassNotFoundException(name));
            try {
                ClassNode node=new ClassNode();
                if(original.equals(TANK)){try(var in=getParent().getResourceAsStream(TANK+".class")){new ClassReader(in).accept(node,0);}}
                else try(var zip=new ZipFile(SbwTestDependency.path().toFile())){new ClassReader(zip.getInputStream(zip.getEntry(original+".class"))).accept(node,0);}
                Set<String> keep=original.equals(TANK)?(TANK.endsWith("DnvBlizzardEntity") ? Set.of("setTurretYRot", "getTurretYaw") : Set.of("adjustTurretAngle","turretAutoAimFromVector","getTurretMinPitch","getTurretMaxPitch","getTurretTurnXSpeed","getTurretTurnYSpeed","emitExhaust")):
                        original.equals(BASE)?Set.of("turretAutoAimFromVector","getVectorFromString"):
                        original.equals(AIM)?Set.of("<init>","<clinit>","adjustTurretAngle","turretAutoAimFromVector"):
                        Set.of("<init>","<clinit>","getCameraDirection","getZoomDirection","getXRotFromVector","getYRotFromVector","transformPosition");
                node.methods.removeIf(m->!keep.contains(m.name)||(original.equals(BASE)&&m.name.equals("getVectorFromString")&&!m.desc.startsWith("(Ljava/lang/String;FI)")));
                node.interfaces.clear();node.innerClasses.clear();node.nestMembers=null;node.visibleAnnotations=null;
                if(original.equals(BASE)||original.equals(TANK)) {
                    node.fields.clear();node.access=Opcodes.ACC_PUBLIC;node.superName=original.equals(BASE)?Type.getInternalName(TankBase.class):BASE;
                    MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
                    init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
                    init.instructions.add(new InsnNode(Opcodes.RETURN));init.maxStack=1;init.maxLocals=1;node.methods.add(init);
                }
                ClassWriter output=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(new ClassRemapper(output,new Remapper(){
                    @Override public String map(String value){return types.getOrDefault(value,value);}
                    @Override public String mapMethodName(String o,String value,String d){return methods.getOrDefault(value,value);}
                    @Override public String mapFieldName(String o,String value,String d){return fields.getOrDefault(value,value);}
                }));
                byte[] bytes=output.toByteArray();return defineClass(name,bytes,0,bytes.length);
            }catch(Exception e){throw new ClassNotFoundException(name,e);}
        }
    }
}
