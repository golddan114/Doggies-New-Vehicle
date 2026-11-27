package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks both compiled adapters; these assertions do not substitute for Forge collision tests. */
class DnvBossDriveWiringTest {
    private static final String DRIVER = "com/liucodex/doggiesnewvehicle/boss/DnvBossGroundDrive";
    private static final String CONTROL = "com/liucodex/doggiesnewvehicle/core/DnvGroundDrive";

    @ParameterizedTest
    @ValueSource(strings = {"Noise", "SilentHeaven"})
    void movementUsesResolvedDisplacementAndEveryLockClearsLocalMemory(String name) throws Exception {
        MethodNode travel = method(name, "Navigation", "travel");
        int move = call(travel, null, "move");
        int track = call(travel, DRIVER, "forwardTravel");
        assertTrue(move >= 0 && track > move);
        assertTrue(callAfter(travel, "getX", move) < track);
        assertTrue(callAfter(travel, "getZ", move) < track);
        assertTrue(call(travel, DRIVER, "velocity") < move);
        assertTrue(call(travel, DRIVER, "powerStep") >= 0);
        assertTrue(call(method(name, "Lifecycle", "updateAutonomousControls"), CONTROL, "command") >= 0);
        assertTrue(call(method(name, "Lifecycle", "clearAutonomyDecision"), CONTROL, "reset") >= 0);
        assertTrue(call(method(name, "Lifecycle", "lockForEntrance"), null, "clearAutonomyDecision") >= 0);
        assertTrue(call(method(name, "Lifecycle", "clearCombatStateForWreck"), null, "clearAutonomyDecision") >= 0);
        if (name.equals("SilentHeaven")) {
            assertTrue(call(method(name, "Lifecycle", "lockForTransition"), null, "clearAutonomyDecision") >= 0);
            assertTrue(call(method(name, "Navigation", "lockForTeleport"), CONTROL, "reset") >= 0);
        }
    }

    private static MethodNode method(String name, String layer, String method) throws Exception {
        String resource = "com/liucodex/" + name.toLowerCase(java.util.Locale.ROOT)
                + "/entity/Abstract" + name + "Vehicle" + layer + ".class";
        ClassNode node = new ClassNode();
        try (var in = DnvBossDriveWiringTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in); new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
        }
        return node.methods.stream().filter(m -> m.name.equals(method)).findFirst().orElseThrow();
    }

    private static int call(MethodNode method, String owner, String name) {
        int index = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)
                    && (owner == null || call.owner.equals(owner))) return index;
            index++;
        }
        return -1;
    }

    private static int callAfter(MethodNode method, String name, int after) {
        int index = 0;
        for (var instruction : method.instructions) {
            if (index > after && instruction instanceof MethodInsnNode call && call.name.equals(name)) return index;
            index++;
        }
        fail("No " + name + " after actual move"); return -1;
    }
}
