package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes compiled station guards and physical rider/save code against explicit bounded world doubles. */
class DnvM1RemoteCrewExecutionTest {
  @Test void physicalBodyAdmissionRejectsUnsafeGroundFluidBorderAndOccupancy() throws Exception {
    var f = new Fixture(); var world = f.turret.world;
    assertTrue(f.safe(true));
    world.collision = true; assertFalse(f.safe(true)); world.collision = false;
    world.fluid = true; assertFalse(f.safe(true)); world.fluid = false;
    world.floor = false; assertFalse(f.safe(true)); world.floor = true;
    world.border = false; assertFalse(f.safe(true)); world.border = true;
    world.loaded = false; assertFalse(f.safe(true)); world.loaded = true;
    world.outsideHeight = true; assertFalse(f.safe(true)); world.outsideHeight = false;
    world.occupants.add(new Body()); assertFalse(f.safe(true)); assertTrue(f.safe(false));
    world.occupants.clear(); world.occupants.add(f.turret); assertTrue(f.safe(true));
  }

  @Test void physicalAdmissionNeverReplacesAnExistingRideAndAllowsOnlyAvailableActor() throws Exception {
    var f = new Fixture(); var player = f.turret.player;
    assertEquals("ALREADY_RIDING", f.rejection());
    player.vehicle = null; assertEquals("NONE", f.rejection());
    player.sleeping = true; assertEquals("ACTOR_UNAVAILABLE", f.rejection()); player.sleeping = false;
    player.spectator = true; assertEquals("ACTOR_UNAVAILABLE", f.rejection()); player.spectator = false;
    f.turret.world.collision = true; assertEquals("SEAT_BLOCKED", f.rejection());
  }
  @Test void nativeRiderAndSaveRemainAtTheStationAcrossRemoteTurretPositions() throws Exception {
    var fixture = new Fixture(); var turret = fixture.turret; var player = turret.player;
    turret.body = new BlockPos(12, 64, -37);
    fixture.position(player);
    assertEquals(Vec3.atBottomCenterOf(turret.body), player.pos);
    var tag = new CompoundTag(); tag.put("RootVehicle", new CompoundTag());
    fixture.save(player, tag);
    var pos = tag.getList("Pos", Tag.TAG_DOUBLE);
    assertEquals(12.5, pos.getDouble(0)); assertEquals(64, pos.getDouble(1)); assertEquals(-36.5, pos.getDouble(2));
    assertFalse(tag.contains("RootVehicle"));
    assertFalse((boolean) fixture.entityType.getMethod("hasExactlyOnePlayerPassenger").invoke(turret));
    assertEquals(player.pos, fixture.entityType.getMethod("getDismountLocationForPassenger", Body.class).invoke(turret, player));
    var other = new Body(); other.pos = new Vec3(99, 70, 99);
    fixture.position(other); assertEquals(new Vec3(99, 70, 99), other.pos);
    player.vehicle = null; var unchanged = new CompoundTag(); unchanged.putString("untouched", "yes");
    var copy = unchanged.copy(); fixture.save(player, unchanged); assertEquals(copy, unchanged);
  }

  @Test void actualGuardRejectsDeathLogoutOwnershipRevisionRemovalAndObstruction() throws Exception {
    var f = new Fixture(); assertTrue(f.valid());
    f.turret.player.alive = false; assertFalse(f.valid()); f.turret.player.alive = true;
    f.turret.player.spectator = true; assertFalse(f.valid()); f.turret.player.spectator = false;
    f.turret.player.server.current = null; assertFalse(f.valid()); f.turret.player.server.current = f.turret.player;
    f.turret.revision++; assertFalse(f.valid()); f.turret.revision--;
    f.turret.player.id = UUID.randomUUID(); assertFalse(f.valid()); f.turret.player.id = f.turret.actor;
    f.turret.world.status = DnvRemoteWeaponLinks.Reason.TARGET_UNLOADED; assertFalse(f.valid());
    f.turret.world.status = DnvRemoteWeaponLinks.Reason.NONE;
    var anchor = f.turret.world.anchor; f.turret.world.anchor = null; assertFalse(f.valid()); f.turret.world.anchor = anchor;
    f.turret.world.safe = false; assertFalse(f.valid()); f.turret.world.safe = true;
    f.turret.player.pos = f.turret.player.pos.add(1, 0, 0); assertFalse(f.valid());
  }

