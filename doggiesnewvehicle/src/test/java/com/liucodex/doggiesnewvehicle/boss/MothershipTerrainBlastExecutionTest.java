package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

/** Executes the compiled world adapter against block, Forge event and save ports. */
class MothershipTerrainBlastExecutionTest {
    @Test void boundedNativeBlockBatchesRespectProtectionAndDoNotDamageEntities() throws Exception {
        World world = new World();
        Ship ship = new Ship(world);
        Object blast = newBlast();
        begin(blast, ship);
        world.protectCenter = true;
        tick(blast, ship);
        assertEquals(1, world.batches.size());
        assertTrue(world.batches.get(0).size() <= MothershipBlastFront.MAX_BLOCKS_PER_TICK);
        assertFalse(world.batches.get(0).contains(new Pos(0, 64, 0)), "Forge removed the protected center");
        assertFalse(world.batches.get(0).contains(new Pos(100, 64, 100)), "Forge cannot inject a remote block");
        assertEquals(world.batches.get(0).size(), world.nativeDropCallbacks);
        assertEquals(0, world.entityDamage);
        tick(blast, ship);
        assertEquals(1, world.batches.size(), "One owner pass per game tick");
        world.time++;
        tick(blast, ship);
        assertEquals(2, world.batches.size());
        assertTrue(world.batches.get(1).size() <= MothershipBlastFront.MAX_BLOCKS_PER_TICK);
    }

    @Test void savedCursorResumesWithoutReplayingBlocksAndMissingStateStaysFinished() throws Exception {
        World world = new World(); Ship ship = new Ship(world); Object blast = newBlast();
        begin(blast, ship); tick(blast, ship);
        Set<Pos> first = new HashSet<>(world.batches.get(0));
        Tag saved = (Tag) BLAST.getMethod("save").invoke(blast);
        assertTrue(saved.getInt("Cursor") > 0);
        Object resumed = newBlast(); BLAST.getMethod("restore", Tag.class).invoke(resumed, saved);
        world.time++;
        tick(resumed, ship);
        assertEquals(2, world.batches.size());
        assertTrue(first.stream().noneMatch(world.batches.get(1)::contains));
        Object absent = newBlast(); BLAST.getMethod("restore", Tag.class).invoke(absent, new Tag());
        world.time++;
        tick(absent, ship);
        assertEquals(2, world.batches.size(), "Old saves cannot replay terrain destruction");
    }

    @Test void deniedOrUnsafeWorldLeavesBlocksUntouched() throws Exception {
        World world = new World(); Ship ship = new Ship(world); Object blast = newBlast();
        world.cancelStart = true; begin(blast, ship); tick(blast, ship);
        assertTrue(world.batches.isEmpty());
        world.cancelStart = false; world.mobGriefing = false; begin(blast, ship); tick(blast, ship);
        assertTrue(world.batches.isEmpty());
        world.mobGriefing = true; world.loaded = false; begin(blast, ship); tick(blast, ship);
        assertTrue(world.batches.isEmpty(), "An unloaded chunk is never forced in");
    }

