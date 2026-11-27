package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ClientEntityCycleTest {
    @Test void killAllBetweenTicksThenReuseIdsHasNoStaleIteratorOrEntries(){
        var cycle=new ClientEntityCycle<String>();
        for(int i=0;i<300;i++)cycle.put(i,"old"+i);
        var seen=new ArrayList<String>();cycle.visit(32,seen::add);assertEquals(32,seen.size());
        for(int i=0;i<300;i++)cycle.remove(i,"old"+i);
        seen.clear();cycle.visit(32,seen::add);assertTrue(seen.isEmpty());
        cycle.put(284,"new");cycle.remove(284,"old284");
        cycle.visit(32,seen::add);assertEquals(java.util.List.of("new"),seen);
    }
    @Test void boundedRoundRobinEventuallyRevisitsEveryOffscreenVehicle(){
        var cycle=new ClientEntityCycle<Integer>();for(int i=0;i<100;i++)cycle.put(i,i);
        var seen=new ArrayList<Integer>();for(int i=0;i<4;i++)cycle.visit(32,seen::add);
        assertEquals(128,seen.size());assertEquals(100,new java.util.HashSet<>(seen).size());
        assertEquals(0,seen.get(100));
    }
    @Test void removalDuringVisitAndWorldResetAreSafe(){
        var cycle=new ClientEntityCycle<Integer>();for(int i=0;i<100;i++)cycle.put(i,i);
        cycle.visit(32,id->cycle.remove(id,id));
        var seen=new ArrayList<Integer>();cycle.visit(100,seen::add);assertEquals(68,seen.size());
        cycle.clear();seen.clear();cycle.put(-2,7);cycle.visit(32,seen::add);assertEquals(java.util.List.of(7),seen);
        cycle.visit(32,id->cycle.clear());seen.clear();cycle.visit(32,seen::add);assertTrue(seen.isEmpty());
    }
}
