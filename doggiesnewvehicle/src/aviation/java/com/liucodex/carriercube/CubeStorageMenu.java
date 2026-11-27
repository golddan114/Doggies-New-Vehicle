package com.liucodex.carriercube;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

final class CubeStorageMenu extends AbstractContainerMenu {
    static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,CubePrototype.ID);
    static final RegistryObject<MenuType<CubeStorageMenu>> TYPE=MENUS.register("hangar",()->IForgeMenuType.create(
            (id,inv,data)->new CubeStorageMenu(id,inv,null)));
    private final CubeVehicle ship;

    static boolean open(CubeVehicle ship,ServerPlayer p) {
        if (!ship.carrier() || p.isSpectator() || !p.isAlive() || p.getVehicle()!=ship || ship.getSeatIndex(p)!=0) return false;
        NetworkHooks.openScreen(p,new SimpleMenuProvider((id,inv,actor)->new CubeStorageMenu(id,inv,ship),
                Component.translatable("screen.dnvcarriercube.storage")),buffer->{});
        return true;
    }

    CubeStorageMenu(int id, Inventory inventory, CubeVehicle ship) {
        super(TYPE.get(),id);
        this.ship=ship;
        var store=ship==null?new SimpleContainer(3):new SimpleContainer(3) {
            @Override public ItemStack getItem(int i) { return ship.hangar().box(CubeHangar.Bay.values()[i]); }
            @Override public void setItem(int i,ItemStack item) { ship.hangar().setBox(CubeHangar.Bay.values()[i],item); }
            @Override public ItemStack removeItem(int i,int count) {
                if(count<1)return ItemStack.EMPTY;
                var item=getItem(i);
                return ship.hangar().setBox(CubeHangar.Bay.values()[i],ItemStack.EMPTY)?item:ItemStack.EMPTY;
            }
            @Override public ItemStack removeItemNoUpdate(int i) {return removeItem(i,1);}
        };
        for(int i=0;i<3;i++) {
            var bay=CubeHangar.Bay.values()[i];
            addSlot(new Slot(store,i,62+i*18,25) {
                @Override public int getMaxStackSize() {return 1;}
                @Override public boolean mayPlace(ItemStack s) {return CubeHangar.allowed(s) && (ship==null || !ship.hangar().reserved(bay));}
                @Override public boolean mayPickup(Player p) {return ship==null || !ship.hangar().reserved(bay);}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+9*(row+1),8+col*18,64+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,122));
    }

    @Override public boolean stillValid(Player p) {return ship==null || p.isAlive() && !p.isSpectator() && ship.isAlive() && p.getVehicle()==ship && ship.getSeatIndex(p)==0;}
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if(!stillValid(p) || index<0 || index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);
        if(!slot.hasItem() || !slot.mayPickup(p))return ItemStack.EMPTY;
        var stack=slot.getItem();
        var before=stack.copy();
        boolean moved=index<3?moveItemStackTo(stack,3,slots.size(),true)
                :CubeHangar.allowed(stack) && moveItemStackTo(stack,0,3,false);
        if(!moved)return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(p,stack);
        return before;
    }
}
