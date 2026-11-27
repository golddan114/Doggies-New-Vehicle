package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DnvDynamicLightFieldTest {
    @Test void onlySourceBlocksEmitAndNativePropagationOwnsTheSurroundingEnvironment(){
        var field=new DnvDynamicLightField();var engine=new Object();
        field.update(engine,List.of(new DnvDynamicLightField.Source(.1,64.4,-.1,12)),0,64,0);
        assertEquals(12,field.emission(engine,0,64,-1));
        assertEquals(0,field.emission(engine,1,64,-1));assertEquals(0,field.emission(engine,0,65,-1));
        field.update(engine,List.of(new DnvDynamicLightField.Source(.1,64.4,-.1,12),
                new DnvDynamicLightField.Source(.8,64.8,-.5,15)),0,64,0);
        assertEquals(15,field.emission(engine,0,64,-1));
    }
    @Test void movingStoppingRemovingAndReloadingCheckOldPositionsAfterWithdrawingEmission(){
        var field=new DnvDynamicLightField();var engine=new Object();
        var first=new DnvDynamicLightField.Source(0,64,0,12);var second=new DnvDynamicLightField.Source(30,64,0,15);
        var old=new DnvDynamicLightField.Block(0,64,0);var next=new DnvDynamicLightField.Block(30,64,0);
        assertEquals(List.of(old),field.update(engine,List.of(first),0,64,0));
        assertEquals(List.of(old,next),field.update(engine,List.of(second),30,64,0));
        assertEquals(0,field.emission(engine,0,64,0));assertEquals(15,field.emission(engine,30,64,0));
        assertEquals(List.of(next),field.clear(engine));assertEquals(0,field.emission(engine,30,64,0));
        assertTrue(field.clear(engine).isEmpty());
    }
    @Test void integratedServerAndOtherWorldEnginesCannotConsumeOrRetainClientSources(){
        var field=new DnvDynamicLightField();var client=new Object();var server=new Object();var next=new Object();
        var source=new DnvDynamicLightField.Source(-1,64,-1,15);
        field.update(client,List.of(source),0,64,0);
        assertEquals(15,field.emission(client,-1,64,-1));assertEquals(0,field.emission(server,-1,64,-1));
        assertTrue(field.update(next,List.of(),0,64,0).isEmpty());assertEquals(0,field.emission(client,-1,64,-1));
        assertTrue(field.clear(null).isEmpty());assertEquals(0,field.emission(null,-1,64,-1));
    }
    @Test void nearestEightSourcesAndOldNewCheckPositionsAreBounded(){
        var field=new DnvDynamicLightField();var engine=new Object();var sources=new ArrayList<DnvDynamicLightField.Source>();
        for(int i=0;i<64;i++)sources.add(new DnvDynamicLightField.Source(i*2,64,0,15));
        assertEquals(8,field.update(engine,sources,0,64,0).size());assertEquals(8,field.sourceCount());
        assertEquals(15,field.emission(engine,14,64,0));assertEquals(0,field.emission(engine,16,64,0));
        var shifted=new ArrayList<DnvDynamicLightField.Source>();
        for(int i=0;i<64;i++)shifted.add(new DnvDynamicLightField.Source(100+i*2,64,0,15));
        assertEquals(DnvDynamicLightField.MAX_REFRESH,field.update(engine,shifted,100,64,0).size());
        assertEquals(0,field.emission(engine,0,64,0));assertEquals(15,field.emission(engine,100,64,0));
    }
    @Test void invalidInputsRefuseAndUnchangedSourcesRefreshNewOrReplacedNativeCaches(){
        var field=new DnvDynamicLightField();var engine=new Object();
        var source=new DnvDynamicLightField.Source(-1.2,-64.1,0,12);
        var list=Arrays.asList(source,null,new DnvDynamicLightField.Source(Double.NaN,0,0,15),
                new DnvDynamicLightField.Source(0,0,0,16),new DnvDynamicLightField.Source(0,0,0,0),
                new DnvDynamicLightField.Source(1000,0,0,15));
        var refresh=field.update(engine,list,0,-64,0);
        assertEquals(List.of(new DnvDynamicLightField.Block(-2,-65,0)),refresh);
        assertEquals(1,field.sourceCount());assertEquals(refresh,field.update(engine,list,0,-64,0));
        assertEquals(12,field.emission(engine,-2,-65,0));
    }
}
