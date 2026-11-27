package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Runs compiled impact logic; block storage and the native explosion sink are explicit ports. */
class BlizzardImpactExecutionTest {
    @Test void impactClearsOnlyLoadedInBoundsLiquidsAndDispatchesOneConfiguredNativeBlast() throws Exception {
        var missile = probe(); var world = new ServerWorld(); missile.world = world;
        BlockPos center = BlockPos.containing(missile.position());
        State water = new State(new Liquid(), true, false, false);
        State logged = new State(new Block(), true, true, false);
        State plant = new State(new Block(), true, false, true);
        State solid = new State(new Block(), false, false, false);
        State unknownFluid = new State(new Block(), true, false, false);
        var pickup = new Pickup(); State bucket = new State(pickup, true, false, false);
        world.cells.put(center, water);
        world.cells.put(center.offset(1, 0, 0), logged);
        world.cells.put(center.offset(0, 1, 0), plant);
        world.cells.put(center.offset(0, 0, 1), bucket);
        world.cells.put(center.offset(-1, 0, 0), solid);
        world.cells.put(center.offset(0, -1, 0), unknownFluid);
        for (int i = 2; i <= 5; i++) world.cells.put(center.offset(i, 0, 0), water);
        State flowing = new State(new Liquid(), true, false, false, false);
        State carrier = new State(new Carrier(), true, false, false, false);
        world.cells.put(center.offset(6, 0, 0), flowing);
        world.cells.put(center.offset(10, 0, 0), carrier);
        world.cells.put(center.offset(11, 0, 0), flowing);
        world.cells.put(center.offset(10, 1, 0), flowing);
        world.unloaded.add(center.offset(2, 0, 0));
        world.outsideHeight.add(center.offset(3, 0, 0));
        world.border.outside.add(center.offset(4, 0, 0));
        impact(missile);
        assertEquals(1, missile.blasts); assertEquals(50, missile.damage); assertEquals(8, missile.radius);
        assertFalse(missile.destroy); assertEquals(missile.position(), missile.blastPosition);
        assertTrue(missile.removed);
        assertSame(Blocks.AIR.defaultBlockState(), world.cells.get(center));
        assertSame(logged.block, world.cells.get(center.offset(1, 0, 0)).block);
        assertFalse(world.cells.get(center.offset(1, 0, 0)).wet);
        assertSame(Blocks.AIR.defaultBlockState(), world.cells.get(center.offset(0, 1, 0)));
        assertEquals(1, pickup.calls);
        assertSame(solid, world.cells.get(center.offset(-1, 0, 0)));
        assertSame(unknownFluid, world.cells.get(center.offset(0, -1, 0)));
        for (int i = 2; i <= 4; i++) {
            BlockPos pos = center.offset(i, 0, 0);
            assertSame(water, world.cells.get(pos)); assertFalse(world.reads.contains(pos));
        }
        for(int x:new int[]{5,6,10})assertSame(Blocks.AIR.defaultBlockState(),world.cells.get(center.offset(x,0,0)));
        assertSame(flowing,world.cells.get(center.offset(11,0,0)));
        assertSame(flowing,world.cells.get(center.offset(10,1,0)));
        assertFalse(flowing.getFluidState().isSource());assertTrue(water.getFluidState().isSource());
        assertEquals(6, world.writes.size());assertEquals(1,world.scentCalls);
        assertTrue(world.reads.stream().allMatch(pos -> pos.distSqr(center) <= 100));
        impact(missile);
        assertEquals(1, missile.blasts);
        assertEquals(6, world.writes.size()); assertEquals(1, pickup.calls);assertEquals(1,world.scentCalls);
    }
    @Test void canceledTerrainPhaseAlsoVetoesFluidAndScentAndCannotReplayImpact() throws Exception {
        var missile=probe();var world=new ServerWorld();missile.world=world;world.allowed=false;
        impact(missile);impact(missile);
        assertEquals(1,missile.blasts);assertTrue(world.reads.isEmpty());assertEquals(0,world.scentCalls);
        assertTrue(missile.removed);
    }
    @Test void clientImpactHasNoExplosionBlockWritesOrRelease() throws Exception {
        var missile = probe(); missile.world = new World(); impact(missile);
        assertEquals(0, missile.blasts); assertFalse(missile.removed);
    }
    @Test void unloadedBlastFootprintSkipsNativeAreaEffectsAndStillEndsFlight() throws Exception {
        var missile = probe();
        var world = new ServerWorld();
        world.blastChunksReady = false;
        missile.world = world;
        impact(missile);
        impact(missile);
        assertEquals(0, missile.blasts);
        assertTrue(missile.removed);
    }
    private static void impact(Missile missile) throws Exception { missile.getClass().getMethod("impact").invoke(missile); }
    private Missile probe() throws Exception {
        String source = "com/liucodex/doggiesnewvehicle/armored/DnvBlizzardMissile";
        String base = "com/atsuishio/superbwarfare/entity/projectile/JavelinMissileEntity";
        String target = "com/liucodex/doggiesnewvehicle/compat/executionprobe/BlizzardImpact";
        Map<String, String> names = new HashMap<>(); names.put(source, target); names.put(base, Type.getInternalName(Missile.class));
        names.put("net/minecraft/world/entity/Entity", Type.getInternalName(Missile.class));
        String terrainSource="com/liucodex/doggiesnewvehicle/armored/BlizzardTerrainBlast",terrainTarget=target+"Terrain";
        names.put(terrainSource,terrainTarget);
        names.put("com/liucodex/doggiesnewvehicle/compat/DnvBlockDamagePolicy",Type.getInternalName(Terrain.class));
        names.put("com/liucodex/doggiesnewvehicle/api/combat/DnvProjectilePath",Type.getInternalName(Terrain.class));
        names.put("net/minecraft/world/level/Explosion",Type.getInternalName(Blast.class));
        names.put("net/minecraftforge/event/ForgeEventFactory",Type.getInternalName(Terrain.class));
        names.put("net/neoforged/neoforge/event/EventHooks",Type.getInternalName(Terrain.class));
        names.put("com/liucodex/doggiesnewvehicle/artillery/compat/DnvSporeScent",Type.getInternalName(Scent.class));
        Map.ofEntries(Map.entry("world/level/Level", World.class), Map.entry("server/level/ServerLevel", ServerWorld.class),
                Map.entry("world/level/LevelAccessor", Accessor.class), Map.entry("world/level/border/WorldBorder", Border.class),
                Map.entry("world/level/block/state/BlockState", State.class), Map.entry("world/level/material/FluidState", Fluid.class),
                Map.entry("world/level/block/Block", Block.class), Map.entry("world/level/block/LiquidBlock", Liquid.class),
                Map.entry("world/level/block/Blocks", Blocks.class), Map.entry("world/level/block/BucketPickup", Bucket.class),
                Map.entry("world/level/block/state/properties/Property", Property.class),
                Map.entry("world/level/block/state/properties/BooleanProperty", Property.class),
                Map.entry("world/level/block/state/properties/BlockStateProperties", Properties.class),
                Map.entry("world/item/ItemStack", Stack.class), Map.entry("world/entity/player/Player", Actor.class))
                .forEach((name, type) -> names.put("net/minecraft/" + name, Type.getInternalName(type)));
        ClassNode node = new ClassNode();
        try (var in = getClass().getResourceAsStream("/" + source + ".class")) {
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
        }
        node.methods.removeIf(m -> !m.name.equals("impact")); node.fields.removeIf(f -> !f.name.equals("impacted"));
        node.innerClasses.clear();
        node.methods.forEach(m -> m.access = Opcodes.ACC_PUBLIC);
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, base, "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(constructor);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
        };
        node.accept(new ClassRemapper(writer, new Remapper() {
            @Override public String map(String name) { return names.getOrDefault(name, name); }
        }));
        var helper=new ClassNode();try(var in=getClass().getResourceAsStream("/"+terrainSource+".class")){
            new ClassReader(in).accept(helper,ClassReader.SKIP_FRAMES);
        }
        helper.access=Opcodes.ACC_PUBLIC;helper.methods.removeIf(m->!Set.of("clearLiquids","liquidSite").contains(m.name));
        helper.methods.forEach(m->m.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC);
        var helperWriter=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        helper.accept(new ClassRemapper(helperWriter,new SimpleRemapper(names)));
        var mapped=new ClassNode();new ClassReader(helperWriter.toByteArray()).accept(mapped,0);
        String descriptor="(L"+Type.getInternalName(ServerWorld.class)+";L"+Type.getInternalName(Missile.class)+";Lnet/minecraft/world/phys/Vec3;)Z";
        var admission=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"apply",descriptor,null,null);
        for(int index=0;index<3;index++)admission.instructions.add(new VarInsnNode(Opcodes.ALOAD,index));
        admission.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,Type.getInternalName(Terrain.class),"apply",descriptor,false));
        admission.instructions.add(new InsnNode(Opcodes.IRETURN));admission.maxLocals=3;admission.maxStack=3;mapped.methods.add(admission);
        helperWriter=new ClassWriter(0);mapped.accept(helperWriter);
        return (Missile) new ExactQueuedShotFixture.Loader(Map.of(target, writer.toByteArray(),terrainTarget,helperWriter.toByteArray()))
                .loadClass(target.replace('/', '.')).getConstructor().newInstance();
    }
    public static class Terrain { public static boolean apply(ServerWorld level, Missile source, Vec3 point) { return level.allowed; }
        public static boolean blastLoaded(ServerWorld level, Vec3 center, float radius) {
            assertEquals(8, radius);
            return level.blastChunksReady;
        }
        public static boolean allowed(World level,Missile source){return ((ServerWorld)level).grief;}
        public static void onExplosionDetonate(World level,Blast blast,List<Missile> targets,double radius){assertEquals(10,radius);}}
    public static class Blast {public final List<BlockPos> positions=new ArrayList<>();
        public Blast(World world,Missile source,double x,double y,double z,float radius,boolean fire,
                net.minecraft.world.level.Explosion.BlockInteraction interaction){assertEquals(10,radius);}
        public List<BlockPos> getToBlow(){return positions;}}
    public static class Scent { public static void clear(ServerWorld level,Missile source,Vec3 point,int radius) {
        assertEquals(6,radius);assertEquals(source.position(),point);level.scentCalls++;
    } }
    public static class Missile {
        public World world; public int blasts; public float damage, radius; public boolean destroy = true, removed;
        public Vec3 blastPosition; public World level() { return world; } public Vec3 position() { return new Vec3(6, 40, 9); }
        public void releaseControl() { }
        public void discard() { removed = true; }
        public void setExplosionDamage(float value) { damage = value; } public void setExplosionRadius(float value) { radius = value; }
        public float getExplosionRadius() { return radius; }
        public void setExplosionDestroy(boolean value) { destroy = value; }
        public void causeExplode(Vec3 position) { blasts++; blastPosition = position; }
    }
    public interface Accessor {}
    public static class World {}
    public static class ServerWorld extends World implements Accessor {
        public boolean allowed=true,grief=true,blastChunksReady=true;public int scentCalls;
        public final Map<BlockPos, State> cells = new HashMap<>(); public final Set<BlockPos> reads = new HashSet<>();
        public final Set<BlockPos> unloaded = new HashSet<>(), outsideHeight = new HashSet<>();
        public final List<BlockPos> writes = new ArrayList<>(); public final Border border = new Border();
        public boolean hasChunkAt(BlockPos pos) { return !unloaded.contains(pos); }
        public boolean isOutsideBuildHeight(BlockPos pos) { return outsideHeight.contains(pos); }
        public Border getWorldBorder() { return border; }
        public State getBlockState(BlockPos pos) { reads.add(pos); return cells.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        public boolean setBlock(BlockPos pos, State state, int flags) { assertEquals(3, flags); writes.add(pos); cells.put(pos, state); return true; }
    }
    public static class Border { public final Set<BlockPos> outside = new HashSet<>(); public boolean isWithinBounds(BlockPos pos) { return !outside.contains(pos); } }
    public static class Property {}
    public static class Properties { public static final Property WATERLOGGED = new Property(); }
    public static class Fluid {
        private final State state;public Fluid(State state){this.state=state;}
        public boolean isEmpty(){return !state.wet;}public boolean isSource(){return state.source;}
        public State createLegacyBlock(){return state.block instanceof Liquid||state.block instanceof Carrier?state:Blocks.AIR.defaultBlockState();}
    }
    public static class State {
        public final Block block; public final boolean wet, waterlogged, replaceable,source;
        public State(Block block, boolean wet, boolean waterlogged, boolean replaceable) {
            this(block,wet,waterlogged,replaceable,true);
        }
        public State(Block block,boolean wet,boolean waterlogged,boolean replaceable,boolean source){
            this.block=block;this.wet=wet;this.waterlogged=waterlogged;this.replaceable=replaceable;this.source=source;
        }
        public Fluid getFluidState() { return new Fluid(this); } public Block getBlock() { return block; }
        public boolean hasProperty(Property property) { return waterlogged; } public boolean canBeReplaced() { return replaceable; }
        public Object setValue(Property property, Comparable<?> value) { assertSame(Properties.WATERLOGGED, property); assertEquals(false, value); return new State(block, false, true, replaceable); }
    }
    public static class Block { private final State state = new State(this, false, false, false); public State defaultBlockState() { return state; } }
    public static class Liquid extends Block {}
    public static class Carrier extends Block {}
    public static class Blocks { public static final Block AIR = new Block(); }
    public static class Stack {}
    public static class Actor {}
    public interface Bucket {
        Stack pickupBlock(Accessor level, BlockPos pos, State state);
        Stack pickupBlock(Actor actor, Accessor level, BlockPos pos, State state);
    }
    public static class Pickup extends Block implements Bucket {
        public int calls;
        public Stack pickupBlock(Accessor level, BlockPos pos, State state) { calls++; return new Stack(); }
        public Stack pickupBlock(Actor actor, Accessor level, BlockPos pos, State state) { assertNull(actor); return pickupBlock(level, pos, state); }
    }
}
