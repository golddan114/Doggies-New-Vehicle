package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the crafted DNV entry and pinned SBW useOn/place. Only vanilla world admission is a double. */
class NuclearPlacementExecutionTest {
    static final String ROOT="com/liucodex/doggiesnewvehicle/artillery/silo/",NATIVE="com/atsuishio/superbwarfare/item/container/ContainerBlockItem";
    @Test void dogEggCraftedItemUsesTheDistinctNativeContainerAndStillConsumesOnce() throws Exception {
        var f=new Fixture();f.deployer.getField("dogEgg").setBoolean(f.entry,true);var c=new Context();c.held.count=1;
        var original=c.held.data.copy();assertEquals(InteractionResult.CONSUME,f.place(c));
        assertEquals("doggiesnewvehicle:dogegg_silo",c.placed.getString("EntityType"));
        assertEquals(1,c.writes);assertEquals(0,c.held.count);assertEquals(original,c.held.data);
    }
    @Test void survivalCraftedItemUsesRealNativePlaceInsteadOfTheNativeNoOpAndConsumesOnce() throws Exception {
        var f=new Fixture();var c=new Context();c.held.count=1;
        assertEquals(InteractionResult.PASS,f.nativeType.getMethod("useOn",Context.class).invoke(Factory.item,c));
        assertEquals(1,c.held.count);assertEquals(0,c.writes);var original=c.held.data.copy();
        assertEquals(InteractionResult.CONSUME,f.place(c));assertEquals(1,c.writes);assertEquals(0,c.held.count);
        assertEquals(original,c.held.data);assertEquals("doggiesnewvehicle:nuclear_silo",c.placed.getString("EntityType"));
        assertFalse(c.placed.contains("UserLabel"));
    }
    @Test void rejectedPlacementAndCreativeCountKeepTheirOriginalDataAndNeverSpendTwice() throws Exception {
        var f=new Fixture();var c=new Context();c.allowed=false;var original=c.held.data.copy();
        assertEquals(InteractionResult.FAIL,f.place(c));assertEquals(0,c.writes);assertEquals(3,c.held.count);assertEquals(original,c.held.data);
        c.allowed=true;c.creative=true;assertEquals(InteractionResult.CONSUME,f.place(c));assertEquals(3,c.held.count);
        assertEquals(1,c.writes);assertEquals(original,c.held.data);
    }
    @Test void failedNativeWorldCallbackStillRestoresTemporaryMetadata() throws Exception {
        var f=new Fixture();var c=new Context();c.throwsOnPlace=true;var original=c.held.data.copy();
        assertThrows(java.lang.reflect.InvocationTargetException.class,()->f.place(c));
        assertEquals(original,c.held.data);assertEquals(3,c.held.count);assertEquals(0,c.writes);
    }
    static class Fixture {
        final Class<?> nativeType,deployer;final Object entry;
        Fixture() throws Exception {
            var names=new HashMap<String,String>();
            map(names,"net/minecraft/world/item/ItemStack",Stack.class);map(names,"net/minecraft/world/item/Item",BlockItemEdge.class);
            map(names,"net/minecraft/world/item/BlockItem",BlockItemEdge.class);map(names,"net/minecraft/world/item/context/UseOnContext",Context.class);
            map(names,"net/minecraft/world/item/context/BlockPlaceContext",PlaceContext.class);map(names,"net/minecraft/world/entity/player/Player",Actor.class);
            map(names,"net/minecraft/world/entity/player/Inventory",Inventory.class);map(names,"net/minecraft/world/level/Level",World.class);
            map(names,"net/minecraft/world/entity/EntityType",Kind.class);map(names,"net/minecraftforge/registries/RegistryObject",Ref.class);
            map(names,"net/neoforged/neoforge/registries/DeferredHolder",Ref.class);
            map(names,"com/liucodex/doggiesnewvehicle/artillery/DnvArtilleryEntities",Entities.class);
            map(names,"net/minecraft/core/component/DataComponentType",Key.class);map(names,"net/minecraft/core/component/DataComponents",Components.class);
            map(names,"net/minecraft/world/item/component/CustomData",Data.class);
            String prefix="com/liucodex/doggiesnewvehicle/compat/placementprobe/";
            names.put(ROOT+"NuclearContainerData",prefix+"Data");names.put(ROOT+"NuclearSiloDeployerItem",prefix+"Deployer");names.put(NATIVE,prefix+"NativeBox");
            var definitions=new HashMap<String,byte[]>();ClassNode nativeNode;
            try(var zip=new ZipFile(SbwTestDependency.path().toFile())){nativeNode=NuclearNativeBoundaryTest.node(zip.getInputStream(zip.getEntry(NATIVE+".class")).readAllBytes());}
            prepare(nativeNode,Set.of("m_6225_","m_40576_","useOn","place"),BlockItemEdge.class);
            // Only creation is an edge. The invoked native placement instance contains the exact SBW methods.
            var factory=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"createInstance","(Lnet/minecraft/world/entity/EntityType;)Lnet/minecraft/world/item/ItemStack;",null,null);
            factory.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            factory.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,Type.getInternalName(Factory.class),"createInstance","(L"+Type.getInternalName(Kind.class)+";)L"+Type.getInternalName(Stack.class)+";",false));
            factory.instructions.add(new InsnNode(Opcodes.ARETURN));nativeNode.methods.add(factory);definitions.put(names.get(NATIVE),remap(nativeNode,names));
            for(String name:List.of("NuclearContainerData","NuclearSiloDeployerItem")){
                ClassNode node;try(var in=getClass().getResourceAsStream("/"+ROOT+name+".class")){node=NuclearNativeBoundaryTest.node(in.readAllBytes());}
                prepare(node,name.equals("NuclearContainerData")?Set.of("read","copyForPlacement","restorePlacementData"):Set.of("useOn"),Object.class);
                if(name.equals("NuclearSiloDeployerItem"))node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"dogEgg","Z",null,null));
                definitions.put(names.get(ROOT+name),remap(node,names));
            }
            var loader=new ExactQueuedShotFixture.Loader(definitions);nativeType=loader.loadClass(names.get(NATIVE).replace('/','.'));
            Factory.item=(BlockItemEdge)nativeType.getConstructor().newInstance();
            deployer=loader.loadClass(names.get(ROOT+"NuclearSiloDeployerItem").replace('/','.'));entry=deployer.getConstructor().newInstance();
        }
        Object place(Context c) throws Exception{return deployer.getMethod("useOn",Context.class).invoke(entry,c);}
    }
    static void map(Map<String,String> names,String key,Class<?> type){names.put(key,Type.getInternalName(type));}
    static void prepare(ClassNode node,Set<String> methods,Class<?> parent){
        node.access=Opcodes.ACC_PUBLIC;node.superName=Type.getInternalName(parent);node.fields.clear();node.interfaces.clear();node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;
        node.methods.removeIf(m->!methods.contains(m.name));node.methods.forEach(m->m.access=(m.access&Opcodes.ACC_STATIC)|Opcodes.ACC_PUBLIC);
        var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false));ctor.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(ctor);
    }
    static byte[] remap(ClassNode node,Map<String,String> names){
        var aliases=Map.of("m_6225_","useOn","m_40576_","place","m_43722_","getItemInHand","m_43723_","getPlayer","m_186336_","getBlockEntityData","m_128423_","get","m_9236_","level","m_150109_","getInventory","m_36057_","removeItem");
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer,new Remapper(){
            @Override public String map(String name){return names.getOrDefault(name,name);}
            @Override public String mapMethodName(String owner,String name,String desc){return aliases.getOrDefault(name,name);}
            @Override public String mapFieldName(String owner,String name,String desc){return name.equals("f_46443_")?"isClientSide":name;}
        }));return writer.toByteArray();
    }
    public static class BlockItemEdge {
        public InteractionResult place(PlaceContext c){
            if(c.source.throwsOnPlace)throw new IllegalStateException("world callback");if(!c.source.allowed)return InteractionResult.FAIL;
            c.source.placed=c.held.data.copy();c.source.writes++;if(!c.source.creative)c.held.count--;return InteractionResult.CONSUME;
        }
        public static CompoundTag getBlockEntityData(Stack stack){return stack.data;}
    }
    public static class Context {
        public final Stack held=new Stack();public boolean allowed=true,creative,throwsOnPlace;public int writes;public CompoundTag placed;
        public Context(){held.data.putString("UserLabel","keep");}public Stack getItemInHand(){return held;}public Actor getPlayer(){return new Actor();}
    }
    public static class PlaceContext extends Context {final Context source;public final Stack held;
        public PlaceContext(Context source){this.source=source;held=source.held;}@Override public Stack getItemInHand(){return held;}}
    public static class Actor {public World level(){return new World();}public Inventory getInventory(){return new Inventory();}}
    public static class World {public boolean isClientSide;}
    public static class Inventory {public void removeItem(Stack s){fail("Fresh deployment must not enter saved-entity removal");}}
    public static class Stack {
        public int count=3;public CompoundTag data=new CompoundTag();public Stack copy(){var s=new Stack();s.count=count;s.data=data.copy();return s;}
        public BlockItemEdge getItem(){return Factory.item;}public CompoundTag getTag(){return data;}public void setTag(CompoundTag tag){data=tag;}
        public Object get(Key key){return new Data(data);}public Object getOrDefault(Key key,Object fallback){return get(key);}
        public Object set(Key key,Object value){data=((Data)value).copyTag();return value;}public Object remove(Key key){data=new CompoundTag();return null;}
    }
    public static class Factory {public static BlockItemEdge item;
        public static Stack createInstance(Kind kind){assertTrue(kind==Entities.KIND||kind==Entities.DOG);var s=new Stack();
            s.data.putString("EntityType",kind==Entities.DOG?"doggiesnewvehicle:dogegg_silo":"doggiesnewvehicle:nuclear_silo");return s;}}
    public static class Kind { }
    public record Ref(Kind kind) {public Object get(){return kind;}}
    public static class Entities {public static final Kind KIND=new Kind(),DOG=new Kind();
        public static final Ref NUCLEAR_SILO=new Ref(KIND),DOGEGG_SILO=new Ref(DOG);}
    public static class Key { }
    public static class Components {public static final Key BLOCK_ENTITY_DATA=new Key();}
    public record Data(CompoundTag tag){public static final Data EMPTY=new Data(new CompoundTag());
        public CompoundTag copyTag(){return tag.copy();}public static Data of(CompoundTag tag){return new Data(tag.copy());}}
}
