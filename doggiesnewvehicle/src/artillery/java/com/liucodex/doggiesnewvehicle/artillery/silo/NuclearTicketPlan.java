package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.Set;
import java.util.function.LongConsumer;

/** Release before acquire: rolling a full three-chunk window never briefly holds six tickets. */
public final class NuclearTicketPlan {
    private NuclearTicketPlan(){ }
    public static void apply(Set<Long> before,Set<Long> desired,LongConsumer remove,LongConsumer add){
        apply(before,desired,remove,add,checkpoint->{ });
    }
    public static void apply(Set<Long> before,Set<Long> desired,LongConsumer remove,LongConsumer add,java.util.function.Consumer<Set<Long>> checkpoint){
        if(before.size()>3||desired.size()>3)throw new IllegalArgumentException("Nuclear chunk budget");
        var current=new java.util.LinkedHashSet<>(before);
        for(long chunk:before)if(!desired.contains(chunk)){remove.accept(chunk);current.remove(chunk);checkpoint.accept(Set.copyOf(current));}
        // Persist acquisition intent first: an external callback can throw after actually acquiring the ticket.
        for(long chunk:desired)if(!before.contains(chunk)){current.add(chunk);checkpoint.accept(Set.copyOf(current));add.accept(chunk);}
    }
}
