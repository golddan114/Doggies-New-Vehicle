package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/** API compatibility is independent of successful compilation against either release. */
class SbwDualVersionCompatibilityTest {
    private static final Path BASELINE = Path.of("libs/superbwarfare-0.8.9.1-hotfix-993063bed.jar");
    private static final String PREFIX = "com/atsuishio/superbwarfare/";

    @Test void selectedReleasePreservesBaselineLinkageSurface() throws Exception {
        List<String> failures = new ArrayList<>();
        int classes = 0;
        try (var oldJar = new ZipFile(BASELINE.toFile());
             var selected = new ZipFile(SbwTestDependency.path().toFile())) {
            for (var entry : oldJar.stream().filter(e -> e.getName().startsWith(PREFIX)
                    && e.getName().endsWith(".class")).toList()) {
                var next = selected.getEntry(entry.getName());
                if (next == null) {
                    failures.add("Missing class " + entry.getName());
                    continue;
                }
                failures.addAll(incompatibilities(read(oldJar, entry.getName()), read(selected, entry.getName())));
                classes++;
            }
        }
        assertTrue(classes > 1000, "The complete upstream class surface must be examined");
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
        System.out.println("SBW linkage surface checked: " + classes + " classes; selected=" + SbwTestDependency.path());
    }

    @Test void existingDchReflectionAndDnvMixinTargetsRemainByteIdentical() throws Exception {
        try (var oldJar = new ZipFile(BASELINE.toFile());
             var selected = new ZipFile(SbwTestDependency.path().toFile())) {
            for (String name : List.of("entity/vehicle/base/VehicleEntity", "data/gun/GunData",
                    "item/gun/GunItem", "tools/SeekTool$Builder", "event/GunEventHandler",
                    "entity/vehicle/utils/VehicleEngineUtils", "item/gun/vehicle/VehicleGunItem",
                    "entity/projectile/JavelinMissileEntity", "item/misc/ArtilleryIndicatorItem",
                    "network/message/send/ArtilleryIndicatorFireMessage")) {
                String path = PREFIX + name + ".class";
                assertArrayEquals(oldJar.getInputStream(oldJar.getEntry(path)).readAllBytes(),
                        selected.getInputStream(selected.getEntry(path)).readAllBytes(), name);
            }
            // 0.8.9.2 updates Kotlin serializer metadata/InnerClasses without changing
            // AmmoConsumer code. Still compare every executable method, not just its API.
            String ammo = PREFIX + "data/gun/AmmoConsumer.class";
            var baselineMethods = executableMethods(readCode(oldJar, ammo));
            var selectedMethods = executableMethods(readCode(selected, ammo));
            assertEquals(baselineMethods.keySet(), selectedMethods.keySet());
            baselineMethods.forEach((method, code) -> assertArrayEquals(code, selectedMethods.get(method), ammo + '.' + method));
        }
    }

    @Test void brokenDescriptorAndNewFinalMethodAreRejected() throws Exception {
        try (var jar = new ZipFile(BASELINE.toFile())) {
            String path = PREFIX + "entity/vehicle/base/VehicleEntity.class";
            var oldType = read(jar, path);
            var changed = read(jar, path);
            var health = changed.methods.stream().filter(m -> m.name.equals("getHealth")).findFirst().orElseThrow();
            health.desc = "()D";
            assertFalse(incompatibilities(oldType, changed).isEmpty());
            changed = read(jar, path);
            var method = changed.methods.stream().filter(m -> m.name.equals("getHealth")).findFirst().orElseThrow();
            method.access |= Opcodes.ACC_FINAL;
            assertFalse(incompatibilities(oldType, changed).isEmpty());
            var ammo = readCode(jar, PREFIX + "data/gun/AmmoConsumer.class");
            var before = executableMethods(ammo);
            var count = ammo.methods.stream().filter(m -> m.name.equals("count")).findFirst().orElseThrow();
            count.instructions.clear();
            count.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_0));
            count.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN));
            count.tryCatchBlocks.clear();
            count.maxStack = 1;
            assertFalse(java.util.Arrays.equals(before.get(count.name + count.desc),
                    executableMethods(ammo).get(count.name + count.desc)), "Same API with broken ammo count must be rejected");
        }
    }

    private static ClassNode readCode(ZipFile jar, String path) throws Exception {
        var node = new ClassNode();
        new ClassReader(jar.getInputStream(jar.getEntry(path))).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static java.util.Map<String, byte[]> executableMethods(ClassNode type) {
        var result = new java.util.TreeMap<String, byte[]>();
        for (var method : type.methods) {
            var writer = new ClassWriter(0);
            writer.visit(type.version, type.access, type.name, type.signature, type.superName, type.interfaces.toArray(String[]::new));
            method.accept(writer);
            writer.visitEnd();
            result.put(method.name + method.desc, writer.toByteArray());
        }
        return result;
    }

    private static ClassNode read(ZipFile jar, String path) throws Exception {
        var node = new ClassNode();
        new ClassReader(jar.getInputStream(jar.getEntry(path))).accept(node,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static List<String> incompatibilities(ClassNode oldType, ClassNode next) {
        List<String> failures = new ArrayList<>();
        if (!java.util.Objects.equals(oldType.superName, next.superName)
                || !next.interfaces.containsAll(oldType.interfaces)) failures.add(oldType.name + " hierarchy changed");
        checkAccess(oldType.name, oldType.access, next.access, failures);
        var methods = next.methods.stream().collect(Collectors.toMap(m -> m.name + m.desc, Function.identity()));
        var fields = next.fields.stream().collect(Collectors.toMap(f -> f.name + f.desc, Function.identity()));
        for (var method : oldType.methods) {
            if (!exported(method.access)) continue;
            String key = method.name + method.desc;
            var newer = methods.get(key);
            if (newer == null) failures.add(oldType.name + " missing method " + key);
            else checkAccess(oldType.name + "." + key, method.access, newer.access, failures);
        }
        for (var field : oldType.fields) {
            if (!exported(field.access)) continue;
            String key = field.name + field.desc;
            var newer = fields.get(key);
            if (newer == null) failures.add(oldType.name + " missing field " + key);
            else checkAccess(oldType.name + "." + key, field.access, newer.access, failures);
        }
        return failures;
    }

    private static boolean exported(int access) {
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0;
    }

    private static void checkAccess(String id, int oldAccess, int newAccess, List<String> failures) {
        int required = oldAccess & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED);
        if (required == Opcodes.ACC_PUBLIC && (newAccess & Opcodes.ACC_PUBLIC) == 0
                || required == Opcodes.ACC_PROTECTED && !exported(newAccess)
                || ((oldAccess ^ newAccess) & Opcodes.ACC_STATIC) != 0
                || (oldAccess & Opcodes.ACC_FINAL) == 0 && (newAccess & Opcodes.ACC_FINAL) != 0
                || (oldAccess & Opcodes.ACC_ABSTRACT) == 0 && (newAccess & Opcodes.ACC_ABSTRACT) != 0) {
            failures.add(id + " incompatible access/dispatch flags");
        }
    }
}
