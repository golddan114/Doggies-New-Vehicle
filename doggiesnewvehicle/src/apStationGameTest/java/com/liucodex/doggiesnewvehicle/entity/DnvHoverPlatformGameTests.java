package com.liucodex.doggiesnewvehicle.entity;

import com.liucodex.doggiesnewvehicle.compat.DnvBossTeamMembership;
import com.liucodex.doggiesnewvehicle.compat.DnvHoverRoutePlatformRuntime;
import com.liucodex.doggiesnewvehicle.compat.DnvHoverRoutePlatformServices;
import com.liucodex.doggiesnewvehicle.compat.DnvHoverPlatformWeapons;
import com.liucodex.doggiesnewvehicle.boss.DnvBossContent;
import com.liucodex.doggiesnewvehicle.platform.DnvPlatformContent;
import com.liucodex.doggiesnewvehicle.mission.DnvRoutePlan;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real isolated ServerLevel checks for the new DNV-owned airborne entities. */
@GameTestHolder("dnvapgametest")
@PrefixGameTestTemplate(false)
public final class DnvHoverPlatformGameTests {
    private DnvHoverPlatformGameTests() {
    }

    @GameTest(template = "ap_arena", timeoutTicks = 110)
    public static void cubeBossSpawnsWithVanillaTeamAndPersistentHoverAnchor(
            GameTestHelper helper) {
        helper.assertTrue(!ModList.get().isLoaded("doggiecasterhud"),
                "Independent DNV test unexpectedly loaded DCH");
        DnvHoverCubeBossEntity cube = DnvBossContent.HOVER_CUBE_BOSS.get()
                .create(helper.getLevel());
        helper.assertTrue(cube != null, "CUBE registry factory returned null");
        cube.setPos(helper.absoluteVec(new Vec3(7, 20, 7)));
        double initialX = cube.getX();
        double initialZ = cube.getZ();
        List<ChunkPos> testTickets = forceNearbyChunks(
                helper, cube.getX(), cube.getZ());
        helper.assertTrue(helper.getLevel().addFreshEntity(cube),
                "CUBE was not admitted to the isolated world");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(cube.isNoGravity(),
                    "CUBE lost the server-owned hover constraint");
            helper.assertTrue(cube.getTeam() != null
                            && DnvBossTeamMembership.DEFAULT_TEAM.equals(
                                    cube.getTeam().getName()),
                    "New CUBE was not assigned its vanilla boss team");
            CompoundTag tag = new CompoundTag();
            cube.addAdditionalSaveData(tag);
            helper.assertTrue(tag.contains("DnvHoverAnchorX", 6)
                            && tag.contains("DnvHoverAnchorZ", 6)
                            && tag.getDouble("DnvHoverAnchorX") == initialX
                            && tag.getDouble("DnvHoverAnchorZ") == initialZ,
                    "CUBE hover anchor was not persisted from its spawn point");
        });
        final Vec3[] previous = new Vec3[1];
        helper.runAfterDelay(35, () -> {
            previous[0] = cube.position();
            helper.assertTrue(previous[0].distanceToSqr(
                            new Vec3(initialX, previous[0].y, initialZ)) > 0.25D,
                    "CUBE did not start moving inside the loaded orbit area");
        });
        helper.runAfterDelay(75, () -> {
            try {
                helper.assertTrue(previous[0] != null
                                && cube.position().distanceToSqr(previous[0]) > 0.25D,
                        "CUBE moved once but did not continue its loaded-area flight");
                helper.succeed();
            } finally {
                cube.discard();
                testTickets.forEach(chunk -> helper.getLevel().setChunkForced(
                        chunk.x, chunk.z, false));
            }
        });
    }

    @GameTest(template = "ap_arena")
    public static void hoverRouteStopsWhenOwnerIsNotOnline(GameTestHelper helper) {
        DnvHoverRoutePlatformEntity platform = DnvPlatformContent.HOVER_ROUTE_PLATFORM
                .get().create(helper.getLevel());
        helper.assertTrue(platform != null,
                "Owned hover platform registry factory returned null");
        platform.setPos(helper.absoluteVec(new Vec3(7, 20, 7)));
        helper.assertTrue(helper.getLevel().addFreshEntity(platform),
                "Owned hover platform was not admitted to the isolated world");
        UUID absentOwner = UUID.randomUUID();
        platform.bindOwner(absentOwner);
        helper.assertTrue(helper.getLevel().getServer().getPlayerList()
                        .getPlayer(absentOwner) == null,
                "The negative-state owner unexpectedly logged in");
        double startX = platform.getX();
        double startY = platform.getY();
        double startZ = platform.getZ();
        helper.assertTrue(DnvHoverRoutePlatformRuntime.start(
                        platform, routePlan(helper, platform))
                        .accepted()
                        && DnvHoverRoutePlatformRuntime.active(platform),
                "Prepared route did not enter the server runtime");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!DnvHoverRoutePlatformRuntime.active(platform),
                    "Offline owner's route stayed active");
            helper.assertTrue(platform.getX() == startX
                            && platform.getY() == startY
                            && platform.getZ() == startZ,
                    "Platform moved without its online owner");
            platform.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "ap_arena")
    public static void distantOwnerCanStopLoadedHoverRouteButStrangerCannot(
            GameTestHelper helper) {
        DnvHoverRoutePlatformEntity platform = DnvPlatformContent.HOVER_ROUTE_PLATFORM
                .get().create(helper.getLevel());
        helper.assertTrue(platform != null,
                "Owned hover platform registry factory returned null");
        platform.setPos(helper.absoluteVec(new Vec3(7, 20, 7)));
        helper.assertTrue(helper.getLevel().addFreshEntity(platform),
                "Owned hover platform was not admitted to the isolated world");
        var owner = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "hover_owner"));
        var stranger = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "hover_stranger"));
        platform.bindOwner(owner.getUUID());
        owner.setPos(platform.getX() + 100.0D,
                platform.getY(), platform.getZ());
        stranger.setPos(platform.position());
        helper.assertTrue(owner.distanceToSqr(platform) > 64.0D * 64.0D,
                "Negative-state owner remained inside the old stop radius");
        helper.assertTrue(DnvHoverRoutePlatformRuntime.start(
                        platform, routePlan(helper, platform)).accepted()
                        && DnvHoverRoutePlatformRuntime.active(platform),
                "Prepared route did not enter the server runtime");
        helper.assertTrue(!DnvHoverRoutePlatformServices.stop(stranger, platform)
                        && DnvHoverRoutePlatformRuntime.active(platform),
                "A stranger stopped the owner's loaded route");
        helper.assertTrue(DnvHoverRoutePlatformServices.stop(owner, platform)
                        && !DnvHoverRoutePlatformRuntime.active(platform),
                "Distant owner could not stop the loaded route");
        platform.discard();
        helper.succeed();
    }

    @GameTest(template = "ap_arena", timeoutTicks = 140)
    public static void onlineOwnerRouteClimbsThenMovesTowardWaypoint(
            GameTestHelper helper) {
        DnvHoverRoutePlatformEntity platform = DnvPlatformContent.HOVER_ROUTE_PLATFORM
                .get().create(helper.getLevel());
        helper.assertTrue(platform != null,
                "Owned hover platform registry factory returned null");
        Vec3 spawn = helper.absoluteVec(new Vec3(7, 20, 7));
        int terrain = helper.getLevel().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(spawn.x), (int) Math.floor(spawn.z));
        platform.setPos(spawn.x, Math.max(spawn.y, terrain + 12.0D),
                spawn.z);
        double startX = platform.getX();
        double startY = platform.getY();
        double startZ = platform.getZ();
        List<ChunkPos> testTickets = forceNearbyChunks(
                helper, startX, startZ);
        helper.assertTrue(helper.getLevel().addFreshEntity(platform),
                "Owned hover platform was not admitted to the isolated world");
        ServerPlayer owner = onlineFixturePlayer(helper);
        owner.setPos(startX + 6.0D, startY, startZ + 6.0D);
        platform.bindOwner(owner.getUUID());
        helper.assertTrue(helper.getLevel().getServer().getPlayerList()
                        .getPlayer(owner.getUUID()) == owner,
                "The test owner is not online in the server player list");
        helper.assertTrue(DnvHoverRoutePlatformRuntime.start(
                        platform, routePlan(helper, platform)).accepted(),
                "Prepared route did not enter the server runtime");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(platform.getY() > startY + 0.2D,
                    "Online owner's route did not begin climbing");
            helper.assertTrue(Math.hypot(platform.getX() - startX,
                            platform.getZ() - startZ) < 0.3D,
                    "Platform moved horizontally before reaching route height");
        });
        helper.runAfterDelay(110, () -> {
            try {
                helper.assertTrue(platform.getY() > startY + 2.0D,
                        "Platform did not climb toward the route height");
                helper.assertTrue(platform.getX() > startX + 0.3D,
                        "Platform did not move toward its eastward waypoint");
                helper.succeed();
            } finally {
                DnvHoverRoutePlatformRuntime.stop(platform);
                platform.discard();
                testTickets.forEach(chunk -> helper.getLevel().setChunkForced(
                        chunk.x, chunk.z, false));
                helper.getLevel().getServer().getPlayerList().remove(owner);
            }
        });
    }

    @GameTest(template = "ap_arena", timeoutTicks = 140)
    public static void ownerDescentReplacesRouteAndStopsOverSolidGround(
            GameTestHelper helper) {
        DnvHoverRoutePlatformEntity platform = DnvPlatformContent.HOVER_ROUTE_PLATFORM
                .get().create(helper.getLevel());
        helper.assertTrue(platform != null,
                "Owned hover platform registry factory returned null");
        Vec3 spawn = helper.absoluteVec(new Vec3(7, 20, 7));
        int terrain = helper.getLevel().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(spawn.x), (int) Math.floor(spawn.z));
        platform.setPos(spawn.x, Math.max(spawn.y, terrain + 12.0D),
                spawn.z);
        double startX = platform.getX();
        double startY = platform.getY();
        double startZ = platform.getZ();
        List<ChunkPos> testTickets = forceNearbyChunks(
                helper, startX, startZ);
        int padY = (int) Math.floor(startY) - 10;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                helper.getLevel().setBlock(new BlockPos(
                        (int) Math.floor(startX) + dx, padY,
                        (int) Math.floor(startZ) + dz),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
        helper.assertTrue(helper.getLevel().addFreshEntity(platform),
                "Owned hover platform was not admitted to the isolated world");
        ServerPlayer owner = onlineFixturePlayer(helper);
        owner.setPos(startX + 6.0D, startY, startZ + 6.0D);
        platform.bindOwner(owner.getUUID());
        var stranger = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "land_stranger"));
        helper.assertTrue(DnvHoverRoutePlatformRuntime.start(
                        platform, routePlan(helper, platform)).accepted(),
                "Prepared route did not enter the server runtime");
        helper.assertTrue(!DnvHoverRoutePlatformServices.land(stranger, platform)
                        && DnvHoverRoutePlatformRuntime.active(platform),
                "Stranger replaced the owner's route with descent");
        BlockPos blockedLanding = new BlockPos((int) Math.floor(startX),
                (int) Math.floor(startY) + 3, (int) Math.floor(startZ));
        helper.getLevel().setBlock(blockedLanding,
                Blocks.BEDROCK.defaultBlockState(), 3);
        helper.assertTrue(!DnvHoverRoutePlatformServices.land(owner, platform)
                        && DnvHoverRoutePlatformRuntime.active(platform),
                "Unsafe landing request interrupted the existing route");
        helper.getLevel().setBlock(blockedLanding,
                Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(DnvHoverRoutePlatformServices.land(owner, platform)
                        && DnvHoverRoutePlatformRuntime.active(platform),
                "Owner could not request loaded-ground descent");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(platform.getY() < startY - 1.0D,
                    "Platform did not begin bounded descent");
            helper.assertTrue(platform.getX() == startX
                            && platform.getZ() == startZ,
                    "Descent changed the horizontal route position");
        });
        helper.runAfterDelay(110, () -> {
            try {
                helper.assertTrue(!DnvHoverRoutePlatformRuntime.active(platform),
                        "Descent did not stop after reaching the ground");
                helper.assertTrue(platform.getY() < startY - 4.0D
                                && helper.getLevel().noCollision(platform,
                                        platform.getBoundingBox()),
                        "Platform did not settle in clear space");
                helper.succeed();
            } finally {
                DnvHoverRoutePlatformRuntime.stop(platform);
                platform.discard();
                testTickets.forEach(chunk -> helper.getLevel().setChunkForced(
                        chunk.x, chunk.z, false));
                helper.getLevel().getServer().getPlayerList().remove(owner);
            }
        });
    }

    @GameTest(template = "ap_arena", timeoutTicks = 90)
    public static void fixedLightGunsKeepAiAndPlayerFireIndependent(
            GameTestHelper helper) {
        DnvHoverRoutePlatformEntity platform = DnvPlatformContent.HOVER_ROUTE_PLATFORM
                .get().create(helper.getLevel());
        helper.assertTrue(platform != null, "Platform factory returned null");
        Vec3 center = helper.absoluteVec(new Vec3(7, 20, 7));
        platform.setPos(center);
        List<ChunkPos> tickets = forceNearbyChunks(helper, center.x, center.z);
        helper.assertTrue(helper.getLevel().addFreshEntity(platform),
                "Platform was not admitted to the isolated world");
        ServerPlayer owner = onlineFixturePlayer(helper);
        owner.setPos(center.x, center.y, center.z - 5.0D);
        platform.bindOwner(owner.getUUID());
        var manualTarget = EntityType.HUSK.create(helper.getLevel());
        helper.assertTrue(manualTarget != null, "Manual target factory returned null");
        manualTarget.setNoAi(true);
        manualTarget.setPos(center.x + 6.0D, center.y, center.z);
        helper.assertTrue(helper.getLevel().addFreshEntity(manualTarget),
                "Manual target was not admitted to the isolated world");
        helper.assertTrue(DnvHoverPlatformWeapons.claim(owner, platform)
                        == DnvHoverPlatformWeapons.SessionIssue.NONE,
                "Owner could not claim the manual-only gun");
        float before = manualTarget.getHealth();
        helper.assertTrue(DnvHoverPlatformWeapons.fire(owner, platform,
                        manualTarget.getEyePosition()) == DnvHoverPlatformWeapons.ShotIssue.FIRED
                        && manualTarget.getHealth() < before,
                "Manual light gun did not execute real server damage");
        helper.assertTrue(DnvHoverPlatformWeapons.release(owner)
                        == DnvHoverPlatformWeapons.SessionIssue.NONE
                        && DnvHoverPlatformWeapons.fire(owner, platform,
                                manualTarget.getEyePosition())
                                == DnvHoverPlatformWeapons.ShotIssue.NO_SESSION,
                "Released player gun accepted a stale fire command");
        manualTarget.discard();

        var aiTarget = EntityType.HUSK.create(helper.getLevel());
        helper.assertTrue(aiTarget != null, "AI target factory returned null");
        aiTarget.setNoAi(true);
        aiTarget.setPos(center.x - 6.0D, center.y, center.z);
        helper.assertTrue(helper.getLevel().addFreshEntity(aiTarget),
                "AI target was not admitted to the isolated world");
        float aiBefore = aiTarget.getHealth();
        helper.runAfterDelay(40, () -> {
            try {
                helper.assertTrue(aiTarget.getHealth() < aiBefore,
                        "AI light gun stopped when the player gun was released");
                helper.succeed();
            } finally {
                aiTarget.discard();
                platform.discard();
                tickets.forEach(chunk -> helper.getLevel().setChunkForced(
                        chunk.x, chunk.z, false));
                helper.getLevel().getServer().getPlayerList().remove(owner);
            }
        });
    }

    private static ServerPlayer onlineFixturePlayer(GameTestHelper helper) {
        ServerPlayer owner = new ServerPlayer(helper.getLevel().getServer(),
                helper.getLevel(), new GameProfile(UUID.randomUUID(),
                        "hover_fixture"));
        Connection transport = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void send(Packet<?> packet) {
            }

            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {
            }
        };
        new EmbeddedChannel(transport);
        helper.getLevel().getServer().getPlayerList()
                .placeNewPlayer(transport, owner);
        return owner;
    }

    private static DnvRoutePlan routePlan(
            GameTestHelper helper, DnvHoverRoutePlatformEntity platform) {
        double height = Math.min(helper.getLevel().getMaxBuildHeight() - 4.0D,
                Math.max(8.0D, Math.max(
                        helper.getLevel().getMinBuildHeight() + 12.0D,
                        platform.getY() + 4.0D)));
        return new DnvRoutePlan(
                DnvRoutePlan.CURRENT_SCHEMA,
                helper.getLevel().dimension().location().toString(),
                1L,
                DnvRoutePlan.MovementMedium.AIR,
                List.of(new DnvRoutePlan.RoutePoint(platform.getX() + 10.0D,
                        platform.getZ(), OptionalDouble.of(height),
                        DnvRoutePlan.WaypointAction.HOLD)),
                DnvRoutePlan.CompletionPolicy.HOLD,
                DnvRoutePlan.BlockedPolicy.RECOVERY_REQUIRED,
                new DnvRoutePlan.AltitudePolicy(
                        DnvRoutePlan.AltitudeReference.ABSOLUTE,
                        height, 8.0D,
                        helper.getLevel().getMaxBuildHeight() - 4.0D));
    }

    private static List<ChunkPos> forceNearbyChunks(
            GameTestHelper helper, double centerX, double centerZ) {
        int chunkX = (int) Math.floor(centerX) >> 4;
        int chunkZ = (int) Math.floor(centerZ) >> 4;
        List<ChunkPos> tickets = new ArrayList<>();
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
                if (helper.getLevel().setChunkForced(x, z, true)) {
                    tickets.add(new ChunkPos(x, z));
                }
            }
        }
        return tickets;
    }
}
