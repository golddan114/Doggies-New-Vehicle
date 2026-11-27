package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Executes the production native-slot reconciliation, without replacing the repair decision. */
class BlizzardRepairExecutionTest {
    private static Port tank() throws Exception {
        var names=new java.util.HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",World.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Port.class);
        return (Port)NuclearRecoveryExecutionTest.extract(
                "com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity",Set.of("handlePartHealth"),names,Port.class,false)
                .getConstructor().newInstance();
    }
    @Test void completedHullRepairAndRestoredFullHullSnapshotsClearEveryNativeDamageAndBurnSlot()throws Exception{
        var tank=tank();tank.handlePartHealth();
        String dependency=System.getProperty("dnv.sbw.jar");assertNotNull(dependency);
        boolean neo=java.nio.file.Path.of(dependency).getFileName().toString().contains("mc1.21.1");
        assertArrayEquals(neo?new float[]{50,75,75,95,95}:new float[]{50,70,80,90,100},tank.parts);
        assertArrayEquals(new boolean[5],tank.damaged);
        assertFalse(tank.burned);assertEquals(0,tank.timer);assertEquals(1500,tank.health);assertEquals(1,tank.nativeCalls);
        tank.health=800;Arrays.fill(tank.parts,0);Arrays.fill(tank.damaged,true);tank.burned=true;tank.timer=200;
        tank.handlePartHealth();assertTrue(tank.damaged[0]);
        tank.health=1500;tank.handlePartHealth();assertFalse(tank.damaged[0]);assertEquals(50,tank.parts[0]);assertFalse(tank.burned);
    }
    @Test void partialClientWreckRemovedAndMalformedStatesOnlyDelegateTheOrdinaryNativePartTick()throws Exception{
        for(int mode=0;mode<6;mode++){
            var tank=tank();
            switch(mode){case 0 -> tank.health=1499;case 1 -> tank.world.isClientSide=true;case 2 -> tank.wreck=true;
                case 3 -> tank.removed=true;case 4 -> tank.health=Float.NaN;case 5 -> tank.maximum=Float.NaN;default -> throw new AssertionError();}
            tank.handlePartHealth();assertArrayEquals(new float[5],tank.parts);assertTrue(tank.damaged[0]);
            assertTrue(tank.burned);assertEquals(400,tank.timer);assertEquals(1,tank.nativeCalls);
        }
    }
    public static class World {public boolean isClientSide;}
    public static class Port {
        public final World world=new World();public float health=1500,maximum=1500;
        public final float[] parts=new float[5];public final boolean[] damaged={true,true,true,true,true};
        public boolean wreck,removed,burned=true;public int timer=400,nativeCalls;
        public World level(){return world;}public boolean isWreck(){return wreck;}public boolean isRemoved(){return removed;}
        public float getHealth(){return health;}public float getMaxHealth(){return maximum;}
        public float getTurretMaxHealth(){return 50;}public float getLeftWheelMaxHealth(){return 70;}public float getRightWheelMaxHealth(){return 80;}
        public float getMainEngineMaxHealth(){return 90;}public float getSubEngineMaxHealth(){return 100;}
        public float getWheelMaxHealth(){return 75;}public float getEngineMaxHealth(){return 95;}
        public void setTurretHealth(float value){parts[0]=value;}public void setLeftWheelHealth(float value){parts[1]=value;}
        public void setRightWheelHealth(float value){parts[2]=value;}public void setMainEngineHealth(float value){parts[3]=value;}
        public void setSubEngineHealth(float value){parts[4]=value;}
        public void setTurretDamaged(boolean value){damaged[0]=value;}public void setLeftWheelDamaged(boolean value){damaged[1]=value;}
        public void setRightWheelDamaged(boolean value){damaged[2]=value;}public void setMainEngineDamaged(boolean value){damaged[3]=value;}
        public void setSubEngineDamaged(boolean value){damaged[4]=value;}
        public void setTurretBurned(boolean value){burned=value;}public void setTurretBurnTimer(int value){timer=value;}
        public void handlePartHealth(){nativeCalls++;}
    }
}
