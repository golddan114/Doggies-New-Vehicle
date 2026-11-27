package com.liucodex.doggiesnewvehicle.client;

import java.util.*;

/** Client tick publishes immutable light-engine inputs, never entities or server-world state. */
public final class DnvDynamicLightField {
    public static final int MAX_SOURCES=8,MAX_REFRESH=16;
    public record Source(double x,double y,double z,int level){ }
    public record Block(int x,int y,int z){ }
    private record Binding(Object engine,List<Source> sources){ }
    private volatile Binding current=new Binding(null,List.of());

    public List<Block> update(Object engine,List<Source> candidates,double x,double y,double z){
        var previous=current;
        var sources=engine==null?List.<Source>of():candidates.stream()
                .filter(s->s!=null&&Double.isFinite(s.x)&&Double.isFinite(s.y)&&Double.isFinite(s.z)
                        &&s.level>0&&s.level<=15&&square(s.x-x)+square(s.y-y)+square(s.z-z)<=64*64)
                .sorted(Comparator.comparingDouble(s->square(s.x-x)+square(s.y-y)+square(s.z-z)))
                .limit(MAX_SOURCES).map(s->new Source(Math.floor(s.x),Math.floor(s.y),Math.floor(s.z),s.level)).toList();
        // Publish before checking old/new positions so native decrease propagation sees withdrawn sources.
        current=new Binding(engine,sources);
        if(engine==null)return List.of();
        var refresh=new LinkedHashSet<Block>();
        if(previous.engine==engine)for(var source:previous.sources)refresh.add(block(source));
        for(var source:sources)refresh.add(block(source));
        // Refresh unchanged sources too: newly loaded chunks and incoming native light data can replace caches.
        return List.copyOf(refresh);
    }
    public int emission(Object engine,int x,int y,int z){
        var snapshot=current;if(engine==null||engine!=snapshot.engine)return 0;
        int light=0;
        for(var source:snapshot.sources)if(source.x==x&&source.y==y&&source.z==z)light=Math.max(light,source.level);
        return light;
    }
    public List<Block> clear(Object engine){return update(engine,List.of(),0,0,0);}
    int sourceCount(){return current.sources.size();}
    private static Block block(Source source){return new Block((int)source.x,(int)source.y,(int)source.z);}
    private static double square(double value){return value*value;}
}
