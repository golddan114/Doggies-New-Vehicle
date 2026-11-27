package com.liucodex.doggiesnewvehicle.artillery.silo;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class NuclearClearanceTest {
    static final Vec3 ORIGIN=new Vec3(.5,65.5572145,.5);
    @Test void netherCeilingAndRocketSideContactRejectBeforeLeavingTheShaft(){
        var world=new World();world.ceiling=127;
        var query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.BLOCKED,run(query,world));assertTrue(query.launchSide());
        assertEquals(127,query.blocked().getY());assertFalse(world.held);
        world=new World();world.solid=p->p.getX()==1&&p.getY()==90&&p.getZ()==0;
        query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.BLOCKED,run(query,world));assertEquals(1,query.blocked().getX());
    }
    @Test void distantDiagonalApproachIsCheckedEvenWhereTheTargetVerticalColumnIsClear(){
        var world=new World();world.solid=p->p.getX()>1500&&p.getX()<1900&&p.getY()==200;
        var query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.BLOCKED,run(query,world));assertFalse(query.launchSide());
        assertTrue(query.blocked().getX()<1900);assertEquals(200,query.blocked().getY());
    }
    @Test void exactGroundSurfaceAndTwoHundredThousandBlockRoutePassWithoutLoadingHighSky(){
        var world=new World();world.solid=p->p.getY()<=64;
        var query=new NuclearClearance(world,ORIGIN,new BlockPos(199999,64,0),0,0);
        assertEquals(NuclearClearance.State.CLEAR,run(query,world));assertFalse(world.held);
        assertTrue(world.reads<200_000,"Budget bounds the live-world portion, not total coordinate distance");
        assertFalse(world.loadedMiddle,"High sky traversal does not load the entire route");
    }
    @Test void missingChunksWaitWithoutReadingAndTimeoutOrCancellationReleasesTheLease(){
        var world=new World();world.loaded=false;
        var query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.CHECKING,query.tick(0));assertEquals(0,world.reads);assertTrue(world.held);
        assertEquals(NuclearClearance.State.TIMEOUT,query.tick(NuclearClearance.TIMEOUT_TICKS));assertFalse(world.held);
        world=new World();world.loaded=false;query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        query.tick(0);query.close();assertEquals(NuclearClearance.State.CANCELLED,query.state());assertFalse(world.held);
        world=new World();world.loaded=false;query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        query.tick(0);world.loaded=true;assertEquals(NuclearClearance.State.CLEAR,run(query,world));
    }
    @Test void perTickBudgetAndFreshLaunchRecheckCatchAnObstructionAddedDuringRemoteInspection(){
        var world=new World();var target=new BlockPos(2000,64,0);
        var query=new NuclearClearance(world,ORIGIN,target,0,0);
        query.tick(1);int reads=world.reads;query.tick(1);assertEquals(reads,world.reads);
        assertEquals(NuclearClearance.State.CLEAR,run(query,world));
        world.ceiling=80;var again=new NuclearClearance(world,ORIGIN,target,0,0,true);
        assertEquals(NuclearClearance.State.BLOCKED,run(again,world));assertTrue(again.launchSide());
    }
    @Test void transparentCollisionShapesAndNegativeWorldCoordinatesUsePhysicalBounds(){
        var world=new World();world.emptyShapes=true;world.ceiling=100;
        var query=new NuclearClearance(world,new Vec3(-100.5,65.5572145,-100.5),new BlockPos(-5000,64,-4000),73,0);
        assertEquals(NuclearClearance.State.CLEAR,run(query,world));
        world=new World();world.ceiling=100;
        query=new NuclearClearance(world,new Vec3(-100.5,65.5572145,-100.5),new BlockPos(-5000,64,-4000),73,0);
        assertEquals(NuclearClearance.State.BLOCKED,run(query,world));
    }
    @Test void nearbyCanopyAndOneBlockSurfaceErrorsArePermittedWhileLaunchLeavesStillBlock(){
        var world=new World();world.solid=p->p.getY()<=64||p.getX()>1900&&p.getY()==72;
        world.foliage=p->p.getY()==72;
        var query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.CLEAR,run(query,world));assertFalse(world.held);
        world=new World();world.solid=p->p.getY()<=64;
        query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,63,0),0,0);
        assertEquals(NuclearClearance.State.CLEAR,run(query,world));
        world=new World();world.solid=p->p.getY()<=64||p.getX()>=1999&&p.getY()==65;
        query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.CLEAR,run(query,world));
        world=new World();world.ceiling=80;world.foliage=p->true;
        query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.BLOCKED,run(query,world));
        assertEquals(NuclearClearance.Rejection.LAUNCH,query.rejection());
        world=new World();world.solid=p->p.getX()>1500&&p.getX()<1900&&p.getY()==200;world.foliage=p->true;
        query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.BLOCKED,run(query,world));
        assertEquals(NuclearClearance.Rejection.OUTSIDE_IMPACT,query.rejection());
    }
    @Test void largeRoofAndUnbreakableContactStillRejectAndLeavesCannotHideTheRoof(){
        var world=new World();world.solid=p->p.getX()>1900&&p.getY()==72;
        var query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.BLOCKED,run(query,world));
        assertEquals(NuclearClearance.Rejection.CEILING,query.rejection());
        assertEquals("minecraft:stone",query.description().id());assertFalse(world.held);
        world=new World();world.solid=p->p.getX()>1900&&p.getY()==72;world.unbreakable=p->true;
        query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.BLOCKED,run(query,world));
        assertEquals(NuclearClearance.Rejection.UNBREAKABLE,query.rejection());
        world=new World();world.solid=p->p.getX()>1900&&(p.getY()==72||p.getY()==75);world.foliage=p->p.getY()==75;
        query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.BLOCKED,run(query,world));
        assertEquals(NuclearClearance.Rejection.CEILING,query.rejection());
    }
    @Test void continuousGroundIsNotMistakenForAnOverheadCeiling(){
        var world=new World();world.solid=p->p.getY()<=64||p.getX()>1950&&p.getY()<=72;
        var query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.CLEAR,run(query,world));assertFalse(world.held);
    }
    @Test void terminalRoofSamplingWaitsForItsChunksAndKeepsTheOriginalBudgetAndTimeout(){
        var world=new World();world.solid=p->p.getX()>1900&&p.getY()==72;world.foliage=p->true;world.roofUnloaded=true;
        var query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        for(int tick=1;tick<200&&world.roofWaits==0;tick++){
            int before=world.reads;query.tick(tick);assertTrue(world.reads-before<=NuclearClearance.CELLS_PER_TICK);
        }
        assertTrue(world.roofWaits>0);assertEquals(NuclearClearance.State.CHECKING,query.state());
        assertTrue(world.held);world.roofUnloaded=false;
        assertEquals(NuclearClearance.State.CLEAR,run(query,world));assertFalse(world.held);
        world=new World();world.solid=p->p.getX()>1900&&p.getY()==72;world.foliage=p->true;world.roofUnloaded=true;
        query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.TIMEOUT,run(query,world));assertFalse(world.held);
    }
    @Test void firstContactRatherThanCellOrderControlsAnAllowedEarlyImpact(){
        var world=new World();world.solid=p->(p.getX()==8||p.getX()==5)&&p.getY()>64&&p.getY()<80&&Math.abs(p.getZ())<3;
        world.foliage=p->p.getX()==8;world.unbreakable=p->p.getX()==5;
        var query=new NuclearClearance(world,new Vec3(2000.5,65.5572145,.5),new BlockPos(0,64,0),0,0);
        assertEquals(NuclearClearance.State.CLEAR,run(query,world));
    }
    @Test void aContinuousSlopingRoofIsStillALargeCeiling(){
        var world=new World();world.solid=p->p.getX()>1900&&p.getY()==p.getX()-1916;
        var query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.BLOCKED,run(query,world));
        assertEquals(NuclearClearance.Rejection.CEILING,query.rejection());
    }
    @Test void disconnectedAlternatingLevelsDoNotCombineIntoOneCeiling(){
        var world=new World();world.solid=p->p.getX()>1900&&p.getY()==72+2*((p.getX()+p.getZ())&1);
        var query=new NuclearClearance(world,ORIGIN,new BlockPos(2000,64,0),0,0);
        assertEquals(NuclearClearance.State.CLEAR,run(query,world));assertFalse(world.held);
    }
    @Test void zeroTargetUnderY67TerrainReproducesTheReportedApproachRejection(){
        var world=new World();world.solid=p->p.getY()<=67;
        var origin=new Vec3(2000.5,68.5572145,.5);
        var query=new NuclearClearance(world,origin,BlockPos.ZERO,0,0);
        assertEquals(NuclearClearance.State.BLOCKED,run(query,world));
        assertFalse(query.launchSide());assertEquals(NuclearClearance.Rejection.OUTSIDE_IMPACT,query.rejection());
        assertEquals(67,query.blocked().getY());assertFalse(world.held);
        world=new World();world.solid=p->p.getY()<=67;
        query=new NuclearClearance(world,origin,new BlockPos(0,67,0),0,0);
        assertEquals(NuclearClearance.State.CLEAR,run(query,world));assertFalse(world.held);
    }
    static NuclearClearance.State run(NuclearClearance query,World world){
        for(int tick=2;tick<=NuclearClearance.TIMEOUT_TICKS&&query.state()==NuclearClearance.State.CHECKING;tick++){
            int before=world.reads;query.tick(tick);
            assertTrue(world.reads-before<=NuclearClearance.CELLS_PER_TICK);
        }
        return query.state();
    }
    static class World implements NuclearClearance.World {
        boolean held,loaded=true,emptyShapes,loadedMiddle,roofUnloaded;int reads,roofWaits,ceiling=Integer.MAX_VALUE;
        Predicate<BlockPos> solid=p->false;
        Predicate<BlockPos> foliage=p->false,unbreakable=p->false;
        public int minY(){return -64;}public int maxY(){return 320;}
        public boolean ready(BlockPos p){held=true;loadedMiddle|=p.getX()>1000&&p.getX()<198000;
            if(roofUnloaded&&p.getX()>1900&&p.getZ()<-2){roofWaits++;return false;}return loaded;}
        public List<AABB> obstacles(BlockPos p){assertTrue(loaded);assertTrue(held);reads++;
            assertTrue(p.getY()>=minY()&&p.getY()<maxY());
            return !emptyShapes&&(p.getY()==ceiling||solid.test(p))?List.of(new AABB(p)):List.of();}
        public void release(){held=false;}
        public NuclearClearance.BlockKind kind(BlockPos p){return unbreakable.test(p)?NuclearClearance.BlockKind.UNBREAKABLE:
                foliage.test(p)?NuclearClearance.BlockKind.FOLIAGE:NuclearClearance.BlockKind.NORMAL;}
        public NuclearClearance.Description describe(BlockPos p){return new NuclearClearance.Description("block.minecraft.stone","minecraft:stone");}
    }
}
