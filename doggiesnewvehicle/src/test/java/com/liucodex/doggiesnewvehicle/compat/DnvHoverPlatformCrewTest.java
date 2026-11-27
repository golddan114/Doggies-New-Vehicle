package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.platform.DnvHoverPlatformStations;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

/** Executes production crew/exit/entity lifecycle bytecode with a bounded world and connection.
 * Minecraft terrain, networking and event dispatch are doubles, not a Forge/real-client claim. */
class DnvHoverPlatformCrewTest {
    private static final String TICKET = "DnvHoverCrewRecovery";

    @Test void validAnchorRescuesBeforeRemovalAndReleasesFallMotionAndBothOwners() throws Exception {
        var f = new Fixture(); f.world.ground(3, 0); f.riding(new Vec3(3.5, 64, .5));
        f.player.fallDistance = 100; f.player.velocity = new Vec3(0, -4, 0);
        assertEquals("RELEASED", f.release());
        f.assertReleased(new Vec3(3.5, 64, .5));
        assertNull(f.platform.anchor); assertEquals(0, f.player.fallDistance);
        assertEquals(Vec3.ZERO, f.player.velocity); assertFalse(f.player.gunClaimed);
        assertEquals("RELEASED", f.release()); // duplicate release is harmless
    }

    @Test void destroyedAnchorUsesNearbyGroundBeforeRemoteFallback() throws Exception {
        var f = new Fixture(); f.riding(new Vec3(3.5, 64, .5));
        f.world.ground(6, 0);
        assertEquals("RELEASED", f.release());
        f.assertReleased(new Vec3(6.5, 64, .5));
        assertEquals(1, f.world.chunkRequests); assertEquals(0, f.spawn.chunkRequests);
    }

    @Test void missingInvalidAndOutsideBorderAnchorsUseLoadedGroundBelowPlatform() throws Exception {
        for (Vec3 anchor : Arrays.asList(null, new Vec3(Double.NaN, 64, 0),
                new Vec3(Double.POSITIVE_INFINITY, 64, 0), new Vec3(5000, 64, 0))) {
            var f = new Fixture(); f.riding(anchor); f.world.ground(100, 100);
            assertEquals("RELEASED", f.release());
            f.assertReleased(new Vec3(100.5, 64, 100.5));
            assertEquals(0, f.world.chunkRequests); assertEquals(0, f.spawn.chunkRequests);
        }
    }

    @Test void spawnFallbackChangesDimensionOnlyAfterValidatedGroundAndActualTeleport() throws Exception {
        var f = new Fixture(); f.riding(null); f.spawn.ground(0, 0);
        assertEquals("RELEASED", f.release());
        f.assertReleased(new Vec3(.5, 64, .5)); assertSame(f.spawn, f.player.world);
        assertEquals(1, f.spawn.chunkRequests);
    }

    @Test void noGroundTransfersProtectionToPersistentTicketThenRecovers() throws Exception {
        var f = new Fixture(); f.riding(null);
        assertEquals("RECOVERY_PENDING", f.release());
        assertFalse(f.player.isPassenger()); assertTrue(f.pending());
        assertFalse(f.player.gunClaimed); assertEquals(1, f.player.messages);
        Vec3 held = f.player.pos;
        f.player.pos = held.add(0, -20, 0); f.player.velocity = new Vec3(0, -4, 0);
        f.player.tickCount = 1; f.tick();
        assertEquals(held, f.player.pos); assertEquals(Vec3.ZERO, f.player.velocity);
        assertEquals(1, f.spawn.chunkRequests); // holding does not scan/load again every tick
        var hurt = new Attack(f.player, false); f.call("attack", hurt); assertTrue(hurt.canceled);
        var kill = new Attack(f.player, true); f.call("attack", kill); assertFalse(kill.canceled);
        f.spawn.ground(0, 0); f.player.tickCount = 20; f.tick();
        f.assertReleased(new Vec3(.5, 64, .5));
        var after = new Attack(f.player, false); f.call("attack", after); assertFalse(after.canceled);
    }

