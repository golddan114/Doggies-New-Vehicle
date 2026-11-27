package com.liucodex.doggiesnewvehicle.artillery.silo;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class VacuumPayloadTest {
    private record Job(NuclearSiloStore store,UUID id) { }
    private static Job job(SiloPayloadKind kind){
        var store=new NuclearSiloStore();var silo=UUID.randomUUID();var id=UUID.randomUUID();
        assertTrue(store.claim(silo,BlockPos.ZERO));
        assertTrue(store.reserve(new NuclearSiloStore.Flight(id,UUID.randomUUID(),silo,10000,BlockPos.ZERO,false,kind)));
        store.commit(id);assertTrue(store.impact(id,BlockPos.ZERO));return new Job(store,id);
    }
    private static VacuumPayloadEngine.Work tick(Job job,World world,long time){
        return VacuumPayloadEngine.tick(job.store(),world,time,()->0L,Long.MAX_VALUE);
    }
    @Test void pullsSixSecondsRevealsForTwoThenBurstsExactlyOnceAndFadesUntil222(){
        var job=job(SiloPayloadKind.DOGEGG);var world=new World();tick(job,world,0);
        assertEquals(1,world.starts);assertEquals(0,job.store().pendingImpact(job.id()).vacuum().age());
        for(int time=1;time<120;time++){
            tick(job,world,time);int pulls=world.pulls;tick(job,world,time);assertEquals(pulls,world.pulls);
            assertEquals(0,world.bursts);
        }
        int pulls=world.pulls;
        for(int time=120;time<160;time++){tick(job,world,time);assertEquals(0,world.bursts);assertEquals(pulls,world.pulls);}
        tick(job,world,160);
        assertEquals(1,world.bursts);assertEquals(pulls,world.pulls);
        assertTrue(job.store().pendingImpact(job.id()).vacuum().burst());
        for(int time=161;time<222;time++)tick(job,world,time);
        assertNotNull(job.store().pendingImpact(job.id()));tick(job,world,222);
        assertNull(job.store().pendingImpact(job.id()));assertEquals(1,world.bursts);
    }
    @Test void savingBeforeAndAfterBurstNeverRestartsTheEffectOrReplaysItsBurst(){
        var job=job(SiloPayloadKind.DOGEGG);var world=new World();tick(job,world,0);
        for(int time=1;time<=159;time++)tick(job,world,time);
        var reloaded=new Job(NuclearSiloStore.load(job.store().save(new net.minecraft.nbt.CompoundTag())),job.id());
        assertTrue(reloaded.store().writable());tick(reloaded,world,160);assertEquals(1,world.bursts);
        reloaded=new Job(NuclearSiloStore.load(reloaded.store().save(new net.minecraft.nbt.CompoundTag())),job.id());
        tick(reloaded,world,160);tick(reloaded,world,161);assertEquals(1,world.bursts);assertEquals(1,world.starts);
    }
    @Test void cancelledStartAndUnloadedChunksCannotApplyWorldEffects(){
        var job=job(SiloPayloadKind.DOGEGG);var world=new World();world.available=false;
        for(int time=0;time<300;time++)tick(job,world,time);
        assertFalse(job.store().pendingImpact(job.id()).vacuum().started());assertEquals(0,world.starts);
        world.available=true;world.allow=false;tick(job,world,300);
        assertNull(job.store().pendingImpact(job.id()));assertEquals(0,world.pulls);assertEquals(0,world.peels);assertEquals(0,world.bursts);
    }
    @Test void dimensionWideBudgetsIncludeAllJobsWhileTheirTimersRemainIndependent(){
        var job=job(SiloPayloadKind.DOGEGG);var second=UUID.randomUUID();
        var first=job.store().pendingImpact(job.id());var silo=job.store().facility();
        job.store().reserve(new NuclearSiloStore.Flight(second,first.actor(),silo,10000,BlockPos.ZERO,false,SiloPayloadKind.DOGEGG));
        job.store().commit(second);job.store().impact(second,BlockPos.ZERO);
        var world=new World();world.candidates=true;world.entityUse=512;
        tick(job,world,0);
        for(int time=1;time<170;time++){
            var work=tick(job,world,time);
            assertTrue(work.visits()<=VacuumPayloadEngine.VISITS_PER_TICK);assertTrue(work.blocks()<=VacuumPayloadEngine.BLOCKS_PER_TICK);assertTrue(work.entities()<=512);
            assertTrue(work.starts()<=1);
        }
        assertEquals(2,world.bursts);assertEquals(2,world.starts);
    }
    @Test void exhaustedTerrainTimeDoesNotPostponeTheEightSecondBurst(){
        var job=job(SiloPayloadKind.DOGEGG);var world=new World();world.candidates=true;
        for(int time=0;time<=160;time++){
            var clock=new java.util.concurrent.atomic.AtomicLong();
            var work=VacuumPayloadEngine.tick(job.store(),world,time,()->clock.addAndGet(10),1);
            assertEquals(0,work.visits());
        }
        assertEquals(1,world.bursts);assertEquals(0,world.peels);
    }
    @Test void nuclearAndVacuumQueuesCannotConsumeEachOthersProgress(){
        var job=job(SiloPayloadKind.NUCLEAR);var world=new World();
        for(int time=0;time<300;time++)tick(job,world,time);
        assertEquals(0,world.starts);assertEquals(NuclearPayloadProgress.EMPTY,job.store().pendingImpact(job.id()).progress());
        var dog=job(SiloPayloadKind.DOGEGG);
        assertThrows(IllegalStateException.class,()->dog.store().advanceImpact(dog.id(),NuclearPayloadProgress.EMPTY));
        assertThrows(IllegalStateException.class,()->job.store().advanceVacuum(job.id(),VacuumPayloadProgress.EMPTY));
    }
    @Test void flightKindSurvivesObservationCommitAndLandingAndUnknownDataFailsClosed(){
        var job=job(SiloPayloadKind.DOGEGG);var tag=job.store().save(new net.minecraft.nbt.CompoundTag());
        var entry=tag.getList("Impacts",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        assertEquals("DOGEGG",entry.getString("Kind"));entry.putString("Kind","UNKNOWN");
        var invalid=NuclearSiloStore.load(tag);assertFalse(invalid.writable());assertEquals(tag,invalid.save(new net.minecraft.nbt.CompoundTag()));
        assertThrows(IllegalArgumentException.class,()->new VacuumPayloadProgress(true,119,0,true,1));
        assertThrows(IllegalArgumentException.class,()->new VacuumPayloadProgress(false,1,0,false,-1));
    }
    @Test void previousSchemaStillLoadsAsNuclearAndProgressCannotRewind(){
        var old=job(SiloPayloadKind.NUCLEAR);var tag=old.store().save(new net.minecraft.nbt.CompoundTag());
        tag.putInt("Schema",1);tag.getList("Impacts",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("Kind");
        var migrated=NuclearSiloStore.load(tag);assertTrue(migrated.writable());
        assertEquals(SiloPayloadKind.NUCLEAR,migrated.pendingImpact(old.id()).kind());
        var dog=job(SiloPayloadKind.DOGEGG);var world=new World();tick(dog,world,0);tick(dog,world,1);
        assertThrows(IllegalArgumentException.class,()->dog.store().advanceVacuum(dog.id(),VacuumPayloadProgress.EMPTY));
    }
    @Test void nativePullMathIsFiniteBoundedAndStopsAtTheBurst(){
        for(int age=0;age<120;age++)for(double distance:new double[]{0,.01,.15,1,16,32}){
            var value=VacuumPull.velocity(distance,0,0,.1,.2,.1,age);
            assertTrue(Double.isFinite(value.x()+value.y()+value.z()));
            assertTrue(Math.sqrt(value.x()*value.x()+value.y()*value.y()+value.z()*value.z())<=1.2000001);
        }
        assertEquals(new VacuumPull.Motion(1,2,3),VacuumPull.velocity(33,0,0,1,2,3,0));
        assertEquals(new VacuumPull.Motion(1,2,3),VacuumPull.velocity(1,0,0,1,2,3,120));
        assertEquals(new VacuumPull.Motion(0,0,0),VacuumPull.velocity(Double.NaN,0,0,1,2,3,0));
    }
    @Test void hostilityRequiresActualEnemyOrDeclaredFactionAndAlwaysRejectsFriends(){
        assertFalse(VacuumTargetRules.hostile("red","blue",false,false));
        assertFalse(VacuumTargetRules.hostile(null,null,false,false));
        assertTrue(VacuumTargetRules.hostile("dnv_allied","dnv_boss",false,false));
        assertTrue(VacuumTargetRules.hostile("dnv_allied",null,false,true));
        assertFalse(VacuumTargetRules.hostile("dnv_allied","dnv_allied",false,true));
        assertFalse(VacuumTargetRules.hostile("dnv_allied","dnv_boss",true,true));
    }
    @Test void buriedTerrainIsVisitedCenterOutWithinTheSharedBudget() {
        int previous=-1;var positions=new java.util.HashSet<BlockPos>();
        for(int index=0;index<VacuumTerrainScan.size();index++){
            var p=VacuumTerrainScan.offset(index);int d=p.getX()*p.getX()+p.getY()*p.getY()+p.getZ()*p.getZ();
            assertTrue(d>=previous && d<=256);previous=d;assertTrue(positions.add(p));
        }
        assertEquals(17077,positions.size());assertTrue(positions.contains(new BlockPos(0,-16,0)));
        assertFalse(positions.contains(new BlockPos(0,-17,0)));
        var job=job(SiloPayloadKind.DOGEGG);var world=new World();world.candidates=true;
        for(int time=0;time<120;time++)tick(job,world,time);
        assertEquals(VacuumTerrainScan.size(),world.removed.size());assertEquals(world.removed.size(),new java.util.HashSet<>(world.removed).size());
        assertTrue(world.removed.contains(new BlockPos(0,-16,0)));
        assertFalse(world.removed.contains(new BlockPos(0,-17,0)));
        assertTrue(world.removed.contains(new BlockPos(0,-8,0)),"Ordinary buried layers participate without adjacency-to-air");
    }
    @Test void legacyConsumedBurstsCannotReplayAndBadCursorsStillFailClosed() {
        var old=new VacuumPayloadProgress(true,120,500,true,100).save();old.putInt("Schema",1);
        var migrated=VacuumPayloadProgress.load(old);assertEquals(0,migrated.terrainCursor());assertTrue(migrated.burst());
        for(int time=101;time<200;time++)migrated=migrated.tick(time);
        assertSame(migrated,migrated.consumeBurst());assertEquals(4,migrated.save().getInt("Schema"));
        old.putInt("Terrain",-1);assertThrows(IllegalArgumentException.class,()->VacuumPayloadProgress.load(old));
        old.putInt("Terrain",Integer.MAX_VALUE);assertThrows(IllegalArgumentException.class,()->VacuumPayloadProgress.load(old));
        old.putInt("Terrain",1);old.putBoolean("Started",false);old.putBoolean("Burst",false);old.putInt("Age",0);old.putLong("LastTick",-1);
        assertThrows(IllegalArgumentException.class,()->VacuumPayloadProgress.load(old),"Migration cannot hide an invalid unstarted cursor");
    }
    @Test void unloadingDuringTheTwoSecondRevealPausesWithoutOfflineCatchupOrAnotherStart() {
        var job=job(SiloPayloadKind.DOGEGG);var world=new World();
        for(int time=0;time<=140;time++)tick(job,world,time);
        var restored=new Job(NuclearSiloStore.load(job.store().save(new net.minecraft.nbt.CompoundTag())),job.id());
        world.available=false;for(int time=141;time<=1000;time++)tick(restored,world,time);
        assertEquals(140,restored.store().pendingImpact(restored.id()).vacuum().age());
        world.available=true;for(int time=1001;time<1020;time++)tick(restored,world,time);
        assertEquals(0,world.bursts);tick(restored,world,1020);assertEquals(1,world.bursts);assertEquals(1,world.starts);
    }
    @Test void blastTerrainHasItsOwnPersistedBoundedCursorAndDoesNotReplayAfterReload(){
        var job=job(SiloPayloadKind.DOGEGG);var world=new World();world.candidates=true;
        for(int time=0;time<=160;time++)tick(job,world,time);
        assertEquals(VacuumPayloadEngine.BLOCKS_PER_TICK,world.blastRemoved.size());
        assertEquals(1,world.bursts);
        var restored=new Job(NuclearSiloStore.load(job.store().save(new net.minecraft.nbt.CompoundTag())),job.id());
        assertTrue(restored.store().writable());
        for(int time=161;time<=222;time++)tick(restored,world,time);
        assertEquals(VacuumTerrainScan.blastSize(),world.blastRemoved.size());
        assertEquals(world.blastRemoved.size(),new java.util.HashSet<>(world.blastRemoved).size());
        assertTrue(world.blastRemoved.contains(new BlockPos(16,2,0)));assertEquals(1,world.bursts);
        assertNull(restored.store().pendingImpact(restored.id()));
    }
    @Test void cancelledTerminalExplosionCannotLeaveADeferredTerrainBlast(){
        var job=job(SiloPayloadKind.DOGEGG);var world=new World();
        for(int time=0;time<160;time++)tick(job,world,time);
        world.allow=false;world.candidates=true;tick(job,world,160);
        for(int time=161;time<=222;time++)tick(job,world,time);
        assertTrue(world.blastRemoved.isEmpty());assertEquals(1,world.bursts);
        assertNull(job.store().pendingImpact(job.id()));
    }
    @Test void fixedBlastTypeBypassesResistanceArmorEnchantmentsAndNativeVehicleReduction()throws Exception{
        var nuclear=new NuclearPayloadTest();String id="doggiesnewvehicle:vacuum_blast";
        for(String tag:List.of("bypasses_armor","bypasses_resistance","bypasses_enchantments","bypasses_shield","bypasses_cooldown","is_explosion"))
            assertTrue(nuclear.damageTag("minecraft",tag).contains(id),tag);
        assertTrue(nuclear.damageTag("superbwarfare","bypasses_vehicle").contains(id));
        assertTrue(nuclear.damageTag("doggiesnewvehicle","armor_special").contains(id),"DNV physical presets must not restore armor reduction");
        var file=java.nio.file.Path.of("src/artillery/resources/data/doggiesnewvehicle/damage_type/vacuum_blast.json");
        var data=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
        assertEquals("never",data.get("scaling").getAsString());assertEquals(325F,VacuumPayloadProgress.BLAST_DAMAGE);
    }
    @Test void deferredTerrainMayFinishAfterVisualExpiryWithoutSpawningAnotherExpiredEffect(){
        var job=job(SiloPayloadKind.DOGEGG);var world=new World();
        for(int time=0;time<=222;time++)VacuumPayloadEngine.tick(job.store(),world,time,()->2L,0);
        assertNotNull(job.store().pendingImpact(job.id()));int visuals=world.visuals;
        for(int time=223;time<240;time++)tick(job,world,time);
        assertNull(job.store().pendingImpact(job.id()));assertEquals(visuals,world.visuals);assertEquals(1,world.bursts);
    }
    @Test void uncertainTerminalCallbackCannotArmTerrainOrReplayDamageOnRestore(){
        var job=job(SiloPayloadKind.DOGEGG);var world=new World();
        for(int time=0;time<160;time++)tick(job,world,time);
        world.burstThrows=true;assertThrows(IllegalStateException.class,()->tick(job,world,160));
        assertTrue(job.store().pendingImpact(job.id()).vacuum().burst());
        var restored=new Job(NuclearSiloStore.load(job.store().save(new net.minecraft.nbt.CompoundTag())),job.id());
        assertTrue(restored.store().writable());world.burstThrows=false;world.candidates=true;
        for(int time=161;time<=222;time++)tick(restored,world,time);
        assertEquals(1,world.bursts);assertTrue(world.blastRemoved.isEmpty());assertNull(restored.store().pendingImpact(restored.id()));
    }
    @Test void capturedEnemiesSurviveTheRevealAndReloadWithoutReplayingTheConsumedBurst(){
        var job=job(SiloPayloadKind.DOGEGG);var world=new World();world.entityUse=1;
        for(int time=0;time<=159;time++)tick(job,world,time);
        var captured=job.store().pendingImpact(job.id()).vacuum().captured();assertEquals(1,captured.size());
        var restored=new Job(NuclearSiloStore.load(job.store().save(new net.minecraft.nbt.CompoundTag())),job.id());
        assertTrue(restored.store().writable());assertEquals(captured,restored.store().pendingImpact(job.id()).vacuum().captured());
        tick(restored,world,160);assertEquals(captured,world.lastBurstCaptured);assertEquals(1,world.bursts);
        restored=new Job(NuclearSiloStore.load(restored.store().save(new net.minecraft.nbt.CompoundTag())),job.id());
        tick(restored,world,161);assertEquals(1,world.bursts);
    }
    @Test void capturedListsRejectDuplicatesOverflowLateAdditionsAndLedgerRewind(){
        var first=UUID.randomUUID();var second=UUID.randomUUID();
        var progress=VacuumPayloadProgress.EMPTY.start(0).tick(1).capture(List.of(first));
        assertEquals(progress,VacuumPayloadProgress.load(progress.save()));
        assertThrows(IllegalArgumentException.class,()->new VacuumPayloadProgress(true,1,0,false,1,-1,List.of(first,first)));
        var full=new ArrayList<UUID>();for(int i=0;i<=VacuumPayloadProgress.MAX_CAPTURED;i++)full.add(new UUID(0,i));
        assertThrows(IllegalArgumentException.class,()->progress.capture(full));
        var malformed=progress.save();var bad=new net.minecraft.nbt.ListTag();bad.add(net.minecraft.nbt.StringTag.valueOf("bad"));
        malformed.put("Captured",bad);assertThrows(IllegalArgumentException.class,()->VacuumPayloadProgress.load(malformed));
        var duplicate=progress.save();var list=duplicate.getList("Captured",net.minecraft.nbt.Tag.TAG_COMPOUND);list.add(list.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class,()->VacuumPayloadProgress.load(duplicate));
        var job=job(SiloPayloadKind.DOGEGG);tick(job,new World(),0);job.store().advanceVacuum(job.id(),progress);
        assertThrows(IllegalArgumentException.class,()->job.store().advanceVacuum(job.id(),new VacuumPayloadProgress(true,1,0,false,1)));
        var reveal=new VacuumPayloadProgress(true,120,0,false,120,-1,List.of(first));job.store().advanceVacuum(job.id(),reveal);
        assertThrows(IllegalStateException.class,()->reveal.capture(List.of(second)));
        var late=new VacuumPayloadProgress(true,120,0,false,120,-1,List.of(first,second));
        assertThrows(IllegalArgumentException.class,()->job.store().advanceVacuum(job.id(),late));
    }
    @Test void reducedTerrainOrderPreservesTheOldInnerPrefixAndLegacyProgressCannotRecallUnknownTargets()throws Exception{
        var create=VacuumTerrainScan.class.getDeclaredMethod("create",int.class);create.setAccessible(true);
        int[] oldOrder=(int[])create.invoke(null,24);
        for(int i=0;i<VacuumTerrainScan.size();i++)assertEquals(VacuumTerrainScan.offset(i),NuclearSphereScan.offset(24,oldOrder[i]));
        for(int cursor:new int[]{0,300,17077,50000,57777}){
            var old=VacuumPayloadProgress.EMPTY.start(0).tick(1).save();old.putInt("Schema",3);old.putInt("Terrain",cursor);
            var current=VacuumPayloadProgress.load(old);assertEquals(Math.min(cursor,17077),current.terrainCursor());assertTrue(current.captured().isEmpty());
        }
        var old=VacuumPayloadProgress.EMPTY.start(0).tick(1).save();old.putInt("Schema",3);old.putInt("Terrain",57778);
        assertThrows(IllegalArgumentException.class,()->VacuumPayloadProgress.load(old));
    }
    @Test void strongerPullRaisesAFallingPitTargetUnderOrdinaryGravityWhileRemainingBounded(){
        double x=20,y=-16,vx=0,vy=-.6,highest=y;
        for(int age=0;age<120;age++){
            var next=VacuumPull.velocity(-x,-y,0,vx,vy,0,age);
            assertTrue(Math.hypot(next.x(),next.y())<=1.2000001);
            vx=next.x();vy=(next.y()-.08)*.98;x+=vx;y+=vy;highest=Math.max(highest,y);
        }
        assertTrue(highest>-2,"The pull must lift the target out of a 16-block pit in the gravity port");
        assertTrue(Math.hypot(x,y)<4,"Terminal recall is separate from effective six-second pull");
        var close=VacuumPull.velocity(0,0,0,8,-8,8,60);
        assertTrue(Math.sqrt(close.x()*close.x()+close.y()*close.y()+close.z()*close.z())<=1.2000001);
    }
    public static class World implements VacuumPayloadEngine.World {
        boolean available=true,allow=true,candidates,burstThrows;int starts,bursts,pulls,peels,entityUse,visuals;
        final List<BlockPos> removed=new ArrayList<>(),blastRemoved=new ArrayList<>();
        final List<UUID> offered=java.util.stream.IntStream.range(0,512).mapToObj(i->new UUID(0,i)).toList();
        List<UUID> lastBurstCaptured=List.of();
        public boolean available(NuclearSiloStore.Impact i){return available;}
        public boolean ensureVisual(NuclearSiloStore.Impact i){visuals++;return true;}
        public boolean begin(NuclearSiloStore.Impact i){starts++;return allow;}
        public List<UUID> pull(NuclearSiloStore.Impact i,int budget){pulls++;return offered.subList(0,Math.min(budget,entityUse));}
        public boolean eligible(NuclearSiloStore.Impact i,BlockPos p){return candidates;}
        public void peel(NuclearSiloStore.Impact i,List<BlockPos> p){peels++;removed.addAll(p);}
        public boolean burst(NuclearSiloStore.Impact i){assertTrue(i.vacuum().burst());bursts++;
            lastBurstCaptured=i.vacuum().captured();
            if(burstThrows)throw new IllegalStateException("native callback");return allow;}
        public boolean blastEligible(NuclearSiloStore.Impact i,BlockPos p){return candidates;}
        public void blastTerrain(NuclearSiloStore.Impact i,List<BlockPos> p){blastRemoved.addAll(p);}
    }
}
