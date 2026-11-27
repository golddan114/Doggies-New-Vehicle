package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point;
import com.liucodex.doggiesnewvehicle.core.DnvTurretExecutionPort;
import com.liucodex.doggiesnewvehicle.core.DnvTurretTargetMemory;
import com.liucodex.doggiesnewvehicle.api.combat.DnvCombatPolicyProvider.LossBehavior;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/** Production adapter, hooks, module/reload/release/path/NBT method bodies; explicit world/SBW doubles. */
public final class JingzheExecutionFixture {
  private static final String ROOT = "com/liucodex/doggiesnewvehicle/";
  private static final String ADAPTER = ROOT + "compat/DnvJingzheTurretExecution";
  private static final String DEPLOY = ROOT + "entity/AbstractJingzheTurretDeployment";
  private static final String EFFECTS = ROOT + "entity/AbstractJingzheTurretEffects";
  private static final String STATE = ROOT + "entity/AbstractJingzheTurretState";
  private static final String ENTITY = ROOT + "entity/JingzheTurretEntity";
  private static final String PROBE = ROOT + "compat/jingzheprobe/Vehicle";
  private static final String EXECUTION = ROOT + "compat/jingzheprobe/Execution";
  final World world = new World();
  final Target target = new Target();
  final Base vehicle;
  final Object execution;
  final ClassLoader loader;

