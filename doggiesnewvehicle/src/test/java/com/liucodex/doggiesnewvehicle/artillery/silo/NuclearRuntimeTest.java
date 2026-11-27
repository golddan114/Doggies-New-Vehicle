package com.liucodex.doggiesnewvehicle.artillery.silo;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.energy.DnvEnergyInputBudget;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Execute the production mechanical, transaction, persistence and ticket boundaries without a game world. */
class NuclearRuntimeTest {
    @Test void ticketAcquisitionThrowAfterWorldMutationRetainsARecoverableIntent(){
        var actual=new java.util.HashSet<Long>();var saved=new java.util.concurrent.atomic.AtomicReference<java.util.Set<Long>>(java.util.Set.of());
        assertThrows(IllegalStateException.class,()->NuclearTicketPlan.apply(java.util.Set.of(),java.util.Set.of(10L,11L),actual::remove,
                c->{actual.add(c);throw new IllegalStateException("after forceChunk");},saved::set));
        assertEquals(actual,saved.get());assertEquals(1,actual.size());
        NuclearTicketPlan.apply(saved.get(),java.util.Set.of(),actual::remove,actual::add,saved::set);
        assertTrue(actual.isEmpty());assertTrue(saved.get().isEmpty());
    }
    final NuclearRules rules=NuclearRules.DEFAULT;
    @Test void deploymentUsesExactlySeventeenBlocksAroundTheContainerWithoutFacingOffset(){
        for(var container:new BlockPos[]{new BlockPos(-17,60,15),BlockPos.ZERO,new BlockPos(15,70,-17)}){
            var anchor=NuclearDeploymentGeometry.anchorFromContainer(container);
            assertEquals(container,anchor);
            var low=NuclearDeploymentGeometry.low(anchor);var high=NuclearDeploymentGeometry.high(anchor);
            assertEquals(17,high.getX()-low.getX()+1);assertEquals(17,high.getZ()-low.getZ()+1);
            assertEquals(21,high.getY()-low.getY()+1);
            var clearance=NuclearDeploymentGeometry.clearance(new net.minecraft.world.phys.Vec3(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5));
            assertEquals(low.getX(),clearance.minX);assertEquals(high.getX()+1,clearance.maxX);
            assertEquals(low.getZ(),clearance.minZ);assertEquals(high.getZ()+1,clearance.maxZ);
            assertEquals(low.getY(),clearance.minY);assertEquals(high.getY()+1,clearance.maxY);
        }
    }
    @Test void packingDropsPreparationAndFreshDeploymentMustWaitTheWholeReloadAgain(){
        for(int waited:new int[]{0,100,5999,6000}){
            var cycle=new SiloCycle(rules);cycle.tick(true);
            for(int i=0;i<waited;i++)cycle.tick(true);
            assertTrue(cycle.canPack());cycle.reset();
            var fresh=new SiloCycle(rules);fresh.restore(SiloCycleCodec.read(SiloCycleCodec.write(cycle.snapshot())));
            assertEquals(new SiloCycle.Snapshot(SiloCycle.Phase.EMPTY,0,null,null),fresh.snapshot());
            fresh.tick(true);assertEquals(6000,fresh.snapshot().remaining());
            for(int i=0;i<5999;i++)fresh.tick(true);
            assertFalse(fresh.request(UUID.randomUUID(),new SiloCycle.Target(1,2,3),true,rules.shotFE()));
            fresh.tick(true);assertEquals(SiloCycle.Phase.READY,fresh.snapshot().phase());
        }
        var launch=ready();launch.request(UUID.randomUUID(),new SiloCycle.Target(1,2,3),true,rules.shotFE());
        assertFalse(launch.canPack());for(int i=0;i<rules.openTicks();i++)launch.tick(true);
        launch.launched(true);assertFalse(launch.canPack());
        for(int i=0;i<rules.departureTicks();i++)launch.tick(false);assertFalse(launch.canPack());
    }
    @Test void actualChargingBudgetAndMechanicalReloadRunAlongsideEachOther(){
        SiloCycle cycle=new SiloCycle(rules);var input=com.liucodex.doggiesnewvehicle.artillery.energy.DnvEnergyInputPolicy.forVehicle("doggievehicle:nuclear_silo");
        int battery=20_000_000,energy=0,fullAt=0;cycle.tick(true);
        for(int tick=1;tick<=6000;tick++){
            int received=Math.min(battery,Math.min(20_000_000-energy,input.allowance(tick,Integer.MAX_VALUE)));
            input.received(tick,received,false);battery-=received;energy+=received;
            if(energy==20_000_000&&fullAt==0)fullAt=tick;
            assertFalse(cycle.tick(true));
            if(tick<6000)assertEquals(SiloCycle.Phase.RELOADING,cycle.snapshot().phase());
        }
        assertEquals(1,fullAt);assertEquals(0,battery);assertEquals(SiloCycle.Phase.READY,cycle.snapshot().phase());
        assertFalse(cycle.request(UUID.randomUUID(),new SiloCycle.Target(100,64,20),true,19_999_999));
        assertTrue(cycle.request(UUID.randomUUID(),new SiloCycle.Target(100,64,20),true,energy));
        assertFalse(cycle.request(UUID.randomUUID(),new SiloCycle.Target(200,64,20),true,energy));
    }
    @Test void lampsFollowMechanicsEvenWhenLaunchConsumesEveryFe(){
        var cycle=new SiloCycle(rules);
        assertEquals(0,SiloStatusLight.color(cycle.snapshot().phase(),0,true,0));
        assertEquals(0xFFDC38,SiloStatusLight.color(cycle.snapshot().phase(),1,true,0));
        cycle.tick(true);
        assertEquals(0xFF8A18,SiloStatusLight.color(cycle.snapshot().phase(),1,true,30));
        assertEquals(0,SiloStatusLight.color(cycle.snapshot().phase(),1,true,0));
        for(int i=0;i<rules.reloadTicks();i++)cycle.tick(true);
        assertEquals(0xFF8A18,SiloStatusLight.color(cycle.snapshot().phase(),1,true,5));
        assertTrue(cycle.request(UUID.randomUUID(),new SiloCycle.Target(0,64,0),true,rules.shotFE()));
        for(int i=0;i<rules.openTicks();i++)cycle.tick(true);
        cycle.launched(true);
        for(int i=0;i<rules.departureTicks()+rules.closeTicks();i++){
            assertEquals(0xFF2818,SiloStatusLight.color(cycle.snapshot().phase(),0,true,30));
            assertEquals(0,SiloStatusLight.color(cycle.snapshot().phase(),0,true,60));
            cycle.tick(false);
        }
        assertEquals(SiloCycle.Phase.EMPTY,cycle.snapshot().phase());
        assertEquals(0xFFDC38,SiloStatusLight.color(cycle.snapshot().phase(),1,true,0));
        assertEquals(0,SiloStatusLight.color(cycle.snapshot().phase(),0,true,0));
        assertEquals(0,SiloStatusLight.color(cycle.snapshot().phase(),1,false,0));
    }
    @Test void statusLampsBreatheContinuouslyFromDarkToFullAndBack(){
        for(var phase: new SiloCycle.Phase[]{SiloCycle.Phase.RELOADING,SiloCycle.Phase.OPENING,
                SiloCycle.Phase.DEPARTING,SiloCycle.Phase.CLOSING}){
            int energy=phase==SiloCycle.Phase.RELOADING?1:0;
            int peak=phase==SiloCycle.Phase.RELOADING?0xFF8A18:0xFF2818;
            assertEquals(0,SiloStatusLight.color(phase,energy,true,0));
            assertEquals(peak,SiloStatusLight.color(phase,energy,true,30));
            assertEquals(0,SiloStatusLight.color(phase,energy,true,60));
            int before=0;
            for(double tick=.25;tick<=60;tick+=.25){
                int color=SiloStatusLight.color(phase,energy,true,tick);
                int red=(color>>16)&255;
                if(tick<=30)assertTrue(red>=before,"rise must be monotonic");
                else assertTrue(red<=before,"fall must be monotonic");
                assertTrue(Math.abs(red-before)<=4,"no full-bright on/off edge");
                assertEquals(color,SiloStatusLight.color(phase,energy,true,tick+60));
                before=red;
            }
            int half=SiloStatusLight.color(phase,energy,true,15);
            assertTrue(((half>>16)&255)>=127&&((half>>16)&255)<=128);
            assertEquals(half,SiloStatusLight.color(phase,energy,true,45));
            assertEquals(half,SiloStatusLight.color(phase,energy,true,-15));
            assertEquals(0,SiloStatusLight.color(phase,energy,true,Double.NaN));
        }
    }
    @Test void removingAmmoCancelsReloadWithoutProducingAFreeChamber(){
        SiloCycle cycle=new SiloCycle(rules);cycle.tick(true);for(int i=0;i<5999;i++)cycle.tick(true);
        cycle.tick(false);assertEquals(SiloCycle.Phase.EMPTY,cycle.snapshot().phase());
        cycle.tick(true);assertEquals(6000,cycle.snapshot().remaining());
    }
    @Test void everyMechanicalStageSurvivesNbtAndOnlyOneLaunchGateIsConsumed(){
        SiloCycle a=ready();assertTrue(a.request(UUID.randomUUID(),new SiloCycle.Target(-123,70,800),true,rules.shotFE()));
        int gates=0;
        for(int tick=0;tick<rules.openTicks()+rules.departureTicks()+rules.closeTicks();tick++){
            SiloCycle b=new SiloCycle(rules);b.restore(SiloCycleCodec.read(SiloCycleCodec.write(a.snapshot())));
            boolean gate=a.tick(tick<rules.openTicks()),restored=b.tick(tick<rules.openTicks());
            assertEquals(gate,restored);assertEquals(a.snapshot(),b.snapshot());
            if(gate){gates++;a.launched(true);b.launched(true);assertEquals(a.snapshot(),b.snapshot());}
        }
        assertEquals(1,gates);assertEquals(SiloCycle.Phase.EMPTY,a.snapshot().phase());
    }
    @Test void refusedSpawnAndPostEventResourceLossReleaseTheCandidateWithoutDebit(){
        Port p=new Port();p.spawnAccepted=false;assertFalse(SiloLaunchTransaction.execute(p));assertTrue(p.discarded);assertEquals(0,p.debits);
        p=new Port();p.removeAmmoOnSpawn=true;assertFalse(SiloLaunchTransaction.execute(p));assertTrue(p.discarded);assertEquals(0,p.debits);
        p=new Port();p.energy=0;assertFalse(SiloLaunchTransaction.execute(p));assertTrue(p.discarded);assertEquals(0,p.spawns);
    }
    @Test void committedTransactionConsumesExactlyOnePhysicalItemAndOneBatteryOfFe(){
        Port p=new Port();assertTrue(SiloLaunchTransaction.execute(p));assertEquals(0,p.ammo);assertEquals(0,p.energy);assertEquals(1,p.spawns);assertEquals(1,p.debits);
        assertFalse(SiloLaunchTransaction.execute(p));assertEquals(1,p.debits);
    }
    @Test void dimensionRegistrationIsIdempotentAndUnloadDoesNotFreeIt() throws Exception {
        NuclearSiloStore store=new NuclearSiloStore();UUID silo=UUID.randomUUID();BlockPos anchor=new BlockPos(-17,60,15);
        assertTrue(store.claim(silo,anchor));assertTrue(store.claim(silo,anchor));assertFalse(store.claim(silo,anchor.above()));assertFalse(store.claim(UUID.randomUUID(),anchor));
        var restored=NuclearSiloStore.load(save(store));assertFalse(restored.canPlace());assertEquals(silo,restored.facility());assertEquals(anchor,restored.anchor());
        restored.releaseFacility(UUID.randomUUID());assertFalse(restored.canPlace());restored.releaseFacility(silo);assertTrue(restored.canPlace());
    }
    @Test void committedFlightLandsOnceAndPendingImpactSurvivesFacilityRemoval() throws Exception {
        var store=new NuclearSiloStore();UUID silo=UUID.randomUUID(),launch=UUID.randomUUID(),actor=UUID.randomUUID();store.claim(silo,BlockPos.ZERO);
        assertTrue(store.reserve(new NuclearSiloStore.Flight(launch,actor,silo,48000,BlockPos.ZERO,false)));
        assertFalse(store.impact(launch,BlockPos.ZERO));store.commit(launch);store.tickets(launch,Set.of(1L,2L,3L));
        var restored=NuclearSiloStore.load(save(store));assertTrue(restored.committed(launch));assertEquals(3,restored.tickets(launch).size());
        assertFalse(restored.impact(launch,BlockPos.ZERO));
        var landing=restored;
        NuclearTicketPlan.apply(landing.tickets(launch),Set.of(),c->{},c->fail("Landing acquired ticket"),chunks->landing.tickets(launch,chunks));
        assertTrue(restored.impact(launch,new BlockPos(100,64,200)));assertFalse(restored.impact(launch,BlockPos.ZERO));
        restored.releaseFacility(silo);restored=NuclearSiloStore.load(save(restored));assertTrue(restored.canPlace());assertEquals(1,restored.pendingImpacts().size());
        assertEquals(actor,restored.pendingImpacts().get(0).actor());assertFalse(restored.reserve(new NuclearSiloStore.Flight(launch,actor,silo,48000,BlockPos.ZERO,false)));
    }
    @Test void malformedAndForeignTicketNbtRemainUnchangedAndReadOnly() throws Exception {
        CompoundTag unknown=new CompoundTag();unknown.putInt("Schema",987);unknown.putString("DoNotErase","saved user data");
        var store=NuclearSiloStore.load(unknown);assertFalse(store.writable());assertFalse(store.claim(UUID.randomUUID(),BlockPos.ZERO));assertEquals(unknown,save(store));
        var clean=new NuclearSiloStore();var data=save(clean);var tickets=new net.minecraft.nbt.ListTag();var foreign=new CompoundTag();foreign.putUUID("Id",UUID.randomUUID());foreign.putLongArray("Chunks",new long[]{1});tickets.add(foreign);data.put("Tickets",tickets);
        store=NuclearSiloStore.load(data);assertFalse(store.writable());assertEquals(data,save(store));
    }
    @Test void legacyImpactTicketsRecoverOnlyWithValidReceiptAndPreservePayloadAndFacility() throws Exception {
        var store=new NuclearSiloStore();UUID silo=UUID.randomUUID(),launch=UUID.randomUUID(),actor=UUID.randomUUID();
        store.claim(silo,BlockPos.ZERO);store.tickets(silo,Set.of(7L));
        store.reserve(new NuclearSiloStore.Flight(launch,actor,silo,48000,BlockPos.ZERO,false));store.commit(launch);
        assertTrue(store.impact(launch,new BlockPos(10,64,10)));
        var data=save(store);var legacy=new CompoundTag();legacy.putUUID("Id",launch);legacy.putLongArray("Chunks",new long[]{1,2,3});
        data.getList("Tickets",net.minecraft.nbt.Tag.TAG_COMPOUND).add(legacy);
        var recovered=NuclearSiloStore.load(data);assertTrue(recovered.writable());assertTrue(recovered.isDirty());
        assertEquals(silo,recovered.facility());assertEquals(Set.of(7L),recovered.tickets(silo));
        assertTrue(recovered.tickets(launch).isEmpty());assertFalse(recovered.validTicketOwner(launch,0));
        assertEquals(store.pendingImpacts(),recovered.pendingImpacts());assertTrue(recovered.committed(launch));
        assertTrue(NuclearSiloStore.load(save(recovered)).writable());
        var noReceipt=data.copy();noReceipt.put("Receipts",new net.minecraft.nbt.ListTag());
        var invalid=NuclearSiloStore.load(noReceipt);assertFalse(invalid.writable());assertEquals(noReceipt,save(invalid));
        var duplicate=data.copy();duplicate.getList("Tickets",net.minecraft.nbt.Tag.TAG_COMPOUND).add(legacy.copy());
        invalid=NuclearSiloStore.load(duplicate);assertFalse(invalid.writable());assertEquals(duplicate,save(invalid));
    }
    @Test void pendingPayloadBudgetStopsUnboundedSavedDataGrowth(){
        var store=new NuclearSiloStore();UUID silo=UUID.randomUUID(),actor=UUID.randomUUID();store.claim(silo,BlockPos.ZERO);
        for(int i=0;i<64;i++){UUID id=UUID.randomUUID();assertTrue(store.reserve(new NuclearSiloStore.Flight(id,actor,silo,48000,BlockPos.ZERO,false)));store.commit(id);assertTrue(store.impact(id,BlockPos.ZERO));}
        assertFalse(store.reserve(new NuclearSiloStore.Flight(UUID.randomUUID(),actor,silo,48000,BlockPos.ZERO,false)));assertEquals(64,store.pendingImpacts().size());
    }
    @Test void fullTicketWindowReleasesBeforeAcquiringAndDoesNotReacquireUnchangedTickets(){
        Set<Long> held=new HashSet<>(Set.of(1L,2L,3L));int[] additions={0};
        NuclearTicketPlan.apply(Set.copyOf(held),Set.of(4L,5L,6L),held::remove,c->{assertTrue(held.size()<3);held.add(c);additions[0]++;});
        assertEquals(Set.of(4L,5L,6L),held);assertEquals(3,additions[0]);
        NuclearTicketPlan.apply(Set.copyOf(held),Set.copyOf(held),c->fail("Unnecessary release"),c->fail("Unnecessary acquire"));
        assertThrows(IllegalArgumentException.class,()->NuclearTicketPlan.apply(held,Set.of(1L,2L,3L,4L),held::remove,held::add));
    }
    @Test void diagonalAndNegativeChunkCrossingsCoverTheWholeNextMovementStep(){
        for(int x=-33;x<33;x++)for(int z=-33;z<33;z++)for(int sign:new int[]{-1,1}){
            var p=new NuclearFlight.Point(x+.1,1024,z+.8);var v=new NuclearFlight.Point(sign*8,0,-sign*8);var tickets=NuclearFlight.chunks(p,v);assertTrue(tickets.size()<=3);
            for(int step=0;step<=20;step++){var q=p.add(v.scale(step/20.0));int cx=(int)Math.floor(q.x()/16),cz=(int)Math.floor(q.z()/16);assertTrue(tickets.contains((cx&0xffffffffL)|((cz&0xffffffffL)<<32)));}
        }
    }
    @Test void independentCoordinateFlightExitsVerticallyAndReachesMaximumRangeWithinLifetime(){
        var origin=new NuclearFlight.Point(0,65,0);var target=new NuclearFlight.Point(200000,65,0);assertTrue(NuclearFlight.withinRange(origin,target,rules));
        assertFalse(NuclearFlight.withinRange(origin,new NuclearFlight.Point(200000.01,65,0),rules));
        var stage=NuclearFlight.Stage.CLIMB;
        var p=origin;for(int age=0;age<rules.exitTicks();age++)p=p.add(NuclearFlight.velocity(p,target,age,stage,rules));
        assertEquals(0,p.x());assertEquals(0,p.z());assertEquals(76.2,p.y(),1e-7);
        int age=rules.exitTicks();for(;age<rules.lifeTicks()&&p.distance(target)>.5;age++){stage=NuclearFlight.stage(p,target,stage,rules);var v=NuclearFlight.velocity(p,target,age,stage,rules);assertTrue(v.distance(new NuclearFlight.Point(0,0,0))<=rules.maxSpeed()+1e-7);assertTrue(NuclearFlight.chunks(p,v).size()<=3);p=p.add(v);}
        assertTrue(age<rules.lifeTicks());assertTrue(p.distance(target)<=.5);
    }
    @Test void nearAndThresholdTargetsAllClimbBeforeDivingAndAccelerationIsExponential(){
        for(double distance:new double[]{0,20,1399,1400,1401,200000}){
            var target=new NuclearFlight.Point(distance,65,0);var p=new NuclearFlight.Point(0,65,0);
            var stage=NuclearFlight.Stage.CLIMB;boolean dove=false;double maxY=p.y();int age=0;
            for(;age<rules.lifeTicks();age++){
                stage=NuclearFlight.stage(p,target,stage,rules);
                if(stage==NuclearFlight.Stage.DIVE){dove=true;assertTrue(maxY>=rules.cruiseAltitude()-1e-6);}
                var v=NuclearFlight.velocity(p,target,age,stage,rules);p=p.add(v);maxY=Math.max(maxY,p.y());
                if(stage==NuclearFlight.Stage.DIVE&&p.distance(target)<=.5)break;
            }
            assertTrue(dove);assertTrue(age<rules.lifeTicks());assertTrue(p.distance(target)<=.5);
        }
        for(int age=0;age<70;age++)assertEquals(Math.exp(rules.accelerationRate()),NuclearFlight.speed(age+1,rules)/NuclearFlight.speed(age,rules),1e-10);
        assertTrue(NuclearFlight.speed(0,rules)*20>1.2,"Ignition must not spend seconds nearly stationary");
        assertTrue(NuclearFlight.speed(0,rules)*20<2,"Ignition remains continuous with the racked position");
        assertEquals(40,rules.exitTicks());assertEquals(8.5,rules.maxSpeed());
        assertEquals(rules.maxSpeed(),NuclearFlight.speed(85,rules),1e-9,"Full boost within 4.25 seconds");
        assertEquals(rules.maxSpeed(),NuclearFlight.speed(Integer.MAX_VALUE,rules),1e-9);
    }
    @Test void verticalDepartureKeepsRackYawUntilThereIsARealHorizontalHeading(){
        for(float yaw:new float[]{0,90,180,270}){
            assertEquals(yaw,NuclearFlight.yaw(new NuclearFlight.Point(0,4,0),yaw));
            assertEquals(yaw,NuclearFlight.yaw(new NuclearFlight.Point(0,-4,0),yaw));
            var ready=NuclearRackMotion.tail(NuclearMechanicalTimeline.READY_FRAME,yaw);
            assertTrue(ready.distance(NuclearRackMotion.tail(NuclearMechanicalTimeline.LAUNCH_FRAME,yaw))<1e-5);
        }
        assertEquals(-90,NuclearFlight.yaw(new NuclearFlight.Point(4,0,0),180));
    }
    @Test void version150MechanicalProgressMigratesToFasterTimingAndClosingCanFinish(){
        var id=UUID.randomUUID();var target=new SiloCycle.Target(10,64,20);
        for(var phase:new SiloCycle.Phase[]{SiloCycle.Phase.OPENING,SiloCycle.Phase.DEPARTING,SiloCycle.Phase.CLOSING}){
            int old=phase==SiloCycle.Phase.OPENING?148:phase==SiloCycle.Phase.DEPARTING?321:374;
            int current=phase==SiloCycle.Phase.OPENING?rules.openTicks():phase==SiloCycle.Phase.DEPARTING?rules.departureTicks():rules.closeTicks();
            var tag=SiloCycleCodec.write(new SiloCycle.Snapshot(phase,old/2,id,target));tag.putInt("Schema",4);
            var state=SiloCycleCodec.read(tag);assertEquals((int)Math.ceil((old/2)*(double)current/old),state.remaining());
            assertEquals(id,state.launch());assertEquals(target,state.target());assertEquals(state,SiloCycleCodec.read(SiloCycleCodec.write(state)));
        }
        var cycle=new SiloCycle(rules);cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.CLOSING,100,id,target));
        for(int tick=0;tick<100;tick++)cycle.tick(false);assertEquals(SiloCycle.Phase.EMPTY,cycle.snapshot().phase());
    }
    @Test void preparedRackAndAuthoringTimelineAgreeInAllFourFacings(){
        assertEquals(1,NuclearMechanicalTimeline.frame(SiloCycle.Phase.RELOADING,6000,rules));
        assertEquals(600,NuclearMechanicalTimeline.PREPARATION_TICKS);
        assertEquals(1,NuclearMechanicalTimeline.frame(SiloCycle.Phase.RELOADING,1720,rules));
        assertEquals(781,NuclearMechanicalTimeline.frame(SiloCycle.Phase.RELOADING,1200,rules));
        assertEquals(1381,NuclearMechanicalTimeline.frame(SiloCycle.Phase.RELOADING,800,rules));
        assertEquals(1681,NuclearMechanicalTimeline.frame(SiloCycle.Phase.RELOADING,600,rules));
        assertEquals(1681,NuclearMechanicalTimeline.frame(SiloCycle.Phase.RELOADING,0,rules));
        assertEquals(1681,NuclearMechanicalTimeline.frame(SiloCycle.Phase.READY,0,rules));
        assertEquals(1681,NuclearMechanicalTimeline.frame(SiloCycle.Phase.OPENING,rules.openTicks(),rules));
        assertEquals(1903,NuclearMechanicalTimeline.frame(SiloCycle.Phase.OPENING,0,rules));
        assertEquals(2384,NuclearMechanicalTimeline.frame(SiloCycle.Phase.CLOSING,rules.closeTicks(),rules));
        assertEquals(2944,NuclearMechanicalTimeline.frame(SiloCycle.Phase.CLOSING,0,rules));
        for(float yaw:new float[]{0,90,180,270}){
            var ready=NuclearRackMotion.tail(1681,yaw);assertEquals(0,ready.x,1e-5);assertEquals(0,ready.z,1e-5);
            assertEquals(NuclearModelData.DEFAULT.readyTailHeight(),ready.y,1e-5);
            assertTrue(NuclearRackMotion.tail(1,yaw).y<ready.y-1);
            float start=NuclearMechanicalTimeline.frame(SiloCycle.Phase.RELOADING,1200,rules);
            assertEquals(NuclearRackMotion.tail(1,yaw).y,NuclearRackMotion.tail(start,yaw).y,1e-5);
            float moving=NuclearMechanicalTimeline.frame(SiloCycle.Phase.RELOADING,1180,rules);
            assertTrue(NuclearRackMotion.tail(moving,yaw).y>NuclearRackMotion.tail(start,yaw).y,
                    "Physical rack must follow the V20 lift, twenty seconds before the support-arm sequence");
        }
    }
    @Test void previousCycleDurationsKeepTheirProgressAndTransactionWhenLoadedIntoSlowerMechanisms(){
        UUID id=UUID.randomUUID();var target=new SiloCycle.Target(-123,70,800);
        for(var phase:new SiloCycle.Phase[]{SiloCycle.Phase.OPENING,SiloCycle.Phase.DEPARTING,SiloCycle.Phase.CLOSING}){
            int oldDuration=phase==SiloCycle.Phase.CLOSING?80:54;
            int duration=switch(phase){case OPENING->rules.openTicks();case DEPARTING->rules.departureTicks();default->rules.closeTicks();};
            for(int left:new int[]{0,1,oldDuration/2,oldDuration}){
                var tag=SiloCycleCodec.write(new SiloCycle.Snapshot(phase,left,id,target));tag.putInt("Schema",2);
                var migrated=SiloCycleCodec.read(tag);
                assertEquals((int)Math.ceil(left*(double)duration/oldDuration),migrated.remaining());
                assertEquals(id,migrated.launch());assertEquals(target,migrated.target());
                assertEquals(migrated,SiloCycleCodec.read(SiloCycleCodec.write(migrated)));
            }
        }
    }
    @Test void duplicateNativeAndFallbackTicksShareTheSameClock(){
        var gate=new NuclearTickGate();assertTrue(gate.claim(123));assertFalse(gate.claim(123));assertTrue(gate.claim(124));assertFalse(gate.claim(124));
    }
    @Test void malformedMechanicalStateCannotInventLaunchOrAmmunition(){
        var cycle=new SiloCycle(rules);assertThrows(IllegalArgumentException.class,()->cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.OPENING,0,null,null)));
        assertThrows(IllegalArgumentException.class,()->cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.READY,0,UUID.randomUUID(),new SiloCycle.Target(1,2,3))));
        var bad=SiloCycleCodec.write(cycle.snapshot());bad.putString("Launch","invalid");assertThrows(IllegalArgumentException.class,()->SiloCycleCodec.read(bad));
    }
    @Test void legacyOpenAndResetDurationsMigrateOnceWithoutChangingOperationOrTarget(){
        UUID id=UUID.randomUUID();var target=new SiloCycle.Target(-123,70,800);
        for(var phase:new SiloCycle.Phase[]{SiloCycle.Phase.OPENING,SiloCycle.Phase.CLOSING}){
            int oldDuration=phase==SiloCycle.Phase.OPENING?190:40;
            for(int left:new int[]{0,1,oldDuration/2,oldDuration}){
                var tag=SiloCycleCodec.write(new SiloCycle.Snapshot(phase,left,id,target));tag.putInt("Schema",1);
                var migrated=SiloCycleCodec.read(tag);assertEquals(id,migrated.launch());assertEquals(target,migrated.target());
                assertEquals((int)Math.ceil(left*(double)(phase==SiloCycle.Phase.OPENING?rules.openTicks():rules.closeTicks())/oldDuration),migrated.remaining());
                assertEquals(migrated,SiloCycleCodec.read(SiloCycleCodec.write(migrated)));
            }
        }
    }
    @Test void cancelledPreparedShotReturnsContinuouslyWithoutRepeatingTheFiveMinuteLoad(){
        var cycle=ready();UUID operation=UUID.randomUUID();var target=new SiloCycle.Target(100,64,0);
        assertTrue(cycle.request(operation,target,true,rules.shotFE()));
        for(int i=0;i<rules.openTicks();i++)cycle.tick(true);
        cycle.launched(false);assertEquals(SiloCycle.Phase.RETURNING,cycle.snapshot().phase());
        assertEquals(operation,cycle.snapshot().launch());assertFalse(cycle.canPack());
        float previous=NuclearMechanicalTimeline.LAUNCH_FRAME;
        for(int i=0;i<rules.openTicks();i++){
            float frame=NuclearMechanicalTimeline.frame(cycle.snapshot().phase(),cycle.snapshot().remaining(),rules);
            assertTrue(frame<=previous&&frame>=NuclearMechanicalTimeline.READY_FRAME);previous=frame;
            assertEquals(0,NuclearRackMotion.tail(frame,0).distance(NuclearRackMotion.tail(NuclearMechanicalTimeline.READY_FRAME,0)),1e-5);
            cycle.tick(true);
        }
        assertEquals(SiloCycle.Phase.READY,cycle.snapshot().phase());assertNull(cycle.snapshot().launch());
        assertTrue(cycle.request(UUID.randomUUID(),target,true,rules.shotFE()));
    }
    @Test void successfulShotWithMoreInventoryStillRequiresAFullReload(){
        var cycle=ready();assertTrue(cycle.request(UUID.randomUUID(),new SiloCycle.Target(100,64,0),true,rules.shotFE()));
        for(int i=0;i<rules.openTicks();i++)cycle.tick(true);cycle.launched(true);
        for(int i=0;i<rules.departureTicks()+rules.closeTicks();i++)cycle.tick(true);
        assertEquals(SiloCycle.Phase.EMPTY,cycle.snapshot().phase());cycle.tick(true);
        assertEquals(SiloCycle.Phase.RELOADING,cycle.snapshot().phase());assertEquals(6000,cycle.snapshot().remaining());
        assertFalse(cycle.request(UUID.randomUUID(),new SiloCycle.Target(100,64,0),true,rules.shotFE()));
    }
    @Test void returningSavesItsProgressAndRetainedAmmunitionControlsTheFinalState(){
        var state=new SiloCycle.Snapshot(SiloCycle.Phase.RETURNING,17,UUID.randomUUID(),new SiloCycle.Target(100,64,0));
        var tag=SiloCycleCodec.write(state);assertEquals(6,tag.getInt("Schema"));
        var restored=new SiloCycle(rules);restored.restore(SiloCycleCodec.read(tag));assertEquals(state,restored.snapshot());
        for(int i=0;i<17;i++)restored.tick(true);assertEquals(SiloCycle.Phase.READY,restored.snapshot().phase());
        restored.restore(state);for(int i=0;i<17;i++)restored.tick(false);assertEquals(SiloCycle.Phase.EMPTY,restored.snapshot().phase());
        tag.putInt("Schema",5);assertThrows(IllegalArgumentException.class,()->SiloCycleCodec.read(tag));
        var previous=new SiloCycle.Snapshot(SiloCycle.Phase.CLOSING,17,UUID.randomUUID(),new SiloCycle.Target(100,64,0));
        var old=SiloCycleCodec.write(previous);old.putInt("Schema",5);assertEquals(previous,SiloCycleCodec.read(old));
    }
    @Test void quickPreparationNeverOverridesAnOperationOrCreatesAmmunition(){
        var cycle=new SiloCycle(rules);assertFalse(cycle.completePreparation(false));
        assertTrue(cycle.completePreparation(true));assertEquals(SiloCycle.Phase.READY,cycle.snapshot().phase());
        assertFalse(cycle.completePreparation(true));
        assertTrue(cycle.request(UUID.randomUUID(),new SiloCycle.Target(100,64,0),true,rules.shotFE()));
        var before=cycle.snapshot();assertFalse(cycle.completePreparation(true));assertEquals(before,cycle.snapshot());
        for(int i=0;i<rules.openTicks();i++)cycle.tick(true);cycle.launched(false);
        before=cycle.snapshot();assertFalse(cycle.completePreparation(true));assertEquals(before,cycle.snapshot());
    }
    @Test void removingAndReplacingStockDuringReturnInvalidatesPreparedProgressAcrossSaveLoad(){
        var cycle=ready();assertTrue(cycle.request(UUID.randomUUID(),new SiloCycle.Target(100,64,0),true,rules.shotFE()));
        for(int i=0;i<rules.openTicks();i++)cycle.tick(true);cycle.launched(false);
        cycle.tick(false);assertFalse(cycle.snapshot().preparationRetained());
        var saved=SiloCycleCodec.write(cycle.snapshot());cycle.restore(SiloCycleCodec.read(saved));
        for(int i=0;i<rules.openTicks()-1;i++)cycle.tick(true);
        assertEquals(SiloCycle.Phase.EMPTY,cycle.snapshot().phase());cycle.tick(true);
        assertEquals(SiloCycle.Phase.RELOADING,cycle.snapshot().phase());assertEquals(6000,cycle.snapshot().remaining());
        saved.remove("PreparationRetained");assertThrows(IllegalArgumentException.class,()->SiloCycleCodec.read(saved));
    }
    @Test void cancellationKeepsReadySoundObserversAndReturningLightsConsistent(){
        var observer=new NuclearReadyCue();var before=new SiloCycle.Snapshot(SiloCycle.Phase.READY,0,null,null);
        observer.observe(before);observer.announce(before,before,true);
        var returning=new SiloCycle.Snapshot(SiloCycle.Phase.RETURNING,10,UUID.randomUUID(),new SiloCycle.Target(100,64,0));
        assertFalse(observer.observe(before,returning));assertFalse(observer.announce(before,returning,true));
        assertFalse(observer.observe(returning,before));assertFalse(observer.announce(returning,before,true));
        assertEquals(0xFF2818,SiloStatusLight.color(SiloCycle.Phase.RETURNING,0,true,30));
    }
    @Test void creativeAnimationShortcutPreservesTheTimelineSavingAndLaunchGates(){
        for(boolean initiallyLoading:new boolean[]{false,true}){
            var cycle=new SiloCycle(rules);if(initiallyLoading)cycle.tick(true);
            assertTrue(cycle.startPreparation(true));assertEquals(1720,cycle.snapshot().remaining());
            assertEquals(1,NuclearMechanicalTimeline.frame(cycle.snapshot().phase(),cycle.snapshot().remaining(),rules));
            for(int i=0;i<1720;i++){
                var before=cycle.snapshot();assertFalse(cycle.startPreparation(true));assertEquals(before,cycle.snapshot());
                assertFalse(cycle.request(UUID.randomUUID(),new SiloCycle.Target(100,64,0),true,rules.shotFE()));
                cycle.restore(SiloCycleCodec.read(SiloCycleCodec.write(before)));cycle.tick(true);
                assertTrue(NuclearMechanicalTimeline.frame(cycle.snapshot().phase(),cycle.snapshot().remaining(),rules)>1);
            }
            assertEquals(SiloCycle.Phase.READY,cycle.snapshot().phase());assertFalse(cycle.startPreparation(true));
            assertFalse(cycle.request(UUID.randomUUID(),new SiloCycle.Target(100,64,0),true,0));
            assertTrue(cycle.request(UUID.randomUUID(),new SiloCycle.Target(100,64,0),true,rules.shotFE()));
            var busy=cycle.snapshot();assertFalse(cycle.startPreparation(true));assertEquals(busy,cycle.snapshot());
        }
    }
    @Test void creativeAnimationCannotInventStockOrRetainItAfterRemoval(){
        var cycle=new SiloCycle(rules);assertFalse(cycle.startPreparation(false));assertEquals(SiloCycle.Phase.EMPTY,cycle.snapshot().phase());
        cycle.tick(true);assertFalse(cycle.startPreparation(false));assertEquals(6000,cycle.snapshot().remaining());
        assertTrue(cycle.startPreparation(true));cycle.tick(false);assertEquals(SiloCycle.Phase.EMPTY,cycle.snapshot().phase());
        cycle.tick(true);assertEquals(6000,cycle.snapshot().remaining());
        for(var phase:new SiloCycle.Phase[]{SiloCycle.Phase.OPENING,SiloCycle.Phase.RETURNING,SiloCycle.Phase.DEPARTING,SiloCycle.Phase.CLOSING}){
            var busy=new SiloCycle.Snapshot(phase,1,UUID.randomUUID(),new SiloCycle.Target(100,64,0));cycle.restore(busy);
            assertFalse(cycle.startPreparation(true));assertEquals(busy,cycle.snapshot());
        }
    }
    private SiloCycle ready(){var c=new SiloCycle(rules);c.tick(true);for(int i=0;i<6000;i++)c.tick(true);return c;}
    static CompoundTag save(NuclearSiloStore store) throws Exception {
        for(var method:NuclearSiloStore.class.getDeclaredMethods())if(method.getName().equals("save")&&method.getParameterTypes()[0]==CompoundTag.class){Object[] args=new Object[method.getParameterCount()];args[0]=new CompoundTag();return (CompoundTag)method.invoke(store,args);}
        throw new AssertionError("Missing platform save boundary");
    }
    static class Port implements SiloLaunchTransaction.Port {
        int ammo=1,energy=20_000_000,spawns,debits;boolean spawnAccepted=true,removeAmmoOnSpawn,discarded;
        public boolean resourcesAvailable(){return ammo>0&&energy>=20_000_000;}
        public boolean spawnUnarmed(){spawns++;if(removeAmmoOnSpawn)ammo=0;return spawnAccepted;}
        public void discardCandidate(){discarded=true;}
        public void debitAndArm(){ammo--;energy-=20_000_000;debits++;}
    }
}
