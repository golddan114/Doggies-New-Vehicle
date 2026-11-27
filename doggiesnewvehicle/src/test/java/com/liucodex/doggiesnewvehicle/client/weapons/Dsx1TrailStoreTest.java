package com.liucodex.doggiesnewvehicle.client.weapons;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.network.Dsx1TrailNetwork;
import com.liucodex.doggiesnewvehicle.network.Dsx1TrailNetwork.Segment;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class Dsx1TrailStoreTest {
    private static final ResourceLocation DIM=ResourceLocation.fromNamespaceAndPath("minecraft","overworld");
    private static Segment segment(UUID id,int sequence,long tick){return new Segment(DIM,id,sequence,tick,new Vec3(sequence,70,0),new Vec3(sequence+1,70,0));}
    @Test void worldPointsStayFrozenDuplicateAndOutOfOrderPacketsAreRejectedAndTracksExpire(){
        var store=new Dsx1TrailStore();var id=UUID.randomUUID();var first=segment(id,0,100);store.accept(first,100);
        store.accept(first,100);store.accept(segment(id,2,101),101);store.accept(segment(id,1,101),101);
        var trail=store.trails().iterator().next();assertEquals(2,trail.size());
        assertEquals(new Vec3(0,70,0),trail.segments().iterator().next().from());
        store.prune(124);assertEquals(1,store.size());store.prune(126);assertEquals(0,store.size());
        store.accept(segment(id,3,100),126);assertEquals(0,store.size(),"A delayed old packet cannot recreate an expired trail");
    }
    @Test void countAndTimeBoundsSurviveBurstsAndClockRollback(){
        var store=new Dsx1TrailStore();
        for(int i=0;i<200;i++)store.accept(segment(new UUID(0,i),0,200),200);
        assertEquals(64,store.size());store.prune(10);assertEquals(0,store.size());
        store.accept(segment(UUID.randomUUID(),0,100),10);assertEquals(0,store.size());
        assertEquals(1,Dsx1TrailStore.alpha(0));assertEquals(.25F,Dsx1TrailStore.alpha(12));assertEquals(0,Dsx1TrailStore.alpha(24));
        store.clear();assertEquals(0,store.size());
    }
    @Test void boundedWireCodecRoundTripsAndRejectsInvalidCoordinatesSequenceAndLengths(){
        var original=segment(UUID.randomUUID(),12,100);var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{Dsx1TrailNetwork.encode(original,buffer);assertTrue(buffer.readableBytes()<150);assertEquals(original,Dsx1TrailNetwork.decode(buffer));assertEquals(0,buffer.readableBytes());}
        finally{buffer.release();}
        assertThrows(IllegalArgumentException.class,()->segment(UUID.randomUUID(),128,1));
        assertThrows(IllegalArgumentException.class,()->new Segment(DIM,UUID.randomUUID(),0,1,new Vec3(Double.NaN,0,0),Vec3.ZERO));
        assertFalse(Dsx1TrailNetwork.validSegment(Vec3.ZERO,new Vec3(513,0,0)));
    }
}
