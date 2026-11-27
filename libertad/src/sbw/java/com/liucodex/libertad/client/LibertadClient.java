package com.liucodex.libertad.client;

import com.atsuishio.superbwarfare.init.ModKeyMappings;
import com.atsuishio.superbwarfare.init.ModMobEffects;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.liucodex.libertad.Libertad;
import com.liucodex.libertad.naval.CrewControl;
import com.liucodex.libertad.ship.LibertadEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = Libertad.ID, value = Dist.CLIENT)
public final class LibertadClient {
    private static final ResourceLocation SBW_VEHICLE_CROSSHAIR = new ResourceLocation("superbwarfare", "vehicle_crosshair");
    private static final ResourceLocation VANILLA_CROSSHAIR = new ResourceLocation("minecraft", "crosshair");
    private static final KeyMapping SECONDARIES = new KeyMapping("key.libertad.secondaries", HelmContext.INSTANCE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.libertad");
    private static KeyMapping standaloneMode;
    private static KeyMapping sharedMode;
    private static boolean modeDown;
    private static int previousShip = -1, previousSeat = -1;
    private static long sideGeneration;
    private static int sideShip = -1;
    private static boolean sideHigh, sideArmed;
    private static boolean sideZoomOwner, sideZoomLatched, sideSwitchDown;
    private static LibertadEntity helmShip;
    private static long helmGeneration;
    private static boolean helmZoom;
    private static MainBatteryHud.Projection projection;

    private LibertadClient() {}

    private enum HelmContext implements IKeyConflictContext {
        INSTANCE;

        @Override public boolean isActive() {
            var client = Minecraft.getInstance();
            return client.screen == null && client.player != null && client.player.getVehicle() instanceof LibertadEntity ship
                    && ship.getSeatIndex(client.player) == 0;
        }

        @Override public boolean conflicts(IKeyConflictContext other) { return this == other; }
    }

    @Mod.EventBusSubscriber(modid = Libertad.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
            event.register(SECONDARIES);
            event.register(WatchCamera.KEY);
            if (!ModList.get().isLoaded("doggiesnewvehicle")) {
                standaloneMode = new KeyMapping("key.libertad.mode", HelmContext.INSTANCE,
                        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.libertad");
                event.register(standaloneMode);
            }
        }
    }

    private static KeyMapping modeKey(Minecraft client) {
        if (sharedMode == null) {
            for (KeyMapping key : client.options.keyMappings) {
                if (key.getName().equals("key.doggiesnewvehicle.blizzard_mode")) {
                    sharedMode = key;
                    break;
                }
            }
            if (sharedMode == null) sharedMode = standaloneMode;
        }
        return sharedMode;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var client = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            constrainLocalHelm(client);
            return;
        }
        var ship = riding(client);
        int seat = ship == null ? -1 : ship.getSeatIndex(client.player);
        boolean acceptsInput = client.screen == null && !client.isPaused() && client.isWindowActive();
        var mode = modeKey(client);
        boolean down = mode != null && mode.isDown();
        if (down && !modeDown && acceptsInput && seat >= 0) Libertad.NETWORK.sendToServer(new Libertad.ToggleCrewMode(ship.getId()));
        modeDown = down;
        while (SECONDARIES.consumeClick()) {
            if (acceptsInput && seat >= 0) Libertad.NETWORK.sendToServer(new Libertad.ToggleSecondaries(ship.getId()));
        }
        // SBW selects the optical camera from zoomVehicle, including toggle zoom and rebound inputs.
        var helm = seat == 0 ? ship : null;
        long helmToken = helm == null ? 0 : helm.fireSnapshot().getLong("operator0");
        boolean zoom = helm != null && !sideZoomOwner && ClientEventHandler.zoomVehicle;
        if (helmToken != 0 && (helm != helmShip || helmToken != helmGeneration || zoom != helmZoom)) {
            Libertad.NETWORK.sendToServer(new Libertad.HelmOptic(helm.getId(), helmToken, zoom));
        }
        helmShip = helm;
        helmGeneration = helmToken;
        helmZoom = zoom;
        long generation = seat > 0 && ship.multicrew() ? ship.fireSnapshot().getLong("operator" + seat) : 0;
        if (!acceptsInput || generation == 0 || client.player.hasEffect(ModMobEffects.SHOCK.get())) {
            // Never leave native vehicle zoom latched after losing this side station.
            if (sideZoomOwner || seat > 0) ClientEventHandler.zoomVehicle = false;
            sideZoomOwner = sideZoomLatched = sideSwitchDown = false;
            sideShip = -1;
            return;
        }
        boolean high = !client.options.getCameraType().isFirstPerson();
        boolean fire = keyDown(client, ModKeyMappings.FIRE);
        boolean switchZoom = keyDown(client, ModKeyMappings.SWITCH_ZOOM);
        boolean newOperator = sideShip != ship.getId() || sideGeneration != generation;
        if (newOperator) {
            sideArmed = !fire;
            sideZoomLatched = false;
            sideSwitchDown = switchZoom;
        }
        if (switchZoom && !sideSwitchDown) sideZoomLatched = !sideZoomLatched;
        sideSwitchDown = switchZoom;
        sideZoomOwner = true;
        ClientEventHandler.zoomVehicle = keyDown(client, ModKeyMappings.HOLD_ZOOM) || sideZoomLatched;
        if (!fire) sideArmed = true;
        boolean shoot = fire && sideArmed;
        if (newOperator || high != sideHigh || shoot) {
            Libertad.NETWORK.sendToServer(new Libertad.SideInput(ship.getId(), generation, high, shoot));
        }
        sideShip = ship.getId();
        sideGeneration = generation;
        sideHigh = high;
    }