  @Test void queuedNativeFireCannotSurviveExitAndReentryOrAChangedStation() throws Exception {
    var f = new Fixture(); int[] shots = {0};
    Runnable admitted = f.guard(() -> shots[0]++); admitted.run(); assertEquals(1, shots[0]);
    var stopped=f.guard(()->shots[0]++);f.turret.readiness="inactive";stopped.run();assertEquals(1,shots[0]);
    f.turret.readiness="unpowered";stopped.run();assertEquals(1,shots[0]);f.turret.readiness="ready";
    Runnable stale = f.guard(() -> shots[0]++); f.turret.generation += 2; stale.run(); assertEquals(1, shots[0]);
    var disconnected = f.guard(() -> shots[0]++); f.turret.player.server.current = null; disconnected.run(); assertEquals(1, shots[0]);
    var nativeOther = new Body(); Runnable work = () -> shots[0]++;
    assertSame(work, f.guard(nativeOther, work));
  }

  public static class Body {
    public Vec3 pos = Vec3.ZERO; public UUID id = UUID.randomUUID(); public Body vehicle;
    public boolean alive = true, removed, spectator, sleeping;
    public Server server; public World world;
    public UUID getUUID() { return id; } public Vec3 position() { return pos; }
    public double getX() { return pos.x; } public double getY() { return pos.y; } public double getZ() { return pos.z; }
    public Body getVehicle() { return vehicle; } public boolean isAlive() { return alive; }
    public boolean isRemoved() { return removed; } public boolean isSpectator() { return spectator; }
    public World level() { return world; }
    public World serverLevel() { return world; }
    public boolean isPassenger() { return vehicle != null; }
    public boolean isSleeping() { return sleeping; }
    public Body getFirstPassenger() { return null; }
  }
  public static class Player extends Body {}
  public static class Turret extends Body {
    public boolean mountedCrew(){return false;}
    public boolean hasExactlyOnePlayerPassenger(){return false;}
    public void positionRider(Body body,Move move){move.accept(body,body.pos.x,body.pos.y,body.pos.z);}
    public Vec3 getDismountLocationForPassenger(Body body){return body.pos;}
    public String readiness="ready";public String remoteCrewReadiness(){return readiness;}
    public Player player; public UUID actor, station = UUID.randomUUID(); public BlockPos stationPos = new BlockPos(4, 64, 4);
    public BlockPos body = stationPos.relative(Direction.NORTH); public long revision = 1, generation = 1;
    public boolean isDestroyed() { return !alive; }
    public Body remoteCrewEntity() { return this; }
    public Body getFirstPassenger() { return player; }
    public boolean hasPassenger(Body body) { return body == player && body.vehicle == this; }
    public boolean isRemoteOperator(Body body) { return body != null && body.getUUID().equals(actor); }
    public UUID remoteStation() { return station; } public BlockPos remoteStationPosition() { return stationPos; }
    public long remoteStationRevision() { return revision; } public long remoteCrewGeneration() { return generation; }
    public BlockPos remoteBodyPosition() { return body; }
  }
  public interface Move { void accept(Body body, double x, double y, double z); }
  public static class Server {
    public Store store = new Store(); public Player current;
    public Server getPlayerList() { return this; } public Player getPlayer(UUID id) { return current; }
  }
  public static class World {
    public Anchor anchor = new Anchor(); public boolean safe = true;
    public boolean collision, fluid, outsideHeight;
    public boolean floor = true, border = true, loaded = true;
    public final List<Body> occupants = new ArrayList<>();
    public DnvRemoteWeaponLinks.Reason status = DnvRemoteWeaponLinks.Reason.NONE;
    public boolean isOutsideBuildHeight(BlockPos pos) { return outsideHeight; }
    public Border getWorldBorder() { return new Border(border); }
    public boolean hasChunkAt(BlockPos pos) { return loaded; }
    public Fluid getFluidState(BlockPos pos) { return new Fluid(fluid); }
    public State getBlockState(BlockPos pos) { return new State(); }
    public boolean noCollision(Body player, AABB body) { return !collision; }
    public List<Body> getEntities(Body player, AABB body, java.util.function.Predicate<Body> filter) {
      return occupants.stream().filter(filter).toList();
    }
  }
  public record Border(boolean within) { public boolean isWithinBounds(AABB body) { return within; } }
  public record Fluid(boolean present) { public boolean isEmpty() { return !present; } }
  public static class Store {
    public boolean writable = true; public DnvRemoteWeaponLinks.Station station;
    public static Store get(Server server) { return server.store; } public boolean writable() { return writable; }
    public Optional<DnvRemoteWeaponLinks.Station> station(UUID id) { return Optional.ofNullable(station); }
  }
  public static class Property {}
  public static class Content { public static final Property FACING = new Property(); }
  public static class State {
    public boolean hasProperty(Property value) { return true; }
    public Comparable<?> getValue(Property value) { return Direction.NORTH; }
    public boolean isFaceSturdy(World level, BlockPos pos, Direction face) { return level.floor; }
  }
  public static class Anchor {
    public BlockPos pos; public State getBlockState() { return new State(); } public BlockPos getBlockPos() { return pos; }
  }
  public static class Services {
    public static DnvRemoteWeaponLinks.Reason status(World world, DnvRemoteWeaponLinks.Station station) { return world.status; }
    public static Anchor anchor(World world, BlockPos pos) { return world.anchor; }
  }
  public static class Seat { public static boolean safe(Player player, Vec3 pos, boolean occupancy) { return player.world.safe; } }

