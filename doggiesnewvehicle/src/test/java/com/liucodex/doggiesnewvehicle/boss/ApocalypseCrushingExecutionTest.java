package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.armored.ApocalypseRig;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Compiled contact owner with exact SBW OBB math; entity, damage and world ports are deterministic fixtures. */
public class ApocalypseCrushingExecutionTest {
    private static final Class<?> SUBJECT=compile();
    private static Object create()throws Exception{return SUBJECT.getConstructor().newInstance();}
    private static void tick(Object owner,Tank tank)throws Exception{SUBJECT.getMethod("tick",Tank.class).invoke(owner,tank);}

    @Test void cappedForwardSpeedCrushesAndThePosedFrontBladeDoesMoreDamageWithOneHitPerHalfSecond()throws Exception {
        for(double yaw:new double[]{0,90,180,270}) {
            Tank tank=new Tank(yaw);Living front=tank.target(new Vec3(0,.9,6.2));Living body=tank.target(new Vec3(0,.9,3));
            Object owner=create();tick(owner,tank);
            assertEquals(1,front.hits);assertEquals(1,body.hits);assertTrue(body.damage>0);
            assertEquals(body.damage*1.5,front.damage,1e-5);assertSame(tank.driver,front.source.owner);
            assertTrue(front.push.dot(tank.motion)>0);
            tick(owner,tank);for(int i=1;i<10;i++){tank.world.now=i;tick(owner,tank);}
            assertEquals(1,front.hits);tank.world.now=10;tick(owner,tank);assertEquals(2,front.hits);
        }
    }
    @Test void reverseUsesBodyDamageAndStationaryOrOverheadContactCannotCut()throws Exception {
        Tank tank=new Tank(0);Living rear=tank.target(new Vec3(0,.9,-3.5));Living front=tank.target(new Vec3(0,.9,6.2));
        tank.motion=new Vec3(0,0,-.075);Object owner=create();tick(owner,tank);
        assertTrue(rear.damage>0);assertEquals(0,front.hits);
        tank.world.now=20;tank.motion=Vec3.ZERO;tick(owner,tank);assertEquals(1,rear.hits);
        Tank high=new Tank(0);Living overhead=high.target(new Vec3(0,3.4,1));overhead.box=box(overhead.position,.1,.1);
        Living gap=high.target(new Vec3(0,4.1,1.5));gap.box=box(gap.position,.05,.05);
        tick(create(),high);assertEquals(0,overhead.hits);assertEquals(0,gap.hits);
    }
    @ParameterizedTest @ValueSource(strings={"client","airborne","wreck","health","removed","disabled","spectator","creative","protectedEffect","passenger","ally","driverAlly","pvp","behind"})
    void rejectedContactsCannotWriteDamageOrKnockback(String reason)throws Exception {
        Tank tank=new Tank(0);Living target=tank.target(new Vec3(0,.9,6.2));
        switch(reason) {
            case "client"->tank.world.isClientSide=true;case "airborne"->tank.ground=false;case "wreck"->tank.wreck=true;
            case "health"->tank.health=0;case "removed"->tank.removed=true;case "disabled"->tank.allowed=false;
            case "spectator"->target.spectator=true;case "creative"->target.protectedPlayer=true;
            case "protectedEffect"->target.protection=true;case "passenger"->target.vehicle=tank;case "ally"->target.team=tank.team;
            case "driverAlly"->target.team=tank.driver.team;
            case "pvp"->{tank.world.targets.clear();Player player=new Player();player.position=target.position;player.box=target.box;tank.world.targets.add(player);target=player;((Player)tank.driver).pvp=false;}
            case "behind"->target.motion=tank.motion.scale(2);
            default->fail(reason);
        }
        tick(create(),tank);assertEquals(0,target.hits);assertEquals(Vec3.ZERO,target.push);assertEquals(0,tank.world.sounds);
    }

