package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.core.DnvDeferredShot;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/**
 * Executes the exact transformed named queue/callback and admission tail, plus the compiled DNV
 * adapter. Minecraft world/registry, gun creation and ammo are explicit test doubles. No server or
 * full projectile lifecycle is claimed. Method bodies for DNV scope handling are not substituted.
 */
public final class ExactQueuedShotFixture {
  static final String SBW = "com/atsuishio/superbwarfare/";
  static final String VEHICLE = SBW + "entity/vehicle/base/VehicleEntity";
  static final String GUN = SBW + "item/gun/GunItem";
  static final String ADAPTER = "com/liucodex/doggiesnewvehicle/compat/DnvSbwShotExecution";
  static final String TURRET = "com/liucodex/doggiesnewvehicle/compat/DnvSbwTurretExecution";
  static final String GENERATED = "com/liucodex/doggiesnewvehicle/compat/executionprobe/";
  final Level level = new Level();
  final Vehicle vehicle;
  final Entity target = new Entity();
  final Object adapter;
  final List<DnvDeferredShot.Receipt> receipts = new ArrayList<>();
  final Loader loader;
  private final Class<?> adapterClass;

  ExactQueuedShotFixture(Path temp) throws Exception {
    this(temp, false);
  }

  ExactQueuedShotFixture(Path temp, boolean apocalypse) throws Exception {
    this(temp, apocalypse, false);
  }

  ExactQueuedShotFixture(Path temp, boolean apocalypse, boolean allied) throws Exception {
    this(temp, apocalypse, allied, false);
  }

  ExactQueuedShotFixture(Path temp, boolean apocalypse, boolean allied, boolean blizzard) throws Exception {
    this(temp, apocalypse, allied, blizzard, false);
  }

