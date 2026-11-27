package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class DnvNativeIndicatorBytecodeContractTest {
  @Test void missingNuclearBindingNeverReportsAnM1Failure() {
    var silo=com.liucodex.doggiesnewvehicle.api.combat.DnvPlayerArtilleryTarget.SILO_INDICATOR_TYPE;
    var m1=com.liucodex.doggiesnewvehicle.api.combat.DnvPlayerArtilleryTarget.M1_INDICATOR_TYPE;
    assertEquals("message.doggiesnewvehicle.nuclear.indicator_unavailable",DnvM1IndicatorOrders.unavailableKey(silo,null,true));
    assertEquals("message.doggiesnewvehicle.m1_indicator.unavailable",DnvM1IndicatorOrders.unavailableKey(m1,null,true));
  }
  private static final Path JAR = com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path();
  private static final String SHA = SbwTestDependency.expectedSha256();

  @Test
  void exactJarContainsNativeIndicatorAndTerminalResources() throws Exception {
    assertEquals(SHA, java.util.HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
    try (ZipFile zip = new ZipFile(JAR.toFile())) {
      for (String entry : new String[]{
          "assets/superbwarfare/models/item/artillery_indicator.json",
          "assets/superbwarfare/models/item/tactical_terminal.json",
          "com/atsuishio/superbwarfare/client/screens/ArtilleryIndicatorScreen.class",
          "com/atsuishio/superbwarfare/client/screens/TacticalMapScreen.class"}) {
        assertNotNull(zip.getEntry(entry), entry);
      }
    }
  }

  @Test
  void exactParametersUseAllFiveTypedNativeKeys() throws Exception {
    ClassNode node = node("com/atsuishio/superbwarfare/item/misc/FiringParametersItemKt");
    Set<String> constants = new HashSet<>();
    node.methods.forEach(method -> method.instructions.forEach(instruction -> {
      if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String text) constants.add(text);
    }));
    assertTrue(constants.containsAll(Set.of(
        "TargetX", "TargetY", "TargetZ", "Radius", "IsDepressed")));
    assertMethod(node, "getFiringParameters",
        "(Lnet/minecraft/world/item/ItemStack;)Lcom/atsuishio/superbwarfare/item/misc/"
            + "FiringParametersItem$Parameters;");
    assertMethod(node, "setFiringParameters",
        "(Lnet/minecraft/world/item/ItemStack;Lcom/atsuishio/superbwarfare/item/misc/"
            + "FiringParametersItem$Parameters;)V");
  }

  @Test
  void exactTacticalScreenKeepsTheRouteHookContract() throws Exception {
    ClassNode tactical = node("com/atsuishio/superbwarfare/client/screens/TacticalMapScreen");
    assertTrue((tactical.access & Opcodes.ACC_FINAL) != 0);
    assertMethod(tactical, "m_7856_", "()V");
    MethodNode render = assertMethod(tactical, "m_88315_",
        "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V");
    assertMethod(tactical, "m_6375_", "(DDI)Z");
    int recompute = callIndex(render, "recomputeLayout");
    int markers = callIndex(render, "renderPositionMarkers");
    assertTrue(recompute >= 0 && markers > recompute);
  }

  @Test
  void dnvRouteHookConsumesMapClicksOnlyWhileEditingAndLeavesNativeInputsUninjected()
      throws Exception {
    String source = Files.readString(Path.of("src/platform/java/com/liucodex/doggiesnewvehicle/compat/mixin/"
        + "TacticalMapScreenMixin.java"), StandardCharsets.UTF_8);
    assertFalse(source.contains("Screen.hasShiftDown()"));
    assertTrue(source.contains("button == 0"));
    assertTrue(source.contains("if (!dnv$routeMode || !dnv$isInsideMap(mouseX, mouseY)) return;"));
    assertTrue(source.contains("DnvTacticalRouteNetwork.commit"));
    assertFalse(source.contains("DnvNativeFireSession"));
    assertFalse(source.contains("DnvCombatIntentView"));
    assertFalse(Files.exists(Path.of("src/main/java/com/liucodex/doggiesnewvehicle/"
        + "mission/DnvNativeFireSession.java")));
    assertFalse(Files.exists(Path.of("src/main/java/com/liucodex/doggiesnewvehicle/"
        + "compat/DnvTacticalFireOverlay.java")));
    String combatPanel = Files.readString(Path.of("src/main/java/com/liucodex/"
        + "doggiesnewvehicle/client/DnvCombatOrderPanel.java"), StandardCharsets.UTF_8);
    assertTrue(combatPanel.contains("DnvCombatUi.Action.DESIGNATE_ENTITY"));
    assertTrue(source.contains("renderPositionMarkers"));
    assertFalse(source.contains("method = {\"mouseDragged\""));
    assertFalse(source.contains("method = {\"mouseScrolled\""));
    assertFalse(source.contains("method = {\"keyPressed\""));
    assertFalse(source.contains("method = {\"charTyped\""));
  }

  @Test
  void exactKillFeedReadsTheVehicleIconAndDrawsItsFourToOneSilhouette() throws Exception {
    ClassNode overlay = node("com/atsuishio/superbwarfare/client/overlay/KillMessageOverlay");
    MethodNode lookup = assertMethod(overlay, "getWeaponIcon",
        "(Lcom/atsuishio/superbwarfare/tools/LivingKillRecord;)Lnet/minecraft/resources/ResourceLocation;");
    boolean vehicleIcon = false;
    for (AbstractInsnNode instruction : lookup.instructions) {
      if (instruction instanceof MethodInsnNode call
          && call.owner.equals("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity")
          && call.name.equals("getVehicleIcon")) vehicleIcon = true;
    }
    assertTrue(vehicleIcon, "The M1 data resource must feed the actual native kill lookup");
    boolean horizontalBlit = false;
    for (MethodNode method : overlay.methods) {
      for (AbstractInsnNode instruction : method.instructions) {
        if (!(instruction instanceof MethodInsnNode call) || !call.name.equals("preciseBlit")) continue;
        var last = previousOpcode(instruction);
        if (last instanceof LdcInsnNode h && h.cst.equals(8.0F)
            && previousOpcode(last) instanceof LdcInsnNode w && w.cst.equals(-32.0F)
            && previousOpcode(w) instanceof LdcInsnNode sourceH && sourceH.cst.equals(8.0F)
            && previousOpcode(sourceH) instanceof LdcInsnNode sourceW && sourceW.cst.equals(32.0F)) {
          horizontalBlit = true;
        }
      }
    }
    assertTrue(horizontalBlit, "Pin the actual 32x8 native draw/UV aspect rather than stretching a square image");
  }

  private static AbstractInsnNode previousOpcode(AbstractInsnNode instruction) {
    var previous = instruction.getPrevious();
    while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
    return previous;
  }

  private static ClassNode node(String name) throws Exception {
    try (ZipFile zip = new ZipFile(JAR.toFile());
         InputStream input = zip.getInputStream(zip.getEntry(name + ".class"))) {
      ClassNode node = new ClassNode();
      new ClassReader(input).accept(node, 0);
      return node;
    }
  }

  private static MethodNode assertMethod(ClassNode node, String name, String descriptor) {
    MethodNode method = node.methods.stream()
        .filter(value -> value.name.equals(name) && value.desc.equals(descriptor))
        .findFirst().orElse(null);
    assertNotNull(method, name + descriptor);
    return method;
  }

  private static int callIndex(MethodNode method, String name) {
    int index = 0;
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof MethodInsnNode call && call.name.equals(name)) return index;
      index++;
    }
    return -1;
  }
}
