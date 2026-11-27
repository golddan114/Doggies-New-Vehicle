package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.compat.ExactApBlockFixture.Sound;
import com.liucodex.doggiesnewvehicle.compat.ExactApBlockFixture.State;
import org.junit.jupiter.api.Test;

class DnvApBlockRuntimeTest {
  @Test void nativeDurabilityStopsOpaqueOrGlassHighHardnessBeforeAnyNegativeMultiplier() throws Exception {
    var fixture = new ExactApBlockFixture();
    assertEquals(50, fixture.nativeDurability);
    for (State material : new State[] {State.material(100, true, Sound.OTHER), State.material(100, false, Sound.f_56744_)}) {
      var shell = fixture.create(material, true);
      var velocity = shell.velocity;
      fixture.hit(shell);
      assertTrue(shell.removed);
      assertEquals(1, shell.explosions);
      assertEquals(0, shell.level.destroys);
      assertEquals(velocity, shell.velocity);
    }
  }

  @Test void fullHandlerCanReverseOnNonOccludingMaterialEvenWhenWorldRejectsDestruction() throws Exception {
    var fixture = new ExactApBlockFixture();
    for (boolean destroyed : new boolean[] {false, true}) {
      var shell = fixture.create(State.material(100, false, Sound.f_56742_), destroyed);
      var velocity = shell.velocity;
      fixture.hit(shell);
      assertFalse(shell.removed);
      assertEquals(0, shell.explosions);
      assertEquals(1, shell.level.destroys);
      assertEquals(45, shell.durability);
      assertTrue(shell.velocity.dot(velocity) < 0);
      assertEquals(-0.05 * velocity.x, shell.velocity.x, 1E-7);
      assertEquals(15.01, shell.position.x, 1E-7, "the loop retains the incoming direction for all 30 advances");
      assertTrue(shell.damage < 0 && shell.explosionDamage < 0 && shell.radius < 0);
    }
    // Material properties are controlled inputs: this does not identify a reachable real-world block.
  }

  @Test void penetrationBudgetAdvancesThroughAirAndUnbreakableMaterialTerminatesWithExplosion() throws Exception {
    var fixture = new ExactApBlockFixture();
    var shell = fixture.create(State.air(), false);
    fixture.hit(shell);
    assertEquals(15.01, shell.position.x, 1E-7);
    assertTrue(shell.level.reads <= 16);
    assertEquals(0, shell.level.destroys + shell.explosions);
    assertFalse(shell.removed);
    shell = fixture.create(State.material(-1, false, Sound.OTHER), false);
    fixture.hit(shell);
    assertEquals(1, shell.explosions);
    assertTrue(shell.removed);
    assertEquals(0, shell.level.destroys);
  }

  @Test void disabledDestructionUsesOriginalFallbackWithoutClaimingItStops() throws Exception {
    var fixture = new ExactApBlockFixture();
    var shell = fixture.create(State.material(1, true, Sound.OTHER), true);
    ExactApBlockFixture.Config.EXTRA_EXPLOSION_EFFECT.value = false;
    fixture.hit(shell);
    assertEquals(1, shell.fallback);
    assertEquals(0, shell.level.reads + shell.level.destroys);
    assertEquals(0, shell.explosions);
  }
}
