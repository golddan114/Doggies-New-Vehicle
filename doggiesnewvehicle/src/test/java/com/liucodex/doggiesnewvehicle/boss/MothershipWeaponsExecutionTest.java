package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.ClassNode;

/** Runs the compiled firing adapter against insertion, collision and audio ports; no Minecraft boot. */
public class MothershipWeaponsExecutionTest {
    @Test void laserHitRemovesScentOnceWhileOrdinaryWeaponsAndBlockedOrCanceledBeamsCannot() throws Exception {
        for (int scenario=0;scenario<6;scenario++) {
            Actor ship=new Actor(new World()); LivingActor scent=new LivingActor(ship.world,10);
            scent.scent=true; scent.acceptsDamage=false; scent.point=new Vec3(0,65,128);
            Vec3 muzzle=new Vec3(0,97,-10);
            if(scenario==1)ship.world.blocked=true;
            if(scenario==2)ship.world.loaded=false;
            if(scenario==3)ship.world.cancelAttack=true;
            if(scenario==4)scent.authorized=false;
            if(scenario==5)ship.world.intercept=target(ship,new Vec3(0,80,60));
            assertEquals(scenario==0,fire(MothershipArmament.Station.REAR_FORWARD,0,ship,scent,muzzle));
            assertEquals(scenario==0?1:0,scent.discards);assertEquals(0,scent.damageCalls);
            assertEquals(scenario==0?1:0,ship.world.inserted.size());
            if(scenario==0) {
                assertFalse(fire(MothershipArmament.Station.REAR_FORWARD,1,ship,scent,muzzle));
                assertEquals(1,scent.discards);assertEquals(1,ship.world.attacks);
            }
            if(scenario==3) {
                ship.world.cancelAttack=false;
                assertTrue(fire(MothershipArmament.Station.REAR_FORWARD,0,ship,scent,muzzle));
                assertEquals(1,scent.discards);
            }
        }
        for(var station:List.of(MothershipArmament.Station.FRONT_PORT,MothershipArmament.Station.MIDDLE_PORT)) {
            Actor ship=new Actor(new World());LivingActor scent=new LivingActor(ship.world,10);
            scent.scent=true;scent.point=new Vec3(70,80,0);
            assertFalse(fire(station,0,ship,scent,new Vec3(8,97,0)));
            assertEquals(0,scent.discards);assertTrue(ship.world.inserted.isEmpty());
        }
    }

    @Test void doomsdayDirectBeamCanClearAnIncidentalScentButItsBlastCannot() throws Exception {
        var method=SUBJECT.getMethod("doomsdayDamage",Actor.class,Actor.class,MothershipDoomsday.Hit.class,boolean.class);
        Actor ship=new Actor(new World());LivingActor scent=new LivingActor(ship.world,10);scent.scent=true;scent.acceptsDamage=false;
        assertEquals(false,method.invoke(null,ship,scent,new MothershipDoomsday().hit(1),true));assertEquals(0,scent.discards);
        assertEquals(true,method.invoke(null,ship,scent,new MothershipDoomsday().hit(1),false));assertEquals(1,scent.discards);
        assertEquals(false,method.invoke(null,ship,scent,new MothershipDoomsday().hit(2),false));assertEquals(0,scent.damageCalls);
    }

