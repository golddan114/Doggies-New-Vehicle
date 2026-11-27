package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the exact SBW packet and parameter codec. Ray results, actor, registry and feedback
 * are explicit ports; this does not claim client key delivery, network sync or DNV Mixin execution. */
class NativeIndicatorMarkExecutionTest {
    private static final String SBW="com/atsuishio/superbwarfare/";
    private static final String CODEC=SBW+"item/misc/FiringParametersItemKt";
    private static final String PARAMS=SBW+"item/misc/FiringParametersItem$Parameters";
    private static final String PACKET=SBW+"network/message/send/SetFiringParametersMessage";
    private static final String PROBE="com/liucodex/doggiesnewvehicle/compat/markprobe/";

    @Test void absentCoordinatesReadAsZeroWithoutCreatingATargetTag() throws Exception {
        var f=new Fixture();var stack=f.actor.main;
        assertEquals(BlockPos.ZERO,f.pos(stack));assertNull(stack.tag);
        stack.tag=new CompoundTag();stack.tag.putString("Type","entity.doggiesnewvehicle.nuclear_silo");
        assertEquals(BlockPos.ZERO,f.pos(stack));assertFalse(stack.tag.contains("TargetX"));
        f.write(stack,BlockPos.ZERO,0,false);
        assertEquals(BlockPos.ZERO,f.pos(stack));assertTrue(stack.tag.contains("TargetX"));
    }

    @Test void mainHandMarkWritesRayCoordinatesBeforeTargetCallbackAndPreservesOtherParameters() throws Exception {
        var f=new Fixture();var expected=new BlockPos(-123,67,456);
        f.write(f.actor.main,BlockPos.ZERO,17,true);f.actor.world.hit=hit(expected);
        f.mark();assertEquals(expected,f.pos(f.actor.main));
        assertEquals(expected,f.actor.callbackTarget);assertEquals(1,f.actor.callbacks);
        assertEquals(17,f.actor.main.tag.getInt("Radius"));assertTrue(f.actor.main.tag.getBoolean("IsDepressed"));
        assertEquals(512,f.actor.world.ray.from.distanceTo(f.actor.world.ray.to),1e-7);
        assertEquals(1,f.actor.messages.size());assertNull(f.actor.off.tag);
    }

    @Test void entityCenterWinsUnlessThePlayerCrouches() throws Exception {
        var f=new Fixture();var ground=new BlockPos(50,67,70);
        f.actor.world.hit=hit(ground);f.actor.looking=new Entity();
        f.actor.looking.box=new AABB(100,70,200,102,74,202);
        f.mark();assertEquals(new BlockPos(101,72,201),f.pos(f.actor.main));
        f.actor.crouching=true;f.mark();assertEquals(ground,f.pos(f.actor.main));
        assertEquals(2,f.actor.callbacks);
    }

    @Test void nativeMissCurrentlyOverwritesAnExistingTargetWithTheRayEndpoint() throws Exception {
        var f=new Fixture();f.write(f.actor.main,new BlockPos(50,67,70),0,false);
        f.actor.view=new Vec3(0,1,0);var endpoint=new BlockPos(0,582,0);
        f.actor.world.hit=BlockHitResult.miss(new Vec3(0,582,0),Direction.DOWN,endpoint);
        assertEquals(HitResult.Type.MISS,f.actor.world.hit.getType());
        f.mark();assertEquals(endpoint,f.pos(f.actor.main));assertEquals(endpoint,f.actor.callbackTarget);
        assertEquals(new Vec3(0,582,0),f.actor.world.ray.to);
        assertEquals(1,f.actor.callbacks,"Observed native behavior, not a successful valid DNV target");
    }

    private static BlockHitResult hit(BlockPos p){return new BlockHitResult(p.getCenter(),Direction.UP,p,false);}

