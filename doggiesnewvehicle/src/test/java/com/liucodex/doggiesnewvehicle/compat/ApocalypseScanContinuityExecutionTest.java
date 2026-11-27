package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.compat.ApocalypseAutonomyExecutionTest.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Real controller -> real candidate cache -> real pager/budget. Native driving and weapons remain ports. */
class ApocalypseScanContinuityExecutionTest {
    @Test void changingHullHeadingAcquiresAndReacquiresThroughTheRealPagedCache() throws Exception {
        for(int density:new int[]{1,129}) {
            var f=new Fixture(true);var enemy=populate(f,density);
            int first=-1;
            for(int tick=0;tick<80;tick++) {
                pose(f.tank,tick);f.step();
                assertTrue(f.tank.world.getter.storage.visits<=64);
                if(f.active(enemy)){first=tick;break;}
                assertFalse(f.tank.navigation.enemy);assertEquals(0,f.weapons.shots);
            }
            assertTrue(first>=0,"continuous rotation must finish discovery");
            assertTrue(f.tank.navigation.enemy);assertTrue(f.weapons.shots>0);
            int shots=f.weapons.shots;f.tank.world.entities.remove(enemy.id);pose(f.tank,90);f.step();
            assertFalse(f.active(enemy));assertTrue(f.tank.navigation.stopped);assertEquals(shots,f.weapons.shots);
            var replacement=f.enemy(42);
            for(int tick=0;tick<80&&!f.active(replacement);tick++){pose(f.tank,91+tick);f.step();}
            assertTrue(f.active(replacement));assertTrue(f.weapons.shots>shots);
        }
    }

    @Test void completedRotatingScanDoesNotBypassSightOrLiveTargetRevalidation() throws Exception {
        var f=new Fixture(true);var enemy=populate(f,129);f.tank.world.blocked=true;
        for(int tick=0;tick<60;tick++){pose(f.tank,tick);f.step();}
        assertEquals(List.of(enemy),candidates(cache(f),f.tank));
        assertFalse(f.active(enemy));assertEquals(0,f.weapons.shots);
        f.tank.world.blocked=false;
        for(int tick=60;tick<80&&!f.active(enemy);tick++){pose(f.tank,tick);f.step();}
        assertTrue(f.active(enemy));int shots=f.weapons.shots;
        f.before();enemy.eligible=false;f.after();
        assertFalse(f.active(enemy));assertEquals(shots,f.weapons.shots);
    }

    @Test void worldChangeAndStopDiscardPendingScansAndOldCandidates() throws Exception {
        var f=new Fixture(true);var old=populate(f,512);
        for(int tick=0;tick<20;tick++){pose(f.tank,tick);f.step();}
        Object cache=cache(f);assertNotNull(field(cache,"scan"));
        f.tank.world=new Server();f.tank.world.dimension="nether";
        for(int tick=0;tick<30;tick++){pose(f.tank,tick);f.step();}
        assertFalse(f.active(old));assertEquals(0,f.weapons.shots);
        var next=f.enemy(40);
        for(int tick=0;tick<60&&!f.active(next);tick++){pose(f.tank,tick);f.step();}
        assertTrue(f.active(next));int shots=f.weapons.shots;
        f.tank.ready=false;f.step();
        assertNull(field(cache,"scan"));assertTrue(candidates(cache,f.tank).isEmpty());
        assertEquals(shots,f.weapons.shots);assertTrue(f.tank.navigation.stopped);
    }

    @Test void movingRotatingDenseScanFinishesThenRefreshesAroundItsNewPosition() throws Exception {
        var f=new Fixture(true);var enemy=populate(f,4096);Object cache=cache(f);
        boolean completed=false;
        for(int tick=0;tick<100;tick++) {
            f.tank.tickCount=tick;f.tank.world.time=tick;f.tank.position=new Vec3(tick*1.2,0,0);pose(f.tank,tick);
            f.tank.world.getter.storage.rebuild(f.tank.world.entities.values());
            completed=refresh(cache,f.tank,224);
            assertTrue(f.tank.world.getter.storage.visits<=64);
            if(completed)break;
        }
        assertTrue(completed);assertEquals(List.of(enemy),candidates(cache,f.tank));
        assertEquals(true,field(cache,"refreshAfterMovement"));
        f.tank.tickCount++;f.tank.world.time++;pose(f.tank,140);
        assertFalse(refresh(cache,f.tank,224));
        Vec3 origin=(Vec3)field(field(cache,"scan"),"origin");
        assertEquals(f.tank.position,origin);
    }