    @Test void refusedTeleportKeepsTicketAndProtectionInsteadOfReportingSuccess() throws Exception {
        var f = new Fixture(); f.riding(null); f.spawn.ground(0, 0);
        f.player.rejectTeleport = true;
        assertEquals("RECOVERY_PENDING", f.release()); assertTrue(f.pending());
        f.player.rejectTeleport = false; f.player.tickCount = 20; f.tick();
        f.assertReleased(new Vec3(.5, 64, .5));
    }

    @Test void restartRestoresPendingRecoveryWithoutAnyOldPlatformOrStaticSession() throws Exception {
        var before = new Fixture(); before.riding(null); before.release();
        CompoundTag saved = before.player.data.copy();
        var after = new Fixture(); after.player.data = saved.copy(); after.spawn.ground(0, 0);
        after.call("loggedIn", new PlayerEvent(after.player));
        after.assertReleased(new Vec3(.5, 64, .5));
        assertTrue(saved.getCompound(TICKET).getBoolean("Pending"));
    }

    @Test void airborneRideSaveAlsoRecoversAfterRestartWithoutLogoutEvent() throws Exception {
        var before = new Fixture(); before.riding(new Vec3(3.5, 64, .5));
        assertFalse(before.pending()); // the ordinary ride ticket is not invulnerability
        CompoundTag saved = before.player.data.copy();
        var after = new Fixture(); after.player.data = saved; after.world.ground(3, 0);
        after.call("loggedIn", new PlayerEvent(after.player));
        after.assertReleased(new Vec3(3.5, 64, .5));
    }

    @Test void logoutAndStoppingRescueBeforeSavedStateAndRepeatedLifecycleIsIdempotent() throws Exception {
        for (String entry : List.of("loggedOut", "stopping")) {
            var f = new Fixture(); f.riding(null); f.spawn.ground(0, 0);
            f.call(entry, entry.equals("stopping") ? new ServerEvent(f.server) : new PlayerEvent(f.player));
            f.assertReleased(new Vec3(.5, 64, .5));
            assertFalse(f.player.data.copy().contains(TICKET));
            f.call("loggedOut", new PlayerEvent(f.player)); f.call("stopped", new ServerEvent(f.server));
            assertTrue(f.forced().isEmpty());
        }
    }

    @Test void directUnloadCallbackRecoversEvenWhenEntityWasAlreadyMarkedRemoved() throws Exception {
        var f = new Fixture(); f.riding(null); f.platform.removed = true; f.spawn.ground(0, 0);
        f.call("left", new Leave(f.platform));
        f.assertReleased(new Vec3(.5, 64, .5)); assertFalse(f.platform.gunsActive);
        f.call("left", new Leave(f.platform)); assertTrue(f.forced().isEmpty());
    }

    @Test void allEntityRemovalReasonsTransferSafetyBeforeSuperRemoval() throws Exception {
        for (Removal reason : Removal.values()) {
            var f = new Fixture(); f.riding(null);
            f.platform.remove(reason);
            assertTrue(f.pending(), reason.name()); assertFalse(f.player.isPassenger());
            assertTrue(f.platform.removed); assertFalse(f.platform.routeActive);
            assertFalse(f.platform.gunsActive); assertEquals(1, f.platform.removalCalls);
        }
    }

    @Test void ordinaryHighExitAndOtherVetoRetainTicketButEmergencyReleaseOverridesVeto() throws Exception {
        var f = new Fixture(); f.riding(new Vec3(3.5, 64, .5)); f.world.ground(3, 0);
        assertEquals(false, f.call("exit", f.player)); assertTrue(f.player.isPassenger());
        assertTrue(f.player.data.contains(TICKET));
        f.platform.pos = new Vec3(0, 64, 0); f.player.vetoExit = true;
        assertEquals(false, f.call("exit", f.player)); assertTrue(f.player.isPassenger());
        assertNotNull(f.platform.anchor);
        assertEquals("RELEASED", f.release()); f.assertReleased(new Vec3(3.5, 64, .5));
        assertTrue(f.forced().isEmpty());
    }

    @Test void normalExitClearsAnchorTicketAndLeaseOnlyAfterSuccessfulDetach() throws Exception {
        var f = new Fixture(); f.riding(new Vec3(3.5, 64, .5));
        f.platform.pos = new Vec3(0, 64, 0); f.world.ground(3, 0);
        assertEquals(true, f.call("exit", f.player));
        assertFalse(f.player.isPassenger()); assertFalse(f.player.data.contains(TICKET));
        assertNull(f.platform.anchor); assertFalse(f.player.gunClaimed);
    }

