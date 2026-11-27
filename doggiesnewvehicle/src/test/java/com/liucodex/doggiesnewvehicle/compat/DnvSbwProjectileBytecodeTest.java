package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Exact artifact/API evidence, not a Forge entity execution test. */
class DnvSbwProjectileBytecodeTest {
  private static final Path JAR = com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path();
  private static final String PREFIX = "com/atsuishio/superbwarfare/";

  @Test void smallCannonNeedsTheDirectHitBridgeBecauseNativeBypassSetterDoesNothing() throws Exception {
    String shell = "entity/projectile/SmallCannonShellEntity";
    assertEquals(PREFIX + "entity/projectile/FastThrowableProjectile", read(shell).superName);
    assertTrue(read(shell).methods.stream().noneMatch(m -> m.name.equals("setBypassArmorRate") || m.name.equals("getBypassArmorRate")));
    for (String name : List.of("setBypassArmorRate", "getBypassArmorRate"))
      assertTrue(calls(method("entity/projectile/FastThrowableProjectile", name, null))
          .contains(PREFIX + "entity/projectile/IBulletProperties$DefaultImpls." + name));
    assertEquals(List.of(org.objectweb.asm.Opcodes.RETURN), StreamSupport.stream(
        method("entity/projectile/IBulletProperties$DefaultImpls", "setBypassArmorRate", null).instructions.spliterator(), false)
        .map(i -> i.getOpcode()).filter(i -> i >= 0).toList());
    assertEquals(List.of(org.objectweb.asm.Opcodes.FCONST_0, org.objectweb.asm.Opcodes.FRETURN), StreamSupport.stream(
        method("entity/projectile/IBulletProperties$DefaultImpls", "getBypassArmorRate", null).instructions.spliterator(), false)
        .map(i -> i.getOpcode()).filter(i -> i >= 0).toList());
    assertTrue(read(shell).methods.stream().anyMatch(m -> calls(m).contains(PREFIX + "init/ModDamageTypes.causeProjectileHitDamage")));
  }

