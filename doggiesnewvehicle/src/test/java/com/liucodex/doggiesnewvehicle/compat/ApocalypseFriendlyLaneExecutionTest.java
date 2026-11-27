package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.atsuishio.superbwarfare.tools.OBB;
import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.ClassNode;

/** Executes current lane geometry/cache; the loaded-level entity index and team facts are ports. */
class ApocalypseFriendlyLaneExecutionTest {
    @Test void frontAllyBlocksWhileSideBehindHighDeadWreckAndNonAlliesDoNot() throws Exception {
        var f=new Fixture();var ally=f.ally(new Vec3(0,0,20));assertEquals(false,f.check(true));
        for(Vec3 point:List.of(new Vec3(8,0,20),new Vec3(0,0,-8),new Vec3(0,8,20))) {
            ally.point=point;assertEquals(true,f.check(true));
        }
        ally.point=new Vec3(0,0,20);ally.allied=false;assertEquals(true,f.check(true));
        ally.allied=true;ally.alive=false;assertEquals(true,f.check(true));
        ally.alive=true;ally.wreck=true;assertEquals(true,f.check(true));
        ally.wreck=false;ally.removed=true;assertEquals(true,f.check(true));
    }
    @Test void explorationBudgetCacheAndImmediateShotRecheckHaveSeparateAuthority() throws Exception {
        var f=new Fixture();Budget.allowed=false;assertNull(f.check(false));assertEquals(0,f.tank.world.getter.visits);
        Budget.allowed=true;assertEquals(true,f.check(false));int visits=f.tank.world.getter.visits;
        f.ally(new Vec3(0,0,20));Budget.allowed=false;
        assertEquals(true,f.check(false));assertEquals(visits,f.tank.world.getter.visits);
        assertEquals(false,f.check(true),"a same-tick newly arrived ally cannot use cached clearance");
        f.tank.world.now+=5;assertNull(f.check(false));
        Budget.allowed=true;assertEquals(false,f.check(false));
        f.tank.world.getter.entities.remove(1);assertEquals(true,f.check(true));
    }
    @Test void denseQueriesAbortAtTheCandidateLimitAndNeverBecomeClear() throws Exception {
        var f=new Fixture();for(int i=0;i<1000;i++)f.ally(new Vec3(6,0,20));
        assertNull(f.check(true));assertEquals(64,f.tank.world.getter.visits);
    }
    @Test void sourceOrDestinationMovementTimeRollbackAndUnloadDiscardExplorationCache() throws Exception {
        var f=new Fixture();assertEquals(true,f.check(false));Budget.allowed=false;
        f.from=new Vec3(1,2,0);assertNull(f.check(false));f.from=new Vec3(0,2,0);
        f.end=new Vec3(2,2,40);assertNull(f.check(false));f.end=new Vec3(0,2,40);
        f.tank.world.now=-1;assertNull(f.check(false));f.tank.world.now=0;
        f.type.getMethod("clear").invoke(f.lane);assertEquals(1,Budget.releases);assertNull(f.check(false));
        assertNull(f.inspect(new Vec3(Double.NaN,2,0),f.end,true));
        assertNull(f.inspect(f.from,new Vec3(0,2,300),true));
    }
    @Test void muzzleInsideAlliedBoxAndAdditionalFriendlyRecognitionAreBlocked() throws Exception {
        var f=new Fixture();var ally=f.ally(Vec3.ZERO);assertEquals(false,f.check(true));
        f.tank.allied=false;assertEquals(true,f.check(true));
        Seek.override=true;assertEquals(false,f.check(true));
    }

    static class Fixture {
        final Tank tank=new Tank();final Class<?> type;final Object lane;
        Vec3 from=new Vec3(0,2,0),end=new Vec3(0,2,40);
        Fixture()throws Exception {
            Budget.allowed=true;Budget.releases=0;Seek.override=false;tank.world.getter.entities.add(tank);
            String source="com/liucodex/doggiesnewvehicle/armored/ApocalypseFriendlyLane";
            String name="com/liucodex/doggiesnewvehicle/compat/executionprobe/FriendlyLane";
            Map<String,String> names=new HashMap<>();names.put(source,name);
            Map.ofEntries(Map.entry("com/liucodex/doggiesnewvehicle/armored/DnvAlliedApocalypseEntity",Tank.class),
                    Map.entry("net/minecraft/world/entity/Entity",Body.class),Map.entry("net/minecraft/world/level/Level",World.class),
                    Map.entry("net/minecraft/server/level/ServerLevel",Server.class),
                    Map.entry("net/minecraft/world/level/entity/LevelEntityGetter",Getter.class),
                    Map.entry("com/atsuishio/superbwarfare/tools/SeekTool",Seek.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvTargetingBudget",Budget.class))
                    .forEach((k,v)->names.put(k,Type.getInternalName(v)));
            ClassNode n=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(in.readAllBytes()).accept(n,0);}
            n.access=Opcodes.ACC_PUBLIC;n.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
            ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);
            n.accept(new ClassRemapper(out,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}));
            type=new ExactQueuedShotFixture.Loader(Map.of(name,out.toByteArray())).loadClass(name.replace('/','.'));
            lane=type.getConstructor(Tank.class).newInstance(tank);
        }
        Tank ally(Vec3 at){var other=new Tank();other.point=at;other.world=tank.world;tank.world.getter.entities.add(other);return other;}
        Boolean check(boolean immediate)throws Exception{return inspect(from,end,immediate);}
        Boolean inspect(Vec3 a,Vec3 b,boolean immediate)throws Exception{return (Boolean)type.getMethod("inspect",Vec3.class,Vec3.class,boolean.class).invoke(lane,a,b,immediate);}
    }
    public static class Body { }
    public static class Tank extends Body {
        public Server world=new Server();public Vec3 point=Vec3.ZERO;public final UUID id=UUID.randomUUID();
        public boolean allied=true,alive=true,wreck,removed;
        public World level(){return world;}public UUID getUUID(){return id;}
        public boolean isAlive(){return alive;}public boolean isRemoved(){return removed;}public boolean isWreck(){return wreck;}
        public boolean isAlliedTo(Body other){return allied&&((Tank)other).allied;}
        public AABB getBoundingBox(){return new AABB(point.add(-2.8,0,-2.8),point.add(2.8,4.3,2.8));}
        public OBB getCollisionOBB(){return null;}
    }
    public static class World { }
    public static class Server extends World {
        public long now;public final Index getter=new Index();public long getGameTime(){return now;}public Getter getEntities(){return getter;}
    }
    public interface Getter {
        void get(EntityTypeTest<?,?> type,AABB bounds,AbortableIterationConsumer<Tank> visitor);
    }
    public static class Index implements Getter {
        public final List<Tank> entities=new ArrayList<>();public int visits;
        public void get(EntityTypeTest<?,?> type,AABB bounds,AbortableIterationConsumer<Tank> visitor) {
            for(Tank tank:entities)if(bounds.intersects(tank.getBoundingBox())) {
                visits++;if(visitor.accept(tank)==AbortableIterationConsumer.Continuation.ABORT)return;
            }
        }
    }
    public static class Seek {public static boolean override;public static final BiPredicate<Body,Body> IS_FRIENDLY=(a,b)->override;}
    public static class Budget {
        public static boolean allowed;public static int releases;
        public static boolean admitGeometryCheck(Server world,UUID id){return allowed;}
        public static void releaseGeometryOwner(Server world,UUID id){releases++;}
    }
}
