package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Exact argument flow is evidence of placement wiring, not a receipt that a world accepted an entity. */
class DnvMuzzlePipelineBytecodeTest {
  private static final String PREFIX = "com/atsuishio/superbwarfare/";

  @Test void namedAndSeatedDirectionsRemainUpstreamWithoutRetiredBinding() throws Exception {
    ClassNode utils = read("entity/vehicle/utils/VehicleVecUtils");
    var named = method(utils, "getShootVec", "(L" + PREFIX + "entity/vehicle/base/VehicleEntity;Ljava/lang/String;F)Lnet/minecraft/world/phys/Vec3;");
    var seated = method(utils, "getShootVec", "(L" + PREFIX + "entity/vehicle/base/VehicleEntity;Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/world/phys/Vec3;");
    assertEquals("(Ljava/lang/String;F)Lnet/minecraft/world/phys/Vec3;", vectorCall(named).desc);
    assertEquals("(Ljava/lang/String;FI)Lnet/minecraft/world/phys/Vec3;", vectorCall(seated).desc);
    ClassNode vehicle = read("entity/vehicle/base/VehicleEntity");
    assertFalse(instructions(method(vehicle, "registerTransforms", null)).stream()
        .anyMatch(i -> i instanceof LdcInsnNode constant && "Passenger".equals(constant.cst)));
    assertTrue(instructions(method(vehicle, "getVectorFromString", "(Ljava/lang/String;FI)Lnet/minecraft/world/phys/Vec3;")).stream()
        .anyMatch(i -> i instanceof LdcInsnNode constant && "Passenger".equals(constant.cst)));
    assertNull(getClass().getResource("/com/liucodex/doggiesnewvehicle/compat/DnvAc130MuzzleBinding.class"));
  }

  @Test void nativeSpawnUsesAllThreeMuzzleCoordinatesAndLaunchDirectionBeforeWorldAdmission() throws Exception {
    MethodNode shoot = method(read("item/gun/GunItem"), "shootBullet", "(L" + PREFIX + "data/gun/ShootParameters;)Z");
    var code = instructions(shoot);
    int positionSlot = parameterSlot(code, "shootPosition");
    int directionSlot = parameterSlot(code, "shootDirection");
    MethodInsnNode setPosition = code.stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
        .filter(i -> i.name.equals("m_6034_") && i.desc.equals("(DDD)V")).findFirst().orElseThrow();
    AbstractInsnNode cursor = previous(setPosition);
    for (String coordinate : List.of("f_82481_", "f_82480_", "f_82479_")) {
      assertEquals(coordinate, ((FieldInsnNode) cursor).name);
      cursor = previous(cursor);
      assertEquals(positionSlot, ((VarInsnNode) cursor).var);
      assertEquals(Opcodes.ALOAD, cursor.getOpcode());
      cursor = previous(cursor);
    }
    long directionComponents = code.stream().filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
        .filter(i -> List.of("f_82479_", "f_82480_", "f_82481_").contains(i.name))
        .filter(i -> previous(i) instanceof VarInsnNode local && local.var == directionSlot).count();
    assertEquals(3, directionComponents);
    int launch = indexOfCall(code, "m_6686_");
    int admission = indexOfCall(code, "m_7967_");
    assertTrue(code.indexOf(setPosition) < launch && launch < admission);
    // Upstream discards addFreshEntity's boolean: even shootBullet's true isn't an admission receipt.
    assertEquals(Opcodes.POP, next(code.get(admission)).getOpcode());
  }

  private static int parameterSlot(List<AbstractInsnNode> code, String name) {
    FieldInsnNode field = code.stream().filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
        .filter(i -> i.owner.equals(PREFIX + "data/gun/ShootParameters") && i.name.equals(name)).findFirst().orElseThrow();
    assertEquals(Opcodes.ASTORE, next(field).getOpcode());
    return ((VarInsnNode) next(field)).var;
  }

  @Test void falseBulletResultReturnsBeforeNativeAfterShootAndItsAmmoWriters() throws Exception {
    ClassNode gun = read("item/gun/GunItem");
    var shoot = instructions(method(gun, "shoot", "(L" + PREFIX + "data/gun/ShootParameters;)V"));
    var call = shoot.get(indexOfCall(shoot, "shootBullet"));
    assertEquals(Opcodes.IFNE, next(call).getOpcode());
    assertEquals(Opcodes.RETURN, next(next(call)).getOpcode(), "false must exit before afterShoot, heat and ammo");
    assertTrue(indexOfCall(shoot, "afterShoot") > shoot.indexOf(call));
    var after = instructions(method(gun, "afterShoot", "(L" + PREFIX + "data/gun/ShootParameters;)V"));
    assertTrue(after.stream().anyMatch(i -> i instanceof MethodInsnNode m && m.name.equals("consumeBackupAmmo")));
    assertTrue(after.stream().anyMatch(i -> i instanceof FieldInsnNode f && f.name.equals("ammo")));
    assertTrue(after.stream().anyMatch(i -> i instanceof MethodInsnNode m && m.owner.endsWith("/value/IntValue") && m.name.equals("set")));
  }
  private static int indexOfCall(List<AbstractInsnNode> code, String name) {
    return code.indexOf(code.stream().filter(i -> i instanceof MethodInsnNode call && call.name.equals(name)).findFirst().orElseThrow());
  }
  private static MethodInsnNode vectorCall(MethodNode method) {
    return instructions(method).stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
        .filter(i -> i.name.equals("getVectorFromString")).findFirst().orElseThrow();
  }
  private static AbstractInsnNode previous(AbstractInsnNode node) { do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0); return node; }
  private static AbstractInsnNode next(AbstractInsnNode node) { do { node = node.getNext(); } while (node != null && node.getOpcode() < 0); return node; }
  private static List<AbstractInsnNode> instructions(MethodNode method) { return Arrays.asList(method.instructions.toArray()); }
  private static MethodNode method(ClassNode type, String name, String descriptor) {
    return type.methods.stream().filter(i -> i.name.equals(name) && (descriptor == null || i.desc.equals(descriptor))).findFirst().orElseThrow();
  }
  private static ClassNode read(String name) throws Exception {
    try (ZipFile zip = new ZipFile(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path().toFile())) {
      ClassNode node = new ClassNode();
      new ClassReader(zip.getInputStream(zip.getEntry(PREFIX + name + ".class"))).accept(node, 0);
      return node;
    }
  }
}
