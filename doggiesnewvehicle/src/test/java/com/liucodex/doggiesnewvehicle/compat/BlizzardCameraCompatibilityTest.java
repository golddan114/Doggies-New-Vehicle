package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Real Minecraft/Mixin pipeline plus the fixed-index parameter edit proven by the player's log. */
class BlizzardCameraCompatibilityTest {
    @TempDir Path temp;

    @ParameterizedTest @CsvSource({"false,900", "true,900", "false,1500", "true,1500"})
    void parameterEditorsCoexistInEitherPriorityAndRegistrationOrder(boolean dnvFirst, int priority) throws Exception {
        var fixture = new DnvMuzzleMixinApplicationTest(); fixture.temp = temp;
        String runtime = System.getProperty("dnv.minecraft.srg", "");
        for (String mapping : runtime.isBlank() ? new String[]{""} : new String[]{"", runtime}) {
            try (var loader = fixture.loader()) {
                var service = loader.loadClass("com.liucodex.doggiesnewvehicle.compat.transform.ExactMuzzleMixinService");
                byte[][] result = (byte[][]) service.getMethod("transformCameraParameter", boolean.class, int.class, String.class)
                        .invoke(null, dnvFirst, priority, mapping);
                ClassNode renderer = new ClassNode(); new ClassReader(result[1]).accept(renderer, 0);
                String renderName = mapping.isBlank() ? "renderLevel" : "m_109089_";
                String setupName = mapping.isBlank() ? "setup" : "m_90575_";
                var render = renderer.methods.stream().filter(m -> m.name.equals(renderName)).findFirst().orElseThrow();
                var calls = Arrays.stream(render.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast).toList();
                assertEquals(1, calls.stream().filter(c -> c.owner.equals("net/minecraft/client/Camera")
                        && c.name.equals(setupName)).count(), "Preserve the native call exactly once for other injectors");
                assertEquals(1, calls.stream().filter(c -> c.name.contains("dnvtest$partial")).count(),
                        "The third-party float edit must still execute");
                assertEquals(1, renderer.methods.stream().flatMap(m -> Arrays.stream(m.instructions.toArray()))
                        .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                        .filter(c -> c.owner.endsWith("/BlizzardTvCamera") && c.name.equals("afterSetup")).count());
            }
        }
    }
}
