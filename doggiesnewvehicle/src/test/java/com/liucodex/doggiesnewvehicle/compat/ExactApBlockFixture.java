package com.liucodex.doggiesnewvehicle.compat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/** Executes the complete exact AP block handler. World, material and effects are explicit doubles. */
final class ExactApBlockFixture extends ClassLoader {
  private static final String SBW = "com/atsuishio/superbwarfare/";
  private static final String SHELL = SBW + "entity/projectile/CannonShellEntity";
  private final Class<?> type;
  final int nativeDurability;

  ExactApBlockFixture() throws Exception {
    super(ExactApBlockFixture.class.getClassLoader());
    ClassNode shell = read(SHELL);
    ClassNode fast = read(SBW + "entity/projectile/FastThrowableProjectile");
    MethodNode init = fast.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
    var store = Arrays.stream(init.instructions.toArray()).filter(FieldInsnNode.class::isInstance)
        .map(FieldInsnNode.class::cast).filter(f -> f.name.equals("durability") && f.getOpcode() == Opcodes.PUTFIELD).findFirst().orElseThrow();
    AbstractInsnNode value = store.getPrevious();
    while (value.getOpcode() < 0) value = value.getPrevious();
    nativeDurability = ((IntInsnNode) value).operand;
    var generated = new ClassNode();
    generated.version = Opcodes.V17;
    generated.access = Opcodes.ACC_PUBLIC;
    generated.name = "com/liucodex/doggiesnewvehicle/compat/lifetimeprobe/ApBlock";
    generated.superName = Type.getInternalName(Projectile.class);
    var constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, generated.superName, "<init>", "()V", false));
    constructor.instructions.add(new InsnNode(Opcodes.RETURN));
    generated.methods.add(constructor);
    generated.methods.add(shell.methods.stream().filter(m -> m.name.equals("afterHitBlock")).findFirst().orElseThrow());
    for (MethodNode method : generated.methods) for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof MethodInsnNode call
          && call.owner.equals("kotlin/jvm/internal/Intrinsics") && call.name.equals("areEqual")) {
        call.owner = "java/util/Objects";
        call.name = "equals";
      }
    }
    Map<String, String> names = new HashMap<>();
    names.put(SHELL, Type.getInternalName(Projectile.class));
    names.put(SHELL + "$Type", Type.getInternalName(ShellType.class));
    names.put("net/minecraft/world/level/Level", Type.getInternalName(Level.class));
    names.put("net/minecraft/server/level/ServerLevel", Type.getInternalName(ServerLevel.class));
    names.put("net/minecraft/world/phys/BlockHitResult", Type.getInternalName(Hit.class));
    names.put("net/minecraft/world/level/block/state/BlockState", Type.getInternalName(State.class));
    names.put("net/minecraft/world/level/block/Block", Type.getInternalName(Block.class));
    names.put("net/minecraft/world/level/block/SoundType", Type.getInternalName(Sound.class));
    names.put("net/minecraft/world/entity/Entity", "java/lang/Object");
    names.put("net/minecraftforge/common/ForgeConfigSpec$BooleanValue", Type.getInternalName(Flag.class));
    names.put(SBW + "config/server/ExplosionConfig", Type.getInternalName(Config.class));
    names.put(SBW + "tools/ParticleTool", Type.getInternalName(Particles.class));
    names.put("kotlin/jvm/internal/Intrinsics", Type.getInternalName(ExactQueuedShotFixture.Intrinsics.class));
    names.put("kotlin/ranges/RangesKt", "net/minecraft/util/Mth");
    var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    generated.accept(new ClassRemapper(writer, new Remapper() {
      @Override public String map(String name) { return names.getOrDefault(name, name); }
      @Override public String mapFieldName(String owner, String name, String desc) {
        if (owner.equals("net/minecraft/world/phys/Vec3")) return switch (name) {
          case "f_82479_" -> "x"; case "f_82480_" -> "y"; case "f_82481_" -> "z"; default -> name;
        };
        return name;
      }
      @Override public String mapMethodName(String owner, String name, String desc) {
        if (owner.equals("kotlin/ranges/RangesKt") && name.equals("coerceIn")) return "clamp";
        if (owner.equals("net/minecraft/core/BlockPos") && name.equals("m_274446_")) return "containing";
        if (owner.equals("net/minecraft/world/phys/Vec3")) return switch (name) {
          case "m_82541_" -> "normalize"; case "m_82490_" -> "scale";
          case "m_82549_" -> "add"; case "m_82512_" -> "atCenterOf"; default -> name;
        };
        return name;
      }
    }));
    byte[] bytes = writer.toByteArray();
    type = defineClass(generated.name.replace('/', '.'), bytes, 0, bytes.length);
    Config.EXPLOSION_DESTROY.value = Config.EXTRA_EXPLOSION_EFFECT.value = true;
  }

  Projectile create(State material, boolean destructionAccepted) throws Exception {
    var projectile = (Projectile) type.getConstructor().newInstance();
    projectile.durability = nativeDurability;
    projectile.level.material = material;
    projectile.level.destructionAccepted = destructionAccepted;
    return projectile;
  }

  void hit(Projectile projectile) throws Exception {
    type.getMethod("afterHitBlock", Hit.class).invoke(projectile, new Hit());
  }

  public enum ShellType { AP, HE, WP }
  public static class Flag { boolean value = true; public Object get() { return value; } }
  public static class Config {
    public static final Flag EXPLOSION_DESTROY = new Flag(), EXTRA_EXPLOSION_EFFECT = new Flag();
  }
  public static class Level {}
  public static class ServerLevel extends Level {
    State material;
    boolean destructionAccepted;
    int reads, destroys;
    public State m_8055_(BlockPos pos) { reads++; return pos.getX() == 0 ? material : State.air(); }
    public boolean m_46961_(BlockPos pos, boolean drops) { destroys++; return destructionAccepted; }
  }
  public static class Hit { public Vec3 m_82450_() { return Vec3.ZERO; } }
  public static class Sound {
    public static final Sound f_56744_ = new Sound(), f_56742_ = new Sound(), f_56743_ = new Sound(),
        f_154663_ = new Sound(), f_56725_ = new Sound(), OTHER = new Sound();
  }
  public static class Block {
    final float hardness;
    Block(float hardness) { this.hardness = hardness; }
    public float m_155943_() { return hardness; }
  }
  public static class State {
    final Block block;
    final boolean occludes, air;
    final Sound sound;
    State(float hardness, boolean occludes, Sound sound, boolean air) {
      this.block = new Block(hardness); this.occludes = occludes; this.sound = sound; this.air = air;
    }
    static State material(float hardness, boolean occludes, Sound sound) { return new State(hardness, occludes, sound, false); }
    static State air() { return new State(0, false, Sound.OTHER, true); }
    public Block m_60734_() { return block; }
    public boolean m_60795_() { return air; }
    public boolean m_60815_() { return occludes; }
    public Sound m_60827_() { return sound; }
  }
  public static class Particles { public static void cannonHitParticles(ServerLevel level, Vec3 point) {} }
  public static class Projectile {
    public ShellType type = ShellType.AP;
    public ServerLevel level = new ServerLevel();
    public int durability, explosions, fallback;
    public boolean removed, destroysBlocks = true;
    public Vec3 position = Vec3.ZERO, velocity = new Vec3(16.4667F, 0, 0), explosionPoint;
    public float damage = 100, explosionDamage = 100, radius = 5;
    public Level m_9236_() { return level; }
    public Object m_19749_() { return null; }
    public boolean getExplosionDestroyValue() { return destroysBlocks; }
    public Vec3 m_20184_() { return velocity; }
    public void m_20256_(Vec3 v) { velocity = v; }
    public int getDurability() { return durability; }
    public void setDurability(int v) { durability = v; }
    public float getDamageValue() { return damage; }
    public void setDamage(float v) { damage = v; }
    public float getExplosionDamageValue() { return explosionDamage; }
    public void setExplosionDamage(float v) { explosionDamage = v; }
    public float getExplosionRadiusValue() { return radius; }
    public void setExplosionRadius(float v) { radius = v; }
    public void causeExplode(Vec3 p) { explosionPoint = p; explosions++; }
    public void causeWPEffect(Vec3 p, Object owner) { throw new AssertionError("not AP"); }
    public void m_146870_() { removed = true; }
    public void m_6034_(double x, double y, double z) { position = new Vec3(x, y, z); }
    public void destroyBlock(Hit hit) { fallback++; }
  }
  private static ClassNode read(String name) throws Exception {
    try (var zip = new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
      var node = new ClassNode();
      new ClassReader(zip.getInputStream(zip.getEntry(name + ".class"))).accept(node, ClassReader.SKIP_FRAMES);
      return node;
    }
  }
}
