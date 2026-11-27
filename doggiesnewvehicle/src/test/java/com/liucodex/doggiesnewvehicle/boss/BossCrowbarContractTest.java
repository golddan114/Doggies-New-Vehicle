package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.compat.SbwTestDependency;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks the real SBW interaction route and the compiled boss barriers. */
class BossCrowbarContractTest {
    private static final String HELPER = "com/liucodex/doggiesnewvehicle/boss/DnvBossInteraction";

    @Test void bothNativeCrowbarRoutesAreRejectedBeforeVehicleRetrieval() throws Exception {
        for (String entry : List.of(
                "com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity#m_6096_",
                "com/liucodex/noise/entity/AbstractNoiseVehicleCombat#interact",
                "com/liucodex/silentheaven/entity/AbstractSilentHeavenVehicleCombat#interact")) {
            String boss = entry.substring(0, entry.indexOf('#'));
            var interact = method(compiled(boss), entry.substring(entry.indexOf('#') + 1));
            var calls = calls(interact);
            int predicate = index(calls, HELPER, "isRetrievalTool");
            int reject = index(calls, HELPER, "rejectRetrieval");
            int nativeInteract = -1;
            for (int i = 0; i < calls.size(); i++) if (calls.get(i).getOpcode() == Opcodes.INVOKESPECIAL
                    && (calls.get(i).name.equals("interact") || calls.get(i).name.equals("m_6096_"))) nativeInteract = i;
            assertTrue(predicate >= 0 && reject > predicate && nativeInteract > reject, boss);
        }
        for (String boss : List.of(
                "com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity",
                "com/liucodex/noise/entity/AbstractNoiseVehicleContract",
                "com/liucodex/silentheaven/entity/AbstractSilentHeavenVehicleContract")) {
            var crowbar = calls(method(compiled(boss), "onCrowbarInteract"));
            assertEquals(1, crowbar.size(), boss);
            assertEquals(HELPER + ".rejectRetrieval", crowbar.get(0).owner + "." + crowbar.get(0).name);
        }
        var helper = compiled(HELPER);
        var reject = calls(method(helper, "rejectRetrieval"));
        assertFalse(reject.stream().anyMatch(c -> c.owner.contains("CrowbarItem")
                || c.name.equals("m_142687_") || c.name.equals("m_146870_")
                || c.name.contains("drop") || c.name.contains("shrink")));
    }

    @Test void exactSbwInteractionDispatchesCrowbarTagAndItemToTheInterceptedPaths() throws Exception {
        try (var zip = new ZipFile(SbwTestDependency.path().toFile())) {
            var entry = zip.getEntry("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity.class");
            assertNotNull(entry);
            var node = new ClassNode();
            new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(node, 0);
            var calls = calls(method(node, "m_6096_"));
            assertTrue(calls.stream().anyMatch(c -> c.name.equals("onCrowbarInteract")));
            assertTrue(calls.stream().anyMatch(c -> c.name.equals("onInteractVehicle")
                    && c.owner.equals("com/atsuishio/superbwarfare/item/IVehicleInteract")));
        }
    }

    private static int index(List<MethodInsnNode> calls, String owner, String name) {
        for (int i = 0; i < calls.size(); i++) if (calls.get(i).owner.equals(owner) && calls.get(i).name.equals(name)) return i;
        return -1;
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        return StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode compiled(String name) throws Exception {
        try (var input = BossCrowbarContractTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