    public static class Registry { }
    public static class Effect { }
    public static class Sound { }
    public record Holder(Object value){public Object get(){return value;}}
    public static class Effects {public static final Holder STRIKE_PROTECTION=new Holder(new Effect());}
    public static class Sounds {public static final Holder VEHICLE_STRIKE=new Holder(new Sound());}
    public record Damage(Entity direct,Entity owner){ }
    public static class DamageTypes {public static Damage causeVehicleStrikeDamage(Registry registry,Entity direct,Entity owner){return new Damage(direct,owner);}}
    public static class DamagePort {public static boolean doDamage(Entity target,Damage source,float amount){target.hits++;target.damage+=amount;target.source=source;return true;}}
    public static class Policy {public static boolean isProtectedPlayerTarget(Entity entity){return entity.protectedPlayer;}}
    public static class Entity {
        public final UUID id=UUID.randomUUID();public Vec3 position=Vec3.ZERO,motion=Vec3.ZERO,push=Vec3.ZERO;
        public AABB box=box(position,.3,.9);public boolean removed,spectator,protectedPlayer;public Object team=new Object();
        public Entity vehicle;public int hits;public float damage;public Damage source;
        public UUID getUUID(){return id;}public boolean isAlive(){return !removed;}public boolean isSpectator(){return spectator;}
        public Entity getVehicle(){return vehicle;}public boolean isAlliedTo(Entity other){return team==other.team;}
        public Stream<Entity> getSelfAndPassengers(){return Stream.of(this);}
        public AABB getBoundingBox(){return box;}public Vec3 position(){return position;}public Vec3 getDeltaMovement(){return motion;}
        public void push(double x,double y,double z){push=push.add(x,y,z);}
    }
    public static class Living extends Entity {public boolean protection;public boolean hasEffect(Effect ignored){return protection;}}
    public static class Player extends Living {public boolean pvp=true;public boolean canHarmPlayer(Player other){return pvp;}}
    public static class Boat extends Entity { }
    public static class Cart extends Entity { }
    public static class Vehicle extends Entity {public float getMass(){return 80;}}
    public static class Tank extends Vehicle {
        public final World world=new World();public final List<ContactBox> boxes=new ArrayList<>();public final Matrix4d transform;
        public final Entity driver=new Player();public boolean ground=true,wreck,allowed=true;public float health=3000;
        public Tank(double yaw)throws Exception {
            transform=new Matrix4d().rotateY(java.lang.Math.toRadians(-yaw));
            motion=ApocalypseRig.transformDirection(transform,new Vec3(0,0,1)).scale(.225);
            for(var part:ApocalypseRig.INSTANCE.boxes) {
                Matrix4d at=new Matrix4d(transform).mul(ApocalypseRig.INSTANCE.local(part.transform(),0,0,0,0));
                Vec3 center=ApocalypseRig.transformPoint(at,part.center());
                var kind=switch(part.part()){case "WheelLeft"->NativeHullFixture.Part.WHEEL_LEFT;case "WheelRight"->NativeHullFixture.Part.WHEEL_RIGHT;case "Turret"->NativeHullFixture.Part.TURRET;default->NativeHullFixture.Part.BODY;};
                boxes.add(new ContactBox(vec(center),vec(part.half()),at.getNormalizedRotation(new Quaterniond()),kind));
            }
            box=boxes.get(0).nativeBox.bounds();for(var part:boxes)box=box.minmax(part.nativeBox.bounds());
        }
        public Living target(Vec3 local){Living target=new Living();target.position=ApocalypseRig.transformPoint(transform,local);target.box=box(target.position,.3,.9);world.targets.add(target);return target;}
        public World level(){return world;}public boolean isRemoved(){return removed;}public boolean isWreck(){return wreck;}public float getHealth(){return health;}
        public boolean canCrushEntities(){return allowed;}public boolean onGround(){return ground;}
        public Matrix4d getVehicleTransform(float partial){return new Matrix4d(transform);}public Entity getFirstPassenger(){return driver;}
        public List<ContactBox> getOBBs(){return boxes;}public SoundSource getSoundSource(){return SoundSource.NEUTRAL;}
        public boolean isInObb(Entity target,Vec3 movement){return boxes.stream().anyMatch(b->ContactBox.isColliding(b,target.box.move(movement.scale(-1))));}
    }
    public static class World {
        public boolean isClientSide;public long now;public int sounds;public final List<Entity> targets=new ArrayList<>();
        public long getGameTime(){return now;}public Registry registryAccess(){return new Registry();}
        public List<Entity> getEntities(Entity ignored,AABB bounds,Predicate<Entity> filter){return targets.stream().filter(e->bounds.intersects(e.box)).filter(filter).toList();}
        public void playSound(Player player,Entity tank,Sound sound,SoundSource channel,float volume,float pitch){sounds++;}
    }
    public static class ContactBox {
        public final NativeHullFixture.Part part;public final NativeHullFixture.Box nativeBox;
        public ContactBox(Vector3d center,Vector3d half,Quaterniond rotation,NativeHullFixture.Part part) {
            this.part=part;
            try{nativeBox=new NativeHullFixture.Box(NativeHullFixture.type("OBB").getConstructor(Vector3d.class,Vector3d.class,Quaterniond.class,NativeHullFixture.Part.class).newInstance(center,half,rotation,part));}
            catch(Exception failure){throw new AssertionError(failure);}
        }
        public static boolean isColliding(ContactBox box,AABB other){try{return NativeHullFixture.intersects(List.of(box.nativeBox),other);}catch(Exception failure){throw new AssertionError(failure);}}
    }
    private static Vector3d vec(Vec3 point){return new Vector3d(point.x,point.y,point.z);}
    private static AABB box(Vec3 center,double halfWidth,double halfHeight){return new AABB(center.add(-halfWidth,-halfHeight,-halfWidth),center.add(halfWidth,halfHeight,halfWidth));}
    private static Class<?> compile() {
        String original="com/liucodex/doggiesnewvehicle/armored/ApocalypseCrushing",generated=original+"Probe";
        Map<String,String> types=new HashMap<>();types.put(original,generated);
        for(var entry:Map.ofEntries(
                Map.entry("com/liucodex/doggiesnewvehicle/armored/DnvApocalypseEntity",Tank.class),Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Vehicle.class),
                Map.entry("net/minecraft/world/entity/Entity",Entity.class),Map.entry("net/minecraft/world/entity/LivingEntity",Living.class),Map.entry("net/minecraft/world/entity/player/Player",Player.class),
                Map.entry("net/minecraft/world/entity/vehicle/Boat",Boat.class),Map.entry("net/minecraft/world/entity/vehicle/Minecart",Cart.class),Map.entry("net/minecraft/world/level/Level",World.class),
                Map.entry("net/minecraft/core/RegistryAccess",Registry.class),Map.entry("net/minecraft/world/damagesource/DamageSource",Damage.class),Map.entry("net/minecraft/world/effect/MobEffect",Effect.class),
                Map.entry("net/minecraft/sounds/SoundEvent",Sound.class),Map.entry("net/minecraftforge/registries/RegistryObject",Holder.class),
                Map.entry("com/atsuishio/superbwarfare/init/ModDamageTypes",DamageTypes.class),Map.entry("com/atsuishio/superbwarfare/init/ModMobEffects",Effects.class),Map.entry("com/atsuishio/superbwarfare/init/ModSounds",Sounds.class),
                Map.entry("com/atsuishio/superbwarfare/tools/DamageHandler",DamagePort.class),Map.entry("com/atsuishio/superbwarfare/tools/OBB",ContactBox.class),Map.entry("com/atsuishio/superbwarfare/tools/OBB$Part",NativeHullFixture.Part.class),
                Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvCombatPolicyRuntime",Policy.class)).entrySet())types.put(entry.getKey(),Type.getInternalName(entry.getValue()));
        try(var input=ApocalypseCrushingExecutionTest.class.getClassLoader().getResourceAsStream(original+".class")) {
            ClassWriter writer=new ClassWriter(0);new ClassReader(input).accept(new ClassRemapper(writer,new SimpleRemapper(types)),0);byte[] bytes=writer.toByteArray();
            return new ClassLoader(ApocalypseCrushingExecutionTest.class.getClassLoader()){Class<?> define(){return defineClass(generated.replace('/','.'),bytes,0,bytes.length);}}.define();
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
}
