package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes pinned SBW use/useOn/place and compiled DNV item use. World admission and UI services are ports. */
class DnvSurvivalItemExecutionTest {
    @Test void allSixAssembledVehicleBoxesReachNativePlacementInSurvival() throws Exception {
        var f = new Fixture();
        String recipeRoot = getClass().getResource("/data/doggiesnewvehicle/recipe/changfeng_vehicle_assembling.json") != null
                ? "/data/doggiesnewvehicle/recipe/" : "/data/doggiesnewvehicle/recipes/";
        for (String recipe : List.of("changfeng", "storm_turret", "jingzhe_turret", "m1_yunlong", "apocalypse", "blizzard")) {
            try (var in = getClass().getResourceAsStream(recipeRoot + recipe + "_vehicle_assembling.json")) {
                assertNotNull(in, recipe);
                var result = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("result");
                assertFalse(result.has("item"), "An item deployer must be tested through its own entry");
                var player = new Actor();
                player.held.data.putString("EntityType", result.get("entity").getAsString());
                var original = player.held.data.copy();
                assertEquals(InteractionResult.PASS, f.box.getClass().getMethod("useOn", Click.class)
                        .invoke(f.box, new Click(player, Hand.MAIN, player.world.hit)));
                assertEquals(0, player.world.writes);
                var used = f.use(f.box, player, Hand.MAIN);
                assertEquals(InteractionResult.CONSUME, used.result());
                assertSame(player.held, used.object());
                assertEquals(0, player.held.count);
                assertEquals(1, player.world.writes);
                assertEquals(BlockPos.ZERO.above(), player.world.placedAt);
                assertEquals(original, player.world.placedData);
                assertEquals(original, player.held.data);
            }
        }
    }

    @Test void rayMissAndDeniedWorldAdmissionNeverConsumeTheSurvivalBox() throws Exception {
        var f = new Fixture();
        var miss = new Actor(); miss.world.hit = new Hit(HitType.MISS, BlockPos.ZERO);
        assertEquals(InteractionResult.PASS, f.use(f.box, miss, Hand.MAIN).result());
        assertEquals(1, miss.held.count); assertEquals(0, miss.world.writes);
        var denied = new Actor(); denied.world.allowed = false;
        denied.held.data.putString("EntityType", "doggievehicle:m1_yunlong");
        var original = denied.held.data.copy();
        assertEquals(InteractionResult.FAIL, f.use(f.box, denied, Hand.OFF).result());
        assertEquals(1, denied.held.count); assertEquals(0, denied.world.writes);
        assertEquals(original, denied.held.data);
    }

    @Test void creativeNativePlacementKeepsTheBoxAndStillWritesOnlyOnce() throws Exception {
        var f = new Fixture(); var player = new Actor(); player.creative = true;
        assertEquals(InteractionResult.CONSUME, f.use(f.box, player, Hand.OFF).result());
        assertEquals(1, player.held.count); assertEquals(1, player.world.writes);
    }

    @Test void ordinaryServerPlayersCanOpenBothPortableToolsWithoutSpendingTheirItem() throws Exception {
        var f = new Fixture();
        for (String name : List.of("DnvVehicleCrewCoreItem", "DnvVehicleOperationsItem")) {
            for (Hand hand : Hand.values()) {
                var player = new ServerActor();
                assertEquals(InteractionResult.CONSUME, f.use(f.item(name), player, hand).result());
                assertEquals(1, player.opens); assertEquals(1, player.held.count);
                assertEquals(name.equals("DnvVehicleCrewCoreItem") ? hand : null, player.configHand);
            }
        }
    }

    @Test void portableToolClientPredictionDoesNotOpenAServerSessionOrConsumeItems() throws Exception {
        var f = new Fixture();
        for (String name : List.of("DnvVehicleCrewCoreItem", "DnvVehicleOperationsItem")) {
            var player = new Actor(); player.world.isClientSide = true;
            assertEquals(InteractionResult.SUCCESS, f.use(f.item(name), player, Hand.MAIN).result());
            assertEquals(0, player.opens); assertEquals(1, player.held.count);
        }
    }

