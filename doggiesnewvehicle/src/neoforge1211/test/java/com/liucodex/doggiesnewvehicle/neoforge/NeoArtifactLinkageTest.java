package com.liucodex.doggiesnewvehicle.neoforge;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Checks current compiled output by default; the artifact-tagged test requires an explicit packaging task. */
class NeoArtifactLinkageTest {
    private final Map<String, byte[]> bytes = new HashMap<>();
    private final Map<String, ClassNode> nodes = new HashMap<>();
    private final Set<String> absent = new HashSet<>();
    @Test void currentCompiledMinecraftNeoForgeAndSbwCallsResolveAgainstSelectedProfile() throws Exception {
        readJar(System.getProperty("dnv.sbw.jar"), true);
        verifyProduction(readDirectories(System.getProperty("dnv.production.classDirs")), "compiled");
    }
    @Test @Tag("artifact")
    void allShippedMinecraftNeoForgeAndSbwCallsResolveAgainstSelectedProfile() throws Exception {
        readJar(System.getProperty("dnv.sbw.jar"), true);
        verifyProduction(readJar(System.getProperty("dnv.artifact"), false), "artifact");
    }
    @Test void missingCompiledInputCannotFallBackToAnOldArtifact(@TempDir Path directory) {
        assertThrows(IOException.class, () -> readDirectories(null));
        assertThrows(IOException.class, () -> readDirectories(directory.resolve("missing").toString()));
        assertThrows(IOException.class, () -> readDirectories(directory.toString()));
    }
    @Test void missingExternalMemberIsRejectedInsteadOfUsingAnUnrelatedCompileDependency() throws Exception {
        readJar(System.getProperty("dnv.sbw.jar"), true);
        assertFalse(member("net/minecraft/world/entity/Entity", "dnv$missingLinkageMember", "()V", true, new HashSet<>()));
        assertFalse(member("com/atsuishio/superbwarfare/NoSuchDnvLinkageClass", "missing", "()V", true, new HashSet<>()));
    }
    private void verifyProduction(List<ClassNode> shipped, String input) throws IOException {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (ClassNode node : shipped) for (MethodNode method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && external(call.owner)) {
                    checked++;
                    if (!member(call.owner, call.name, call.desc, true, new HashSet<>()))
                        failures.add(node.name + "." + method.name + " -> " + call.owner + "." + call.name + call.desc);
                } else if (instruction instanceof FieldInsnNode field && external(field.owner)) {
                    checked++;
                    if (!member(field.owner, field.name, field.desc, false, new HashSet<>()))
                        failures.add(node.name + "." + method.name + " -> " + field.owner + "." + field.name + ":" + field.desc);
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object arg : dynamic.bsmArgs) if (arg instanceof Handle handle && external(handle.getOwner())) {
                        checked++;
                        if (!member(handle.getOwner(), handle.getName(), handle.getDesc(), handle.getTag() >= Opcodes.H_INVOKEVIRTUAL, new HashSet<>()))
                            failures.add(node.name + "." + method.name + " -> " + handle);
                    }
                }
            }
        }
        Set<String> modules = Set.of(System.getProperty("dnv.modules", "").split(","));
        assertTrue(modules.contains("main"), "Missing module selection");
        if (modules.containsAll(Set.of("main", "artillery", "boss", "platform"))) {
            assertTrue(shipped.size() > 700, "All four modules and route-core must be in this input");
            assertTrue(checked > 5000, "The scan must resolve production call sites");
        } else {
            assertFalse(shipped.isEmpty(), "Selected modules have no production classes");
            assertTrue(checked > 0, "Selected modules have no external references");
        }
        assertEquals(List.of(), failures, () -> String.join("\n", failures));
        System.out.println("LINKAGE " + input + " " + System.getProperty("dnv.sbw.profile") + ": " + shipped.size() + " classes, " + checked + " references");
    }
    private List<ClassNode> readDirectories(String paths) throws IOException {
        if (paths == null || paths.isBlank()) throw new IOException("Missing current production class directories");
        List<ClassNode> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (String directory : paths.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path root = Path.of(directory);
            if (!Files.isDirectory(root)) throw new IOException("Missing compiled output: " + root);
            int before = result.size();
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                    byte[] data = Files.readAllBytes(file);
                    ClassNode node = new ClassNode(); new ClassReader(data).accept(node, 0);
                    if (!names.add(node.name)) throw new IOException("Duplicate production class: " + node.name);
                    bytes.put(node.name, data);
                    result.add(node);
                }
            }
            if (result.size() == before) throw new IOException("Empty compiled output: " + root);
        }
        return result;
    }
    private static boolean external(String owner) {
        return owner.startsWith("net/minecraft/") || owner.startsWith("net/neoforged/")
                || owner.startsWith("com/atsuishio/superbwarfare/");
    }
    private List<ClassNode> readJar(String file, boolean nested) throws IOException {
        List<ClassNode> result = new ArrayList<>();
        try (ZipFile jar = new ZipFile(file)) {
            for (var entry : jar.stream().toList()) {
                if (entry.getName().endsWith(".class")) {
                    byte[] data = jar.getInputStream(entry).readAllBytes();
                    ClassNode node = new ClassNode(); new ClassReader(data).accept(node, 0);
                    bytes.put(node.name, data);
                    if (!nested) result.add(node);
                } else if (nested && entry.getName().startsWith("META-INF/jarjar/") && entry.getName().endsWith(".jar")) {
                    try (ZipInputStream zip = new ZipInputStream(jar.getInputStream(entry))) {
                        for (var part = zip.getNextEntry(); part != null; part = zip.getNextEntry()) {
                            if (part.getName().endsWith(".class")) {
                                byte[] data = zip.readAllBytes();
                                bytes.putIfAbsent(new ClassReader(data).getClassName(), data);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }
    private ClassNode node(String name) throws IOException {
        if (name == null || absent.contains(name)) return null;
        if (nodes.containsKey(name)) return nodes.get(name);
        byte[] data = bytes.get(name);
        // An absent SBW member/class must not be silently supplied by the compile-profile JAR.
        if (data == null && !name.startsWith("com/atsuishio/superbwarfare/")) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
                if (input != null) data = input.readAllBytes();
            }
        }
        if (data == null) { absent.add(name); return null; }
        ClassNode node = new ClassNode(); new ClassReader(data).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        nodes.put(name, node); return node;
    }
    private boolean member(String owner, String name, String descriptor, boolean method, Set<String> seen) throws IOException {
        if (!seen.add(owner)) return false;
        ClassNode type = node(owner);
        if (type == null) return false;
        boolean found = method ? type.methods.stream().anyMatch(value -> value.name.equals(name) && value.desc.equals(descriptor))
                : type.fields.stream().anyMatch(value -> value.name.equals(name) && value.desc.equals(descriptor));
        if (found) return true;
        if (name.equals("<init>")) return false;
        if (type.superName != null && member(type.superName, name, descriptor, method, seen)) return true;
        for (String parent : type.interfaces) if (member(parent, name, descriptor, method, seen)) return true;
        return false;
    }
}
