package com.liucodex.dnvapgametest;

import com.atsuishio.superbwarfare.Mod;
import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.atsuishio.superbwarfare.tools.CustomExplosion;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.parsers.ParserConfigurationException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native projectile regressions after retiring DNV post-launch ownership. */
@net.minecraftforge.fml.common.Mod("dnvapgametest")
@GameTestHolder("dnvapgametest")
@PrefixGameTestTemplate(false)
public final class DnvApRuntimeGameTests {
  public DnvApRuntimeGameTests() throws ParserConfigurationException {
    String report = System.getProperty("dnv.apGameTest.report");
    if (report == null || !new File(report).isAbsolute()) throw new IllegalStateException("Missing AP XML report path");
    GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(new File(report)));
  }

  @GameTest(template = "ap_arena")
  public static void exactModsAndNativeProjectileAreLoaded(GameTestHelper helper) {
    for (String id : new String[] {"superbwarfare", "doggiesnewvehicle", "curios",
        "geckolib", "simplebedrockmodel", "ponder", "flywheel", "cloth_config"}) {
      helper.assertTrue(ModList.get().isLoaded(id), "Required actual mod missing: " + id);
    }
    var type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("superbwarfare", "ac_130h"));
    helper.assertTrue(type != null && type != net.minecraft.world.entity.EntityType.PIG, "Native AC-130H registry missing");
    var vehicle = type.create(helper.getLevel());
    helper.assertTrue(vehicle != null && vehicle.getClass().getName().endsWith(".Ac130hEntity"), "Wrong AC-130 runtime");
    helper.assertTrue(ModList.get().isLoaded("dragonrise_reforge") == Boolean.getBoolean("dnv.withDragonrise"),
        "Wrong optional-mod combination");
    CannonShellEntity shell = com.atsuishio.superbwarfare.init.ModEntities.CANNON_SHELL.get().create(helper.getLevel());
    helper.assertTrue(shell != null && shell.getType() == com.atsuishio.superbwarfare.init.ModEntities.CANNON_SHELL.get(),
        "Exact native shell missing");
    helper.assertTrue(java.util.Arrays.stream(shell.getClass().getInterfaces())
        .noneMatch(typeInterface -> typeInterface.getName().startsWith("com.liucodex.doggiesnewvehicle")),
        "Native projectile still has a DNV ownership interface");
    helper.succeed();
  }

  @GameTest(template = "ap_arena", timeoutTicks = 40)
  public static void nativeServerQueueExecutesOnce(GameTestHelper helper) {
    AtomicInteger calls = new AtomicInteger();
    helper.assertTrue(Mod.Companion.queueServerWork(2, calls::incrementAndGet), "Native queue rejected server work");
    helper.runAfterDelay(6, () -> {
      helper.assertTrue(calls.get() == 1, "Native delayed queue must execute exactly once");
      helper.succeed();
    });
  }

  @GameTest(template = "ap_arena", timeoutTicks = 40)
  public static void nativeExplosionStillDamagesWorld(GameTestHelper helper) {
    Fixture fixture = fixture(helper);
    ExplosionEvents events = new ExplosionEvents(fixture.shell);
    MinecraftForge.EVENT_BUS.register(events);
    try {
      new CustomExplosion.Builder(fixture.shell).position(fixture.center).damage(20).radius(4).explode();
    } finally {
      MinecraftForge.EVENT_BUS.unregister(events);
    }
    helper.assertTrue(events.calls == 1, "Unbound native Builder no longer reaches Forge callback");
    helper.runAfterDelay(6, () -> {
      helper.assertTrue(fixture.pig.getHealth() < fixture.health, "Native explosion did not damage the actual entity");
      helper.assertTrue(helper.getLevel().getBlockState(fixture.block).isAir(), "Native explosion did not destroy the actual block");
      helper.succeed();
    });
  }

  @GameTest(template = "ap_arena", timeoutTicks = 40)
  public static void savedNativeShellKeepsMotionAndExplosion(GameTestHelper helper) throws Exception {
    restoreNative(helper, false, false);
  }

  @GameTest(template = "ap_arena", timeoutTicks = 40)
  public static void legacyMarkerDoesNotRejectFactoryRestoration(GameTestHelper helper) throws Exception {
    restoreNative(helper, true, false);
  }

  @GameTest(template = "ap_arena", timeoutTicks = 40)
  public static void legacyMarkerDoesNotRejectDirectLoad(GameTestHelper helper) throws Exception {
    restoreNative(helper, true, true);
  }

  private static void restoreNative(GameTestHelper helper, boolean legacyMarker, boolean direct) throws Exception {
    Fixture fixture = fixture(helper);
    fixture.shell.setExplosionDamage(20);
    fixture.shell.setExplosionRadius(4);
    fixture.shell.setExplosionDestroy(true);
    fixture.shell.setDeltaMovement(0.25, 0, 0);
    CompoundTag saved = new CompoundTag();
    helper.assertTrue(fixture.shell.save(saved), "Native shell did not save");
    helper.assertTrue(!saved.contains("dnv:ap_effect_bound"), "New native save still writes the retired constraint");
    if (legacyMarker) saved.putBoolean("dnv:ap_effect_bound", true);
    var bytes = new java.io.ByteArrayOutputStream();
    NbtIo.writeCompressed(saved, bytes);
    CompoundTag tag = NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes.toByteArray()));
    CannonShellEntity restored;
    if (direct) {
      restored = newShell(helper);
      restored.load(tag);
    } else {
      restored = (CannonShellEntity) EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
    }
    helper.assertTrue(restored != null && !restored.isRemoved(), "Native restoration rejected");
    helper.assertTrue(helper.getLevel().addFreshEntity(restored) && restored.isAddedToWorld(), "Restored shell not admitted");
    Vec3 before = restored.position();
    restored.tick();
    helper.assertTrue(!before.equals(restored.position()), "Native movement stopped");
    restored.causeExplode(fixture.center);
    helper.runAfterDelay(6, () -> {
      helper.assertTrue(fixture.pig.getHealth() < fixture.health
          && helper.getLevel().getBlockState(fixture.block).isAir() && restored.getExploded(),
          "Restored shell lost native damage or block destruction");
      helper.succeed();
    });
  }

  private static CannonShellEntity newShell(GameTestHelper helper) {
    return com.atsuishio.superbwarfare.init.ModEntities.CANNON_SHELL.get().create(helper.getLevel());
  }

  private static Fixture fixture(GameTestHelper helper) {
    Vec3 center = helper.absoluteVec(new Vec3(7.5, 3.5, 7.5));
    BlockPos block = BlockPos.containing(center);
    helper.getLevel().setBlockAndUpdate(block, Blocks.GLASS.defaultBlockState());
    Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 8.5F, 3.5F, 7.5F);
    pig.setNoGravity(true);
    CannonShellEntity shell = newShell(helper);
    shell.setPos(center.add(0, 2, 0));
    shell.setNoGravity(true);
    return new Fixture(shell, pig, pig.getHealth(), block, center);
  }

  private record Fixture(CannonShellEntity shell, Pig pig, float health, BlockPos block, Vec3 center) {}

  public static final class ExplosionEvents {
    private final CannonShellEntity source;
    private int calls;
    ExplosionEvents(CannonShellEntity source) { this.source = source; }
    @SubscribeEvent public void onStart(ExplosionEvent.Start event) {
      if (event.getExplosion().getDirectSourceEntity() == source) calls++;
    }
  }
}
