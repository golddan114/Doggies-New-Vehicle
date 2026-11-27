package com.liucodex.libertad.client;

import com.liucodex.libertad.Libertad;
import com.liucodex.libertad.ship.LibertadEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Libertad.ID, value = Dist.CLIENT)
public final class WatchCamera {
    private enum WatchContext implements IKeyConflictContext {
        INSTANCE;
        public boolean isActive() {
            var mc = Minecraft.getInstance();
            return mc.screen == null && observerShip(mc) != null;
        }
        public boolean conflicts(IKeyConflictContext other) { return this == other; }
    }

    static final KeyMapping KEY = new KeyMapping("key.libertad.watch_camera", WatchContext.INSTANCE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.libertad");
    private static LibertadEntity subject;
    private static Player operator;
    private static int station;
    private static WatchOrbit orbit;
    private static CameraType previousView;
    private static double lastX = Double.NaN, lastY;

    private WatchCamera() {}

    private static LibertadEntity observerShip(Minecraft mc) {
        var p = mc.player;
        if (p == null || !p.isAlive() || p.isSpectator() || mc.getCameraEntity() != p
                || !(p.getVehicle() instanceof LibertadEntity ship) || ship.isRemoved()
                || ship.isWreck() || ship.multicrew()) return null;
        int seat = ship.getSeatIndex(p);
        return seat == 1 || seat == 2 ? ship : null;
    }

    private static boolean active(Minecraft mc) {
        return orbit != null && operator == mc.player && observerShip(mc) == subject
                && subject.getSeatIndex(operator) == station && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
    }

    private static void exit(Minecraft mc) {
        if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(previousView);
        orbit = null;
        subject = null;
        operator = null;
        lastX = Double.NaN;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (orbit != null && !active(mc)) exit(mc);
        boolean input = mc.screen == null && mc.isWindowActive() && !mc.isPaused();
        if (!input) lastX = Double.NaN;
        while (KEY.consumeClick()) {
            var ship = observerShip(mc);
            if (!input || ship == null) continue;
            if (orbit == null) {
                subject = ship;
                operator = mc.player;
                station = ship.getSeatIndex(operator);
                orbit = new WatchOrbit(station);
                previousView = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                lastX = Double.NaN;
            } else if (!orbit.locked()) orbit.lock();
            else exit(mc);
        }
    }

    static Component hint() {
        String key = orbit == null ? "enter" : orbit.locked() ? "exit" : "frame";
        return Component.translatable("hud.libertad.watch_camera." + key, KEY.getTranslatedKeyMessage());
    }

    public static Vec2 rotation(LibertadEntity ship, Player player, float partial) {
        var mc = Minecraft.getInstance();
        if (subject != ship || operator != player || !active(mc)) return null;
        if (!orbit.locked() && mc.screen == null && mc.isWindowActive() && mc.mouseHandler.isMouseGrabbed()) {
            double x = mc.mouseHandler.xpos(), y = mc.mouseHandler.ypos();
            if (!Double.isNaN(lastX)) {
                double base = mc.options.sensitivity().get() * .6 + .2;
                double scale = 1.2 * base * base * base;
                orbit.orbit((x - lastX) * scale, (y - lastY) * scale * (mc.options.invertYMouse().get() ? 1 : -1));
            }
            lastX = x;
            lastY = y;
        } else lastX = Double.NaN;
        return orbit.rotation(ship.getYaw(partial));
    }

    public static Vec3 position(LibertadEntity ship, Player player, float partial) {
        return subject == ship && operator == player && active(Minecraft.getInstance())
                ? orbit.position(ship.getPosition(partial), ship.getYaw(partial)) : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        if (!active(mc) || !mc.isWindowActive() || mc.screen != null) return;
        orbit.scroll(event.getScrollDelta(), Screen.hasControlDown());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        if (!active(mc) || event.getCamera() != mc.gameRenderer.getMainCamera()) return;
        // Apply the camera's own interpolated angles after the native SBW shake and seat callbacks.
        event.setYaw(event.getCamera().getYRot());
        event.setPitch(event.getCamera().getXRot());
        event.setRoll(0);
    }
}
