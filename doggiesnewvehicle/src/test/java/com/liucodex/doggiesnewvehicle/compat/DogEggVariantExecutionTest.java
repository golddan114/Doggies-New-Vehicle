package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.SiloPayloadKind;
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Actual compiled type and ammo selection, with registry/inventory edges. */
class DogEggVariantExecutionTest {
    static final String ROOT="com/liucodex/doggiesnewvehicle/artillery/";
    @Test void nativeIndicatorAdmissionAcceptsDogEggAndRejectsUnrelatedOrUnboundItems() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/item/ItemStack",IndicatorStack.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/item/Item",Item.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraftforge/registries/RegistryObject",Ref.class);
        NuclearRecoveryExecutionTest.map(names,"net/neoforged/neoforge/registries/DeferredHolder",Ref.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/init/ModItems",Items.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/core/component/DataComponentType",Key.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/core/component/DataComponents",Keys.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/neoforge/DnvNeoItemData",NeoData.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/compat/DnvM1IndicatorOrders",
                Set.of("isM1Indicator","unavailableKey"),names,null,false);
        var method=type.getMethod("isM1Indicator",IndicatorStack.class);var stack=new IndicatorStack();
        for(String name:new String[]{"m1_yunlong","nuclear_silo","dogegg_silo"}){
            stack.tag.putString("Type","entity.doggievehicle."+name);assertEquals(true,method.invoke(null,stack));
        }
        stack.tag.putString("Type","entity.doggievehicle.storm_turret");assertEquals(false,method.invoke(null,stack));
        stack.tag.putString("Type","entity.doggievehicle.dogegg_silo");stack.nativeItem=false;assertEquals(false,method.invoke(null,stack));
        stack.nativeItem=true;stack.bound=false;assertEquals(false,method.invoke(null,stack));
        assertEquals("message.doggiesnewvehicle.nuclear.indicator_unavailable",type.getMethod("unavailableKey",String.class,
                com.liucodex.doggiesnewvehicle.api.combat.DnvPlayerArtilleryTarget.class,boolean.class)
                .invoke(null,"entity.doggievehicle.dogegg_silo",null,true));
    }
    @Test void nativeIdentitySelectsDogPayloadAndOnlyItsOwnPhysicalInventory() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/EntityType",Kind.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/item/Item",Item.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/item/ItemStack",Stack.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraftforge/registries/RegistryObject",Ref.class);
        NuclearRecoveryExecutionTest.map(names,"net/neoforged/neoforge/registries/DeferredHolder",Ref.class);
        NuclearRecoveryExecutionTest.map(names,ROOT+"DnvArtilleryEntities",Entities.class);
        NuclearRecoveryExecutionTest.map(names,ROOT+"DnvArtilleryItems",Items.class);
        var type=NuclearRecoveryExecutionTest.extract(ROOT+"silo/NuclearSiloEntity",
                Set.of("dogEgg","payloadKind","ammunition","ammoSlot"),names,Body.class,false);
        var body=(Body)type.getConstructor().newInstance();
        var kind=type.getMethod("payloadKind");var slot=type.getMethod("ammoSlot");
        assertEquals(SiloPayloadKind.NUCLEAR,kind.invoke(body));assertEquals(0,slot.invoke(body));
        body.type=Entities.DOG;assertEquals(SiloPayloadKind.DOGEGG,kind.invoke(body));assertEquals(1,slot.invoke(body));
        body.items[1]=new Stack(Items.DOG,0);assertEquals(-1,slot.invoke(body));
        assertEquals(3,body.items[0].getCount());
        var missile=NuclearRecoveryExecutionTest.extract(ROOT+"silo/NuclearMissileEntity",
                Set.of("dogEgg","payloadKind"),names,Body.class,false);
        body=(Body)missile.getConstructor().newInstance();assertEquals(SiloPayloadKind.NUCLEAR,missile.getMethod("payloadKind").invoke(body));
        body.type=Entities.DOG;assertEquals(SiloPayloadKind.DOGEGG,missile.getMethod("payloadKind").invoke(body));
    }
    public static class Kind { }
    public static class Item { }
    public record Ref(Object value){public Object get(){return value;}public boolean isPresent(){return true;}public boolean isBound(){return true;}}
    public static class Entities {static final Kind NUCLEAR=new Kind(),DOG=new Kind();
        public static final Ref DOGEGG_SILO=new Ref(DOG),DOGEGG_MISSILE=new Ref(DOG);}
    public static class Items {static final Item NUCLEAR=new Item(),DOG=new Item();
        public static final Ref NUCLEAR_MISSILE=new Ref(NUCLEAR),DOGEGG_MISSILE=new Ref(DOG),ARTILLERY_INDICATOR=new Ref(new Item());}
    public record Stack(Item item,int count){public boolean is(Item value){return item==value;}public int getCount(){return count;}}
    public static class Body {public Kind type=Entities.NUCLEAR;public Stack[] items={new Stack(Items.NUCLEAR,3),new Stack(Items.DOG,2)};
        public Kind getType(){return type;}public int getContainerSize(){return items.length;}public Stack getItem(int slot){return items[slot];}}
    public static class Key { }
    public static class Keys {public static final Key CUSTOM_DATA=new Key();}
    public static class IndicatorStack {public boolean nativeItem=true,bound=true;public net.minecraft.nbt.CompoundTag tag=new net.minecraft.nbt.CompoundTag();
        public boolean is(Item item){return nativeItem;}public boolean hasTag(){return bound;}public boolean has(Key key){return bound;}
        public net.minecraft.nbt.CompoundTag getTag(){return tag;}}
    public static class NeoData {public static net.minecraft.nbt.CompoundTag copy(IndicatorStack stack){return stack.tag;}}
}