  JingzheExecutionFixture() throws Exception {
    Map<String, byte[]> definitions = new HashMap<>();
    for (String suffix : List.of("", "$ModulePort")) {
      definitions.put(EXECUTION + suffix, remap(read(ADAPTER + suffix)));
    }
    definitions.put(PROBE + "$1", remap(read(DEPLOY + "$1")));
    definitions.put(PROBE + "Sight", remap(read(ROOT + "compat/DnvTargetLineOfSight")));
    ClassNode probe = node(read(DEPLOY));
    probe.methods.removeIf(m -> !Set.of("<init>", "tickCombatSystems", "tickModule", "startAffordableReloads",
            "invalidateExecution", "invalidateModuleExecution", "invalidateModuleTargetMemory",
            "invalidateTargetMemories", "targetDecisionSnapshot", "acquireTarget",
            "selectAutonomousTarget", "isTargetingState", "checkedModule", "createTargetMemories",
            "targetPosition", "later", "isPvpProtected", "hasVerticalDropPath", "isAttackTargetValid",
            "isCandidate", "isCheapModuleTargetValid", "candidateView", "hasAttackPath",
            "exploratoryPathClear", "isModuleTargetValid", "initializeModuleValidation").contains(m.name)
        && !m.name.startsWith("lambda$acquireTarget$"));
    for (var method : node(read(EFFECTS)).methods) {
      if (Set.of("releaseAttack", "resetModuleToReady", "clearTransientTargets", "clearModuleValidation",
          "tickPendingSecondaryStrikes", "detonateSecondaryStrike", "applyImpactAreaDamage",
          "distanceToAabb").contains(method.name)) probe.methods.add(method);
    }
    for (var method : node(read(ENTITY)).methods) {
      if (Set.of("addAdditionalSaveData", "readAdditionalSaveData").contains(method.name)) probe.methods.add(method);
    }
    probe.access = Opcodes.ACC_PUBLIC;
    for (var method : probe.methods) {
      method.access = (method.access & ~(Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE))
          | Opcodes.ACC_PUBLIC;
    }
    definitions.put(PROBE, remap(write(probe)));
    loader = new ClassLoader(getClass().getClassLoader()) {
      @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = definitions.get(name.replace('.', '/'));
        if (bytes == null) throw new ClassNotFoundException(name);
        return defineClass(name, bytes, 0, bytes.length);
      }
    };
    vehicle = (Base) loader.loadClass(PROBE.replace('/', '.')).getConstructor(Kind.class, World.class)
        .newInstance(new Kind("doggievehicle:jingzhe_turret"), world);
    var field = vehicle.getClass().getDeclaredField("execution");
    field.setAccessible(true);
    execution = field.get(vehicle);
    target.level = world;
    target.position = new Vec3(12, 3, 4);
    world.entities.put(target.id, target);
    world.entities.put(vehicle.id, vehicle);
    vehicle.candidate = target;
    vehicle.sharedCandidates = List.of(target);
    Coordinator.allowed = true;
    Coordinator.stamp = new Object();
    Coordinator.beforeAuthorization = () -> {};
    Coordinator.commandTarget = null;
    Coordinator.commandRevision = -1L;
    Coordinator.commandValidUntil = -1L;
    Coordinator.commandExplicit = false;
    Coordinator.commandLive = false;
    Coordinator.commandLoss = LossBehavior.NATIVE_FALLBACK;
  }

  DnvTurretExecutionPort port(int module) throws Exception {
    return (DnvTurretExecutionPort) execution.getClass().getMethod("module", int.class).invoke(execution, module);
  }
  void ready(int module) {
    vehicle.moduleState[module] = 4;
    vehicle.moduleTimer[module] = 12;
    vehicle.moduleCharge[module] = 1000;
    vehicle.moduleTarget[module] = target.id.toString();
  }
  DnvTurretExecutionPort.Frame aim(int module) throws Exception {
    var port = port(module);
    var frame = port.inspect(target.id);
    var result = port.applyAim(frame, new Point(0, -1, 0));
    org.junit.jupiter.api.Assertions.assertTrue(result.accepted(), result.reason());
    return frame;
  }
  void call(String name) throws Exception { vehicle.getClass().getMethod(name).invoke(vehicle); }
  void tick() throws Exception { world.now++; vehicle.tickCount++; call("tickCombatSystems"); }
  void secondaryTick() throws Exception { call("tickPendingSecondaryStrikes"); }
  void load(CompoundTag tag) throws Exception {
    vehicle.getClass().getMethod("readAdditionalSaveData", CompoundTag.class).invoke(vehicle, tag);
  }
  CompoundTag save() throws Exception {
    var tag = new CompoundTag();
    vehicle.getClass().getMethod("addAdditionalSaveData", CompoundTag.class).invoke(vehicle, tag);
    return tag;
  }
  DnvTurretTargetMemory.Decision decision(int module) throws Exception {
    return (DnvTurretTargetMemory.Decision) vehicle.getClass()
        .getMethod("targetDecisionSnapshot", int.class).invoke(vehicle, module);
  }
  void impact() throws Exception {
    vehicle.getClass().getMethod("applyImpactAreaDamage", World.class, Vec3.class).invoke(vehicle, world, target.position);
  }

  private static byte[] read(String name) throws Exception {
    try (var in = JingzheExecutionFixture.class.getResourceAsStream("/" + name + ".class")) { return in.readAllBytes(); }
  }
  private static ClassNode node(byte[] bytes) {
    ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
  }
  private static byte[] remap(byte[] bytes) {
    Map<String, String> map = new HashMap<>();
    map.put(DEPLOY, PROBE);
    map.put(DEPLOY + "$1", PROBE + "$1");
    map.put(ADAPTER, EXECUTION);
    map.put(ADAPTER + "$ModulePort", EXECUTION + "$ModulePort");
    map.put(STATE, Type.getInternalName(Base.class));
    map.put(EFFECTS, Type.getInternalName(Base.class));
    map.put(ENTITY, PROBE);
    map.put(STATE + "$SecondaryStrike", Type.getInternalName(Secondary.class));
    map.put(STATE + "$GroundFlameEffect", Type.getInternalName(GroundFlame.class));
    map.put(ROOT + "compat/DnvCombatCoordinator", Type.getInternalName(Coordinator.class));
    map.put(ROOT + "compat/DnvCombatCoordinator$TargetCommand", Type.getInternalName(Command.class));
    map.put(ROOT + "compat/DnvCombatPolicyRuntime", Type.getInternalName(Policy.class));
    map.put(ROOT + "compat/DnvTargetExclusions", Type.getInternalName(Exclusions.class));
    map.put(ROOT + "compat/DnvTargetingBudget", Type.getInternalName(Budget.class));
    map.put(ROOT + "compat/DnvTargetLineOfSight", PROBE + "Sight");
    map.put(ROOT + "core/DnvDirectArmorPenetration", Type.getInternalName(DirectDamage.class));
    String sbw = "com/atsuishio/superbwarfare/";
    map.put(sbw + "entity/vehicle/base/AutoAimableEntity", Type.getInternalName(Base.class));
    map.put(sbw + "tools/EntityFindUtil", Type.getInternalName(EntityFind.class));
    map.put(sbw + "tools/SeekTool", Type.getInternalName(SeekTool.class));
    map.put(sbw + "entity/vehicle/ai/TowerAI$TeamResolver", Type.getInternalName(TeamResolver.class));
    map.put(sbw + "entity/living/TargetEntity", Type.getInternalName(Dummy.class));
    map.put(sbw + "data/vehicle/VehicleData", Type.getInternalName(VehicleData.class));
    map.put(sbw + "data/vehicle/DefaultVehicleData", Type.getInternalName(Config.class));
    map.put(sbw + "data/vehicle/subdata/SeekInfo", Type.getInternalName(Seek.class));
    Map<String, Class<?>> types = Map.ofEntries(
        Map.entry("world/entity/Entity", Target.class), Map.entry("world/entity/LivingEntity", Target.class),
        Map.entry("world/entity/projectile/Projectile", Dummy.class), Map.entry("world/entity/monster/Enemy", Enemy.class),
        Map.entry("world/entity/EntityType", Kind.class), Map.entry("world/entity/LightningBolt", Lightning.class),
        Map.entry("world/entity/player/Player", Target.class), Map.entry("world/level/Level", World.class),
        Map.entry("server/level/ServerLevel", World.class), Map.entry("resources/ResourceKey", Dimension.class),
        Map.entry("core/registries/BuiltInRegistries", Registries.class), Map.entry("core/DefaultedRegistry", Registry.class),
        Map.entry("core/registries/Registries", Registries.class), Map.entry("core/RegistryAccess", RegistryAccess.class),
        Map.entry("core/Registry", DamageRegistry.class), Map.entry("core/Holder", DamageHolder.class),
        Map.entry("core/Holder$Reference", DamageHolder.class),
        Map.entry("world/damagesource/DamageType", DamageKind.class), Map.entry("world/damagesource/DamageTypes", DamageTypes.class),
        Map.entry("world/level/ClipContext", Clip.class), Map.entry("world/level/ClipContext$Block", ClipBlock.class),
        Map.entry("world/level/ClipContext$Fluid", ClipFluid.class), Map.entry("world/phys/BlockHitResult", Hit.class),
        Map.entry("world/phys/HitResult$Type", HitKind.class), Map.entry("sounds/SoundEvents", Sounds.class),
        Map.entry("sounds/SoundEvent", Sound.class), Map.entry("sounds/SoundSource", Channel.class),
        Map.entry("core/particles/ParticleTypes", Particles.class), Map.entry("core/particles/SimpleParticleType", Particle.class),
        Map.entry("core/particles/ParticleOptions", Particle.class), Map.entry("world/damagesource/DamageSources", DamageSources.class),
        Map.entry("world/damagesource/DamageSource", Damage.class), Map.entry("network/syncher/EntityDataAccessor", Accessor.class),
        Map.entry("network/syncher/SynchedEntityData", SynchedData.class));
    types.forEach((name, type) -> map.put("net/minecraft/" + name, Type.getInternalName(type)));
    ClassNode result = new ClassNode();
    new ClassReader(bytes).accept(new ClassRemapper(result, new Remapper() {
      @Override public String map(String name) { return map.getOrDefault(name, name); }
    }), ClassReader.SKIP_FRAMES);
    // The retained static helper moved with its unchanged body into the isolated probe.
    for (var method : result.methods) for (var instruction : method.instructions.toArray()) {
      if (instruction instanceof MethodInsnNode call && call.owner.equals(Type.getInternalName(Base.class))
          && Set.of("detonateSecondaryStrike", "applyImpactAreaDamage", "distanceToAabb")
              .contains(call.name)) call.owner = PROBE;
      if (instruction instanceof MethodInsnNode call && call.owner.equals(Type.getInternalName(Base.class))
          && Set.of("invalidateModuleTargetMemory", "invalidateTargetMemories").contains(call.name)) {
        call.owner = PROBE;
      }
    }
    return write(result);
  }
  private static byte[] write(ClassNode node) {
    var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
      @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
    };
    node.accept(writer); return writer.toByteArray();
  }

  public static class Target {
    public final UUID id = UUID.randomUUID();
    public World level;
    public Vec3 position = Vec3.ZERO;
    public boolean removed, pvp, immune, allied;
    public int hits;
    public float damage;
    public Damage lastSource;
    public Runnable duringHurt = () -> {};
    public UUID getUUID() { return id; }
    public String getStringUUID() { return id.toString(); }
    public World level() { return level; }
    public boolean isRemoved() { return removed; }
    public boolean isAlive() { return !removed; }
    public float getHealth() { return isAlive() ? 100 : 0; }
    public boolean isAlliedTo(Target other) { return other.allied; }
    public double distanceToSqr(Target other) { return position.distanceToSqr(other.position); }
    public Vec3 position() { return position; }
    public double getEyeY() { return position.y + 1.7D; }
    public AABB getBoundingBox() { return new AABB(position, position.add(1, 2, 1)); }
    public boolean hurt(Damage source, float amount) {
      lastSource = source; hits++; damage += immune ? 0 : amount; duringHurt.run(); return !immune;
    }
  }
  public static class Base extends Target {
    public final int[] moduleState = new int[5], moduleTimer = new int[5], moduleCharge = new int[5];
    public final boolean[] moduleEnergyReserved = new boolean[5];
    public final String[] moduleTarget = {"none", "none", "none", "none", "none"};
    public final String[] moduleValidationTarget = {"none", "none", "none", "none", "none"};
    public final int[] nextModuleRelationCheckTick = new int[5];
    public final boolean[] cachedModuleRelationAllowed = new boolean[5];
    public final DnvSightObservation[] moduleSight = new DnvSightObservation[5];
    public final boolean[] modulePathDeferred = new boolean[5];
    public final DnvTargetCandidateCache targetCandidates = new DnvTargetCandidateCache(20);
    public List<Target> sharedCandidates = List.of();
    public final List<Secondary> pendingSecondaryStrikes = new ArrayList<>();
    public final List<Object> groundFlameEffects = new ArrayList<>();
    public int tickCount = 100, lastReleaseTick = -10000, lastLockStartTick = -10000, nextReloadIndex;
    public int candidateViewReads;
    public int candidateProbeCursor;
    public int energy = 1_000_000, energyWrites, effects, publishes, locks;
    public boolean active = true, deployed = true, targetAllowed = true, pathDeferred, wreck;
    public Target owner, passenger, candidate;
    public Runnable duringRelation = () -> {};
    public Kind type;
    public VehicleData vehicleData = new VehicleData();
    public static final Accessor DEPLOY_PHASE = new Accessor(), PHASE_TICK = new Accessor(), ORBIT_ANGLE = new Accessor();
    public final SynchedData synced = new SynchedData();
    public Base(Kind type, World world) { this.type = type; level = world; synced.set(DEPLOY_PHASE, 6); synced.set(PHASE_TICK, 0); synced.set(ORBIT_ANGLE, 0F); }
    public Kind getType() { return type; }
    public int getId() { return 1; }
    public UUID moduleGeometryOwner(int module) { return new UUID(id.getMostSignificantBits(), module); }
    public boolean isWreck() { return wreck; }
    public float getHealth() { return wreck ? 0 : 300; }
    public Target getOwner() { return owner; }
    public Target getFirstPassenger() { return passenger; }
    public VehicleData data() { return vehicleData; }
    public int getEnergy() { return energy; }
    public void consumeEnergy(int cost) { energy -= cost; energyWrites++; }
    public boolean isTurretActivated() { return active; }
    public boolean isCombatDeployed() { return deployed; }
    public boolean hasOperationalPower() { return energy > 0; }
    public int getDeployPhase() { return (int) synced.get(DEPLOY_PHASE); }
    public SynchedData getEntityData() { return synced; }
    public boolean needsTargetScan() { return false; }
    public void refreshSharedCandidates(Seek seek) { throw new AssertionError("scan not under test"); }
    public Target acquireTarget(int module, Seek seek) { return candidate; }
    public void initializeModuleValidation(int module, Target target) { }
    public Target resolveModuleTarget(int module) { return level.entities.get(UUID.fromString(moduleTarget[module])); }
    public boolean isModuleTargetValid(int module, Target target, Seek seek) {
      modulePathDeferred[module] = pathDeferred;
      return target != null && targetAllowed && !pathDeferred;
    }
    public boolean isAttackTargetValid(Target target, Seek seek) { throw new AssertionError("production override required"); }
    public void playModuleLockSound(int module) { locks++; }
    public void spawnModuleWindupParticle(int module, int tick) { }
    public Vec3 moduleFxUpWorldPosition(int module, double radial, double up) { return position; }
    public void spawnModuleSkyDischarge(World world, Vec3 origin) { effects++; }
    public void publishModule(int module) { publishes++; }
    public boolean hasVerticalDropPath(Target target) { throw new AssertionError("production override required"); }
    public List<Target> resolveCandidateView() { candidateViewReads++; return sharedCandidates; }
    public Boolean exploratoryPathClear(int module, Target target) {
      return target != null && hasVerticalDropPath(target);
    }
    public void releaseModuleGeometryOwners() { }
    public boolean releaseAttack(int module, Target target) { throw new AssertionError("production override required"); }
    public void clearModuleValidation(int module) { throw new AssertionError("production override required"); }
    public void resetModuleToReady(int module) { throw new AssertionError("production override required"); }
    public void invalidateExecution() { throw new AssertionError("production override required"); }
    public void invalidateModuleExecution(int module) { throw new AssertionError("production override required"); }
    public boolean isPvpProtected(Target target) { throw new AssertionError("production override required"); }
    public DamageSources damageSources() { return new DamageSources(); }
    public void m_7380_(CompoundTag tag) { }
    public void m_7378_(CompoundTag tag) { }
  }
  public static class World {
    public long now = 100;
    public boolean isClientSide, loaded = true, clear = true, sideClear = true;
    public int clips, admissions, geometryChecks, geometryLimit = Integer.MAX_VALUE;
    public final Map<UUID, Target> entities = new LinkedHashMap<>();
    public long getGameTime() { return now; }
    public Dimension dimension() { return new Dimension(); }
    public Target getEntity(UUID id) { return entities.get(id); }
    public int getMaxBuildHeight() { return 320; }
    public boolean hasChunkAt(BlockPos pos) { return loaded; }
    public boolean hasChunksAt(BlockPos from, BlockPos to) { return loaded; }
    public Hit clip(Clip context) {
      clips++;
      boolean vertical = context.start.x == context.end.x && context.start.z == context.end.z;
      return new Hit((clear && (vertical || sideClear)) ? HitKind.MISS : HitKind.BLOCK);
    }
    public int sendParticles(Particle type, double x, double y, double z, int count, double dx, double dy, double dz, double speed) { return count; }
    public void playSound(Target player, double x, double y, double z, Sound event, Channel channel, float volume, float pitch) { }
    public boolean addFreshEntity(Target target) { admissions++; return true; }
    public RegistryAccess registryAccess() { return new RegistryAccess() { }; }
    public <T> List<T> getEntitiesOfClass(Class<T> type, AABB area) {
      return entities.values().stream().filter(type::isInstance).filter(t -> area.intersects(t.getBoundingBox())).map(type::cast).toList();
    }
  }
  public static class Seek { public double min = 2, max = 72; public double getMinSeekRange() { return min; } public double getMaxSeekRange() { return max; } }
  public static class Config { public Seek seek = new Seek(); public Seek getSeekInfo() { return seek; } }
  public static class VehicleData { public Config config = new Config(); public Config compute() { return config; } }
  public record Dimension() { public ResourceLocation location() { return ResourceLocation.parse("minecraft:overworld"); } }
  public static class Kind {
    public static final Kind LIGHTNING_BOLT = new Kind("minecraft:lightning_bolt");
    public final String id;
    public Kind(String id) { this.id = id; }
    public Target create(World world) { var result = new Lightning(); result.level = world; return result; }
  }
  public static class Lightning extends Target { public void moveTo(double x, double y, double z) { } public void setVisualOnly(boolean value) { } }
  public interface Registry { ResourceLocation getKey(Object type); }
  public static class Registries {
    public static final Registry ENTITY_TYPE = t -> ResourceLocation.parse(((Kind) t).id);
    public static final Dimension DAMAGE_TYPE = new Dimension();
  }
  public interface RegistryAccess {
    default DamageRegistry registryOrThrow(Dimension key) { return new DamageRegistry() { }; }
  }
  public interface DamageRegistry {
    default DamageHolder getHolderOrThrow(Dimension key) { return new DamageHolder(); }
  }
  public static class DamageHolder { }
  public static class DamageKind { }
  public static class DamageTypes { public static final Dimension LIGHTNING_BOLT = new Dimension(); }
  public static class Coordinator {
    public static Object stamp;
    public static boolean allowed;
    public static Runnable beforeAuthorization;
    public static Target commandTarget;
    public static long commandRevision = -1L, commandValidUntil = -1L;
    public static boolean commandExplicit, commandLive;
    public static LossBehavior commandLoss = LossBehavior.NATIVE_FALLBACK;
    public static Command targetCommand(Target source) {
      Optional<Target> target = Optional.ofNullable(commandTarget);
      return new Command(target, target.map(Target::getUUID), commandRevision, commandValidUntil,
          commandExplicit, commandLive, commandLoss,
          target.isPresent() ? "ACTIVE_ASSIGNMENT" : "NO_ACTIVE_ASSIGNMENT");
    }
    public static Object executionStamp(Target source) { return stamp; }
    public static boolean fireAuthorized(Target source, Target target, String station, String weapon, long tick) {
      beforeAuthorization.run(); return allowed;
    }
  }
  public record Command(
      Optional<Target> target,
      Optional<UUID> targetId,
      long revision,
      long validUntilTick,
      boolean explicitDesignation,
      boolean liveLease,
      LossBehavior lossBehavior,
      String reason) { }
  public static class Policy {
    public static boolean isPvpProtected(Target target) { return target.pvp; }
    public static boolean allowsNativeOrActiveAssignment(Target source, Target target, boolean nativeEligible) {
      var vehicle = (Base) source; vehicle.duringRelation.run();
      return vehicle.targetAllowed && nativeEligible && !target.pvp;
    }
  }
  public static class Dummy extends Target { }
  public interface Enemy { }
  public static class Exclusions { public static boolean isUnavailableCombatTarget(Target target) { return target == null || target.removed; } }
  public static class Budget {
    public static void releaseGeometryOwner(World level, UUID owner) { }
    public static boolean admitGeometryCheck(World level, UUID owner) {
      if (level.entities.values().stream().anyMatch(t -> t instanceof Base b && b.pathDeferred)) return false;
      if (level.geometryChecks >= level.geometryLimit) return false;
      level.geometryChecks++;
      return true;
    }
    public static int nextCheckTick(int tick, int id, int interval, int salt) { return tick + interval; }
  }
  public static class SeekTool {
    public static final java.util.function.Predicate<Target> IS_INVULNERABLE = t -> false, IN_BLACKLIST = t -> false;
  }
  public static class TeamResolver { public static boolean isHostile(Base source, Target target) { return !target.allied; } }
  public record Clip(Vec3 start, Vec3 end, ClipBlock block, ClipFluid fluid, Target target) { }
  public enum ClipBlock { COLLIDER }
  public enum ClipFluid { NONE }
  public enum HitKind { MISS, BLOCK }
  public record Hit(HitKind type) { public HitKind getType() { return type; } }
  public static class Particle { }
  public static class Particles {
    public static final Particle ELECTRIC_SPARK = new Particle(), END_ROD = new Particle();
    public static final Particle FLASH = new Particle(), EXPLOSION_EMITTER = new Particle();
    public static final Particle FLAME = new Particle(), CLOUD = new Particle();
  }
  public static class Sound { }
  public static class Sounds {
    public static final Sound LIGHTNING_BOLT_THUNDER = new Sound();
    public static final Sound GENERIC_EXPLODE = new Sound();
  }
  public enum Channel { WEATHER, HOSTILE }
  public static class Damage { }
  public static class DirectDamage extends Damage {
    public final float fraction;
      public DirectDamage(DamageHolder type, Target direct, Target attacker, float fraction, float percent) {
        if (percent != .035F) throw new AssertionError("Jingzhe percent");
      this.fraction = fraction;
    }
  }
  public static class DamageSources {
    public Damage lightningBolt() { return new Damage(); }
    public Damage explosion(Target source, Target owner) { return new Damage(); }
  }
  public static final class Secondary {
    public final String targetId;
    public Vec3 impact;
    public int ticks;
    public Secondary(String targetId, Vec3 impact, int ticks) {
      this.targetId = targetId;
      this.impact = impact;
      this.ticks = ticks;
    }
  }
  public static final class GroundFlame {
    public final Vec3 center;
    public int ticks;
    public GroundFlame(Vec3 center, int ticks) {
      this.center = center;
      this.ticks = ticks;
    }
  }
  public static final class EntityFind {
    public static Target findEntity(World world, String targetId) {
      return world.entities.get(UUID.fromString(targetId));
    }
  }
  public static class Accessor { }
  public static class SynchedData {
    final Map<Accessor, Object> values = new HashMap<>();
    public Object get(Accessor key) { return values.get(key); }
    public void set(Accessor key, Object value) { values.put(key, value); }
  }
}