    @Test void actualAimAndSpawnedRoundCrossSmallGroundTargetAtThreeRangesIncludingHorizontalMotion() throws Exception {
        for(double x:new double[]{44,70,96})for(double speed:new double[]{0,.25}) {
            Actor ship=new Actor(new World());Actor target=target(ship,new Vec3(x,.25,0));target.velocity=new Vec3(speed,.4,0);
            Vec3 muzzle=new Vec3(8,35,0);var station=MothershipArmament.Station.FRONT_PORT;
            Vec3 aim=(Vec3)SUBJECT.getMethod("aimPoint",MothershipArmament.Station.class,Actor.class,Vec3.class)
                    .invoke(null,station,target,muzzle);
            assertTrue((boolean)SUBJECT.getMethod("fireSecondary",MothershipArmament.Station.class,int.class,Actor.class,Actor.class,Vec3.class,Vec3.class)
                    .invoke(null,station,0,ship,target,muzzle,aim));
            Bullet bullet=(Bullet)ship.world.inserted.get(0);assertEquals(.05F,bullet.gravity);
            assertTrue(bullet.boundedAtAdmission);
            // Native collision traces against the target's current box, not a simultaneous relative sweep.
            for(int targetTickPhase:new int[]{0,1}) {
                Vec3 cursor=bullet.point,step=bullet.velocity;boolean hit=false;
                for(int tick=0;tick<bullet.life;tick++) {
                    Vec3 targetNow=new Vec3(x+speed*(tick+targetTickPhase),.25,0),next=cursor.add(step);
                    AABB smallTarget=new AABB(targetNow.add(-.25,-.25,-.25),targetNow.add(.25,.25,.25));
                    if(smallTarget.clip(cursor,next).isPresent()){hit=true;break;}
                    if(next.y<0)break;
                    cursor=next;step=step.add(0,-bullet.gravity,0);
                }
                assertTrue(hit,"Native per-tick segment must hit for either target update order");
            }
            Vec3 point=bullet.point,velocity=bullet.velocity;boolean crossed=false;
            for(int tick=0;tick<bullet.life;tick++) {
                Vec3 next=point.add(velocity);double before=point.x-(x+speed*tick),after=next.x-(x+speed*(tick+1));
                if(before<=0 && after>=0) {
                    double fraction=-before/(after-before);
                    Vec3 hit=point.add(velocity.scale(fraction)),expected=new Vec3(x+speed*(tick+fraction),.25,0);
                    assertTrue(hit.distanceTo(expected)<.01,"Actual projectile must cross the small target before striking the ground");
                    crossed=true;break;
                }
                point=next;velocity=velocity.add(0,-bullet.gravity,0);
            }
            assertTrue(crossed);assertEquals(0,ship.world.damage);
        }
    }
    @Test void oneTenAndFiftyTargetsKeepOneCallbackPerExistingHitAndReadMaxHealthOnlyForDirectDamage() throws Exception {
        var method=SUBJECT.getMethod("doomsdayDamage",Actor.class,Actor.class,MothershipDoomsday.Hit.class,boolean.class);
        for(int count:new int[]{1,10,50}) {
            Actor ship=new Actor(new World());int callbacks=0,maximumReads=0;
            for(int i=0;i<count;i++) {
                LivingActor target=new LivingActor(ship.world,10000);
                for(int pulse=1;pulse<=4;pulse++)for(boolean blast:new boolean[]{false,true})
                    method.invoke(null,ship,target,new MothershipDoomsday().hit(pulse),blast);
                callbacks+=target.damageCalls;maximumReads+=target.maximumReads;
            }
            assertEquals(8*count,callbacks);assertEquals(4*count,maximumReads);
            assertEquals(0,ship.world.entityQueries);assertTrue(ship.world.inserted.isEmpty());
        }
    }
    @Test void actualSecondaryUsesMuzzleRangeAndRejectsCompensatedAimOutsideIt()throws Exception {
        Actor ship=new Actor(new World());
        var station=MothershipArmament.Station.FRONT_PORT;
        Actor target=target(ship,new Vec3(103,80,0));
        assertTrue(fire(station,0,ship,target,new Vec3(8,97,0)),"outside ship range but inside muzzle range");
        target.point=new Vec3(90,80,0);
        assertFalse(fire(station,0,ship,target,new Vec3(-8,97,0)),"inside ship range but outside muzzle range");
        assertEquals(false,SUBJECT.getMethod("fireSecondary",MothershipArmament.Station.class,int.class,Actor.class,Actor.class,Vec3.class,Vec3.class)
                .invoke(null,station,0,ship,target,new Vec3(8,97,0),new Vec3(110,80,0)));
        assertEquals(1,ship.world.inserted.size());
    }
    @Test void actualDoomsdayAdapterCombinesFixedAndPercentDamageWithoutExtraHitsOrBlastScaling()throws Exception {
        Actor ship=new Actor(new World());
        var method=SUBJECT.getMethod("doomsdayDamage",Actor.class,Actor.class,MothershipDoomsday.Hit.class,boolean.class);
        for(Actor target:List.of(new LivingActor(ship.world,4000),new VehicleActor(ship.world,8000))) {
            float maximum=target instanceof LivingActor living?living.getMaxHealth():((VehicleActor)target).getMaxHealth();
            for(int pulse=1;pulse<=4;pulse++) {
                var hit=new MothershipDoomsday().hit(pulse);
                assertEquals(true,method.invoke(null,ship,target,hit,false));
                assertEquals(true,method.invoke(null,ship,target,hit,true));
            }
            assertEquals(3750+maximum*.8,target.receivedDamage,.01);
            assertEquals(8,target.damageCalls,"Exactly four direct hits and four existing area hits");
            assertTrue(target.damageSources.stream().allMatch(source->source.percent==0),"Doomsday already merged its original percentage");
        }
        assertTrue(ship.world.inserted.isEmpty());assertTrue(ship.world.nativeFx.isEmpty());
    }
    @Test void rejectedDoomsdayTargetsStayUntouchedAndNativeDamageRejectionIsRespected()throws Exception {
        Actor ship=new Actor(new World());
        var method=SUBJECT.getMethod("doomsdayDamage",Actor.class,Actor.class,MothershipDoomsday.Hit.class,boolean.class);
        for(int scenario=0;scenario<6;scenario++) {
            Actor target=new LivingActor(ship.world,4000);
            switch(scenario){case 0->target.alive=false;case 1->target.spectator=true;case 2->target.protectedPlayer=true;
                case 3->target.unavailable=true;case 4->target.authorized=false;case 5->target.allied=true;}
            assertEquals(false,method.invoke(null,ship,target,new MothershipDoomsday().hit(4),false));
            assertEquals(0,target.receivedDamage);assertEquals(0,target.damageCalls);
        }
        Actor rejects=new LivingActor(ship.world,4000);rejects.acceptsDamage=false;
        assertEquals(false,method.invoke(null,ship,rejects,new MothershipDoomsday().hit(4),false));
        assertEquals(1,rejects.damageCalls);assertEquals(0,rejects.receivedDamage);
    }
    @Test void crashDamagesNearbyEntitiesIncludingAlliesWithFalloffAndHasOnlyBoundedVisualSideEffects()throws Exception {
        Actor ship=new Actor(new World());Vec3 point=new Vec3(0,99.75,0);
        Actor near=target(ship,point.add(10,0,0)),far=target(ship,point.add(30,0,0)),ally=target(ship,point.add(-10,0,0));
        ally.allied=true;
        Actor outside=target(ship,point.add(40,0,0)),creative=target(ship,point.add(1,0,0)),spectator=target(ship,point.add(2,0,0));
        creative.protectedPlayer=true;spectator.spectator=true;
        ship.world.targets.addAll(List.of(near,far,ally,outside,creative,spectator));
        SUBJECT.getMethod("crashImpact",Actor.class).invoke(null,ship);
        assertEquals(900,near.receivedDamage,.001);assertEquals(300,far.receivedDamage,.001);assertEquals(900,ally.receivedDamage,.001);
        for(Actor excluded:List.of(outside,creative,spectator)){assertEquals(0,excluded.receivedDamage);assertEquals(Vec3.ZERO,excluded.velocity);}
        assertTrue(near.velocity.x>far.velocity.x);assertTrue(near.velocity.y>0);assertTrue(ally.velocity.x<0);
        assertEquals(List.of(FxType.EPIC),ship.world.nativeFx);assertEquals(List.of(point.add(0,2,0)),ship.world.nativeFxPoints);
        assertTrue(ship.world.particles.isEmpty(),"Use native distant particle delivery instead of the 32-block vanilla packets");
        assertTrue(ship.world.inserted.isEmpty(),"No extra entities or persistent debris");
        // World deliberately offers no explosion, setBlock or fire API: executing such a path would fail linkage.
    }
    @Test void fallingBurstUsesAPosedHullRegionAndHasNoDamageOrRemovalSideEffects()throws Exception {
        VehicleActor ship=new VehicleActor(new World(),8000);ship.boxes.add(new Box(new AABB(10,80,20,20,90,30)));
        SUBJECT.getMethod("fallingExplosion",VehicleActor.class,int.class).invoke(null,ship,20);
        assertEquals(List.of(FxType.MEDIUM),ship.world.nativeFx);
        assertEquals(List.of(new Vec3(15,88.5,25)),ship.world.nativeFxPoints);
        assertEquals(0,ship.world.damage);assertTrue(ship.alive);assertTrue(ship.world.inserted.isEmpty());
    }
    @Test void fourFootfallImpactsUseIncreasingNativeEffectsAndFinalVisualRadius29Point4WithoutExtraDamage()throws Exception {
        Actor ship=new Actor(new World());Vec3 point=new Vec3(12,64,147);
        var method=SUBJECT.getMethod("playDoomsdayImpact",Actor.class,Vec3.class,MothershipDoomsday.Hit.class);
        NativeFx.requestedRadii.clear();
        var weapon=new MothershipDoomsday();
        for(int ordinal=1;ordinal<=4;ordinal++)method.invoke(null,ship,point,weapon.hit(ordinal));
        assertEquals(List.of(4F,8F,12F,29.4F),NativeFx.requestedRadii);
        assertEquals(List.of(FxType.MEDIUM,FxType.MEDIUM,FxType.MEDIUM,FxType.GIANT),ship.world.nativeFx);
        assertEquals(List.of(point,point,point,point),ship.world.nativeFxPoints);
        assertEquals(0,ship.world.damage);assertTrue(ship.world.inserted.isEmpty());
        ship.world.loaded=false;method.invoke(null,ship,point,weapon.hit(4));ship.world.loaded=true;
        method.invoke(null,ship,new Vec3(Double.NaN,0,0),weapon.hit(4));method.invoke(null,ship,null,weapon.hit(4));
        assertEquals(4,ship.world.nativeFx.size());
    }
    private static final Class<?> SUBJECT=load();
    private static Class<?> load(){try{return new Loader().loadClass(Loader.GENERATED.replace('/','.'));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static boolean fire(MothershipArmament.Station station,int barrel,Actor ship,Actor target,Vec3 muzzle)throws Exception {
        try{return (boolean)SUBJECT.getMethod("fireSecondary",MothershipArmament.Station.class,int.class,Actor.class,Actor.class,Vec3.class,Vec3.class)
                .invoke(null,station,barrel,ship,target,muzzle,target.getBoundingBox().getCenter());}
        catch(InvocationTargetException e){if(e.getCause() instanceof Error error)throw error;throw (Exception)e.getCause();}
    }
    private static Actor target(Actor ship,Vec3 point){Actor a=new Actor(ship.world);a.point=point;return a;}

    @Test void bothAutocannonsSpawnNativeExplosiveRoundsWithOwnerAndNoInstantLaserDamage()throws Exception {
        for(var station:List.of(MothershipArmament.Station.FRONT_PORT,MothershipArmament.Station.FRONT_STARBOARD)) {
            Actor ship=new Actor(new World());double x=station.ordinal()==0?70:-70;
            Actor target=target(ship,new Vec3(x,80,0));Vec3 muzzle=new Vec3(Math.signum(x)*8,97,0);
            assertTrue(fire(station,0,ship,target,muzzle));assertEquals(1,ship.world.inserted.size());
            assertEquals(Bullet.class,ship.world.inserted.get(0).getClass());Bullet bullet=(Bullet)ship.world.inserted.get(0);
            assertSame(ship,bullet.owner);assertEquals(muzzle,bullet.point);assertEquals(10,bullet.damage);
            assertEquals(5,bullet.blast);assertEquals(1,bullet.radius);
            assertEquals(1,bullet.head);assertEquals(1,bullet.leg);assertEquals(40,bullet.life);
            assertEquals(.10F,bullet.armorFraction);assertEquals(.001F,bullet.percentFraction);
            assertEquals(6,bullet.velocity.length(),1e-8);assertTrue(bullet.destroy);
            assertEquals(0,ship.world.damage);assertTrue(ship.world.sounds.isEmpty(),"The entity plays one sound per accepted burst");
        }
    }
    @Test void deniedFireNeverWritesDamageOrInsertsAProjectile()throws Exception {
        for(int scenario=0;scenario<9;scenario++) {
            Actor ship=new Actor(new World());Actor target=target(ship,new Vec3(70,80,0));Vec3 muzzle=new Vec3(8,97,0);int barrel=0;
            switch(scenario) {
                case 0->target.authorized=false;case 1->target.allied=true;case 2->target.alive=false;
                case 3->target.point=new Vec3(104.01,80,0);case 4->ship.world.loaded=false;
                case 5->ship.world.blocked=true;case 6->ship.world.intercept=target(ship,new Vec3(40,90,0));
                case 7->barrel=1;case 8->muzzle=new Vec3(Double.NaN,97,0);
            }
            assertFalse(fire(MothershipArmament.Station.FRONT_PORT,barrel,ship,target,muzzle),"scenario "+scenario);
            assertTrue(ship.world.inserted.isEmpty());assertEquals(0,ship.world.damage);
        }
        Actor ship=new Actor(new World());ship.world.accept=false;
        assertFalse(fire(MothershipArmament.Station.FRONT_PORT,0,ship,target(ship,new Vec3(70,80,0)),new Vec3(8,97,0)));
        assertTrue(ship.world.inserted.isEmpty());assertEquals(0,ship.world.damage);
    }
    @Test void rocketAndTwinLaserKeepTheirDamageAndAllSixMountsResolveTheRequestedSound()throws Exception {
        Actor rocketShip=new Actor(new World());
        assertTrue(fire(MothershipArmament.Station.MIDDLE_PORT,0,rocketShip,target(rocketShip,new Vec3(70,70,0)),new Vec3(8,97,0)));
        Rocket rocket=(Rocket)rocketShip.world.inserted.get(0);
        assertTrue(rocket.boundedAtAdmission);
        assertEquals(20,rocket.damage);assertEquals(15,rocket.blast);assertEquals(2,rocket.radius);assertTrue(rocket.destroy);assertEquals(1,rocket.blastStyle);
        assertEquals(.20F,rocket.armorFraction);assertEquals(.015F,rocket.percentFraction);
        assertEquals(80,rocket.life);assertEquals(2.5,rocket.velocity.length(),1e-8);
        Actor laserShip=new Actor(new World());Actor target=target(laserShip,new Vec3(0,65,128));
        for(int barrel=0;barrel<2;barrel++)assertTrue(fire(MothershipArmament.Station.REAR_FORWARD,barrel,laserShip,target,new Vec3(barrel,97,-10)));
        assertEquals(100,laserShip.world.damage);assertEquals(2,laserShip.world.inserted.size());
        assertEquals(2,target.damageSources.size());
        for(var source:target.damageSources)assertEquals(.02F,source.percent);
        assertNotSame(target.damageSources.get(0),target.damageSources.get(1),"Each beam owns its own hit claim");
        assertTrue(laserShip.world.inserted.stream().allMatch(Laser.class::isInstance));
        for(Actor inserted:laserShip.world.inserted) {
            Laser laser=(Laser)inserted;
            assertTrue(laser.length>128,"Muzzle offset extends the visual segment beyond the center-based target range");
            assertEquals(target.getBoundingBox().getCenter().distanceTo(laser.point),laser.length,1e-4);
        }
        for(var station:MothershipArmament.Station.values())SUBJECT.getMethod("playStationSound",MothershipArmament.Station.class,Actor.class,Vec3.class)
                .invoke(null,station,laserShip,laserShip.point);
        assertEquals(List.of("autocannon","autocannon","rocket","rocket","waveforce","waveforce"),laserShip.world.sounds);
    }

    public static class Actor {
        public final World world;public Vec3 point=new Vec3(0,100,0),velocity=Vec3.ZERO;public boolean alive=true,allied,authorized=true,protectedPlayer,spectator;
        public float receivedDamage,armorFraction,percentFraction;public final List<Damage> damageSources=new ArrayList<>();public int blastStyle,damageCalls,discards;public boolean scent,unavailable,acceptsDamage=true;public final List<Box> boxes=new ArrayList<>();
        public boolean isRemoved(){return discards>0;}public void discard(){discards++;alive=false;}
        public List<Box> getOBBs(){return boxes;}
        public Actor(World world){this.world=world;}public World level(){return world;}public Vec3 position(){return point;}
        public AABB getBoundingBox(){return new AABB(point.add(-.5,-1,-.5),point.add(.5,1,.5));}
        public boolean isAlive(){return alive;}public boolean isAlliedTo(Actor other){return other.allied;}
        public boolean isPickable(){return true;}public boolean isSpectator(){return spectator;}public float getYRot(){return 0;}
        public void push(double x,double y,double z){velocity=velocity.add(x,y,z);}
        public void setPos(double x,double y,double z){point=new Vec3(x,y,z);}public void setDeltaMovement(Vec3 v){velocity=v;}
        public Vec3 getDeltaMovement(){return velocity;}
    }
    public static class LivingActor extends Actor {
        public int maximumReads;
        private final float maximum;public LivingActor(World world,float maximum){super(world);this.maximum=maximum;}
        public float getMaxHealth(){maximumReads++;return maximum;}
    }
    public static class VehicleActor extends Actor {
        private final float maximum;public VehicleActor(World world,float maximum){super(world);this.maximum=maximum;}
        public float getMaxHealth(){return maximum;}
    }
    public static class Bullet extends Actor {
        public Actor owner;public float damage,blast,radius,head,leg,gravity;public int life;public boolean destroy=true;
        boolean boundedAtAdmission;
        public int getLife(){return life;}
        public void setCustomGravity(float value){gravity=value;}
        public Bullet(World world){super(world);}public void setOwner(Actor actor){owner=actor;}public void setDamage(float value){damage=value;}
        public void setExplosionDamage(float value){blast=value;}public void setExplosionRadius(float value){radius=value;}
        public void setHeadShot(float value){head=value;}public void setLegShot(float value){leg=value;}
        public void setExplosionDestroy(boolean value){destroy=value;}public void setLife(int value){life=value;}
        public void shoot(double x,double y,double z,float speed,float spread){assertEquals(0,spread);velocity=new Vec3(x,y,z).normalize().scale(speed);}
    }
    public static class Rocket extends Bullet {
        public Rocket(EntityKind ignored,double x,double y,double z,World world,float damage,float blast,float radius) {
            super(world);setPos(x,y,z);this.damage=damage;this.blast=blast;this.radius=radius;
        }
    }
    public static class Laser extends Actor {public float length;public Laser(World world){super(world);}public void setBeamLength(float value){length=value;}public void setLifetime(int life){}}
    public static class EntityKind {public Actor create(World world){return new Laser(world);}}
    public static class Registry { }
    public static class Damage { public float percent; }
    public static class DamageSources {public Damage explosion(Actor direct,Actor owner){return new Damage();}}
    public static class World {
        public int entityQueries;
        public boolean accept=true,loaded=true,blocked,cancelAttack;public Actor intercept;public float damage;public int attacks;
        public final List<Actor> inserted=new ArrayList<>();public final List<String> sounds=new ArrayList<>();
        public final List<Vec3> soundPoints=new ArrayList<>(),particles=new ArrayList<>();
        public final List<Actor> targets=new ArrayList<>();public final List<Integer> particleCounts=new ArrayList<>();
        public final List<FxType> nativeFx=new ArrayList<>();public final List<Vec3> nativeFxPoints=new ArrayList<>();
        public DamageSources damageSources(){return new DamageSources();}
        public List<Actor> getEntities(Actor excluded,AABB box,Predicate<Actor> predicate){entityQueries++;return targets.stream().filter(predicate).toList();}
        public boolean addFreshEntity(Actor actor){if(accept)inserted.add(actor);return accept;}
        public Registry registryAccess(){return new Registry();}
        public void playSound(Actor player,double x,double y,double z,Sound sound,SoundSource source,float volume,float pitch){
            sounds.add(sound.id);soundPoints.add(new Vec3(x,y,z));assertEquals(switch(sound.id){case "blast"->1.6F;case "autocannon"->.4F;case "rocket"->.7F;case "waveforce"->.65F;default->1F;},volume);
        }
        public int sendParticles(Particle type,double x,double y,double z,int count,double dx,double dy,double dz,double speed){
            particleCounts.add(count);particles.add(new Vec3(x,y,z));return 1;
        }
    }
    public record Trace(Vec3 end,boolean impact) { }
    public static class Queries {
        public static Trace trace(Actor ship,Vec3 from,Vec3 to){return new Trace(ship.world.loaded?to:from,ship.world.blocked);}
        public static boolean loaded(World world,AABB box){return world.loaded;}
    }
    public static class Particle { }
    public enum FxType {MEDIUM,GIANT,EPIC}
    public record Box(AABB bounds) {public static AABB getWorldAABB(Box box){return box.bounds;}}
    public static class NativeFx {
        public static final List<Float> requestedRadii=new ArrayList<>();
        public static FxType particleTypeForRadius(float radius){requestedRadii.add(radius);return radius>=30?FxType.EPIC:radius>=20?FxType.GIANT:FxType.MEDIUM;}
        public static void spawnExplosionParticles(FxType type,World world,Vec3 point){world.nativeFx.add(type);world.nativeFxPoints.add(point);}
    }
    public static class BlastEffects {public static void rocket(Actor projectile){projectile.blastStyle=1;}}
    public static class ShellLifetime {
        public static void trackKinetic(Actor projectile, long lifetime) { track(projectile, lifetime); }

        public static void track(Actor projectile, long lifetime) {
            var bullet = (Bullet) projectile;
            assertFalse(bullet.world.inserted.contains(bullet));
            assertEquals(bullet.life, lifetime);
            bullet.boundedAtAdmission = true;
        }
    }
    public static class PercentMarker {public static void mark(Actor projectile,float fraction){projectile.percentFraction=fraction;}}
    public static class TaggedArmor {public static void mark(Actor projectile,float fraction){projectile.armorFraction=fraction;}}
    public static class Particles {public static final Particle EXPLOSION_EMITTER=new Particle(),LARGE_SMOKE=new Particle(),FLAME=new Particle(),LAVA=new Particle();}
    public record Hit(Actor entity) {public Actor getEntity(){return entity;}}
    public static class Intercept {
        public static Hit getEntityHitResult(World world,Actor ship,Vec3 from,Vec3 to,AABB box,Predicate<Actor> predicate){return world.intercept==null?null:new Hit(world.intercept);}
    }
    public static class Policy {
        public static boolean fireAuthorizedBoss(Actor ship,Actor target){return target.authorized&&!target.scent&&!target.isRemoved();}
        public static boolean fireAuthorizedBossLaser(Actor ship,Actor target){return target.authorized&&!target.isRemoved();}
        public static boolean isProtectedPlayerTarget(Actor target){return target.protectedPlayer;}
        public static boolean isUnavailableCombatTarget(Actor target){return target.unavailable||target.scent;}
        public static boolean isUnavailableLaserTarget(Actor target){return target.unavailable;}
        public static boolean isSporeScent(Actor target){return target.scent;}
    }
    public static class Hooks {public static boolean onLivingAttack(LivingActor target,Damage source,float amount){target.world.attacks++;return !target.world.cancelAttack;}}
    public static class DamageTypes {
        public static Damage piercingLaser(Registry registry,Actor direct,Actor owner){return new Damage();}
        public static Damage piercingLaser(Registry registry,Actor direct,Actor owner,float percent){var source=new Damage();source.percent=percent;return source;}
        public static Damage doomsdayBlast(Registry registry,Actor direct,Actor owner){return new Damage();}
    }
    public static class DamageHandler {public static boolean doDamage(Actor target,Damage source,float amount){
        target.damageCalls++;target.damageSources.add(source);if(!target.acceptsDamage)return false;
        target.world.damage+=amount;target.receivedDamage+=amount;return true;
    }}
    public record Sound(String id) { }
    public record Holder(Object value) {public Object get(){return value;}}
    public static class Entities {public static final Holder RPG_ROCKET_STANDARD=new Holder(new EntityKind()),LASER_BOLT=new Holder(new EntityKind());}
    public static class Sounds {
        public static final Holder BLAST=new Holder(new Sound("blast"));
        public static final Holder AUTOCANNON_FIRE=new Holder(new Sound("autocannon")),ROCKET_FIRE=new Holder(new Sound("rocket")),LASER_FIRE=new Holder(new Sound("waveforce"));
    }
    private static class Loader extends ClassLoader {
        static final String ORIGINAL="com/liucodex/doggiesnewvehicle/boss/MothershipWeapons",GENERATED="com/liucodex/doggiesnewvehicle/boss/weaponprobe/ExecutedWeapons";
        private final Map<String,String> mapping=new HashMap<>();
        Loader(){super(MothershipWeaponsExecutionTest.class.getClassLoader());
            mapping.put(ORIGINAL,GENERATED);mapping.put(ORIGINAL+"$1",GENERATED+"$1");
            mapping.put("com/liucodex/doggiesnewvehicle/boss/DnvBossLaserHit", GENERATED+"LaserHit");
            for(var entry:Map.ofEntries(
                    Map.entry("net/minecraft/world/entity/Entity",Actor.class),Map.entry("net/minecraft/world/entity/player/Player",Actor.class),
                    Map.entry("net/minecraft/world/entity/LivingEntity",LivingActor.class),
                    Map.entry("net/minecraft/world/level/Level",World.class),Map.entry("net/minecraft/server/level/ServerLevel",World.class),
                    Map.entry("net/minecraft/world/entity/EntityType",EntityKind.class),Map.entry("net/minecraft/core/RegistryAccess",Registry.class),
                    Map.entry("net/minecraft/world/damagesource/DamageSource",Damage.class),Map.entry("net/minecraft/world/phys/EntityHitResult",Hit.class),
                    Map.entry("net/minecraft/world/damagesource/DamageSources",DamageSources.class),
                    Map.entry("net/minecraft/world/entity/projectile/ProjectileUtil",Intercept.class),Map.entry("net/minecraft/sounds/SoundEvent",Sound.class),
                    Map.entry("net/minecraftforge/registries/RegistryObject",Holder.class),
                    Map.entry("net/minecraft/core/particles/ParticleTypes",Particles.class),
                    Map.entry("net/minecraft/core/particles/ParticleOptions",Particle.class),
                    Map.entry("net/minecraft/core/particles/SimpleParticleType",Particle.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/projectile/ProjectileEntity",Bullet.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",VehicleActor.class),
                    Map.entry("com/atsuishio/superbwarfare/tools/OBB",Box.class),
                    Map.entry("com/atsuishio/superbwarfare/tools/ParticleTool",NativeFx.class),
                    Map.entry("com/atsuishio/superbwarfare/tools/ParticleTool$ParticleType",FxType.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvProjectileBlastEffects",BlastEffects.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/api/combat/DnvProjectileLifetime",ShellLifetime.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/core/DnvTaggedProjectileArmor",TaggedArmor.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/core/DnvPercentHits",PercentMarker.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/projectile/RpgRocketStandardEntity",Rocket.class),
                    Map.entry("com/atsuishio/superbwarfare/init/ModEntities",Entities.class),Map.entry("com/liucodex/noise/init/ModEntities",Entities.class),
                    Map.entry("com/liucodex/noise/entity/NoiseLaserBoltEntity",Laser.class),Map.entry("com/atsuishio/superbwarfare/tools/DamageHandler",DamageHandler.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/combat/DnvDamageTypes",DamageTypes.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvCombatCoordinator",Policy.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvCombatPolicyRuntime",Policy.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvTargetExclusions",Policy.class),
                    Map.entry("net/minecraftforge/common/ForgeHooks",Hooks.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/boss/MothershipWorld",Queries.class),Map.entry("com/liucodex/doggiesnewvehicle/boss/MothershipWorld$Trace",Trace.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/boss/MothershipSounds",Sounds.class)).entrySet())mapping.put(entry.getKey(),Type.getInternalName(entry.getValue()));
        }
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            String source=name.replace('.','/').equals(GENERATED+"LaserHit")
                    ? "com/liucodex/doggiesnewvehicle/boss/DnvBossLaserHit"
                    : ORIGINAL+name.replace('.','/').substring(GENERATED.length());
            try(var input=getParent().getResourceAsStream(source+".class")) {
                var node=new ClassNode();new ClassReader(input).accept(node,0);
                var output=new ClassWriter(0);node.accept(new ClassRemapper(output,new SimpleRemapper(mapping)));
                byte[] bytes=output.toByteArray();return defineClass(name,bytes,0,bytes.length);
            }catch(Exception e){throw new ClassNotFoundException(name,e);}
        }
    }
}