    @Test void fiftyRotatingOwnersFinishActualCachesWithoutExceedingTheSharedVisitBudget() throws Exception {
        var f=new Fixture(true);var enemy=populate(f,512);
        Object[] caches=new Object[50];Tank[] tanks=new Tank[50];boolean[] found=new boolean[50];
        Class<?> type=cache(f).getClass();
        for(int i=0;i<50;i++) {
            caches[i]=type.getConstructor(int.class).newInstance(20);
            tanks[i]=new Tank();tanks[i].world=f.tank.world;tanks[i].id=new UUID(1,i);tanks[i].runtimeId=i;
        }
        for(int tick=0;tick<160&&!all(found);tick++) {
            f.tank.world.time=tick;f.tank.world.getter.storage.rebuild(f.tank.world.entities.values());
            for(int i=0;i<50;i++) {
                tanks[i].tickCount=tick;pose(tanks[i],tick+i);refresh(caches[i],tanks[i],224);
                found[i]|=candidates(caches[i],tanks[i]).contains(enemy);
            }
            assertTrue(f.tank.world.getter.storage.visits<=16*64,"actual visits must respect the level-wide scan-step budget");
        }
        assertTrue(all(found),"every rotating owner must eventually publish its dense tail candidate");
    }

    @Test void explicitRangeAndClockChangesStillInvalidatePendingSnapshots() throws Exception {
        var f=new Fixture(true);var enemy=populate(f,129);Object cache=cache(f);
        f.tank.world.getter.storage.rebuild(f.tank.world.entities.values());
        assertFalse(refresh(cache,f.tank,224));Object original=field(cache,"scan");assertNotNull(original);
        f.tank.tickCount=1;f.tank.world.time=1;
        assertFalse(refresh(cache,f.tank,16));assertNull(field(cache,"scan"));
        f.tank.tickCount=20;f.tank.world.time=20;
        assertTrue(refresh(cache,f.tank,16));assertTrue(candidates(cache,f.tank).isEmpty());
        f.tank.tickCount=40;f.tank.world.time=40;
        assertFalse(refresh(cache,f.tank,224));Object future=field(cache,"scan");
        f.tank.tickCount=0;f.tank.world.time=0;
        assertFalse(refresh(cache,f.tank,224));assertNotSame(future,field(cache,"scan"));
        for(int tick=1;tick<5;tick++){f.tank.tickCount=tick;f.tank.world.time=tick;pose(f.tank,tick);refresh(cache,f.tank,224);}
        assertEquals(List.of(enemy),candidates(cache,f.tank));
    }

    private static Body populate(Fixture f,int density) {
        for(int i=1;i<density;i++){Body body=new Body();body.eligible=false;body.position=new Vec3(0,0,40);f.add(body);}
        return f.enemy(40);
    }
    private static void pose(Tank tank,float yaw) {
        tank.yaw=yaw;double angle=Math.toRadians(yaw),c=Math.abs(Math.cos(angle)),s=Math.abs(Math.sin(angle));
        // Authored movement OBB half lengths; rotation changes world AABB dimensions without resizing the unit.
        double x=c*2.625+s*4.323314666748047,z=s*2.625+c*4.323314666748047;
        tank.bounds=new AABB(-x,0,-z,x,2.14,z).move(tank.position);
    }
    private static boolean all(boolean[] values){for(boolean value:values)if(!value)return false;return true;}
    private static Object field(Object owner,String name)throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static Object cache(Fixture f)throws Exception{return field(f.instance,"candidates");}
    private static boolean refresh(Object cache,Tank tank,double range)throws Exception {
        return (boolean)cache.getClass().getMethod("refreshIfDue",Body.class,double.class,Predicate.class)
                .invoke(cache,tank,range,(Predicate<Body>)(body->body.eligible));
    }
    @SuppressWarnings("unchecked")
    private static List<Body> candidates(Object cache,Tank tank)throws Exception {
        return (List<Body>)cache.getClass().getMethod("candidates",Body.class).invoke(cache,tank);
    }
}