    static final class Fixture {
        final Map<String, String> names = new HashMap<>();
        final Object box;
        Fixture() throws Exception {
            map("world/item/Item", ItemEdge.class); map("world/item/BlockItem", ItemEdge.class);
            map("world/item/ItemStack", Stack.class); map("world/item/context/UseOnContext", Click.class);
            map("world/item/context/BlockPlaceContext", Place.class); map("world/entity/player/Player", Actor.class);
            map("server/level/ServerPlayer", ServerActor.class); map("world/entity/player/Inventory", Inventory.class);
            map("world/level/Level", World.class); map("world/InteractionHand", Hand.class);
            map("world/InteractionResultHolder", HeldResult.class); map("world/phys/BlockHitResult", Hit.class);
            map("world/phys/HitResult$Type", HitType.class); map("world/level/ClipContext$Fluid", Fluid.class);
            map("core/component/DataComponents", Components.class); map("core/component/DataComponentType", Key.class);
            map("world/item/component/CustomData", Data.class);
            names.put("com/liucodex/doggiesnewvehicle/compat/DnvVehicleCrewServices", Type.getInternalName(Services.class));
            names.put("com/liucodex/doggiesnewvehicle/compat/DnvManagedVehicleNetwork", Type.getInternalName(Services.class));
            String nativeName = NuclearPlacementExecutionTest.NATIVE;
            try (var zip = new ZipFile(Path.of(System.getProperty("dnv.sbw.jar")).toFile())) {
                var node = NuclearNativeBoundaryTest.node(zip.getInputStream(zip.getEntry(nativeName + ".class")).readAllBytes());
                box = load(node, Set.of("m_7203_", "m_6225_", "m_40576_", "use", "useOn", "place"));
            }
        }
        void map(String from, Class<?> to) { names.put("net/minecraft/" + from, Type.getInternalName(to)); }
        Object item(String name) throws Exception {
            try (var in = getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/item/" + name + ".class")) {
                return load(NuclearNativeBoundaryTest.node(in.readAllBytes()), Set.of("use"));
            }
        }
        Object load(ClassNode node, Set<String> keep) throws Exception {
            String target = "com/liucodex/doggiesnewvehicle/compat/survivalprobe/" + node.name.substring(node.name.lastIndexOf('/') + 1);
            names.put(node.name, target);
            NuclearPlacementExecutionTest.prepare(node, keep, ItemEdge.class);
            var aliases = Map.ofEntries(Map.entry("m_7203_", "use"), Map.entry("m_6225_", "useOn"),
                    Map.entry("m_40576_", "place"), Map.entry("m_41435_", "getPlayerPOVHitResult"),
                    Map.entry("m_6662_", "getType"), Map.entry("m_82425_", "getBlockPos"),
                    Map.entry("m_7494_", "above"), Map.entry("m_82430_", "withPosition"),
                    Map.entry("m_21120_", "getItemInHand"), Map.entry("m_43722_", "getItemInHand"),
                    Map.entry("m_43723_", "getPlayer"), Map.entry("m_186336_", "getBlockEntityData"),
                    Map.entry("m_128423_", "get"), Map.entry("m_9236_", "level"),
                    Map.entry("m_150109_", "getInventory"), Map.entry("m_36057_", "removeItem"));
            var out = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(out, new Remapper() {
                @Override public String map(String key) { return names.getOrDefault(key, key); }
                @Override public String mapMethodName(String owner, String name, String desc) { return aliases.getOrDefault(name, name); }
                @Override public String mapFieldName(String owner, String name, String desc) { return name.equals("f_46443_") ? "isClientSide" : name; }
            }));
            return new ExactQueuedShotFixture.Loader(Map.of(target, out.toByteArray()))
                    .loadClass(target.replace('/', '.')).getConstructor().newInstance();
        }
        HeldResult use(Object item, Actor player, Hand hand) throws Exception {
            return (HeldResult) item.getClass().getMethod("use", World.class, Actor.class, Hand.class)
                    .invoke(item, player.world, player, hand);
        }
    }

    public enum Hand { MAIN, OFF }
    public enum Fluid { ANY }
    public enum HitType { MISS, BLOCK }
    public record Hit(HitType type, BlockPos pos) {
        public HitType getType() { return type; } public BlockPos getBlockPos() { return pos; }
        public Hit withPosition(BlockPos next) { return new Hit(type, next); }
    }
    public record HeldResult(InteractionResult result, Object object) {
        public static HeldResult sidedSuccess(Object object, boolean client) {
            return new HeldResult(client ? InteractionResult.SUCCESS : InteractionResult.CONSUME, object);
        }
    }
    public static class ItemEdge {
        public HeldResult use(World world, Actor player, Hand hand) { return new HeldResult(InteractionResult.PASS, player.held); }
        public static Hit getPlayerPOVHitResult(World world, Actor player, Fluid fluid) { return world.hit; }
        public InteractionResult useOn(Click click) { return place(new Place(click)); }
        public InteractionResult place(Place click) {
            var world = click.player.world;
            if (!world.allowed) return InteractionResult.FAIL;
            world.writes++; world.placedAt = click.hit.pos; world.placedData = click.player.held.data.copy();
            if (!click.player.creative) click.player.held.count--;
            return InteractionResult.CONSUME;
        }
        public static CompoundTag getBlockEntityData(Stack stack) { return stack.data; }
    }
    public static class Click {
        public final Actor player; public final Hand hand; public final Hit hit;
        public Click(Actor player, Hand hand, Hit hit) { this.player = player; this.hand = hand; this.hit = hit; }
        public Actor getPlayer() { return player; } public Stack getItemInHand() { return player.held; }
    }
    public static class Place extends Click { public Place(Click c) { super(c.player, c.hand, c.hit); } }
    public static class Actor {
        public World world = new World(); public Stack held = new Stack(); public boolean creative;
        public int opens; public Hand configHand;
        public Stack getItemInHand(Hand hand) { return held; } public World level() { return world; }
        public Inventory getInventory() { return new Inventory(); }
    }
    public static class ServerActor extends Actor { }
    public static class Inventory { public void removeItem(Stack stack) { fail("Fresh boxes must not use saved-entity removal"); } }
    public static class World {
        public boolean isClientSide, allowed = true; public Hit hit = new Hit(HitType.BLOCK, BlockPos.ZERO);
        public int writes; public BlockPos placedAt; public CompoundTag placedData;
        public boolean isClientSide() { return isClientSide; }
    }
    public static class Stack { public int count = 1; public CompoundTag data = new CompoundTag();
        public Object get(Key key) { return new Data(data); }
        public Object getOrDefault(Key key, Object fallback) { return new Data(data); }
    }
    public static class Key { }
    public static class Components { public static final Key BLOCK_ENTITY_DATA = new Key(); }
    public record Data(CompoundTag tag) { public static final Data EMPTY = new Data(new CompoundTag()); public CompoundTag copyTag() { return tag.copy(); } }
    public static class Services {
        public static void open(ServerActor player) { player.opens++; }
        public static void openCoreConfiguration(ServerActor player, Hand hand) { player.opens++; player.configHand = hand; }
    }
}
