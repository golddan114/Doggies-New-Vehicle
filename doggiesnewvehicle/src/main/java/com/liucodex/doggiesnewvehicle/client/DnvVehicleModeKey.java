package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.network.DnvVehicleModeNetwork;
import com.liucodex.doggiesnewvehicle.api.DnvVehicleModeSwitch;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** One click per press; menus and inactive clients never submit a mode request. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", value = Dist.CLIENT)
public final class DnvVehicleModeKey {
    // Retain the saved mapping ID so an existing custom binding survives the shared-key migration.
    private static final KeyMapping KEY = new KeyMapping("key.doggiesnewvehicle.blizzard_mode", GLFW.GLFW_KEY_G, "key.categories.doggiesnewvehicle");
    private DnvVehicleModeKey() { }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        boolean clicked = false;
        while (KEY.consumeClick()) clicked = true;
        if (clicked && mc.screen == null && !mc.isPaused() && mc.isWindowActive() && mc.player != null
                && mc.player.getVehicle() instanceof DnvVehicleModeSwitch)
            DnvVehicleModeNetwork.request(mc.player.getVehicle().getUUID());
    }
    @Mod.EventBusSubscriber(modid = "doggiesnewvehicle", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(KEY); }
    }
}
