package com.liucodex.doggiesnewvehicle.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DnvM1WeaponFeedbackContractTest {
  private static final Path DATA = Path.of(
      "src/artillery/resources/data/doggievehicle/sbw/vehicles/m1_yunlong.json");

  @Test void heavyShellUsesTheValidatedSbwBallisticBaselineAndExpandedBlast() throws Exception {
    JsonObject weapon = weapon();
    double velocity = weapon.get("Velocity").getAsDouble();
    double gravity = weapon.get("Gravity").getAsDouble();
    assertEquals(15.0D, velocity);
    assertEquals(0.06D, gravity);
    assertEquals("superbwarfare:cannon_shell", weapon.get("Projectile").getAsString());
    assertEquals(550.0D, weapon.get("Damage").getAsDouble());
    assertEquals(280.0D, weapon.get("ExplosionDamage").getAsDouble());
    assertEquals(240, weapon.get("EmptyReloadTime").getAsInt());
    assertEquals(12.0D, weapon.get("ExplosionRadius").getAsDouble());
    assertTrue(weapon.get("ExplosionDestroy").getAsBoolean());

    double flightTicksAt100Blocks = 100.0D / velocity;
    double approximateDrop = 0.5D * gravity * flightTicksAt100Blocks * flightTicksAt100Blocks;
    assertTrue(approximateDrop < 1.5D, "100-block drop regressed to " + approximateDrop);
  }

  @Test void recoilShakeAndM1SoundRemainOneCoherentHeavyCannonFeedbackSet() throws Exception {
    JsonObject weapon = weapon();
    assertEquals(42, weapon.get("RecoilTime").getAsInt());
    var shake = weapon.getAsJsonArray("ShootShake");
    assertEquals(12.0F, shake.get(0).getAsFloat());
    assertEquals(14.0F, shake.get(1).getAsFloat());
    assertEquals(80.0F, shake.get(2).getAsFloat());
    assertEquals(64.0D, weapon.get("SoundRadius").getAsDouble());
    var sound = weapon.getAsJsonObject("SoundInfo");
    assertEquals("doggiesnewvehicle:m1_cannon_fire", sound.get("Fire1P").getAsString());
    assertEquals("doggiesnewvehicle:m1_cannon_fire", sound.get("Fire3P").getAsString());

    JsonObject soundEvents = JsonParser.parseString(Files.readString(Path.of(
        "src/artillery/resources/assets/doggiesnewvehicle/sounds.json"))).getAsJsonObject();
    JsonObject m1Sample = soundEvents.getAsJsonObject("m1_cannon_fire")
        .getAsJsonArray("sounds").get(0).getAsJsonObject();
    assertEquals("doggiesnewvehicle:cannon_fire_01", m1Sample.get("name").getAsString());
    assertEquals(1.25D, m1Sample.get("volume").getAsDouble());
    assertFalse(soundEvents.getAsJsonObject("cannon_fire_01")
        .getAsJsonArray("sounds").get(0).getAsJsonObject().has("volume"));
    assertTrue(Files.isRegularFile(Path.of(
        "src/artillery/resources/assets/doggiesnewvehicle/sounds/cannon_fire_01.ogg")));
    assertTrue(Files.readString(Path.of(
        "src/artillery/java/com/liucodex/doggiesnewvehicle/artillery/DnvArtillerySounds.java"))
        .contains("register(\"m1_cannon_fire\")"));
    JsonObject changfeng = JsonParser.parseString(Files.readString(Path.of(
        "src/artillery/resources/data/doggievehicle/sbw/vehicles/50_changfeng_cannon.json")))
        .getAsJsonObject().getAsJsonObject("Weapons").getAsJsonObject("Main");
    assertEquals("doggiesnewvehicle:cannon_fire_01",
        changfeng.getAsJsonObject("SoundInfo").get("Fire3P").getAsString());

    String entity = Files.readString(Path.of(
        "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/DnvM1YunlongEntity.java"));
    assertTrue(entity.contains("if(admittedProjectile) publishAcceptedShot"));
    assertTrue(entity.contains("super.turretTurnSound(diffX * 2.0F, diffY * 2.0F, pitch)"));
    assertTrue(entity.contains("RECOIL_TICKS = 42.0F"));
  }

  @Test void manualM1TurnSpeedImprovesButStaysBelowNativeArtillery() throws Exception {
    var turn = JsonParser.parseString(Files.readString(DATA)).getAsJsonObject()
        .getAsJsonArray("TurretTurnSpeed");
    assertEquals(0.4D, turn.get(0).getAsDouble());
    assertEquals(0.6D, turn.get(1).getAsDouble());
    assertTrue(turn.get(0).getAsDouble() < 1.25D);
    assertTrue(turn.get(1).getAsDouble() < 1.75D);
  }

  @Test void manualLampProjectionUsesPowerBeforeDamageBlinkRatherThanAiActivation() throws Exception {
    String entity = Files.readString(Path.of(
        "src/artillery/java/com/liucodex/doggiesnewvehicle/entity/DnvM1YunlongEntity.java"));
    String bridge = Files.readString(Path.of(
        "src/artillery/java/com/liucodex/doggiesnewvehicle/client/DnvM1ClientBridge.java"));
    String renderer = Files.readString(Path.of(
        "src/artillery/java/com/liucodex/doggiesnewvehicle/client/DnvDragonTurretRenderer.java"));
    assertTrue(entity.contains("hasEnergyStorage() && getEnergy() > 0"),
        "SBW returns a positive sentinel when no energy storage exists");
    assertTrue(bridge.contains("m1.hasOperationalPower()"));
    assertTrue(renderer.contains("DnvTurretLampBrightness.value(state.powered(),state.damaged()"));
    assertEquals(0,com.liucodex.doggiesnewvehicle.client.DnvTurretLampBrightness.value(false,true,4));
    assertEquals(1,com.liucodex.doggiesnewvehicle.client.DnvTurretLampBrightness.value(true,false,12));
    assertTrue(com.liucodex.doggiesnewvehicle.client.DnvTurretLampBrightness.value(true,true,4)>
        com.liucodex.doggiesnewvehicle.client.DnvTurretLampBrightness.value(true,true,12));
    assertFalse(renderer.contains("state.activated()"));
  }

  private static JsonObject weapon() throws Exception {
    return JsonParser.parseString(Files.readString(DATA)).getAsJsonObject()
        .getAsJsonObject("Weapons").getAsJsonObject("Main");
  }
}
