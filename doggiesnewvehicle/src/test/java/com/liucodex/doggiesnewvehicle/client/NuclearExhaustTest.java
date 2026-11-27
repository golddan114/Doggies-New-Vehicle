package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NuclearExhaustTest {
    @Test void actualJetsAreWarmTranslucentTaperedAndFollowTheMissileTailAxis(){
        for(boolean central:new boolean[]{true,false})for(float age:new float[]{0,20,40})for(int detail:new int[]{1,2}){
            var vertices=new java.util.ArrayList<double[]>();
            NuclearExhaust.render((x,y,z,r,g,b,a)->vertices.add(new double[]{x,y,z,r,g,b,a}),central,age,detail);
            assertEquals(detail==2?192:48,vertices.size());
            double end=vertices.stream().mapToDouble(v->v[1]).min().orElseThrow();
            assertEquals(-NuclearExhaust.length(central,age)*(1+Math.sin(age*.63)*.035),end,1e-6);
            for(var v:vertices){
                assertTrue(v[1]<=0);assertTrue(v[3]>=v[4]&&v[4]>=v[5]);assertTrue(v[6]>=0&&v[6]<1);
                if(Math.abs(v[1]-end)<1e-7){assertEquals(0,v[0],1e-8);assertEquals(0,v[2],1e-8);assertEquals(0,v[6]);}
            }
            assertTrue(vertices.stream().anyMatch(v->v[6]>.8));
        }
    }
    @Test void ignitionAndBoostKeepFiveVisibleNozzlesWithoutVelocityPackets(){
        assertEquals(4,NuclearExhaust.length(true,0));assertEquals(2,NuclearExhaust.length(false,0));
        assertEquals(12,NuclearExhaust.length(true,40));assertEquals(6,NuclearExhaust.length(false,40));
        float previous=0;
        for(float age=0;age<200;age+=.25F){
            float length=NuclearExhaust.length(true,age);assertTrue(length>=previous&&length<=12);previous=length;
            assertTrue(NuclearExhaust.radius(true,age)>.35&&NuclearExhaust.radius(false,age)>.22);
            assertTrue(NuclearExhaust.length(false,age)<length);
        }
    }
}