    @Test void groundFireWaitsForAllTerrainBatchesAndSurvivesReloadExactlyOnce() throws Exception {
        World world = new World(); Ship ship = new Ship(world); Object blast = newBlast();
        begin(blast, ship, true);
        assertNull(tick(blast, ship));
        Tag saved = (Tag) BLAST.getMethod("save").invoke(blast);
        assertTrue(saved.getBoolean("IgnitePending"));
        Object resumed = newBlast(); BLAST.getMethod("restore", Tag.class).invoke(resumed, saved);
        Vec3 ignition = null;
        for (int i = 0; i < 200 && ignition == null; i++) {
            world.time++;
            ignition = tick(resumed, ship);
        }
        assertEquals(new Vec3(0, 64, 0), ignition);
        assertTrue(world.batches.size() > 1, "Terrain must have completed across bounded batches");
        world.time++;
        assertNull(tick(resumed, ship), "Fire must be requested only once");
        Tag finished = (Tag) BLAST.getMethod("save").invoke(resumed);
        assertFalse(finished.getBoolean("IgnitePending"));
        Object restored = newBlast(); BLAST.getMethod("restore", Tag.class).invoke(restored, finished);
        world.time++;
        assertNull(tick(restored, ship), "A completed saved blast must not reignite");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disablingTerrainPolicyCancelsRemainingDoomsdayTerrainAndIgnitionAcrossReload(boolean sbwCommand) throws Exception {
        World world = new World(); Ship ship = new Ship(world); Object blast = newBlast();
        begin(blast, ship, true); tick(blast, ship);
        int batches = world.batches.size(); int callbacks = world.nativeDropCallbacks;
        Tag saved = (Tag) BLAST.getMethod("save").invoke(blast);
        Object resumed = newBlast(); BLAST.getMethod("restore", Tag.class).invoke(resumed, saved);
        if (sbwCommand) world.explosions = false;
        else world.mobGriefing = false;
        world.time++;
        assertNull(tick(resumed, ship)); assertEquals(batches, world.batches.size());
        assertEquals(callbacks, world.nativeDropCallbacks); assertFalse((boolean)BLAST.getMethod("active").invoke(resumed));
        assertFalse(((Tag)BLAST.getMethod("save").invoke(resumed)).getBoolean("IgnitePending"));
        world.explosions = world.mobGriefing = true; world.time++;
        assertNull(tick(resumed, ship)); assertEquals(batches, world.batches.size(), "Re-enabling cannot replay a canceled blast");
        begin(resumed, ship, true); world.time++;
        tick(resumed, ship); assertTrue(world.batches.size() > batches, "A new authorized blast still works");
    }

    private static final Class<?> BLAST = load();
    private static Object newBlast() throws Exception { return BLAST.getConstructor().newInstance(); }
    private static void begin(Object blast, Ship ship) throws Exception { begin(blast, ship, false); }
    private static void begin(Object blast, Ship ship, boolean igniteGround) throws Exception {
        BLAST.getMethod("begin", Ship.class, Vec3.class, boolean.class).invoke(blast, ship, new Vec3(0, 64, 0), igniteGround);
    }
    private static Vec3 tick(Object blast, Ship ship) throws Exception {
        return (Vec3) BLAST.getMethod("tick", Ship.class).invoke(blast, ship);
    }
    private static Class<?> load() {
        String original = "com/liucodex/doggiesnewvehicle/boss/MothershipTerrainBlast";
        String generated = "com/liucodex/doggiesnewvehicle/boss/terrainprobe/ExecutedTerrainBlast";
        Map<String, String> names = new HashMap<>();
        names.put(original, generated);
        names.put("com/liucodex/doggiesnewvehicle/compat/DnvBlockDamagePolicy", Type.getInternalName(ForgeEvents.class));
        names.put("net/minecraft/world/entity/Entity", Type.getInternalName(Ship.class));
        names.put("net/minecraft/world/level/Level", Type.getInternalName(World.class));
        names.put("net/minecraft/world/level/BlockGetter", Type.getInternalName(World.class));
        names.put("net/minecraft/server/level/ServerLevel", Type.getInternalName(World.class));
        names.put("net/minecraft/world/level/Explosion", Type.getInternalName(NativeExplosion.class));
        names.put("net/minecraft/world/level/Explosion$BlockInteraction", Type.getInternalName(BlockInteraction.class));
        names.put("net/minecraft/core/BlockPos", Type.getInternalName(Pos.class));
        names.put("net/minecraft/nbt/CompoundTag", Type.getInternalName(Tag.class));
        names.put("net/minecraft/world/level/block/state/BlockState", Type.getInternalName(State.class));
        names.put("net/minecraft/world/level/border/WorldBorder", Type.getInternalName(Border.class));
        names.put("net/minecraftforge/event/ForgeEventFactory", Type.getInternalName(ForgeEvents.class));
        try (InputStream input = MothershipTerrainBlastExecutionTest.class.getResourceAsStream('/' + original + ".class")) {
            assertNotNull(input);
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(input).accept(new ClassRemapper(writer, new SimpleRemapper(names)), 0);
            byte[] bytes = writer.toByteArray();
            return new ClassLoader(MothershipTerrainBlastExecutionTest.class.getClassLoader()) {
                Class<?> define() { return defineClass(generated.replace('/', '.'), bytes, 0, bytes.length); }
            }.define();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    public static final class Ship {
        final World world;
        Ship(World world) { this.world = world; }
        public World level() { return world; }
    }
    public record Pos(int x, int y, int z) {
        public static Pos containing(net.minecraft.core.Position point) {
            return new Pos((int)Math.floor(point.x()), (int)Math.floor(point.y()), (int)Math.floor(point.z()));
        }
        public Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
        public int getX() { return x; } public int getY() { return y; } public int getZ() { return z; }
        public Vec3 getCenter() { return new Vec3(x + .5, y + .5, z + .5); }
    }
    public static final class Tag {
        final Map<String, Number> values = new HashMap<>();
        public void putInt(String key, int value) { values.put(key, value); }
        public int getInt(String key) { return values.getOrDefault(key, 0).intValue(); }
        public void putDouble(String key, double value) { values.put(key, value); }
        public double getDouble(String key) { return values.getOrDefault(key, 0).doubleValue(); }
        public void putBoolean(String key, boolean value) { values.put(key, value ? 1 : 0); }
        public boolean getBoolean(String key) { return values.getOrDefault(key, 0).intValue() != 0; }
        public boolean contains(String key, int type) { return values.containsKey(key); }
    }
    public static final class State {
        public boolean isAir() { return false; }
        public float getDestroySpeed(World world, Pos pos) { return 1; }
        public float getExplosionResistance(World world, Pos pos, NativeExplosion explosion) { return 1; }
    }
    public static final class Border { public boolean isWithinBounds(Pos pos) { return true; } }
    public static final class World {
        long time; boolean mobGriefing = true, explosions = true, cancelStart, loaded = true, protectCenter;
        int nativeDropCallbacks, entityDamage;
        final List<Set<Pos>> batches = new ArrayList<>();
        public long getGameTime() { return time; }
        public int getMinBuildHeight() { return -64; } public int getMaxBuildHeight() { return 320; }
        public Border getWorldBorder() { return new Border(); }
        public boolean hasChunk(int x, int z) { return loaded; }
        public State getBlockState(Pos pos) { return new State(); }
    }
    public enum BlockInteraction { DESTROY_WITH_DECAY }
    public static final class NativeExplosion {
        final World world; final List<Pos> toBlow = new ArrayList<>();
        public NativeExplosion(World world, Ship ship, double x, double y, double z, float radius,
                boolean fire, BlockInteraction interaction) {
            this.world = world;
            assertEquals(16, radius); assertFalse(fire); assertEquals(BlockInteraction.DESTROY_WITH_DECAY, interaction);
        }
        public List<Pos> getToBlow() { return toBlow; }
        public void finalizeExplosion(boolean particles) {
            assertFalse(particles); world.batches.add(Set.copyOf(toBlow)); world.nativeDropCallbacks += toBlow.size();
        }
    }
    public static final class ForgeEvents {
        public static boolean allowed(World world,Ship ship){return world.explosions && world.mobGriefing;}
        public static boolean getMobGriefingEvent(World world, Ship ship) { return world.mobGriefing; }
        public static boolean onExplosionStart(World world, NativeExplosion explosion) { return world.cancelStart; }
        public static void onExplosionDetonate(World world, NativeExplosion explosion, List<Ship> entities, double diameter) {
            assertEquals(32, diameter);
            assertTrue(entities.isEmpty());
            if (world.protectCenter) explosion.toBlow.remove(new Pos(0, 64, 0));
            explosion.toBlow.add(new Pos(100, 64, 100));
        }
    }
}