    private static boolean keyDown(Minecraft client, KeyMapping mapping) {
        if (!mapping.isConflictContextAndModifierActive()) return false;
        var key = mapping.getKey();
        // SBW cancels MouseButton.Pre for BanHand seats before KeyMapping.set can run.
        // Read the configured mouse button, retaining Forge's context/modifier checks.
        return key.getType() == InputConstants.Type.MOUSE
                ? key.getValue() >= 0 && GLFW.glfwGetMouseButton(client.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS
                : mapping.isDown();
    }

    @SubscribeEvent
    public static void beforeFrame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) constrainLocalHelm(Minecraft.getInstance());
    }

    private static void constrainLocalHelm(Minecraft client) {
        var ship = riding(client);
        int id = ship == null ? -1 : ship.getId();
        int seat = ship == null ? -1 : ship.getSeatIndex(client.player);
        if (seat > 0 && (id != previousShip || seat != previousSeat)) {
            client.player.setYRot(ship.getYRot() + (seat == 1 ? -90 : 90));
            client.player.yRotO = client.player.getYRot();
        }
        previousShip = id;
        previousSeat = seat;
        if (ship != null) constrainHelmYaw(ship, client.player);
    }

    public static void constrainHelmYaw(LibertadEntity ship, Entity rider) {
        var client = Minecraft.getInstance();
        if (rider != client.player || ship.isWreck()) return;
        float yaw = rider.getYRot();
        float clamped = CrewControl.viewYaw(yaw, ship.getYRot(), ship.getSeatIndex(rider), client.options.getCameraType().isFirstPerson());
        if (clamped != yaw) {
            rider.setYRot(clamped);
            rider.yRotO += clamped - yaw;
        }
    }

    static float helmYaw(float yaw, float bowYaw, boolean firstPerson) {
        return CrewControl.viewYaw(yaw, bowYaw, 0, firstPerson);
    }

    @SubscribeEvent
    public static void captureView(RenderLevelStageEvent event) {
        // LevelRenderer supplies the camera rotation stack here. AFTER_LEVEL supplies the projection stack.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var client = Minecraft.getInstance();
        LibertadEntity ship = riding(client);
        if (ship == null || client.screen != null || client.options.hideGui
                || (ship.getSeatIndex(client.player) != 0 && !ship.multicrew())
                || event.getCamera() != client.gameRenderer.getMainCamera()) {
            projection = null;
            return;
        }
        Vec3 eye = event.getCamera().getPosition();
        projection = new MainBatteryHud.Projection(ship.getId(), eye,
                new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()));
    }

    @SubscribeEvent
    public static void crosshair(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if ((id.equals(SBW_VEHICLE_CROSSHAIR) || id.equals(VANILLA_CROSSHAIR))
                && riding(Minecraft.getInstance()) != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        var view = projection;
        projection = null;
        var client = Minecraft.getInstance();
        LibertadEntity ship = riding(client);
        if (ship == null || client.screen != null || client.options.hideGui) return;
        int seat = ship.getSeatIndex(client.player);
        if (seat < 0) return;
        int bottom = client.gui instanceof ForgeGui forge ? Math.max(forge.leftHeight, forge.rightHeight) : 45;
        var status = ship.fireSnapshot();
        var gui = event.getGuiGraphics();
        if (seat > 0 && !ship.multicrew()) {
            gui.drawCenteredString(client.font, Component.translatable(seat == 1 ? "hud.libertad.port_watch" : "hud.libertad.starboard_watch")
                            .append(" · ").append(WatchCamera.hint()),
                    gui.guiWidth() / 2, MainBatteryHud.hintTop(MainBatteryHud.barTop(gui.guiHeight(), bottom)), 0xF2F5F8);
            return;
        }
        Component hint;
        if (seat == 0) {
            hint = Component.translatable(ship.multicrew() ? "hud.libertad.mode.crew" : "hud.libertad.mode.solo",
                    modeKey(client) == null ? Component.literal("—") : modeKey(client).getTranslatedKeyMessage());
            if (!ship.multicrew()) hint = hint.copy().append(" · ").append(Component.translatable(
                    MainBatteryHud.secondaryHintKey(status.getInt("secondary")), SECONDARIES.getTranslatedKeyMessage()));
        } else hint = Component.translatable(seat == 1 ? "hud.libertad.port_guns" : "hud.libertad.starboard_guns");
        MainBatteryHud.draw(gui, client.font, ship.batteryDisplay(seat), status.getInt(seat == 0 ? "ready" : "ready" + seat), hint, bottom,
                view != null && view.shipId() == ship.getId() ? view : null);
    }

    private static LibertadEntity riding(Minecraft client) {
        if (client.player == null || !client.player.isAlive() || client.player.isSpectator()
                || !(client.player.getVehicle() instanceof LibertadEntity ship) || ship.isWreck()) return null;
        return ship;
    }
}
