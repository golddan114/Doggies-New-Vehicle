package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

class DnvDragonControlRepairTest {
    @Test void everyPinnedNativeSyncRetainsIncomingMotionForTheLocalTurretPassenger() throws Exception {
        var jars=java.util.List.of(SbwTestDependency.path());
        for(var jar:jars)for(boolean fixed:new boolean[]{false,true})try(var zip=new ZipFile(jar.toFile())) {
            String nativeType="com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity";
            String own="com/liucodex/doggiesnewvehicle/entity/DnvM1YunlongEntity",target="camera/probe/Sync";
            var node=new ClassNode();try(var in=zip.getInputStream(zip.getEntry(nativeType+".class"))){new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
            node.methods.removeIf(m->!m.name.equals("handleClientSync"));node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;
            if(fixed){var dnv=new ClassNode();try(var in=getClass().getResourceAsStream("/"+own+".class")){new ClassReader(in.readAllBytes()).accept(dnv,ClassReader.SKIP_FRAMES);}
                node.methods.add(dnv.methods.stream().filter(m->m.name.equals("isControlledByLocalInstance")).findFirst().orElseThrow());}
            node.superName=Type.getInternalName(MotionHost.class);node.access=Opcodes.ACC_PUBLIC;node.version=Opcodes.V17;
            var init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));init.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(init);
            var map=Map.of(nativeType,target,own,target,"net/minecraft/world/level/Level",Type.getInternalName(PoseWorld.class));
            // Keep foreign bytecode names literal at runtime; the Neo source projector rewrites plain SRG strings.
            var methodNames=Map.ofEntries(Map.entry("m_9236_","level"),Map.entry("m_"+"20185_","getX"),Map.entry("m_"+"20186_","getY"),Map.entry("m_"+"20189_","getZ"),
                Map.entry("m_146908_","getYRot"),Map.entry("m_146909_","getXRot"),Map.entry("m_146922_","setYRot"),Map.entry("m_146926_","setXRot"),
                Map.entry("m_6034_","setPos"),Map.entry("m_14175_","wrapDegrees"),Map.entry("m_14177_","wrapDegrees"),Map.entry("m_6109_","isControlledByLocalInstance"));
            var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(writer,new Remapper(){
                @Override public String map(String name){return map.getOrDefault(name,name);}
                @Override public String mapFieldName(String owner,String name,String desc){return name.equals("f_46443_")?"isClientSide":name;}
                @Override public String mapMethodName(String owner,String name,String desc){
                    if(desc.equals("(DDD)V")&&!name.equals("setPos")&&!name.equals("m_6034_"))return "syncPacketPositionCodec";
                    return methodNames.getOrDefault(name,name);
                }
            }));
            var type=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
            var host=(MotionHost)type.getConstructor().newInstance();type.getMethod("handleClientSync").invoke(host);
            assertEquals(fixed?9:0,host.interpolationSteps,jar.toString());assertEquals(fixed?1:0,host.x,1e-9);
            assertEquals(fixed?0:1,host.codecWrites);
        }
    }
    @Test void nativeSeatAndCameraPreviousFieldsFollowTheVisualShortestArc() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",PoseWorld.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/network/syncher/SynchedEntityData",PoseData.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/network/syncher/EntityDataAccessor",PoseKey.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/entity/DnvM1YunlongEntity",
                Set.of("synchronizeTurretPose"),names,PoseHost.class,false);
        var host=(PoseHost)type.getConstructor().newInstance();
        var tick=type.getMethod("synchronizeTurretPose");
        host.entityData.pose=new net.minecraft.core.Rotations(179,10,0);tick.invoke(host);
        host.entityData.pose=new net.minecraft.core.Rotations(-179,12,0);tick.invoke(host);
        assertEquals(-179,host.yaw);assertEquals(-181,host.oldYaw);
        assertEquals(-180,net.minecraft.util.Mth.lerp(.5F,host.oldYaw,host.yaw));
        assertEquals(180,Math.abs(host.clientTurretPose.yaw(.5F,host.entityData.pose)));
        assertEquals(11,net.minecraft.util.Mth.lerp(.5F,host.oldPitch,host.pitch));
    }
    @Test void allFiveSeatsTouchTheRuntimeCushionsWithActualVanillaRidingGeometry() throws Exception {
        float hipContact = seatedHipContact();
        assertEquals(.5882301, hipContact, 1e-5);
        int checked = 0;
        for (String model : new String[]{"bofors_twin", "bofors_quad", "flak38"}) {
            var p = DnvDragonTurretProfile.ALL.get("dragon_" + model);
            var json = JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles",p.entity+".json"))).getAsJsonObject();
            for(int index=0;index<p.seats;index++) {
                var seat=json.getAsJsonArray("Seats").get(index).getAsJsonObject();
                var a=seat.getAsJsonArray("Position");var root=new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
                assertEquals("Turret",seat.get("Transform").getAsString());assertEquals(p.nativeSeatPosition(index),root);
                // Independent surface evidence: actual runtime top faces under the seated pelvis.
                double cushionY=runtimeCushionTop(model,root.x,model.equals("flak38")?-1.3563504:-1.12);
                assertEquals(cushionY,p.yaw.y+root.y+hipContact,1e-5);
                assertEquals(model.equals("flak38")?-1.3563504:-1.12,root.z,1e-5);
                var old=p.seatPositions.get(index).add(0,model.equals("flak38")?-.75:0,0);
                assertTrue(Math.abs(p.yaw.y+old.y+hipContact-cushionY)>.25,"1.1.77 must fail the independent contact check");
                for(float yaw:new float[]{0,45,90,179,-179,270}) {
                    var f=new ExactSbwTransformFixture();f.vehicle.turretPos=p.yaw;f.vehicle.turretYaw=yaw;
                    var matrix=f.vehicle.getTurretTransform(1);
                    var world=matrix.transformPosition(root.x,root.y,root.z,new org.joml.Vector3d());
                    var contact=p.yawed(new Vec3(root.x,cushionY,root.z),-yaw);
                    assertEquals(contact.y,world.y+hipContact,1e-5);
                    assertEquals(contact.x,world.x,2e-4);assertEquals(contact.z,world.z,2e-4);
                }
                checked++;
            }
        }
        assertEquals(5,checked);
    }
    private static float seatedHipContact() throws Exception {
        var mesh=net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE,false);
        var root=net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh,64,64).bakeRoot();
        var leg=root.getChild("right_leg");
        var node=new ClassNode();
        try(var in=net.minecraft.client.model.HumanoidModel.class.getResourceAsStream("HumanoidModel.class")){
            new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);
        }
        // Read actual riding constants from this Minecraft line, not a second hard-coded pose.
        var anim=node.methods.stream().filter(m->m.name.equals("setupAnim")&&!m.name.equals("<init>")&&m.desc.contains("FFFFF")).findFirst().orElseThrow();
        boolean riding=false;int found=0;String part=null;Float value=null;
        for(var insn:anim.instructions) {
            if(insn instanceof FieldInsnNode field && field.getOpcode()==Opcodes.GETFIELD && field.name.equals("riding"))riding=true;
            if(!riding)continue;
            if(insn instanceof FieldInsnNode field && field.getOpcode()==Opcodes.GETFIELD)part=field.name;
            if(insn instanceof LdcInsnNode ldc && ldc.cst instanceof Float number)value=number;
            if(insn instanceof FieldInsnNode field && field.getOpcode()==Opcodes.PUTFIELD && "rightLeg".equals(part) && value!=null) {
                if(field.name.equals("xRot")){leg.xRot=value;found++;}
                if(field.name.equals("yRot")){leg.yRot=value;found++;}
                if(field.name.equals("zRot")){leg.zRot=value;found++;}
                if(found==3)break;
            }
        }
        assertEquals(3,found);
        var stack=new com.mojang.blaze3d.vertex.PoseStack();
        stack.scale(-.9375F,-.9375F,.9375F);stack.translate(0,-1.501F,0);
        float[] result={Float.NaN};
        leg.visit(stack,(pose,path,index,cube)->{
            var point=new org.joml.Vector3f(0,cube.minY/16,cube.maxZ/16);
            pose.pose().transformPosition(point);result[0]=point.y;
        });
        return result[0];
    }
    private static double runtimeCushionTop(String model,double centerX,double centerZ)throws Exception {
        var bytes=Files.readAllBytes(Path.of("src/artillery/resources/assets/doggiesnewvehicle/meshes/dragon_"+model+".etm"));
        var b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);b.position(8);int parts=b.getInt();
        double highest=Double.NEGATIVE_INFINITY;
        for(int part=0;part<parts;part++) {
            int length=Short.toUnsignedInt(b.getShort());byte[] name=new byte[length];b.get(name);
            boolean yaw=new String(name,java.nio.charset.StandardCharsets.UTF_8).equals("yaw_assembly");int count=b.getInt()+b.getInt();
            for(int vertex=0;vertex<count;vertex++) {
                float x=b.getFloat(),y=b.getFloat(),z=b.getFloat();b.position(b.position()+20);
                if(yaw&&Math.abs(x-centerX)<.261&&Math.abs(z-centerZ)<.231&&y>1.3&&y<(model.equals("flak38")?1.5:1.65))highest=Math.max(highest,y);
            }
        }
        assertTrue(Double.isFinite(highest));return highest;
    }
    @Test void flak38RuntimeSightFrameSamplesTheFlatMetalPaletteInsteadOfOtherPaintedIslands() throws Exception {
        var bytes=Files.readAllBytes(Path.of("src/artillery/resources/assets/doggiesnewvehicle/meshes/dragon_flak38.etm"));
        var b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);b.position(8);int parts=b.getInt(),corners=0;
        var atlas=javax.imageio.ImageIO.read(Path.of("src/artillery/resources/assets/doggiesnewvehicle/textures/entity/dragon_flak38.png").toFile());
        for(int part=0;part<parts;part++){
            int length=Short.toUnsignedInt(b.getShort());byte[] name=new byte[length];b.get(name);
            boolean pitch=new String(name,java.nio.charset.StandardCharsets.UTF_8).equals("pitch_assembly");
            int count=b.getInt()+b.getInt();
            for(int vertex=0;vertex<count;vertex++){
                b.position(b.position()+24);float u=b.getFloat(),v=b.getFloat();
                if(pitch&&u>=1008F/1024-.000001&&u<=1016F/1024+.000001&&v>=8F/1024-.000001&&v<=16F/1024+.000001){
                    corners++;assertEquals(0x182028,atlas.getRGB(Math.min(1023,(int)(u*1024)),Math.min(1023,(int)(v*1024)))&0xffffff);
                }
            }
        }
        assertEquals(96,corners,"All 32 existing frame triangles use the runtime renderer's atlas; PNG existence is insufficient");
        assertFalse(b.hasRemaining());
    }
    @Test void compiledReadinessDistinguishesMountedFromRemoteAndNeverIgnoresInvalidState() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/data/gun/GunData",Gun.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/data/gun/value/IntValue",Cell.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Power.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/entity/DnvM1YunlongEntity",
            Set.of("hasOperationalPower","remoteCrewReadiness"),names,Power.class,false);
        var body=(Power)type.getConstructor().newInstance();
        var power=type.getMethod("hasOperationalPower");var reason=type.getMethod("remoteCrewReadiness");
        assertFalse((boolean)power.invoke(body));assertEquals("inactive",reason.invoke(body));
        body.active=true;assertFalse((boolean)power.invoke(body));assertEquals("unpowered",reason.invoke(body));
        body.energy=8;assertTrue((boolean)power.invoke(body));assertEquals("ready",reason.invoke(body));
        body.gun.ammo.value=0;assertEquals("no_ammo",reason.invoke(body));
        body.gun.virtualAmmo.value=1;assertEquals("ready",reason.invoke(body));body.gun.virtualAmmo.value=0;
        body.gun.backup=true;assertEquals("ready",reason.invoke(body));body.gun.backup=false;
        body.gun.reloading=true;assertEquals("ready",reason.invoke(body));body.gun.reloading=false;
        body.gun=null;assertEquals("no_ammo",reason.invoke(body));
        body.mounted=true;body.energy=0;body.active=false;body.storage=false;
        assertTrue((boolean)power.invoke(body));assertEquals("mounted",reason.invoke(body));
        body.destroyed=true;assertFalse((boolean)power.invoke(body));assertEquals("unavailable",reason.invoke(body));
        body.destroyed=false;body.nativeStateInvalid=true;assertFalse((boolean)power.invoke(body));
    }

    @Test void actualNativeCameraGetterSwapsConfigZIntoRetreatForEveryPinnedSbw() throws Exception {
        var jars=java.util.List.of(SbwTestDependency.path());
        for(var jar:jars)try(var zip=new ZipFile(jar.toFile())) {
            String source="com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",target="camera/probe/Vehicle";
            var node=new ClassNode();try(var in=zip.getInputStream(zip.getEntry(source+".class"))){new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
            node.methods.removeIf(m->!m.name.equals("getThirdPersonCameraPosition"));node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;
            node.superName=Type.getInternalName(CameraHost.class);node.access=Opcodes.ACC_PUBLIC;node.version=Opcodes.V17;
            var init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));init.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(init);
            var map=Map.of(source,target,"com/atsuishio/superbwarfare/data/vehicle/DefaultVehicleData",Type.getInternalName(CameraData.class),
                "com/atsuishio/superbwarfare/event/ClientMouseHandler",Type.getInternalName(Scroll.class));
            var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(writer,new Remapper(){
                @Override public String map(String name){return map.getOrDefault(name,name);}
                @Override public String mapFieldName(String owner,String name,String desc){
                    if(owner.equals("net/minecraft/world/phys/Vec3"))return switch(name){case "f_82479_"->"x";case "f_82480_"->"y";case "f_82481_"->"z";default->name;};return name;
                }
            }));
            var type=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
            var host=(CameraHost)type.getConstructor().newInstance();var getter=type.getMethod("getThirdPersonCameraPosition");
            Scroll.custom3pDistanceLerp=1.25;
            for(var p:DnvDragonTurretProfile.ALL.values()){
                var data=JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles",p.entity+".json"))).getAsJsonObject();
                var a=data.getAsJsonArray("ThirdPersonCameraPos");host.data.position=new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
                var result=(Vec3)getter.invoke(host);
                assertEquals(host.data.position.z+1.25,result.x,1e-8,jar.toString());
                assertEquals(0,result.z,1e-8,"The model must not be pushed sideways out of the frame");
                assertTrue(result.x>4,"Both normal and reverse third-person require positive retreat");
            }
        }finally{Scroll.custom3pDistanceLerp=0;}
    }

    @Test void physicalBodyNoLongerIncludesTheFullBarrelAndCullingStillContainsEveryNeutralCorner(){
        for(var p:DnvDragonTurretProfile.ALL.values()){
            assertTrue(p.width()<Math.max(p.maximum.x-p.minimum.x,p.maximum.z-p.minimum.z));
            for(double x:new double[]{p.minimum.x,p.maximum.x})for(double y:new double[]{p.minimum.y,p.maximum.y})for(double z:new double[]{p.minimum.z,p.maximum.z})
                assertTrue(new Vec3(x,y,z).length()<p.renderRadius);
            if(p.seats>0)assertEquals(0,p.servo);else assertTrue(p.servo>0);
        }
    }
    public static class Power {
        public Gun gun=new Gun();public Gun getGunData(String name){return gun;}
        public boolean nativeStateInvalid,mounted,active,destroyed,locked,storage=true;public int energy;
        public boolean mountedCrew(){return mounted;}public boolean isDestroyed(){return destroyed;}
        public boolean getActive(){return active;}public boolean getLocked(){return locked;}
        public boolean hasEnergyStorage(){return storage;}public int getEnergy(){return energy;}
    }
    public static class Cell {public int value;public int get(){return value;}}
    public static class Gun {
        public Cell ammo=new Cell(),virtualAmmo=new Cell();public boolean backup,reloading;
        public Gun(){ammo.value=1;}
        public boolean reloading(){return reloading;}public boolean hasBackupAmmo(Power body){return backup;}
    }
    public static class CameraHost {public final CameraData data=new CameraData();public CameraData computed(){return data;}}
    public static class CameraData {public Vec3 position;public Vec3 getThirdPersonCameraPos(){return position;}}
    public static class Scroll {public static double custom3pDistanceLerp;}
    public static class PoseWorld {public boolean isClientSide=true;}
    public static class MotionHost {
        public PoseWorld world=new PoseWorld();public int interpolationSteps=10,codecWrites;
        public double xO=10,yO=20,zO=30,x,y,z;public float serverYaw=90,serverPitch=10,yaw,pitch;
        public PoseWorld level(){return world;}public boolean isControlledByLocalInstance(){return world.isClientSide;}
        public double getX(){return x;}public double getY(){return y;}public double getZ(){return z;}
        public float getYRot(){return yaw;}public float getXRot(){return pitch;}
        public void setYRot(float v){yaw=v;}public void setXRot(float v){pitch=v;}
        public float getServerYaw(){return serverYaw;}public float getServerPitch(){return serverPitch;}
        public void setServerYaw(float v){serverYaw=v;}public void setServerPitch(float v){serverPitch=v;}
        public void setPos(double a,double b,double c){x=a;y=b;z=c;}
        public void syncPacketPositionCodec(double a,double b,double c){codecWrites++;}
    }
    public static class PoseKey {}
    public static class PoseData {public net.minecraft.core.Rotations pose;public Object get(PoseKey key){return pose;}public void set(PoseKey key,Object value){pose=(net.minecraft.core.Rotations)value;}}
    public static class PoseHost {
        public static final PoseKey SYNCED_TURRET_POSE=new PoseKey();public PoseData entityData=new PoseData();
        public com.liucodex.doggiesnewvehicle.entity.DnvTurretPoseInterpolation clientTurretPose=new com.liucodex.doggiesnewvehicle.entity.DnvTurretPoseInterpolation();
        public float yaw,pitch,oldYaw,oldPitch;public PoseWorld level(){return new PoseWorld();}
        public void setTurretYRot(float x){yaw=x;}public void setTurretXRot(float x){pitch=x;}
        public void setTurretYRotO(float x){oldYaw=x;}public void setTurretXRotO(float x){oldPitch=x;}
        public float getTurretYRot(){return yaw;}public float getTurretXRot(){return pitch;}
        public void turretTurnSound(float x,float y,float z){}
    }
}
