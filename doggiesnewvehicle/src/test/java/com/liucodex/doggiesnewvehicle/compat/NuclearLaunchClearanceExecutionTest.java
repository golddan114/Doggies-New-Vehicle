package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Compiled facility gate + real bounded trajectory inspector, with explicit world/ticket edges. */
class NuclearLaunchClearanceExecutionTest {
    static final BlockPos TARGET=new BlockPos(2000,64,0);static final Vec3 ORIGIN=new Vec3(.5,65.5572145,.5);
    @Test void blockedCeilingCancelsCycleWithoutConsumingOrAuthorizingTransaction() throws Exception {
        var f=new Fixture();f.world.ceiling=127;assertFalse(f.run());
        assertEquals(SiloCycle.Phase.RETURNING,f.body.cycle.snapshot().phase());assertEquals(List.of("checking","launch_blocked"),f.body.messages);
        assertFalse(f.world.held);assertNull(f.body.clearance);
    }
    @Test void distantCheckThenFreshLaunchRecheckAreBothRequiredBeforeGateOpens() throws Exception {
        var f=new Fixture();assertTrue(f.run());assertEquals(2,f.world.queries);assertFalse(f.world.held);assertNull(f.body.clearance);
        assertEquals(SiloCycle.Phase.OPENING,f.body.cycle.snapshot().phase());
        f=new Fixture();while(!f.body.clearanceFinal){assertFalse(f.step());assertTrue(f.world.time<NuclearClearance.TIMEOUT_TICKS);}
        f.world.ceiling=80;assertFalse(f.run());assertEquals(SiloCycle.Phase.RETURNING,f.body.cycle.snapshot().phase());
        assertTrue(f.body.messages.contains("launch_blocked"));
    }
    @Test void unloadedInspectionTimeoutExceptionAndUnloadReleaseQueryAndKeepLaunchUncommitted() throws Exception {
        var f=new Fixture();f.world.loaded=false;assertFalse(f.step());f.world.time=NuclearClearance.TIMEOUT_TICKS+1;
        assertFalse(f.step());assertEquals(SiloCycle.Phase.RETURNING,f.body.cycle.snapshot().phase());assertFalse(f.world.held);
        f=new Fixture();f.world.failure=true;assertFalse(f.step());assertEquals(SiloCycle.Phase.RETURNING,f.body.cycle.snapshot().phase());assertFalse(f.world.held);
        assertEquals(List.of("checking","failed"),f.body.messages);assertEquals(1,f.body.failures);
        f=new Fixture();f.world.loaded=false;f.step();assertTrue(f.world.held);
        f.type.getMethod("releaseClearance").invoke(f.body);assertFalse(f.world.held);assertNull(f.body.clearance);
    }
    static class Fixture {
        final World world=new World();final Body body;final Class<?> type;
        Fixture() throws Exception {
            var names=new HashMap<String,String>();
            NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
            NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/artillery/silo/NuclearClearanceWorld",Port.class);
            type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearSiloEntity",Set.of("checkLaunchClearance","releaseClearance"),names,Body.class);
            body=(Body)type.getConstructor().newInstance();
        }
        boolean step() throws Exception {world.time++;return (boolean)type.getMethod("checkLaunchClearance",World.class,BlockPos.class,Vec3.class).invoke(body,world,TARGET,ORIGIN);}
        boolean run() throws Exception {while(world.time<2*NuclearClearance.TIMEOUT_TICKS&&body.cycle.snapshot().phase()==SiloCycle.Phase.OPENING){if(step())return true;}return false;}
    }
    public static class Body {
        public SiloPayloadKind payloadKind(){return SiloPayloadKind.NUCLEAR;}
        public NuclearClearance clearance;public boolean clearanceFinal;public final List<String> messages=new ArrayList<>();
        public final SiloCycle cycle=new SiloCycle(NuclearRules.DEFAULT);
        public Body(){cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.OPENING,0,UUID.randomUUID(),new SiloCycle.Target(2000,64,0)));}
        public float getYRot(){return 0;}public void clearanceMessage(World world,String reason,Object... args){messages.add(reason);}
        public int failures;
        public int ammoSlot(){return 0;}
        public void logClearanceFailure(BlockPos target,RuntimeException failure){failures++;}
        public void logClearanceBlocked(BlockPos target,BlockPos block,NuclearClearance.Description detail,NuclearClearance.Rejection reason){ }
    }
    public static class World {public long time;public int queries,ceiling=Integer.MAX_VALUE;public boolean loaded=true,held,failure;public long getGameTime(){return time;}}
    public static class Port implements NuclearClearance.World {
        final World world;public Port(World world){this.world=world;world.queries++;}
        public int minY(){return -64;}public int maxY(){return 320;}
        public boolean ready(BlockPos p){world.held=true;if(world.failure)throw new IllegalStateException("ticket callback");return world.loaded;}
        public List<AABB> obstacles(BlockPos p){return p.getY()==world.ceiling?List.of(new AABB(p)):List.of();}
        public void release(){world.held=false;}
    }
}