  private static final class Fixture extends ClassLoader {
    final String crewName = "com/liucodex/doggiesnewvehicle/compat/crewprobe/Crew";
    final String entityName = "com/liucodex/doggiesnewvehicle/compat/crewprobe/Entity";
    final Class<?> crewType, entityType, safetyType; final Turret turret;
    Fixture() throws Exception {
      super(Fixture.class.getClassLoader());
      String entity = "com/liucodex/doggiesnewvehicle/entity/DnvM1YunlongEntity";
      String crew = "com/liucodex/doggiesnewvehicle/compat/DnvM1RemoteCrew";
      Map<String, String> map = new HashMap<>();
      map.put(crew, crewName); map.put(entity, Type.getInternalName(Turret.class));
      map.put("com/liucodex/doggiesnewvehicle/api/combat/DnvNativeCrewTarget", Type.getInternalName(Turret.class));
      map.put("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity", Type.getInternalName(Body.class));
      map.put("com/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity", Type.getInternalName(Turret.class));
      map.put("net/minecraft/world/entity/Entity", Type.getInternalName(Body.class));
      map.put("net/minecraft/world/entity/LivingEntity", Type.getInternalName(Body.class));
      map.put("net/minecraft/world/entity/Entity$MoveFunction", Type.getInternalName(Move.class));
      map.put("net/minecraft/server/level/ServerPlayer", Type.getInternalName(Player.class));
      map.put("net/minecraft/server/MinecraftServer", Type.getInternalName(Server.class));
      map.put("net/minecraft/server/players/PlayerList", Type.getInternalName(Server.class));
      map.put("net/minecraft/world/level/Level", Type.getInternalName(World.class));
      map.put("net/minecraft/world/level/BlockGetter", Type.getInternalName(World.class));
      map.put("net/minecraft/world/level/border/WorldBorder", Type.getInternalName(Border.class));
      map.put("net/minecraft/world/level/material/FluidState", Type.getInternalName(Fluid.class));
      map.put("net/minecraft/server/level/ServerLevel", Type.getInternalName(World.class));
      map.put("net/minecraft/world/level/block/entity/BlockEntity", Type.getInternalName(Anchor.class));
      map.put("net/minecraft/world/level/block/state/BlockState", Type.getInternalName(State.class));
      map.put("net/minecraft/world/level/block/state/properties/Property", Type.getInternalName(Property.class));
      map.put("net/minecraft/world/level/block/state/properties/DirectionProperty", Type.getInternalName(Property.class));
      for (var entry : Map.of("DnvRemoteWeaponLinkStore",Store.class,"DnvRemoteWeaponServices",Services.class,
          "DnvM1CrewSafety",Seat.class,"DnvRemoteWeaponStationContent",Content.class).entrySet())
        map.put("com/liucodex/doggiesnewvehicle/compat/" + entry.getKey(), Type.getInternalName(entry.getValue()));
      crewType = define(crew, crewName, Set.of("valid", "preserveBodySave", "guardQueuedShot"), map, false);
      entityType = define(entity, entityName, Set.of("positionRider", "getDismountLocationForPassenger", "hasExactlyOnePlayerPassenger"), map, true);
      map.put("com/liucodex/doggiesnewvehicle/compat/DnvM1CrewSafety", "com/liucodex/doggiesnewvehicle/compat/crewprobe/Safety");
      safetyType = define("com/liucodex/doggiesnewvehicle/compat/DnvM1CrewSafety",
          "com/liucodex/doggiesnewvehicle/compat/crewprobe/Safety", Set.of("safe", "rejection"), map, false);
      turret = (Turret) entityType.getConstructor().newInstance();
      var player = new Player(); var server = new Server(); var world = new World();
      turret.player = player; turret.actor = player.id; turret.world = player.world = world;
      player.server = server; player.vehicle = turret; player.pos = Vec3.atBottomCenterOf(turret.body); server.current = player;
      world.anchor.pos = turret.stationPos;
      server.store.station = new DnvRemoteWeaponLinks.Station(turret.station, turret.actor, "minecraft:overworld",
          turret.stationPos.asLong(), 1, Optional.of(new DnvRemoteWeaponLinks.Target(turret.id, "doggievehicle:m1_yunlong", "minecraft:overworld")));
    }
    private Class<?> define(String source, String name, Set<String> methods, Map<String,String> map, boolean entity) throws Exception {
      var node = new ClassNode();
      try (var in = getResourceAsStream(source + ".class")) { new ClassReader(in.readAllBytes()).accept(node, 0); }
      node.name = name; node.superName = entity ? Type.getInternalName(Turret.class) : "java/lang/Object";
      node.interfaces.clear(); node.fields.clear(); node.innerClasses.clear(); node.nestMembers = null;
      // JDK/platform projection can renumber synthetic methods; retain the actual guard's lambda body.
      node.methods.removeIf(m -> !methods.contains(m.name)
          && !m.name.startsWith("lambda$guardQueuedShot$") && !m.name.startsWith("lambda$safe$"));
      node.access |= Opcodes.ACC_PUBLIC;
      for (var method : node.methods) if (methods.contains(method.name)) method.access |= Opcodes.ACC_PUBLIC;
      for (var method : node.methods) for (var instruction : method.instructions) {
        if (instruction instanceof MethodInsnNode call && call.owner.equals("com/liucodex/doggiesnewvehicle/api/combat/DnvNativeCrewTarget")) {
          call.itf = false; call.setOpcode(Opcodes.INVOKEVIRTUAL);
        }
      }
      if (entity) {
        var init = new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));
        init.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(init);
      }
      var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
      node.accept(new ClassRemapper(writer,new Remapper() { @Override public String map(String value) { return map.getOrDefault(value,value); } }));
      byte[] bytes = writer.toByteArray(); return defineClass(name.replace('/','.'),bytes,0,bytes.length);
    }
    boolean valid() throws Exception { return (boolean) crewType.getMethod("valid",Turret.class).invoke(null,turret); }
    boolean safe(boolean occupancy) throws Exception {
      return (boolean) safetyType.getMethod("safe", Player.class, Vec3.class, boolean.class)
          .invoke(null, turret.player, turret.player.pos, occupancy);
    }
    String rejection() throws Exception {
      return (String) safetyType.getMethod("rejection", Player.class, Anchor.class)
          .invoke(null, turret.player, turret.world.anchor);
    }
    Runnable guard(Runnable work) throws Exception { return guard(turret,work); }
    Runnable guard(Body target,Runnable work) throws Exception { return (Runnable) crewType.getMethod("guardQueuedShot",Body.class,Runnable.class).invoke(null,target,work); }
    void position(Body body) throws Exception { entityType.getMethod("positionRider",Body.class,Move.class).invoke(turret,body,(Move)(p,x,y,z)->p.pos=new Vec3(x,y,z)); }
    void save(Body body,CompoundTag tag) throws Exception { crewType.getMethod("preserveBodySave",Body.class,CompoundTag.class).invoke(null,body,tag); }
  }
}
