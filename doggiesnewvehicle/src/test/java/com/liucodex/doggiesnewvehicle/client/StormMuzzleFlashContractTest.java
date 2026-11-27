package com.liucodex.doggiesnewvehicle.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class StormMuzzleFlashContractTest {
    private static final String FLARE = "assets/superbwarfare/textures/particle/flare.png";

    @Test void rendererUsesAVisibleFlareAvailableInBothExactForgeDependencies() throws Exception {
        var node = new ClassNode();
        try (var input = getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/client/StormTurretRenderer.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        var initializer = node.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow();
        assertTrue(StreamSupport.stream(initializer.instructions.spliterator(), false)
                .anyMatch(n -> n instanceof LdcInsnNode literal && "textures/particle/flare.png".equals(literal.cst)));
        var render = node.methods.stream().filter(m -> m.name.equals("renderTurret")).findFirst().orElseThrow();
        assertTrue(StreamSupport.stream(render.instructions.spliterator(), false)
                .anyMatch(n -> n instanceof MethodInsnNode call && call.name.equals("hasClientMuzzleFlash")));
        for (String jar : List.of("superbwarfare-0.8.9.1-hotfix-993063bed.jar",
                "superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all.jar")) {
            try (var zip = new ZipFile(Path.of("libs", jar).toFile())) {
                var entry = zip.getEntry(FLARE);
                assertNotNull(entry, jar);
                var image = ImageIO.read(new ByteArrayInputStream(zip.getInputStream(entry).readAllBytes()));
                assertNotNull(image, jar);
                assertTrue(image.getWidth() > 0 && image.getHeight() > 0, jar);
                boolean visible = false;
                for (int y = 0; y < image.getHeight() && !visible; y++)
                    for (int x = 0; x < image.getWidth(); x++)
                        if ((image.getRGB(x, y) >>> 24) != 0) { visible = true; break; }
                assertTrue(visible, jar);
            }
        }
    }
}
