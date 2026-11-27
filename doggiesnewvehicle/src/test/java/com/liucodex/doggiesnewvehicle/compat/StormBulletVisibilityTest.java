package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the compiled visibility and draw methods without starting a graphics context. */
class StormBulletVisibilityTest {
    @Test void hiddenAtAllDistancesAndDirectRenderingNeedsNoVertexBuffer() throws Exception {
        Class<?> renderer = rendererProbe();
        Object instance = renderer.getConstructor().newInstance();
        var visible = renderer.getMethod("shouldRender", Object.class, Object.class,
                double.class, double.class, double.class);
        for (double distance : new double[] {0, 1, 32, 128, 512}) {
            assertEquals(false, visible.invoke(instance, null, null, distance, -distance, distance));
        }
        var draw = renderer.getMethod("render", Object.class, float.class, float.class,
                Object.class, Object.class, int.class);
        for (float partial : new float[] {0, .5F, 1}) {
            // An invisible projectile must not request a world, pose or vertex buffer.
            assertDoesNotThrow(() -> draw.invoke(instance, null, 90F, partial, null, null, 0));
        }
    }

    private static Class<?> rendererProbe() throws Exception {
        String source = "com/liucodex/doggiesnewvehicle/client/StormBulletRenderer";
        String probe = source + "VisibilityProbe";
        ClassNode node = new ClassNode();
        try (var input = StormBulletVisibilityTest.class.getClassLoader().getResourceAsStream(source + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        node.methods.removeIf(m -> !(m.name.equals("shouldRender") || m.name.equals("render"))
                || (m.access & Opcodes.ACC_BRIDGE) != 0);
        node.fields.clear();
        node.superName = "java/lang/Object";
        node.signature = null;
        node.innerClasses.clear();
        MethodVisitor constructor = node.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        Map<String, String> names = Map.of(
                source, probe,
                "com/liucodex/doggiesnewvehicle/entity/StormBulletEntity", "java/lang/Object",
                "net/minecraft/client/renderer/culling/Frustum", "java/lang/Object",
                "com/mojang/blaze3d/vertex/PoseStack", "java/lang/Object",
                "net/minecraft/client/renderer/MultiBufferSource", "java/lang/Object");
        ClassWriter writer = new ClassWriter(0);
        node.accept(new ClassRemapper(writer, new SimpleRemapper(names)));
        byte[] bytes = writer.toByteArray();
        return new ClassLoader(StormBulletVisibilityTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(probe.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
    }
}
