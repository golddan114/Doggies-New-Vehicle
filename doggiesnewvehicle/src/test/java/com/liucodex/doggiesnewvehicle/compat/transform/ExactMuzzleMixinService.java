package com.liucodex.doggiesnewvehicle.compat.transform;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;

/** Independent metadata-only real Mixin SPI; mirrors the established test method, not DCH runtime. */
public final class ExactMuzzleMixinService extends MixinServiceAbstract
    implements IClassProvider, IClassBytecodeProvider, IGlobalPropertyService, IClassTracker {
  private static final Map<IPropertyKey, Object> PROPERTIES = new HashMap<>();
  private static String selectedMixin;
  private static boolean broken;
  private static Path releaseMinecraft;
  private static Path releaseArtifact;
  private static boolean omitReleaseRefmap;
  private static Path combinedArmsArtifact;
  private static Path lightcoreArtifact;
  private static Path cameraArtifact;
  private static Path guardianArtifact;
  private static int cameraParameterPriority;
  private final IContainerHandle container = new ContainerHandleVirtual("DNV exact muzzle Mixin");

  public static byte[][] transform(String target, String mixin, String side, boolean invalid) throws Exception {
    selectedMixin = mixin;
    broken = invalid;
    MixinBootstrap.init();
    var environment = MixinEnvironment.getDefaultEnvironment();
    environment.setSide(MixinEnvironment.Side.valueOf(side));
    Mixins.addConfiguration(selectedMixin.startsWith("Blizzard") ? "mixins.dnvblizzard.json" : artilleryMixin() ? "mixins.dnvartillery.json" : "mixins.doggiesnewvehicle.json");
    var service = (ExactMuzzleMixinService) MixinService.getService();
    var transformer = service.getInternal(IMixinTransformerFactory.class).createTransformer();
    byte[] before = service.bytes(target + ".class");
    return new byte[][] {before, transformer.transformClass(environment, target.replace('/', '.'), before)};
  }

  public static byte[][] transformRelease(String minecraft, String artifact, String target,
                                          String mixin, boolean omitRefmap) throws Exception {
    return transformReleaseOnSide(minecraft, artifact, target, mixin, "SERVER", omitRefmap);
  }

  public static byte[][] transformReleaseOnSide(String minecraft, String artifact, String target,
                                                String mixin, String side, boolean omitRefmap) throws Exception {
    releaseMinecraft = Path.of(minecraft);
    releaseArtifact = artifact.isBlank() ? null : Path.of(artifact);
    omitReleaseRefmap = omitRefmap;
    return transform(target, mixin, side, false);
  }

  /** Applies both real damage mixins together; third-party game bootstrap is outside this fixture. */
  public static byte[][] transformCombinedArms(String artifact, String side, boolean dnvFirst) throws Exception {
    combinedArmsArtifact = Path.of(artifact);
    selectedMixin = "DnvVehicleArmorMixin";
    MixinBootstrap.init();
    var environment = MixinEnvironment.getDefaultEnvironment();
    environment.setSide(MixinEnvironment.Side.valueOf(side));
    for (String config : dnvFirst
        ? List.of("mixins.doggiesnewvehicle.json", "tacz_sewv.mixins.json")
        : List.of("tacz_sewv.mixins.json", "mixins.doggiesnewvehicle.json")) Mixins.addConfiguration(config);
    var service = (ExactMuzzleMixinService) MixinService.getService();
    var transformer = service.getInternal(IMixinTransformerFactory.class).createTransformer();
    byte[][] result = new byte[4][];
    int index = 0;
    for (String target : List.of("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",
        "com/atsuishio/superbwarfare/entity/vehicle/damage/DamageModifier")) {
      byte[] before = service.bytes(target + ".class");
      result[index++] = before;
      result[index++] = transformer.transformClass(environment, target.replace('/', '.'), before);
    }
    return result;
  }

  /** Explicit local reference probe only; lightcore never becomes a runtime or mandatory build dependency. */
  public static byte[][] transformClientLightCore(String artifact, boolean dnvFirst) throws Exception {
    lightcoreArtifact=Path.of(artifact);selectedMixin="BlizzardDynamicLightMixin";
    MixinBootstrap.init();var environment=MixinEnvironment.getDefaultEnvironment();
    environment.setSide(MixinEnvironment.Side.CLIENT);
    for(String config:dnvFirst?List.of("mixins.dnvblizzard.json","gd656lightcore.mixins.json"):
            List.of("gd656lightcore.mixins.json","mixins.dnvblizzard.json"))Mixins.addConfiguration(config);
    var service=(ExactMuzzleMixinService)MixinService.getService();
    var transformer=service.getInternal(IMixinTransformerFactory.class).createTransformer();
    String target="net/minecraft/world/level/lighting/BlockLightEngine";byte[] before=service.bytes(target+".class");
    return new byte[][]{before,transformer.transformClass(environment,target.replace('/','.'),before)};
  }

  private byte[] releaseBytes(String name) throws IOException {
    if (name.startsWith("net/minecraft/")) return archiveBytes(releaseMinecraft, name);
    if (name.equals("mixins.doggiesnewvehicle.json") || name.equals("mixins.dnvblizzard.json")) {
      byte[] data = releaseArtifact == null
          ? Files.readAllBytes(Path.of(name.equals("mixins.dnvblizzard.json") ? "src/artillery/resources" : "src/main/resources", name))
          : archiveBytes(releaseArtifact, name);
      var config = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
      if (omitReleaseRefmap) config.remove("refmap");
      for (String side : List.of("mixins", "client")) {
        JsonArray selected = new JsonArray();
        if (config.has(side)) for (var value : config.getAsJsonArray(side))
          if (value.getAsString().equals(selectedMixin) || guardianArtifact != null
              && (value.getAsString().equals("BlizzardCameraAccess")
                  || value.getAsString().equals("BlizzardCameraFrameMixin"))) selected.add(value);
        config.add(side, selected);
      }
      return config.toString().getBytes(StandardCharsets.UTF_8);
    }
    if (name.equals("mixins.doggiesnewvehicle.refmap.json") || name.equals("mixins.dnvartillery.refmap.json")) return releaseArtifact == null
        ? Files.readAllBytes(Path.of(name.equals("mixins.dnvartillery.refmap.json") ? "build/tmp/compileArtilleryJava" : "build/tmp/compileJava", name))
        : archiveBytes(releaseArtifact, name);
    if (releaseArtifact != null && name.startsWith("com/liucodex/doggiesnewvehicle/"))
      return archiveBytes(releaseArtifact, name);
    return null;
  }

  /** Neo's actual Camera plus SBW's cancellable setup; no synthetic ordering assumption. */
  public static byte[][] transformVehicleCamera(String artifact, boolean dnvFirst) throws Exception {
    cameraArtifact=Path.of(artifact);selectedMixin="BlizzardCameraSetupMixin";
    MixinBootstrap.init();var environment=MixinEnvironment.getDefaultEnvironment();
    environment.setSide(MixinEnvironment.Side.CLIENT);
    for(String config:dnvFirst?List.of("mixins.dnvblizzard.json","mixins.superbwarfare.json"):
            List.of("mixins.superbwarfare.json","mixins.dnvblizzard.json"))Mixins.addConfiguration(config);
    var service=(ExactMuzzleMixinService)MixinService.getService();
    var transformer=service.getInternal(IMixinTransformerFactory.class).createTransformer();
    byte[][] result=new byte[4][];int index=0;
    for(String target:List.of("net/minecraft/client/Camera","net/minecraft/client/renderer/GameRenderer")){
      byte[] before=service.bytes(target+".class");result[index++]=before;
      result[index++]=transformer.transformClass(environment,target.replace('/','.'),before);
    }
    return result;
  }

  /** Opt-in external reference probe; original Guardian/SBW handlers and refmaps remain intact. */
  public static byte[][] transformGuardianCamera(String guardian, String sbw, String minecraft,
                                                 String dnvRelease, boolean dnvFirst) throws Exception {
    guardianArtifact = Path.of(guardian);
    cameraArtifact = Path.of(sbw);
    releaseMinecraft = Path.of(minecraft);
    releaseArtifact = dnvRelease.isBlank() ? null : Path.of(dnvRelease);
    selectedMixin = "BlizzardCameraSetupMixin";
    MixinBootstrap.init();
    var environment = MixinEnvironment.getDefaultEnvironment();
    environment.setSide(MixinEnvironment.Side.CLIENT);
    for (String config : dnvFirst
        ? List.of("mixins.dnvblizzard.json", "mixins.superbwarfare.json", "mixins.time_slow.json")
        : List.of("mixins.time_slow.json", "mixins.superbwarfare.json", "mixins.dnvblizzard.json"))
      Mixins.addConfiguration(config);
    var service = (ExactMuzzleMixinService) MixinService.getService();
    var transformer = service.getInternal(IMixinTransformerFactory.class).createTransformer();
    byte[][] result = new byte[4][];
    int index = 0;
    for (String target : List.of("net/minecraft/client/Camera", "net/minecraft/client/renderer/GameRenderer")) {
      byte[] before = service.bytes(target + ".class");
      result[index++] = before;
      result[index++] = transformer.transformClass(environment, target.replace('/', '.'), before);
    }
    return result;
  }

  /** Log-derived ModifyArg mechanism, not Guardian's unavailable implementation. */
  public static byte[][] transformCameraParameter(boolean dnvFirst, int priority, String runtime) throws Exception {
    cameraParameterPriority = priority;
    if (!runtime.isBlank()) releaseMinecraft = Path.of(runtime);
    selectedMixin = "BlizzardCameraSetupMixin";
    MixinBootstrap.init();
    var environment = MixinEnvironment.getDefaultEnvironment();
    environment.setSide(MixinEnvironment.Side.CLIENT);
    for (String config : dnvFirst
        ? List.of("mixins.dnvblizzard.json", "dnv-camera-parameter-test.json")
        : List.of("dnv-camera-parameter-test.json", "mixins.dnvblizzard.json")) Mixins.addConfiguration(config);
    var service = (ExactMuzzleMixinService) MixinService.getService();
    var transformer = service.getInternal(IMixinTransformerFactory.class).createTransformer();
    String target = "net/minecraft/client/renderer/GameRenderer";
    byte[] before = service.bytes(target + ".class");
    return new byte[][] {before, transformer.transformClass(environment, target.replace('/', '.'), before)};
  }

  private static byte[] cameraParameterMixin() {
    var writer = new org.objectweb.asm.ClassWriter(0);
    writer.visit(org.objectweb.asm.Opcodes.V17, org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_ABSTRACT,
        "dnv/test/camera/ParameterMixin", null, "java/lang/Object", null);
    var mixin = writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
    var targets = mixin.visitArray("value");
    targets.visit(null, org.objectweb.asm.Type.getObjectType("net/minecraft/client/renderer/GameRenderer"));
    targets.visitEnd(); mixin.visit("priority", cameraParameterPriority); mixin.visitEnd();
    var method = writer.visitMethod(org.objectweb.asm.Opcodes.ACC_PRIVATE, "dnvtest$partial", "(F)F", null, null);
    var modify = method.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/ModifyArg;", true);
    var methods = modify.visitArray("method");
    methods.visit(null, releaseMinecraft == null ? "renderLevel" : "m_109089_"); methods.visitEnd();
    var at = modify.visitAnnotation("at", "Lorg/spongepowered/asm/mixin/injection/At;");
    at.visit("value", "INVOKE");
    at.visit("target", "Lnet/minecraft/client/Camera;" + (releaseMinecraft == null ? "setup" : "m_90575_")
        + "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V");
    at.visitEnd(); modify.visit("index", 4); modify.visit("remap", false); modify.visitEnd();
    method.visitCode(); method.visitLdcInsn(0.125F); method.visitInsn(org.objectweb.asm.Opcodes.FRETURN);
    method.visitMaxs(1, 2); method.visitEnd(); writer.visitEnd();
    return writer.toByteArray();
  }

  private static byte[] archiveBytes(Path archive, String name) throws IOException {
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      var entry = zip.getEntry(name);
      if (entry == null) throw new IOException("Missing runtime member: " + name);
      return zip.getInputStream(entry).readAllBytes();
    }
  }

  private byte[] testBytes(String name) throws IOException {
    if (guardianArtifact != null) {
      if (name.equals("mixins.time_slow.json")) {
        var config = JsonParser.parseString(new String(archiveBytes(guardianArtifact, name), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray selected = new JsonArray();
        selected.add("MixinTimeStop$CameraMixin"); selected.add("MixinTimeStop$GameRendererMixin");
        config.add("client", selected); config.add("mixins", new JsonArray());
        return config.toString().getBytes(StandardCharsets.UTF_8);
      }
      if (name.equals("mixins.time_slow.refmap.json") || name.startsWith("net/wzz/time_slow/"))
        return archiveBytes(guardianArtifact, name);
    }
    if (cameraParameterPriority != 0) {
      if (name.equals("dnv-camera-parameter-test.json")) return ("{\"required\":true,\"minVersion\":\"0.8\","
          + "\"package\":\"dnv.test.camera\",\"compatibilityLevel\":\"JAVA_17\","
          + "\"client\":[\"ParameterMixin\"],\"injectors\":{\"defaultRequire\":1}}")
          .getBytes(StandardCharsets.UTF_8);
      if (name.equals("dnv/test/camera/ParameterMixin.class")) return cameraParameterMixin();
    }
    if(cameraArtifact!=null && name.equals("mixins.superbwarfare.json")){
      var config=JsonParser.parseString(new String(archiveBytes(cameraArtifact,name),StandardCharsets.UTF_8)).getAsJsonObject();
      if (releaseMinecraft == null) config.remove("refmap");
      config.remove("plugin");
      JsonArray selected=new JsonArray();selected.add("CameraMixin");selected.add("GameRendererMixin");
      config.add("client",selected);config.add("mixins",new JsonArray());
      return config.toString().getBytes(StandardCharsets.UTF_8);
    }
    if(lightcoreArtifact!=null){
      if(name.equals("gd656lightcore.mixins.json")){
        var config=JsonParser.parseString(new String(archiveBytes(lightcoreArtifact,name),StandardCharsets.UTF_8)).getAsJsonObject();
        config.remove("refmap");JsonArray selected=new JsonArray();selected.add("client.BlockLightEngineDynamicLightMixin");
        config.add("client",selected);return config.toString().getBytes(StandardCharsets.UTF_8);
      }
      if(name.startsWith("org/mods/gd656lightcore/"))return archiveBytes(lightcoreArtifact,name);
    }
    if (combinedArmsArtifact != null) {
      if (name.equals("tacz_sewv.mixins.json")) {
        var config = JsonParser.parseString(new String(archiveBytes(combinedArmsArtifact, name),
            StandardCharsets.UTF_8)).getAsJsonObject();
        // Preserve required/defaultRequire/refmap and the actual redirect; isolate its registration.
        config.remove("plugin");
        JsonArray selected = new JsonArray(); selected.add("MixinVehicleDamageRedirect");
        config.add("mixins", selected); config.add("client", new JsonArray()); config.add("server", new JsonArray());
        return config.toString().getBytes(StandardCharsets.UTF_8);
      }
      if (name.equals("tacz_sewv.refmap.json") || name.startsWith("com/neoalive/tacz_sewv/"))
        return archiveBytes(combinedArmsArtifact, name);
    }
    return bytes(name);
  }

  private byte[] bytes(String name) throws IOException {
    if (releaseMinecraft != null) {
      byte[] data = releaseBytes(name);
      if (data != null) return data;
    }
    if (name.equals("mixins.doggiesnewvehicle.json") || name.equals("mixins.dnvartillery.json") || name.equals("mixins.dnvblizzard.json")) {
      var config = JsonParser.parseString(Files.readString(Path.of(
          !name.equals("mixins.doggiesnewvehicle.json") ? "src/artillery/resources" : "src/main/resources", name))).getAsJsonObject();
      // This fixture transforms development-named classes; release refmaps have their own gate.
      config.remove("refmap");
      for (String side : List.of("mixins", "client")) {
        JsonArray selected = new JsonArray();
        for (var value : config.getAsJsonArray(side)) if (value.getAsString().equals(selectedMixin)
                || selectedMixin.equals("ApocalypseClientFireMixin") && value.getAsString().equals("DragonClientPredictionMixin")
                || cameraArtifact!=null && (value.getAsString().equals("BlizzardCameraAccess")
                    || value.getAsString().equals("BlizzardCameraFrameMixin"))) selected.add(value);
        config.add(side, selected);
      }
      return config.toString().getBytes(StandardCharsets.UTF_8);
    }
    if (name.startsWith("com/liucodex/doggiesnewvehicle/")) {
      Path production = Path.of("build/classes/java/main", name);
      if (!Files.exists(production)) production = Path.of("build/classes/java/artillery", name);
      return Files.readAllBytes(production);
    }
    try (ZipFile sbw = new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
      var entry = sbw.getEntry(name);
      if (entry != null) return sbw.getInputStream(entry).readAllBytes();
      if (name.startsWith("com/github/mcmodderanchor/")) {
        try (ZipInputStream nested = new ZipInputStream(sbw.getInputStream(sbw.getEntry("META-INF/jarjar/simplebedrockmodel-2.5.1-forge-mc1.20.1.jar")))) {
          for (var part = nested.getNextEntry(); part != null; part = nested.getNextEntry()) {
            if (part.getName().equals(name)) return nested.readAllBytes();
          }
        }
      }
    }
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
      if (stream == null) throw new IOException(name);
      return stream.readAllBytes();
    }
  }

  private static boolean artilleryMixin() {
    return selectedMixin.startsWith("Apocalypse") || selectedMixin.startsWith("ArtilleryIndicator") || selectedMixin.equals("M1RemoteBodySaveMixin")
          || selectedMixin.equals("M1ReloadSoundMixin") || selectedMixin.startsWith("Nuclear")
          || selectedMixin.equals("DragonClientPredictionMixin") || selectedMixin.equals("DnvBlizzardFireReleaseMixin")
        || selectedMixin.startsWith("VehicleEnergy");
  }

  @Override public String getName() { return "DNVExactMuzzle"; }
  @Override public boolean isValid() { return true; }
  @Override public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() { return MixinEnvironment.CompatibilityLevel.JAVA_17; }
  @Override public IClassProvider getClassProvider() { return this; }
  @Override public IClassBytecodeProvider getBytecodeProvider() { return this; }
  @Override public ITransformerProvider getTransformerProvider() { return null; }
  @Override public IClassTracker getClassTracker() { return this; }
  @Override public IMixinAuditTrail getAuditTrail() { return null; }
  @Override public Collection<String> getPlatformAgents() { return List.of(); }
  @Override public IContainerHandle getPrimaryContainer() { return container; }
  @Override public Collection<IContainerHandle> getMixinContainers() { return List.of(); }
  @Override public URL[] getClassPath() { return new URL[0]; }
  @Override public void registerInvalidClass(String name) {}
  @Override public boolean isClassLoaded(String name) { return false; }
  @Override public String getClassRestrictions(String name) { return ""; }
  @Override public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
  @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
    if (name.startsWith("net.minecraft.") || name.startsWith("com.atsuishio.")) throw new ClassNotFoundException("metadata only: " + name);
    return Class.forName(name, initialize, getClass().getClassLoader());
  }
  @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name, initialize); }
  @Override public InputStream getResourceAsStream(String name) {
    try { return new ByteArrayInputStream(testBytes(name)); } catch (IOException exception) { return null; }
  }
  @Override public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException { return getClassNode(name, true); }
  @Override public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
    ClassNode node = new ClassNode();
    new ClassReader(testBytes(name.replace('.', '/') + ".class")).accept(node, 0);
    if (broken && name.endsWith("GunShotAdmissionMixin")) {
      for (var method : node.methods) if (method.name.equals("dnv$worldAdmission")) {
        method.desc = method.desc.replace("Lnet/minecraft/world/entity/Entity;", "Ljava/lang/String;");
      }
    }
    if (broken && name.endsWith("BlizzardDynamicLightMixin")) {
      for (var method : node.methods) if (method.name.equals("dnv$sourceEmission")) {
        for (var annotation : method.visibleAnnotations) if (annotation.desc.endsWith("/Redirect;")) {
          int at = annotation.values.indexOf("at") + 1;
          var point = (org.objectweb.asm.tree.AnnotationNode) annotation.values.get(at);
          int target = point.values.indexOf("target") + 1;
          point.values.set(target, point.values.get(target).toString().replace("getLightEmission(", "missingLightEmission("));
        }
      }
    }
    return node;
  }
  @Override public IPropertyKey resolveKey(String name) { return new Key(name); }
  @SuppressWarnings("unchecked") @Override public <T> T getProperty(IPropertyKey key) { return (T) PROPERTIES.get(key); }
  @Override public void setProperty(IPropertyKey key, Object value) { PROPERTIES.put(key, value); }
  @SuppressWarnings("unchecked") @Override public <T> T getProperty(IPropertyKey key, T fallback) { return (T) PROPERTIES.getOrDefault(key, fallback); }
  @Override public String getPropertyString(IPropertyKey key, String fallback) { return String.valueOf(PROPERTIES.getOrDefault(key, fallback)); }
  private record Key(String name) implements IPropertyKey {}
}
