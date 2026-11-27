package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;

/** Advances vacuum impacts in the existing store, with one shared terrain/entity budget per dimension. */
public final class VacuumPayloadEngine {
    public static final int VISITS_PER_TICK = 2048, BLOCKS_PER_TICK = 512, ENTITIES_PER_TICK = 512;
    public interface World {
        boolean available(NuclearSiloStore.Impact impact);
        boolean ensureVisual(NuclearSiloStore.Impact impact);
        boolean begin(NuclearSiloStore.Impact impact);
        List<UUID> pull(NuclearSiloStore.Impact impact, int budget);
        boolean eligible(NuclearSiloStore.Impact impact, BlockPos position);
        void peel(NuclearSiloStore.Impact impact, List<BlockPos> positions);
        boolean burst(NuclearSiloStore.Impact impact);
        boolean blastEligible(NuclearSiloStore.Impact impact,BlockPos position);
        void blastTerrain(NuclearSiloStore.Impact impact,List<BlockPos> positions);
    }
    public record Work(int starts, int bursts, int visits, int blocks, int entities) { }
    private VacuumPayloadEngine() { }

    public static Work tick(NuclearSiloStore store, World world, long time) {
        return tick(store, world, time, System::nanoTime, 2_000_000L);
    }

    public static Work tick(NuclearSiloStore store, World world, long time, LongSupplier clock, long nanos) {
        if (!store.writable()) return new Work(0,0,0,0,0);
        long startedAt = clock.getAsLong();
        int starts=0, bursts=0, visits=0, blocks=0, entities=0;
        var jobs=store.pendingImpacts().stream().filter(i->i.kind()==SiloPayloadKind.DOGEGG).toList();
        if (jobs.isEmpty()) return new Work(0,0,0,0,0);
        int first=(int)Math.floorMod(time,(long)jobs.size());
        for(int n=0;n<jobs.size();n++) {
            var impact=jobs.get((first+n)%jobs.size());
            if(!world.available(impact)||impact.vacuum().age()<VacuumPayloadProgress.TOTAL_TICKS&&!world.ensureVisual(impact)) continue;
            var before=impact.vacuum();
            if(!before.started()) {
                if(starts>0) continue;
                store.advanceVacuum(impact.id(),before.start(time)); starts++;
                if(!world.begin(impact)) store.finishImpact(impact.id());
                continue;
            }
            if(time<=before.lastTick()) continue;
            var progress=before.tick(time);
            store.advanceVacuum(impact.id(),progress);
            impact=store.pendingImpact(impact.id());
            if(progress.age()>=VacuumPayloadProgress.BURST_TICK&&!progress.burst()) {
                // Consume before callbacks, as in the existing nuclear payload engine.
                progress=progress.consumeBurst(); store.advanceVacuum(impact.id(),progress);
                if(world.burst(store.pendingImpact(impact.id()))) {
                    // -1 is unadmitted. Cancellation or an exception cannot arm deferred terrain after restart.
                    progress=progress.blastTerrain(0);store.advanceVacuum(impact.id(),progress);
                }
                bursts++;
            }
            if(progress.burst() && progress.blastCursor()>=0 && progress.blastCursor()<VacuumTerrainScan.blastSize()) {
                var batch=new ArrayList<BlockPos>();int cursor=progress.blastCursor();
                BlockPos anchor=impact.position().above(2);
                while(cursor<VacuumTerrainScan.blastSize()&&visits<VISITS_PER_TICK&&blocks<BLOCKS_PER_TICK
                        &&clock.getAsLong()-startedAt<nanos) {
                    BlockPos pos=anchor.offset(VacuumTerrainScan.blastOffset(cursor++));visits++;
                    if(world.blastEligible(impact,pos)){batch.add(pos);blocks++;}
                }
                if(cursor!=progress.blastCursor()) {
                    progress=progress.blastTerrain(cursor);store.advanceVacuum(impact.id(),progress);
                }
                if(!batch.isEmpty())world.blastTerrain(store.pendingImpact(impact.id()),List.copyOf(batch));
            }
            if(progress.age()>=VacuumPayloadProgress.TOTAL_TICKS
                    &&(progress.blastCursor()<0||progress.blastCursor()>=VacuumTerrainScan.blastSize())) {
                store.finishImpact(impact.id());continue;
            }
            if(progress.age()>=VacuumPayloadProgress.PULL_TICKS) continue;
            if(entities<ENTITIES_PER_TICK) {
                var pulled=world.pull(impact,ENTITIES_PER_TICK-entities);
                if(pulled.size()>ENTITIES_PER_TICK-entities||new java.util.HashSet<>(pulled).size()!=pulled.size())
                    throw new IllegalStateException("Vacuum entity budget or duplicate capture");
                entities+=pulled.size();var captured=progress.capture(pulled);
                if(captured!=progress){progress=captured;store.advanceVacuum(impact.id(),progress);impact=store.pendingImpact(impact.id());}
            }
            var batch=new ArrayList<BlockPos>(); int cursor=progress.terrainCursor();
            while(cursor<VacuumTerrainScan.size()
                    &&visits<VISITS_PER_TICK&&blocks<BLOCKS_PER_TICK&&clock.getAsLong()-startedAt<nanos) {
                var offset=VacuumTerrainScan.offset(cursor++); visits++;
                if(NuclearSphereScan.contains(offset,VacuumPayloadProgress.TERRAIN_RADIUS)) {
                    var position=impact.position().offset(offset);
                    if(world.eligible(impact,position)){batch.add(position);blocks++;}
                }
            }
            if(cursor!=progress.terrainCursor()) store.advanceVacuum(impact.id(),progress.terrain(cursor));
            if(!batch.isEmpty()) world.peel(impact,List.copyOf(batch));
        }
        return new Work(starts,bursts,visits,blocks,entities);
    }
}
