package com.liucodex.doggiesnewvehicle.neoforge;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.util.Arrays;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class NeoRenderStageTest {
    @Test void cpuProjectionUsesTheNativeCameraMatrixWhenTheLocalStackIsEmpty() {
        var local = new PoseStack();
        var camera = new Matrix4f().rotateY(.7F).rotateX(-.2F);
        var event = new RenderLevelStageEvent(null, null, local, camera,
                new Matrix4f(), 0, null, null, null);
        var relative = new Vector3f(7, 2, -11);
        var expected = camera.transformPosition(new Vector3f(relative));
        var actual = DnvNeoRenderStage.stack(event).last().pose().transformPosition(new Vector3f(relative));
        assertEquals(0, actual.distance(expected), 1e-5);
        assertTrue(actual.distance(relative) > 1);
        assertEquals(new Matrix4f(), local.last().pose());
        assertEquals(camera, event.getModelViewMatrix());
    }

    @ParameterizedTest
    @CsvSource({
            "boss, doggiesnewvehicle/boss/client/MothershipFxPass",
            "boss, doggiesnewvehicle/boss/client/MothershipFxPreview",
            "artillery, doggiesnewvehicle/client/BlizzardBeamPass",
            "main, doggiesnewvehicle/client/weapons/Dsx1TrailRenderer",
            "naval, libertad/client/ShellTracer"
    })
    void cameraRelativeDrawsUseTheEventPose(String module, String renderer) throws IOException {
        Assumptions.assumeTrue(Arrays.asList(System.getProperty("dnv.modules").split(",")).contains(module),
                () -> module + " is disabled in this build");
        var node = compiled(renderer);
        var stage = Type.getInternalName(RenderLevelStageEvent.class);
        var draw = node.methods.stream().filter(method -> method.name.equals("render")
                && method.desc.equals("(L" + stage + ";)V")).findFirst().orElseThrow();
        int poseReads = 0;
        for (var instruction : draw.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                assertFalse(call.owner.equals(Type.getInternalName(DnvNeoRenderStage.class))
                        || (call.owner.equals(stage) && call.name.equals("getModelViewMatrix")), renderer);
                if (call.owner.equals(stage) && call.name.equals("getPoseStack")) poseReads++;
            }
        }
        assertTrue(poseReads > 0, renderer);
    }

    @ParameterizedTest @ValueSource(strings={"DnvVehicleOperationsScreen","DnvVehicleCrewCoreScreen"})
    void screenDrawsItsPanelAfterTheSingleNativeBackdrop(String name) throws IOException {
        var node=compiled("doggiesnewvehicle/client/"+name);
        assertFalse(node.methods.stream().anyMatch(m->m.name.equals("render")));
        var background=node.methods.stream().filter(m->m.name.equals("renderBackground")).findFirst().orElseThrow();
        var calls=java.util.Arrays.stream(background.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).toList();
        assertEquals(node.superName,calls.get(0).owner);
        assertEquals("renderBackground",calls.get(0).name);
        assertEquals(1,calls.stream().filter(c->c.name.equals("renderBackground")).count());
        assertTrue(calls.stream().anyMatch(c->c.name.equals("fill")));
    }

    private ClassNode compiled(String renderer) throws IOException {
        var file = java.util.Arrays.stream(System.getProperty("dnv.production.classDirs").split(java.io.File.pathSeparator))
                .map(dir -> java.nio.file.Path.of(dir,"com/liucodex/" + renderer + ".class"))
                .filter(java.nio.file.Files::isRegularFile).findFirst().orElseThrow();
        var node = new ClassNode();
        try (var bytes = java.nio.file.Files.newInputStream(file)) {
            new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG);
        }
        return node;
    }
}
