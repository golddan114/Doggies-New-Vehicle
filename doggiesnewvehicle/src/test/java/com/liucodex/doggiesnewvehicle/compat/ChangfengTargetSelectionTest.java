package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.core.DnvTurretTargetMemory;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/** Executes compiled acquisition, bounded selection and sight caching; world/control are ports. */
class ChangfengTargetSelectionTest {
  @ParameterizedTest @ValueSource(booleans = {false, true})
  void hiddenPriorityTargetCannotLockOrStarveAVisibleCandidate(boolean storm) throws Exception {
    Base cannon = create(storm);
    Target hidden = target(cannon, false), visible = target(cannon, true);
    cannon.targetCandidates.targets = List.of(hidden, visible);
    assertNull(acquire(cannon));
    assertEquals("none", cannon.data.value);
    assertEquals(0, cannon.energySpent);
    cannon.tickCount++;
    assertSame(visible, acquire(cannon));
    assertEquals(visible.id.toString(), cannon.data.value);
    assertEquals(7, cannon.energySpent, "deferred acquisition still pays once");
    assertEquals(2, cannon.world.rays);
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void deferredOldLockWaitsButConfirmedObstructionAndInvalidRelationReleaseIt(boolean storm) throws Exception {
    Base cannon = create(storm);
    Target target = target(cannon, true);
    cannon.targetCandidates.targets = List.of(target);
    assertSame(target, acquire(cannon));
    cannon.tickCount += 20;
    cannon.world.deferred = true;
    assertSame(target, acquire(cannon));
    assertEquals(target.id.toString(), cannon.data.value);
    cannon.world.deferred = false;
    target.clear = false;
    cannon.tickCount++;
    assertNull(acquire(cannon));
    assertEquals("none", cannon.data.value);
    cannon.world.deferred = true;
    target.allowed = false;
    assertNull(acquire(cannon));
    assertEquals(7, cannon.energySpent);
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void explicitCommandKeepsItsExistingPriorityAndUnloadedAutonomyDoesNotRaycast(boolean storm) throws Exception {
    Base cannon = create(storm);
    Target target = target(cannon, false);
    cannon.turretExecution.command = new DnvTurretTargetMemory.CommandObservation<>(
        Optional.of(target), 1, 1000, true, DnvTurretTargetMemory.CommandMode.ACTIVE, "COMMAND");
    assertSame(target, acquire(cannon));
    assertEquals(DnvTurretTargetMemory.Source.EXPLICIT_COMMAND,
        cannon.targetMemory.current().target().orElseThrow().source());
    assertEquals(0, cannon.world.rays);
    cannon.turretExecution.command = noCommand();
    cannon.world.loaded = false;
    assertNull(acquire(cannon));
    assertEquals(0, cannon.world.rays);
  }

  private static Target target(Base base, boolean clear) {
    Target target = new Target(); target.clear = clear;
    base.world.targets.put(target.id, target);
    return target;
  }
  private static Object acquire(Base base) throws Exception {
    return base.getClass().getMethod("resolveOrAcquireTarget", World.class, Vec3.class, Seek.class, long.class)
        .invoke(base, base.world, Vec3.ZERO, new Seek(), 0L);
  }
  private static DnvTurretTargetMemory.CommandObservation<Target> noCommand() {
    return new DnvTurretTargetMemory.CommandObservation<>(Optional.empty(), -1, -1, false,
        DnvTurretTargetMemory.CommandMode.NONE, "NONE");
  }

  private static Base create(boolean storm) throws Exception {
    String original = "com/liucodex/doggiesnewvehicle/entity/" + (storm ? "StormTurretEntity" : "ChangfengCannonEntity");
    String probe = "com/liucodex/doggiesnewvehicle/compat/changfengprobe/Cannon";
    ClassNode source = new ClassNode();
    try (var in = ChangfengTargetSelectionTest.class.getResourceAsStream("/" + original + ".class")) {
      new ClassReader(in).accept(source, ClassReader.SKIP_FRAMES);
    }
    source.interfaces.clear(); source.fields.clear(); source.innerClasses.clear();
    source.nestMembers = null;
    source.methods.removeIf(m -> !Set.of("resolveOrAcquireTarget", "seekCandidate", "targetPosition",
        "later", "hasCachedLineOfSight").contains(m.name)
        && !m.name.startsWith("lambda$resolveOrAcquireTarget$") && !m.name.startsWith("lambda$seekCandidate$"));
    source.methods.forEach(m -> m.access = (m.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC);
    var init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, source.superName, "<init>", "()V", false));
    init.instructions.add(new InsnNode(Opcodes.RETURN)); source.methods.add(init);
    Map<String, String> names = new HashMap<>();
    names.put(original, probe);
    names.put(source.superName, Type.getInternalName(Base.class));
    names.put("net/minecraft/world/entity/Entity", Type.getInternalName(Target.class));
    names.put("net/minecraft/world/level/Level", Type.getInternalName(World.class));
    names.put("net/minecraft/server/level/ServerLevel", Type.getInternalName(World.class));
    names.put("net/minecraft/network/syncher/SynchedEntityData", Type.getInternalName(Data.class));
    names.put("net/minecraft/network/syncher/EntityDataAccessor", Type.getInternalName(Accessor.class));
    names.put("com/atsuishio/superbwarfare/data/vehicle/subdata/SeekInfo", Type.getInternalName(Seek.class));
    names.put("com/liucodex/doggiesnewvehicle/compat/DnvSbwTurretExecution", Type.getInternalName(Execution.class));
    names.put("com/liucodex/doggiesnewvehicle/compat/DnvTargetCandidateCache", Type.getInternalName(Cache.class));
    names.put("com/liucodex/doggiesnewvehicle/compat/DnvTargetingBudget", Type.getInternalName(Budget.class));
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
      @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
    };
    source.accept(new ClassRemapper(writer, new Remapper() {
      @Override public String map(String name) { return names.getOrDefault(name, name); }
    }));
    byte[] bytes = writer.toByteArray();
    Class<?> type = new ClassLoader(ChangfengTargetSelectionTest.class.getClassLoader()) {
      Class<?> loadProbe() { return defineClass(probe.replace('/', '.'), bytes, 0, bytes.length); }
    }.loadProbe();
    return (Base) type.getConstructor().newInstance();
  }