    @Test void deathReleasesRiderWithoutTeleportOrPersistentProtectionAndUnrelatedDeathIsUntouched() throws Exception {
        var f = new Fixture(); f.riding(null); f.player.alive = false;
        f.call("death", new PlayerEvent(f.player));
        assertFalse(f.player.isPassenger()); assertFalse(f.player.data.contains(TICKET));
        assertEquals(0, f.player.teleports); assertNull(f.platform.anchor);
        var other = new Rider(); other.velocity = new Vec3(1, 2, 3); other.gunClaimed = true;
        f.call("death", new PlayerEvent(other));
        assertEquals(new Vec3(1, 2, 3), other.velocity); assertTrue(other.gunClaimed);
    }

    @Test void adminTeleportToSafeGroundFinishesRecoveryImmediatelyAndPendingCannotMount() throws Exception {
        var f = new Fixture(); f.riding(null); f.release();
        var mount = new Mount(f.player, f.platform, false); f.call("mount", mount);
        assertTrue(mount.canceled);
        f.world.ground(44, 55); f.player.pos = new Vec3(44.5, 64, 55.5); f.player.tickCount = 1;
        f.tick(); f.assertReleased(new Vec3(44.5, 64, 55.5));
    }

    @Test void ticketForAnActiveRideDoesNotHoldOrGrantProtection() throws Exception {
        var f = new Fixture(); f.riding(null); f.player.pos = new Vec3(110, 140, 110);
        f.tick(); assertEquals(0, f.player.teleports); assertTrue(f.player.isPassenger());
        var damage = new Attack(f.player, false); f.call("attack", damage); assertFalse(damage.canceled);
    }

    @Test void malformedRecoveryCoordinatesAndMovedBorderNeverBecomeBlindDestinations() throws Exception {
        var f = new Fixture(); f.riding(new Vec3(3.5, 64, .5)); f.release();
        var ticket = f.player.data.getCompound(TICKET);
        ticket.getCompound("Anchor").remove("X");
        ticket.getCompound("Hold").putDouble("Y", Double.NaN);
        f.world.border.centerX = 500; f.world.border.centerZ = 500; f.world.border.radius = 10;
        f.player.tickCount = 1; f.tick();
        assertEquals(500, f.player.getX()); assertEquals(500, f.player.getZ());
        assertTrue(Double.isFinite(f.player.getY())); assertTrue(f.pending());
    }

    @Test void groundProbeRejectsFluidHazardsUnloadedBoundsCollisionAndWorldBorder() throws Exception {
        for (String issue : List.of("fluid", "fire", "magma", "cactus", "berry", "campfire", "rose",
                "unloaded", "edgeUnloaded", "collision", "border", "void")) {
            var f = new Fixture(); f.world.ground(3, 0);
            switch (issue) {
                case "fluid" -> f.world.hazard = new State(null, true);
                case "fire" -> f.world.hazard = new State(Blocks.FIRE, false);
                case "magma" -> f.world.hazard = new State(Blocks.MAGMA_BLOCK, false);
                case "cactus" -> f.world.hazard = new State(Blocks.CACTUS, false);
                case "berry" -> f.world.hazard = new State(Blocks.SWEET_BERRY_BUSH, false);
                case "campfire" -> f.world.hazard = new State(Blocks.CAMPFIRE, false);
                case "rose" -> f.world.hazard = new State(Blocks.WITHER_ROSE, false);
                case "unloaded" -> f.world.loaded = false;
                case "edgeUnloaded" -> f.world.boundsLoaded = false;
                case "collision" -> f.world.collision = true;
                case "border" -> f.world.border.radius = 1;
                case "void" -> f.world.tiles.clear();
                default -> fail(issue);
            }
            assertTrue(((Optional<?>) f.call("groundPoint", f.world, f.player, 3.5D, .5D, 64D, 4D)).isEmpty(), issue);
        }
    }

