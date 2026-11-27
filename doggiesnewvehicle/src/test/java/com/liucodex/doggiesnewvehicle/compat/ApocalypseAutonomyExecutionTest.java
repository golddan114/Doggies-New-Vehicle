package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicy;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.ClassNode;

/** Executes the whole compiled controller; world scan, collision, navigation and gun actions are ports. */
class ApocalypseAutonomyExecutionTest {
    @Test void removedEnemyAtHomeResumesPatrolNativeInputsAndTurretSearchThenReacquires() throws Exception {
        var f=new Fixture();f.tank.id=new UUID(0,45);var enemy=f.enemy(40);f.step();
        assertTrue(f.active(enemy));assertEquals(1,f.weapons.shots);
        f.tank.world.entities.remove(enemy.id);f.step();
        assertFalse(f.active(enemy));assertTrue(f.tank.navigation.stopped);assertEquals(1,f.weapons.shots);
        assertTrue(f.tank.navigation.stopped);
        Vec3 scan=f.tank.lastAim;f.step();assertEquals(scan,f.tank.lastAim, "idle scan includes a pause");
        var next=f.enemy(32);f.step();assertTrue(f.active(next));assertEquals(2,f.weapons.shots);
        assertTrue(f.tank.navigation.enemy);assertEquals(next.position,f.tank.navigation.destination);
    }
    @Test void idleHullStaysBrakedForMinutesWhileTurretScansOnlySmallAnglesWithPauses() throws Exception {
        var f=new Fixture();f.tank.id=new UUID(0,45);int paused=0,moving=0;Vec3 previous=null;
        for(int tick=0;tick<2400;tick++) {
            f.step();assertTrue(f.tank.navigation.stopped);
            double yaw=Math.toDegrees(Math.atan2(-f.tank.lastAim.x,f.tank.lastAim.z));
            assertTrue(Math.abs(yaw)<=28.001);
            if(previous!=null) {if(previous.equals(f.tank.lastAim))paused++;else moving++;}
            previous=f.tank.lastAim;
        }
        assertTrue(paused>400);assertTrue(moving>100);assertEquals(0,f.weapons.shots);
        f.tank.position=new Vec3(0,0,35);f.step();assertEquals(Vec3.ZERO,f.tank.navigation.destination);
        f.tank.energy=0;int aims=f.tank.aims;f.step();assertTrue(f.tank.navigation.stopped);assertEquals(aims,f.tank.aims);
    }
    @Test void friendlyLaneTriggersExistingSideRepositionAndUnknownGeometryStops() throws Exception {
        var f=new Fixture();var enemy=f.enemy(40);f.weapons.laneClear=false;f.before();
        assertTrue(f.active(enemy));assertTrue(f.tank.navigation.enemy);assertTrue(f.tank.navigation.blocked);
        f.weapons.laneClear=null;f.before();assertTrue(f.tank.navigation.stopped);
        f.weapons.laneClear=true;f.before();assertFalse(f.tank.navigation.stopped);assertFalse(f.tank.navigation.blocked);
    }
    @Test void highTargetExpandsRetreatSpaceWithoutExpandingTargetAcquisition() throws Exception {
        var f=new Fixture();var enemy=f.enemy(20);f.step();f.weapons.range=110;
        f.tank.position=new Vec3(0,0,-60);f.step();
        assertFalse(f.tank.navigation.stopped);assertTrue(f.active(enemy));assertEquals(2,f.weapons.shots);
        assertEquals(110,f.tank.navigation.range);
        f.tank.position=new Vec3(0,0,-90);f.step();
        assertFalse(f.tank.navigation.stopped);assertTrue(f.active(enemy));
        f.tank.position=new Vec3(0,0,-146);f.weapons.range=160;f.step();
        assertTrue(f.active(enemy));
        f.tank.position=new Vec3(0,0,-30);f.weapons.range=110;f.step();
        assertFalse(f.tank.navigation.stopped);assertEquals(110,f.tank.navigation.range);
    }
    @Test void lostHighTargetRestoresOrdinaryLeashAndReturnsWithoutTrackingHiddenMotion() throws Exception {
        var f=new Fixture();var enemy=f.enemy(20);f.weapons.range=110;f.step();
        f.tank.position=new Vec3(0,0,-90);f.step();assertTrue(f.active(enemy));
        f.tank.world.blocked=true;enemy.position=new Vec3(30,0,20);f.step();
        assertFalse(f.active(enemy));assertFalse(f.tank.navigation.enemy);
        assertEquals(Vec3.ZERO,f.tank.navigation.destination);
    }
    @Test void acquisitionRequiresSightAndBudgetAndRevalidatesBeforeWeaponAction() throws Exception {
        var f = new Fixture(); var enemy = f.enemy(40);
        Budget.available = false; f.step(); assertFalse(f.active(enemy)); assertEquals(0, f.weapons.shots);
        Budget.available = true; f.tank.world.blocked = true; f.step(); assertFalse(f.active(enemy));
        f.advance(6); f.tank.world.blocked = false; f.step(); assertTrue(f.active(enemy));
        assertEquals(1, f.weapons.shots); assertEquals(enemy.position.subtract(f.tank.lockOrigin()),f.tank.lastAim);
        f.before(); enemy.eligible = false; f.after();
        assertEquals(1, f.weapons.shots); assertFalse(f.active(enemy));
    }

