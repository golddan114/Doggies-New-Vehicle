package com.liucodex.noise.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Executes the compiled save codec without bootstrapping a Forge game server. */
class NoiseStationPersistenceTest {
  @Test
  void legacyLockProgressIsDiscardedWhileAmmunitionAndTargetSurviveRead() throws Exception {
    for (String owner : new String[]{"noise", "silentheaven"}) {
      assertStationCodec(compiledCodec(owner));
    }
  }

  private static void assertStationCodec(Class<?> codec) throws Exception {
    Station station = new Station();
    UUID target = new UUID(1L, 2L);
    CompoundTag legacy = new CompoundTag();
    legacy.putUUID("Target", target);
    legacy.putInt("Ammo", 1);
    legacy.putInt("Cooldown", 7);
    legacy.putInt("Reload", 13);
    legacy.putInt("Scan", 9);
    legacy.putInt("Settled", 20);
    legacy.putDouble("AimX", 1.0D);

    codec.getMethod("readWeaponStation", Station.class, CompoundTag.class, int.class)
        .invoke(null, station, legacy, 2);

    assertEquals(target, station.targetId);
    assertEquals(1, station.ammo);
    assertEquals(7, station.cooldown);
    assertEquals(13, station.reload);
    assertEquals(0, station.settledTicks);
    assertEquals(Vec3.ZERO, station.aimVector);
    CompoundTag saved = (CompoundTag) codec.getMethod("writeWeaponStation", Station.class)
        .invoke(null, station);
    assertFalse(saved.contains("Scan"));
    assertFalse(saved.contains("Settled"));
    assertFalse(saved.contains("AimX"));
  }

  private static Class<?> compiledCodec(String owner) throws Exception {
    String root = "com/liucodex/" + owner + "/entity/";
    String entityName = owner.equals("noise") ? "NoiseVehicleEntity" : "SilentHeavenVehicleEntity";
    String stateName = owner.equals("noise")
        ? "AbstractNoiseVehicleState$WeaponStation"
        : "AbstractSilentHeavenVehicleState$WeaponStation";
    String subjectName = root + "ExecutedStationPersistence";
    ClassNode source = new ClassNode();
    try (InputStream stream = NoiseStationPersistenceTest.class.getClassLoader()
        .getResourceAsStream(root + entityName + ".class")) {
      org.junit.jupiter.api.Assertions.assertNotNull(stream);
      new ClassReader(stream).accept(source, 0);
    }
    ClassNode subject = new ClassNode();
    subject.version = Opcodes.V17;
    subject.access = Opcodes.ACC_PUBLIC;
    subject.name = subjectName;
    subject.superName = "java/lang/Object";
    for (MethodNode method : source.methods) {
      if (method.name.equals("readWeaponStation") || method.name.equals("writeWeaponStation")) {
        method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        subject.methods.add(method);
      }
    }
    Map<String, String> names = new HashMap<>();
    names.put(root + entityName, subjectName);
    names.put(root + stateName, Type.getInternalName(Station.class));
    ClassWriter writer = new ClassWriter(0);
    subject.accept(new ClassRemapper(writer, new SimpleRemapper(names)));
    byte[] bytes = writer.toByteArray();
    return new ClassLoader(NoiseStationPersistenceTest.class.getClassLoader()) {
      Class<?> define() { return defineClass(subjectName.replace('/', '.'), bytes, 0, bytes.length); }
    }.define();
  }

  public static class Station {
    public UUID targetId;
    public int ammo;
    public int cooldown;
    public int reload;
    public int settledTicks;
    public Vec3 aimVector = Vec3.ZERO;
  }
}
