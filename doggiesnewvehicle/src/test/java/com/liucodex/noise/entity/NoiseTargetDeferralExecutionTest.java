package com.liucodex.noise.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.compat.DnvTargetCandidateCache;
import com.liucodex.doggiesnewvehicle.core.DnvLazyCandidateFrame;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

/** Executes the compiled selector; world lookup and geometry admission are controlled ports. */
class NoiseTargetDeferralExecutionTest {
  @Test
  void deferredGeometryPreservesIdentityButSuspendsLiveTrackingAndLockProgress() throws Exception {
    Base boss = create();
    Actor target = boss.world.target;
    Station station = new Station();
    station.targetId = target.id;
    try (var frame = new DnvLazyCandidateFrame<Actor>(() -> List.of(target))) {
      boss.geometryAllowed = false;
      station.settledTicks = 12;
      assertNull(boss.resolveStationTarget(station, Vec3.ZERO, 8, 80, frame),
          "An unobserved live entity must not reach aiming or VLS progress");
      assertEquals(12, station.settledTicks, "Deferral pauses the old progress until sight is checked");
      assertEquals(target.id, station.targetId);
      assertTrue(station.geometryDeferred);

      boss.geometryAllowed = true;
      assertSame(target, boss.resolveStationTarget(station, Vec3.ZERO, 8, 80, frame));
      target.legal = false;
      boss.geometryAllowed = false;
      assertNull(boss.resolveStationTarget(station, Vec3.ZERO, 8, 80, frame));
      assertNull(station.targetId, "A team/range rejection must not keep the old lock");
    }
  }

  private static Base create() throws Exception {
    String root = "com/liucodex/noise/entity/";
    String subjectName = root + "ExecutedNoiseTargetDeferral";
    ClassNode source = new ClassNode();
    try (InputStream stream = NoiseTargetDeferralExecutionTest.class.getClassLoader()
        .getResourceAsStream(root + "AbstractNoiseVehicleWeapons.class")) {
      assertNotNull(stream);
      new ClassReader(stream).accept(source, 0);
    }
    ClassNode subject = new ClassNode();
    subject.version = Opcodes.V17;
    subject.access = Opcodes.ACC_PUBLIC;
    subject.name = subjectName;
    subject.superName = Type.getInternalName(Base.class);
    for (MethodNode method : source.methods) {
      if (method.name.equals("resolveStationTarget")
          || method.name.startsWith("lambda$resolveStationTarget$")) {
        method.access = (method.access & ~(Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE)) | Opcodes.ACC_PUBLIC;
        subject.methods.add(method);
      }
    }
    MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
        subject.superName, "<init>", "()V", false));
    constructor.instructions.add(new InsnNode(Opcodes.RETURN));
    constructor.maxStack = 1;
    constructor.maxLocals = 1;
    subject.methods.add(constructor);
    Map<String, String> names = new HashMap<>();
    names.put(root + "AbstractNoiseVehicleWeapons", subjectName);
    for (String parent : new String[]{"State", "Contract", "Lifecycle", "Navigation"})
      names.put(root + "AbstractNoiseVehicle" + parent, subject.superName);
    names.put(root + "AbstractNoiseVehicleState$WeaponStation", Type.getInternalName(Station.class));
    names.put("net/minecraft/world/entity/Entity", Type.getInternalName(Actor.class));
    names.put("net/minecraft/world/level/Level", Type.getInternalName(World.class));
    names.put("net/minecraft/server/level/ServerLevel", Type.getInternalName(ServerWorld.class));
    names.put(Type.getInternalName(DnvTargetCandidateCache.class), Type.getInternalName(Cache.class));
    ClassWriter writer = new ClassWriter(0);
    subject.accept(new ClassRemapper(writer, new SimpleRemapper(names)));
    byte[] bytes = writer.toByteArray();
    Class<?> compiled = new ClassLoader(NoiseTargetDeferralExecutionTest.class.getClassLoader()) {
      Class<?> define() { return defineClass(subjectName.replace('/', '.'), bytes, 0, bytes.length); }
    }.define();
    return (Base) compiled.getConstructor().newInstance();
  }

  public static class Actor {
    public final UUID id = new UUID(0, 1);
    public boolean legal = true;
    public UUID getUUID() { return id; }
    public double distanceToSqr(Vec3 muzzle) { return 100; }
  }
  public static class World { }
  public static class ServerWorld extends World {
    public final Actor target = new Actor();
    public Actor getEntity(UUID id) { return target.id.equals(id) ? target : null; }
  }
  public static class Station {
    public UUID targetId;
    public boolean geometryDeferred;
    public int candidateProbeCursor, settledTicks;
  }
  public static class Cache {
    public boolean refreshedAt(int tick) { return false; }
    public static <T> T selectBest(List<T> live, T current, Predicate<T> eligible,
        ToDoubleFunction<T> distance, Function<T, UUID> identity) {
      return DnvTargetCandidateCache.selectBest(live, current, eligible, distance, identity);
    }
  }
  public static class Base extends Actor {
    public final ServerWorld world = new ServerWorld();
    public final Cache targetCandidates = new Cache();
    public int tickCount;
    public boolean geometryAllowed;
    public World level() { return world; }
    public boolean isValidStationTarget(Station station, Actor actor, Vec3 muzzle,
        double minimumRange, double maximumRange) {
      if (actor == null || !actor.legal) return false;
      station.geometryDeferred = !geometryAllowed;
      return geometryAllowed;
    }
    public Actor resolveStationTarget(Station station, Vec3 muzzle, double minimumRange,
        double maximumRange, DnvLazyCandidateFrame<Actor> frame) {
      throw new AssertionError("Compiled production method must override this port");
    }
  }
}