    @Test void boardTicketIsCreatedOnlyAfterAuthorizedGroundedRideSucceeds() throws Exception {
        var f = new Fixture(); f.platform.pos = f.player.pos = new Vec3(0, 64, 0); f.world.ground(3, 0);
        f.platform.ownerId = UUID.randomUUID(); assertEquals("NOT_AUTHORIZED", f.call("board", f.player, f.platform).toString());
        assertFalse(f.player.data.contains(TICKET)); f.platform.ownerId = f.player.id;
        f.platform.routeActive = true; assertEquals("NOT_LANDED", f.call("board", f.player, f.platform).toString());
        f.platform.routeActive = false; f.player.rejectMount = true;
        assertEquals("FAILED", f.call("board", f.player, f.platform).toString()); assertFalse(f.player.data.contains(TICKET));
        f.player.rejectMount = false; assertEquals("NONE", f.call("board", f.player, f.platform).toString());
        assertTrue(f.player.data.getCompound(TICKET).hasUUID("Platform")); assertFalse(f.pending());
    }

    @Test void actualPlatformNbtRoundTripRejectsMissingAndNonFiniteAnchorFieldsAndDropsLeases() throws Exception {
        var f = new Fixture(); f.platform.boardAnchor = new Vec3(3.5, 64, .5);
        f.platform.stations.claimPlayer(f.player.id, f.player.id, 0, f.platform.stations.revision());
        var saved = new CompoundTag(); f.platform.addAdditionalSaveData(saved);
        f.platform.boardAnchor = null; f.platform.readAdditionalSaveData(saved);
        assertEquals(new Vec3(3.5, 64, .5), f.platform.boardAnchor);
        assertFalse(f.platform.stations.playerMayFire(f.player.id, f.player.id, 0));
        for (String bad : List.of("missing", "nan", "infinite")) {
            CompoundTag malformed = saved.copy(); var anchor = malformed.getCompound("DnvBoardAnchor");
            switch (bad) {
                case "missing" -> anchor.remove("X");
                case "nan" -> anchor.putDouble("Y", Double.NaN);
                case "infinite" -> anchor.putDouble("Z", Double.POSITIVE_INFINITY);
                default -> fail(bad);
            }
            f.platform.readAdditionalSaveData(malformed); assertNull(f.platform.boardAnchor, bad);
        }
    }

    @Test void scopedEmergencyOverrideIsClearedEvenWhenDismountThrows() throws Exception {
        var f = new Fixture(); f.riding(null); f.player.throwOnExit = true;
        assertThrows(AssertionError.class, f::release); assertTrue(f.forced().isEmpty());
        assertTrue(f.pending()); assertTrue(f.player.isPassenger());
        f.player.throwOnExit = false; f.spawn.ground(0, 0); f.player.tickCount = 20; f.tick();
        f.assertReleased(new Vec3(.5, 64, .5));
    }

    @Test void normalExitFollowedByAnotherVehicleDoesNotStealItsRideOrMovement() throws Exception {
        var f = new Fixture(); f.riding(null); var other = new Body();
        f.platform.passengers.clear(); f.player.vehicle = other; f.player.velocity = new Vec3(1, 0, 1);
        f.tick();
        assertSame(other, f.player.vehicle); assertEquals(0, f.player.teleports);
        assertFalse(f.player.data.contains(TICKET)); assertEquals(new Vec3(1, 0, 1), f.player.velocity);
    }

    @Test void exactForgeDisconnectHookPrecedesSaveAndPersistentDataHasSaveLoadWiring() throws Exception {
        var list = readClass("net/minecraft/server/players/PlayerList");
        var remove = list.methods.stream().filter(m -> m.name.equals("remove")
                && m.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;)V")).findFirst().orElseThrow();
        int logout = -1, save = -1;
        for (int i = 0; i < remove.instructions.size(); i++) {
            if (remove.instructions.get(i) instanceof MethodInsnNode call) {
                if (call.name.equals("firePlayerLoggedOut")) logout = i;
                if (call.name.equals("save")) save = i;
            }
        }
        assertTrue(logout >= 0 && save > logout, "recovery must run before logout saves player/RootVehicle");
        var entity = readClass("net/minecraft/world/entity/Entity");
        for (String method : List.of("saveWithoutId", "load")) {
            var code = entity.methods.stream().filter(m -> m.name.equals(method)).findFirst().orElseThrow();
            assertTrue(Arrays.stream(code.instructions.toArray()).anyMatch(i -> i instanceof LdcInsnNode ldc
                    && "ForgeData".equals(ldc.cst)), method);
            assertTrue(Arrays.stream(code.instructions.toArray()).anyMatch(i -> i instanceof FieldInsnNode field
                    && field.name.equals("persistentData")), method);
        }
    }