    private static final class Fixture {
        final Actor actor=new Actor();final Class<?> codec,params;final Object packet;
        Fixture() throws Exception {
            var names=new HashMap<String,String>();
            names.put(CODEC,PROBE+"Codec");names.put(PARAMS,PROBE+"Parameters");names.put(PACKET,PROBE+"Packet");
            map(names,"net/minecraft/server/level/ServerPlayer",Actor.class);
            map(names,"net/minecraft/world/entity/player/Player",Actor.class);
            map(names,"net/minecraft/world/entity/Entity",Entity.class);
            map(names,"net/minecraft/world/item/ItemStack",Stack.class);map(names,"net/minecraft/world/item/Item",Item.class);
            map(names,"net/minecraft/world/level/Level",World.class);map(names,"net/minecraft/world/level/ClipContext",Clip.class);
            map(names,"net/minecraft/world/level/ClipContext$Block",Clip.Block.class);
            map(names,"net/minecraft/world/level/ClipContext$Fluid",Clip.Fluid.class);
            map(names,"net/minecraftforge/registries/RegistryObject",Registry.class);
            map(names,"net/minecraft/sounds/SoundEvent",Sound.class);
            map(names,SBW+"network/ServerPacketPayload",Payload.class);
            map(names,SBW+"item/misc/ArtilleryIndicatorItem",Indicator.class);
            map(names,SBW+"init/ModItems",Items.class);map(names,SBW+"init/ModSounds",Sounds.class);
            map(names,SBW+"tools/TraceTool",Trace.class);map(names,SBW+"tools/SoundTool",SoundPort.class);
            // Resolve SRG calls with this build's actual ForgeGradle mappings, including inherited calls.
            var methods=new HashMap<String,String>();var fields=new HashMap<String,String>();
            for(String line:Files.readAllLines(Path.of("build/createSrgToMcp/output.srg"))){
                var parts=line.split(" ");
                if(parts[0].equals("MD:"))methods.put(simple(parts[1]),simple(parts[3]));
                if(parts[0].equals("FD:"))fields.put(simple(parts[1]),simple(parts[2]));
            }
            var definitions=new HashMap<String,byte[]>();
            try(var zip=new ZipFile(SbwTestDependency.path().toFile())){
                for(String name:List.of(CODEC,PARAMS,PACKET)){
                    var node=new ClassNode();new ClassReader(zip.getInputStream(zip.getEntry(name+".class"))).accept(node,ClassReader.SKIP_FRAMES);
                    node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;
                    var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
                    node.accept(new ClassRemapper(writer,new Remapper(){
                        @Override public String map(String key){return names.getOrDefault(key,key);}
                        @Override public String mapMethodName(String owner,String name,String desc){return methods.getOrDefault(name,name);}
                        @Override public String mapFieldName(String owner,String name,String desc){return fields.getOrDefault(name,name);}
                    }));definitions.put(names.get(name),writer.toByteArray());
                }
            }
            var loader=new ExactQueuedShotFixture.Loader(definitions);
            codec=loader.loadClass((PROBE+"Codec").replace('/','.'));params=loader.loadClass((PROBE+"Parameters").replace('/','.'));
            packet=loader.loadClass((PROBE+"Packet").replace('/','.')).getField("INSTANCE").get(null);
        }
        BlockPos pos(Stack stack) throws Exception {return (BlockPos)params.getMethod("pos").invoke(codec.getMethod("getFiringParameters",Stack.class).invoke(null,stack));}
        void write(Stack stack,BlockPos p,int radius,boolean depressed) throws Exception {
            codec.getMethod("setFiringParameters",Stack.class,params).invoke(null,stack,params.getConstructor(BlockPos.class,int.class,boolean.class).newInstance(p,radius,depressed));
        }
        void mark() throws Exception {packet.getClass().getMethod("handler",Supplier.class).invoke(packet,(Supplier<Actor>)()->actor);}
    }
    private static String simple(String name){return name.substring(name.lastIndexOf('/')+1);}
    private static void map(Map<String,String> names,String name,Class<?> target){names.put(name,Type.getInternalName(target));}
    public static class Payload {public Actor sender(Supplier<?> context){return (Actor)context.get();}}
    public static class Entity {
        public AABB box=new AABB(0,65,0,1,67,1);
        public AABB getBoundingBox(){return box;}public BlockPos blockPosition(){return BlockPos.containing(box.getCenter());}
    }
    public static class Actor extends Entity {
        public Stack main=new Stack((Item)Items.ARTILLERY_INDICATOR.get()),off=new Stack(new Item());
        public World world=new World();public Entity looking;public boolean crouching;public int callbacks;public Vec3 view=new Vec3(1,0,0);
        public BlockPos callbackTarget;public List<Component> messages=new ArrayList<>();
        public Stack getMainHandItem(){return main;}public Stack getOffhandItem(){return off;}
        public World level(){return world;}public Vec3 getEyePosition(){return new Vec3(0,70,0);}
        public Vec3 getViewVector(float ignored){return view;}public boolean isShiftKeyDown(){return crouching;}
        public void displayClientMessage(Component message,boolean overlay){messages.add(message);}
    }
    public static class Item {}
    public static class Stack {
        public Item item;public CompoundTag tag;public Stack(Item item){this.item=item;}
        public boolean is(Item other){return item==other;}public Item getItem(){return item;}
        public CompoundTag getTag(){return tag;}public CompoundTag getOrCreateTag(){if(tag==null)tag=new CompoundTag();return tag;}
    }
    public static class Indicator extends Item {
        public void setTarget(Stack stack,Actor actor){actor.callbacks++;var t=stack.getOrCreateTag();
            actor.callbackTarget=new BlockPos(t.getInt("TargetX"),t.getInt("TargetY"),t.getInt("TargetZ"));}
    }
    public record Registry(Object value){public Object get(){return value;}}
    public static class Items {public static final Registry ARTILLERY_INDICATOR=new Registry(new Indicator()),FIRING_PARAMETERS=new Registry(new Item());}
    public static class Sounds {public static final Registry CANNON_ZOOM_IN=new Registry(new Sound());}
    public static class Sound {}
    public static class SoundPort {public static void playLocalSound(Actor actor,Sound sound,float volume,float pitch){}}
    public static class Trace {public static Entity findLookingEntity(Entity entity,double range){return ((Actor)entity).looking;}}
    public static class World {public BlockHitResult hit=NativeIndicatorMarkExecutionTest.hit(BlockPos.ZERO);public Clip ray;
        public BlockHitResult clip(Clip context){ray=context;return hit;}}
    public static class Clip {
        public enum Block {OUTLINE}public enum Fluid {NONE}
        public final Vec3 from,to;
        public Clip(Vec3 from,Vec3 to,Block block,Fluid fluid,Entity entity){this.from=from;this.to=to;}
    }
}
