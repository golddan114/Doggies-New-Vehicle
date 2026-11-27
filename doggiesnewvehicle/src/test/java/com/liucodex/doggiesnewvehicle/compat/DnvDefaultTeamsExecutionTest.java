package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.ClassNode;

/** Executes both production assignment owners, with the vanilla scoreboard as the only state port. */
class DnvDefaultTeamsExecutionTest {
    @Test void firstTickAssignsCommandSpawnButNeverRejoinsClearedManagedOrInitializedTeam() throws Exception {
        var f=new Fixture();var heaven=new Heaven(new Server());
        f.initialize(heaven);assertEquals("dnv_guardians",heaven.getTeam().getName());
        heaven.world.board.members.clear();f.initialize(heaven);assertNull(heaven.getTeam());
        var old=new Heaven(heaven.world);f.boss(old);old.world.board.members.clear();
        f.initialize(old);assertNull(old.getTeam());
        var child=new Heaven(heaven.world);Team custom=child.world.board.addPlayerTeam("admin");
        f.bosses.getMethod("inheritIfUnassigned",Body.class,Team.class).invoke(null,child,custom);
        f.initialize(child);assertSame(custom,child.getTeam());
    }
    @Test void playerGuardiansAndHelldiversAreThreeActualTeamsWithSharedMembershipInsideEach() throws Exception {
        var f=new Fixture();var player=new Player();var ship=new Body(player.world);var noise=new Body(player.world);var heaven=new Heaven(player.world);
        f.player(player);f.boss(ship);f.boss(noise);f.boss(heaven);
        assertEquals("dnv_allied",player.getTeam().getName());assertEquals("dnv_boss",ship.getTeam().getName());
        assertEquals("dnv_guardians",heaven.getTeam().getName());assertSame(ship.getTeam(),noise.getTeam());
        assertEquals(3,Set.of(player.getTeam(),ship.getTeam(),heaven.getTeam()).size());
        assertEquals("team.doggiesnewvehicle.players",player.getTeam().label.key);
        assertEquals("team.doggiesnewvehicle.guardians",heaven.getTeam().label.key);
        assertEquals("team.doggiesnewvehicle.helldivers",ship.getTeam().label.key);
    }
    @Test void playerAssignmentDoesNotOverwriteCustomTeamOrRejoinAfterAdminClearsIncludingDeath() throws Exception {
        var f=new Fixture();var player=new Player();Team admin=player.world.board.addPlayerTeam("admin");
        player.world.board.addPlayerToTeam(player.name,admin);f.player(player);assertSame(admin,player.getTeam());
        player.world.board.members.clear();f.player(player);assertNull(player.getTeam());
        var replacement=new Player();f.defaults.getMethod("cloned",Clone.class).invoke(null,new Clone(player,replacement));
        f.player(replacement);assertNull(replacement.getTeam());
    }
    @Test void onlyStillManagedLegacyHeavenMovesAndUnloadOrManualReassignmentIsPreserved() throws Exception {
        var f=new Fixture();var old=new Heaven(new Server());Team legacy=old.world.board.addPlayerTeam("dnv_boss");
        old.world.board.addPlayerToTeam(old.name,legacy);old.tag.putBoolean("doggiesnewvehicle.boss_team_managed",true);
        old.tag.putString("doggiesnewvehicle.boss_team_assigned_name","dnv_boss");
        f.reconcile(old);assertEquals("dnv_guardians",old.getTeam().getName());
        f.remove(old,Reason.UNLOADED_TO_CHUNK);assertNotNull(old.getTeam());
        Team admin=old.world.board.addPlayerTeam("admin");old.world.board.addPlayerToTeam(old.name,admin);
        f.reconcile(old);f.remove(old,Reason.KILLED);assertSame(admin,old.getTeam());
        old.world.board.members.clear();f.reconcile(old);assertNull(old.getTeam());
        var unowned=new Heaven(old.world);old.world.board.addPlayerToTeam(unowned.name,legacy);
        f.reconcile(unowned);assertSame(legacy,unowned.getTeam());
    }
    @Test void manualDisplayNameAndSummonerTeamAreNeverReplaced() throws Exception {
        var f=new Fixture();var source=new Body(new Server());Team custom=source.world.board.addPlayerTeam("dnv_boss");
        custom.label=new Text("Admin label");f.boss(source);assertEquals("Admin label",custom.label.key);
        var child=new Body(source.world);
        f.bosses.getMethod("inheritIfUnassigned",Body.class,Team.class).invoke(null,child,custom);
        assertSame(custom,child.getTeam());f.remove(child,Reason.KILLED);assertNull(child.getTeam());
    }