  ExactQueuedShotFixture(Path temp, boolean apocalypse, boolean allied, boolean blizzard, boolean project612) throws Exception {
    var mixinTest = new DnvMuzzleMixinApplicationTest();
    mixinTest.temp = temp;
    byte[] vehicleBytes;
    byte[] gunBytes;
    try (var engine = mixinTest.loader()) {
      vehicleBytes = DnvMuzzleMixinApplicationTest.transform(engine, "entity/vehicle/base/VehicleEntity", "VehicleQueuedShotMixin", "SERVER", false)[1];
    }
    try (var engine = mixinTest.loader()) {
      gunBytes = DnvMuzzleMixinApplicationTest.transform(engine, "item/gun/GunItem", "GunShotAdmissionMixin", "SERVER", false)[1];
    }
    Map<String, byte[]> definitions = new HashMap<>();
    definitions.put(GENERATED + "Vehicle", vehicle(vehicleBytes, apocalypse, allied, blizzard, project612));
    definitions.put(GENERATED + "Gun", gun(gunBytes));
    try (var stream = getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/api/combat/DnvNativeShotVisual.class")) {
      // Execute the real opt-in check too; these turret guns do not request a handheld trail.
      definitions.put(GENERATED + "ShotVisual", remap(stream.readAllBytes()));
    }
    if (apocalypse) {
      try (var stream = getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/armored/ApocalypseArmament.class")) {
        ClassNode armament = node(stream.readAllBytes());
        armament.methods.removeIf(m -> !Set.of("<init>", "ready", "readyNow", "fire", "guard", "lambda$guard$0",
            "queueResult", "clearQueue", "admit", "accepted", "muzzleClear", "decorate").contains(m.name));
        definitions.put(GENERATED + "ApocalypseArmament", remap(write(armament)));
      }
    }
    if (blizzard) {
      try (var stream = getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/armored/BlizzardMissileArmament.class")) {
        ClassNode armament = node(stream.readAllBytes());
        armament.access |= Opcodes.ACC_PUBLIC;
        armament.methods.forEach(m -> m.access = (m.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC);
        definitions.put(GENERATED + "BlizzardArmament", remap(write(armament)));
      }
    }
    if (project612) {
      try (var stream = getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/armored/Project612Armament.class")) {
        definitions.put(GENERATED + "Project612Armament", remap(stream.readAllBytes()));
      }
    }
    if (allied) {
      try (var stream = getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/armored/ApocalypseAutonomousWeapons.class")) {
        ClassNode weapons = node(stream.readAllBytes());
        weapons.access = Opcodes.ACC_PUBLIC;
        weapons.methods.forEach(m -> m.access = (m.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC);
        definitions.put(GENERATED + "AutonomousWeapons", remap(write(weapons)));
      }
    }
    for (String suffix : List.of("", "$Request")) {
      try (var stream = getClass().getResourceAsStream("/" + ADAPTER + suffix + ".class")) {
        byte[] bytes = stream.readAllBytes();
        definitions.put(GENERATED + "Adapter" + suffix, remap(bytes));
      }
    }
    for (String suffix : List.of("", "$WeaponConfiguration")) {
      try (var stream = getClass().getResourceAsStream("/" + TURRET + suffix + ".class")) {
        definitions.put(GENERATED + "Turret" + suffix, remap(stream.readAllBytes()));
      }
    }
    loader = new Loader(definitions);
    vehicle = (Vehicle) loader.loadClass((GENERATED + "Vehicle").replace('/', '.')).getConstructor().newInstance();
    vehicle.gun.item = (Gun) loader.loadClass((GENERATED + "Gun").replace('/', '.')).getConstructor().newInstance();
    vehicle.level = target.level = level;
    level.entities.put(vehicle.id, vehicle);
    level.entities.put(target.id, target);
    adapterClass = loader.loadClass((GENERATED + "Adapter").replace('/', '.'));
    adapter = adapterClass.getConstructor().newInstance();
    Mod.Companion.work.clear();
    ApocalypseShotHost.Network.feedback.clear();
    Mod.Companion.accept = true;
    Coordinator.stamp = new Object();
    Coordinator.allowed = true;
    Coordinator.beforeAuthorization = () -> {};
    if (apocalypse) ((ApocalypseShotHost) vehicle).initialize(this);
    if (allied) ((AlliedApocalypseShotHost) vehicle).initializeAi(this);
    if (blizzard) ((BlizzardShotHost) vehicle).initialize(this);
    if (project612) ((Project612ShotHost) vehicle).initialize(this);
  }

  void submit(Supplier<String> validation) throws Exception {
    submit(target.id, validation);
  }

  void submit(UUID targetId, Supplier<String> validation) throws Exception {
    var intent = new DnvDeferredShot.Intent(UUID.randomUUID(), vehicle.id, "seat_1", "Cannon105", targetId,
        UUID.randomUUID(), 2, 3, 4, 5, level.now, 0, new com.liucodex.doggiesnewvehicle.core.DnvProjectilePreflight.Point(9, 2, 4));
    submitIntent(intent, validation);
  }

  void submitIntent(DnvDeferredShot.Intent intent, Supplier<String> validation) throws Exception {
    call("submitTurret", new Class<?>[] {Vehicle.class, Entity.class, String.class,
        DnvDeferredShot.Intent.class, Supplier.class, Consumer.class, Runnable.class},
        vehicle, null, "superbwarfare:cannon_shell", intent, validation,
        (Consumer<DnvDeferredShot.Receipt>) receipts::add, (Runnable) () -> {
          var aim = intent.aimPoint();
          vehicle.vehicleShoot(null, intent.weapon(), intent.targetId(), new Vec3(aim.x(), aim.y(), aim.z()));
        });
  }

  void clear() throws Exception { call("clear", new Class<?>[0]); }
  void expire() throws Exception { call("expire", new Class<?>[] {long.class}, level.now); }
  void run() { Mod.Companion.work.remove(0).run(); }
  private void call(String name, Class<?>[] types, Object... args) throws Exception {
    Method method = adapterClass.getDeclaredMethod(name, types);
    method.setAccessible(true);
    method.invoke(adapter, args);
  }

  private static byte[] vehicle(byte[] transformed, boolean apocalypse, boolean allied, boolean blizzard, boolean project612) {
    ClassNode original = node(transformed);
    ClassNode isolated = shell(GENERATED + "Vehicle", Type.getInternalName(project612 ? Project612ShotHost.class
        : blizzard ? BlizzardShotHost.class : allied ? AlliedApocalypseShotHost.class : apocalypse ? ApocalypseShotHost.class : Vehicle.class));
    String issueDescriptor = "(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;Ljava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V";
    for (MethodNode method : original.methods) {
      if (method.name.equals("vehicleShoot") && method.desc.equals(issueDescriptor)
          || method.name.equals("getVectorFromString") && method.desc.equals("(Ljava/lang/String;FI)Lnet/minecraft/world/phys/Vec3;")
          || method.name.startsWith("vehicleShoot$lambda$") || method.name.contains("dnv$")) {
        isolated.methods.add(method);
      }
    }
    // Preserve each player's actual opt-in; a hand-written host constant could hide a missing production override.
    String owner = project612 ? "DnvProject612Entity" : blizzard ? "DnvBlizzardEntity" : apocalypse && !allied ? "DnvApocalypseEntity" : null;
    if (owner != null) try (var stream = ExactQueuedShotFixture.class.getResourceAsStream(
            "/com/liucodex/doggiesnewvehicle/armored/" + owner + ".class")) {
      node(stream.readAllBytes()).methods.stream().filter(m -> m.name.equals("deferNativeShotEffects")).forEach(isolated.methods::add);
    } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    return remap(write(isolated));
  }

  private static byte[] gun(byte[] transformed) {
    ClassNode original = node(transformed);
    ClassNode isolated = shell(GENERATED + "Gun", Type.getInternalName(Gun.class));
    for (MethodNode method : original.methods) {
      if (method.name.contains("dnv$")) isolated.methods.add(method);
      if (method.name.equals("afterShoot") && method.desc.equals("(L" + SBW + "data/gun/ShootParameters;)V")) {
        // Execute the native index-rotation block, rather than mimicking it in the ammo fixture.
        var all = Arrays.asList(method.instructions.toArray());
        var indexStart = all.stream().filter(i -> i instanceof FieldInsnNode f && f.name.equals("SHOOT_POS"))
            .findFirst().orElseThrow().getPrevious();
        var indexEnd = all.stream().filter(i -> i instanceof MethodInsnNode m && m.name.equals("clearTempModifications"))
            .findFirst().orElseThrow().getPrevious();
        var labels = new HashMap<LabelNode, LabelNode>();
        all.stream().filter(LabelNode.class::isInstance).map(LabelNode.class::cast).forEach(l -> labels.put(l, new LabelNode()));
        var rotate = new MethodNode(Opcodes.ACC_PUBLIC, "advanceFireIndex", "(L" + SBW + "data/gun/GunData;)V", null, null);
        for (var i = indexStart; i != indexEnd; i = i.getNext()) {
          if (i instanceof FrameNode || i instanceof LineNumberNode) continue;
          var copy = i.clone(labels);
          if (copy instanceof VarInsnNode v && v.var == 2) v.var = 1;
          rotate.instructions.add(copy);
        }
        rotate.instructions.add(new InsnNode(Opcodes.RETURN));
        isolated.methods.add(rotate);
      }
      if (!method.name.equals("shootBullet") || !method.desc.equals("(L" + SBW + "data/gun/ShootParameters;)Z")) continue;
      // Preserve the exact final admission basic block (including POP and true return) and the
      // actual Mixin handlers. The native constructor/launch prefix has its own bytecode tests.
      MethodInsnNode admission = Arrays.stream(method.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
          .map(MethodInsnNode.class::cast).filter(i -> i.name.contains("dnv$worldAdmission")).findFirst().orElseThrow();
      AbstractInsnNode start = admission;
      for (int count = 0; count < 4;) { start = start.getPrevious(); if (start.getOpcode() >= 0) count++; }
      AbstractInsnNode levelLoad = start.getNext();
      while (levelLoad.getOpcode() < 0) levelLoad = levelLoad.getNext();
      AbstractInsnNode entityLoad = levelLoad.getNext();
      while (entityLoad.getOpcode() < 0) entityLoad = entityLoad.getNext();
      InsnList prefix = new InsnList();
      prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
      prefix.add(new FieldInsnNode(Opcodes.GETFIELD, SBW + "data/gun/ShootParameters", "level", "Lnet/minecraft/server/level/ServerLevel;"));
      prefix.add(new VarInsnNode(Opcodes.ASTORE, ((VarInsnNode) levelLoad).var));
      prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
      prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(ExactQueuedShotFixture.class),
          "projectile", "(" + Type.getDescriptor(Parameters.class) + ")" + Type.getDescriptor(Entity.class), false));
      prefix.add(new VarInsnNode(Opcodes.ASTORE, ((VarInsnNode) entityLoad).var));
      while (method.instructions.getFirst() != start) method.instructions.remove(method.instructions.getFirst());
      method.instructions.insert(prefix);
      method.tryCatchBlocks.clear();
      method.localVariables = null;
      isolated.methods.add(method);
    }
    return remap(write(isolated));
  }

  public static Entity projectile(Parameters parameters) {
    Entity entity = parameters.data.projectileType.equals("doggievehicle:blizzard_missile") ? new BlizzardShotHost.Missile() : new Entity();
    entity.level = parameters.level;
    entity.type = parameters.data.projectileType;
    entity.position = parameters.shootPosition;
    entity.velocity = parameters.shootDirection.scale(16.4667F);
    return entity;
  }

  private static ClassNode shell(String name, String parent) {
    ClassNode node = new ClassNode();
    node.version = Opcodes.V17;
    node.access = Opcodes.ACC_PUBLIC;
    node.name = name;
    node.superName = parent;
    MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false));
    constructor.instructions.add(new InsnNode(Opcodes.RETURN));
    node.methods.add(constructor);
    return node;
  }

  private static byte[] remap(byte[] bytes) {
    Map<String, String> names = new HashMap<>();
    names.put("com/liucodex/doggiesnewvehicle/api/combat/DnvNativeShotVisual", GENERATED + "ShotVisual");
    names.put("com/liucodex/doggiesnewvehicle/compat/DnvBlockDamagePolicy",Type.getInternalName(BlockOrigin.class));
    names.put("com/liucodex/doggiesnewvehicle/compat/DnvTurretShells",Type.getInternalName(ShellSnapshot.class));
    names.put(VEHICLE, Type.getInternalName(Vehicle.class));
    names.put(SBW + "entity/vehicle/base/AutoAimableEntity", Type.getInternalName(Vehicle.class));
    names.put(SBW + "data/vehicle/VehicleData", Type.getInternalName(VehicleData.class));
    names.put(SBW + "data/vehicle/DefaultVehicleData", Type.getInternalName(VehicleConfig.class));
    names.put(SBW + "data/vehicle/subdata/SeekInfo", Type.getInternalName(Seek.class));
    names.put(SBW + "data/gun/value/IntValue", Type.getInternalName(IntValue.class));
    names.put(SBW + "data/gun/value/BooleanValue", Type.getInternalName(BooleanValue.class));
    names.put(SBW + "tools/VectorTool", Type.getInternalName(VectorMath.class));
    names.put(SBW + "tools/MinecraftUtil", Type.getInternalName(ApocalypseShotHost.Network.class));
    names.put(SBW + "network/message/receive/VehicleShootClientMessage", Type.getInternalName(ApocalypseShotHost.Feedback.class));
    names.put("com/liucodex/doggiesnewvehicle/compat/DnvCombatCoordinator", Type.getInternalName(Coordinator.class));
    names.put("com/liucodex/doggiesnewvehicle/compat/DnvCombatPolicyRuntime", Type.getInternalName(Policy.class));
    names.put("com/liucodex/doggiesnewvehicle/compat/DnvProjectileBlastEffects", Type.getInternalName(ApocalypseShotHost.BlastEffects.class));
    names.put("com/liucodex/doggiesnewvehicle/core/DnvTaggedProjectileArmor", Type.getInternalName(ApocalypseShotHost.RocketWarhead.class));
    names.put("com/liucodex/doggiesnewvehicle/core/DnvPercentHits", Type.getInternalName(PercentMarker.class));
    names.put(GUN, Type.getInternalName(Gun.class));
    names.put(SBW + "Mod", Type.getInternalName(Mod.class));
    names.put(SBW + "Mod$Companion", Type.getInternalName(Companion.class));
    names.put(SBW + "data/gun/GunData", Type.getInternalName(Data.class));
    names.put(SBW + "data/gun/GunProp", Type.getInternalName(Prop.class));
    names.put(SBW + "data/gun/AmmoConsumer", Type.getInternalName(AmmoConsumer.class));
    names.put(SBW + "data/gun/ProjectileInfo", Type.getInternalName(ProjectileInfo.class));
    names.put(SBW + "data/gun/ShootPos", Type.getInternalName(ShootPos.class));
    names.put(SBW + "data/gun/ShootParameters", Type.getInternalName(Parameters.class));
    names.put(SBW + "entity/projectile/CannonShellEntity", Type.getInternalName(Entity.class));
    names.put("net/minecraft/world/entity/Entity", Type.getInternalName(Entity.class));
    names.put("net/minecraft/world/entity/LivingEntity", Type.getInternalName(Entity.class));
    names.put("net/minecraft/world/entity/EntityType", Type.getInternalName(EntityType.class));
    names.put("net/minecraft/resources/ResourceKey", Type.getInternalName(Dimension.class));
    names.put("net/minecraft/world/level/Level", Type.getInternalName(Level.class));
    names.put("net/minecraft/server/level/ServerLevel", Type.getInternalName(Level.class));
    names.put("net/minecraft/server/level/ServerPlayer", Type.getInternalName(ApocalypseShotHost.Player.class));
    names.put("com/liucodex/doggiesnewvehicle/armored/ApocalypseArmament", GENERATED + "ApocalypseArmament");
    names.put("com/liucodex/doggiesnewvehicle/armored/Project612Armament", GENERATED + "Project612Armament");
    names.put("com/liucodex/doggiesnewvehicle/armored/DnvProject612Entity", Type.getInternalName(Project612ShotHost.class));
    names.put("com/liucodex/doggiesnewvehicle/armored/BlizzardMissileArmament", GENERATED + "BlizzardArmament");
    names.put("com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity", Type.getInternalName(BlizzardShotHost.class));
    names.put("com/liucodex/doggiesnewvehicle/armored/DnvBlizzardMissile", Type.getInternalName(BlizzardShotHost.Missile.class));
    names.put("com/liucodex/doggiesnewvehicle/armored/BlizzardLoadedPath", Type.getInternalName(BlizzardShotHost.Loaded.class));
    names.put("com/liucodex/doggiesnewvehicle/armored/ApocalypseAutonomousWeapons", GENERATED + "AutonomousWeapons");
    names.put("com/liucodex/doggiesnewvehicle/armored/ApocalypseFriendlyLane", Type.getInternalName(AlliedApocalypseShotHost.FriendlyLane.class));
    names.put("com/liucodex/doggiesnewvehicle/armored/DnvAlliedApocalypseEntity", Type.getInternalName(AlliedApocalypseShotHost.class));
    names.put("com/liucodex/doggiesnewvehicle/armored/DnvApocalypseMissile", Type.getInternalName(AlliedApocalypseShotHost.Missile.class));
    names.put(SBW + "tools/SeekTool", Type.getInternalName(AlliedApocalypseShotHost.Smoke.class));
    names.put("com/liucodex/doggiesnewvehicle/armored/DnvApocalypseEntity", Type.getInternalName(ApocalypseShotHost.class));
    names.put("com/liucodex/doggiesnewvehicle/armored/ApocalypseTargeting", Type.getInternalName(ApocalypseShotHost.Targeting.class));
    names.put("com/liucodex/doggiesnewvehicle/api/combat/DnvNativeShotAdmission", Type.getInternalName(ApocalypseShotAdmission.class));
    names.put("net/minecraft/world/entity/player/Player", Type.getInternalName(ApocalypseShotHost.Player.class));
    names.put("net/minecraft/world/level/ClipContext", Type.getInternalName(ApocalypseShotHost.Clip.class));
    names.put("net/minecraft/world/level/ClipContext$Block", Type.getInternalName(ApocalypseShotHost.Block.class));
    names.put("net/minecraft/world/level/ClipContext$Fluid", Type.getInternalName(ApocalypseShotHost.Fluid.class));
    names.put("net/minecraft/world/phys/HitResult", Type.getInternalName(ApocalypseShotHost.Hit.class));
    names.put("net/minecraft/world/phys/BlockHitResult", Type.getInternalName(ApocalypseShotHost.Hit.class));
    names.put("net/minecraft/world/phys/HitResult$Type", Type.getInternalName(ApocalypseShotHost.HitType.class));
    names.put("net/minecraft/core/registries/BuiltInRegistries", Type.getInternalName(Registries.class));
    names.put("net/minecraft/core/DefaultedRegistry", Type.getInternalName(Registry.class));
    names.put("kotlin/jvm/internal/Intrinsics", Type.getInternalName(Intrinsics.class));
    Remapper mapper = new Remapper() {
      @Override public String map(String name) {
        if (name.startsWith(ADAPTER)) return GENERATED + "Adapter" + name.substring(ADAPTER.length());
        if (name.startsWith(TURRET)) return GENERATED + "Turret" + name.substring(TURRET.length());
        return names.getOrDefault(name, name);
      }
      @Override public Object mapValue(Object value) {
        if (value instanceof Handle handle && handle.getOwner().equals(VEHICLE) && handle.getName().startsWith("vehicleShoot$lambda$")) {
          return new Handle(handle.getTag(), GENERATED + "Vehicle", handle.getName(), mapMethodDesc(handle.getDesc()), handle.isInterface());
        }
        return super.mapValue(value);
      }
    };
    ClassNode mapped = new ClassNode();
    new ClassReader(bytes).accept(new ClassRemapper(mapped, mapper), ClassReader.SKIP_FRAMES);
    for (MethodNode method : mapped.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
      if (instruction instanceof MethodInsnNode call && call.name.contains("dnv$")
          && (call.owner.equals(Type.getInternalName(Vehicle.class)) || call.owner.equals(Type.getInternalName(Gun.class)))) {
        call.owner = mapped.name;
      }
    }
    return write(mapped);
  }

  private static ClassNode node(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
  private static byte[] write(ClassNode node) {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
      @Override protected String getCommonSuperClass(String a, String b) {
        return a.startsWith("java/") && b.startsWith("java/") ? super.getCommonSuperClass(a, b) : "java/lang/Object";
      }
    };
    node.accept(writer);
    return writer.toByteArray();
  }

  static final class Loader extends ClassLoader {
    final Map<String, byte[]> definitions;
    Loader(Map<String, byte[]> definitions) { super(ExactQueuedShotFixture.class.getClassLoader()); this.definitions = definitions; }
    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
      byte[] bytes = definitions.get(name.replace('.', '/'));
      if (bytes == null) throw new ClassNotFoundException(name);
      return defineClass(name, bytes, 0, bytes.length);
    }
  }

  public static class ShellSnapshot {public static void mark(Entity projectile,Parameters shot) {}}
  public static class BlockOrigin {public static void mark(Entity projectile,Entity host){projectile.dnvOrigin=true;} public static void markShot(Entity projectile,Parameters shot){mark(projectile,shot.ammoSupplier);}}
  public static class Entity {
    public final UUID id = UUID.randomUUID();
    public UUID f_19820_ = id;
    public UUID uuid = id;
    public Level level;
    public Vec3 position = Vec3.ZERO;
    public Vec3 velocity = new Vec3(0, 0, 1);
    public boolean removed;
    public boolean pvp;
    public int blastStyle;
    public boolean armorMarked,dnvOrigin;
    public float armorFraction;
    public float percentFraction;
    public String type = "superbwarfare:cannon_shell";
    public UUID getUUID() { return id; }
    public UUID m_20148_() { return id; }
    public Level level() { return level; }
    public Level m_9236_() { return level; }
    public Vec3 position() { return position; }
    public Vec3 getDeltaMovement() { return velocity; }
    public boolean isRemoved() { return removed; }
    public EntityType getType() { return new EntityType(type); }
    public boolean isAlive() { return !removed; }
    public boolean isSpectator() { return false; }
    public net.minecraft.world.phys.AABB getBoundingBox() { return new net.minecraft.world.phys.AABB(position, position); }
    public Vec3 m_20252_(float tick) { return velocity; }
  }
  public static class Vehicle extends Entity implements com.liucodex.doggiesnewvehicle.api.combat.DnvNativeProjectilePenetration,
      com.liucodex.doggiesnewvehicle.api.combat.DnvNativeProjectilePercent {
    @Override public float directPercent(String weapon) { return percentFraction; }
    public float directFraction;
    @Override public float directPenetration(String weapon) { return directFraction; }
    public Data gun = new Data();
    public Vec3 muzzle = new Vec3(2, 3, 4);
    public Vec3 direction = new Vec3(1, 0, 0);
    public int recoil, sound;
    public Entity passenger;
    public Entity owner;
    public int energy = 60000, energyWrites, aimWrites, rpm = 10;
    public boolean twinMuzzles;
    public boolean lineClear = true;
    public VehicleData vehicleData = new VehicleData();
    public float gunX, gunY;
    public boolean isWreck() { return false; }
    public float getHealth() { return 320; }
    public Entity getOwner() { return owner; }
    public Entity getFirstPassenger() { return passenger; }
    public int getEnergy() { return energy; }
    public void consumeEnergy(int cost) { energy -= cost; energyWrites++; }
    public int vehicleWeaponRpm(String weapon) { return rpm; }
    public void turretAutoAimFromVector(Vec3 vector) { direction = vector; aimWrites++; }
    public boolean checkNoClip(Entity entity, Vec3 muzzle) { return lineClear; }
    public VehicleData data() { return vehicleData; }
    public Data getGunData(String name) { return gun; }
    public Entity getAmmoSupplier() { return this; }
    public Entity getNthEntity(int index) { return passenger; }
    public void modifyGunData(String name, Consumer<Data> consumer) { consumer.accept(gun); }
    public Vec3 getShootPos(String name, float tick) {
      return twinMuzzles ? muzzle.add(0, 0, gun.fireIndex.get() == 0 ? 0.306176 : -0.306176) : muzzle;
    }
    public Vec3 getShootVec(String name, float tick) { return direction; }
    public void afterShoot(Data data, Vec3 vector) { recoil++; }
    public void playShootSound3p(Entity shooter, String name) { sound++; }
    public void vehicleShoot(Entity shooter, String weapon, UUID target, Vec3 point) { throw new AssertionError("must invoke transformed method"); }
    public Vec3 getVectorFromString(String string, float tick) { return velocity; }
    public Vec3 getVectorFromString(String string, float tick, int seat) { throw new AssertionError("must invoke exact seat branch"); }
    public Vec3 cameraDirection() { return velocity; }
    public Vec3 bombHitPos(Entity entity) { return Vec3.ZERO; }
    public Vec3 getShootPosForHud(Entity entity, float tick) { return muzzle; }
  }
  public static class Data {
    public Gun item;
    public int delay, ammoWrites, calls;
    public boolean ready = true;
    public Runnable beforeShot = () -> {};
    public int cost = 20000;
    public int projectileAmount = 1;
    public double gravity = 0.05, velocity = 16.4667, spread = 0.03;
    public double explosionDamage = 120;
    public String projectileType = "superbwarfare:cannon_shell";
    public String configuredProjectile = "superbwarfare:cannon_shell";
    public ShootPos shootPos = new ShootPos();
    public IntValue ammo = new IntValue(), virtualAmmo = new IntValue(), fireIndex = new IntValue(), selectedAmmoType = new IntValue();
    public List<AmmoConsumer> consumers = List.of(new AmmoConsumer(), new AmmoConsumer());
    public BooleanValue overHeat = new BooleanValue();
    public Object get(Prop prop) {
      if (prop == Prop.SHOOT_DELAY_TIME) return delay;
      if (prop == Prop.AMMO_COST_PER_SHOOT) return cost;
      if (prop == Prop.PROJECTILE) return new ProjectileInfo(configuredProjectile);
      if (prop == Prop.PROJECTILE_AMOUNT) return projectileAmount;
      if (prop == Prop.SHOOT_POS) return shootPos;
      if (prop == Prop.GRAVITY) return gravity;
      if (prop == Prop.VELOCITY) return velocity;
      if (prop == Prop.SPREAD) return spread;
      if (prop == Prop.RPM) return 120;
      if (prop == Prop.EXPLOSION_DAMAGE) return explosionDamage;
      if (prop == Prop.AMMO_CONSUMER) return consumers;
      throw new AssertionError("unconfigured gun property");
    }
    public boolean canShoot(Entity entity) { return ready; }
    public boolean reloading() { return false; }
    public boolean hasBackupAmmo(Entity entity) { return false; }
    public void shoot(Parameters parameters) {
      calls++;
      beforeShot.run();
      if (item.shootBullet(parameters)) {
        ammoWrites++;
        item.advanceFireIndex(this);
      }
    }
  }
  public static class Gun {
    public boolean shootBullet(Parameters parameters) { throw new AssertionError("must invoke transformed admission tail"); }
    public void advanceFireIndex(Data data) { throw new AssertionError("must invoke native index rotation"); }
  }
  /** NBT boundary only; the real percent math/claims are executed by DnvVehicleArmorExecutionTest. */
  public static class PercentMarker { public static void mark(Entity projectile, float fraction) { projectile.percentFraction = fraction; } }
  public record ProjectileInfo(String id) { public String getItemId() { return id; } }
  public static class ShootPos {
    public int muzzles = 1;
    public ArrayList<Vec3> getPositions() { return new ArrayList<>(Collections.nCopies(muzzles, Vec3.ZERO)); }
    public boolean getBoundUpWithAmmoAmount() { return false; }
  }
  public static class Prop {
    public static final Prop AMMO_CONSUMER = new Prop();
    public static final Prop SHOOT_DELAY_TIME = new Prop(), SPREAD = new Prop(), AMMO_COST_PER_SHOOT = new Prop();
    public static final Prop PROJECTILE = new Prop(), PROJECTILE_AMOUNT = new Prop(), SHOOT_POS = new Prop();
    public static final Prop GRAVITY = new Prop(), VELOCITY = new Prop();
    public static final Prop RPM = new Prop();
    public static final Prop EXPLOSION_DAMAGE = new Prop();
  }
  public static class AmmoConsumer {
    public int stock, queries;
    public int count(Data data, Entity entity) { queries++; return stock; }
  }
  public static class Mod { public static final Companion Companion = new Companion(); }
  public static class Companion {
    public final List<Runnable> work = new ArrayList<>();
    public boolean accept = true;
    public boolean queueServerWork(int delay, Runnable action) { if (accept) work.add(action); return accept; }
  }
  public static class Parameters {
    public final Entity ammoSupplier, shooter;
    public final Level level;
    public final Vec3 shootPosition, shootDirection, targetPos;
    public final Data data;
    public final UUID targetEntityUUID;
    public final double spread;
    public final boolean zoom;
    public Parameters(Entity supplier, Entity shooter, Level level, Vec3 position, Vec3 direction, Data data,
        double spread, boolean zoom, UUID target, Vec3 point) {
      this.ammoSupplier = supplier; this.shooter = shooter; this.level = level; this.shootPosition = position;
      this.shootDirection = direction; this.data = data; this.targetEntityUUID = target; this.targetPos = point;
      this.spread = spread; this.zoom = zoom;
    }
  }
  public static class Level {
    public long now = 100;
    public boolean accept = true, f_46443_;
    public boolean isClientSide;
    public int admissions;
    public Runnable duringAdmission = () -> {};
    public final Map<UUID, Entity> entities = new HashMap<>();
    public long getGameTime() { return now; }
    public Dimension dimension() { return new Dimension(); }
    public Entity getEntity(UUID id) { return entities.get(id); }
    public boolean addFreshEntity(Entity entity) {
      admissions++;
      duringAdmission.run();
      if (accept) entities.put(entity.id, entity);
      return accept;
    }
    public ApocalypseShotHost.Hit clip(ApocalypseShotHost.Clip clip) {
      return new ApocalypseShotHost.Hit(((Vehicle) clip.owner()).lineClear ? ApocalypseShotHost.HitType.MISS : ApocalypseShotHost.HitType.BLOCK);
    }
  }
  public record EntityType(String id) {}
  public static class Dimension { public ResourceLocation location() { return ResourceLocation.parse("minecraft:overworld"); } }
  public static class Registries {
    public static final Registry ENTITY_TYPE = type -> ResourceLocation.parse(((EntityType) type).id());
  }
  public static class IntValue {
    public int value;
    public int get() { return value; }
    public void set(int value) { this.value = value; }
    public void reset() { value = 0; }
  }
  public static class BooleanValue { public boolean value; public Boolean get() { return value; } }
  public static class Seek { public double getMinSeekRange() { return 14; } public double getMaxSeekRange() { return 96; } }
  public static class VehicleConfig { public Seek seek = new Seek(); public Seek getSeekInfo() { return seek; } }
  public static class VehicleData { public VehicleConfig config = new VehicleConfig(); public VehicleConfig compute() { return config; } }
  public static class VectorMath {
    public static double calculateAngle(Vec3 a, Vec3 b) {
      return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.normalize().dot(b.normalize())))));
    }
  }
  public static class Coordinator {
    public static Object stamp;
    public static boolean allowed;
    public static Runnable beforeAuthorization;
    public static Object executionStamp(Entity entity) { return stamp; }
    public static boolean fireAuthorized(Entity source, Entity target, String station, String weapon, long tick) {
      beforeAuthorization.run();
      return allowed;
    }
  }
  public static class Policy {
    public static boolean isPvpProtected(Entity target) { return target.pvp; }
    public static boolean allowsNativeOrActiveAssignment(Entity source, Entity target, boolean nativeEligible) {
      return nativeEligible && !target.pvp;
    }
  }
  public interface Registry { ResourceLocation getKey(Object type); }
  public static class Intrinsics {
    public static void checkNotNullParameter(Object value, String name) { Objects.requireNonNull(value, name); }
    public static void checkNotNullExpressionValue(Object value, String name) { Objects.requireNonNull(value, name); }
    public static void checkNotNull(Object value) { Objects.requireNonNull(value); }
    public static void checkNotNull(Object value, String name) { Objects.requireNonNull(value, name); }
  }
}