  public static class Target {
    private static long sequence;
    public final UUID id = new UUID(0, ++sequence);
    public boolean clear = true, allowed = true;
    public UUID getUUID() { return id; }
    public String getStringUUID() { return id.toString(); }
    public Vec3 position() { return new Vec3(20, 2, 0); }
    public AABB getBoundingBox() { return new AABB(position(), position().add(1, 2, 1)); }
    public double getEyeY() { return 3.7; }
    public double distanceToSqr(Vec3 origin) { return position().distanceToSqr(origin); }
  }
  public static class Base extends Target {
    public final World world = new World();
    public final Cache targetCandidates = new Cache();
    public final Execution turretExecution = new Execution();
    public final DnvTurretTargetMemory targetMemory = new DnvTurretTargetMemory();
    public final Data data = new Data();
    public static final Accessor TARGET_UUID = new Accessor();
    public int tickCount = 100, candidateProbeCursor, energySpent;
    public long autonomousObservationRevision;
    public String trackedTargetId = "none";
    public DnvSightObservation geometrySight;
    public boolean geometryDeferred;
    public Base() { world.source = this; }
    public World level() { return world; }
    public Data getEntityData() { return data; }
    public float getTurretYRot() { return 0; }
    public float getTurretXRot() { return 0; }
    public float getYRot() { return 0; }
    public int getId() { return 1; }
    public Target findTarget(String id) { return "none".equals(id) ? null : world.targets.get(UUID.fromString(id)); }
    public boolean isCandidate(Target target, Vec3 muzzle, Seek seek) { return target.allowed; }
    public double targetPriorityScore(Target target, Vec3 muzzle, Seek seek) {
      return targetCandidates.targets.size() - targetCandidates.targets.indexOf(target);
    }
    public boolean checkNoClip(Target target, Vec3 muzzle) { world.rays++; return target.clear; }
    public void consumeEnergy(int amount) { energySpent += amount; }
    public void beginTracking(Target target) { trackedTargetId = target.id.toString(); }
    public void clearExecutionTracking() { data.value = trackedTargetId = "none"; geometrySight = null; }
  }
  public static class World {
    public Base source;
    public boolean loaded = true, deferred;
    public int rays, lastGeometryTick = -1;
    public final Map<UUID, Target> targets = new HashMap<>();
    public long getGameTime() { return source.tickCount; }
    public Target getEntity(UUID id) { return targets.get(id); }
    public boolean hasChunksAt(BlockPos from, BlockPos to) { return loaded; }
  }
  public static class Accessor { }
  public static class Data {
    public String value = "none";
    public Object get(Accessor key) { return value; }
    public void set(Accessor key, Object value) { this.value = (String) value; }
  }
  public static class Seek {
    public double getMaxSeekRange() { return 96; }
    public int getSeekIterative() { return 20; }
    public int getSeekEnergyCost() { return 7; }
  }
  public static class Execution {
    public DnvTurretTargetMemory.CommandObservation<Target> command = noCommand();
    public DnvTurretTargetMemory.CommandObservation<Target> targetCommand() { return command; }
  }
  public static class Cache {
    public List<Target> targets = List.of();
    public boolean refreshIfDue(Target source, double range, java.util.function.Predicate<Target> filter, int interval) { return false; }
    public List<Target> candidates(Target source) { return targets; }
    public static int compareStableIds(UUID left, UUID right) { return left.compareTo(right); }
  }
  public static class Budget {
    public static int fullScanInterval(int value) { return 20; }
    public static int nextCheckTick(int tick, int id, int interval, int salt) { return tick + interval; }
    public static boolean admitGeometryCheck(World world, UUID owner) {
      if (world.deferred || world.lastGeometryTick == world.source.tickCount) return false;
      world.lastGeometryTick = world.source.tickCount;
      return true;
    }
  }
}