    static class Fixture {
        final Class<?> defaults,bosses;
        Fixture() throws Exception {
            String base="com/liucodex/doggiesnewvehicle/compat/",probe=base+"executionprobe/";
            Map<String,String> names=new HashMap<>();
            names.put(base+"DnvDefaultTeams",probe+"DefaultTeams");names.put(base+"DnvBossTeamMembership",probe+"BossTeams");
            Map.ofEntries(Map.entry("net/minecraft/world/entity/Entity",Body.class),Map.entry("net/minecraft/world/entity/player/Player",Player.class),
                    Map.entry("net/minecraft/world/entity/Entity$RemovalReason",Reason.class),Map.entry("net/minecraft/world/level/Level",World.class),
                    Map.entry("net/minecraft/server/level/ServerLevel",Server.class),Map.entry("net/minecraft/world/scores/Scoreboard",Board.class),
                    Map.entry("net/minecraft/server/ServerScoreboard",Board.class),Map.entry("net/minecraft/world/scores/PlayerTeam",Team.class),
                    Map.entry("net/minecraft/world/scores/Team",Team.class),Map.entry("net/minecraft/network/chat/Component",Text.class),
                    Map.entry("net/minecraft/network/chat/MutableComponent",Text.class),
                    Map.entry("net/minecraftforge/event/entity/player/PlayerEvent$Clone",Clone.class),
                    Map.entry("net/neoforged/neoforge/event/entity/player/PlayerEvent$Clone",Clone.class),
                    Map.entry("com/liucodex/silentheaven/entity/SilentHeavenVehicleEntity",Heaven.class))
                    .forEach((k,v)->names.put(k,Type.getInternalName(v)));
            Map<String,byte[]> definitions=new HashMap<>();
            for(String simple:List.of("DnvDefaultTeams","DnvBossTeamMembership")) {
                ClassNode node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+base+simple+".class")){new ClassReader(in.readAllBytes()).accept(node,0);}
                node.methods.removeIf(m->Set.of("loggedIn","joined").contains(m.name));
                for(var method:node.methods)for(var instruction:method.instructions)
                    if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                            && call.owner.equals("net/minecraft/network/chat/Component")) {
                        call.itf=false;if(call.getOpcode()==Opcodes.INVOKEINTERFACE)call.setOpcode(Opcodes.INVOKEVIRTUAL);
                    }
                ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(new ClassRemapper(out,new SimpleRemapper(names)));
                definitions.put(names.get(base+simple),out.toByteArray());
            }
            var loader=new ExactQueuedShotFixture.Loader(definitions);
            defaults=loader.loadClass((probe+"DefaultTeams").replace('/','.'));bosses=loader.loadClass((probe+"BossTeams").replace('/','.'));
        }
        void player(Player player)throws Exception{defaults.getMethod("initializePlayer",Player.class).invoke(null,player);}
        void boss(Body entity)throws Exception{bosses.getMethod("assignIfUnassigned",Body.class).invoke(null,entity);}
        void initialize(Body entity)throws Exception{bosses.getMethod("initializeIfNeeded",Body.class).invoke(null,entity);}
        void reconcile(Body entity)throws Exception{bosses.getMethod("reconcileManagedDefault",Body.class).invoke(null,entity);}
        void remove(Body entity,Reason reason)throws Exception{bosses.getMethod("releaseOnTerminalRemoval",Body.class,Reason.class).invoke(null,entity,reason);}
    }
    public enum Reason{UNLOADED_TO_CHUNK,UNLOADED_WITH_PLAYER,CHANGED_DIMENSION,KILLED}
    public record Text(String key){public static Text translatable(String key){return new Text(key);}public String getString(){return key;}}
    public static class World{public Board getScoreboard(){throw new AssertionError();}}
    public static class Server extends World{public final Board board=new Board();@Override public Board getScoreboard(){return board;}}
    public static class Body {
        public final Server world;public final String name=UUID.randomUUID().toString();public final CompoundTag tag=new CompoundTag();
        public Body(Server world){this.world=world;}public World level(){return world;}public CompoundTag getPersistentData(){return tag;}
        public String getScoreboardName(){return name;}public Team getTeam(){return world.board.members.get(name);}
        public String getEncodeId(){return "doggiesnewvehicle:mothership";}
    }
    public static class Player extends Body{public Player(){super(new Server());}}
    public static class Heaven extends Body{public Heaven(Server world){super(world);}@Override public String getEncodeId(){return "doggievehicle:silent_heaven";}}
    public record Clone(Player original,Player entity){public Player getOriginal(){return original;}public Player getEntity(){return entity;}}
    public static class Team{private final String name;public Text label;public Team(String name){this.name=name;label=new Text(name);}
        public String getName(){return name;}public Text getDisplayName(){return label;}public void setDisplayName(Text value){label=value;}}
    public static class Board {
        public final Map<String,Team> teams=new HashMap<>(),members=new HashMap<>();
        public Team getPlayerTeam(String name){return teams.get(name);}public Team addPlayerTeam(String name){Team team=new Team(name);teams.put(name,team);return team;}
        public boolean addPlayerToTeam(String name,Team team){members.put(name,team);return true;}
        public void removePlayerFromTeam(String name,Team team){members.remove(name,team);}
    }
}