    @Test void memoryDoesNotTrackHiddenPositionAndExpiresThenPatrolsNearHome() throws Exception {
        var f = new Fixture(); var enemy = f.enemy(45); f.step();
        Vec3 seen = enemy.position;
        enemy.position = new Vec3(20, 0, 80); f.tank.world.blocked = true;
        f.step(); assertFalse(f.active(enemy)); assertEquals(seen, f.tank.navigation.destination);
        assertTrue(f.tank.navigation.blocked); assertEquals(1, f.weapons.shots);
        f.advance(41); f.step(); assertTrue(f.tank.navigation.stopped);
        assertFalse(f.active(enemy));
    }

    @Test void leashAndFiniteSupplyReturnHomeAndEnergyOrRemovalStops() throws Exception {
        var f = new Fixture(); var enemy = f.enemy(100); f.step();
        f.tank.position = new Vec3(0, 0, 63); f.step();
        assertFalse(f.active(enemy)); assertEquals(Vec3.ZERO, f.tank.navigation.destination);
        f.tank.position = new Vec3(0, 0, 20); f.step();
        assertFalse(f.tank.navigation.enemy, "return must not oscillate into pursuit at the leash boundary");
        var empty = new Fixture(); empty.enemy(40); empty.weapons.supply = false; empty.step();
        assertEquals(0, empty.weapons.shots); assertFalse(empty.tank.navigation.enemy);
        empty.weapons.supply = true; empty.tank.energy = 0; empty.step(); assertTrue(empty.tank.navigation.stopped);
        empty.tank.energy = 100; empty.tank.ready = false; empty.step(); assertTrue(empty.tank.navigation.stopped);
    }

    @Test void saveRestoreKeepsOnlyHomeAndDimensionChangeOrInvalidNbtReanchors() throws Exception {
        var f = new Fixture(); f.tank.position = new Vec3(8, 2, 9); f.enemy(40); f.step();
        var saved = new CompoundTag(); f.call("save", new Class<?>[]{CompoundTag.class}, saved);
        assertEquals(8, saved.getDouble("HomeX")); assertFalse(saved.hasUUID("Target"));
        var restored = new Fixture(); restored.tank.position = new Vec3(20, 2, 9);
        restored.call("load", new Class<?>[]{CompoundTag.class}, saved); restored.step();
        assertTrue(restored.tank.navigation.stopped);
        assertEquals(0, restored.weapons.shots);
        restored.tank.world.dimension = "nether"; restored.step();
        assertTrue(restored.tank.navigation.stopped);
        saved.putDouble("HomeX", Double.NaN); restored.call("load", new Class<?>[]{CompoundTag.class}, saved);
        restored.step(); assertTrue(restored.tank.navigation.stopped);
    }

    @Test void bossPriorityAndAmmoPreferenceDoNotCreateAuthorityForInvalidOrOutOfAreaTargets() throws Exception {
        var f = new Fixture(); f.enemy(20); var boss = new Boss(); boss.position = new Vec3(0,0,90); f.add(boss);
        f.step(); assertTrue(f.active(boss)); assertTrue(f.weapons.armored);
        boss.eligible = false; f.step(); assertFalse(f.weapons.armored);
        assertFalse(f.active(boss));
        var outside = new Fixture(); outside.enemy(161); outside.step(); assertEquals(0, outside.weapons.shots);
    }

