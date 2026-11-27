package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

/** Executes production assignment/save/restore/removal against a scoreboard port. */
class ApocalypseAlliedMembershipExecutionTest {
    @Test void assignmentIsOnceOnlyAndReloadPreservesAdministratorReassignmentOrClearing() throws Exception {
        var f = new Fixture(); var entity = new Body();
        var red = entity.world.board.addPlayerTeam("red");
        f.call("request", new Class<?>[] {String.class}, "red");
        assertNull(entity.getTeam()); // Constructing a failed deployment never changes scoreboard membership.
        f.call("added", new Class<?>[] {Body.class}, entity);
        assertSame(red, entity.getTeam()); assertEquals(1, entity.world.board.joins);
        var blue = entity.world.board.addPlayerTeam("blue");
        entity.world.board.members.put(entity.name, blue);
        f.call("added", new Class<?>[] {Body.class}, entity);
        assertSame(blue, entity.getTeam());
        var tag = new CompoundTag(); f.call("save", new Class<?>[] {CompoundTag.class}, tag);
        var restored = new Fixture(); restored.call("load", new Class<?>[] {CompoundTag.class}, tag);
        entity.world.board.members.remove(entity.name);
        restored.call("added", new Class<?>[] {Body.class}, entity);
        assertNull(entity.getTeam());
        assertEquals(1, entity.world.board.joins);
    }

    @Test void defaultAndPreassignedTeamsSurviveUnloadAndOnlyManagedTerminalMembershipIsReleased() throws Exception {
        var f = new Fixture(); var entity = new Body();
        f.call("added", new Class<?>[] {Body.class}, entity);
        assertEquals("dnv_allied", entity.getTeam().getName());
        f.call("removed", new Class<?>[] {Body.class, Reason.class}, entity, Reason.UNLOADED);
        assertNotNull(entity.getTeam());
        f.call("removed", new Class<?>[] {Body.class, Reason.class}, entity, Reason.DESTROYED);
        assertNull(entity.getTeam());
        var preassigned = new Body(); var admin = preassigned.world.board.addPlayerTeam("admin");
        preassigned.world.board.members.put(preassigned.name, admin);
        var another = new Fixture(); another.call("added", new Class<?>[] {Body.class}, preassigned);
        another.call("removed", new Class<?>[] {Body.class, Reason.class}, preassigned, Reason.DESTROYED);
        assertSame(admin, preassigned.getTeam()); assertEquals(0, preassigned.world.board.joins);
    }

    private static class Fixture {
        final Class<?> type;
        final Object object;
        Fixture() throws Exception {
            String source = "com/liucodex/doggiesnewvehicle/armored/ApocalypseAlliedMembership";
            String name = "com/liucodex/doggiesnewvehicle/compat/executionprobe/AlliedMembership";
            Map<String, String> names = new HashMap<>(); names.put(source, name);
            Map.ofEntries(Map.entry("net/minecraft/world/entity/Entity", Body.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvDefaultTeams", Defaults.class),
                    Map.entry("net/minecraft/world/entity/Entity$RemovalReason", Reason.class),
                    Map.entry("net/minecraft/server/level/ServerLevel", Server.class),
                    Map.entry("net/minecraft/world/level/Level", World.class),
                    Map.entry("net/minecraft/world/scores/Team", Team.class),
                    Map.entry("net/minecraft/world/scores/PlayerTeam", Team.class),
                    Map.entry("net/minecraft/server/ServerScoreboard", Board.class),
                    Map.entry("net/minecraft/world/scores/Scoreboard", Board.class))
                    .forEach((key, value) -> names.put(key, Type.getInternalName(value)));
            ClassNode n = new ClassNode();
            try (var in = getClass().getResourceAsStream("/" + source + ".class")) { new ClassReader(in.readAllBytes()).accept(n, 0); }
            n.access = Opcodes.ACC_PUBLIC;
            n.methods.forEach(m -> m.access = (m.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            n.accept(new ClassRemapper(writer, new Remapper() { @Override public String map(String key) { return names.getOrDefault(key, key); } }));
            type = new ExactQueuedShotFixture.Loader(Map.of(name, writer.toByteArray())).loadClass(name.replace('/', '.'));
            object = type.getConstructor().newInstance();
        }
        Object call(String name, Class<?>[] types, Object... args) throws Exception { return type.getMethod(name, types).invoke(object, args); }
    }
    public enum Reason { UNLOADED, DESTROYED; public boolean shouldDestroy() { return this == DESTROYED; } }
    public static class World { public Board getScoreboard() { throw new AssertionError(); } }
    public static class Server extends World { public final Board board = new Board(); public Board getScoreboard() { return board; } }
    public static class Body {
        public final Server world = new Server();
        public final String name = UUID.randomUUID().toString();
        public World level() { return world; }
        public String getScoreboardName() { return name; }
        public Team getTeam() { return world.board.getPlayersTeam(name); }
    }
    public static class Team { private final String name; Team(String n) { name = n; } public String getName() { return name; } }
    public static class Defaults { public static Team getOrCreate(Board board,String name){Team team=board.getPlayerTeam(name);return team==null?board.addPlayerTeam(name):team;} }
    public static class Board {
        final Map<String, Team> teams = new HashMap<>(), members = new HashMap<>();
        int joins;
        public Team getPlayerTeam(String n) { return teams.get(n); }
        public Team addPlayerTeam(String n) { var team = new Team(n); teams.put(n, team); return team; }
        public Team getPlayersTeam(String n) { return members.get(n); }
        public boolean addPlayerToTeam(String n, Team t) { joins++; members.put(n, t); return true; }
        public void removePlayerFromTeam(String n, Team t) { members.remove(n, t); }
    }
}
