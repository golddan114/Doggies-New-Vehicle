package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.item.misc.ArtilleryIndicatorItem;
import com.atsuishio.superbwarfare.item.misc.FiringParametersItem;
import com.atsuishio.superbwarfare.item.misc.FiringParametersItemKt;
import com.liucodex.doggiesnewvehicle.api.combat.DnvPlayerArtilleryTarget;
import com.liucodex.doggiesnewvehicle.compat.DnvRemoteWeaponStationContent;
import com.liucodex.doggiesnewvehicle.compat.DnvVehicleCrewServices;
import com.liucodex.doggiesnewvehicle.init.DnvM1Content;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exact-mod ServerLevel checks after the retired AC-130 positive fixture stopped compiling. */
@GameTestHolder("dnvapgametest")
@PrefixGameTestTemplate(false)
public final class DnvM1IndicatorGameTests {
  @GameTest(template = "ap_arena")
  public static void retiredAc130IdsHaveNoDnvCrewCatalog(GameTestHelper helper) {
    helper.assertTrue(DnvVehicleCrewServices.sbwCatalog().find("superbwarfare:ac_130h").isEmpty(),
        "Retired SBW carrier regained a DNV crew adapter");
    helper.assertTrue(DnvVehicleCrewServices.sbwCatalog().find("dragonrise_reforge:ac130").isEmpty(),
        "Retired optional carrier regained a DNV crew adapter");
    helper.succeed();
  }