    static class Fixture {
        final Tank tank = new Tank(); final Weapons weapons = new Weapons();
        final Class<?> type; final Object instance;
        final boolean realScan;
        Fixture() throws Exception { this(false); }
        Fixture(boolean realScan) throws Exception {
            this.realScan = realScan;
            Budget.available = true;
            String original = "com/liucodex/doggiesnewvehicle/armored/ApocalypseAutonomy";
            String generated = "com/liucodex/doggiesnewvehicle/compat/executionprobe/ApocalypseAutonomy";
            Map<String,String> names = new HashMap<>(); names.put(original, generated);
            Map.ofEntries(Map.entry("net/minecraft/world/entity/Entity", Body.class),
                    Map.entry("net/minecraft/world/level/Level", World.class), Map.entry("net/minecraft/server/level/ServerLevel", Server.class),
                    Map.entry("net/minecraft/resources/ResourceKey", Dimension.class), Map.entry("net/minecraft/resources/ResourceLocation", Location.class),
                    Map.entry("net/minecraft/world/level/ClipContext", Clip.class), Map.entry("net/minecraft/world/level/ClipContext$Block", Block.class),
                    Map.entry("net/minecraft/world/level/ClipContext$Fluid", Fluid.class), Map.entry("net/minecraft/world/phys/BlockHitResult", Hit.class),
                    Map.entry("net/minecraft/world/phys/HitResult$Type", HitType.class),
                    Map.entry("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity", Tank.class),
                    Map.entry("com/atsuishio/superbwarfare/tools/SeekTool", Seek.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/armored/DnvAlliedApocalypseEntity", Tank.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/armored/ApocalypseAutonomousNavigation", Navigation.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/armored/ApocalypseAutonomousWeapons", Weapons.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/armored/ApocalypseAlliedTargets", Relations.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/armored/ApocalypseTargeting", Targeting.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvTargetCandidateCache", Cache.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvTargetingBudget", Budget.class))
                    .forEach((k,v)->names.put(k,Type.getInternalName(v)));
            if (realScan) TargetScanExecutionFixture.configure(names);
            ClassNode n = new ClassNode();
            try(var input=getClass().getResourceAsStream("/"+original+".class")) { new ClassReader(input.readAllBytes()).accept(n,0); }
            n.access=Opcodes.ACC_PUBLIC;
            n.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
            n.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}));
            Map<String,byte[]> definitions = realScan ? TargetScanExecutionFixture.classes(names) : new HashMap<>();
            definitions.put(generated,writer.toByteArray());
            type=new ExactQueuedShotFixture.Loader(definitions).loadClass(generated.replace('/','.'));
            instance=type.getConstructor(Tank.class,Weapons.class).newInstance(tank,weapons);
        }
        Body enemy(double z) { Body body=new Body();body.position=new Vec3(0,0,z);add(body);return body; }
        void add(Body body) { tank.world.entities.put(body.id,body); }
        void advance(int ticks) { tank.world.time+=ticks;tank.tickCount+=ticks; }
        void before() throws Exception {
            advance(1);
            if (realScan) tank.world.getter.storage.rebuild(tank.world.entities.values());
            call("beforeNativeTick",new Class<?>[0]);
        }
        void after() throws Exception { call("afterNativeTick",new Class<?>[0]); }
        void step() throws Exception { before();after(); }
        boolean active(Body body) throws Exception { return (boolean)call("activeEnemy",new Class<?>[]{Body.class},body); }
        Object call(String name,Class<?>[] parameters,Object...args)throws Exception{return type.getMethod(name,parameters).invoke(instance,args);}
    }
    public static class Body {
        public UUID id=UUID.randomUUID(); public Vec3 position=Vec3.ZERO; public boolean eligible=true,smoke;
        public int tickCount,runtimeId; public AABB bounds;
        public World level(){return ((Tank)this).world;} public int getId(){return runtimeId;}
        public UUID getUUID(){return id;} public Vec3 position(){return position;} public double getY(){return position.y;}
        public double distanceToSqr(Body other){return position.distanceToSqr(other.position);}
        public double getEyeY(){return position.y+1;} public AABB getBoundingBox(){return bounds==null?new AABB(position,position.add(1,2,1)):bounds;}
    }
    public static class Tank extends Body {
        public Server world=new Server();public int aims,preferred,energy=100;public boolean ready=true;public Navigation navigation;
        public Vec3 lastAim;public float yaw;public float getYRot(){return yaw;}
        public World level(){return world;} public boolean autonomousReady(){return ready;}
        public int getMaxEnergy(){return 100;} public int getEnergy(){return energy;}
        public Vec3 lockOrigin(){return position.add(0,2,0);} public Vec3 firingPivot(String name){return lockOrigin();}
        public void turretAutoAimFromVector(Vec3 v){aims++;lastAim=v;} public void setPreferredAmmo(int value){preferred=value;}
        public String getGunName(int seat,int group){return "MainL";} public double distanceToSqr(Body body){return position.distanceToSqr(body.position);}
    }
    public static class Boss extends Tank implements DnvCombatPolicy.BossAutonomy { }
    public static class World {
        public long time;public boolean blocked;public String dimension="overworld";
        public long getGameTime(){return time;} public Dimension dimension(){return new Dimension(dimension);}
        public Hit clip(Clip clip){return new Hit(blocked);}
    }
    public static class Server extends World implements TargetScanExecutionFixture.ServerAccess {
        public Map<UUID,Body> entities=new LinkedHashMap<>();public Body getEntity(UUID id){return entities.get(id);}
        public final TargetScanExecutionFixture.GetterImpl getter=new TargetScanExecutionFixture.GetterImpl();
        public final TargetScanExecutionFixture.Manager manager=new TargetScanExecutionFixture.Manager(getter.storage);
        public TargetScanExecutionFixture.Manager dnv$getEntityManager(){return manager;}
        public TargetScanExecutionFixture.Getter entityGetter=getter;
        public TargetScanExecutionFixture.Getter getEntities(){return entityGetter;}
    }
    public record Dimension(String id){public Location location(){return new Location(id);}}
    public record Location(String id){@Override public String toString(){return id;}}
    public enum Block{COLLIDER} public enum Fluid{NONE} public enum HitType{MISS,BLOCK}
    public record Clip(Vec3 start,Vec3 end,Block block,Fluid fluid,Body source){}
    public record Hit(boolean blocked){public HitType getType(){return blocked?HitType.BLOCK:HitType.MISS;}}
    public static class Seek{public static final Predicate<Body> NOT_IN_SMOKE=e->!e.smoke;}
    public static class Relations{public static boolean eligible(Body source,Body target){return target!=null&&target.eligible;}}
    public static class Targeting{public static boolean loadedRay(Body tank,Vec3 a,Vec3 b){return true;}}
    public static class Budget{
        public static boolean available;public static boolean admitGeometryCheck(Server server,UUID owner){return available;}
        public static void releaseGeometryOwner(Server server,UUID owner){}
    }
    public static class Cache{
        public Cache(int interval){} public boolean refreshIfDue(Body source,double range,Predicate<Body> accepted){return false;}
        public List<Body> candidates(Body source){return List.copyOf(((Tank)source).world.entities.values());}public void clear(){}
        public static int compareStableIds(UUID a,UUID b){return a.compareTo(b);}
    }
    public static class Navigation{
        public Vec3 destination;public boolean enemy,blocked,stopped;public double range;
        public Navigation(Tank tank){tank.navigation=this;}
        public void move(Vec3 at,boolean enemy,boolean blocked){destination=at;this.enemy=enemy;this.blocked=blocked;stopped=false;}
        public void move(Vec3 at,boolean enemy,boolean blocked,double range){move(at,enemy,blocked);this.range=range;}
        public void stop(){stopped=true;}public void clear(){stop();}
    }
    public static class Weapons{
        public boolean supply=true,armored;public int shots;public double range=24;
        public Boolean laneClear=true;public Boolean mainLaneClear(Body enemy){return laneClear;}
        public double combatDistance(Body enemy){return range;}
        public void preferAmmo(boolean value){armored=value;}
        public boolean hasSupply(){return supply;}public void clear(){}public int select(Body enemy){return 0;}
        public Vec3 aimPoint(int selection,Body enemy){return enemy.position;}public void tickLock(Body enemy,boolean seen){}
        public boolean fire(int selection,Body enemy){shots++;return true;}
    }
}
