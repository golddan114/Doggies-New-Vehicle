package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.liucodex.doggiesnewvehicle.platform.DnvHoverPlatformStations;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Runs compiled platform guards and selection against bounded entity/connection doubles. */
class DnvHoverPlatformWeaponsTest {
    @Test void blockedRetainedTargetCannotRestartTheCursorAndStarveTheVisibleEnemy() throws Exception {
        var f=new Fixture();var hidden=new Monster();var visible=new Monster();
        hidden.world=visible.world=f.world;hidden.visible=false;
        hidden.point=new Vec3(10,0,0);visible.point=new Vec3(20,0,0);
        f.world.entities.addAll(List.of(hidden,visible));f.targeting.candidates.live=List.of(hidden,visible);
        f.targeting.targetId=hidden.id;
        assertNull(f.nearest());assertNull(f.targeting.targetId);assertEquals(1,f.world.rays);
        f.world.time++;f.platform.tickCount++;
        assertSame(visible,f.nearest());assertEquals(visible.id,f.targeting.targetId);
        visible.point=visible.point.add(1,0,0);f.world.deferred=true;
        assertNull(f.nearest(),"Deferred movement invalidates cached sight and suspends live aiming");
    }

    @Test void enemyPlayersAreEligibleButTeamCreativeSpectatorPvpAndProviderGuardsStillWin() throws Exception {
        var f = new Fixture(); var enemy = new Player();
        assertTrue(f.legal(enemy));
        enemy.team = f.owner.team = "same"; assertFalse(f.legal(enemy));
        enemy.team = "enemy"; assertTrue(f.legal(enemy));
        enemy.creative = true; assertFalse(f.legal(enemy)); enemy.creative = false;
        enemy.spectator = true; assertFalse(f.legal(enemy)); enemy.spectator = false;
        f.owner.server.pvp = false; assertFalse(f.legal(enemy)); f.owner.server.pvp = true;
        f.owner.harmAllowed = false; assertFalse(f.legal(enemy)); f.owner.harmAllowed = true;
        enemy.excluded = true; assertFalse(f.legal(enemy)); enemy.excluded = false;
        assertFalse(f.legal(f.owner)); assertFalse(f.legal(f.platform));
        enemy.alive = false; assertFalse(f.legal(enemy)); enemy.alive = true;
        enemy.removed = true; assertFalse(f.legal(enemy));
        var occupied = new Body(); occupied.playerAboard = true; assertFalse(f.legal(occupied));
        var monster = new Monster(); assertTrue(f.legal(monster));
        monster.team = f.owner.team; assertFalse(f.legal(monster));
    }