  @Test
  void dependencyAndProjectileSuperclassMatchTheInspectedModel() throws Exception {
    assertEquals(SbwTestDependency.expectedSha256(),
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
    assertEquals(PREFIX + "entity/projectile/FastThrowableProjectile",
        read("entity/projectile/CannonShellEntity").superName);
    MethodNode move = method("entity/projectile/FastThrowableProjectile", "projectileMove", null);
    List<String> calls = calls(move);
    assertTrue(calls.indexOf("net/minecraft/world/entity/projectile/ThrowableItemProjectile.m_8119_") < 0);
    assertTrue(calls.stream().anyMatch(value -> value.endsWith(".getCustomGravity")));
    assertTrue(calls.stream().anyMatch(value -> value.endsWith(".m_6034_")));
    assertTrue(calls.stream().noneMatch(value -> value.endsWith(".nextGaussian")));
  }

  @Test
  void zeroDelayUsesAQueueAndTheCallbackReSamplesMuzzleAndNativeReadiness() throws Exception {
    List<String> queue = calls(method("Mod$Companion", "queueServerWork", null));
    assertTrue(queue.contains("java/util/concurrent/ConcurrentLinkedQueue.add"));
    assertFalse(queue.contains("java/lang/Runnable.run"));
    String vehicle = "entity/vehicle/base/VehicleEntity";
    List<String> callback = calls(method(vehicle, "vehicleShoot$lambda$28$lambda$27", null));
    assertTrue(callback.contains(PREFIX + "data/gun/GunData.canShoot"));
    assertTrue(callback.contains(PREFIX + vehicle + ".getShootPos"));
    assertTrue(callback.contains(PREFIX + vehicle + ".getShootVec"));
    assertTrue(callback.contains(PREFIX + "data/gun/GunData.shoot"));
    assertTrue(callback.indexOf(PREFIX + vehicle + ".getShootPos")
        < callback.indexOf(PREFIX + "data/gun/GunData.shoot"));
  }

  @Test
  void actualMuzzleComesFromWeaponPositionAndTransformNotTargetLocation() throws Exception {
    List<String> calls = calls(method("entity/vehicle/base/VehicleEntity", "getShootPos",
        "(Ljava/lang/String;F)Lnet/minecraft/world/phys/Vec3;"));
    assertTrue(calls.contains(PREFIX + "data/gun/GunData.firePosition"));
    assertTrue(calls.contains(PREFIX + "entity/vehicle/base/VehicleEntity.getTransformFromString"));
    assertTrue(calls.contains(PREFIX + "entity/vehicle/base/VehicleEntity.transformPosition"));
  }

  @Test
  void armorPiercingImpactHasSecondaryTraceBeyondPrimaryHit() throws Exception {
    List<String> calls = calls(method("entity/projectile/CannonShellEntity", "afterHitEntity", null));
    assertTrue(calls.contains(PREFIX + "tools/TraceTool.getEntitiesAlongVector"));
    assertTrue(calls.contains(PREFIX + "entity/projectile/CannonShellEntity.causeExplode"));
  }

  @Test
  void stormUsesTheNativeRegistryProjectileParametersLaunchAndIndexOwner() throws Exception {
    var storm = new ClassNode();
    try (var input = getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/entity/StormBulletEntity.class")) {
      new ClassReader(input).accept(storm, 0);
    }
    assertEquals(PREFIX + "entity/projectile/ProjectileEntity", storm.superName);
    // Storm owns spawn synchronization only; native movement, impacts and damage remain inherited.
    assertEquals(List.of("<init>", "setCustomGravity", "getAddEntityPacket", "writeSpawnData", "readSpawnData"),
        storm.methods.stream().map(m -> m.name).toList());
    var constructor = calls(method("item/gun/GunItem", "shootBullet$lambda$19", null));
    for (String call : List.of("net/minecraft/world/entity/EntityType.m_20615_",
        "net/minecraft/world/entity/projectile/Projectile.m_5602_",
        PREFIX + "entity/projectile/IBulletProperties.setCustomGravity",
        PREFIX + "entity/projectile/IBulletProperties.setVelocity")) assertTrue(constructor.contains(call), call);
    var fire = calls(method("item/gun/GunItem", "shootBullet", "(L" + PREFIX + "data/gun/ShootParameters;)Z"));
    assertTrue(fire.indexOf("net/minecraft/world/entity/projectile/Projectile.m_6686_")
        < fire.indexOf("net/minecraft/server/level/ServerLevel.m_7967_"));
    var position = method("data/gun/GunData", "firePosition", null);
    assertTrue(StreamSupport.stream(position.instructions.spliterator(), false)
        .anyMatch(i -> i instanceof org.objectweb.asm.tree.FieldInsnNode f && f.name.equals("fireIndex")));
    var timer = method("item/gun/GunItem", "enableShootTimer", null);
    assertEquals(List.of(org.objectweb.asm.Opcodes.ICONST_0, org.objectweb.asm.Opcodes.IRETURN),
        StreamSupport.stream(timer.instructions.spliterator(), false).map(i -> i.getOpcode()).filter(i -> i >= 0).toList());
  }

  private static ClassNode read(String name) throws Exception {
    try (ZipFile zip = new ZipFile(JAR.toFile())) {
      var entry = zip.getEntry(PREFIX + name + ".class");
      assertNotNull(entry, name);
      ClassNode node = new ClassNode();
      new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(node, 0);
      return node;
    }
  }

  private static MethodNode method(String type, String name, String descriptor) throws Exception {
    return read(type).methods.stream().filter(method -> method.name.equals(name)
        && (descriptor == null || method.desc.equals(descriptor))).findFirst().orElseThrow();
  }

  private static List<String> calls(MethodNode method) {
    return StreamSupport.stream(method.instructions.spliterator(), false)
        .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
        .map(call -> call.owner + "." + call.name).toList();
  }
}
