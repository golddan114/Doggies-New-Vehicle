package com.liucodex.doggiesnewvehicle.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the compiled admission owner with world and native parameter ports, not a duplicate algorithm. */
class DnvDragonAdmissionExecutionTest {
    @Test void oneAcceptedWorldInsertionPublishesTheFiredBarrelAndImmutableLipOnce() throws Exception {
        var f=new Fixture();f.host.gun.fireIndex.value=1;f.world.accept=true;
        assertTrue(f.admit());assertEquals(1,f.world.calls);assertEquals(1,f.host.effects);
        assertEquals(1,f.world.barrel);assertEquals(new Vec3(1,2,2.97),f.world.lip);
        assertEquals(2,f.world.particles);assertEquals(1,f.host.sounds);assertEquals(1,Packets.last.barrel);
        assertTrue(f.accepted());assertFalse(f.accepted());assertEquals(1,f.host.effects);
    }
    @Test void failedWorldInsertionNeverEmitsSuccessFeedback() throws Exception {
        var f=new Fixture();f.world.accept=false;
        assertFalse(f.admit());assertEquals(1,f.world.calls);assertEquals(0,f.host.effects);
        var pending=f.host.getClass().getDeclaredField("admittedParameters");pending.setAccessible(true);assertNull(pending.get(f.host));
        assertFalse(f.accepted());
    }
    @Test void nestedCallbackKeepsTheOuterAdmissionAndRootScopeReleasesReferences() throws Exception {
        var f=new Fixture();f.world.accept=true;
        f.world.before=()->f.scope(()->{try{assertFalse(f.admit());}catch(Exception e){throw new RuntimeException(e);}});
        f.scope(()->{try{assertTrue(f.admit());assertTrue(f.accepted());}catch(Exception e){throw new RuntimeException(e);}});
        assertEquals(1,f.world.calls);assertEquals(1,f.host.effects);
        var pending=f.host.getClass().getDeclaredField("admittedParameters");pending.setAccessible(true);assertNull(pending.get(f.host));
    }
    @Test void secondarySeatDepartedActorAndChangedDimensionCannotEmitShots()throws Exception {
        var f=new Fixture();f.host.seat=1;assertFalse(f.admit());
        f.host.seat=0;f.host.passenger=false;assertFalse(f.admit());
        f.host.passenger=true;((Actor)f.params.shooter).alive=false;assertFalse(f.admit());
        ((Actor)f.params.shooter).alive=true;f.params.shooter.world=new World();assertFalse(f.admit());
        assertEquals(0,f.world.calls);assertEquals(0,f.host.effects);assertEquals(0,f.host.sounds);
    }
    @Test void revokedCrewWrongGunChangedMuzzleAndForeignWorldFailBeforeWorldWrite() throws Exception {
        var f=new Fixture();f.host.allowed=false;assertFalse(f.admit());assertEquals(0,f.world.calls);
        f.host.allowed=true;f.host.powered=false;assertFalse(f.admit());assertEquals(0,f.world.calls);
        f.host.powered=true;f.params.data=new Gun();assertFalse(f.admit());assertEquals(0,f.world.calls);
        f.params.data=f.host.gun;f.params.shootPosition=new Vec3(100,2,3);assertFalse(f.admit());assertEquals(0,f.world.calls);
        f.params.shootPosition=f.host.origin;f.params.level=new World();assertFalse(f.admit());assertEquals(0,f.world.calls);
        assertEquals(0,f.host.effects);
    }
    private static final class Fixture {
        final World world=new World();final Host host;final Params params=new Params();final java.lang.reflect.Method admit,accepted;
        Fixture() throws Exception {
            String source="com/liucodex/doggiesnewvehicle/entity/DnvM1YunlongEntity",target="com/liucodex/doggiesnewvehicle/entity/probe/DragonAdmission";
            var node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
            node.interfaces.clear();node.innerClasses.clear();node.nestHostClass=null;
            node.methods.removeIf(m->!List.of("admitNativeProjectile","nativeProjectileAccepted","publishAcceptedShot","canShoot","runNativeShotScope").contains(m.name));
            node.fields.removeIf(f->!List.of("admittedParameters","admittedProjectile","admittingProjectile","nativeStateInvalid","indicatorPending","crowbarActor").contains(f.name));
            node.methods.forEach(m->m.access=Opcodes.ACC_PUBLIC);
            String parent=node.superName;
            var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false));
            ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
            var names=new HashMap<String,String>();names.put(source,target);names.put(parent,Type.getInternalName(Host.class));
            names.put("net/minecraft/world/entity/Entity",Type.getInternalName(Body.class));
            names.put("net/minecraft/world/entity/LivingEntity",Type.getInternalName(Actor.class));
            names.put("net/minecraft/world/entity/player/Player",Type.getInternalName(Actor.class));
            names.put("net/minecraft/server/level/ServerPlayer",Type.getInternalName(Actor.class));
            names.put("net/minecraft/world/level/Level",Type.getInternalName(World.class));
            names.put("net/minecraft/server/level/ServerLevel",Type.getInternalName(World.class));
            names.put("com/atsuishio/superbwarfare/data/gun/ShootParameters",Type.getInternalName(Params.class));
            names.put("com/atsuishio/superbwarfare/data/gun/GunData",Type.getInternalName(Gun.class));
            names.put("com/atsuishio/superbwarfare/data/gun/value/IntValue",Type.getInternalName(Cell.class));
            names.put("com/liucodex/doggiesnewvehicle/entity/DnvDragonTurretProfile",Type.getInternalName(Profile.class));
            names.put("com/liucodex/doggiesnewvehicle/entity/DnvM1IndicatorAim","java/lang/Object");
            names.put("com/liucodex/doggiesnewvehicle/api/combat/DnvNativeCrewTarget",Type.getInternalName(Host.class));
            names.put("com/liucodex/doggiesnewvehicle/compat/DnvM1RemoteCrew",Type.getInternalName(Crew.class));
            names.put("com/atsuishio/superbwarfare/tools/ParticleTool",Type.getInternalName(NativeParticles.class));
            names.put("com/atsuishio/superbwarfare/client/particle/CannonMuzzleFlareOption",Type.getInternalName(Flare.class));
            names.put("net/minecraft/core/particles/ParticleTypes",Type.getInternalName(Particles.class));
            names.put("net/minecraft/core/particles/SimpleParticleType",Type.getInternalName(Particle.class));
            names.put("net/minecraft/core/particles/ParticleOptions",Type.getInternalName(Particle.class));
            names.put("com/atsuishio/superbwarfare/tools/MinecraftUtil",Type.getInternalName(Packets.class));
            names.put("net/minecraft/network/protocol/common/custom/CustomPacketPayload","java/lang/Object");
            names.put("com/atsuishio/superbwarfare/network/message/receive/VehicleShootClientMessage",Type.getInternalName(Message.class));
            var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){
                @Override protected String getCommonSuperClass(String a,String b){return "java/lang/Object";}
            };
            node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String n){return names.getOrDefault(n,n);}}));
            var loader=new ClassLoader(getClass().getClassLoader()){
                @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                    if(name.equals(target.replace('/','.'))){byte[] bytes=writer.toByteArray();return defineClass(name,bytes,0,bytes.length);}return super.findClass(name);
                }
            };
            var type=loader.loadClass(target.replace('/','.'));host=(Host)type.getConstructor().newInstance();host.world=world;
            params.level=world;params.ammoSupplier=host;params.shooter=new Actor();params.data=host.gun;
            params.shooter.world=world;
            params.shootPosition=host.origin;params.shootDirection=host.direction;
            admit=type.getMethod("admitNativeProjectile",World.class,Body.class,Params.class);accepted=type.getMethod("nativeProjectileAccepted",Params.class);
        }
        boolean admit()throws Exception{var projectile=new Body();projectile.world=world;return (boolean)admit.invoke(host,world,projectile,params);}
        boolean accepted()throws Exception{return (boolean)accepted.invoke(host,params);}
        void scope(Runnable work){try{host.getClass().getMethod("runNativeShotScope",Runnable.class).invoke(host,work);}
            catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new RuntimeException(e.getCause());}
            catch(Exception e){throw new RuntimeException(e);}}
    }
    public static class World {
        public boolean accept,isClientSide;public int calls,barrel,particles;public Vec3 lip;public Runnable before;
        public boolean addFreshEntity(Body body){calls++;if(before!=null)before.run();return accept;}
        public void broadcastEntityEvent(Body body,byte event){barrel=event-80;}
        public int sendParticles(Particle p,double x,double y,double z,int n,double a,double b,double c,double speed){
            particles+=n;lip=new Vec3(x,y,z);return n;
        }
    }
    public static class Body {public World world;public World level(){return world;}public boolean isRemoved(){return false;}
        public java.util.UUID getUUID(){return new java.util.UUID(1,2);}}
    public static class Actor extends Body{public boolean alive=true;public boolean isAlive(){return alive;}}
    public static class Cell {public int value;public int get(){return value;}}
    public static class Gun {public Cell fireIndex=new Cell();}
    public static class Params {public Body ammoSupplier,shooter;public World level;public Gun data;public Vec3 shootPosition,shootDirection;}
    public static class Profile {public int barrels=2,seats=2;public String entity="dragon_bofors_twin";}
    public static class Particle{}
    public static class Flare extends Particle{public Flare(float r,float g,float b,int life,float scale,int type,float speed){}}
    public static class NativeParticles {
        public static void spawnDirectionalParticles(int n,double spread,World w,Particle option,Vec3 direction,Vec3 lip,double speed){w.particles+=n;w.lip=lip;}
        public static void spawnBarrelSmoke(int n,World w,Vec3 direction,Vec3 lip){w.particles+=n;w.lip=lip;}
        public static void spawnMediumCannonMuzzleParticles(Vec3 direction,Vec3 lip,World w,Body host){w.particles+=10;w.lip=lip;}
        public static void spawnBigCannonMuzzleParticles(Vec3 direction,Vec3 lip,World w,Body host){w.particles+=20;w.lip=lip;}
    }
    public static class Particles{public static final Particle FLAME=new Particle(),SMOKE=new Particle();}
    public static class Message{public final int barrel;public Message(java.util.UUID actor,java.util.UUID host,int barrel,String weapon){this.barrel=barrel;}}
    public static class Packets{public static Message last;public static void sendPacketToAll(Object message){last=(Message)message;}}
    public static class Crew{public static boolean valid(Host host){return host.allowed;}}
    public static class Host extends Body {
        public boolean allowed=true,passenger=true;public Gun gun=new Gun();public int effects,sounds,seat;
        public Vec3 origin=new Vec3(1,2,3),direction=new Vec3(0,0,1);
        public boolean powered=true;public boolean hasOperationalPower(){return powered;}
        public boolean isDestroyed(){return false;}public boolean getLocked(){return false;}
        public boolean canShoot(Actor actor){return allowed;}public Gun getGunData(String name){return gun;}
        public boolean mountedCrew(){return true;}public boolean hasPassenger(Body actor){return passenger;}
        public int getSeatIndex(Body actor){return seat;}public boolean isRemoteOperator(Body actor){return passenger;}
        public Vec3 getShootPos(String name,float partial){return origin;}public Vec3 getShootVec(String name,float partial){return direction;}
        public Profile profile(){return new Profile();}public boolean indicatorValid(Object aim,Actor actor){return false;}
        public boolean indicatorAligned(Object aim){return false;}public List<Body> getPassengers(){return List.of();}
        public boolean crowbarReady(Actor actor){return false;}
        public void afterShoot(Gun data,Vec3 direction){effects++;}
        public void playShootSound3p(Actor actor,Gun data,Vec3 point){sounds++;}
    }
}
