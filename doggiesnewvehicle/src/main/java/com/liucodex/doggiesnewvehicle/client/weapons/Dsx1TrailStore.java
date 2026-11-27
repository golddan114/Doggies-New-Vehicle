package com.liucodex.doggiesnewvehicle.client.weapons;

import com.liucodex.doggiesnewvehicle.network.Dsx1TrailNetwork.Segment;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Client-only world-space history, bounded by both age and count; owns no entity references. */
public final class Dsx1TrailStore {
    public static final int MAX_TRAILS=64,MAX_SEGMENTS=128,LIFE_TICKS=24;
    private final LinkedHashMap<UUID,Trail> trails=new LinkedHashMap<>();
    private long clock=Long.MIN_VALUE;
    public void accept(Segment sample,long tick){
        prune(tick);
        if(sample.tick()>tick+5||sample.tick()<tick-LIFE_TICKS)return;
        var trail=trails.get(sample.trail());
        if(trail==null){
            if(trails.size()>=MAX_TRAILS)trails.remove(trails.keySet().iterator().next());
            trail=new Trail();trails.put(sample.trail(),trail);
        }
        if(sample.sequence()<=trail.sequence)return;
        trail.sequence=sample.sequence();
        if(trail.segments.size()>=MAX_SEGMENTS)trail.segments.removeFirst();
        trail.segments.addLast(sample);
    }
    public void prune(long tick){
        if(tick<clock)clear();clock=tick;
        for(var trail:trails.values())while(!trail.segments.isEmpty()&&trail.segments.peekFirst().tick()<tick-LIFE_TICKS)trail.segments.removeFirst();
        trails.values().removeIf(t->t.segments.isEmpty());
    }
    public Iterable<Trail> trails(){return trails.values();}
    public int size(){return trails.size();}
    public void clear(){trails.clear();clock=Long.MIN_VALUE;}
    public static float alpha(double age){float f=(float)Math.max(0,Math.min(1,1-age/LIFE_TICKS));return f*f;}
    public static final class Trail {
        private int sequence=-1;
        private final ArrayDeque<Segment> segments=new ArrayDeque<>();
        public Iterable<Segment> segments(){return segments;}
        public int size(){return segments.size();}
    }
}
