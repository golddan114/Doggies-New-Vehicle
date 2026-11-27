package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;

/** One dimension-wide budget; consume cursors before calling external/native world mutation hooks. */
public final class NuclearPayloadEngine {
    public enum Phase { TERRAIN, FLUID, FIRE }
    public interface World {
        boolean begin(NuclearSiloStore.Impact impact);
        default void present(NuclearSiloStore.Impact impact) { }
        default void advanced(NuclearSiloStore.Impact impact,int before,int after) { }
        boolean mayModify(NuclearSiloStore.Impact impact);
        boolean eligible(NuclearSiloStore.Impact impact,BlockPos position,Phase phase);
        void apply(NuclearSiloStore.Impact impact,List<BlockPos> positions,Phase phase);
    }
    public record Work(int starts,int visits,int candidates) { }
    private NuclearPayloadEngine(){ }
    public static Work tick(NuclearSiloStore store,World world,long time){return tick(store,world,time,System::nanoTime,2_000_000L);}
    public static Work tick(NuclearSiloStore store,World world,long time,LongSupplier clock,long nanos){
        if(!store.writable())return new Work(0,0,0);
        long startedAt=clock.getAsLong();
        var rules=NuclearPayloadRules.DEFAULT;int starts=0,visits=0,candidates=0;
        for(var impact:store.pendingImpacts())if(impact.kind()==SiloPayloadKind.NUCLEAR&&!impact.progress().started()){
            store.advanceImpact(impact.id(),impact.progress().advance(0,0,0));starts++;
            if(!world.begin(impact)){
                store.finishImpact(impact.id());
                break;
            }
            var admitted=store.pendingImpact(impact.id());
            if(world.mayModify(admitted)){
                // Reserve one bounded central slice even when the entity callbacks exhaust the soft deadline.
                var first=admitted.progress().composite()
                        ?composite(store,world,admitted,rules.visitsPerTick(),rules.blocksPerTick(),clock,startedAt,nanos,true)
                        :wave(store,world,admitted,rules.visitsPerTick(),rules.blocksPerTick(),clock,startedAt,nanos,true);
                visits=first.visits();candidates=first.candidates();
            }else store.finishImpact(impact.id());
            world.present(admitted);
            break; // Admit a blast each tick, independently of prior cleanup duration.
        }
        var jobs=store.pendingImpacts().stream().filter(i->i.kind()==SiloPayloadKind.NUCLEAR&&i.progress().started()).toList();
        if(jobs.isEmpty())return new Work(starts,visits,candidates);
        if(clock.getAsLong()-startedAt>=nanos)return new Work(starts,visits,candidates);
        int first=(int)Math.floorMod(time,(long)jobs.size());
        for(int n=0;n<jobs.size()&&visits<rules.visitsPerTick()&&candidates<rules.blocksPerTick();n++){
            var impact=jobs.get((first+n)%jobs.size());
            if(!world.mayModify(impact)){store.finishImpact(impact.id());continue;}
            var p=impact.progress();int terrain=p.terrainCursor(),fluid=p.fluidCursor(),fire=p.fireCursor();
            if(p.composite()){
                var work=composite(store,world,impact,rules.visitsPerTick()-visits,rules.blocksPerTick()-candidates,clock,startedAt,nanos,false);
                visits+=work.visits();candidates+=work.candidates();
                if(clock.getAsLong()-startedAt>=nanos)break;
                continue;
            }
            if(p.wave()){
                var work=wave(store,world,impact,rules.visitsPerTick()-visits,rules.blocksPerTick()-candidates,clock,startedAt,nanos,false);
                visits+=work.visits();candidates+=work.candidates();
                if(clock.getAsLong()-startedAt>=nanos)break;
                continue;
            }
            for(Phase phase:Phase.values()){
                int radius=phase==Phase.FLUID?p.fluidRadius():p.terrainRadius();
                int cursor=switch(phase){case TERRAIN->terrain;case FLUID->fluid;case FIRE->fire;};
                var batch=new ArrayList<BlockPos>();
                while(cursor<NuclearSphereScan.size(radius)&&visits<rules.visitsPerTick()&&candidates<rules.blocksPerTick()){
                    if(clock.getAsLong()-startedAt>=nanos)break;
                    BlockPos offset=NuclearSphereScan.offset(radius,cursor++);visits++;
                    if(NuclearSphereScan.contains(offset,radius)){
                        BlockPos position=impact.position().offset(offset);
                        if(world.eligible(impact,position,phase)){batch.add(position);candidates++;}
                    }
                }
                switch(phase){case TERRAIN->terrain=cursor;case FLUID->fluid=cursor;case FIRE->fire=cursor;}
                // Saving before callbacks prevents normal snapshot/reload from replaying consumed blocks.
                store.advanceImpact(impact.id(),p.advance(terrain,fluid,fire));
                if(!batch.isEmpty())world.apply(impact,List.copyOf(batch),phase);
                if(visits>=rules.visitsPerTick()||candidates>=rules.blocksPerTick()||clock.getAsLong()-startedAt>=nanos)break;
            }
            if(p.advance(terrain,fluid,fire).complete())store.finishImpact(impact.id());
            if(clock.getAsLong()-startedAt>=nanos)break;
        }
        return new Work(starts,visits,candidates);
    }

