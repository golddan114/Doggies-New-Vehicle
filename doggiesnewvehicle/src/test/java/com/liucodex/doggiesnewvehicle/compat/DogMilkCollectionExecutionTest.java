package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

/** Execute DNV collection and the real vanilla filled-container helper. Inventory/world edges remain observable. */
class DogMilkCollectionExecutionTest {
    static final String SOURCE="com/liucodex/doggiesnewvehicle/artillery/silo/DogMilkCollection";
    @Test void oneEmptyCartonBecomesOneFilledCartonOnlyOnTheServer() throws Exception {
        var f=new Fixture();f.player.held.count=1;
        assertEquals(InteractionResult.CONSUME,f.collect());assertSame(Items.FULL,f.player.held.item);
        assertEquals(1,f.player.held.count);assertEquals(1,f.player.handWrites);assertEquals(1,f.wolf.sounds);
        assertEquals(InteractionResult.PASS,f.collect());assertEquals(1,f.player.handWrites);
        f=new Fixture();f.player.world.isClientSide=true;int count=f.player.held.count;
        assertEquals(InteractionResult.SUCCESS,f.collect());assertEquals(count,f.player.held.count);
        assertEquals(0,f.player.handWrites);assertEquals(0,f.wolf.sounds);
    }
    @Test void stackedCartonsUseVanillaInventoryOverflowAndCreativeDuplicateRules() throws Exception {
        var f=new Fixture();f.player.held.count=3;assertEquals(InteractionResult.CONSUME,f.collect());
        assertSame(Items.EMPTY,f.player.held.item);assertEquals(2,f.player.held.count);assertEquals(1,f.player.inventory.added);
        f=new Fixture();f.player.inventory.room=false;f.collect();assertEquals(1,f.player.drops);assertEquals(2,f.player.held.count);
        f=new Fixture();f.player.abilities.instabuild=true;f.collect();assertEquals(3,f.player.held.count);
        assertEquals(1,f.player.inventory.added);f.collect();assertEquals(1,f.player.inventory.added);
    }
    @Test void deadSpectatorWrongItemEmptyAndForeignWorldCannotExchangeAnything() throws Exception {
        for(int state=0;state<6;state++){
            var f=new Fixture();switch(state){case 0->f.player.alive=false;case 1->f.player.spectator=true;
                case 2->f.wolf.alive=false;case 3->f.player.held.item=new Item();case 4->f.player.held.count=0;case 5->f.wolf.world=new World();}
            int count=f.player.held.count;assertEquals(InteractionResult.PASS,f.collect());assertEquals(count,f.player.held.count);
            assertEquals(0,f.player.handWrites);assertEquals(0,f.player.inventory.added);assertEquals(0,f.wolf.sounds);
        }
    }
    @Test void canceledOrNonWolfInteractionPassesThroughAndSuccessfulWolfUseConsumesTheNativeSitAction() throws Exception {
        var f=new Fixture();var event=new Event(f.player,f.wolf);event.canceled=true;f.interact(event);assertEquals(0,f.player.handWrites);
        event=new Event(f.player,new Entity());f.interact(event);assertFalse(event.canceled);assertEquals(0,f.player.handWrites);
        event=new Event(f.player,f.wolf);f.interact(event);assertTrue(event.canceled);assertEquals(InteractionResult.CONSUME,event.result);
        assertEquals(1,f.player.handWrites);assertEquals(1,f.player.inventory.added);
    }
    static class Fixture {
        final Player player=new Player();final Wolf wolf=new Wolf();final Class<?> target;
        Fixture() throws Exception {
            wolf.world=player.world;var names=new HashMap<String,String>();
            Map.ofEntries(Map.entry("net/minecraft/world/entity/player/Player",Player.class),
                Map.entry("net/minecraft/world/entity/player/Inventory",Inventory.class),
                Map.entry("net/minecraft/world/entity/player/Abilities",Abilities.class),
                Map.entry("net/minecraft/world/entity/animal/Wolf",Wolf.class),Map.entry("net/minecraft/world/entity/Entity",Entity.class),
                Map.entry("net/minecraft/world/entity/LivingEntity",Entity.class),
                Map.entry("net/minecraft/world/entity/item/ItemEntity",Drop.class),Map.entry("net/minecraft/world/level/Level",World.class),
                Map.entry("net/minecraft/world/item/Item",Item.class),Map.entry("net/minecraft/world/level/ItemLike",Item.class),
                Map.entry("net/minecraft/world/item/ItemStack",Stack.class),
                Map.entry("net/minecraft/sounds/SoundEvent",Sound.class),Map.entry("net/minecraft/sounds/SoundEvents",Sounds.class),
                Map.entry("net/minecraftforge/registries/RegistryObject",Ref.class),Map.entry("net/neoforged/neoforge/registries/DeferredHolder",Ref.class),
                Map.entry("net/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract",Event.class),
                Map.entry("net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteract",Event.class),
                Map.entry("com/liucodex/doggiesnewvehicle/artillery/DnvArtilleryItems",Items.class))
                .forEach((name,type)->names.put(name,Type.getInternalName(type)));
            var definitions=new HashMap<String,byte[]>();
            String vanilla="net/minecraft/world/item/ItemUtils";
            names.put(SOURCE,"com/liucodex/doggiesnewvehicle/compat/milkprobe/Collection");
            names.put(vanilla,"com/liucodex/doggiesnewvehicle/compat/milkprobe/VanillaFill");
            for(String source:List.of(SOURCE,vanilla)){
                ClassNode node;try(var input=getClass().getResourceAsStream("/"+source+".class")){
                    node=NuclearNativeBoundaryTest.node(input.readAllBytes());}
                node.access=Opcodes.ACC_PUBLIC;node.superName="java/lang/Object";node.interfaces.clear();node.fields.clear();
                node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;
                node.methods.removeIf(m->!Set.of("collect","interact","createFilledResult").contains(m.name));
                node.methods.forEach(m->m.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC);
                definitions.put(names.get(source),NuclearNativeBoundaryTest.remap(node,names));
            }
            var loader=new ExactQueuedShotFixture.Loader(definitions);target=loader.loadClass(names.get(SOURCE).replace('/','.'));
        }
        Object collect() throws Exception {return target.getMethod("collect",Player.class,InteractionHand.class,Wolf.class)
                .invoke(null,player,InteractionHand.MAIN_HAND,wolf);}
        void interact(Event event) throws Exception {target.getMethod("interact",Event.class).invoke(null,event);}
    }
    public static class World {public boolean isClientSide;}
    public static class Entity {public World world=new World();public boolean alive=true;
        public World level(){return world;}public boolean isAlive(){return alive;}}
    public static class Wolf extends Entity {public int sounds;public void playSound(Sound event,float volume,float pitch){sounds++;}}
    public static class Player extends Entity {public boolean spectator;public int handWrites,drops;
        public Stack held=new Stack(Items.EMPTY,3);public final Inventory inventory=new Inventory();public final Abilities abilities=new Abilities();
        public Stack getItemInHand(InteractionHand hand){return held;}public boolean isSpectator(){return spectator;}
        public void setItemInHand(InteractionHand hand,Stack stack){held=stack;handWrites++;}
        public Inventory getInventory(){return inventory;}public Abilities getAbilities(){return abilities;}
        public boolean hasInfiniteMaterials(){return abilities.instabuild;}
        public Drop drop(Stack stack,boolean random){assertSame(Items.FULL,stack.item);drops++;return new Drop();}}
    public static class Inventory {public boolean room=true,containsFull;public int added;
        public boolean contains(Stack stack){return containsFull;}public boolean add(Stack stack){if(!room)return false;added++;containsFull=true;return true;}}
    public static class Abilities {public boolean instabuild;}
    public static class Item { }
    public static class Drop { }
    public static class Stack {public Item item;public int count;
        public Stack(Item item){this(item,1);}public Stack(Item item,int count){this.item=item;this.count=count;}
        public boolean is(Item value){return item==value;}public boolean isEmpty(){return count<=0;}public void shrink(int amount){count-=amount;}
        public void consume(int amount,Entity source){if(!(source instanceof Player p)||!p.hasInfiniteMaterials())shrink(amount);}}
    public record Ref(Item value){public Object get(){return value;}}
    public static class Items {static final Item EMPTY=new Item(),FULL=new Item();
        public static final Ref DOG_MILK_CARTON=new Ref(EMPTY),WILD_DOG_MILK=new Ref(FULL);}
    public static class Sound { }
    public static class Sounds {public static final Sound BOTTLE_FILL=new Sound();}
    public static class Event {final Player player;final Entity entity;public boolean canceled;public InteractionResult result;
        public Event(Player player,Entity entity){this.player=player;this.entity=entity;}
        public Player getEntity(){return player;}public Entity getTarget(){return entity;}public InteractionHand getHand(){return InteractionHand.MAIN_HAND;}
        public boolean isCanceled(){return canceled;}public void setCanceled(boolean value){canceled=value;}
        public void setCancellationResult(InteractionResult value){result=value;}}
}
