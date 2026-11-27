package com.liucodex.libertadcheck;

import com.atsuishio.superbwarfare.block.ContainerBlock;
import com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity;
import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineType;
import com.atsuishio.superbwarfare.item.container.ContainerBlockItem;
import com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.network.ServerPacketPayload;
import com.atsuishio.superbwarfare.network.message.send.EditMessage;
import com.atsuishio.superbwarfare.network.message.send.VehicleFireMessage;
import com.liucodex.libertad.Libertad;
import com.liucodex.libertad.ship.LibertadEntity;
import com.liucodex.libertad.naval.ShellRange;
import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/** Launched as a separate test mod against the reobfuscated trial archive. */
@Mod("libertadcheck")
@GameTestHolder("libertadcheck")
@PrefixGameTestTemplate(false)
public final class NativeShipGameTest {
    public NativeShipGameTest() throws Exception {
        GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(new File(
                System.getProperty("libertad.testReport", "libertad-tests.xml"))));
    }

    @GameTest(template = "water_test", timeoutTicks = 2600)
    public static void nativeDeploymentFloatsAtPaintLine(GameTestHelper test) {
        test.assertTrue(ModList.get().isLoaded("doggiesnewvehicle") == Boolean.getBoolean("libertad.withDnv"),
                "incorrect DNV installation for this scenario");
        ServerLevel level = test.getLevel();
        BlockPos anchor = test.absolutePos(BlockPos.ZERO).atY(240);
        for (int z = -78; z <= 78; z++) {
            for (int x = -12; x <= 12; x++) {
                for (int y = -7; y <= 0; y++) {
                    level.setBlock(anchor.offset(x, y, z), Blocks.WATER.defaultBlockState(), 2);
                }
            }
        }
        BlockPos boxPos = anchor.above();
        var player = FakePlayerFactory.getMinecraft(level);
        player.moveTo(boxPos.getX() + .5, boxPos.getY(), boxPos.getZ() + 3.5, 0, 0);
        ItemStack boxItem = Boolean.getBoolean("libertad.baseline")
                ? ContainerBlockItem.createInstance(Libertad.SHIP.get()) : creativeBox(test);
        player.setItemInHand(InteractionHand.MAIN_HAND, boxItem);
        var placeHit = new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false);
        var item = (ContainerBlockItem) boxItem.getItem();
        var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, placeHit)) {
            @Override public BlockPos getClickedPos() { return boxPos; }
            @Override public boolean replacingClickedOnBlock() { return true; }
        };
        test.assertTrue(item.place(context).consumesAction(), "native item must place the box");
        test.assertTrue(level.getBlockEntity(boxPos) instanceof ContainerBlockEntity, "native box entity missing");
        var box = (ContainerBlockEntity) level.getBlockEntity(boxPos);
        test.assertTrue(box.entityType == Libertad.SHIP.get(), "native box lost the type");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(
                BuiltInRegistries.ITEM.get(new ResourceLocation("superbwarfare", "crowbar"))));
        var openHit = new BlockHitResult(Vec3.atCenterOf(boxPos), Direction.UP, boxPos, false);
        var state = level.getBlockState(boxPos);
        var probe = Libertad.SHIP.get().create(level);
        test.assertTrue(probe.computed().getEngineType() == EngineType.SHIP && probe.computed().seats().size() == 1,
                "SBW did not load Libertad's engine and seat from the actual pack");
        test.assertTrue(ContainerBlock.canOpen(level, boxPos, box.entityType, box.entityTag),
                "open water was rejected by the ship's native preflight");
        test.assertTrue(state.use(level, player, InteractionHand.MAIN_HAND, openHit).consumesAction(),
                "native crowbar must start the animation");
        var opened = level.getBlockState(boxPos);
        BlockPos lateWall = anchor.offset(3, 1, 3);
        level.setBlockAndUpdate(lateWall, Blocks.STONE.defaultBlockState());
        test.assertTrue(!ContainerBlock.canOpen(level, boxPos, box.entityType, box.entityTag),
                "native preflight must see the new wall");
        for (int tick = 0; tick < 21; tick++) ContainerBlockEntity.Companion.serverTick(level, boxPos, opened, box);
        test.assertTrue(level.getBlockEntity(boxPos) == box
                && !level.getBlockState(boxPos).getValue(ContainerBlock.OPENED),
                "rejected launch did not reset the box: " + level.getBlockState(boxPos) + ", tick " + box.getTick());
        level.setBlockAndUpdate(lateWall, Blocks.AIR.defaultBlockState());
        test.assertTrue(level.getBlockState(boxPos).use(level, player, InteractionHand.MAIN_HAND, openHit).consumesAction(),
                "native box did not reopen after removing the obstruction");
        var accepted = level.getBlockState(boxPos);
        for (int tick = 0; tick < 21; tick++) ContainerBlockEntity.Companion.serverTick(level, boxPos, accepted, box);
        List<LibertadEntity> ships = level.getEntities(Libertad.SHIP.get(), new AABB(anchor).inflate(80), e -> true);
        test.assertTrue(ships.size() == 1 && level.getBlockEntity(boxPos) == null, "native launch must add exactly one ship");
        LibertadEntity ship = ships.get(0);
        test.assertTrue(ship.computed().getEngineType() == EngineType.SHIP
                && ship.getHealth() == 20000 && ship.getGunDataMap().size() == 40
                && ship.getPassengers().isEmpty(), "actual reload, ship engine, guns or seating incorrect");
        verifyInventory(test, ship, player);
        double surface = anchor.getY() + 8.0 / 9;
        test.assertTrue(Math.abs(ship.getY() + .22 - surface) < .02, "spawn must align paint and water lines");
        verifyTurn(test, ship, anchor);
        verifyAimAndPersistence(test, ship, anchor);
        test.runAfterDelay(120, () -> {
            double waterlineError = ship.getY() + .22 - surface;
            test.assertTrue(Math.abs(waterlineError) < .20, "native floating waterline error: " + waterlineError);
            test.assertTrue(Math.abs(ship.getDeltaMovement().y) < .04, "ship did not settle on the water");
            test.assertTrue(Math.abs(ship.angles(6, 1).yaw()) < .01, "restored secondary did not finish returning");
            System.out.println("Libertad GameTest waterline error: " + waterlineError);
            verifyGunnery(test, ship, player);
        });
    }

    private static ItemStack creativeBox(GameTestHelper test) {
        var level = test.getLevel();
        var tab = level.registryAccess().registryOrThrow(Registries.CREATIVE_MODE_TAB)
                .get(new ResourceLocation("libertad", "libertad"));
        test.assertTrue(tab != null, "Libertad creative tab missing");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
        var items = tab.getDisplayItems().stream().toList();
        test.assertTrue(items.size() == 1 && items.get(0).getItem() instanceof ContainerBlockItem,
                "Libertad tab must contain the native ship container");
        return items.get(0).copy();
    }

    private static void verifyGunnery(GameTestHelper test, LibertadEntity ship, ServerPlayer player) {
        var level = test.getLevel();
        test.assertTrue(player.startRiding(ship, true) && ship.getSeatIndex(player) == 0 && ship.getGunData(player) == ship.getGunData("main_01_l"),
                "native helm did not resolve the primary weapon slot");
        // Real active chunks around the broadside, independent of the fake player's lack of view tickets.
        var center = ship.chunkPosition();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) level.setChunkForced(center.x + x, center.z + z, true);
        }
        player.setYRot(ship.getYRot() + 90);
        player.setXRot(-80);
        test.runAfterDelay(2, () -> {
            nativeFire(ship, player);
            test.runAfterDelay(2, () -> {
                test.assertTrue(shellCount(level, ship, ShellRange.MAIN) == 0 && ship.fireSnapshot().getInt("blocked") == 63,
                        "out-of-elevation fire request escaped the per-mount gate");
                player.setXRot(-8);
                test.runAfterDelay(500, () -> verifyBroadside(test, ship, player));
            });
        });
    }

    private static void verifyBroadside(GameTestHelper test, LibertadEntity ship, ServerPlayer player) {
        var level = test.getLevel();
        int ready = ship.fireSnapshot().getInt("ready");
        test.assertTrue(ready != 0 && ready != 63, "expected some ready pairs and some blocked pairs: " + ship.fireSnapshot());
        var ap = BuiltInRegistries.ITEM.get(new ResourceLocation("superbwarfare", "large_shell_ap"));
        ship.getInventory().setStackInSlot(0, new ItemStack(ap, 12));
        nativeFire(ship, player);
        test.runAfterDelay(2, () -> {
            int count = shellCount(level, ship, ShellRange.MAIN);
            test.assertTrue(count == 2 * Integer.bitCount(ready), "native packet admitted " + count + " shells for mask " + ready);
            test.assertTrue(ship.getGunDataMap().entrySet().stream().anyMatch(e -> e.getKey().startsWith("main_") && e.getValue().reloading()),
                    "SBW did not begin reloading fired barrels");
            nativeFire(ship, player);
            test.runAfterDelay(2, () -> {
                test.assertTrue(shellCount(level, ship, ShellRange.MAIN) == count, "native reload allowed an immediate repeat");
                System.out.println("Libertad native broadside mask " + ready + ", " + count + " live AP shells");
                handle(new EditMessage(5, true, true), player);
                test.assertTrue(ship.getGunData(player).selectedAmmoType.get() == 0,
                        "native ammo switch bypassed reload");
                test.runAfterDelay(820, () -> verifyHighExplosive(test, ship, player));
            });
        });
    }

    private static void verifyHighExplosive(GameTestHelper test, LibertadEntity ship, ServerPlayer player) {
        var level = test.getLevel();
        int ready = ship.fireSnapshot().getInt("ready");
        test.assertTrue(ship.fireSnapshot().getInt("ammo") == 1 && ready != 0,
                "native EditMessage did not yield a ready HE battery: " + ship.fireSnapshot());
        nativeFire(ship, player);
        test.runAfterDelay(2, () -> {
            test.assertTrue(shellCount(level, ship, ShellRange.MAIN) == 2 * Integer.bitCount(ready),
                    "HE selection did not fire the ready pairs");
            for (int i = 0; i < ship.getInventory().getSlots(); i++) ship.getInventory().setStackInSlot(i, ItemStack.EMPTY);
            ship.getGunDataMap().forEach((key, gun) -> {
                if (key.startsWith("main_")) gun.ammo.set(0);
            });
            test.runAfterDelay(30, () -> {
                nativeFire(ship, player);
                test.runAfterDelay(2, () -> {
                    test.assertTrue(shellCount(level, ship, ShellRange.MAIN) == 0 && ship.fireSnapshot().getInt("ready") == 0,
                            "empty gun request admitted a projectile");
                    System.out.println("Libertad native HE switch, HE salvo and empty rejection passed");
                    verifySecondary(test, ship, player);
                });
            });
        });
    }

    private static void nativeFire(LibertadEntity ship, ServerPlayer player) {
        handle(new VehicleFireMessage(null, null, null, ship.getId()), player);
    }

    private static void handle(ServerPacketPayload packet, ServerPlayer player) {
        try {
            var connection = new Connection(PacketFlow.SERVERBOUND);
            connection.setListener(player.connection);
            var ctor = NetworkEvent.Context.class.getDeclaredConstructor(Connection.class, NetworkDirection.class, int.class);
            ctor.setAccessible(true);
            var context = ctor.newInstance(connection, NetworkDirection.PLAY_TO_SERVER, 0);
            packet.handler(() -> context);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static int shellCount(ServerLevel level, LibertadEntity ship, int range) {
        return level.getEntitiesOfClass(CannonShellEntity.class, ship.getBoundingBox().inflate(280),
                shell -> shell.getPersistentData().getCompound("LibertadShot").getInt("Range") == range).size();
    }

    private static void verifySecondary(GameTestHelper test, LibertadEntity ship, ServerPlayer player) {
        var level = test.getLevel();
        Entity target;
        if (Boolean.getBoolean("libertad.withDnv")) {
            var type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("doggievehicle", "noise"));
            target = type.create(level);
            test.assertTrue(target instanceof VehicleEntity && target.getClass().getName().contains("NoiseVehicleEntity"),
                    "DNV did not register the expected Noise boss");
        } else {
            var zombie = EntityType.ZOMBIE.create(level);
            zombie.setNoAi(true);
            target = zombie;
        }
        target.setNoGravity(true);
        Vec3 position = ship.position().add(50, 14, 0);
        if (target instanceof VehicleEntity) {
            // Noise uses native vehicle gravity even when vanilla setNoGravity is set.
            // Keep the platform below the guns so its front edge cannot mask an otherwise clear target.
            BlockPos ground = BlockPos.containing(ship.position().add(50, 4, 0)).below();
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) level.setBlock(ground.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
            }
            position = Vec3.atBottomCenterOf(ground.above());
        }
        target.setPos(position);
        level.addFreshEntity(target);
        if (Boolean.getBoolean("libertad.withDnv")) {
            test.runAfterDelay(2, () -> {
                var team = target.getTeam();
                test.assertTrue(team != null && team.getName().equals("dnv_boss"), "Noise must use its real enemy team");
                level.getScoreboard().addPlayerToTeam(player.getScoreboardName(), (net.minecraft.world.scores.PlayerTeam) team);
                int ammunition = secondaryAmmo(ship);
                ship.toggleSecondaries(player);
                test.runAfterDelay(108, () -> {
                    test.assertTrue(shellCount(level, ship, ShellRange.SECONDARY) == 0 && secondaryAmmo(ship) == ammunition,
                            "secondary fired on an allied Noise");
                    level.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
                    test.assertTrue(!player.isAlliedTo(target), "Noise still allied in enemy phase");
                    var boss = (VehicleEntity) target;
                    boss.setWreck(true);
                    test.assertTrue(secondaryDiagnostics(ship, player, target).startsWith("NEUTRAL"),
                            "a real DNV wreck remained a valid target");
                    boss.setWreck(false);
                    waitForSecondary(test, ship, player, target, test.getTick() + 310);
                });
            });
            return;
        }
        ship.toggleSecondaries(player);
        waitForSecondary(test, ship, player, target, test.getTick() + 280);
    }

    private static int secondaryAmmo(LibertadEntity ship) {
        return ship.getGunDataMap().entrySet().stream().filter(entry -> entry.getKey().startsWith("secondary_"))
                .mapToInt(entry -> entry.getValue().ammo.get()).sum();
    }

    private static void waitForSecondary(GameTestHelper test, LibertadEntity ship, ServerPlayer player, Entity target, long deadline) {
        if (shellCount(test.getLevel(), ship, ShellRange.SECONDARY) == 0) {
            test.assertTrue(test.getTick() < deadline, "secondary never admitted an HE shell against " + target.getType()
                    + " team=" + target.getTeam() + " pos=" + target.position()
                    + " diagnostics=" + (test.getTick() >= deadline ? secondaryDiagnostics(ship, player, target) : ""));
            test.runAfterDelay(1, () -> waitForSecondary(test, ship, player, target, deadline));
            return;
        }
        System.out.println("Libertad secondary admitted shells against " + target.getType());
        player.stopRiding();
        nativeFire(ship, player);
        target.discard();
        test.runAfterDelay(310, () -> {
            test.assertTrue(ship.fireSnapshot().getInt("secondary") == 0 && shellCount(test.getLevel(), ship, ShellRange.SECONDARY) == 0,
                    "secondaries did not cease fire and return after dismount");
            for (int i = 0; i < 24; i++) test.assertTrue(Math.abs(ship.angles(i, 1).yaw()) < .001
                    && Math.abs(ship.angles(i, 1).pitch()) < .001, "mount failed to reach home: " + i);
            ship.discard();
            test.succeed();
        });
    }

    private static String secondaryDiagnostics(LibertadEntity ship, ServerPlayer player, Entity target) {
        try {
            var field = LibertadEntity.class.getDeclaredField("fire");
            field.setAccessible(true);
            Object fire = field.get(ship);
            var relation = fire.getClass().getDeclaredMethod("relation", Entity.class, ServerPlayer.class);
            relation.setAccessible(true);
            var director = fire.getClass().getDeclaredField("secondary");
            director.setAccessible(true);
            var state = (com.liucodex.libertad.naval.SecondaryDirector) director.get(fire);
            var solve = fire.getClass().getDeclaredMethod("solve", int.class, Vec3.class, Vec3.class, boolean.class);
            solve.setAccessible(true);
            var visible = fire.getClass().getDeclaredMethod("visibleFrom", ServerLevel.class, int.class,
                    com.liucodex.libertad.naval.MountGeometry.Angles.class, Vec3.class);
            visible.setAccessible(true);
            var center = target.getBoundingBox().getCenter();
            var summary = new StringBuilder().append(relation.invoke(fire, target, player)).append(" target center ").append(center)
                    .append(" tracks ").append(state.assignments()).append(" ");
            for (int i = 6; i < 24; i++) {
                var goal = solve.invoke(fire, i, center, target.getDeltaMovement(), true);
                if (goal != null && (boolean) visible.invoke(fire, (ServerLevel) ship.level(), i, goal, center)) {
                    summary.append(i).append(" goal ").append(goal).append(" actual ").append(ship.angles(i, 1)).append(" ");
                }
            }
            return summary.toString();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void verifyInventory(GameTestHelper test, LibertadEntity ship, net.minecraft.server.level.ServerPlayer player) {
        var before = cargo(ship);
        for (int i = 1; i <= 2; i++) {
            var menu = ship.createMenu(i, player.getInventory(), player);
            test.assertTrue(menu != null, "native cargo menu missing");
            var visible = new TreeMap<String, Integer>();
            for (var slot : menu.slots) {
                if (!(slot instanceof net.minecraftforge.items.SlotItemHandler cargo) || cargo.getItemHandler() != ship.getInventory()) continue;
                var stack = slot.getItem();
                if (!stack.isEmpty()) visible.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
            test.assertTrue(visible.equals(before), "native cargo slots disagree with ship inventory: " + visible);
            menu.removed(player);
            test.assertTrue(cargo(ship).equals(before), "opening or closing cargo changed inventory");
        }
        var reload = Libertad.SHIP.get().create(test.getLevel());
        reload.load(ship.saveWithoutId(new CompoundTag()));
        test.assertTrue(cargo(reload).equals(before), "native NBT round trip changed inventory");
        System.out.println("Libertad cargo before/open/reopen/save-load: " + before);
        test.assertTrue(before.equals(Map.of("superbwarfare:large_shell_ap", 12, "superbwarfare:large_shell_he", 40)),
                "incorrect starter cargo before opening: " + before);
    }

    private static Map<String, Integer> cargo(LibertadEntity ship) {
        var counts = new TreeMap<String, Integer>();
        for (var stack : ship.getItems()) {
            if (!stack.isEmpty()) counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static void verifyTurn(GameTestHelper test, LibertadEntity ship, BlockPos anchor) {
        // At x=.5 the middle OBB reaches x=9.6. Yawing .35° at z=-24 cannot hit x=10,
        // so move the initial hull .35 blocks toward this shoreline for the negative case.
        Vec3 position = ship.position();
        ship.setPos(position.add(.35, 0, 0));
        ship.updateOBB();
        var middle = ship.getOBBs().stream().filter(obb -> obb.part == com.atsuishio.superbwarfare.tools.OBB.Part.COLLISION)
                .findFirst().orElseThrow();
        BlockPos wall = anchor.offset(10, 1, -24);
        var level = test.getLevel();
        double angle = -Math.toRadians(.35);
        test.assertTrue(canTurn(level, ship, middle, angle), "empty water blocked the center section");
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        test.assertTrue(!canTurn(level, ship, middle, angle), "turn missed the wall along the center section");
        level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        ship.setPos(position);
        ship.updateOBB();
    }

    private static boolean canTurn(ServerLevel level, LibertadEntity ship,
                                   com.atsuishio.superbwarfare.tools.OBB hull, double angle) {
        try {
            var type = Class.forName("com.liucodex.libertad.ship.ShipHull", true, ship.getClass().getClassLoader());
            var method = type.getDeclaredMethod("canTurn", net.minecraft.world.level.Level.class, LibertadEntity.class,
                    com.atsuishio.superbwarfare.tools.OBB.class, double.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, level, ship, hull, angle);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void verifyAimAndPersistence(GameTestHelper test, LibertadEntity ship, BlockPos anchor) {
        var level = test.getLevel();
        Vec3 eye = Vec3.atBottomCenterOf(anchor.above(22));
        var pig = EntityType.PIG.create(level);
        pig.setNoGravity(true);
        pig.setPos(eye.x, eye.y, eye.z + 7);
        level.addFreshEntity(pig);
        Vec3 aim = aim(level, ship, eye.add(0, .35, 0), new Vec3(0, 0, 1));
        test.assertTrue(aim != null && aim.z > eye.z + 6 && aim.z < eye.z + 7, "air target must supply the actual ray hit");
        BlockPos wall = BlockPos.containing(eye.add(0, 0, 3));
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        aim = aim(level, ship, eye.add(0, .35, 0), new Vec3(0, 0, 1));
        test.assertTrue(aim != null && aim.z < eye.z + 4, "wall must occlude the air target");
        level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        pig.discard();

        CompoundTag saved = ship.saveWithoutId(new CompoundTag());
        CompoundTag mounts = saved.getCompound("LibertadTurrets");
        mounts.getCompound("main_01").putDouble("Yaw", -17);
        mounts.getCompound("main_01").putDouble("Pitch", 6);
        mounts.getCompound("secondary_234_01").putDouble("Yaw", -70);
        var reload = Libertad.SHIP.get().create(level);
        reload.load(saved);
        test.assertTrue(reload.angles(0, 1).yaw() == -17 && reload.angles(0, 1).pitch() == 6
                && reload.angles(6, 1).yaw() == -70, "round trip lost mechanical angles");
        test.assertTrue(reload.fireSnapshot().getInt("secondary") == 2, "restored guns must return without resuming combat");
        ship.load(saved);
    }

    private static Vec3 aim(ServerLevel level, Entity ship, Vec3 eye, Vec3 direction) {
        try {
            Class<?> fireControl = Class.forName("com.liucodex.libertad.ship.ShipFireControl", true,
                    ship.getClass().getClassLoader());
            Method method = fireControl.getDeclaredMethod("aimPoint", ServerLevel.class, Entity.class, Vec3.class, Vec3.class);
            method.setAccessible(true);
            return (Vec3) method.invoke(null, level, ship, eye, direction);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