    @Test void aiUsesBoundedCandidateScanAndKeepsImmediateShotGeometry() throws Exception {
        ClassNode node = new ClassNode();
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "com/liucodex/doggiesnewvehicle/compat/DnvHoverPlatformWeapons.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(node, 0);
        }
        var tick = node.methods.stream().filter(method -> method.name.equals("tick"))
                .findFirst().orElseThrow();
        var search = node.methods.stream().filter(method -> method.name.equals("nearestHostile"))
                .findFirst().orElseThrow();
        var tickCalls = Arrays.stream(tick.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        var searchCalls = Arrays.stream(search.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertTrue(tickCalls.stream().anyMatch(call -> call.name.equals("refreshIfDue")));
        assertTrue(tickCalls.stream().anyMatch(call -> call.name.equals("loadedSegment")));
        assertTrue(tickCalls.stream().anyMatch(call -> call.name.equals("clip")));
        assertTrue(searchCalls.stream().anyMatch(call -> call.name.equals("candidates")));
        assertTrue(searchCalls.stream().anyMatch(call -> call.name.equals("admitGeometryCheck")));
        assertFalse(searchCalls.stream().anyMatch(call -> call.name.equals("getEntitiesOfClass")));
    }

    @Test void expiredOrInvalidConnectionsReleaseBothIndexAndStationWithoutTakingOverAi() throws Exception {
        for (String reason : List.of("expired", "dead", "spectator", "logout", "replacement",
                "dimension", "distance", "removed", "owner", "unregistered", "same_uuid")) {
            var f = new Fixture(); f.claim(); assertTrue(f.active());
            switch (reason) {
                case "expired" -> f.world.time = DnvHoverPlatformStations.PLAYER_LEASE_TICKS;
                case "dead" -> f.owner.alive = false;
                case "spectator" -> f.owner.spectator = true;
                case "logout" -> f.owner.server.players.current = null;
                case "replacement" -> f.owner.server.players.current = new ServerPlayer();
                case "dimension" -> f.owner.world = new World();
                case "distance" -> f.platform.distance = 128D * 128D + 1;
                case "removed" -> f.platform.removed = true;
                case "owner" -> f.platform.owner = UUID.randomUUID();
                case "unregistered" -> f.world.entities.remove(f.platform);
                case "same_uuid" -> {var replacement=new Platform();replacement.id=f.platform.id;
                    f.world.entities.remove(f.platform);f.world.entities.add(replacement);}
                default -> fail(reason);
            }
            f.invoke("pruneSessions", new Class<?>[0]);
            assertTrue(f.sessions.isEmpty(), reason);
            assertFalse(f.platform.stations.playerMayFire(f.owner.id, f.owner.id, 0), reason);
            assertTrue(f.platform.stations.aiMayFire(f.world.time), reason);
        }
    }

    @Test void unloadAndReleaseAreIdempotentAndDoNotDropAnotherPlatformSession() throws Exception {
        var f = new Fixture(); f.claim(); var other = new Platform();
        f.invoke("clearSession", new Class<?>[]{ServerPlayer.class, Platform.class}, f.owner, other);
        assertTrue(f.active());
        f.invoke("dropPlatform", new Class<?>[]{Platform.class}, f.platform);
        f.invoke("dropPlatform", new Class<?>[]{Platform.class}, f.platform);
        assertTrue(f.sessions.isEmpty()); assertFalse(f.active());
        assertFalse(f.platform.stations.playerMayFire(f.owner.id, f.owner.id, 0));
    }

    public static class Body {
        public UUID id = UUID.randomUUID(); public String team;
        public boolean alive = true, removed, visible = true, excluded, playerAboard;
        public Vec3 point=Vec3.ZERO;public double distance; public World world;
        public Vec3 position(){return point;}public Vec3 getEyePosition(){return point.add(0,1.7,0);}
        public double getEyeY(){return point.y+1.7;}public AABB getBoundingBox(){return new AABB(point,point.add(1,2,1));}
        public boolean isAlive() { return alive; } public boolean isRemoved() { return removed; }
        public UUID getUUID() { return id; } public World level() { return world; }
        public boolean isAlliedTo(Body other) { return this == other || team != null && team.equals(other.team); }
        public double distanceToSqr(Body other) { return other.distance; }
    }
    public static class Player extends Body {
        public boolean creative, spectator;
        public boolean isCreative() { return creative; } public boolean isSpectator() { return spectator; }
    }
    public static class ServerPlayer extends Player {
        public Server server = new Server(); public boolean harmAllowed = true;
        public Server getServer() { return server; }
        public boolean canHarmPlayer(Player target) { return harmAllowed; }
    }
    public static class Monster extends Body {}
    public static class Platform extends Body {
        public int tickCount;public int getId(){return 42;}public float getBbHeight(){return 1;}
        public UUID owner; public final DnvHoverPlatformStations stations = new DnvHoverPlatformStations();
        public Optional<UUID> ownerId() { return Optional.ofNullable(owner); }
        public DnvHoverPlatformStations stations() { return stations; }
        public AABB getBoundingBox() { return new AABB(0, 0, 0, 1, 1, 1); }
        public Sensing getSensing() { return new Sensing(); }
    }
    public static class Sensing { public boolean hasLineOfSight(Body entity) { return entity.visible; } }
    public static class Server {
        public boolean pvp = true; public final Players players = new Players();
        public boolean isPvpAllowed() { return pvp; } public Players getPlayerList() { return players; }
    }
    public static class Players { public ServerPlayer current; public ServerPlayer getPlayer(UUID id) { return current; } }
    public static class World {
        public boolean deferred;public long lastGeometryTick=-1;public int rays;
        public Body getEntity(UUID id){return entities.stream().filter(e->e.id.equals(id)).findFirst().orElse(null);}
        public boolean hasChunk(int x,int z){return true;}public Border getWorldBorder(){return new Border();}
        public Hit clip(Clip clip){rays++;return new Hit(entities.stream().filter(e->e.getEyePosition().equals(clip.end)).allMatch(e->e.visible));}
        public long time; public final List<Body> entities = new ArrayList<>();
        public long getGameTime() { return time; }
        public <T extends Body> List<T> getEntitiesOfClass(Class<T> type, AABB bounds, Predicate<? super T> filter) {
            return entities.stream().filter(type::isInstance).map(type::cast).filter(filter).toList();
        }
    }
    public static class Policy { public static boolean isPvpProtected(Body entity) { return entity instanceof Player || entity.playerAboard; } }
    public static class Coordinator { public static boolean isExcluded(Body entity) { return entity.excluded; } }
    public static class Budget {
        public static void releaseGeometryOwner(World level, UUID id) { }
        public static boolean admitGeometryCheck(World world,UUID id){if(world.deferred||world.lastGeometryTick==world.time)return false;world.lastGeometryTick=world.time;return true;}
        public static int nextCheckTick(int tick,int id,int interval,int salt){return tick+interval;}
    }
    public static class Border {public boolean isWithinBounds(BlockPos pos){return true;}}
    public static class Hit {public final boolean clear;public Hit(boolean clear){this.clear=clear;}public HitResult.Type getType(){return clear?HitResult.Type.MISS:HitResult.Type.BLOCK;}}
    public static class Clip {
        public enum Block {COLLIDER}public enum Fluid {NONE}public final Vec3 end;
        public Clip(Vec3 from,Vec3 end,Block block,Fluid fluid,Body source){this.end=end;}
    }
    public static class Candidates {public List<Body> live=List.of();public List<Body> candidates(Body source){return live;}}
    public static class Targeting {public final Candidates candidates=new Candidates();public UUID targetId;public int cursor;public DnvSightObservation sight;}


    private static final class Fixture extends ClassLoader {
        final World world = new World(); final ServerPlayer owner = new ServerPlayer();
        final Platform platform = new Platform(); final Class<?> type;
        final Map<ServerPlayer, Platform> sessions;final Targeting targeting=new Targeting();
        @SuppressWarnings("unchecked") Fixture() throws Exception {
            super(DnvHoverPlatformWeaponsTest.class.getClassLoader());
            owner.world = platform.world = world; platform.owner = owner.id; owner.server.players.current = owner;world.entities.add(platform);
            String name = "com/liucodex/doggiesnewvehicle/compat/DnvHoverPlatformWeapons";
            String mapped = "com/liucodex/doggiesnewvehicle/compat/platformprobe/Weapons";
            Map<String, String> names = new HashMap<>(); names.put(name, mapped);
            map(names,name+"$AiTargeting",Targeting.class);
            map(names,"com/liucodex/doggiesnewvehicle/compat/DnvTargetCandidateCache",Candidates.class);
            map(names,"net/minecraft/world/level/ClipContext",Clip.class);
            map(names,"net/minecraft/world/level/ClipContext$Block",Clip.Block.class);
            map(names,"net/minecraft/world/level/ClipContext$Fluid",Clip.Fluid.class);
            map(names,"net/minecraft/world/phys/HitResult",Hit.class);
            map(names,"net/minecraft/world/phys/BlockHitResult",Hit.class);
            map(names,"net/minecraft/world/level/border/WorldBorder",Border.class);
            map(names, "net/minecraft/world/entity/Entity", Body.class);
            map(names, "net/minecraft/world/entity/LivingEntity", Body.class);
            map(names, "net/minecraft/world/entity/monster/Monster", Monster.class);
            map(names, "net/minecraft/world/entity/player/Player", Player.class);
            map(names, "net/minecraft/server/level/ServerPlayer", ServerPlayer.class);
            map(names, "net/minecraft/world/level/Level", World.class);
            map(names, "net/minecraft/server/level/ServerLevel", World.class);
            map(names, "net/minecraft/server/MinecraftServer", Server.class);
            map(names, "net/minecraft/server/players/PlayerList", Players.class);
            map(names, "net/minecraft/world/entity/ai/sensing/Sensing", Sensing.class);
            map(names, "com/liucodex/doggiesnewvehicle/entity/DnvHoverRoutePlatformEntity", Platform.class);
            map(names, "com/liucodex/doggiesnewvehicle/compat/DnvCombatPolicyRuntime", Policy.class);
            map(names, "com/liucodex/doggiesnewvehicle/compat/DnvCombatCoordinator", Coordinator.class);
            map(names, "com/liucodex/doggiesnewvehicle/compat/DnvTargetingBudget", Budget.class);
            var source = new ClassNode();
            try (var input = getResourceAsStream(name + ".class")) {
                new ClassReader(Objects.requireNonNull(input)).accept(source, 0);
            }
            Set<String> methods = Set.of("<clinit>", "authorized", "legalTarget", "nearestHostile", "aiMuzzle", "loadedSegment",
                    "activeSession", "pruneSessions", "clearSession", "dropPlatform", "lambda$dropPlatform$0");
            source.methods.removeIf(method -> !methods.contains(method.name) && !method.name.startsWith("lambda$dropPlatform$"));
            source.methods.forEach(method -> method.access = (method.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC);
            source.visibleAnnotations = null; source.nestMembers = null; source.innerClasses.clear();
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            source.accept(new ClassRemapper(writer, new SimpleRemapper(names)));
            byte[] bytes = writer.toByteArray(); type = defineClass(mapped.replace('/', '.'), bytes, 0, bytes.length);
            var field = type.getDeclaredField("SESSIONS"); field.setAccessible(true);
            sessions = (Map<ServerPlayer, Platform>) field.get(null);
        }
        private static void map(Map<String, String> names, String from, Class<?> to) { names.put(from, Type.getInternalName(to)); }
        Object invoke(String method, Class<?>[] parameters, Object... args) throws Exception {
            return type.getMethod(method, parameters).invoke(null, args);
        }
        Body nearest() throws Exception {
            return (Body)invoke("nearestHostile",new Class<?>[]{World.class,Platform.class,ServerPlayer.class,Targeting.class},world,platform,owner,targeting);
        }
        boolean legal(Body target) throws Exception {
            return (boolean) invoke("legalTarget", new Class<?>[]{Platform.class, Body.class, ServerPlayer.class}, platform, target, owner);
        }
        boolean active() throws Exception {
            return (boolean) invoke("activeSession", new Class<?>[]{ServerPlayer.class, Platform.class}, owner, platform);
        }
        void claim() {
            assertTrue(platform.stations.claimPlayer(owner.id, owner.id, world.time, platform.stations.revision()).accepted());
            sessions.put(owner, platform);
        }
    }
}
