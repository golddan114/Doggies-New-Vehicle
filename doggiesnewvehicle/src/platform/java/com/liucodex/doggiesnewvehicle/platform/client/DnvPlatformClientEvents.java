package com.liucodex.doggiesnewvehicle.platform.client;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.compat.DnvHoverPlatformGunNetwork;
import com.liucodex.doggiesnewvehicle.entity.DnvHoverRoutePlatformEntity;
import com.liucodex.doggiesnewvehicle.platform.DnvPlatformContent;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteClientProjection;
import com.liucodex.doggiesnewvehicle.mission.DnvTacticalRouteView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Client registration belongs to the optional platform content. */
@Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DnvPlatformClientEvents {
    private static final KeyMapping TOGGLE_GUN = new KeyMapping(
            "key.doggiesnewvehicle.hover_gun.toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "key.categories.doggiesnewvehicle.platform");
    private static final KeyMapping FIRE_GUN = new KeyMapping(
            "key.doggiesnewvehicle.hover_gun.fire",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H,
            "key.categories.doggiesnewvehicle.platform");

    private DnvPlatformClientEvents() {
    }

    @SubscribeEvent
    public static void setupClient(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DnvTacticalRouteClientProjection.install(response -> {
            if (Minecraft.getInstance().screen instanceof DnvTacticalRouteView routeView) {
                routeView.dnv$acceptRouteResponse(response);
            }
        }));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DnvPlatformContent.HOVER_ROUTE_PLATFORM.get(),
                DnvHoverRoutePlatformRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_GUN);
        event.register(FIRE_GUN);
    }

    @Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID, value = Dist.CLIENT)
    public static final class InputEvents {
        private static UUID lastPlatform;

        private InputEvents() {
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            boolean toggle = false;
            while (TOGGLE_GUN.consumeClick()) {
                toggle = true;
            }
            boolean fire = false;
            while (FIRE_GUN.consumeClick()) {
                fire = true;
            }
            if (minecraft.player == null || minecraft.screen != null
                    || !(minecraft.player.getVehicle()
                            instanceof DnvHoverRoutePlatformEntity platform)) {
                lastPlatform = null;
                return;
            }
            UUID platformId = platform.getUUID();
            if (!platformId.equals(lastPlatform)) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "message.doggiesnewvehicle.hover_route_platform.gun.controls",
                        TOGGLE_GUN.getTranslatedKeyMessage(),
                        FIRE_GUN.getTranslatedKeyMessage()), true);
                lastPlatform = platformId;
            }
            if (toggle) {
                DnvHoverPlatformGunNetwork.toggle(platformId);
            }
            if (fire) {
                Vec3 target = minecraft.hitResult != null
                                && minecraft.hitResult.getType() != HitResult.Type.MISS
                        ? minecraft.hitResult.getLocation()
                        : minecraft.player.getEyePosition().add(
                                minecraft.player.getLookAngle().scale(45.0D));
                DnvHoverPlatformGunNetwork.fire(platformId, target);
            }
        }
    }
}
