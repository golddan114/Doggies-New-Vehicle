package com.liucodex.libertad.compat.sbw;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;

/** Applies the real cannon-related Mixins to the installed SBW class bytes. */
public final class CannonMixinCheck extends MixinServiceAbstract
        implements IClassProvider, IClassBytecodeProvider, IGlobalPropertyService {
    private static final String LIBERTAD = "libertad.mixins.json";
    private static final String DNV = "mixins.doggiesnewvehicle.json";
    private static final String SBW = "com/atsuishio/superbwarfare/";
    private static final String SHELL = SBW + "entity/projectile/";
    private static final Set<String> LIBERTAD_MIXINS = Set.of(
            "CannonPenetrationMixin", "CannonLoadingMixin", "ShellStepMixin", "TraceToolMixin");
    private static final Set<String> DNV_MIXINS = Set.of(
            "DnvCannonShellChunkMixin", "DnvFastShellTailMixin", "DnvTurretShellLifecycleMixin", "ProjectileBlockDamageMixin");
    private static final Map<IPropertyKey, Object> PROPERTIES = new HashMap<>();
    private static final ZipFile[] inputs = new ZipFile[4];
    private final IContainerHandle container = new ContainerHandleVirtual("Libertad cannon Mixin check");

    public static void main(String[] args) throws Exception {
        boolean withDnv = !args[2].equals("-");
        boolean dnvFirst = args[4].equals("dnv-first");
        try {
            for (int i = 0; i < inputs.length; i++) if (!args[i].equals("-")) inputs[i] = new ZipFile(args[i]);
            MixinBootstrap.init();
            var environment = MixinEnvironment.getDefaultEnvironment();
            environment.setSide(MixinEnvironment.Side.valueOf(args[5]));
            for (String config : withDnv ? (dnvFirst ? List.of(DNV, LIBERTAD) : List.of(LIBERTAD, DNV)) : List.of(LIBERTAD)) {
                Mixins.addConfiguration(config);
            }
            var service = (CannonMixinCheck) MixinService.getService();
            var transformer = service.getInternal(IMixinTransformerFactory.class).createTransformer();
            Map<String, ClassNode> transformed = new HashMap<>();
            for (String target : List.of(SHELL + "CannonShellEntity", SHELL + "FastThrowableProjectile", SBW + "tools/TraceTool")) {
                byte[] before = bytes(target + ".class");
                transformed.put(target, node(transformer.transformClass(environment, target.replace('/', '.'), before)));
            }
            var cannon = transformed.get(SHELL + "CannonShellEntity");
            var penetration = calls(cannon, "afterHitEntity");
            require(count(penetration, "libertad$capturePenetrationHit") == 1
                    && index(penetration, "libertad$capturePenetrationHit") < index(penetration, "libertad$markEntityTrace"),
                    "The hit position must be captured before the original trace predicate is marked");
            require(count(penetration, "libertad$markEntityTrace") == 1, "Libertad must tag the single native entity trace");
            String dispatch = withDnv ? "dnv$loadedPenetration" : "getEntitiesAlongVector";
            require(count(penetration, dispatch) == 1 && index(penetration, "libertad$markEntityTrace") < index(penetration, dispatch),
                    "The tagged argument must reach the native or DNV-dispatched call");
            require(index(penetration, dispatch) < index(penetration, "forceHurt"), "SBW must still own the damage loop");
            require(callsTo(cannon, "afterHitEntity").stream()
                            .noneMatch(call -> call.owner.startsWith("org/spongepowered/asm/synthetic/args/")),
                    "The installed Forge loader must not need a synthetic argument class to link the cannon");
            require(count(calls(cannon, "afterHitBlock"), "libertad$beforeBlockProbe") == 1
                    && count(calls(cannon, "afterHitBlock"), "libertad$beforePenetrationMove") == 1, "Libertad block boundaries missing");
            if (withDnv) {
                var dnvTrace = calls(cannon, dispatch);
                require(count(dnvTrace, "limitSecondaryTrace") == 1 && count(dnvTrace, "getEntitiesAlongVector") == 1,
                        "DNV must still route its own bounded trace to SBW");
                require(count(calls(cannon, "afterHitBlock"), "dnv$loadedBlockPenetration") == 1, "DNV block boundary missing");
                require(count(calls(cannon, "forceLoadChunk"), "dnv$noNativeChunkLoading") == 1, "DNV ticket guard missing");
            }
            require(count(calls(cannon, "forceLoadChunk"), "libertad$noChunkTickets") == 1, "Libertad ticket guard missing");
            var fast = transformed.get(SHELL + "FastThrowableProjectile");
            require(count(calls(fast, "m_8119_"), "libertad$clipAfterNativeHit") == 1, "Libertad step boundary missing");
            require(count(calls(fast, "writeSpawnData"), "libertad$writeTracer") == 1
                    && count(calls(fast, "readSpawnData"), "libertad$readTracer") == 1, "tracer spawn suffix was not applied");
            long clientHooks = args[5].equals("CLIENT") ? 1 : 0;
            require(count(calls(fast, "projectileMove"), "libertad$beforeTracerMove") == clientHooks
                    && count(calls(fast, "projectileMove"), "libertad$afterTracerMove") == clientHooks
                    && count(calls(fast, "shellTrail"), "libertad$replaceShellSmoke") == clientHooks,
                    "tracer hooks must bracket client movement and remain out of the dedicated server");
            require(count(calls(transformed.get(SBW + "tools/TraceTool"), "getEntitiesAlongVector"),
                    "libertad$boundMarkedEntityTrace") == 1, "Libertad trace guard missing");
            System.out.printf("PASS: SBW cannon Mixin application (%s, %s)%n", withDnv ? args[4] : "standalone", args[5]);
        } finally {
            for (ZipFile input : inputs) if (input != null) input.close();
        }
    }

    private static List<String> calls(ClassNode node, String method) {
        return callsTo(node, method).stream().map(call -> call.name).toList();
    }

    private static List<MethodInsnNode> callsTo(ClassNode node, String method) {
        var calls = new ArrayList<MethodInsnNode>();
        var target = node.methods.stream().filter(m -> m.name.equals(method) || m.name.endsWith("$" + method))
                .findFirst().orElseThrow();
        for (var instruction : target.instructions) {
            if (instruction instanceof MethodInsnNode call) calls.add(call);
        }
        return calls;
    }

    private static long count(List<String> calls, String name) { return calls.stream().filter(call -> call.contains(name)).count(); }
    private static int index(List<String> calls, String name) {
        return calls.indexOf(calls.stream().filter(call -> call.contains(name)).findFirst().orElseThrow());
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ClassNode node(byte[] bytes) {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] bytes(String name) throws IOException {
        ZipFile input = null;
        if (name.startsWith(SBW)) input = inputs[0];
        if (name.startsWith("net/minecraft/")) input = inputs[1];
        if (name.startsWith("com/liucodex/doggiesnewvehicle/") || name.startsWith("mixins.doggiesnewvehicle.")) input = inputs[2];
        if (name.startsWith("com/liucodex/libertad/") || name.equals(LIBERTAD)) input = inputs[3];
        if (input != null) {
            var entry = input.getEntry(name);
            if (entry == null) throw new IOException("Missing Mixin input " + name + " in " + input.getName());
            try (InputStream stream = input.getInputStream(entry)) { return stream.readAllBytes(); }
        }
        try (InputStream stream = CannonMixinCheck.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IOException("Missing Mixin input " + name);
            return stream.readAllBytes();
        }
    }

    private static byte[] configuration(String name) throws IOException {
        var config = JsonParser.parseString(new String(bytes(name), StandardCharsets.UTF_8)).getAsJsonObject();
        var selected = new JsonArray();
        for (var entry : config.getAsJsonArray("mixins")) {
            if ((name.equals(DNV) ? DNV_MIXINS : LIBERTAD_MIXINS).contains(entry.getAsString())) selected.add(entry);
        }
        config.add("mixins", selected);
        var client = new JsonArray();
        if (name.equals(LIBERTAD)) {
            for (var entry : config.getAsJsonArray("client")) {
                if (entry.getAsString().equals("ClientShellTrailMixin")) client.add(entry);
            }
        }
        config.add("client", client);
        config.add("server", new JsonArray());
        config.remove("plugin");
        return config.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override public String getName() { return "LibertadCannonCheck"; }
    @Override public boolean isValid() { return inputs[0] != null; }
    @Override public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() { return MixinEnvironment.CompatibilityLevel.JAVA_17; }
    @Override public IClassProvider getClassProvider() { return this; }
    @Override public IClassBytecodeProvider getBytecodeProvider() { return this; }
    @Override public ITransformerProvider getTransformerProvider() { return null; }
    @Override public IClassTracker getClassTracker() { return null; }
    @Override public IMixinAuditTrail getAuditTrail() { return null; }
    @Override public Collection<String> getPlatformAgents() { return List.of(); }
    @Override public IContainerHandle getPrimaryContainer() { return container; }
    @Override public Collection<IContainerHandle> getMixinContainers() { return List.of(); }
    @Override public URL[] getClassPath() { return new URL[0]; }
    @Override public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
    @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        if (name.startsWith("net.minecraft.") || name.startsWith("com.atsuishio.")) throw new ClassNotFoundException(name);
        return Class.forName(name, initialize, getClass().getClassLoader());
    }
    @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name, initialize); }
    @Override public InputStream getResourceAsStream(String name) {
        try { return new ByteArrayInputStream(name.equals(DNV) || name.equals(LIBERTAD) ? configuration(name) : bytes(name)); }
        catch (IOException missing) { return null; }
    }
    @Override public ClassNode getClassNode(String name) throws IOException { return getClassNode(name, true); }
    @Override public ClassNode getClassNode(String name, boolean runTransformers) throws IOException {
        return node(bytes(name.replace('.', '/') + ".class"));
    }
    @Override public IPropertyKey resolveKey(String name) { return new Key(name); }
    @SuppressWarnings("unchecked") @Override public <T> T getProperty(IPropertyKey key) { return (T) PROPERTIES.get(key); }
    @Override public void setProperty(IPropertyKey key, Object value) { PROPERTIES.put(key, value); }
    @SuppressWarnings("unchecked") @Override public <T> T getProperty(IPropertyKey key, T fallback) { return (T) PROPERTIES.getOrDefault(key, fallback); }
    @Override public String getPropertyString(IPropertyKey key, String fallback) { return String.valueOf(PROPERTIES.getOrDefault(key, fallback)); }
    private record Key(String name) implements IPropertyKey {}
}
