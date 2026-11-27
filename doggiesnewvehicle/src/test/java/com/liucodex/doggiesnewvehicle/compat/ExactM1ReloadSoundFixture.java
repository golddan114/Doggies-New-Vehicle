package com.liucodex.doggiesnewvehicle.compat;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Runs exact upstream gunTick/startReload/finishReload, optionally after the real Mixin engine.
 * World/sound sinks, GunData storage, inventory and unrelated gun helpers are explicit doubles.
 * The native countdown/sound comparison and production Mixin bodies are never reimplemented.
 */
public final class ExactM1ReloadSoundFixture extends ClassLoader {
  private static final String SBW = "com/atsuishio/superbwarfare/";
  private static final String HANDLER = SBW + "event/GunEventHandler";
  private static final String PROBE = "com/liucodex/doggiesnewvehicle/compat/reloadprobe/Handler";
  private final Object handler;
  private final Method tick;

  ExactM1ReloadSoundFixture(Path temp, boolean patched) throws Exception {
    super(ExactM1ReloadSoundFixture.class.getClassLoader());
    byte[] bytes;
    if (patched) {
      var application = new DnvMuzzleMixinApplicationTest();
      application.temp = temp;
      try (var engine = application.loader()) {
        bytes = DnvMuzzleMixinApplicationTest.transform(engine, "event/GunEventHandler",
            "M1ReloadSoundMixin", "SERVER", false)[1];
      }
    } else {
      try (var zip = new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
        bytes = zip.getInputStream(zip.getEntry(HANDLER + ".class")).readAllBytes();
      }
    }
    ClassNode source = new ClassNode();
    new ClassReader(bytes).accept(source, ClassReader.SKIP_FRAMES);
    source.superName = Type.getInternalName(OtherGunHelpers.class);
    source.fields.clear();
    source.innerClasses.clear();
    source.nestMembers = null;
    source.methods.removeIf(method -> !(method.name.equals("gunTick") && method.desc.endsWith("ZD)V"))
        && !Set.of("startReload", "finishReload").contains(method.name) && !method.name.contains("dnv$"));
    MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
        source.superName, "<init>", "()V", false));
    constructor.instructions.add(new InsnNode(Opcodes.RETURN));
    source.methods.add(constructor);
    Map<String, String> names = new HashMap<>();
    names.put(HANDLER, PROBE);
    Map.ofEntries(
        Map.entry("data/gun/GunData", Data.class), Map.entry("data/gun/GunProp", Prop.class),
        Map.entry("data/gun/SoundInfo", Sound.class), Map.entry("data/gun/subdata/Reload", Reload.class),
        Map.entry("data/gun/value/IntValue", IntValue.class), Map.entry("data/gun/value/BooleanValue", BoolValue.class),
        Map.entry("data/gun/value/Starter", Starter.class), Map.entry("data/gun/value/ReloadState", State.class),
        Map.entry("data/gun/NbtVersion", NbtVersion.class), Map.entry("item/gun/GunItem", Gun.class),
        Map.entry("entity/vehicle/base/VehicleEntity", Vehicle.class), Map.entry("tools/SoundTool", Sounds.class),
        Map.entry("tools/MinecraftUtil", Events.class), Map.entry("api/event/ReloadEvent$Pre", ReloadEvent.class))
        .forEach((name, type) -> names.put(SBW + name, Type.getInternalName(type)));
    Map.of("Entity", Body.class, "player/Player", Passenger.class).forEach((name, type) ->
        names.put("net/minecraft/world/entity/" + name, Type.getInternalName(type)));
    names.put("net/minecraft/server/level/ServerPlayer", Type.getInternalName(Passenger.class));
    names.put("net/minecraft/world/level/Level", Type.getInternalName(World.class));
    names.put("net/minecraft/core/BlockPos", Type.getInternalName(Position.class));
    names.put("net/minecraft/sounds/SoundEvent", Type.getInternalName(SoundEvent.class));
    names.put("net/minecraft/sounds/SoundSource", Type.getInternalName(SoundSource.class));
    names.put("net/minecraft/util/Mth", Type.getInternalName(Maths.class));
    names.put("net/minecraftforge/eventbus/api/Event", Type.getInternalName(Event.class));
    names.put("kotlin/jvm/internal/Intrinsics", Type.getInternalName(Intrinsics.class));
    names.put("com/liucodex/doggiesnewvehicle/entity/DnvM1YunlongEntity", Type.getInternalName(M1.class));
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
      @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
    };
    source.accept(new ClassRemapper(writer, new Remapper() {
      @Override public String map(String name) { return names.getOrDefault(name, name); }
    }));
    byte[] probe = writer.toByteArray();
    Class<?> type = defineClass(PROBE.replace('/', '.'), probe, 0, probe.length);
    handler = type.getConstructor().newInstance();
    tick = type.getMethod("gunTick", Body.class, Data.class, boolean.class, double.class);
  }

  void tick(Body entity, Data data) throws Exception { tick.invoke(handler, entity, data, true, 1.0D); }
  void idle(Body entity, Data data, int ticks) throws Exception {
    for (int i = 0; i < ticks; i++) tick(entity, data);
  }

  public static class Body {}
  public static class Vehicle extends Body {
    public final World world = new World();
    public final List<Body> passengers = new ArrayList<>();
    public List<Body> m_20197_() { return passengers; }
    public World m_9236_() { return world; }
    public Position m_20097_() { return new Position(); }
    public Position blockPosition() { return new Position(); }
  }
  public static final class M1 extends Vehicle {}
  public static final class Passenger extends Body { public int sounds; }
  public static final class Position {}
  public static final class SoundEvent {}
  public enum SoundSource { PLAYERS }
  public static final class World {
    public int sounds;
    public void m_245803_(Body source, Position pos, SoundEvent event, SoundSource category, float volume, float pitch) {
      sounds++;
    }
  }
  public static final class Sounds {
    public static void playLocalSound(Passenger passenger, SoundEvent sound, float volume, float pitch) { passenger.sounds++; }
  }
  public static final class Maths {
    // Both mapped names implement the exact vanilla int clamp, including inverted bounds.
    public static int m_14045_(int value, int min, int max) { return value < min ? min : Math.min(value, max); }
    public static int clamp(int value, int min, int max) { return m_14045_(value, min, max); }
  }
  public static final class Intrinsics {
    public static void checkNotNullParameter(Object value, String name) { java.util.Objects.requireNonNull(value, name); }
    public static void checkNotNull(Object value) { java.util.Objects.requireNonNull(value); }
  }
  public static class Event {}
  public static final class ReloadEvent extends Event { public ReloadEvent(Body entity, Data data) {} }
  public static final class Events { public static boolean postEvent(Event event) { return false; } }
  public enum Prop { SOUND_INFO, EMPTY_RELOAD_TIME, NORMAL_RELOAD_TIME }
  public enum State { NOT_RELOADING, NORMAL_RELOADING, EMPTY_RELOADING }
  public static final class Sound {
    public SoundEvent vehicleReload = new SoundEvent();
    public int cue = 30;
    public int getVehicleReloadSoundTime() { return cue; }
    public SoundEvent getVehicleReload3p() { return vehicleReload; }
  }
  public static final class IntValue {
    public int value;
    public int get() { return value; }
    public void set(int value) { this.value = value; }
  }
  public static final class BoolValue { public void set(boolean value) {} }
  public static final class NbtVersion { public void invalidateStructural() {} }
  public static final class Starter {
    public boolean requested;
    public boolean start() { boolean value = requested; requested = false; return value; }
    public void finish() { requested = false; }
  }
  public static final class Reload {
    public int remaining;
    public State state = State.NOT_RELOADING;
    public Starter reloadStarter = new Starter();
    public int time() { return remaining; }
    public void setTime(int value) { remaining = value; }
    public void reduce() { remaining = Math.max(0, remaining - 1); }
    public void setState(State value) { state = value; }
  }
  public static final class Gun {
    public boolean openBolt;
    public Map<Integer, Consumer<Data>> reloadTimeBehaviors = Map.of();
    public boolean isOpenBolt(Data data) { return openBolt; }
    public int hideBulletChainBelowShots() { return 0; }
    public void whenNoAmmo(Data data) {}
    public void tick(Body entity, Data data, boolean selected) {}
  }
  public static final class Data {
    public final Reload reload = new Reload();
    public final Sound sound = new Sound();
    public final Gun item = new Gun();
    public final NbtVersion nbtVersion = new NbtVersion();
    public final IntValue shootAnimationTimer = new IntValue(), shootTimer = new IntValue();
    public final BoolValue hideBulletChain = new BoolValue();
    public int duration = 200, magazine = 1, rpm = 60, ammo, completions, saves;
    public Object get(Prop prop) { return prop == Prop.SOUND_INFO ? sound : duration; }
    public boolean reloading() { return reload.state != State.NOT_RELOADING; }
    public int currentAvailableShots(Body entity) { return ammo; }
    public boolean hasEnoughAmmoToShoot(Body entity) { return ammo > 0; }
    public void save() { saves++; }
  }
  public static class OtherGunHelpers {
    public void init(Body entity, Data data) {}
    public void autoReload(Body entity, Data data, boolean selected) {}
    public void tickPerk(Body entity, Data data) {}
    public void handleCooldown(Body entity, Data data, Double rate) {}
    public void redrawExtraAmmo(Body entity, Data data) {}
    public void handleGunBolt(Data data) {}
    public void handleGunSingleReload(Body entity, Data data) {}
    public void handleSentinelCharge(Body entity, Data data) {}
    public void playGunEmptyReloadSounds(Body entity, Data data) {}
    public void playGunNormalReloadSounds(Body entity, Data data) {}
    public void finishGunEmptyReload(Body entity, Data data) { data.completions++; data.ammo = data.magazine; }
    public void finishGunNormalReload(Body entity, Data data) { finishGunEmptyReload(entity, data); }
  }
}
