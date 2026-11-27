package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.compat.SbwTestDependency;
import com.liucodex.doggiesnewvehicle.entity.DnvVehiclePresentation;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the shipped SBW SAT support/side-contact method with production hulls.
 * Only world/entity storage is ported; all OBB math, MTV selection and support logic is upstream bytecode. */
public class NativeVehicleSupportTest {
    @Test void exactNativeContactCannotMoveADeployedSiloAndItsOwnPhysicsCannotAccumulateImpulse()throws Exception{
        var box=NativeHullFixture.type("OBB").getConstructor(org.joml.Vector3d.class,org.joml.Vector3d.class,
                org.joml.Quaterniond.class,NativeHullFixture.Part.class).newInstance(
                new org.joml.Vector3d(.5,63.5,.5),new org.joml.Vector3d(2,1,2),new org.joml.Quaterniond(),NativeHullFixture.Part.BODY);
        var support=new Vehicle(List.of(new NativeHullFixture.Box(box)));support.velocity=new Vec3(.2,0,.1);
        Vec3 anchor=new Vec3(.5,64,.5);var unguarded=new Vehicle();unguarded.point=anchor;
        collide(support,unguarded);assertNotEquals(anchor,unguarded.point,"Exact native negative control displaces the victim");
        var silo=compiledNuclearPosition();silo.anchor=new net.minecraft.core.BlockPos(0,64,0);silo.point=anchor;
        for(int i=0;i<100;i++){
            collide(support,silo);silo.setPos(-8,77,30);silo.setDeltaMovement(new Vec3(4,-3,8));
            silo.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(.1,.2,.3));
            assertEquals(anchor,silo.point);assertEquals(Vec3.ZERO,silo.velocity);
        }
        silo.anchor=null;silo.world.isClientSide=true;silo.setPos(4,5,6);assertEquals(new Vec3(4,5,6),silo.point);
        silo.setDeltaMovement(new Vec3(0,1,0));silo.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,1,0));
        assertEquals(new Vec3(4,5,6),silo.point);assertEquals(Vec3.ZERO,silo.velocity);
    }
    private static Vehicle compiledNuclearPosition()throws Exception{
        String source="com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",target=source+"FixedPoseProbe";
        var node=new ClassNode();try(var in=NativeVehicleSupportTest.class.getClassLoader().getResourceAsStream(source+".class")){
            new ClassReader(in).accept(node,0);
        }
        node.methods.removeIf(m->!Set.of("setPos","setDeltaMovement","move").contains(m.name));
        node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;
        node.superName=Type.getInternalName(Vehicle.class);
        for(var method:node.methods)for(var insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.name.equals("m_20256_"))call.name="setDeltaMovement";
        var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
        var out=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(out,new SimpleRemapper(Map.of(source,target,
                "com/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity",Type.getInternalName(Vehicle.class)))));
        byte[] bytes=out.toByteArray();
        Class<?> type=new ClassLoader(NativeVehicleSupportTest.class.getClassLoader()){Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}}.define();
        return (Vehicle)type.getConstructor().newInstance();
    }
    @Test void exactNativeContactCannotPushAnAlliedTankThroughTheFloorButResolvedMotionStillWorks() throws Exception {
        var boxes=NativeHullFixture.motherPose(Vec3.ZERO,0,null);Vehicle ship=new Vehicle(boxes);
        AABB lower=boxes.stream().map(b->{try{return b.bounds();}catch(Exception e){throw new RuntimeException(e);}})
                .min(Comparator.comparingDouble(b->b.minY)).orElseThrow();
        Vec3 contact=new Vec3(lower.getCenter().x,lower.minY-1.5,lower.getCenter().z);
        AABB floor=new AABB(contact.x-5,contact.y-1,contact.z-5,contact.x+5,contact.y,contact.z+5);
        Vehicle unguarded=new Vehicle(List.of());unguarded.point=contact;collide(ship,unguarded);
        assertTrue(unguarded.point.y<contact.y,"Exact upstream negative control forces the tank below its floor");
        assertTrue(floor.intersects(unguarded.getBoundingBox()));
        Vehicle tank=compiledAlliedPosition();tank.point=contact;tank.world.blocks.add(floor);
        collide(ship,tank);assertEquals(contact,tank.point);
        assertTrue(tank.world.blockQueries>0 && tank.world.blockQueries<=4,"Native SAT retries are bounded to four terrain checks");
        int contactQueries=tank.world.blockQueries;
        tank.setPos(contact.x,contact.y+.5,contact.z);assertEquals(contact.y+.5,tank.point.y);
        tank.vMove(net.minecraft.world.entity.MoverType.SELF,new Vec3(.1,-.05,.1));
        assertEquals(contact.add(.1,.45,.1),tank.point);assertFalse(tank.nativeMotionActive);assertEquals(contactQueries,tank.world.blockQueries);
        tank.failNativeMove=true;
        assertThrows(IllegalStateException.class,()->tank.vMove(net.minecraft.world.entity.MoverType.SELF,Vec3.ZERO));
        assertFalse(tank.nativeMotionActive,"Native movement scope clears even on failure");
        tank.failNativeMove=false;tank.world.blocks.clear();
        tank.setPos(contact.x,contact.y-.1,contact.z);assertEquals(contact.y-.1,tank.point.y,"Clear downward support correction remains available");
        tank.world.loaded=false;
        tank.setPos(contact.x,contact.y-.2,contact.z);assertEquals(contact.y-.1,tank.point.y,"Unloaded terrain cannot authorize correction");
        tank.world.owner=null;
        tank.setPos(contact.x,contact.y-.3,contact.z);assertEquals(contact.y-.3,tank.point.y,"Pre-insertion spawn positioning remains available");
    }

    private static Vehicle compiledAlliedPosition() throws Exception {
        String source="com/liucodex/doggiesnewvehicle/armored/DnvAlliedApocalypseEntity",target=source+"SupportPositionProbe";
        ClassNode node=new ClassNode();
        try(var in=NativeVehicleSupportTest.class.getClassLoader().getResourceAsStream(source+".class")){new ClassReader(in).accept(node,0);}
        node.methods.removeIf(m->!Set.of("setPos","vMove","supportDestinationClear").contains(m.name));
        node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;node.superName=Type.getInternalName(Vehicle.class);
        MethodNode ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
        ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(out,new SimpleRemapper(Map.of(source,target,
                "com/liucodex/doggiesnewvehicle/armored/DnvApocalypseEntity",Type.getInternalName(Vehicle.class),
                "net/minecraft/world/entity/Entity",Type.getInternalName(Body.class),
                "net/minecraft/world/level/Level",Type.getInternalName(World.class),
                "net/minecraft/server/level/ServerLevel",Type.getInternalName(World.class),
                "net/minecraft/world/phys/shapes/VoxelShape",Type.getInternalName(Shape.class),
                "com/atsuishio/superbwarfare/tools/OBB",Type.getInternalName(CollisionBox.class)))));
        byte[] bytes=out.toByteArray();
        Class<?> type=new ClassLoader(NativeVehicleSupportTest.class.getClassLoader()){Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}}.define();
        Vehicle tank=(Vehicle)type.getConstructor().newInstance();tank.world.owner=tank;return tank;
    }
    @Test void anotherVehiclesExactSupportCannotLiftAServerOwnedMothershipWreck() throws Exception {
        var boxes=NativeHullFixture.tracked(false,0,restPose());Vehicle support=new Vehicle(boxes);
        AABB upper=boxes.stream().map(b->{try{return b.bounds();}catch(Exception e){throw new RuntimeException(e);}})
                .max(Comparator.comparingDouble(b->b.maxY)).orElseThrow();
        Vec3 contact=new Vec3(upper.getCenter().x,upper.maxY-.06,upper.getCenter().z);
        Vehicle unguarded=new Vehicle(List.of());unguarded.point=contact;unguarded.velocity=new Vec3(0,-.1,0);
        collide(support,unguarded);assertTrue(unguarded.point.y>contact.y,"Exact upstream negative control lifts the wreck");
        Vehicle wreck=compiledMothershipPosition();wreck.point=contact;wreck.velocity=new Vec3(0,-.1,0);wreck.crashSequence.begin();
        for(int i=0;i<40;i++)collide(support,wreck);
        assertEquals(contact,wreck.point,"Native support must not replace the crash owner's committed position");
        wreck.world.isClientSide=true;wreck.setPos(4,5,6);assertEquals(new Vec3(4,5,6),wreck.point,"Client pose updates stay available");
        Vehicle alive=compiledMothershipPosition();alive.setPos(1,2,3);assertEquals(new Vec3(1,2,3),alive.point);
    }

    private static Vehicle compiledMothershipPosition() throws Exception {
        String source="com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity",target=source+"SupportPositionProbe";
        ClassNode node=new ClassNode();
        try(var in=NativeVehicleSupportTest.class.getClassLoader().getResourceAsStream(source+".class")){new ClassReader(in).accept(node,0);}
        node.methods.removeIf(m->!m.name.equals("setPos"));node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;
        node.superName=Type.getInternalName(Vehicle.class);
        MethodNode ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
        ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(out,new SimpleRemapper(Map.of(source,target,
                "com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Type.getInternalName(Vehicle.class),
                "net/minecraft/world/level/Level",Type.getInternalName(World.class)))));
        byte[] bytes=out.toByteArray();
        Class<?> type=new ClassLoader(NativeVehicleSupportTest.class.getClassLoader()){Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}}.define();
        return (Vehicle)type.getConstructor().newInstance();
    }
    @Test void playerStandsOnMovingMothershipAndBothTrackedBossesWithoutTurningTheirWholeBoundsSolid() throws Exception {
        for(int model=0;model<3;model++) {
            var boxes=model==0?NativeHullFixture.motherPose(new Vec3(271,93,-185),0,null):
                    NativeHullFixture.tracked(model==2,0,restPose());
            Vehicle vehicle=new Vehicle(boxes);vehicle.point=new Vec3(271,93,-185);
            vehicle.xo=270.75;vehicle.zo=-185.1;vehicle.velocity=new Vec3(.25,0,.1);
            // Choose a genuine upward facing part, not the empty AABB space between wings or attachments.
            AABB upper=boxes.stream().map(b->{try{return b.bounds();}catch(Exception e){throw new RuntimeException(e);}})
                    .max(Comparator.comparingDouble(b->b.maxY)).orElseThrow();
            Player player=new Player();player.point=new Vec3(upper.getCenter().x,upper.maxY-.06,upper.getCenter().z);
            player.velocity=new Vec3(0,-.08,0);player.fallDistance=8;
            Vec3 before=player.point;collide(vehicle,player);
            assertTrue(player.onGround,"Model "+model);assertEquals(0,player.fallDistance);
            assertEquals(before.x+.25,player.point.x,1e-7);assertEquals(before.z+.1,player.point.z,1e-7);
            assertTrue(player.point.y>before.y);assertEquals(new Vec3(.25,0,.1),player.velocity);
            Body outside=new Body();outside.point=new Vec3(upper.maxX+100,upper.maxY,upper.maxZ+100);
            Vec3 unchanged=outside.point;collide(vehicle,outside);assertEquals(unchanged,outside.point);assertFalse(outside.onGround);
        }
    }
    @Test void nativeSideContactPushesAnAircraftOutAndIgnoresNoPhysicsAndProjectiles()throws Exception {
        var boxes=NativeHullFixture.motherPose(Vec3.ZERO,0,null);
        Vehicle ship=new Vehicle(boxes);
        // Find an exposed side of the production hull by taking its outermost box.
        AABB side=boxes.stream().map(b->{try{return b.bounds();}catch(Exception e){throw new RuntimeException(e);}})
                .max(Comparator.comparingDouble(b->b.maxX)).orElseThrow();
        Vehicle aircraft=new Vehicle(List.of());aircraft.point=new Vec3(side.maxX-.1,side.getCenter().y-.9,side.getCenter().z);
        Vec3 before=aircraft.point;collide(ship,aircraft);
        assertTrue(aircraft.point.x>before.x,"An aircraft is pushed away from the hull");
        assertFalse(NativeHullFixture.intersects(boxes,aircraft.getBoundingBox()),"No side penetration remains");
        for(Body body:List.of(new Body(),new Projectile())) {
            body.point=before;body.noPhysics=!(body instanceof Projectile);collide(ship,body);
            assertEquals(before,body.point);
        }
        ship.noPhysics=true;Body ignored=new Body();ignored.point=before;collide(ship,ignored);assertEquals(before,ignored.point);
    }
    private static final Loader LOADER=loader();
    private static DnvVehiclePresentation restPose(){return (DnvVehiclePresentation)java.lang.reflect.Proxy.newProxyInstance(
            DnvVehiclePresentation.class.getClassLoader(),new Class<?>[]{DnvVehiclePresentation.class},(p,m,a)->{
                if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==int.class)return 0;
                if(m.getReturnType()==float.class)return 0F;return null;
            });}
    private static Loader loader(){try{return new Loader();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static void collide(Vehicle vehicle,Body body)throws Exception {
        Class<?> type=LOADER.loadClass(Loader.GENERATED.replace('/','.'));
        var method=type.getDeclaredMethod("handleEntityObbCollision",Vehicle.class,Body.class);method.setAccessible(true);
        method.invoke(type.getField("INSTANCE").get(null),vehicle,body);
    }
    public record Shape(AABB bounds){public List<AABB> toAabbs(){return List.of(bounds);}}
    public record CollisionBox(AABB bounds){
        public CollisionBox move(Vec3 delta){return new CollisionBox(bounds.move(delta));}
        public static AABB getWorldAABB(CollisionBox box){return box.bounds;}
        public static boolean isColliding(CollisionBox box,AABB part){return box.bounds.intersects(part);}
    }
    public static class World {
        public boolean isClientSide,loaded=true;public Body owner;public int blockQueries;
        public final List<AABB> blocks=new ArrayList<>();
        public Body getEntity(UUID id){return owner;}
        public boolean hasChunksAt(net.minecraft.core.BlockPos min,net.minecraft.core.BlockPos max){return loaded;}
        public Iterable<Shape> getBlockCollisions(Body actor,AABB query){blockQueries++;return blocks.stream().filter(query::intersects).map(Shape::new).toList();}
    }
    public static class Body {
        public Vec3 point=Vec3.ZERO,velocity=Vec3.ZERO;public double xo,zo;public boolean noPhysics,onGround;public float fallDistance;public int tickCount;
        public final UUID id=UUID.randomUUID();public UUID getUUID(){return id;}
        public double getX(){return point.x;}public double getY(){return point.y;}public double getZ(){return point.z;}
        public AABB getBoundingBox(){return new AABB(point.add(-.3,0,-.3),point.add(.3,1.8,.3));}
        public Vec3 getDeltaMovement(){return velocity;}public void setDeltaMovement(Vec3 v){velocity=v;}
        public void setPos(double x,double y,double z){point=new Vec3(x,y,z);}public boolean onGround(){return onGround;}
        public void setOnGround(boolean value){onGround=value;}
    }
    public static class Vehicle extends Body {
        public net.minecraft.core.BlockPos anchor;
        public void move(net.minecraft.world.entity.MoverType type,Vec3 delta){point=point.add(delta);}
        public final MothershipCrash crashSequence=new MothershipCrash();public final World world=new World();
        public boolean nativeMotionActive,failNativeMove;
        public Vehicle(){this(List.of());}
        private final List<?> boxes;public Vehicle(List<NativeHullFixture.Box> boxes){this.boxes=boxes.stream().map(NativeHullFixture.Box::value).toList();}
        public boolean enableAABB(){return false;}public List<?> getOBBs(){return boxes;}
        public World level(){return world;}public void pushNew(double x,double y,double z){velocity=velocity.add(x,y,z);}
        public CollisionBox getCollisionOBB(){return new CollisionBox(getBoundingBox());}
        public void vMove(net.minecraft.world.entity.MoverType type,Vec3 delta){
            if(failNativeMove)throw new IllegalStateException("Native movement failed");
            setPos(point.x+delta.x,point.y+delta.y,point.z+delta.z);
        }
    }
    public static class Player extends Body {public boolean isCrouching(){return false;}}
    public static class Projectile extends Body { }
    public static class Wreck extends Body {public boolean supportByVehicle;public void setSupportByVehicle(boolean value){supportByVehicle=value;}}
    private static class Loader extends ClassLoader {
        static final String NATIVE="com/atsuishio/superbwarfare/entity/vehicle/utils/VehicleMotionUtils";
        static final String GENERATED="com/liucodex/doggiesnewvehicle/boss/supportprobe/Motion";
        private final Map<String,String> types=new HashMap<>(),methods=new HashMap<>(),fields=new HashMap<>();
        Loader()throws Exception {
            super(NativeHullFixture.loader());types.put(NATIVE,GENERATED);
            types.put("com/atsuishio/superbwarfare/tools/OBB",Type.getInternalName(NativeHullFixture.type("OBB")));
            types.put("com/atsuishio/superbwarfare/tools/OBB$Companion",Type.getInternalName(NativeHullFixture.type("OBB$Companion")));
            types.put("com/atsuishio/superbwarfare/tools/OBB$Part",Type.getInternalName(NativeHullFixture.Part.class));
            for(var entry:Map.of("net/minecraft/world/entity/Entity",Body.class,"net/minecraft/world/entity/player/Player",Player.class,
                    "net/minecraft/world/entity/projectile/Projectile",Projectile.class,"net/minecraft/world/level/Level",World.class,
                    "com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Vehicle.class,
                    "com/atsuishio/superbwarfare/entity/vehicle/TurretWreckEntity",Wreck.class).entrySet())types.put(entry.getKey(),Type.getInternalName(entry.getValue()));
            Path mappings=Path.of("build/createSrgToMcp/output.srg");
            for(String line:Files.exists(mappings)?Files.readAllLines(mappings):List.<String>of()) {
                String[] p=line.split(" ");if(p[0].equals("MD:"))methods.put(shortName(p[1]),shortName(p[3]));
                if(p[0].equals("FD:"))fields.put(shortName(p[1]),shortName(p[2]));
            }
        }
        private static String shortName(String name){return name.substring(name.lastIndexOf('/')+1);}
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            if(!name.replace('.','/').equals(GENERATED))throw new ClassNotFoundException(name);
            try(var zip=new ZipFile(SbwTestDependency.path().toFile())) {
                ClassNode node=new ClassNode();new ClassReader(zip.getInputStream(zip.getEntry(NATIVE+".class"))).accept(node,0);
                node.methods.removeIf(m->!Set.of("<init>","<clinit>","handleEntityObbCollision").contains(m.name));
                node.fields.removeIf(f->!f.name.equals("INSTANCE"));node.innerClasses.clear();node.visibleAnnotations=null;
                ClassWriter output=new ClassWriter(0);node.accept(new ClassRemapper(output,new Remapper(){
                    @Override public String map(String value){return types.getOrDefault(value,value);}
                    @Override public String mapMethodName(String o,String value,String d){
                        if(o.startsWith("net/minecraft/") && value.matches("m_\\d+_") && !methods.containsKey(value))throw new IllegalStateException("Missing Forge mapping: "+value);
                        return methods.getOrDefault(value,value);
                    }
                    @Override public String mapFieldName(String o,String value,String d){
                        if(o.startsWith("net/minecraft/") && value.matches("f_\\d+_") && !fields.containsKey(value))throw new IllegalStateException("Missing Forge mapping: "+value);
                        return fields.getOrDefault(value,value);
                    }
                }));
                byte[] bytes=output.toByteArray();return defineClass(name,bytes,0,bytes.length);
            }catch(Exception e){throw new ClassNotFoundException(name,e);}
        }
    }
}
