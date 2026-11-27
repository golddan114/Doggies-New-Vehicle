package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/** Executes the exact mixed native energy/driver gate. It does not model terrain or track physics. */
class ApocalypseAutonomousDriveExecutionTest {
    @TempDir Path temp;
    private static final String ENGINE = "com/atsuishio/superbwarfare/entity/vehicle/utils/VehicleEngineUtils";
    private static final String VEHICLE = "com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity";
    private static final String PROBE = "com/liucodex/doggiesnewvehicle/compat/executionprobe/AutonomousTrackGate";

    @Test void driverlessPlayerTankStillStopsWhileAuthorizedAiRetainsNativeInputs() throws Exception {
        var gate = gate();
        var playerTank = new Vehicle(); gate.invoke(null, playerTank, null, 2);
        assertFalse(playerTank.forward); assertEquals(0, playerTank.power);
        var ai = new Ai(); ai.authorized = true; gate.invoke(null, ai, null, 2);
        assertTrue(ai.forward); assertTrue(ai.left); assertEquals(.5F, ai.power);
        ai = new Ai(); ai.authorized = false; gate.invoke(null, ai, null, 2);
        assertFalse(ai.forward); assertFalse(ai.left); assertEquals(0, ai.power);
    }

    @Test void aiCannotBypassNativeEnergyCutoffAndOrdinaryDriverKeepsItsIdentity() throws Exception {
        var gate = gate();
        var ai = new Ai(); ai.authorized = true; ai.energy = 2;
        gate.invoke(null, ai, null, 2);
        assertFalse(ai.forward); assertFalse(ai.back); assertFalse(ai.left); assertFalse(ai.right);
        assertEquals(.475F, ai.power, 1E-6);
        var playerTank = new Vehicle(); playerTank.driver = new PortEntity();
        gate.invoke(null, playerTank, null, 2);
        assertTrue(playerTank.forward); assertEquals(.5F, playerTank.power);
        assertSame(playerTank.driver, playerTank.getControllingPassenger());
    }

    private java.lang.reflect.Method gate() throws Exception {
        var harness = new DnvMuzzleMixinApplicationTest(); harness.temp = temp;
        ClassNode full = new ClassNode();
        try (var loader = harness.loader()) {
            new ClassReader(DnvMuzzleMixinApplicationTest.transform(loader,
                    "entity/vehicle/utils/VehicleEngineUtils", "ApocalypseAutonomousDriveMixin", "SERVER", false)[1])
                    .accept(full, ClassReader.SKIP_FRAMES);
        }
        var nativeMethod = full.methods.stream().filter(m -> m.name.equals("trackEngine")).findFirst().orElseThrow();
        var call = Arrays.stream(nativeMethod.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).filter(m -> m.name.equals("m_146895_")).findFirst().orElseThrow();
        var sprint = Arrays.stream(nativeMethod.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).filter(m -> m.name.equals("sprintInputDown")).findFirst().orElseThrow();
        AbstractInsnNode start = previousInstruction(call), end = previousInstruction(sprint);
        // Include the original query before modifying its stored local reference.
        while (!(start instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == 0))
            start = previousInstruction(start);
        ClassNode fragment = new ClassNode(); fragment.version = Opcodes.V17;
        fragment.access = Opcodes.ACC_PUBLIC; fragment.name = ENGINE; fragment.superName = "java/lang/Object";
        full.methods.stream().filter(m -> m.name.contains("dnv$")).forEach(fragment.methods::add);
        MethodNode gate = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "gate", "(L" + VEHICLE + ";Ljava/lang/Object;I)V", null, null);
        gate.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        gate.instructions.add(new VarInsnNode(Opcodes.ISTORE, 4));
        Map<LabelNode, LabelNode> labels = new HashMap<>();
        for (var i : nativeMethod.instructions.toArray()) if (i instanceof LabelNode l) labels.put(l, new LabelNode());
        for (var i = start; i != end; i = i.getNext())
            if (!(i instanceof LineNumberNode) && !(i instanceof FrameNode)) gate.instructions.add(i.clone(labels));
        gate.instructions.add(new InsnNode(Opcodes.RETURN)); fragment.methods.add(gate);
        Map<String, String> names = Map.of(ENGINE, PROBE, VEHICLE, Type.getInternalName(Vehicle.class),
                "com/atsuishio/superbwarfare/data/vehicle/subdata/EngineInfo$Track", "java/lang/Object",
                "net/minecraft/world/entity/Entity", Type.getInternalName(PortEntity.class),
                "com/liucodex/doggiesnewvehicle/armored/DnvAlliedApocalypseEntity", Type.getInternalName(Ai.class));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) { return Type.getInternalName(PortEntity.class); }
        };
        fragment.accept(new ClassRemapper(writer, new Remapper() {
            @Override public String map(String name) { return names.getOrDefault(name, name); }
        }));
        var loader = new ExactQueuedShotFixture.Loader(Map.of(PROBE, writer.toByteArray()));
        return loader.loadClass(PROBE.replace('/', '.')).getMethod("gate", Vehicle.class, Object.class, int.class);
    }

    private static AbstractInsnNode previousInstruction(AbstractInsnNode from) {
        do { from = from.getPrevious(); } while (from.getOpcode() < 0);
        return from;
    }

    public static class PortEntity { }
    public static class Vehicle extends PortEntity {
        public PortEntity driver;
        public boolean forward = true, back = true, left = true, right = true;
        public float power = .5F;
        public int energy = 100;
        public PortEntity getControllingPassenger() { return driver; }
        public PortEntity getFirstPassenger() { return driver; }
        public PortEntity m_146895_() { return driver; }
        public int getEnergy() { return energy; }
        public int getMaxEnergy() { return 100; }
        public float getPower() { return power; }
        public void setPower(float value) { power = value; }
        public void setForwardInputDown(boolean value) { forward = value; }
        public void setBackInputDown(boolean value) { back = value; }
        public void setLeftInputDown(boolean value) { left = value; }
        public void setRightInputDown(boolean value) { right = value; }
    }
    public static class Ai extends Vehicle {
        public boolean authorized;
        public boolean nativeDriveAuthorized() { return authorized; }
    }
}