    /** Whole columns settle together; several columns share one native callback per phase. */
    private static Work composite(NuclearSiloStore store,World world,NuclearSiloStore.Impact impact,int visitLimit,int blockLimit,
            LongSupplier clock,long startedAt,long nanos,boolean firstSlice){
        var p=impact.progress();int radius=p.waveRadius(),cursor=p.terrainCursor(),before=cursor;
        int visits=0,candidates=0,size=NuclearCraterShape.size(radius);
        while(cursor<size){
            int limit=Math.min(128,Math.min((visitLimit-visits)/3,(blockLimit-candidates)/3));
            var positions=new ArrayList<BlockPos>();
            while(cursor<size){
                int end=NuclearCraterShape.columnEnd(radius,cursor);
                if(positions.size()+end-cursor>limit||(!firstSlice&&clock.getAsLong()-startedAt>=nanos))break;
                while(cursor<end)positions.add(impact.position().offset(NuclearCraterShape.offset(radius,cursor++)));
                if(firstSlice)break;
            }
            if(positions.isEmpty())break;
            visits+=positions.size()*3;
            store.advanceImpact(impact.id(),p.advance(cursor,0,0));
            for(Phase phase:Phase.values()){
                var batch=new ArrayList<BlockPos>();
                int phaseRadius=phase==Phase.FLUID?p.fluidRadius():p.terrainRadius();
                for(var pos:positions)if(p.distanceSquared(pos.subtract(impact.position()),phaseRadius)<=1&&world.eligible(impact,pos,phase))batch.add(pos);
                candidates+=batch.size();
                if(!batch.isEmpty())world.apply(impact,List.copyOf(batch),phase);
            }
            if(firstSlice||clock.getAsLong()-startedAt>=nanos)break;
        }
        if(cursor>before)world.advanced(impact,before,cursor);
        if(cursor==size)store.finishImpact(impact.id());
        return new Work(0,visits,candidates);
    }

    private static Work wave(NuclearSiloStore store,World world,NuclearSiloStore.Impact impact,int visitLimit,int blockLimit,
            LongSupplier clock,long startedAt,long nanos,boolean firstSlice){
        var progress=impact.progress();
        int radius=progress.waveRadius(), cursor=progress.terrainCursor(), size=NuclearSphereScan.waveSize(radius);
        int visits=0,candidates=0;
        while(cursor<size){
            // Reserve all three phases for this slice, including air that can ignite after a block is removed.
            int column=NuclearSphereScan.columnHeight(radius);
            int limit=Math.min(column-cursor%column,Math.min((visitLimit-visits)/3,(blockLimit-candidates)/3));
            if(limit==0)break;
            var positions=new ArrayList<BlockPos>(limit);
            int end=Math.min(cursor+limit,size);
            int before=cursor;
            while(cursor<end&&(firstSlice||clock.getAsLong()-startedAt<nanos)){
                var offset=NuclearSphereScan.columnOffset(radius,cursor++);
                if(progress.distanceSquared(offset,radius)<=1)positions.add(impact.position().offset(offset));
            }
            if(cursor==before)break;
            visits+=3*(cursor-before);
            store.advanceImpact(impact.id(),progress.advance(cursor,0,0));
            for(Phase phase:Phase.values()){
                var batch=new ArrayList<BlockPos>();
                int phaseRadius=phase==Phase.FLUID?progress.fluidRadius():progress.terrainRadius();
                for(var position:positions)
                    if(progress.distanceSquared(position.subtract(impact.position()),phaseRadius)<=1&&world.eligible(impact,position,phase))
                        batch.add(position);
                candidates+=batch.size();
                if(!batch.isEmpty())world.apply(impact,List.copyOf(batch),phase);
            }
            if(firstSlice||clock.getAsLong()-startedAt>=nanos)break;
        }
        if(cursor==size)store.finishImpact(impact.id());
        return new Work(0,visits,candidates);
    }
}