    private static ClassNode readClass(String name) throws Exception {
        var node = new ClassNode();
        try (var input = DnvHoverPlatformCrewTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(node, 0);
        }
        return node;
    }

    public static class Body {
        public UUID id = UUID.randomUUID(); public World world;
        public boolean alive = true, removed; public Vec3 pos = new Vec3(100.5, 128, 100.5);
        public float fallDistance; public int tickCount;
        public UUID getUUID() { return id; } public World level() { return world; }
        public boolean isAlive() { return alive; } public boolean isRemoved() { return removed; }
        public Vec3 position() { return pos; } public double getX() { return pos.x; }
        public double getY() { return pos.y; } public double getZ() { return pos.z; }
        public float getBbHeight() { return 1.8F; }
        public double distanceToSqr(Body other) { return pos.distanceToSqr(other.pos); }
        public AABB getBoundingBox() { return new AABB(pos.x - .3, pos.y, pos.z - .3, pos.x + .3, pos.y + 1.8, pos.z + .3); }
    }
    public static class Rider extends Body {
        public CompoundTag data = new CompoundTag(); public Body vehicle;
        public boolean gunClaimed = true, vetoExit, rejectMount, rejectTeleport, throwOnExit;
        public int teleports, messages; public Vec3 velocity = Vec3.ZERO; public Consumer<Mount> dispatch;
        public CompoundTag getPersistentData() { return data; }
        public World serverLevel() { return world; } public Server getServer() { return world.server; }
        public Body getVehicle() { return vehicle; } public boolean isPassenger() { return vehicle != null; }
        public float getYRot() { return 0; } public float getXRot() { return 0; }
        public void setDeltaMovement(Vec3 value) { velocity = value; }
        public void sendSystemMessage(Message value) { messages++; }
        public boolean startRiding(Body body, boolean force) {
            if (rejectMount) return false;
            vehicle = body; ((Platform) vehicle).passengers.add(this); return true;
        }
        public void stopRiding() {
            if (vehicle == null) return;
            if (throwOnExit) throw new IllegalStateException("injected dismount failure");
            var event = new Mount(this, vehicle, true); event.canceled = vetoExit; dispatch.accept(event);
            if (!event.canceled) { var old = (Platform) vehicle; vehicle = null; old.removePassenger(this); }
        }
        public void teleportTo(World target, double x, double y, double z, float yaw, float pitch) {
            teleports++;
            if (!rejectTeleport) { world = target; pos = new Vec3(x, y, z); }
        }
    }
    public static class Platform extends Body {
        public UUID ownerId; public Vec3 anchor, boardAnchor; public boolean routeActive, gunsActive = true;
        public final DnvHoverPlatformStations stations = new DnvHoverPlatformStations();
        public long nextAiShotTick, nextPlayerShotTick;
        public int removalCalls; public final List<Body> passengers = new ArrayList<>();
        public Optional<UUID> ownerId() { return Optional.ofNullable(ownerId); }
        public Optional<Vec3> boardAnchor() { return Optional.ofNullable(anchor); }
        public void setBoardAnchor(Vec3 value) { anchor = value; } public void clearBoardAnchor() { anchor = null; }
        public List<Body> getPassengers() { return passengers; }
        public void addAdditionalSaveData(CompoundTag tag) {}
        public void readAdditionalSaveData(CompoundTag tag) {}
        public void setNoAi(boolean value) {}
        public void setNoGravity(boolean value) {}
        public void removePassenger(Body body) { passengers.remove(body); }
        public void releaseExternalState(Removal reason) { throw new AssertionError("Compiled domain override required"); }
        public void remove(Removal reason) {
            for (Body body : List.copyOf(passengers)) {
                assertTrue(((Rider) body).data.getCompound(TICKET).getBoolean("Pending"), "safety must transfer before removal");
            }
            removalCalls++; removed = true;
        }
    }
    public enum Removal { KILLED, DISCARDED, UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER, CHANGED_DIMENSION;
        public boolean shouldDestroy(){return this==KILLED||this==DISCARDED;} }
    public static class Routes {public static void platformRemoved(Server server,UUID owner,UUID platform) { }}
    public static class Runtime {
        public static boolean active(Platform p) { return p.routeActive; }
        public static void stop(Platform p) { p.routeActive = false; }
    }
    public static class Weapons {
        public static Object release(Rider p) { p.gunClaimed = false; return null; }
        public static void dropPlatform(Platform p) { p.gunsActive = false; }
    }
    public static class Server {
        public World spawn; public final List<Rider> players = new ArrayList<>();
        public World overworld() { return spawn; } public Server getPlayerList() { return this; }
        public List<Rider> getPlayers() { return players; }
    }
    public static class Key {
        final String id; public Key(String id) { this.id = id; }
        public Location location() { return new Location(id); }
    }
    public record Location(String id) { @Override public String toString() { return id; } }
    public static class World {
        public boolean isClientSide, loaded = true, boundsLoaded = true, collision;
        public Server server; public Key key = new Key("test:dimension"); public Border border = new Border();
        public int chunkRequests; public State hazard; public Set<String> tiles = new HashSet<>();
        public void ground(int x, int z) { tiles.add(x + ":" + z); }
        public Body getEntity(UUID id){return server.players.stream().map(p->p.vehicle).filter(java.util.Objects::nonNull)
                .filter(body->body.getUUID().equals(id)).findFirst().orElse(null);}
        public Key dimension() { return key; } public Server getServer() { return server; }
        public Object getChunkAt(BlockPos pos) { chunkRequests++; loaded = true; return null; }
        public BlockPos getSharedSpawnPos() { return new BlockPos(0, 64, 0); }
        public boolean hasChunk(int x, int z) { return loaded; }
        public boolean hasChunksAt(BlockPos min, BlockPos max) { return loaded && boundsLoaded; }
        public int getHeight(Heightmap type, int x, int z) { return tiles.contains(x + ":" + z) ? 64 : -64; }
        public int getMinBuildHeight() { return -64; } public int getMaxBuildHeight() { return 320; }
        public State getBlockState(BlockPos pos) { return new State(null, false); }
        public Border getWorldBorder() { return border; }
        public boolean noCollision(Body body, AABB bounds) { return !collision; }
        public Stream<State> getBlockStates(AABB bounds) { return hazard == null ? Stream.empty() : Stream.of(hazard); }
    }
    public enum Heightmap { MOTION_BLOCKING_NO_LEAVES }
    public static class Border {
        public double centerX, centerZ, radius = 1000;
        public double getCenterX() { return centerX; } public double getCenterZ() { return centerZ; }
        public boolean isWithinBounds(BlockPos p) { return Math.abs(p.getX() - centerX) < radius && Math.abs(p.getZ() - centerZ) < radius; }
        public boolean isWithinBounds(AABB b) { return b.minX >= centerX - radius && b.maxX <= centerX + radius
                && b.minZ >= centerZ - radius && b.maxZ <= centerZ + radius; }
    }
    public static class Block {}
    public static class Blocks {
        public static final Block FIRE = new Block(), SOUL_FIRE = new Block(), MAGMA_BLOCK = new Block(),
                CACTUS = new Block(), SWEET_BERRY_BUSH = new Block(), CAMPFIRE = new Block(),
                SOUL_CAMPFIRE = new Block(), WITHER_ROSE = new Block();
    }
    public record Fluid(boolean empty) { public boolean isEmpty() { return empty; } }
    public static class State {
        final Block block; final boolean fluid;
        public State(Block block, boolean fluid) { this.block = block; this.fluid = fluid; }
        public boolean isSolid() { return true; } public boolean is(Block value) { return block == value; }
        public Fluid getFluidState() { return new Fluid(!fluid); }
    }
    public static class Message { public static Message translatable(String key) { return new Message(); } }
    public static class PlayerEvent {
        final Body player; public PlayerEvent(Body player) { this.player = player; }
        public Body getEntity() { return player; }
    }
    public static class ServerEvent {
        final Server server; public ServerEvent(Server server) { this.server = server; }
        public Server getServer() { return server; }
    }
    public static class Mount {
        final Body rider, platform; final boolean dismount; public boolean canceled;
        public Mount(Body rider, Body platform, boolean dismount) { this.rider = rider; this.platform = platform; this.dismount = dismount; }
        public Body getEntityMounting() { return rider; } public Body getEntityBeingMounted() { return platform; }
        public boolean isDismounting() { return dismount; } public void setCanceled(boolean value) { canceled = value; }
    }
    public static class Leave {
        final Body entity; public Leave(Body entity) { this.entity = entity; }
        public Body getEntity() { return entity; } public World getLevel() { return entity.world; }
    }
    public enum Phase { START, END }
    public static class Tick { public Phase phase = Phase.END; public Body player; public Tick(Body player) { this.player = player; } }
    public static class Attack extends PlayerEvent {
        public boolean canceled; final boolean kill;
        public Attack(Body player, boolean kill) { super(player); this.kill = kill; }
        public Damage getSource() { return new Damage(kill); } public void setCanceled(boolean value) { canceled = value; }
    }
    public record Damage(boolean kill) { public boolean is(Key key) { return kill; } }
    public static class Damages { public static final Key GENERIC_KILL = new Key("kill"); }

