package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.network.Dsx1TrailNetwork;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

class Dsx1TrailProjectileExecutionTest {
    @Test void compiledProjectileSamplesActualTravelAndStopsAtTheActualImpactWithoutMovingTheEntity()throws Exception{
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Body.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",ServerWorld.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/api/combat/DnvNativeShotVisual$TrailSink",Sink.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/compat/mixin/Dsx1TrailProjectileMixin",
            Set.of("dnv$startDsx1Trail","dnv$sample","dnv$flightSample","dnv$impactSample"),names,Body.class,false);
        var body=(Body)type.getConstructor().newInstance();Transport.samples.clear();
        var step=type.getMethod("dnv$flightSample",CallbackInfo.class);var impact=type.getMethod("dnv$impactSample",HitResult.class,CallbackInfo.class);
        step.invoke(body,new Object[]{null});assertTrue(Transport.samples.isEmpty());
        var origin=new Vec3(1,70,1);Sink sink=Transport::send;
        type.getMethod("dnv$startDsx1Trail",Vec3.class,Sink.class).invoke(body,origin,sink);
        body.pos=new Vec3(30,70,1);step.invoke(body,new Object[]{null});assertEquals(1,Transport.samples.size());assertEquals(origin.add(4,0,0),Transport.samples.get(0).from);
        var hit=new Vec3(41,70,1);impact.invoke(body,new BlockHitResult(hit,Direction.WEST,BlockPos.containing(hit),false),null);
        assertEquals(hit,Transport.samples.get(1).to);assertEquals(new Vec3(30,70,1),body.pos,"Visual capture cannot move native ammunition");
        body.pos=new Vec3(80,70,1);step.invoke(body,new Object[]{null});assertEquals(2,Transport.samples.size(),"No visual line may continue through the impact wall");
        type.getMethod("dnv$startDsx1Trail",Vec3.class,Sink.class).invoke(body,origin,sink);
        body.world=new World();step.invoke(body,new Object[]{null});assertEquals(2,Transport.samples.size(),"Client projectile does not publish samples");
    }
    @Test void nearBodyFlightAndNearImpactStayHiddenWhileThresholdCrossingKeepsTheRemainingSegment()throws Exception{
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Body.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",ServerWorld.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/api/combat/DnvNativeShotVisual$TrailSink",Sink.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/compat/mixin/Dsx1TrailProjectileMixin",
            Set.of("dnv$startDsx1Trail","dnv$sample","dnv$flightSample","dnv$impactSample"),names,Body.class,false);
        var body=(Body)type.getConstructor().newInstance();Transport.samples.clear();Sink sink=Transport::send;
        var start=type.getMethod("dnv$startDsx1Trail",Vec3.class,Sink.class);
        var tick=type.getMethod("dnv$flightSample",CallbackInfo.class);
        var impact=type.getMethod("dnv$impactSample",HitResult.class,CallbackInfo.class);
        start.invoke(body,Vec3.ZERO,sink);
        for(double z:new double[]{1,2,3,4}){body.pos=new Vec3(0,0,z);tick.invoke(body,new Object[]{null});}
        assertTrue(Transport.samples.isEmpty());
        body.pos=new Vec3(0,0,10);tick.invoke(body,new Object[]{null});
        assertEquals(new Sample(new Vec3(0,0,4),new Vec3(0,0,10)),Transport.samples.get(0));
        Transport.samples.clear();start.invoke(body,Vec3.ZERO,sink);
        impact.invoke(body,new BlockHitResult(new Vec3(0,0,2),Direction.NORTH,BlockPos.ZERO,false),null);
        body.pos=new Vec3(0,0,30);tick.invoke(body,new Object[]{null});
        assertTrue(Transport.samples.isEmpty(),"A near wall never gets a fabricated muzzle or through-wall trail");
    }
    public static class World{}
    public static class ServerWorld extends World{}
    public static class Body{
        public double dnv$hiddenDistance;public Vec3 dnv$previous;public int dnv$sequence;public boolean dnv$finished;public Sink dnv$sink;
        public Vec3 pos=Vec3.ZERO;public World world=new ServerWorld();public World level(){return world;}
        public Vec3 position(){return pos;}public UUID getUUID(){return new UUID(1,2);}
    }
    public record Sample(Vec3 from,Vec3 to){}
    public interface Sink {void send(ServerWorld world,UUID id,int sequence,Vec3 from,Vec3 to);}
    public static class Transport{
        public static final List<Sample> samples=new ArrayList<>();
        public static boolean validSegment(Vec3 a,Vec3 b){return Dsx1TrailNetwork.validSegment(a,b);}
        public static void send(ServerWorld world,UUID id,int sequence,Vec3 a,Vec3 b){samples.add(new Sample(a,b));}
    }
}