  @GameTest(template = "ap_arena")
  public static void nativeM1EntityAndGunDataLoadWithoutDch(GameTestHelper helper) {
    helper.assertTrue(!ModList.get().isLoaded("doggiecasterhud"), "Independent DNV test unexpectedly loaded DCH");
    var turret = m1(helper);
    helper.assertTrue(DnvM1YunlongEntity.TYPE_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(turret.getType()).toString()),
        "M1 registration ID changed");
    helper.assertTrue(turret.getGunData("Main") != null, "Exact SBW Main GunData missing");
    helper.assertTrue(turret instanceof DnvPlayerArtilleryTarget, "M1 player order boundary missing");
    helper.assertTrue(turret.hasEnergyStorage(), "M1 native energy storage missing");
    turret.setActive(true);
    turret.setEnergy(0);
    helper.assertTrue(!turret.hasOperationalPower(), "AI activation lit an unpowered manual M1");
    turret.setActive(false);
    turret.setEnergy(500);
    helper.assertTrue(turret.hasOperationalPower(), "Powered manual M1 still depended on AI activation");
    turret.discard();
    helper.succeed();
  }

  @GameTest(template = "ap_arena")
  public static void nativeIndicatorBindsAndRemovesM1WithoutChangingItsType(GameTestHelper helper) {
    var turret = m1(helper);
    FakePlayer player = owner(helper, turret);
    ArtilleryIndicatorItem item = indicator();
    ItemStack stack = new ItemStack(item);
    helper.assertTrue(item.onInteractVehicle(turret, stack, player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS,
        "Exact SBW indicator did not bind the M1");
    helper.assertTrue(DnvPlayerArtilleryTarget.M1_INDICATOR_TYPE.equals(stack.getTag().getString("Type")),
        "Native indicator type differs from the M1 description ID");
    helper.assertTrue(stack.getTag().getList("Cannons", 10).size() == 1
        && stack.getTag().getList("Cannons", 10).getCompound(0).getString("UUID").equals(turret.getUUID().toString()),
        "Native indicator did not keep the M1 UUID");
    helper.assertTrue(item.onInteractVehicle(turret, stack, player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
        && stack.getTag().getList("Cannons", 10).isEmpty(), "Native removal did not clear the M1 binding");
    turret.discard();
    helper.succeed();
  }

  @GameTest(template = "ap_arena")
  public static void nativeIndicatorSetTargetCreatesTransientM1Aim(GameTestHelper helper) throws Exception {
    var turret = m1(helper);
    FakePlayer player = owner(helper, turret);
    ArtilleryIndicatorItem item = indicator();
    ItemStack stack = new ItemStack(item);
    helper.assertTrue(item.onInteractVehicle(turret, stack, player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS,
        "M1 bind prerequisite failed");
    BlockPos target = turret.blockPosition().offset(0, 0, 50);
    FiringParametersItemKt.setFiringParameters(stack, new FiringParametersItem.Parameters(target, 0, true));
    item.setTarget(stack, player);
    DnvM1IndicatorAim aim = aim(turret);
    helper.assertTrue(aim != null && aim.matches(player.getUUID(), target, 0, true),
        "Exact SBW setTarget did not create the M1 player order");
    helper.assertTrue(!aim.expired(helper.getLevel().getGameTime()), "Fresh order expired immediately");
    turret.discard();
    helper.succeed();
  }

  @GameTest(template = "ap_arena")
  public static void unloadedM1BindingSurvivesTargetUpdate(GameTestHelper helper) {
    var turret = m1(helper);
    FakePlayer player = owner(helper, turret);
    ArtilleryIndicatorItem item = indicator();
    ItemStack stack = new ItemStack(item);
    helper.assertTrue(item.onInteractVehicle(turret, stack, player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS,
        "M1 bind prerequisite failed");
    UUID id = turret.getUUID();
    turret.discard();
    BlockPos target = new BlockPos(20, 70, 20);
    FiringParametersItemKt.setFiringParameters(stack, new FiringParametersItem.Parameters(target, 0, true));
    item.setTarget(stack, player);
    helper.assertTrue(stack.getTag().getList("Cannons", 10).size() == 1
        && stack.getTag().getList("Cannons", 10).getCompound(0).getString("UUID").equals(id.toString()),
        "An unloaded M1 binding was deleted from the player's item");
    helper.succeed();
  }

  @GameTest(template = "ap_arena")
  public static void stationContentRegistersDuringTheCorrectForgeEvent(GameTestHelper helper) {
    helper.assertTrue(BuiltInRegistries.BLOCK.get(DnvRemoteWeaponStationContent.ID) == DnvRemoteWeaponStationContent.BLOCK,
        "Station block did not register");
    helper.assertTrue(BuiltInRegistries.ITEM.get(DnvRemoteWeaponStationContent.ID) == DnvRemoteWeaponStationContent.ITEM
        && DnvRemoteWeaponStationContent.ITEM.getBlock() == DnvRemoteWeaponStationContent.BLOCK,
        "Station item/block identity mismatch");
    helper.assertTrue(BuiltInRegistries.ITEM.get(DnvRemoteWeaponStationContent.CONNECTOR_ID) == DnvRemoteWeaponStationContent.CONNECTOR,
        "Connector not registered");
    helper.succeed();
  }

  private static DnvM1YunlongEntity m1(GameTestHelper helper) {
    DnvM1YunlongEntity turret = DnvM1Content.M1.get().create(helper.getLevel());
    helper.assertTrue(turret != null, "M1 factory returned null");
    turret.setPos(helper.absoluteVec(new Vec3(7, 16, 7)));
    turret.setLocked(false);
    helper.assertTrue(helper.getLevel().addFreshEntity(turret), "M1 not admitted to the isolated world");
    return turret;
  }

  private static FakePlayer owner(GameTestHelper helper, DnvM1YunlongEntity turret) {
    FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
    helper.assertTrue(turret.initializeOwner(player.getUUID()), "M1 owner initialization failed");
    return player;
  }

  private static ArtilleryIndicatorItem indicator() {
    return (ArtilleryIndicatorItem) com.atsuishio.superbwarfare.init.ModItems.ARTILLERY_INDICATOR.get();
  }

  private static DnvM1IndicatorAim aim(DnvM1YunlongEntity turret) throws Exception {
    Field field = DnvM1YunlongEntity.class.getDeclaredField("indicatorAim");
    field.setAccessible(true);
    return (DnvM1IndicatorAim) field.get(turret);
  }
}
