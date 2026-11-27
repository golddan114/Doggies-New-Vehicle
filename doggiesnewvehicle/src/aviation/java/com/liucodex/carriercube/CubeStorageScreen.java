package com.liucodex.carriercube;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid=CubePrototype.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CubeStorageScreen extends AbstractContainerScreen<CubeStorageMenu> {
    private CubeStorageScreen(CubeStorageMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title);imageWidth=176;imageHeight=148;inventoryLabelY=53;
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->MenuScreens.register(CubeStorageMenu.TYPE.get(),CubeStorageScreen::new));
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff565656);
        g.fill(leftPos,topPos,leftPos+imageWidth-2,topPos+imageHeight-2,0xfffafafa);
        g.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xffc6c6c6);
        for(var slot:menu.slots) {
            int x=leftPos+slot.x-1,y=topPos+slot.y-1;
            g.fill(x,y,x+18,y+18,0xffffffff);g.fill(x,y,x+17,y+17,0xff383838);
            g.fill(x+1,y+1,x+17,y+17,0xff8b8b8b);
        }
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial) {renderBackground(g);super.render(g,x,y,partial);renderTooltip(g,x,y);}
}
