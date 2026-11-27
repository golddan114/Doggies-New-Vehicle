package com.liucodex.doggiesnewvehicle.artillery.silo;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class NuclearPayloadTest {
    final NuclearPayloadRules rules=NuclearPayloadRules.DEFAULT;
    final BlockPos origin=new BlockPos(-17,64,-33);
    static class World implements NuclearPayloadEngine.World {
        int starts,applies;boolean cancel,grief=true,eligible=true,fail;
        final Set<String> modified=new HashSet<>();
        @Override public boolean begin(NuclearSiloStore.Impact i){starts++;return !cancel;}
        @Override public boolean mayModify(NuclearSiloStore.Impact i){return grief;}
        @Override public boolean eligible(NuclearSiloStore.Impact i,BlockPos p,NuclearPayloadEngine.Phase phase){
            if(phase==NuclearPayloadEngine.Phase.FIRE)return false;
            int radius=phase==NuclearPayloadEngine.Phase.TERRAIN?i.progress().terrainRadius():i.progress().fluidRadius();
            assertTrue(i.progress().distanceSquared(p.subtract(i.position()),radius)<=1);return eligible;
        }
        @Override public void apply(NuclearSiloStore.Impact i,List<BlockPos> positions,NuclearPayloadEngine.Phase phase){
            applies++;
            for(BlockPos p:positions)assertTrue(modified.add(i.id()+":"+phase+":"+p),"Consumed block replayed");
            if(fail)throw new IllegalStateException("External callback failure");
        }
    }
    NuclearSiloStore queued(int count){
        var store=new NuclearSiloStore();UUID facility=UUID.randomUUID();assertTrue(store.claim(facility,origin));
        for(int n=0;n<count;n++){
            UUID id=UUID.randomUUID();assertTrue(store.reserve(new NuclearSiloStore.Flight(id,UUID.randomUUID(),facility,100,origin,false)));
            store.commit(id);assertTrue(store.impact(id,origin));
        }
        return store;
    }
    NuclearPayloadEngine.Work tick(NuclearSiloStore store,World world,long time){return NuclearPayloadEngine.tick(store,world,time,()->0,1);}
    @Test void readyCueRequiresANewMechanicalCompletionAndDoesNotReplayAfterLoad(){
        var cue=new NuclearReadyCue();assertFalse(cue.observe(false));assertFalse(cue.observe(false));
        assertTrue(cue.observe(true));assertFalse(cue.observe(true));
        assertFalse(cue.observe(false));assertTrue(cue.observe(true));
        var loaded=new NuclearReadyCue();assertFalse(loaded.observe(true));assertFalse(loaded.observe(true));
    }
    @Test void packedBlastAndCraterRadiiKeepIndependentDamageAndRadiation(){
        assertEquals(112,rules.blastRadius());assertEquals(40,rules.fluidRadius());assertEquals(40,rules.terrainRadius());
        assertEquals(48,rules.radiationRadius());assertEquals(52,rules.scentRadius());
        assertEquals(12,rules.centerKillRadius());assertEquals(.6,rules.blastFraction(),.000001);
        assertEquals(10,rules.radiationDamage());assertEquals(.02,rules.radiationFraction(),.000001);
        assertEquals(400,rules.radiationTicks());assertEquals(20,rules.radiationInterval());
        assertEquals(20,rules.radiationTicks()/rules.radiationInterval());
    }
    Set<String> damageTag(String namespace,String name)throws Exception {
        try(var reader=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of("src/artillery/resources/data/"+namespace+"/tags/damage_type/"+name+".json"))){
            var j=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            assertFalse(j.get("replace").getAsBoolean());var values=new HashSet<String>();j.getAsJsonArray("values").forEach(v->values.add(v.getAsString()));return values;
        }
    }
    @Test void blastKeepsNativeColdTagsWhileHealthBasedRadiationDoesNotUseHurtReductions()throws Exception {
        String blast="doggiesnewvehicle:nuclear_cold_blast",radiation="doggiesnewvehicle:nuclear_radiation";
        for(String tag:new String[]{"bypasses_armor","bypasses_resistance","bypasses_enchantments","bypasses_shield"}){
            assertTrue(damageTag("minecraft",tag).contains(blast));assertFalse(damageTag("minecraft",tag).contains(radiation));
        }
        assertTrue(damageTag("superbwarfare","bypasses_vehicle").contains(blast));
        assertTrue(damageTag("minecraft","bypasses_cooldown").containsAll(Set.of(blast,radiation)));
        assertTrue(damageTag("minecraft","is_freezing").containsAll(Set.of(blast,radiation)));
    }
    @Test void completeScansContainEverySpherePositionOnceIncludingNegativeCoordinates(){
        for(int radius:new int[]{8,10,24,28,36,52}){
            var all=new HashSet<BlockPos>();int in=0;
            for(int n=0;n<NuclearSphereScan.size(radius);n++){
                BlockPos p=NuclearSphereScan.offset(radius,n);assertTrue(all.add(p));
                if(NuclearSphereScan.contains(p,radius))in++;
            }
            assertEquals(NuclearSphereScan.size(radius),all.size());
            if(radius==8)assertEquals(2109,in);if(radius==10)assertEquals(4169,in);
            assertTrue(NuclearSphereScan.contains(new BlockPos(-radius,0,0),radius));
            assertFalse(NuclearSphereScan.contains(new BlockPos(radius,1,0),radius));
            assertFalse(NuclearSphereScan.contains(new BlockPos(radius+1,0,0),radius));
        }
    }
    @Test void cancellationCausesNoCleanupAndKeepsLaunchReceipt(){
        var store=queued(1);UUID id=store.pendingImpacts().get(0).id();var w=new World();w.cancel=true;
        var work=tick(store,w,0);assertEquals(1,work.starts());assertEquals(0,work.visits());
        assertEquals(0,w.applies);assertTrue(store.pendingImpacts().isEmpty());assertTrue(store.committed(id));
        assertFalse(store.impact(id,origin));
    }
    @Test void mobGriefingOffPreservesTheBlastButDoesNotTouchBlocks(){
        var store=queued(1);var w=new World();w.grief=false;tick(store,w,0);
        assertEquals(1,w.starts);assertEquals(0,w.applies);assertTrue(store.pendingImpacts().isEmpty());
    }
    @Test void eachTickHasOneSharedBudgetAcrossAllSixtyFourJobs(){
        var store=queued(64);var w=new World();
        for(var i:store.pendingImpacts())store.advanceImpact(i.id(),new NuclearPayloadProgress(true,0,0));
        for(int t=0;t<64;t++){
            var work=tick(store,w,t);assertTrue(work.visits()<=rules.visitsPerTick());assertTrue(work.candidates()<=rules.blocksPerTick());assertEquals(0,work.starts());
        }
        assertEquals(0,w.starts);assertTrue(store.pendingImpacts().stream().allMatch(i->i.progress().terrainCursor()>0));
    }
    @Test void newlyLandedBlastStartsWithoutWaitingForEarlierCleanup(){
        var store=queued(2);var w=new World();assertEquals(1,tick(store,w,0).starts());
        assertEquals(1,tick(store,w,1).starts());assertEquals(2,w.starts);assertEquals(2,store.pendingImpacts().size());
    }
    @Test void emptyOrUnloadedBlocksStillConsumeScanVisitsButNoMutationBudget(){
        var store=queued(1);var w=new World();w.eligible=false;
        var work=tick(store,w,0);assertTrue(work.visits()>8000&&work.visits()<=rules.visitsPerTick());assertEquals(0,work.candidates());assertEquals(0,w.applies);
        for(int t=1;t<200&&!store.pendingImpacts().isEmpty();t++)tick(store,w,t);
        assertTrue(store.pendingImpacts().isEmpty());assertEquals(1,w.starts);
    }
    @Test void reloadResumesBothCursorsWithoutReplayingBlastOrVisitedBlocks(){
        var store=queued(1);var w=new World();tick(store,w,0);
        int cursor=store.pendingImpacts().get(0).progress().terrainCursor();assertTrue(cursor>0);
        store=NuclearSiloStore.load(store.save(new CompoundTag()));assertTrue(store.writable());
        assertEquals(cursor,store.pendingImpacts().get(0).progress().terrainCursor());
        for(int t=1;t<1000&&!store.pendingImpacts().isEmpty();t++)tick(store,w,t);
        assertTrue(store.pendingImpacts().isEmpty());assertEquals(1,w.starts);
        int shape=0;
        for(int y=-16;y<=20;y++)for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++){
            int vertical=y<0?8:20;
            if(x*x+y*y+z*z<=256||(x*x+z*z)*vertical*vertical+y*y*1600<=1600*vertical*vertical)shape++;
        }
        assertEquals(2*shape,w.modified.size()); // Terrain and fluid see every cell once after reload.
    }
    @Test void aFailingMutationCallbackDoesNotRewindItsConsumedBatch(){
        var store=queued(1);var w=new World();w.fail=true;assertThrows(IllegalStateException.class,()->tick(store,w,0));
        assertTrue(store.pendingImpacts().get(0).progress().terrainCursor()>0);
        var restored=NuclearSiloStore.load(store.save(new CompoundTag()));w.fail=false;
        for(int t=1;t<1000&&!restored.pendingImpacts().isEmpty();t++)tick(restored,w,t);
        assertTrue(restored.pendingImpacts().isEmpty());assertEquals(1,w.starts);
    }
    @Test void unknownProgressIsPreservedReadOnlyRatherThanRestarted(){
        var tag=queued(1).save(new CompoundTag());var impact=tag.getList("Impacts",Tag.TAG_COMPOUND).getCompound(0);
        impact.getCompound("Payload").putInt("Schema",99);
        var broken=NuclearSiloStore.load(tag);assertFalse(broken.writable());assertEquals(tag,broken.save(new CompoundTag()));
        var w=new World();assertEquals(0,tick(broken,w,0).starts());assertEquals(0,w.applies);
    }
    @Test void previousThreeFieldImpactMigratesAsUnstarted(){
        var tag=queued(1).save(new CompoundTag());tag.getList("Impacts",Tag.TAG_COMPOUND).getCompound(0).remove("Payload");
        var store=NuclearSiloStore.load(tag);assertTrue(store.writable());assertEquals(NuclearPayloadProgress.EMPTY,store.pendingImpacts().get(0).progress());
        var w=new World();tick(store,w,0);tick(store,w,1);assertEquals(1,w.starts);
    }
    @Test void budgetDeadlineStopsEvenWhenEveryBlockIsEmpty(){
        var store=queued(1);var w=new World();w.eligible=false;var clock=new AtomicLong();
        var work=NuclearPayloadEngine.tick(store,w,0,()->clock.addAndGet(10),35);
        assertTrue(work.visits()>=111&&work.visits()<=3*(37+128));assertTrue(clock.get()>=35);
        assertEquals(0,w.applies);assertFalse(store.pendingImpacts().isEmpty());
    }
    @Test void theCenterBreaksDrainsAndIgnitesBeforeTheCueEvenAfterASlowEntityCallback(){
        var store=queued(1);var clock=new AtomicLong();
        var sequence=new java.util.ArrayList<String>();
        var w=new World(){
            @Override public boolean begin(NuclearSiloStore.Impact impact){
                clock.addAndGet(100);sequence.add("blast");return super.begin(impact);
            }
            @Override public boolean eligible(NuclearSiloStore.Impact i,BlockPos pos,NuclearPayloadEngine.Phase phase){
                if(!pos.equals(origin))return false;
                return phase!=NuclearPayloadEngine.Phase.FIRE||sequence.equals(List.of("blast","TERRAIN","FLUID"));
            }
            @Override public void apply(NuclearSiloStore.Impact i,List<BlockPos> positions,NuclearPayloadEngine.Phase phase){
                assertEquals(List.of(origin),positions);sequence.add(phase.name());super.apply(i,positions,phase);
            }
            @Override public void present(NuclearSiloStore.Impact impact){sequence.add("cue");}
        };
        var work=NuclearPayloadEngine.tick(store,w,0,clock::get,50);
        assertEquals(1,work.starts());assertEquals(111,work.visits());assertEquals(3,w.applies);
        assertEquals(List.of("blast","TERRAIN","FLUID","FIRE","cue"),sequence);
        assertTrue(store.pendingImpacts().get(0).progress().started());
        var restored=NuclearSiloStore.load(store.save(new CompoundTag()));
        work=NuclearPayloadEngine.tick(restored,w,1,clock::get,50);
        assertEquals(0,work.starts());assertEquals(1,w.starts);assertTrue(work.visits()>0);
        assertEquals(List.of("blast","TERRAIN","FLUID","FIRE","cue"),sequence);
    }
    @Test void progressCannotRewindOrAcceptOutOfBoundsCursors(){
        var store=queued(1);UUID id=store.pendingImpacts().get(0).id();store.advanceImpact(id,new NuclearPayloadProgress(true,5,0));
        assertThrows(IllegalArgumentException.class,()->store.advanceImpact(id,new NuclearPayloadProgress(true,4,0)));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadProgress(false,1,0));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadProgress(true,NuclearSphereScan.size(40)+1,0));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadProgress(true,0,NuclearSphereScan.size(40)+1));
    }
    @Test void oldStartedCursorKeepsItsOriginalCubeAndNewJobsUseTheExpandedSphere(){
        var old=new CompoundTag();old.putInt("Schema",1);old.putBoolean("Started",true);
        old.putInt("Terrain",1000);old.putInt("Fluid",700);
        var progress=NuclearPayloadProgress.load(old);
        assertEquals(8,progress.terrainRadius());assertEquals(10,progress.fluidRadius());
        assertEquals(NuclearSphereScan.offset(8,1000),NuclearSphereScan.offset(progress.terrainRadius(),progress.terrainCursor()));
        assertEquals(progress,NuclearPayloadProgress.load(progress.save()));
        assertEquals(40,NuclearPayloadProgress.EMPTY.terrainRadius());assertEquals(40,NuclearPayloadProgress.EMPTY.fluidRadius());
    }
    @Test void completedPayloadReleasesTheCombinedFlightQueueBudget(){
        var store=queued(64);var w=new World();w.cancel=true;UUID silo=store.facility();
        assertFalse(store.reserve(new NuclearSiloStore.Flight(UUID.randomUUID(),UUID.randomUUID(),silo,100,origin,false)));
        tick(store,w,0);assertEquals(63,store.pendingImpacts().size());
        assertTrue(store.reserve(new NuclearSiloStore.Flight(UUID.randomUUID(),UUID.randomUUID(),silo,100,origin,false)));
    }
    @Test void expandedRadiusRemainsBoundedAndInvalidProgressDoesNotEnterTheQueue(){
        assertEquals(1157625,NuclearSphereScan.size(52));
        assertThrows(IllegalArgumentException.class,()->NuclearSphereScan.size(53));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadProgress(true,0,0,0,53,28));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadRules(113,28,28,48,52,12,.6F,10,.02F,400,20,4096,256));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadRules(84,53,28,48,52,12,.6F,10,.02F,400,20,4096,256));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadRules(84,28,53,48,52,12,.6F,10,.02F,400,20,4096,256));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadRules(84,28,28,113,52,12,.6F,10,.02F,400,20,4096,256));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadRules(84,28,28,48,113,12,.6F,10,.02F,400,20,4096,256));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadRules(112,36,36,48,52,12,.6F,10,.02F,400,20,2,256));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadRules(112,36,36,48,52,12,.6F,10,.02F,400,20,4096,2));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadRules(112,40,40,48,52,12,.6F,10,.02F,400,20,110,1024));
        assertThrows(IllegalArgumentException.class,()->new NuclearPayloadRules(112,40,40,48,52,12,.6F,10,.02F,400,20,8192,110));
        assertDoesNotThrow(()->new NuclearPayloadRules(112,40,40,48,52,12,.6F,10,.02F,400,20,111,111));
        assertEquals(8192,rules.visitsPerTick());assertEquals(1024,rules.blocksPerTick());
    }
    @Test void previousSchemaTwoStartedJobKeepsItsCubeAndDoesNotReplayTheBlastAfterReload(){
        var old=queued(1).save(new CompoundTag());
        old.getList("Impacts",Tag.TAG_COMPOUND).getCompound(0).put("Payload",new NuclearPayloadProgress(true,1000,700,300,24,24).save());
        var restored=NuclearSiloStore.load(old);assertTrue(restored.writable());
        var before=restored.pendingImpacts().get(0).progress();
        assertEquals(24,before.terrainRadius());assertEquals(24,before.fluidRadius());assertEquals(300,before.fireCursor());
        var w=new World();w.eligible=false;var work=tick(restored,w,1);
        assertEquals(0,work.starts());assertEquals(0,w.starts);
        var after=restored.pendingImpacts().get(0).progress();assertEquals(24,after.terrainRadius());
        assertEquals(1000+rules.visitsPerTick(),after.terrainCursor());assertEquals(700,after.fluidCursor());assertEquals(300,after.fireCursor());
    }
    @Test void previousSchemaTwoUnstartedJobUsesCurrentRadiiButCorruptCursorsAreRejected(){
        var old=new NuclearPayloadProgress(false,0,0,0,24,24).save();
        assertEquals(NuclearPayloadProgress.EMPTY,NuclearPayloadProgress.load(old));
        assertEquals(40,NuclearPayloadProgress.load(old).terrainRadius());
        old.putInt("Terrain",1);assertThrows(IllegalArgumentException.class,()->NuclearPayloadProgress.load(old));
    }

    @Test void compositePlanIsUniqueRadialAndMatchesIndependentSphereUnionEllipsoid(){
        var expected=new HashSet<BlockPos>();
        for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)for(int y=-20;y<=20;y++){
            int v=y<0?8:20;
            if(x*x+y*y+z*z<=256||(x*x+z*z)*v*v+y*y*1600<=1600*v*v)expected.add(new BlockPos(x,y,z));
        }
        var actual=new HashSet<BlockPos>();int last=-1;
        for(int n=0;n<NuclearCraterShape.size(40);n++){
            var p=NuclearCraterShape.offset(40,n);assertTrue(actual.add(p));
            int horizontal=p.getX()*p.getX()+p.getZ()*p.getZ();assertTrue(horizontal>=last);last=horizontal;
        }
        assertEquals(expected,actual);assertTrue(actual.contains(new BlockPos(0,-16,0)));
        assertFalse(actual.contains(new BlockPos(0,-17,0)));assertTrue(actual.contains(new BlockPos(40,0,0)));
        assertFalse(actual.contains(new BlockPos(30,-8,0)));
        assertTrue(actual.size()<NuclearSphereScan.waveSize(36)); // No visits to the legacy bounding cylinder's empty corners.
    }

    @Test void startedSchemaThreeKeepsItsShapeAndCursorWhileNewSchemaFourSurvivesReload(){
        var legacy=new NuclearPayloadProgress(true,93,0,0,36,36,true);
        var loaded=NuclearPayloadProgress.load(legacy.save());assertEquals(legacy,loaded);assertFalse(loaded.composite());
        assertTrue(loaded.distanceSquared(new BlockPos(0,-10,0),36)<=1);
        assertTrue(loaded.distanceSquared(new BlockPos(0,-11,0),36)>1);
        var current=NuclearPayloadProgress.EMPTY.advance(NuclearCraterShape.columnEnd(40,0),0,0);
        assertEquals(4,current.save().getInt("Schema"));assertEquals(current,NuclearPayloadProgress.load(current.save()));
    }

    @Test void eachBatchFinishesItsFireBeforeBreakingTheNextAndProgressFollowsCommit(){
        var store=queued(1);var sequence=new java.util.ArrayList<String>();var batches=new java.util.ArrayList<Integer>();
        var w=new World(){
            @Override public boolean eligible(NuclearSiloStore.Impact i,BlockPos pos,NuclearPayloadEngine.Phase phase){return true;}
            @Override public void apply(NuclearSiloStore.Impact i,List<BlockPos> positions,NuclearPayloadEngine.Phase phase){
                if(phase==NuclearPayloadEngine.Phase.TERRAIN){
                    assertTrue(sequence.isEmpty()||sequence.get(sequence.size()-1).equals("FIRE"));batches.add(positions.size());
                }
                sequence.add(phase.name());super.apply(i,positions,phase);
            }
            @Override public void advanced(NuclearSiloStore.Impact i,int before,int after){
                assertEquals("FIRE",sequence.get(sequence.size()-1));assertTrue(after>before);
                assertEquals(after,store.pendingImpact(i.id()).progress().terrainCursor());
            }
        };
        var work=tick(store,w,0);
        assertTrue(work.candidates()<=1024);assertEquals(work.candidates(),work.visits());
        assertTrue(batches.stream().allMatch(n->n<=128));assertTrue(batches.stream().anyMatch(n->n>37));
        assertEquals(3*batches.size(),sequence.size());
    }
}
