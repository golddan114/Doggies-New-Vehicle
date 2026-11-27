package com.liucodex.doggiesnewvehicle.compat;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

/** Executes the exact SBW barrel transform against an isolated vehicle for M1 anchor checks. */
public final class ExactSbwTransformFixture extends ClassLoader {
  private static final String SBW = "com/atsuishio/superbwarfare/";
  private static final String VEHICLE = SBW + "entity/vehicle/base/VehicleEntity";
  private static final String VEC = SBW + "entity/vehicle/utils/VehicleVecUtils";
  private static final String GENERATED = "com/liucodex/doggiesnewvehicle/compat/transformprobe/";
  private final Map<String, byte[]> definitions = new HashMap<>();
  public final Vehicle vehicle = new Vehicle(this);

  public ExactSbwTransformFixture() throws Exception {
    super(ExactSbwTransformFixture.class.getClassLoader());
    try (var zip = new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
      add(zip.getInputStream(zip.getEntry(VEC + ".class")).readAllBytes(),
          Set.of("<init>", "<clinit>", "getGunTransform", "getTurretTransform",
              "getBarrelTransform", "transformPosition"));
    }
  }

  public Vec3 nativeBarrelPoint(Vec3 local) throws Exception {
    Class<?> type = loadClass((GENERATED + "VehicleVecUtils").replace('/', '.'));
    var method = type.getDeclaredMethod("getBarrelTransform", Vehicle.class, float.class);
    method.setAccessible(true);
    Matrix4d transform = (Matrix4d) method.invoke(null, vehicle, 1F);
    Vector3d world = transform.transformPosition(local.x, local.y, local.z, new Vector3d());
    return new Vec3(world.x, world.y, world.z);
  }

  private void add(byte[] original, Set<String> methods) {
    var source = new ClassNode();
    new ClassReader(original).accept(source, 0);
    source.methods.removeIf(method -> !methods.contains(method.name));
    source.access |= Opcodes.ACC_PUBLIC;
    var mapped = new ClassNode();
    source.accept(new ClassRemapper(mapped, new Remapper() {
      @Override public String map(String name) {
        if (name.equals(VEHICLE)) return Type.getInternalName(Vehicle.class);
        if (name.equals(VEC)) return GENERATED + "VehicleVecUtils";
        if (name.equals("kotlin/jvm/internal/Intrinsics"))
          return Type.getInternalName(Intrinsics.class);
        return name;
      }
      @Override public String mapFieldName(String owner, String name, String descriptor) {
        if (owner.equals("net/minecraft/world/phys/Vec3")) return switch (name) {
          case "f_82479_" -> "x";
          case "f_82480_" -> "y";
          case "f_82481_" -> "z";
          default -> name;
        };
        if (owner.equals("com/mojang/math/Axis")) return switch (name) {
          case "f_252436_" -> "YP";
          case "f_252529_" -> "XP";
          default -> name;
        };
        return name;
      }
      @Override public String mapMethodName(String owner, String name, String descriptor) {
        if (owner.equals("com/mojang/math/Axis") && name.equals("m_252977_")) return "rotationDegrees";
        if (owner.equals("net/minecraft/util/Mth")) return switch (name) {
          case "m_14136_" -> "atan2";
          case "m_14179_" -> "lerp";
          case "m_14175_" -> "wrapDegrees";
          case "m_14036_" -> "clamp";
          default -> name;
        };
        return name;
      }
    }));
    var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    mapped.accept(writer);
    definitions.put(mapped.name.replace('/', '.'), writer.toByteArray());
  }

  @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
    byte[] bytes = definitions.get(name);
    if (bytes == null) throw new ClassNotFoundException(name);
    return defineClass(name, bytes, 0, bytes.length);
  }

  public static final class Vehicle {
    private final ExactSbwTransformFixture fixture;
    public Matrix4d body = new Matrix4d();
    public Vec3 turretPos, barrelPos;
    public float turretYaw, turretPitch;

    private Vehicle(ExactSbwTransformFixture fixture) { this.fixture = fixture; }
    public Matrix4d getVehicleTransform(float partial) { return new Matrix4d(body); }
    public Matrix4d getVehicleTransformWithCustomPitch(float partial) { return new Matrix4d(body); }
    public Vec3 getTurretPos() { return turretPos; }
    public Vec3 getBarrelPosition() { return barrelPos; }
    public Matrix4d getTurretTransform(float partial) {
      try {
        Class<?> type = fixture.loadClass((GENERATED + "VehicleVecUtils").replace('/', '.'));
        var method = type.getDeclaredMethod("getTurretTransform", Vehicle.class, float.class);
        method.setAccessible(true);
        return (Matrix4d) method.invoke(null, this, partial);
      } catch (Exception exception) {
        throw new AssertionError(exception);
      }
    }
    public float getTurretYRot() { return turretYaw; }
    public float getTurretYRotO() { return turretYaw; }
    public float getTurretXRot() { return turretPitch; }
    public float getTurretXRotO() { return turretPitch; }
  }

  public static final class Intrinsics {
    public static void checkNotNullParameter(Object value, String name) { Objects.requireNonNull(value, name); }
    public static void checkNotNullExpressionValue(Object value, String name) { Objects.requireNonNull(value, name); }
    public static void checkNotNull(Object value) { Objects.requireNonNull(value); }
    public static void checkNotNull(Object value, String name) { Objects.requireNonNull(value, name); }
  }
}
