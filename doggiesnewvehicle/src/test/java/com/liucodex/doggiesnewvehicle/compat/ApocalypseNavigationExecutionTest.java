package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.ClassNode;

/** Runs production navigation and shared local driver; terrain and native input setters are ports. */
class ApocalypseNavigationExecutionTest {
    @Test void friendlyLineBlockLeavesFireHoldForSideRepositionThenReturnsToHold() throws Exception {
        var f=new Fixture();Vec3 target=new Vec3(0,0,40);
        f.move(target,true);assertTrue(f.tank.brake);assertFalse(f.tank.forward||f.tank.right);
        for(int i=0;i<8;i++)f.type.getMethod("move",Vec3.class,boolean.class,boolean.class).invoke(f.controller,target,true,true);
        assertTrue(f.tank.right);assertFalse(f.tank.brake);
        f.move(target,true);assertTrue(f.tank.brake);assertFalse(f.tank.forward||f.tank.right||f.tank.left);
    }
    @Test void straightRetreatHoldsTheNativeReverseKeyInsteadOfDecayingPowerBetweenPulses() throws Exception {
        var f=new Fixture();
        for(int i=0;i<40;i++) {
            f.move(new Vec3(0,0,10),true);assertTrue(f.tank.back);assertFalse(f.tank.brake);
            f.tank.pos=f.tank.pos.add(0,0,-.15);
        }
    }
    @Test void heightDrivenDistanceRetreatsWhereTheOldFixedBandWouldStop() throws Exception {
        var f=new Fixture();Vec3 target=new Vec3(0,45,35);
        f.move(target,true);assertFalse(f.tank.forward||f.tank.back);
        for(int i=0;i<2;i++)f.type.getMethod("move",Vec3.class,boolean.class,boolean.class,double.class)
                .invoke(f.controller,target,true,false,106d);
        assertTrue(f.tank.back);assertFalse(f.tank.forward);
        f.type.getMethod("move",Vec3.class,boolean.class,boolean.class,double.class)
                .invoke(f.controller,new Vec3(0,45,110),true,false,106d);
        assertTrue(f.tank.brake);assertFalse(f.tank.back);
    }
    @Test void forwardRetreatAndReverseSteeringUseNativeBooleanInputs() throws Exception {
        var f=new Fixture();f.move(new Vec3(0,0,100),true);assertTrue(f.tank.forward);assertFalse(f.tank.back);
        var retreat=new Fixture();retreat.move(new Vec3(0,0,10),true);retreat.move(new Vec3(0,0,10),true);
        assertTrue(retreat.tank.back);assertFalse(retreat.tank.forward);
        var turn=new Fixture();turn.move(new Vec3(-100,0,0),true);assertTrue(turn.tank.right);assertFalse(turn.tank.left);
        turn.tank.power=-.5F;turn.move(new Vec3(-100,0,0),true);assertTrue(turn.tank.right);assertFalse(turn.tank.left);
    }
    @Test void residualReversePowerCannotTurnAnIdlePatrolAwayFromItsWaypoint() throws Exception {
        var f = new Fixture(); f.tank.power = -.5F;
        Vec3 waypoint = new Vec3(-18, 0, 0);
        int advancingTicks = 0;
        for (int tick = 0; tick < 100; tick++) {
            f.move(waypoint, false);
            // Native trackEngine reverses the steering branch on the emitted reverse key, not power.
            int direction = (f.tank.right ? 1 : 0) - (f.tank.left ? 1 : 0);
            f.tank.yaw += direction * (f.tank.back ? -1 : 1);
            if (f.tank.forward) {
                advancingTicks++;
                double radians = Math.toRadians(f.tank.yaw);
                f.tank.pos = f.tank.pos.add(-Math.sin(radians) * .02, 0, Math.cos(radians) * .02);
            }
        }
        Vec3 remaining = waypoint.subtract(f.tank.pos);
        double bearing = Math.toDegrees(Math.atan2(-remaining.x, remaining.z));
        assertEquals(bearing, f.tank.yaw, 10, "patrol heading must converge toward the waypoint");
        assertTrue(advancingTicks > 0, "aligned patrol must advance instead of spinning at zero throttle");
    }
    @Test void unknownBudgetStopsInsteadOfTreatingDeferredGeometryAsAnObstacleToReverseAround() throws Exception {
        var f=new Fixture();Budget.allowed=false;f.move(new Vec3(0,0,100),true);
        assertTrue(f.tank.brake);assertFalse(f.tank.forward||f.tank.back||f.tank.left||f.tank.right);assertEquals(0,Terrain.probes);
        Budget.allowed=true;f.move(new Vec3(0,0,100),true);assertTrue(f.tank.forward);assertEquals(1,Terrain.probes);
    }
    @Test void wholeLocalPathBlockedStopsAndArrivalOrLostReadinessClearsInputs() throws Exception {
        var f=new Fixture();Terrain.clear=false;f.move(new Vec3(0,0,100),true);
        assertTrue(f.tank.brake);assertFalse(f.tank.forward||f.tank.back);assertTrue(Terrain.probes<=4);
        Terrain.clear=true;f.move(new Vec3(0,0,2),false);assertTrue(f.tank.brake);
        f.tank.ready=false;f.move(new Vec3(0,0,100),true);assertFalse(f.tank.forward||f.tank.back||f.tank.left||f.tank.right);
    }
    static class Fixture{
        final Tank tank=new Tank();final Object controller;final Class<?> type;
        Fixture()throws Exception{
            Budget.allowed=true;Terrain.clear=true;Terrain.probes=0;
            String source="com/liucodex/doggiesnewvehicle/armored/ApocalypseAutonomousNavigation",name="com/liucodex/doggiesnewvehicle/compat/executionprobe/AlliedNavigation";
            Map<String,String> names=new HashMap<>();names.put(source,name);
            Map.ofEntries(Map.entry("com/liucodex/doggiesnewvehicle/armored/DnvAlliedApocalypseEntity",Tank.class),
                    Map.entry("net/minecraft/world/entity/Entity",Body.class),Map.entry("net/minecraft/world/level/Level",World.class),
                    Map.entry("net/minecraft/server/level/ServerLevel",Server.class),Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvTargetingBudget",Budget.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvGroundTerrainProbe",Terrain.class)).forEach((k,v)->names.put(k,Type.getInternalName(v)));
            ClassNode n=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(in.readAllBytes()).accept(n,0);}
            n.access=Opcodes.ACC_PUBLIC;n.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
            ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(new ClassRemapper(out,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}));
            type=new ExactQueuedShotFixture.Loader(Map.of(name,out.toByteArray())).loadClass(name.replace('/','.'));controller=type.getConstructor(Tank.class).newInstance(tank);
        }
        void move(Vec3 point,boolean enemy)throws Exception{type.getMethod("move",Vec3.class,boolean.class,boolean.class).invoke(controller,point,enemy,false);}
    }
    public static class World{}public static class Server extends World{}public static class Body{}
    public static class Tank extends Body{
        public boolean ready=true,horizontalCollision,forward,back,left,right,brake;public float power;public final UUID id=UUID.randomUUID();
        public Vec3 pos=Vec3.ZERO; public float yaw;
        public boolean autonomousReady(){return ready;}public Vec3 position(){return pos;}public UUID getUUID(){return id;}
        public double getX(){return pos.x;}public double getZ(){return pos.z;}public float getYRot(){return yaw;}public float getPower(){return power;}
        public boolean onGround(){return true;}public World level(){return new Server();}
        public void setForwardInputDown(boolean value){forward=value;}public void setBackInputDown(boolean value){back=value;}
        public void setLeftInputDown(boolean value){left=value;}public void setRightInputDown(boolean value){right=value;}
        public void setUpInputDown(boolean value){brake=value;}public void setDownInputDown(boolean value){}public void setSprintInputDown(boolean value){}
    }
    public static class Budget{public static boolean allowed;public static boolean admitGeometryCheck(Server level,UUID id){return allowed;}public static void releaseGeometryOwner(Server level,UUID id){}}
    public static class Terrain{public static boolean clear;public static int probes;public Terrain(Body source){}public boolean clear(float yaw,double distance){probes++;return clear;}}
}
