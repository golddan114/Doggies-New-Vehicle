package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Compiled production limiters around the unchanged native chassis and engine-loop ports. */
class Project612MobilityExecutionTest {
    @Test void mouseHeadingConvergesAcrossWrapAndReverseWithoutDraggingTheCamera() throws Exception {
        var data=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/artillery/resources/data/doggievehicle/sbw/vehicles/project612.json"))).getAsJsonObject();
        boolean rotateSeat=data.getAsJsonArray("Seats").get(0).getAsJsonObject().get("RotateWithVehicle").getAsBoolean();
        for(float heading:new float[]{-179,-90,90,179})for(boolean reverse:new boolean[]{false,true})for(boolean server:new boolean[]{false,true}){
            var tank=create();tank.running=tank.simulateSteering=true;tank.back=reverse;tank.driver=new Pilot();tank.driver.heading=heading;
            tank.yaw=Math.abs(heading)>170?-heading:0;tank.world.isClientSide=!server;tank.local=!server;
            for(int tick=0;tick<200;tick++){
                float before=tank.yaw;tank.travel();
                float turn=com.liucodex.doggiesnewvehicle.armored.ApocalypseMotion.degrees(tank.yaw-before);
                assertTrue(Math.abs(turn)<=1.00001);assertFalse(tank.left||tank.right);
                // This is the native copyEntityData RotateWithVehicle branch, applied after chassis travel.
                if(rotateSeat)tank.driver.heading+=turn;
            }
            assertEquals(0,com.liucodex.doggiesnewvehicle.armored.ApocalypseMotion.degrees(tank.yaw-heading),.05);
            assertEquals(heading,tank.driver.heading);
            assertTrue(tank.nativeSteeringCalls>0,"Server authority and local prediction both enter the native track path");
        }
    }
    @Test void manualSteeringAndNativeOwnershipVetoAutomaticMouseCommands() throws Exception {
        var manual=create();manual.running=manual.simulateSteering=true;manual.driver=new Pilot();manual.driver.heading=90;manual.left=true;
        manual.travel();assertEquals(-1,manual.yaw);assertTrue(manual.left);assertFalse(manual.right);
        for(int mode=1;mode<10;mode++){
            var tank=create();tank.running=tank.simulateSteering=true;tank.driver=new Pilot();tank.driver.heading=90;
            switch(mode){
                case 1 -> tank.energy=0;case 2 -> tank.local=false;
                case 3 -> tank.grounded=false;case 4 -> tank.wreck=true;case 5 -> tank.removed=true;
                case 6 -> tank.driver=null;case 7 -> tank.driver.alive=false;case 8 -> tank.driver.spectator=true;
                case 9 -> tank.driver.vertical=true;default -> throw new AssertionError();
            }
            tank.travel();assertEquals(0,tank.yaw);assertFalse(tank.lastLeft||tank.lastRight);
        }
    }
    @Test void nativeExceptionRestoresInputAndAlignedAimStopsResidualSteering() throws Exception {
        var tank=create();tank.running=tank.simulateSteering=true;tank.driver=new Pilot();tank.driver.heading=90;tank.fail=true;
        assertThrows(IllegalStateException.class,tank::travel);assertFalse(tank.left||tank.right);
        tank.fail=false;tank.driver.heading=0;tank.deltaRot=1;tank.hold=9;tank.travel();
        assertEquals(0,tank.yaw);assertEquals(0,tank.deltaRot);assertEquals(0,tank.hold);
    }
    @Test void forwardReverseAndSteeringCapsPreserveVerticalMotionAndDisableSprint() throws Exception {
        for (int sign : new int[] {-1, 1}) {
            var tank = create(); tank.nativeTurn = 12 * sign; tank.nativeVelocity = new Vec3(0, .2, 2 * sign);
            tank.travel();
            assertEquals(sign, tank.yaw); assertEquals(sign < 0 ? .05 : .15, tank.velocity.horizontalDistance(), 1e-8);
            assertEquals(.2, tank.velocity.y); assertFalse(tank.sprint);
            assertEquals(0F, tank.getClass().getMethod("getTurretTurnYSpeed").invoke(tank));
            assertEquals(-3F, tank.getClass().getMethod("getTurretMinPitch").invoke(tank));
            assertEquals(25F, tank.getClass().getMethod("getTurretMaxPitch").invoke(tank));
        }
        var tank = create(); tank.nativeTurn = .25F; tank.nativeVelocity = new Vec3(.01, -.3, .02); tank.travel();
        assertEquals(.25F, tank.yaw); assertEquals(tank.nativeVelocity, tank.velocity);
        tank.grounded = false; tank.nativeVelocity = new Vec3(1, -.3, 2); tank.travel();
        assertEquals(tank.nativeVelocity, tank.velocity);
    }
    @Test void nativeLoopIsAudibleAtIdleAndTracksEngineStopWithoutWritingAnyEngineState() throws Exception {
        var tank = create();
        var volume = tank.getClass().getMethod("getEngineSoundVolume");
        assertEquals(0F, volume.invoke(tank));
        tank.running = true;
        assertEquals(.16F, volume.invoke(tank));
        tank.nativeVolume = .6F; assertEquals(.6F, volume.invoke(tank));
        tank.running = false; assertEquals(0F, volume.invoke(tank));
    }
    private static Port create() throws Exception {
        String source = "com/liucodex/doggiesnewvehicle/armored/DnvProject612Entity", target = source + "MobilityProbe";
        var n = new ClassNode();
        try (var in = Project612MobilityExecutionTest.class.getClassLoader().getResourceAsStream(source + ".class")) {
            new ClassReader(in.readAllBytes()).accept(n, ClassReader.SKIP_FRAMES);
        }
        n.interfaces.clear(); n.fields.clear();
        n.methods.removeIf(m -> !Set.of("travel", "hullAimError", "getTurretTurnYSpeed", "getTurretMinPitch", "getTurretMaxPitch", "getEngineSoundVolume").contains(m.name));
        var init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, n.superName, "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN)); n.methods.add(init);
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        n.accept(new ClassRemapper(writer, new SimpleRemapper(Map.of(source, target,
                "com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity", Type.getInternalName(Port.class),
                "net/minecraft/world/level/Level",Type.getInternalName(World.class),
                "net/minecraft/world/entity/Entity",Type.getInternalName(Pilot.class),
                "net/minecraft/world/entity/player/Player",Type.getInternalName(Pilot.class)))));
        return (Port) new ExactQueuedShotFixture.Loader(Map.of(target, writer.toByteArray())).loadClass(target.replace('/', '.')).getConstructor().newInstance();
    }
    public static class Port extends ApocalypseMobilityExecutionTest.NativeVehicle {
        public boolean running,simulateSteering,left,right,back,wreck,removed,fail,lastLeft,lastRight,local=true;
        public int energy=100_000,hold,nativeSteeringCalls;public Pilot driver;public final World world=new World();
        public float nativeVolume,deltaRot;
        @Override public void travel(){
            lastLeft=left;lastRight=right;
            if(left||right)nativeSteeringCalls++;
            if(fail)throw new IllegalStateException("native physics failed");
            if(simulateSteering)nativeTurn=(right?12:left?-12:deltaRot*8)*(back?-1:1);
            super.travel();
        }
        public Pilot getNthEntity(int seat){return driver;}
        public World level(){return world;}
        public boolean leftInputDown(){return left;}public boolean rightInputDown(){return right;}public boolean backInputDown(){return back;}
        public void setLeftInputDown(boolean value){left=value;}public void setRightInputDown(boolean value){right=value;}
        public void setDeltaRot(float value){deltaRot=value;}public void setHoldTick(int value){hold=value;}
        public boolean isControlledByLocalInstance(){return local;}public int getEnergy(){return energy;}
        public boolean isWreck(){return wreck;}public boolean isRemoved(){return removed;}
        public boolean engineRunning() { return running; }
        public float getEngineSoundVolume() { return nativeVolume; }
    }
    public static class Pilot {
        public float heading;public boolean alive=true,spectator,vertical;
        public boolean isAlive(){return alive;}public boolean isSpectator(){return spectator;}
        public Vec3 getViewVector(float partial){double yaw=Math.toRadians(heading);return vertical?new Vec3(0,1,0):new Vec3(-Math.sin(yaw),0,Math.cos(yaw));}
    }
    public static class World {public boolean isClientSide=true;}
}
