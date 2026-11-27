package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.armored.BlizzardTvNetwork;
import com.liucodex.doggiesnewvehicle.armored.DnvBlizzardMissile;
import com.liucodex.doggiesnewvehicle.client.mixin.BlizzardCameraAccess;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Local camera only: the server player remains seated and vulnerable in the original vehicle. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", value = Dist.CLIENT)
public final class BlizzardTvCamera {
    private static BlizzardTvNetwork.State state;
    private static Entity previousCamera;
    private static CameraType previousType;
    private static float desiredYaw, desiredPitch;
    private static int waitTicks, ticks;
    private static boolean attached;
    private BlizzardTvCamera() { }
    public static void receive(BlizzardTvNetwork.State message) {
        var mc = Minecraft.getInstance();
        if (!message.active()) {
            if (state != null && state.session().equals(message.session())) close(false);
            return;
        }
        if (mc.player == null || mc.level == null || mc.player.getVehicle() == null
                || !mc.player.getVehicle().getUUID().equals(message.vehicle())) return;
        if (state != null && state.session().equals(message.session())) return;
        close(true);
        previousCamera = mc.getCameraEntity(); previousType = mc.options.getCameraType();
        state = message; waitTicks = ticks = 0; attached = false;
    }
    private static DnvBlizzardMissile missile() {
        var mc = Minecraft.getInstance();
        if (state == null || mc.level == null) return null;
        Entity entity = mc.level.getEntity(state.entityId());
        return entity instanceof DnvBlizzardMissile guided && entity.getUUID().equals(state.missile()) && !entity.isRemoved() ? guided : null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || state == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.player.getVehicle() == null
                || !mc.player.getVehicle().getUUID().equals(state.vehicle()) || mc.screen != null
                || !mc.isWindowActive() || mc.getConnection() == null) { close(true); return; }
        var missile = missile();
        if (missile == null) {
            if (attached || ++waitTicks > 40) close(true);
            return;
        }
        if (!attached) {
            desiredYaw = missile.getYRot(); desiredPitch = missile.getXRot();
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.setCameraEntity(missile); attached = true;
        }
        if (mc.getCameraEntity() != missile) { close(true); return; }
        if (++ticks % 2 == 0) BlizzardTvNetwork.sendControl(new BlizzardTvNetwork.Control(state.missile(), state.session(), desiredYaw, desiredPitch, false));
    }
    public static boolean turn(Entity entity, double dx, double dy) {
        if (!attached || state == null || entity != Minecraft.getInstance().player) return false;
        if (Double.isFinite(dx) && Double.isFinite(dy)) {
            desiredYaw = net.minecraft.util.Mth.wrapDegrees(desiredYaw + (float) dx * .15F);
            desiredPitch = net.minecraft.util.Mth.clamp(desiredPitch + (float) dy * .15F, -85, 85);
        }
        return true;
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void camera(ViewportEvent.ComputeCameraAngles cameraEvent) {
        var mc = Minecraft.getInstance(); var missile = missile();
        if (!attached || missile == null || mc.player == null || cameraEvent.getCamera().getEntity() != missile) return;
        float partial = (float) cameraEvent.getPartialTick();
        cameraEvent.setYaw(net.minecraft.util.Mth.rotLerp(partial, missile.yRotO, missile.getYRot()));
        cameraEvent.setPitch(net.minecraft.util.Mth.lerp(partial, missile.xRotO, missile.getXRot())); cameraEvent.setRoll(0);
    }
    public static void afterSetup(net.minecraft.client.Camera camera, float partial) {
        var mc = Minecraft.getInstance(); var missile = missile();
        if (!attached || missile == null || mc.player == null || camera.getEntity() != missile) return;
        var position = missile.getPosition(partial).add(0, .1, 0);
        var access = (BlizzardCameraAccess) camera;
        access.dnv$blizzardPosition(position.x, position.y, position.z);
        access.dnv$blizzardRotation(net.minecraft.util.Mth.rotLerp(partial, missile.yRotO, missile.getYRot()),
                net.minecraft.util.Mth.lerp(partial, missile.xRotO, missile.getXRot()));
    }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (state != null && event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS
                && event.getKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) close(true);
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event) { if (attached && missile() != null) event.setFOV(60); }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (state != null) { event.setCanceled(true); event.setSwingHand(false); }
    }
    @SubscribeEvent public static void hud(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        if (!attached || missile() == null) return;
        var mc = Minecraft.getInstance(); var gui = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        gui.fill(0, 0, width, 22, 0xA0001018);
        gui.drawCenteredString(mc.font, net.minecraft.network.chat.Component.translatable("hud.doggiesnewvehicle.blizzard_tv"), width / 2, 7, 0xB8E8FF);
        int x = width / 2, y = height / 2;
        gui.fill(x - 9, y, x - 3, y + 1, 0xDDB8E8FF); gui.fill(x + 3, y, x + 9, y + 1, 0xDDB8E8FF);
        gui.fill(x, y - 9, x + 1, y - 3, 0xDDB8E8FF); gui.fill(x, y + 3, x + 1, y + 9, 0xDDB8E8FF);
    }
    private static void close(boolean informServer) {
        var mc = Minecraft.getInstance();
        if (state == null) return;
        if (informServer && mc.getConnection() != null) BlizzardTvNetwork.sendControl(new BlizzardTvNetwork.Control(state.missile(), state.session(), 0, 0, true));
        Entity camera = mc.getCameraEntity();
        boolean ownsCamera = attached && (camera == null || state.missile().equals(camera.getUUID()));
        if (ownsCamera && mc.player != null) {
            boolean leftVehicle = mc.player.getVehicle() == null
                    || !mc.player.getVehicle().getUUID().equals(state.vehicle());
            Entity restored = !leftVehicle && previousCamera != null && !previousCamera.isRemoved()
                    && previousCamera.level() == mc.level ? previousCamera : mc.player;
            mc.setCameraEntity(restored);
        }
        if (ownsCamera && previousType != null) mc.options.setCameraType(previousType);
        state = null; previousCamera = null; previousType = null; attached = false;
    }
    @Mod.EventBusSubscriber(modid = "doggiesnewvehicle", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(() -> BlizzardTvNetwork.installClient(BlizzardTvCamera::receive)); }
    }
}