    private static final class Fixture extends ClassLoader {
        final World world = new World(), spawn = new World(); final Server server = new Server();
        final Rider player = new Rider(); final Platform platform; final Class<?> crew;
        Fixture() throws Exception {
            super(DnvHoverPlatformCrewTest.class.getClassLoader());
            String base = "com/liucodex/doggiesnewvehicle/compat/", source = base + "DnvHoverPlatformCrew";
            String probe = base + "hovercrewprobe/"; var names = new HashMap<String, String>();
            names.put(source, probe + "Crew");
            for (String nested : List.of("BoardIssue", "RescueResult")) names.put(source + "$" + nested, probe + "Crew$" + nested);
            names.put(base + "DnvHoverCrewExitRules", probe + "ExitRules");
            for (var e : Map.ofEntries(
                    Map.entry("net/minecraft/world/entity/Entity", Body.class),
                    Map.entry("net/minecraft/world/entity/LivingEntity", Body.class),
                    Map.entry("net/minecraft/world/entity/player/Player", Body.class),
                    Map.entry("net/minecraft/server/level/ServerPlayer", Rider.class),
                    Map.entry("net/minecraft/world/entity/Mob", Platform.class),
                    Map.entry("net/minecraft/world/entity/Entity$RemovalReason", Removal.class),
                    Map.entry("net/minecraft/world/level/Level", World.class),
                    Map.entry("net/minecraft/server/level/ServerLevel", World.class),
                    Map.entry("net/minecraft/world/level/chunk/LevelChunk", Object.class),
                    Map.entry("net/minecraft/server/MinecraftServer", Server.class),
                    Map.entry("net/minecraft/server/players/PlayerList", Server.class),
                    Map.entry("net/minecraft/resources/ResourceKey", Key.class),
                    Map.entry("net/minecraft/resources/ResourceLocation", Location.class),
                    Map.entry("net/minecraft/world/level/border/WorldBorder", Border.class),
                    Map.entry("net/minecraft/world/level/levelgen/Heightmap$Types", Heightmap.class),
                    Map.entry("net/minecraft/world/level/block/state/BlockState", State.class),
                    Map.entry("net/minecraft/world/level/material/FluidState", Fluid.class),
                    Map.entry("net/minecraft/world/level/block/Blocks", Blocks.class),
                    Map.entry("net/minecraft/world/level/block/Block", Block.class),
                    Map.entry("net/minecraft/network/chat/Component", Message.class),
                    Map.entry("net/minecraft/network/chat/MutableComponent", Message.class),
                    Map.entry("net/minecraft/world/damagesource/DamageTypes", Damages.class),
                    Map.entry("net/minecraft/world/damagesource/DamageSource", Damage.class),
                    Map.entry("net/minecraftforge/event/entity/EntityMountEvent", Mount.class),
                    Map.entry("net/minecraftforge/event/entity/EntityLeaveLevelEvent", Leave.class),
                    Map.entry("net/minecraftforge/event/entity/player/PlayerEvent$PlayerLoggedInEvent", PlayerEvent.class),
                    Map.entry("net/minecraftforge/event/entity/player/PlayerEvent$PlayerLoggedOutEvent", PlayerEvent.class),
                    Map.entry("net/minecraftforge/event/entity/living/LivingDeathEvent", PlayerEvent.class),
                    Map.entry("net/minecraftforge/event/entity/living/LivingAttackEvent", Attack.class),
                    Map.entry("net/minecraftforge/event/server/ServerStoppingEvent", ServerEvent.class),
                    Map.entry("net/minecraftforge/event/server/ServerStoppedEvent", ServerEvent.class),
                    Map.entry("net/minecraftforge/event/TickEvent$PlayerTickEvent", Tick.class),
                    Map.entry("net/minecraftforge/event/TickEvent$Phase", Phase.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/entity/DnvHoverRoutePlatformEntity", Platform.class),
                    Map.entry(base + "DnvHoverRoutePlatformRuntime", Runtime.class),
                    Map.entry(base + "DnvHoverRoutePlatformServices", Routes.class),
                    Map.entry(base + "DnvHoverPlatformWeapons", Weapons.class),
                    Map.entry(base + "DnvHoverPlatformWeapons$SessionIssue", Object.class)).entrySet()) {
                names.put(e.getKey(), Type.getInternalName(e.getValue()));
            }
            define(base + "DnvHoverCrewExitRules", probe + "ExitRules", names, false);
            for (String nested : List.of("BoardIssue", "RescueResult")) define(source + "$" + nested, probe + "Crew$" + nested, names, false);
            crew = define(source, probe + "Crew", names, false);
            platform = (Platform) define("com/liucodex/doggiesnewvehicle/entity/DnvHoverRoutePlatformEntity",
                    probe + "PlatformRemoval", names, true).getConstructor().newInstance();
            world.server = spawn.server = server; server.spawn = spawn; server.players.add(player);
            spawn.key = new Key("minecraft:overworld"); player.world = platform.world = world;
            platform.ownerId = player.id;
            player.dispatch = event -> { try { call("mount", event); } catch (Exception failure) { throw new AssertionError(failure); } };
        }
        private Class<?> define(String source, String target, Map<String, String> names, boolean entity) throws Exception {
            var node = new ClassNode();
            try (var input = getResourceAsStream(source + ".class")) { new ClassReader(Objects.requireNonNull(input)).accept(node, 0); }
            node.visibleAnnotations = null; node.nestMembers = null; node.nestHostClass = null; node.innerClasses.clear();
            if (entity) {
                node.name = target; node.superName = Type.getInternalName(Platform.class); node.interfaces.clear(); node.fields.clear();
                node.methods.removeIf(m -> !Set.of("remove", "releaseExternalState", "removePassenger", "addAdditionalSaveData", "readAdditionalSaveData").contains(m.name));
                var init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, "<init>", "()V", false));
                init.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(init);
            }
            for (var method : node.methods) {
                method.access = (method.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                method.visibleAnnotations = null;
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/network/chat/Component")) call.itf = false;
                }
            }
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(writer, new SimpleRemapper(names)));
            byte[] bytes = writer.toByteArray(); return defineClass(target.replace('/', '.'), bytes, 0, bytes.length);
        }
        Object call(String name, Object... args) throws Exception {
            var method = Arrays.stream(crew.getMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
            try { return method.invoke(null, args); }
            catch (InvocationTargetException failure) { throw new AssertionError(name, failure.getCause()); }
        }
        void riding(Vec3 anchor) throws Exception {
            platform.anchor = anchor; player.vehicle = platform; platform.passengers.add(player);
            call("rememberRide", player, platform);
        }
        String release() throws Exception { return call("releaseOnPlatformRemoval", platform).toString(); }
        boolean pending() { return player.data.getCompound(TICKET).getBoolean("Pending"); }
        void tick() throws Exception { call("playerTick", new Tick(player)); }
        Set<?> forced() throws Exception { var field = crew.getDeclaredField("FORCED_RELEASE"); field.setAccessible(true); return (Set<?>) field.get(null); }
        void assertReleased(Vec3 destination) {
            assertEquals(destination, player.pos); assertFalse(player.isPassenger());
            assertFalse(player.data.contains(TICKET)); assertFalse(player.gunClaimed);
        }
    }
}
