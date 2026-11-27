package com.liucodex.carriercube;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@Mod.EventBusSubscriber(modid = CubePrototype.ID, value = Dist.CLIENT)
public final class CubeKeys {
    private static final String CATEGORY = "key.categories.dnvcarriercube";
    private static final List<Binding> BINDINGS = List.of(
            binding("panel", GLFW.GLFW_KEY_O, Context.OCCUPANT),
            binding("storage", GLFW.GLFW_KEY_I, Context.CARRIER),
            binding("flight_station", GLFW.GLFW_KEY_F7, Context.STATION),
            binding("board_aircraft", GLFW.GLFW_KEY_F8, Context.CARRIER),
            binding("launch", GLFW.GLFW_KEY_F9, Context.CARRIER),
            binding("flight_mode", GLFW.GLFW_KEY_G, Context.AIRCRAFT),
            binding("capture", GLFW.GLFW_KEY_HOME, Context.AIRCRAFT),
            binding("return_to_carrier", GLFW.GLFW_KEY_END, Context.AIRCRAFT)
    );

    private CubeKeys() {
    }

    private static Entity lastVehicle;
    private static int lastSeat = -1;

    @SubscribeEvent
    public static void hideBridgeHands(net.minecraftforge.client.event.RenderHandEvent event) {
        var mc=Minecraft.getInstance();
        if (mc.options.getCameraType().isFirstPerson() && mc.player!=null && mc.getCameraEntity()==mc.player
                && mc.player.getVehicle() instanceof CubeVehicle ship && ship.carrier()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        Entity vehicle = client.player == null ? null : client.player.getVehicle();
        int seat = vehicle instanceof CubeVehicle cube ? cube.getSeatIndex(client.player) : -1;
        if (vehicle instanceof CubeVehicle cube && seat >= 0 && seat < (cube.carrier() ? 2 : 1)
                && (vehicle != lastVehicle || seat != lastSeat)) {
            client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dnvcarriercube.panel_hint", BINDINGS.get(0).mapping().getTranslatedKeyMessage()), true);
        }
        lastVehicle = vehicle;
        lastSeat = seat;
        for (Binding binding : BINDINGS) {
            while (binding.mapping().consumeClick()) {
                if (client.screen != null || client.player == null || !binding.context().applies(vehicle)) continue;
                switch (binding.action()) {
                    case "panel" -> CubeStationScreen.open(client, (CubeVehicle) vehicle);
                    case "storage" -> {
                        if (seat == 0) client.player.connection.sendCommand("carriercube storage");
                    }
                    case "flight_mode" -> {
                        // Curios also defaults to G and opens its inventory from the END tick.
                        // Consume only that matching binding while the pilot is using the mode switch.
                        for (KeyMapping other : client.options.keyMappings) {
                            if (other.getName().equals("key.curios.open.desc") && other.same(binding.mapping()))
                                while (other.consumeClick()) { }
                        }
                        client.player.connection.sendCommand("carriercube flight_mode");
                    }
                    case "capture", "return_to_carrier" -> client.player.connection.sendCommand("carriercube return_key");
                    default -> client.player.connection.sendCommand("carriercube " + binding.action());
                }
            }
        }
    }

    // Vanilla searches sections near the crosshair. A 130-block ship can be tracked many sections away.
    @SubscribeEvent
    public static void pickHull(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft client = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || client.player == null || client.player.isPassenger() || client.hitResult == null
                || client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() instanceof CubeVehicle)
            return;
        var player = client.player;
        var start = player.getEyePosition();
        double reach = player.getEntityReach();
        if (client.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS)
            reach = Math.min(reach, start.distanceTo(client.hitResult.getLocation()));
        var end = start.add(player.getViewVector(1).scale(reach));
        var target = CubeVehicle.pickHull(player, start, end);
        if (target != null) client.hitResult = target;
    }

    private static Binding binding(String action, int key, Context context) {
        KeyMapping mapping = new KeyMapping("key.dnvcarriercube." + action,
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CATEGORY);
        return new Binding(mapping, action, context);
    }

    private record Binding(KeyMapping mapping, String action, Context context) {
    }

    private enum Context {
        STATION, CARRIER, AIRCRAFT, OCCUPANT;

        boolean applies(Entity vehicle) {
            return switch (this) {
                case OCCUPANT -> vehicle instanceof CubeVehicle;
                case STATION -> vehicle == null || isCarrier(vehicle);
                case CARRIER -> isCarrier(vehicle);
                case AIRCRAFT -> vehicle instanceof CubeVehicle cube && !cube.carrier();
            };
        }

        private static boolean isCarrier(Entity vehicle) {
            return vehicle instanceof CubeVehicle cube && cube.carrier();
        }
    }

    @Mod.EventBusSubscriber(modid = CubePrototype.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            for (Binding binding : BINDINGS) {
                event.register(binding.mapping());
            }
        }
    }
}
