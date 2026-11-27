package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes production block selection and protection callbacks around explicit block/explosion ports. */
class BlizzardTerrainExecutionTest {
    @Test void exactSphereBreaksWetSolidsButNeverUnloadedUnbreakableOrOutsideBlocks() throws Exception {
        var world=new World(); Vec3 point=new Vec3(.5,40.5,.5); BlockPos center=BlockPos.containing(point);
        for(int x=-5;x<=5;x++) for(int y=-5;y<=5;y++) for(int z=-5;z<=5;z++)
            world.cells.put(center.offset(x,y,z),new State(false,false,1));
        BlockPos wet=center.offset(1,0,0); world.cells.put(wet,new State(false,false,1));
        BlockPos bedrock=center.offset(-1,0,0); world.cells.put(bedrock,new State(false,false,-1));
        BlockPos liquid=center.offset(0,1,0); world.cells.put(liquid,new State(false,true,100));
        BlockPos unloaded=center.offset(0,-1,0);world.unloaded.add(unloaded);
        BlockPos high=center.offset(0,0,1);world.high.add(high);
        BlockPos border=center.offset(0,0,-1);world.border.outside.add(border);
        assertTrue(apply(world,point)); assertEquals(1,world.finalized);
        assertTrue(world.broken.contains(wet)); assertTrue(world.broken.contains(center.offset(4,0,0)));
        for(var excluded:List.of(bedrock,liquid,unloaded,high,border,center.offset(4,1,0))) assertFalse(world.broken.contains(excluded));
        assertTrue(world.reads.stream().allMatch(p->Vec3.atCenterOf(p).distanceToSqr(point)<=16));
        for(var p:List.of(unloaded,high,border))assertFalse(world.reads.contains(p));
    }
    @Test void cancellationAndClaimRemovalAreRespectedAndHookCannotExpandBlast() throws Exception {
        var world=new World();var center=new BlockPos(0,40,0);world.cells.put(center,new State(false,false,1));
        world.cancel=true; assertFalse(apply(world,Vec3.atCenterOf(center)));
        assertEquals(0,world.finalized); assertTrue(world.reads.isEmpty());
        world.cancel=false;world.protectedPos=center;world.inject=center.offset(20,0,0);
        assertTrue(apply(world,Vec3.atCenterOf(center)));assertEquals(1,world.finalized);assertTrue(world.broken.isEmpty());
        assertEquals(1,world.hooks);
    }
    @Test void hardRuleAndCallbackRuleChangesPreserveTerrainAndLiquidHosts()throws Exception {
        var world=new World();var center=new BlockPos(0,40,0);world.grief=false;
        world.cells.put(center,new State(false,false,1));assertTrue(apply(world,Vec3.atCenterOf(center)));
        assertTrue(world.reads.isEmpty());assertEquals(0,world.finalized);
        var clear=subject().getMethod("clearLiquids",World.class,Entity.class,Vec3.class);
        var water=new State(false,true,100);world.cells.put(center,water);
        clear.invoke(null,world,new Entity(),Vec3.atCenterOf(center));assertEquals(0,world.writes);
        world.grief=true;world.flipRule=true;
        clear.invoke(null,world,new Entity(),Vec3.atCenterOf(center));assertEquals(0,world.writes);
        world.flipRule=false;world.grief=true;world.protectedPos=center;
        clear.invoke(null,world,new Entity(),Vec3.atCenterOf(center));assertEquals(0,world.writes);
        world.protectedPos=null;var wet=center.offset(1,0,0);var host=new State(false,false,1);host.logged=true;host.wet=true;
        world.cells.put(wet,host);var outside=center.offset(11,0,0);world.cells.put(outside,water);world.inject=outside;
        clear.invoke(null,world,new Entity(),Vec3.atCenterOf(center));
        assertEquals(2,world.writes);assertTrue(world.cells.get(center).air);assertSame(host,world.cells.get(wet));
        assertFalse(host.wet);assertSame(water,world.cells.get(outside));
    }
    private boolean apply(World world,Vec3 point)throws Exception {
        return (boolean)subject().getMethod("apply",World.class,Entity.class,Vec3.class).invoke(null,world,new Entity(),point);
    }
    private Class<?> subject()throws Exception {
        String source="com/liucodex/doggiesnewvehicle/armored/BlizzardTerrainBlast",target="com/liucodex/doggiesnewvehicle/compat/executionprobe/Terrain";
        Map<String,String> names=new HashMap<>();names.put(source,target);
        Map.ofEntries(Map.entry("server/level/ServerLevel",World.class),Map.entry("world/level/Level",World.class),
            Map.entry("world/level/BlockGetter",World.class),Map.entry("world/entity/Entity",Entity.class),
            Map.entry("world/level/block/state/BlockState",State.class),Map.entry("world/level/block/Block",Block.class),
            Map.entry("world/level/block/LiquidBlock",Liquid.class),Map.entry("world/level/Explosion",Blast.class),
            Map.entry("world/level/border/WorldBorder",Border.class)).forEach((name,type)->names.put("net/minecraft/"+name,Type.getInternalName(type)));
        names.put("net/minecraftforge/event/ForgeEventFactory",Type.getInternalName(Hooks.class));
        names.put("net/neoforged/neoforge/event/EventHooks",Type.getInternalName(Hooks.class));
        names.put("com/liucodex/doggiesnewvehicle/compat/DnvBlockDamagePolicy",Type.getInternalName(Hooks.class));
        names.put("net/minecraft/world/item/ItemStack","java/lang/Object");
        names.put("net/minecraft/world/entity/player/Player",Type.getInternalName(Entity.class));
        for(var entry:Map.of("world/level/block/state/properties/BlockStateProperties",Properties.class,
                "world/level/block/state/properties/Property",Property.class,"world/level/block/state/properties/BooleanProperty",Property.class,
                "world/level/block/Blocks",Catalog.class,"world/level/material/FluidState",Fluid.class,
                "world/level/block/BucketPickup",Pickup.class).entrySet())names.put("net/minecraft/"+entry.getKey(),Type.getInternalName(entry.getValue()));
        ClassNode node=new ClassNode();try(var in=getClass().getResourceAsStream("/"+source+".class")){new ClassReader(in.readAllBytes()).accept(node,ClassReader.SKIP_FRAMES);}
        node.access=Opcodes.ACC_PUBLIC;node.methods.forEach(m->m.access=(m.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer,new Remapper(){@Override public String map(String key){return names.getOrDefault(key,key);}}));
        var kind=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
        return kind;
    }
    public static class Entity {}
    public static class Block {public State defaultBlockState(){return new State(true,false,0);}}
    public static class Liquid extends Block {}
    public static class Catalog {public static final Block AIR=new Block();}
    public interface Pickup {Object pickupBlock(World world,BlockPos pos,State state);
        default Object pickupBlock(Entity actor,World world,BlockPos pos,State state){return pickupBlock(world,pos,state);}}
    public static class Property { }
    public static class Properties {public static final Property WATERLOGGED=new Property();}
    public record Fluid(State state){public boolean isEmpty(){return !state.wet;}public State createLegacyBlock(){return new State(false,true,100);}}
    public static class State {
        final boolean air; final float hardness;final Block block;boolean logged,wet;
        State(boolean air,boolean liquid,float hardness){this.air=air;this.hardness=hardness;block=liquid?new Liquid():new Block();wet=liquid;}
        public boolean isAir(){return air;}public float getDestroySpeed(World w,BlockPos p){return hardness;}public Block getBlock(){return block;}
        public Fluid getFluidState(){return new Fluid(this);}public boolean hasProperty(Property p){return logged;}
        public Object setValue(Property p,Comparable<?> value){wet=(Boolean)value;return this;}public boolean canBeReplaced(){return air;}
    }
    public static class Border {final Set<BlockPos> outside=new HashSet<>();public boolean isWithinBounds(BlockPos p){return !outside.contains(p);}}
    public static class World {
        final Map<BlockPos,State> cells=new HashMap<>();final Set<BlockPos> reads=new HashSet<>(),unloaded=new HashSet<>(),high=new HashSet<>();
        final List<BlockPos> broken=new ArrayList<>();final Border border=new Border();int finalized,hooks,writes;boolean cancel,grief=true,flipRule;BlockPos protectedPos,inject;
        public boolean hasChunkAt(BlockPos p){return !unloaded.contains(p);}public boolean isOutsideBuildHeight(BlockPos p){return high.contains(p);}
        public Border getWorldBorder(){return border;}public State getBlockState(BlockPos p){reads.add(p);return cells.getOrDefault(p,new State(true,false,0));}
        public boolean setBlock(BlockPos p,State state,int flags){writes++;cells.put(p,state);return true;}
    }
    public static class Blast {
        final World world;final List<BlockPos> blocks=new ArrayList<>();
        public Blast(World world,Entity source,double x,double y,double z,float radius,boolean fire,net.minecraft.world.level.Explosion.BlockInteraction interaction){
            this.world=world;assertTrue(radius==4||radius==10);assertFalse(fire);assertEquals(net.minecraft.world.level.Explosion.BlockInteraction.DESTROY,interaction);
        }
        public List<BlockPos> getToBlow(){return blocks;}
        public void explode(){fail("terrain phase must never apply a second entity blast");}
        public void finalizeExplosion(boolean particles){assertFalse(particles);world.finalized++;world.broken.addAll(blocks);}
    }
    public static class Hooks {
        public static boolean allowed(World world,Entity source){return world.grief;}
        public static boolean onExplosionStart(World world,Blast explosion){return world.cancel;}
        public static void onExplosionDetonate(World world,Blast explosion,List<Entity> entities,double radius){
            assertTrue(entities.isEmpty());assertTrue(radius==4||radius==10);world.hooks++;explosion.blocks.remove(world.protectedPos);
            if(world.flipRule)world.grief=false;
            if(world.inject!=null)explosion.blocks.add(world.inject);
        }
    }
}
