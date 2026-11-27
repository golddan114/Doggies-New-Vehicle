package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.security.MessageDigest;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class DnvM1ReloadSoundTest {
  @TempDir Path temp;

  @Test void exactDependencyIdentityIsPinned() throws Exception {
    assertEquals(SbwTestDependency.expectedSha256(),
        java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
            Files.readAllBytes(com.liucodex.doggiesnewvehicle.compat.SbwTestDependency.path()))));
  }

  @Test void reportedOneTickConfigurationReproducesIdleSoundInExactUpstream() throws Exception {
    var fixture = new ExactM1ReloadSoundFixture(temp, false);
    var m1 = new ExactM1ReloadSoundFixture.M1();
    var data = new ExactM1ReloadSoundFixture.Data();
    data.magazine = 10;
    data.rpm = 600;
    data.duration = 1;
    fixture.idle(m1, data, 200);
    assertEquals(200, m1.world.sounds, "Native zero timer matches the invalid zero cue every tick");
    assertFalse(data.reloading());
    assertEquals(0, data.completions, "Repeated sound is not repeated real reloading");
    var passenger = new ExactM1ReloadSoundFixture.Passenger();
    m1.passengers.add(passenger);
    fixture.idle(m1, data, 200);
    assertEquals(400, m1.world.sounds);
    assertEquals(200, passenger.sounds, "Entering does not fix the condition");
  }

  static Stream<Arguments> parameters() {
    return Stream.of(1, 10).flatMap(magazine -> Stream.of(60, 600).flatMap(rpm ->
        Stream.of(1, 2, 3, 200).flatMap(duration -> Stream.of(false, true)
            .map(openBolt -> Arguments.of(magazine, rpm, duration, openBolt)))));
  }

  @ParameterizedTest @MethodSource("parameters")
  void idleReloadFinishExitAndReloadedEntityKeepCorrectAudio(
      int magazine, int rpm, int duration, boolean openBolt) throws Exception {
    var fixture = new ExactM1ReloadSoundFixture(temp, true);
    var m1 = new ExactM1ReloadSoundFixture.M1();
    var data = data(magazine, rpm, duration, openBolt);
    fixture.idle(m1, data, 200);
    assertEquals(0, m1.world.sounds);
    var passenger = new ExactM1ReloadSoundFixture.Passenger();
    m1.passengers.add(passenger);
    reload(fixture, m1, data);
    assertEquals(1, m1.world.sounds);
    assertEquals(1, passenger.sounds);
    assertEquals(1, data.completions);
    assertEquals(0, data.reload.time());
    assertFalse(data.reloading());
    fixture.idle(m1, data, 200);
    assertEquals(1, m1.world.sounds);
    m1.passengers.clear();
    data.ammo = 0;
    reload(fixture, m1, data);
    assertEquals(2, m1.world.sounds);
    assertEquals(1, passenger.sounds, "Exited passenger receives no further local sound");
    fixture.idle(m1, data, 200);
    assertEquals(2, m1.world.sounds);
    assertEquals(magazine, data.magazine);
    assertEquals(rpm, data.rpm);
    assertEquals(duration, data.duration);
    // Models the restored idle projection; actual world NBT load is a separate field acceptance.
    var restored = new ExactM1ReloadSoundFixture.M1();
    fixture.idle(restored, data(magazine, rpm, duration, openBolt), 200);
    assertEquals(0, restored.world.sounds);
  }

  @ParameterizedTest @ValueSource(ints = {-30, 0, 1, 30, 1000})
  void oneTickReloadUsesExactlyOneCueIncludingAutomaticCue(int cue) throws Exception {
    var fixture = new ExactM1ReloadSoundFixture(temp, true);
    var m1 = new ExactM1ReloadSoundFixture.M1();
    var data = data(10, 600, 1, false);
    data.sound.cue = cue;
    reload(fixture, m1, data);
    fixture.idle(m1, data, 200);
    assertEquals(1, m1.world.sounds);
  }

  @Test void nativeCountdownCompletionAndAmmoSinkCallsAreUnchanged() throws Exception {
    var original = new ExactM1ReloadSoundFixture(temp, false);
    var patched = new ExactM1ReloadSoundFixture(temp, true);
    for (int duration : new int[]{1, 2, 3, 200}) {
      for (boolean openBolt : new boolean[]{false, true}) {
        assertEquals(timeline(original, duration, openBolt), timeline(patched, duration, openBolt));
      }
    }
  }

  @Test void otherVehiclesKeepNativeBehaviorAndIdleStaleTimersDoNotCreateM1Sounds() throws Exception {
    var fixture = new ExactM1ReloadSoundFixture(temp, true);
    var other = new ExactM1ReloadSoundFixture.Vehicle();
    fixture.idle(other, data(10, 600, 1, false), 200);
    assertEquals(200, other.world.sounds, "M1 repair must not silently patch other SBW vehicles");
    var m1 = new ExactM1ReloadSoundFixture.M1();
    var stale = data(1, 60, 200, false);
    stale.reload.remaining = 30;
    fixture.tick(m1, stale);
    assertEquals(0, m1.world.sounds, "A timer without a real reload state is not an audio event");
    for (int invalid : new int[]{0, -1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1}) {
      for (int cue : new int[]{0, 30}) {
        var malformed = data(10, 600, invalid, false);
        malformed.sound.cue = cue;
        fixture.idle(m1, malformed, 100);
        malformed.reload.remaining = 2;
        malformed.reload.state = ExactM1ReloadSoundFixture.State.EMPTY_RELOADING;
        fixture.tick(m1, malformed);
        assertEquals(0, m1.world.sounds);
      }
    }
  }

  @Test void realMixinRedirectsOnlyAudioReadAndLeavesThreeNativeReadsAndNoStateWrites() throws Exception {
    var application = new DnvMuzzleMixinApplicationTest();
    application.temp = temp;
    try (var engine = application.loader()) {
      byte[][] bytes = DnvMuzzleMixinApplicationTest.transform(engine, "event/GunEventHandler",
          "M1ReloadSoundMixin", "SERVER", false);
      ClassNode before = node(bytes[0]), after = node(bytes[1]);
      var original = before.methods.stream().filter(m -> m.name.equals("gunTick") && m.desc.endsWith("ZD)V"))
          .findFirst().orElseThrow();
      var patched = after.methods.stream().filter(m -> m.name.equals("gunTick") && m.desc.endsWith("ZD)V"))
          .findFirst().orElseThrow();
      assertEquals(4, calls(original).stream().filter(c -> c.owner.endsWith("/Reload") && c.name.equals("time")).count());
      var reads = calls(patched).stream().filter(c -> c.name.contains("dnv$reloadSoundComparisonTime")
          || (c.owner.endsWith("/Reload") && c.name.equals("time"))).toList();
      assertEquals(4, reads.size());
      assertTrue(reads.get(0).name.contains("dnv$reloadSoundComparisonTime"));
      assertTrue(reads.subList(1, 4).stream().allMatch(c -> c.name.equals("time")));
      var handler = after.methods.stream().filter(m -> m.name.contains("dnv$reloadSoundComparisonTime"))
          .findFirst().orElseThrow();
      assertTrue(calls(handler).stream().noneMatch(c -> c.name.startsWith("set") || c.name.equals("reduce")));
      assertEquals(before.fields.size(), after.fields.size(), "No parallel reload or sound latch state");
    }
  }

  private static ExactM1ReloadSoundFixture.Data data(int magazine, int rpm, int duration, boolean openBolt) {
    var data = new ExactM1ReloadSoundFixture.Data();
    data.magazine = magazine;
    data.rpm = rpm;
    data.duration = duration;
    data.item.openBolt = openBolt;
    return data;
  }

  private static void reload(ExactM1ReloadSoundFixture fixture, ExactM1ReloadSoundFixture.Vehicle vehicle,
      ExactM1ReloadSoundFixture.Data data) throws Exception {
    data.reload.reloadStarter.requested = true;
    int prior = data.completions;
    for (int i = 0; i < data.duration + 3 && data.completions == prior; i++) fixture.tick(vehicle, data);
    assertEquals(prior + 1, data.completions, "Native completion must remain reachable");
  }

  private static List<String> timeline(ExactM1ReloadSoundFixture fixture, int duration, boolean openBolt) throws Exception {
    var m1 = new ExactM1ReloadSoundFixture.M1();
    var data = data(10, 600, duration, openBolt);
    data.reload.reloadStarter.requested = true;
    List<String> values = new ArrayList<>();
    for (int i = 0; i < duration + 5; i++) {
      fixture.tick(m1, data);
      values.add(data.reload.time() + ":" + data.reloading() + ":" + data.completions + ":" + data.ammo + ":" + data.saves);
    }
    return values;
  }

  private static ClassNode node(byte[] bytes) {
    ClassNode node = new ClassNode();
    new ClassReader(bytes).accept(node, 0);
    return node;
  }

  private static List<MethodInsnNode> calls(org.objectweb.asm.tree.MethodNode method) {
    return Arrays.stream(method.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
        .map(MethodInsnNode.class::cast).toList();
  }
}
